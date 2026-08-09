package com.codelabsk.litepipe.downloader.core.impl;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.codelabsk.litepipe.downloader.core.DownloadPrefs;
import com.codelabsk.litepipe.downloader.core.LiteDownloader;
import com.codelabsk.litepipe.downloader.core.MediaMuxer;
import com.codelabsk.litepipe.downloader.core.ProgressCallback;
import com.codelabsk.litepipe.downloader.core.ProgressCallback2;
import com.codelabsk.litepipe.downloader.core.StreamDownloader;
import com.codelabsk.litepipe.downloader.core.Task;
import com.codelabsk.litepipe.extractor.YoutubeExtractor;

import org.apache.commons.io.FileUtils;
import org.schabi.newpipe.extractor.stream.Stream;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class LiteDownloaderImpl implements LiteDownloader {
	private final Context ctx;
	private final StreamDownloader streamDL;
	private final YoutubeExtractor youtubeExtractor;
	private final MediaMerger mediaMerger;
	private final DownloadPrefs prefs;
	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final Map<String, Task> tasks = new ConcurrentHashMap<>();
	private final Map<String, ProgressCallback2> cbs = new ConcurrentHashMap<>();
	
	private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();
	private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
	private final AtomicInteger activeCount = new AtomicInteger(0);

	@Inject
	public LiteDownloaderImpl(@ApplicationContext Context ctx,
	                          StreamDownloader streamDL,
	                          YoutubeExtractor youtubeExtractor,
	                          DownloadPrefs prefs) {
		this(ctx, streamDL, youtubeExtractor, MediaMuxer::merge, prefs);
	}

	LiteDownloaderImpl(@ApplicationContext Context ctx,
	                   StreamDownloader streamDL,
	                   YoutubeExtractor youtubeExtractor,
	                   MediaMerger mediaMerger,
	                   DownloadPrefs prefs) {
		this.ctx = ctx;
		this.streamDL = streamDL;
		this.youtubeExtractor = youtubeExtractor;
		this.mediaMerger = mediaMerger;
		this.prefs = prefs;
	}

	@Override
	public void setCallback(@NonNull String videoId, ProgressCallback2 cb) {
		if (cb != null) cbs.put(videoId, cb);
		else cbs.remove(videoId);
	}

	@Override
	public synchronized void download(@NonNull Task t) {
		final boolean exists = tasks.containsKey(t.videoId());
		tasks.put(t.videoId(), t);
		
		if (runningTasks.contains(t.videoId())) {
			// If already running, we still want to update the URLs in streamDL
			resume(t.videoId());
			return;
		}

		if (!pendingTasks.contains(t)) {
			pendingTasks.offer(t);
		}
		checkQueue();
	}

	private void checkQueue() {
		while (activeCount.get() < prefs.getMaxConcurrentDownloads() && !pendingTasks.isEmpty()) {
			Task t = pendingTasks.poll();
			if (t != null) {
				if (runningTasks.add(t.videoId())) {
					activeCount.incrementAndGet();
					startDownloadInternal(t);
				}
			}
		}
	}

	private void startDownloadInternal(Task t) {
		if (t.subtitle() != null) {
			exec(t, () -> FileUtils.copyURLToFile(new URL(t.subtitle().getContent()), outputFile(t)));
		} else if (t.thumbnail() != null) {
			exec(t, () -> FileUtils.copyURLToFile(new URL(t.thumbnail()), outputFile(t)));
		} else {
			downloadMedia(t);
		}
	}

	private void exec(Task t, RunnableIOC r) {
		CompletableFuture.runAsync(() -> {
			try {
				r.run();
				complete(t.videoId(), outputFile(t));
			} catch (Exception e) {
				throw new CompletionException(e);
			} finally {
				taskFinished(t.videoId());
			}
		}, executor).exceptionally(e -> handleErr(t, e));
	}

	private void downloadMedia(Task t) {
		streamDL.setMaxThreadCount(t.threadCount());
		File vF = tmp(t, "_v"), aF = tmp(t, "_a"), out = outputFile(t);
		long vSz = len(t.video()), aSz = len(t.audio());
		Aggregator agg = new Aggregator(vSz, aSz, (p, d, tot, spd) -> progress(t.videoId(), p, d, tot, spd));

		CompletableFuture<File> vFut = t.video() == null ? null : streamDL.download(t.videoId() + "_v", t.video().getContent(), vF, createProgressAdapter(t.videoId(), (p, spd) -> {
			if (aSz > 0) agg.updV(p, spd);
			else progress(t.videoId(), p, (long) (vSz * (p / 100.0)), vSz, spd);
		}));

		CompletableFuture<File> aFut = t.audio() == null ? null : streamDL.download(t.videoId() + "_a", t.audio().getContent(), aF, createProgressAdapter(t.videoId(), (p, spd) -> {
			if (vSz > 0) agg.updA(p, spd);
			else progress(t.videoId(), p, (long) (aSz * (p / 100.0)), aSz, spd);
		}));

		CompletableFuture<?> combined = (vFut != null && aFut != null ? CompletableFuture.allOf(vFut, aFut) : (vFut != null ? vFut : aFut));
		if (combined != null) {
			combined.thenRun(() -> {
				try {
					if (!tasks.containsKey(t.videoId()) || !runningTasks.contains(t.videoId())) return;
					if (vFut != null && aFut != null) {
						notify(t.videoId(), ProgressCallback2::onMerge);
						File mF = tmp(t, "_m");
						try {
							mediaMerger.merge(vF, aF, mF);
							if (out.exists()) out.delete();
							FileUtils.moveFile(mF, out);
						} finally {
							FileUtils.deleteQuietly(vF);
							FileUtils.deleteQuietly(aF);
							FileUtils.deleteQuietly(mF);
						}
					} else {
						if (out.exists()) out.delete();
						FileUtils.moveFile(vFut != null ? vF : aF, out);
					}
					complete(t.videoId(), out);
				} catch (Exception e) {
					throw new CompletionException(e);
				} finally {
					taskFinished(t.videoId());
				}
			}).exceptionally(e -> {
				taskFinished(t.videoId());
				return handleErr(t, e);
			});
		}
	}

	private void taskFinished(String videoId) {
		if (runningTasks.remove(videoId)) {
			activeCount.decrementAndGet();
			checkQueue();
		}
	}

	@Override
	public void pause(@NonNull String videoId) {
		Task t = tasks.get(videoId);
		if (t != null) {
			if (t.video() != null) streamDL.pause(videoId + "_v");
			if (t.audio() != null) streamDL.pause(videoId + "_a");
			taskFinished(videoId);
		}
	}

	@Override
	public void resume(@NonNull String videoId) {
		Task t = tasks.get(videoId);
		if (t != null) {
			if (!runningTasks.contains(videoId)) {
				if (!pendingTasks.contains(t)) {
					pendingTasks.offer(t);
				}
				checkQueue();
			}
			if (t.video() != null) streamDL.resume(videoId + "_v");
			if (t.audio() != null) streamDL.resume(videoId + "_a");
			progress(videoId, -1, -1, -1, 0);
		}
	}

	@Override
	public void cancel(@NonNull String videoId) {
		Task t = tasks.remove(videoId);
		try {
			if (t == null) return;
			if (t.video() != null) streamDL.cancel(videoId + "_v");
			if (t.audio() != null) streamDL.cancel(videoId + "_a");
			notify(videoId, ProgressCallback2::onCancel);
			clean(t);
		} finally {
			taskFinished(videoId);
			clearCallback(videoId);
		}
	}

	private ProgressCallback createProgressAdapter(String videoId, java.util.function.BiConsumer<Integer, Long> action) {
		return new ProgressCallback() {
			@Override public void onProgress(int p, long spd) { action.accept(p, spd); }
			@Override public void onComplete(File f) {}
			@Override public void onError(Exception e) {}
			@Override public void onCancel() {}
		};
	}

	private Void handleErr(Task t, Throwable e) {
		Throwable c = e instanceof CompletionException ? e.getCause() : e;
		try {
			invalidatePlaybackCacheIfLikelyExpiredStream(t, c);
			if (tasks.containsKey(t.videoId())) {
				notify(t.videoId(), cb -> cb.onError(c instanceof Exception ? (Exception) c : new Exception(c)));
				tasks.remove(t.videoId());
			}
		} finally {
			taskFinished(t.videoId());
			clearCallback(t.videoId());
		}
		return null;
	}

	void invalidatePlaybackCacheIfLikelyExpiredStream(@NonNull final Task task,
	                                                  @Nullable final Throwable throwable) {
		if (task.video() == null && task.audio() == null) return;
		if (!isLikelyExpiredStreamError(throwable)) return;
		final String videoId = rawVideoId(task.videoId());
		if (videoId.isEmpty()) return;
		youtubeExtractor.invalidatePlaybackCacheByVideoId(videoId);
	}

	static boolean isLikelyExpiredStreamError(@Nullable final Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			final String message = current.getMessage();
			if (message != null && (message.contains(" 401")
							|| message.contains(" 403")
							|| message.contains(" 404")
							|| message.contains(" 410"))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	@NonNull
	static String rawVideoId(@NonNull final String taskId) {
		final int suffixIndex = taskId.lastIndexOf(':');
		return suffixIndex >= 0 ? taskId.substring(0, suffixIndex) : taskId;
	}

	private void complete(String videoId, File f) {
		try {
			if (tasks.remove(videoId) != null) notify(videoId, cb -> cb.onComplete(f));
		} finally {
			clearCallback(videoId);
		}
	}

	private void progress(String videoId, int p, long downloaded, long total, long speed) {
		notify(videoId, cb -> cb.onProgress(p, downloaded, total, speed));
	}

	private void notify(String videoId, CallbackAction action) {
		ProgressCallback2 cb = cbs.get(videoId);
		if (cb != null) action.run(cb);
	}

	private void clearCallback(@NonNull final String videoId) {
		cbs.remove(videoId);
	}

	private void clean(Task t) {
		if (t == null) return;
		if (t.video() != null) FileUtils.deleteQuietly(tmp(t, "_v"));
		if (t.audio() != null) FileUtils.deleteQuietly(tmp(t, "_a"));
		if (t.video() != null && t.audio() != null) FileUtils.deleteQuietly(tmp(t, "_m"));
	}

	private File tmp(Task t, String s) {
		return new File(ctx.getCacheDir(), taskFileKey(t) + s + ".tmp");
	}

	private File outputFile(@NonNull final Task task) {
		if (task.subtitle() != null) {
			return new File(task.desDir(), task.fileName() + "." + task.subtitle().getExtension());
		}
		if (task.thumbnail() != null) {
			return new File(task.desDir(), task.fileName() + ".jpg");
		}
		return new File(task.desDir(), task.fileName() + (task.video() != null ? ".mp4" : ".m4a"));
	}

	private String taskFileKey(@NonNull final Task task) {
		return task.videoId().replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	private long len(Stream s) { try { return s.getItagItem().getContentLength(); } catch (Exception e) { return 0; } }

	interface RunnableIOC { void run() throws Exception; }
	interface MediaMerger {
		void merge(@NonNull File videoFile, @NonNull File audioFile, @NonNull File outputFile) throws Exception;
	}
	interface CallbackAction { void run(ProgressCallback2 cb); }
	interface ProgressUpdateListener { void onUpdate(int progress, long downloaded, long total, long speed); }

	private static class Aggregator {
		final long vSz, aSz, tot;
		final ProgressUpdateListener listener;
		int vP, aP;
		long vSpd, aSpd;

		Aggregator(long v, long a, ProgressUpdateListener l) {
			vSz = Math.max(v, 1);
			aSz = Math.max(a, 1);
			tot = vSz + aSz;
			listener = l;
		}

		synchronized void updV(int p, long spd) { vP = p; vSpd = spd; calc(); }
		synchronized void updA(int p, long spd) { aP = p; aSpd = spd; calc(); }

		void calc() {
			int totalProgress = (int) ((vP * vSz + aP * aSz) / tot);
			long downloaded = (long) (vSz * (vP / 100.0) + aSz * (aP / 100.0));
			long totalSpeed = vSpd + aSpd;
			listener.onUpdate(totalProgress, downloaded, tot, totalSpeed);
		}
	}
}
