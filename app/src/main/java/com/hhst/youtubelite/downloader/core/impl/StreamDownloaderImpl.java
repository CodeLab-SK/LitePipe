package com.hhst.youtubelite.downloader.core.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hhst.youtubelite.downloader.core.ProgressCallback;
import com.hhst.youtubelite.downloader.core.StreamDownloader;
import com.tencent.mmkv.MMKV;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Singleton
public class StreamDownloaderImpl implements StreamDownloader {
	private static final String TAG = "StreamDownloader";
	private static final long BLOCK_SIZE = 512 * 1024;
	private static final int MAX_PARALLEL_CHUNKS = 1024;
	private static final int DOWNLOAD_MAX_REQUESTS = 16;
	private static final int DOWNLOAD_MAX_REQUESTS_PER_HOST = 8;
	
	private static final long TIMEOUT_SECONDS = 30L;

	private final OkHttpClient client;
	private final MMKV mmkv;
	private final ThreadPoolExecutor executor;
	private final Map<String, TaskContext> tasks = new ConcurrentHashMap<>();

	@Inject
	public StreamDownloaderImpl(OkHttpClient client, MMKV mmkv) {
		this.client = client.newBuilder()
						.cache(null)
						.dispatcher(createDispatcher())
						.connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
						.writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
						.readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
						.build();
		this.mmkv = mmkv;
		this.executor = new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, "dl-worker"));
		this.executor.allowCoreThreadTimeOut(true);
	}

	private static Dispatcher createDispatcher() {
		final Dispatcher dispatcher = new Dispatcher();
		dispatcher.setMaxRequests(DOWNLOAD_MAX_REQUESTS);
		dispatcher.setMaxRequestsPerHost(DOWNLOAD_MAX_REQUESTS_PER_HOST);
		return dispatcher;
	}

	@Override
	public CompletableFuture<File> download(@NonNull String key, @NonNull String url, @NonNull File out, @Nullable ProgressCallback cb) {
		CompletableFuture<File> future = new CompletableFuture<>();
		TaskContext ctx = new TaskContext(key, url, out, future, cb);
		tasks.put(key, ctx);
		executor.execute(() -> runTask(ctx));
		return future;
	}

	private void runTask(TaskContext ctx) {
		RandomAccessFile raf = null;
		try {
			final long total;
			final boolean rangeSupported;

			try (Response head = client.newCall(new Request.Builder().url(ctx.url).head().build()).execute()) {
				if (!head.isSuccessful() && head.code() != 405) {
					throw new IOException("Probing failed: " + head.code());
				}
				total = Long.parseLong(head.header("Content-Length", "-1"));
				rangeSupported = total > 0 && (head.code() == 206 
								|| "bytes".equalsIgnoreCase(head.header("Accept-Ranges")) 
								|| ctx.url.contains("googlevideo.com"));
			}

			if (total <= 0) {
				throw new IOException("Invalid content length");
			}

			int numBlocks = (int) ((total + BLOCK_SIZE - 1) / BLOCK_SIZE);
			if (numBlocks > MAX_PARALLEL_CHUNKS) {
			}

			String stateKey = "dl_state_" + ctx.key;
			long savedTotal = mmkv.decodeLong(stateKey + "_total", -1);
			if (savedTotal != -1 && savedTotal != total) {
				Log.w(TAG, "Content length changed for " + ctx.key + ", restarting");
				mmkv.removeValueForKey(stateKey + "_bits");
			}
			mmkv.encode(stateKey + "_total", total);

			byte[] savedBits = mmkv.decodeBytes(stateKey + "_bits");
			BitSet bits = (rangeSupported && savedBits != null) ? BitSet.valueOf(savedBits) : new BitSet();
			
			long initialDownloaded = calculateDownloaded(bits, numBlocks, total);
			ctx.downloadedBytes.set(initialDownloaded);
			reportProgress(ctx, total);

			raf = new RandomAccessFile(ctx.out, "rw");
			if (raf.length() > total) raf.setLength(total);
			else if (raf.length() < total && !rangeSupported) raf.setLength(0);

			if (bits.cardinality() < numBlocks) {
				final RandomAccessFile finalRaf = raf;
				CompletableFuture<?>[] futures = IntStream.range(0, numBlocks)
								.filter(i -> !bits.get(i))
								.mapToObj(i -> CompletableFuture.runAsync(() -> 
												downloadBlock(ctx, i, numBlocks, total, rangeSupported, finalRaf, bits, stateKey), executor))
								.toArray(CompletableFuture[]::new);
				
				CompletableFuture.allOf(futures).join();
			}

			if (ctx.out.length() != total) {
				throw new IOException("File size mismatch: expected " + total + " but got " + ctx.out.length());
			}

			if (!ctx.isInactive()) {
				cleanupState(stateKey);
				tasks.remove(ctx.key);
				ctx.future.complete(ctx.out);
				if (ctx.cb != null) ctx.cb.onComplete(ctx.out);
			}
		} catch (Exception e) {
			handleTaskError(ctx, e);
		} finally {
			closeQuietly(raf);
		}
	}

	private void downloadBlock(TaskContext ctx, int idx, int totalBlocks, long totalLen, boolean rangeSupported, RandomAccessFile raf, BitSet bits, String stateKey) {
		if (ctx.isInactive()) return;
		
		long start = idx * BLOCK_SIZE;
		long end = Math.min(start + BLOCK_SIZE - 1, totalLen - 1);
		
		int retry = 0;
		while (retry < 3 && !ctx.isInactive()) {
			Request.Builder rb = new Request.Builder().url(ctx.url);
			if (rangeSupported) {
				rb.header("Range", "bytes=" + start + "-" + end);
			}

			try (Response resp = client.newCall(rb.build()).execute()) {
				if (!resp.isSuccessful()) {
					if (resp.code() == 403 || resp.code() == 410) {
						throw new IOException("URL expired (HTTP " + resp.code() + ")");
					}
					throw new IOException("HTTP " + resp.code());
				}

				if (rangeSupported && resp.code() != 206) {
					if (idx != 0) throw new IOException("Server ignored Range header");
				}

				try (InputStream is = resp.body().byteStream()) {
					byte[] buf = new byte[16384];
					int read;
					long offset = start;
					while ((read = is.read(buf)) != -1) {
						if (ctx.isInactive()) return;
						synchronized (ctx.fileLock) {
							raf.seek(offset);
							raf.write(buf, 0, read);
						}
						ctx.downloadedBytes.addAndGet(read);
						reportProgress(ctx, totalLen);
						offset += read;
						if (rangeSupported && offset > end) break;
					}
					
					synchronized (ctx.stateLock) {
						bits.set(idx);
						mmkv.encode(stateKey + "_bits", bits.toByteArray());
					}
					return;
				}
			} catch (Exception e) {
				retry++;
				if (retry >= 3) {
					ctx.error.set(e);
					return;
				}
				try { Thread.sleep(1000L * retry); } catch (InterruptedException ignored) {}
			}
		}
	}

	private long calculateDownloaded(BitSet bits, int numBlocks, long total) {
		long count = 0;
		for (int i = 0; i < numBlocks; i++) {
			if (bits.get(i)) {
				if (i == numBlocks - 1) {
					count += (total % BLOCK_SIZE == 0) ? BLOCK_SIZE : (total % BLOCK_SIZE);
				} else {
					count += BLOCK_SIZE;
				}
			}
		}
		return count;
	}

	private void reportProgress(TaskContext ctx, long total) {
		if (ctx.cb == null || total <= 0) return;
		int progress = (int) (ctx.downloadedBytes.get() * 100 / total);
		progress = Math.min(99, Math.max(0, progress));
		
		int last = ctx.lastProgress.get();
		if (progress > last) {
			if (ctx.lastProgress.compareAndSet(last, progress)) {
				ctx.cb.onProgress(progress);
			}
		}
	}

	private void handleTaskError(TaskContext ctx, Exception e) {
		if (ctx.isInactive()) return;
		Log.e(TAG, "Task failed: " + ctx.key, e);
		tasks.remove(ctx.key);
		ctx.future.completeExceptionally(e);
		if (ctx.cb != null) ctx.cb.onError(e);
	}

	private void cleanupState(String stateKey) {
		mmkv.removeValueForKey(stateKey + "_total");
		mmkv.removeValueForKey(stateKey + "_bits");
	}

	@Override
	public void pause(@NonNull String key) {
		TaskContext ctx = tasks.get(key);
		if (ctx != null) ctx.paused.set(true);
	}

	@Override
	public void resume(@NonNull String key) {
		TaskContext ctx = tasks.get(key);
		if (ctx != null && ctx.paused.compareAndSet(true, false)) {
			executor.execute(() -> runTask(ctx));
		}
	}

	@Override
	public void cancel(@NonNull String key) {
		TaskContext ctx = tasks.remove(key);
		if (ctx != null) {
			ctx.cancelled.set(true);
			ctx.future.cancel(true);
			cleanupState("dl_state_" + key);
			if (ctx.cb != null) ctx.cb.onCancel();
		}
	}

	@Override
	public void setMaxThreadCount(int count) {
		int threads = Math.max(2, Math.min(count, 16));
		executor.setCorePoolSize(threads);
		executor.setMaximumPoolSize(threads * 2);
	}

	private void closeQuietly(AutoCloseable c) {
		if (c != null) try { c.close(); } catch (Exception ignored) {}
	}

	private static class TaskContext {
		final String key;
		final String url;
		final File out;
		final CompletableFuture<File> future;
		final ProgressCallback cb;
		final AtomicBoolean paused = new AtomicBoolean(false);
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final AtomicLong downloadedBytes = new AtomicLong(0);
		final AtomicInteger lastProgress = new AtomicInteger(-1);
		final Object fileLock = new Object();
		final Object stateLock = new Object();
		final java.util.concurrent.atomic.AtomicReference<Exception> error = new java.util.concurrent.atomic.AtomicReference<>();

		TaskContext(String key, String url, File out, CompletableFuture<File> future, ProgressCallback cb) {
			this.key = key;
			this.url = url;
			this.out = out;
			this.future = future;
			this.cb = cb;
		}

		boolean isInactive() {
			return paused.get() || cancelled.get() || future.isCancelled() || error.get() != null;
		}
	}
}
