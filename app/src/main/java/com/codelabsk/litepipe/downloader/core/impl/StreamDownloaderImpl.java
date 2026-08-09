package com.codelabsk.litepipe.downloader.core.impl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.codelabsk.litepipe.downloader.core.ProgressCallback;
import com.codelabsk.litepipe.downloader.core.StreamDownloader;
import com.tencent.mmkv.MMKV;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
	private static final long BLOCK_SIZE = 1024 * 1024;
	private static final int DOWNLOAD_MAX_REQUESTS = 64;
	private static final int DOWNLOAD_MAX_REQUESTS_PER_HOST = 32;
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
		this.executor = new ThreadPoolExecutor(8, 16, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, "dl-worker"));
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
		TaskContext ctx = tasks.get(key);
		if (ctx == null) {
			ctx = new TaskContext(key, url, out, new CompletableFuture<>(), cb);
			tasks.put(key, ctx);
		} else {
			ctx.url = url;
			ctx.cb = cb;
			ctx.error.set(null);
			ctx.paused.set(false);
			if (ctx.future.isDone() || ctx.future.isCancelled()) {
				ctx.future = new CompletableFuture<>();
			}
		}
		final TaskContext finalCtx = ctx;
		executor.execute(() -> runTask(finalCtx));
		return finalCtx.future;
	}

	private void runTask(TaskContext ctx) {
		final String stateKey = "dl_state_" + ctx.key;
		try (RandomAccessFile raf = new RandomAccessFile(ctx.out, "rw");
			 FileChannel channel = raf.getChannel()) {
			
			long total = mmkv.decodeLong(stateKey + "_total", -1);
			boolean rangeSupported = mmkv.decodeBool(stateKey + "_range", false);

			// Re-probe if we don't have metadata or if we suspect the URL changed
			if (total <= 0) {
				Request probeRequest = new Request.Builder().url(ctx.url).header("Range", "bytes=0-0").build();
				try (Response resp = client.newCall(probeRequest).execute()) {
					if (resp.isSuccessful() && resp.code() == 206) {
						rangeSupported = true;
						String cr = resp.header("Content-Range");
						if (cr != null) total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1));
					} else if (resp.code() == 403 || resp.code() == 410) {
						throw new IOException("URL expired");
					}
				}

				if (total <= 0) {
					try (Response resp = client.newCall(new Request.Builder().url(ctx.url).build()).execute()) {
						if (!resp.isSuccessful()) throw new IOException("Probing failed: " + resp.code());
						String cl = resp.header("Content-Length");
						total = Long.parseLong(cl != null ? cl : "-1");
						if (!rangeSupported) {
							rangeSupported = "bytes".equalsIgnoreCase(resp.header("Accept-Ranges")) || ctx.url.contains("googlevideo.com");
						}
					}
				}

				if (total <= 0) throw new IOException("Invalid content length");
				mmkv.encode(stateKey + "_total", total);
				mmkv.encode(stateKey + "_range", rangeSupported);
			}

			final long finalTotal = total;
			final boolean finalRangeSupported = rangeSupported;
			final int numBlocks = (int) ((finalTotal + BLOCK_SIZE - 1) / BLOCK_SIZE);
			byte[] savedBits = mmkv.decodeBytes(stateKey + "_bits");
			final BitSet bits = (finalRangeSupported && savedBits != null) ? BitSet.valueOf(savedBits) : new BitSet();
			
			long initialDownloaded = calculateDownloaded(stateKey, bits, numBlocks, finalTotal);
			ctx.downloadedBytes.set(initialDownloaded);
			ctx.startTime.set(System.currentTimeMillis());
			ctx.sessionDownloaded.set(0);
			reportProgress(ctx, finalTotal);

			if (raf.length() > finalTotal) raf.setLength(finalTotal);

			if (bits.cardinality() < numBlocks) {
				CompletableFuture<?>[] futures = IntStream.range(0, numBlocks)
								.filter(i -> !bits.get(i))
								.mapToObj(i -> CompletableFuture.runAsync(() -> 
												downloadBlock(ctx, i, finalTotal, finalRangeSupported, channel, bits, stateKey), executor))
								.toArray(CompletableFuture[]::new);
				
				try {
					CompletableFuture.allOf(futures).join();
				} catch (Exception ignored) {
				}
			}

			if (ctx.error.get() != null) {
				throw ctx.error.get();
			}

			if (bits.cardinality() < numBlocks) {
				if (ctx.isInactive()) return;
				throw new IOException("Download incomplete: " + bits.cardinality() + "/" + numBlocks + " blocks");
			}

			if (!ctx.isInactive()) {
				cleanupState(stateKey, numBlocks);
				tasks.remove(ctx.key);
				ctx.future.complete(ctx.out);
				if (ctx.cb != null) ctx.cb.onComplete(ctx.out);
			}
		} catch (Exception e) {
			handleTaskError(ctx, e);
		}
	}

	private void downloadBlock(TaskContext ctx, int idx, long totalLen, boolean rangeSupported, FileChannel channel, BitSet bits, String stateKey) {
		if (ctx.isInactive()) return;
		
		final long blockStart = idx * BLOCK_SIZE;
		final long blockEnd = Math.min(blockStart + BLOCK_SIZE - 1, totalLen - 1);
		
		long savedProg = mmkv.decodeLong(stateKey + "_p_" + idx, -1);
		long currentOffset = (rangeSupported && savedProg != -1) ? savedProg : blockStart;
		
		for (int retry = 0; retry < 5 && !ctx.isInactive(); retry++) {
			Request.Builder rb = new Request.Builder().url(ctx.url);
			if (rangeSupported) {
				rb.header("Range", "bytes=" + currentOffset + "-" + blockEnd);
			}

			try (Response resp = client.newCall(rb.build()).execute()) {
				if (!resp.isSuccessful()) {
					if (resp.code() == 403 || resp.code() == 410) throw new IOException("URL expired");
					throw new IOException("HTTP " + resp.code());
				}

				try (InputStream is = Objects.requireNonNull(resp.body()).byteStream()) {
					byte[] buf = new byte[16384];
					int read;
					while ((read = is.read(buf)) != -1) {
						if (ctx.isInactive()) {
							if (rangeSupported) mmkv.encode(stateKey + "_p_" + idx, currentOffset);
							return;
						}
						
						ByteBuffer buffer = ByteBuffer.wrap(buf, 0, read);
						while (buffer.hasRemaining()) {
							int written = channel.write(buffer, currentOffset);
							currentOffset += written;
						}
						
						ctx.downloadedBytes.addAndGet(read);
						ctx.sessionDownloaded.addAndGet(read);
						reportProgress(ctx, totalLen);
						
						if (rangeSupported && currentOffset > blockEnd) break;
					}
					
					synchronized (ctx.stateLock) {
						bits.set(idx);
						mmkv.encode(stateKey + "_bits", bits.toByteArray());
						mmkv.removeValueForKey(stateKey + "_p_" + idx);
					}
					return;
				}
			} catch (Exception e) {
				if (isExpiredError(e)) {
					ctx.error.compareAndSet(null, e);
					return;
				}
				if (retry == 4) {
					ctx.error.compareAndSet(null, e);
					return;
				}
				try {
					TimeUnit.MILLISECONDS.sleep(1000L * (retry + 1));
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private long calculateDownloaded(String stateKey, BitSet bits, int numBlocks, long total) {
		long count = 0;
		for (int i = 0; i < numBlocks; i++) {
			if (bits.get(i)) {
				if (i == numBlocks - 1) count += (total - (long) (numBlocks - 1) * BLOCK_SIZE);
				else count += BLOCK_SIZE;
			} else {
				long p = mmkv.decodeLong(stateKey + "_p_" + i, -1);
				if (p != -1) {
					count += (p - (long) i * BLOCK_SIZE);
				}
			}
		}
		return count;
	}

	private void reportProgress(TaskContext ctx, long total) {
		if (ctx.cb == null || total <= 0) return;
		long now = System.currentTimeMillis();
		long lastReport = ctx.lastReportTime.get();

		long downloaded = ctx.downloadedBytes.get();
		int progress = (int) (downloaded * 100 / total);
		progress = Math.min(100, Math.max(0, progress));
		
		if (now - lastReport > 500 || progress > ctx.lastProgress.get()) {
			if (ctx.lastReportTime.compareAndSet(lastReport, now)) {
				ctx.lastProgress.set(progress);
				
				long elapsed = now - ctx.startTime.get();
				long speed = elapsed > 500 ? (ctx.sessionDownloaded.get() * 1000 / elapsed) : 0;
				ctx.cb.onProgress(progress, speed);
			}
		}
	}

	private void handleTaskError(TaskContext ctx, Exception e) {
		if (ctx.isInactive() && !isExpiredError(e)) return;
		
		Log.e(TAG, "Task failed: " + ctx.key + " - " + e.getMessage());
		tasks.remove(ctx.key);
		ctx.future.completeExceptionally(e);
		if (ctx.cb != null) ctx.cb.onError(e);
	}

	private boolean isExpiredError(Exception e) {
		String m = e.getMessage();
		return m != null && (m.contains("expired") || m.contains("URL expired") || m.contains("403") || m.contains("410"));
	}

	private void cleanupState(String stateKey, int numBlocks) {
		mmkv.removeValueForKey(stateKey + "_total");
		mmkv.removeValueForKey(stateKey + "_bits");
		mmkv.removeValueForKey(stateKey + "_range");
		for (int i = 0; i < numBlocks; i++) {
			mmkv.removeValueForKey(stateKey + "_p_" + i);
		}
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
			ctx.error.set(null);
			executor.execute(() -> runTask(ctx));
		}
	}

	@Override
	public void cancel(@NonNull String key) {
		TaskContext ctx = tasks.remove(key);
		if (ctx != null) {
			ctx.cancelled.set(true);
			ctx.future.cancel(true);
			if (ctx.cb != null) ctx.cb.onCancel();
		}
	}

	@Override
	public void setMaxThreadCount(int count) {
		int threads = Math.max(2, Math.min(count, 32));
		executor.setCorePoolSize(threads);
		executor.setMaximumPoolSize(threads * 2);
	}

	private static class TaskContext {
		final String key;
		@NonNull String url;
		final File out;
		@NonNull CompletableFuture<File> future;
		@Nullable ProgressCallback cb;
		final AtomicBoolean paused = new AtomicBoolean(false);
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final AtomicLong downloadedBytes = new AtomicLong(0);
		final AtomicLong sessionDownloaded = new AtomicLong(0);
		final AtomicLong startTime = new AtomicLong(0);
		final AtomicLong lastReportTime = new AtomicLong(0);
		final AtomicInteger lastProgress = new AtomicInteger(-1);
		final Object stateLock = new Object();
		final AtomicReference<Exception> error = new AtomicReference<>();

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
