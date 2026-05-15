package com.hhst.youtubelite.downloader.core.history;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class DownloadHistoryRepository {
	public static final String KEY_DOWNLOAD_HISTORY = "download_history";

	private static final Type LIST_TYPE = new TypeToken<List<DownloadRecord>>() {}.getType();

	@NonNull
	private final MMKV mmkv;
	@NonNull
	private final Gson gson;

	private final Map<String, DownloadRecord> cache = new LinkedHashMap<>();
	private boolean initialized = false;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private boolean persistPending = false;
	private final Runnable persistRunnable = this::persistInternal;

	@Inject
	public DownloadHistoryRepository(@NonNull final MMKV mmkv, @NonNull final Gson gson) {
		this.mmkv = mmkv;
		this.gson = gson;
	}

	@NonNull
	public synchronized List<DownloadRecord> getAllSorted() {
		ensureInitialized();
		final List<DownloadRecord> list = new ArrayList<>(cache.values());
		list.sort(Comparator.comparingLong(DownloadRecord::getCreatedAt).reversed());
		return list;
	}

	@Nullable
	public synchronized DownloadRecord findByTaskId(@Nullable final String taskId) {
		if (taskId == null) return null;
		ensureInitialized();
		return cache.get(taskId);
	}

	public synchronized void upsert(@NonNull final DownloadRecord record) {
		ensureInitialized();
		cache.put(record.getTaskId(), record);
		
		if (record.getStatus() == DownloadStatus.RUNNING) {
			schedulePersist();
		} else {
			persistInternal();
		}
	}

	public synchronized void remove(@NonNull final String taskId) {
		ensureInitialized();
		if (cache.remove(taskId) != null) {
			persistInternal();
		}
	}

	public synchronized void removeBatch(@NonNull final Collection<String> taskIds) {
		if (taskIds.isEmpty()) return;
		ensureInitialized();
		boolean removed = false;
		for (String id : taskIds) {
			if (cache.remove(id) != null) {
				removed = true;
			}
		}
		if (removed) {
			persistInternal();
		}
	}

	public synchronized void clear() {
		synchronized (this) {
			cache.clear();
			mainHandler.removeCallbacks(persistRunnable);
			persistPending = false;
		}
		mmkv.removeValueForKey(KEY_DOWNLOAD_HISTORY);
	}

	private void ensureInitialized() {
		if (!initialized) {
			final String json = mmkv.decodeString(KEY_DOWNLOAD_HISTORY, null);
			if (json != null && !json.isBlank()) {
				try {
					final List<DownloadRecord> list = gson.fromJson(json, LIST_TYPE);
					if (list != null) {
						for (DownloadRecord r : list) {
                            cache.put(r.getTaskId(), r);
                        }
					}
				} catch (Exception ignored) {
				}
			}
			initialized = true;
		}
	}

	private void schedulePersist() {
		if (persistPending) return;
		persistPending = true;
		mainHandler.postDelayed(persistRunnable, 5000);
	}

	private void persistInternal() {
		synchronized (this) {
			persistPending = false;
			mainHandler.removeCallbacks(persistRunnable);
			final List<DownloadRecord> list = new ArrayList<>(cache.values());
			mmkv.encode(KEY_DOWNLOAD_HISTORY, gson.toJson(list, LIST_TYPE));
		}
	}
}
