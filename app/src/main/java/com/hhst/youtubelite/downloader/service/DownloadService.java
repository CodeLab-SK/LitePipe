package com.hhst.youtubelite.downloader.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.R;
import com.hhst.youtubelite.downloader.core.LiteDownloader;
import com.hhst.youtubelite.downloader.core.ProgressCallback2;
import com.hhst.youtubelite.downloader.core.Task;
import com.hhst.youtubelite.downloader.core.history.DownloadHistoryRepository;
import com.hhst.youtubelite.downloader.core.history.DownloadRecord;
import com.hhst.youtubelite.downloader.core.history.DownloadStatus;
import com.hhst.youtubelite.downloader.core.history.DownloadType;
import com.hhst.youtubelite.extractor.PlaybackDetails;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.ui.MainActivity;
import com.hhst.youtubelite.util.DownloadStorageUtils;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@UnstableApi
@AndroidEntryPoint
public class DownloadService extends Service {

	public static final String ACTION_DOWNLOAD_RECORD_UPDATED = "com.hhst.youtubelite.action.DOWNLOAD_RECORD_UPDATED";
	public static final String EXTRA_TASK_ID = "extra_task_id";

	public static final String ACTION_PAUSE = "com.hhst.youtubelite.action.PAUSE";
	public static final String ACTION_RESUME = "com.hhst.youtubelite.action.RESUME";
	public static final String ACTION_CANCEL = "com.hhst.youtubelite.action.CANCEL";

	private static final String CHANNEL_ID = "download_channel";
	private static final String GROUP_KEY_DOWNLOADS = "com.hhst.youtubelite.DOWNLOAD_GROUP";
	private static final int SUMMARY_ID = 1001;
	private static final long FAILED_TEMP_CLEANUP_THRESHOLD = TimeUnit.DAYS.toMillis(1);

	private final Set<String> activeTaskIds = ConcurrentHashMap.newKeySet();
	private final Map<String, String> activeTaskNames = new ConcurrentHashMap<>();
	private final Map<String, Task> activeTasks = new ConcurrentHashMap<>();
	private final Map<String, Integer> activeProgress = new ConcurrentHashMap<>();
	private final Map<String, Long> activeSpeeds = new ConcurrentHashMap<>();
	private final Map<String, Long> activeDownloaded = new ConcurrentHashMap<>();
	private final Map<String, Long> activeTotal = new ConcurrentHashMap<>();
	private final Map<String, DownloadStatus> activeStatuses = new ConcurrentHashMap<>();
	private final Map<String, Long> lastBroadcastTimes = new ConcurrentHashMap<>();

	private SharedPreferences itagPrefs;
	private boolean isForeground = false;

	@Inject
	LiteDownloader liteDL;
	@Inject
	DownloadHistoryRepository historyRepository;
	@Inject
	YoutubeExtractor youtubeExtractor;

	private NotificationManager notificationManager;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	@Override
	public void onCreate() {
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		itagPrefs = getSharedPreferences("download_itags", Context.MODE_PRIVATE);
		createNotificationChannel();
		cleanupOldFailedTemps();
		mainHandler.postDelayed(this::resumeRunningTasks, 1000);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			String taskId = intent.getStringExtra(EXTRA_TASK_ID);
			if (action != null && taskId != null) {
				switch (action) {
					case ACTION_PAUSE:
						pause(taskId);
						break;
					case ACTION_RESUME:
						resume(taskId);
						break;
					case ACTION_CANCEL:
						cancel(taskId);
						break;
				}
			}
		}
		return START_STICKY;
	}

	private void cleanupOldFailedTemps() {
		new Thread(() -> {
			long now = System.currentTimeMillis();
			List<DownloadRecord> all = historyRepository.getAllSorted();
			for (DownloadRecord r : all) {
				if (r.getStatus() == DownloadStatus.FAILED && (now - r.getUpdatedAt() > FAILED_TEMP_CLEANUP_THRESHOLD)) {
					String baseName = r.getFileName();
					File cacheDir = getCacheDir();
					deleteFile(new File(cacheDir, baseName + "_v.tmp"));
					deleteFile(new File(cacheDir, baseName + "_a.tmp"));
					deleteFile(new File(cacheDir, baseName + "_m.tmp"));
				}
			}
		}).start();
	}

	private void resumeRunningTasks() {
		new Thread(() -> {
			List<DownloadRecord> all = historyRepository.getAllSorted();
			for (DownloadRecord r : all) {
				if (r.getStatus() == DownloadStatus.RUNNING || r.getStatus() == DownloadStatus.QUEUED || r.getStatus() == DownloadStatus.MERGING) {
					mainHandler.post(() -> resume(r.getTaskId()));
				}
			}
		}).start();
	}

	private void deleteFile(File file) {
		if (file.exists()) {
			if (!file.delete()) {
				Log.w("DownloadService", "Failed to delete file: " + file.getAbsolutePath());
			}
		}
	}

	private void createNotificationChannel() {
		NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
		channel.setSound(null, null);
		if (notificationManager != null) notificationManager.createNotificationChannel(channel);
	}

	@Nullable
	@Override
	public IBinder onBind(@NonNull final Intent intent) {
		return new DownloadBinder();
	}

	public void download(@NonNull List<Task> tasks) {
		if (tasks.isEmpty()) return;
		for (Task task : tasks) {
			startTask(task);
		}
	}

	public void addPlaylistRecord(DownloadRecord record) {
		historyRepository.upsert(record);
		broadcastRecordUpdated(record.getTaskId(), true);
	}

	private void startTask(@NonNull Task task) {
		final String taskId = task.videoId();
		activeTasks.put(taskId, task);
		activeTaskIds.add(taskId);
		activeTaskNames.put(taskId, task.fileName());
		saveItags(task);

		DownloadRecord record = historyRepository.findByTaskId(taskId);
		long now = System.currentTimeMillis();

		DownloadType type;
		String ext;
		if (task.video() != null) {
			type = DownloadType.VIDEO;
			ext = ".mp4";
		} else if (task.audio() != null) {
			type = DownloadType.AUDIO;
			ext = ".m4a";
		} else if (task.subtitle() != null) {
			type = DownloadType.SUBTITLE;
			ext = "." + task.subtitle().getExtension();
		} else {
			type = DownloadType.THUMBNAIL;
			ext = ".jpg";
		}

		String outPath = new File(task.desDir(), task.fileName() + ext).getAbsolutePath();

		if (record == null) {
			record = new DownloadRecord(taskId, taskId, type, DownloadStatus.RUNNING, 0,
							task.fileName(), outPath, now, now, null, 0L, 0L, task.parentId(), task.title(), task.thumbnailUrl(), 0, 0, 0, 0, false, task.quality(), task.threadCount(), 0);
		} else {
			record.setStatus(DownloadStatus.RUNNING);
			record.setUpdatedAt(now);
			record.setFileName(task.fileName());
			record.setOutputPath(outPath);
			record.setType(type);
			record.setTitle(task.title());
			record.setThumbnailUrl(task.thumbnailUrl());
			record.setQuality(task.quality());
			record.setParentId(task.parentId());
			record.setThreadCount(task.threadCount());
		}
		historyRepository.upsert(record);
		broadcastRecordUpdated(taskId, true);

		activeDownloaded.put(taskId, record.getDownloadedSize());
		activeTotal.put(taskId, record.getTotalSize());
		activeStatuses.put(taskId, record.getStatus());
		activeProgress.put(taskId, record.getProgress());

		attachCallback(taskId);
		
		ensureForeground();
		updateNotification(taskId);
		
		liteDL.download(task);
	}

	private void attachCallback(final String taskId) {
		liteDL.setCallback(taskId, new ProgressCallback2() {
			@Override
			public void onProgress(int progress, long d, long t, long speed) {
				updateRecordProgress(taskId, progress, d, t, speed);
				activeProgress.put(taskId, progress);
				activeSpeeds.put(taskId, speed);
				activeDownloaded.put(taskId, d);
				activeTotal.put(taskId, t);
				activeStatuses.put(taskId, DownloadStatus.RUNNING);
				updateNotification(taskId);
			}

			@Override
			public void onComplete(File file) {
				final long fileSize = file.length();
				try {
					Task task = activeTasks.get(taskId);
					String subFolder = task != null ? task.subFolder() : null;
					final String outputReference = DownloadStorageUtils.publishToDownloads(DownloadService.this, file, file.getName(), subFolder);
					markRecordCompleted(taskId, outputReference, fileSize);
					onTaskCompleted(taskId, file.getName(), true);
				} catch (Exception e) {
					Log.e("DownloadService", "Post-download publish failed", e);
					updateRecordStatus(taskId, DownloadStatus.FAILED);
					onTaskCompleted(taskId, getTaskFileName(taskId), false);
				}
			}

			@Override
			public void onError(Exception error) {
				Log.e("DownloadService", "Task error: " + taskId, error);
				updateRecordStatus(taskId, DownloadStatus.FAILED);
				onTaskCompleted(taskId, getTaskFileName(taskId), false);
			}

			@Override
			public void onCancel() {
				updateRecordStatus(taskId, DownloadStatus.CANCELED);
				onTaskCancelled(taskId);
			}

			@Override
			public void onMerge() {
				updateRecordStatus(taskId, DownloadStatus.MERGING);
				activeStatuses.put(taskId, DownloadStatus.MERGING);
				updateNotification(taskId);
			}
		});
	}

	private void updateRecordProgress(String taskId, int p, long d, long t, long speed) {
		DownloadRecord record = historyRepository.findByTaskId(taskId);
		if (record != null) {
			if (p >= 0) record.setProgress(p);
			if (d >= 0) record.setDownloadedSize(d);
			if (t >= 0) record.setTotalSize(t);
			record.setStatus(DownloadStatus.RUNNING);
			record.setSpeed(speed);
			record.setUpdatedAt(System.currentTimeMillis());
			historyRepository.upsert(record);
			broadcastRecordUpdated(taskId, false);
		}
	}

	private void markRecordCompleted(@NonNull final String taskId, @NonNull final String outputReference, final long fileSize) {
		DownloadRecord record = historyRepository.findByTaskId(taskId);
		if (record == null) return;
		record.setProgress(100);
		record.setDownloadedSize(fileSize);
		record.setTotalSize(fileSize);
		record.setOutputPath(outputReference);
		record.setStatus(DownloadStatus.COMPLETED);
		record.setSpeed(0);
		record.setUpdatedAt(System.currentTimeMillis());
		historyRepository.upsert(record);
		broadcastRecordUpdated(taskId, true);
	}

	private void updateRecordStatus(String taskId, DownloadStatus status) {
		DownloadRecord record = historyRepository.findByTaskId(taskId);
		if (record != null) {
			record.setStatus(status);
			record.setSpeed(0);
			record.setUpdatedAt(System.currentTimeMillis());
			historyRepository.upsert(record);
			broadcastRecordUpdated(taskId, true);
		}
	}

	public void pause(@NonNull String vid) {
		liteDL.pause(vid);
		updateRecordStatus(vid, DownloadStatus.PAUSED);
		activeTaskIds.remove(vid);
		activeProgress.remove(vid);
		activeSpeeds.remove(vid);
		activeDownloaded.remove(vid);
		activeTotal.remove(vid);
		activeStatuses.remove(vid);
		
		notificationManager.cancel(vid.hashCode());
		updateNotification(null);
	}

	public void resume(@NonNull String vid) {
		updateRecordStatus(vid, DownloadStatus.QUEUED);
		DownloadRecord record = historyRepository.findByTaskId(vid);
		if (record != null) {
			ensureForeground();
			updateNotification(vid);
			new Thread(() -> reExtractAndResume(record)).start();
		}
	}

	private void reExtractAndResume(DownloadRecord record) {
		try {
			PlaybackDetails details = youtubeExtractor.getPlaybackDetails("https://www.youtube.com/watch?v=" + record.getVideoId(), null);
			int vItag = itagPrefs.getInt(record.getTaskId() + "_v_itag", -1);
			int aItag = itagPrefs.getInt(record.getTaskId() + "_a_itag", -1);
			String aTrack = itagPrefs.getString(record.getTaskId() + "_a_track", null);
			String aLang = itagPrefs.getString(record.getTaskId() + "_a_lang", null);

			VideoStream video = details.catalog().getVideoStreams().stream()
							.filter(s -> s.getItag() == vItag)
							.findFirst()
							.orElse(null);

			AudioStream audio = details.catalog().getAudioStreams().stream()
							.filter(s -> {
								if (s.getItag() != aItag) return false;
								if (aTrack != null) {
									return aTrack.equals(s.getAudioTrackName());
								}
								if (aLang != null) {
									return s.getAudioLocale() != null && aLang.equals(s.getAudioLocale().getLanguage());
								}
								return true;
							})
							.findFirst()
							.orElse(null);

			if (video == null && !details.catalog().getVideoStreams().isEmpty()) video = details.catalog().getVideoStreams().get(0);
			if (audio == null && !details.catalog().getAudioStreams().isEmpty()) audio = details.catalog().getAudioStreams().get(0);

			File outParent = new File(record.getOutputPath()).getParentFile();
			if (outParent == null) outParent = new File(getExternalCacheDir(), "downloads");

			int threads = record.getThreadCount() > 0 ? record.getThreadCount() : 4;
			Task newTask = new Task(record.getTaskId(), video, audio, null, null, record.getFileName(),
							outParent, threads, record.getTitle(), record.getThumbnailUrl(), record.getQuality(), record.getParentId(), null);

			mainHandler.post(() -> startTask(newTask));
		} catch (Exception e) {
			Log.e("DownloadService", "Resume extraction failed", e);
			mainHandler.post(() -> {
				updateRecordStatus(record.getTaskId(), DownloadStatus.FAILED);
				onTaskCompleted(record.getTaskId(), record.getFileName(), false);
			});
		}
	}

	public void cancel(@NonNull String vid) {
		liteDL.cancel(vid);
		updateRecordStatus(vid, DownloadStatus.CANCELED);
		onTaskCancelled(vid);
		broadcastRecordUpdated(vid, true);
	}

	private synchronized void ensureForeground() {
		if (isForeground) return;
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
						.setSmallIcon(R.drawable.ic_download)
						.setContentTitle("YouTube Lite Downloader")
						.setContentText("Initializing...")
						.setPriority(NotificationCompat.PRIORITY_LOW)
						.setSilent(true)
						.setOngoing(true)
						.setGroup(GROUP_KEY_DOWNLOADS)
						.setGroupSummary(true)
						.setContentIntent(createContentIntent());
		
		startForeground(SUMMARY_ID, builder.build());
		isForeground = true;
	}

	private synchronized void updateNotification(@Nullable String changedTaskId) {
		if (activeTaskIds.isEmpty()) {
			handleNoActiveDownloads();
			return;
		}

		long totalDownloaded = 0;
		long totalSize = 0;
		long totalSpeed = 0;
		int count = activeTaskIds.size();

		for (String tid : activeTaskIds) {
			Long downloaded = activeDownloaded.get(tid);
			totalDownloaded += downloaded != null ? downloaded : 0L;
			Long size = activeTotal.get(tid);
			totalSize += size != null ? size : 0L;
			Long speed = activeSpeeds.get(tid);
			totalSpeed += speed != null ? speed : 0L;
		}

		int avgProgress = (totalSize > 0) ? (int) (totalDownloaded * 100 / totalSize) : 0;
		String summaryText = String.format(Locale.getDefault(), "Total: %s • %s",
						formatSpeed(totalSpeed), calculateETA(totalSize, totalDownloaded, totalSpeed));

		NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
						.setSmallIcon(R.drawable.ic_download)
						.setContentTitle(count + (count > 1 ? " downloads" : " download") + " in progress")
						.setContentText(summaryText)
						.setPriority(NotificationCompat.PRIORITY_LOW)
						.setSilent(true)
						.setOngoing(true)
						.setGroup(GROUP_KEY_DOWNLOADS)
						.setGroupSummary(true)
						.setProgress(100, avgProgress, totalSize <= 0)
						.setContentIntent(createContentIntent());

		notificationManager.notify(SUMMARY_ID, summaryBuilder.build());

		if (changedTaskId != null) {
			updateIndividualNotification(changedTaskId);
		} else {
			for (String tid : activeTaskIds) {
				updateIndividualNotification(tid);
			}
		}
	}

	private void updateIndividualNotification(String taskId) {
		String name = activeTaskNames.get(taskId);
		Integer progress = activeProgress.get(taskId);
		Long boxedSpeed = activeSpeeds.get(taskId);
		long speed = boxedSpeed != null ? boxedSpeed : 0L;
		DownloadStatus status = activeStatuses.getOrDefault(taskId, DownloadStatus.RUNNING);
		Long boxedDownloaded = activeDownloaded.get(taskId);
		long downloaded = boxedDownloaded != null ? boxedDownloaded : 0L;
		Long boxedTotal = activeTotal.get(taskId);
		long total = boxedTotal != null ? boxedTotal : 0L;

		boolean isMerging = status == DownloadStatus.MERGING;

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
						.setSmallIcon(R.drawable.ic_download)
						.setContentTitle(isMerging ? "Merging: " + name : name)
						.setGroup(GROUP_KEY_DOWNLOADS)
						.setPriority(NotificationCompat.PRIORITY_LOW)
						.setSilent(true)
						.setOngoing(true)
						.setContentIntent(createContentIntent());

		String speedText = formatSpeed(speed);
		String etaText = calculateETA(total, downloaded, speed);
		String contentText = isMerging ? "Processing files..." : String.format(Locale.getDefault(), "%s • %s", speedText, etaText);
		
		builder.setContentText(contentText);
		builder.setProgress(100, progress != null ? progress : 0, isMerging || total <= 0);

		String bigText = String.format(Locale.getDefault(), 
						"%s: (%s/%s) • %s • %s",
						isMerging ? "Merging" : "Downloading",
						formatSize(downloaded),
						formatSize(total),
						speedText,
						etaText);
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));

		builder.addAction(R.drawable.ic_pause, "Pause", createActionIntent(ACTION_PAUSE, taskId));
		builder.addAction(R.drawable.ic_close, "Cancel", createActionIntent(ACTION_CANCEL, taskId));

		notificationManager.notify(taskId.hashCode(), builder.build());
	}

	private void handleNoActiveDownloads() {
		boolean hasPaused = !activeTasks.isEmpty();
		if (hasPaused) {
			NotificationCompat.Builder pausedBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
							.setSmallIcon(R.drawable.ic_download)
							.setContentTitle("Downloads paused")
							.setContentText("Tap to resume")
							.setProgress(0, 0, false)
							.setOngoing(false)
							.setAutoCancel(true)
							.setContentIntent(createContentIntent());
			
			notificationManager.notify(SUMMARY_ID, pausedBuilder.build());
			stopForeground(STOP_FOREGROUND_DETACH);
			isForeground = false;
		} else {
			stopForeground(STOP_FOREGROUND_DETACH);
			isForeground = false;
			notificationManager.cancel(SUMMARY_ID);
		}
	}

	private String formatSpeed(long bytesPerSec) {
		if (bytesPerSec < 1024) return bytesPerSec + " B/s";
		if (bytesPerSec < 1048576) return String.format(Locale.getDefault(), "%.1f KB/s", bytesPerSec / 1024.0);
		return String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / 1048576.0);
	}

	private String formatSize(long bytes) {
		if (bytes <= 0) return "0 B";
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1048576) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
		if (bytes < 1073741824) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
		return String.format(Locale.getDefault(), "%.1f GB", bytes / 1073741824.0);
	}

	private String calculateETA(long total, long downloaded, long speed) {
		if (speed <= 0 || total <= 0) return "--:--";
		long remainingBytes = total - downloaded;
		if (remainingBytes <= 0) return "0s";
		long seconds = remainingBytes / speed;
		if (seconds < 60) return seconds + "s";
		if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
		return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
	}

	private PendingIntent createActionIntent(String action, String taskId) {
		Intent intent = new Intent(this, DownloadService.class);
		intent.setAction(action);
		intent.putExtra(EXTRA_TASK_ID, taskId);
		return PendingIntent.getService(this, taskId.hashCode() + action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
	}

	private synchronized void onTaskCompleted(@NonNull final String taskId, @NonNull final String fileName, final boolean success) {
		activeTaskIds.remove(taskId);
		activeTaskNames.remove(taskId);
		activeTasks.remove(taskId);
		activeProgress.remove(taskId);
		activeSpeeds.remove(taskId);
		activeDownloaded.remove(taskId);
		activeTotal.remove(taskId);
		activeStatuses.remove(taskId);
		lastBroadcastTimes.remove(taskId);
		
		notificationManager.cancel(taskId.hashCode());
		
		if (activeTaskIds.isEmpty()) finalizeNotification(fileName, success);
		else updateNotification(null);
	}

	private synchronized void onTaskCancelled(@NonNull final String taskId) {
		activeTaskIds.remove(taskId);
		activeTaskNames.remove(taskId);
		activeTasks.remove(taskId);
		activeProgress.remove(taskId);
		activeSpeeds.remove(taskId);
		activeDownloaded.remove(taskId);
		activeTotal.remove(taskId);
		activeStatuses.remove(taskId);
		lastBroadcastTimes.remove(taskId);
		
		notificationManager.cancel(taskId.hashCode());
		
		if (activeTaskIds.isEmpty()) {
			stopForeground(STOP_FOREGROUND_REMOVE);
			isForeground = false;
			if (notificationManager != null) notificationManager.cancel(SUMMARY_ID);
		} else updateNotification(null);
	}

	private synchronized void finalizeNotification(String fileName, boolean success) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
						.setSmallIcon(R.drawable.ic_download)
						.setOngoing(false)
						.setAutoCancel(true)
						.setSilent(false)
						.setProgress(0, 0, false)
						.setContentTitle(success ? "Download Finished" : "Download Failed")
						.setContentText(fileName)
						.setContentIntent(createContentIntent());
		
		notificationManager.notify(SUMMARY_ID, builder.build());
		stopForeground(STOP_FOREGROUND_DETACH);
		isForeground = false;
	}

	private void saveItags(Task t) {
		SharedPreferences.Editor e = itagPrefs.edit();
		if (t.video() != null) e.putInt(t.videoId() + "_v_itag", t.video().getItag());
		if (t.audio() != null) {
			e.putInt(t.videoId() + "_a_itag", t.audio().getItag());
			e.putString(t.videoId() + "_a_track", t.audio().getAudioTrackName());
			if (t.audio().getAudioLocale() != null) e.putString(t.videoId() + "_a_lang", t.audio().getAudioLocale().getLanguage());
			else e.remove(t.videoId() + "_a_lang");
		}
		e.apply();
	}

	private String getTaskFileName(String id) {
		String name = activeTaskNames.get(id);
		if (name != null) return name;
		DownloadRecord record = historyRepository.findByTaskId(id);
		return record != null ? record.getFileName() : "Download";
	}

	private void broadcastRecordUpdated(String tid, boolean force) {
		if (!force) {
			long now = System.currentTimeMillis();
			Long last = lastBroadcastTimes.get(tid);
			if (last != null && now - last < 800) return;
			lastBroadcastTimes.put(tid, now);
		}
		Intent intent = new Intent(ACTION_DOWNLOAD_RECORD_UPDATED);
		intent.putExtra(EXTRA_TASK_ID, tid);
		intent.setPackage(getPackageName());
		sendBroadcast(intent);
	}

	private PendingIntent createContentIntent() {
		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction("OPEN_DOWNLOADS");
		return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
	}

	public class DownloadBinder extends Binder {
		public DownloadService getService() {
			return DownloadService.this;
		}
	}
}
