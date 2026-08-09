package com.codelabsk.litepipe.downloader.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tencent.mmkv.MMKV;

/**
 * Preference storage for download settings.
 */
public final class DownloadPrefs {
	private static final String KEY_VID_ON = "download_single_video_enabled";
	private static final String KEY_AUD_ON = "download_single_audio_enabled";
	private static final String KEY_SUB_ON = "download_subtitle_enabled";
	private static final String KEY_THUMB_ON = "download_thumbnail_enabled";
	private static final String KEY_SUB_LANG = "download_subtitle_language";
	private static final String KEY_MEDIA_MODE = "download_primary_media_mode";
	private static final String KEY_THREADS = "download_thread_count";
	private static final String KEY_MAX_CONCURRENT = "download_max_concurrent";
	public static final String KEY_CLIPBOARD_DOWNLOAD = "download_clipboard_detection_enabled";
	private static final int DEF_THREADS = 4;
	private static final int DEF_MAX_CONCURRENT = 2;

	@NonNull
	private final MMKV kv;

	public DownloadPrefs(@NonNull MMKV kv) {
		this.kv = kv;
	}

	public boolean isSingleVideoEnabled() {
		return kv.decodeBool(KEY_VID_ON, false);
	}

	public boolean isSingleAudioEnabled() {
		return kv.decodeBool(KEY_AUD_ON, false);
	}

	public boolean isSubtitleEnabled() {
		return kv.decodeBool(KEY_SUB_ON, false);
	}

	public void setSubtitleEnabled(boolean enabled) {
		kv.encode(KEY_SUB_ON, enabled);
	}

	public boolean isThumbnailEnabled() {
		return kv.decodeBool(KEY_THUMB_ON, false);
	}

	public void setThumbnailEnabled(boolean enabled) {
		kv.encode(KEY_THUMB_ON, enabled);
	}

	@Nullable
	public String getSubLang() {
		return kv.decodeString(KEY_SUB_LANG, null);
	}

	@NonNull
	public DownloadSelectionConfig.PrimaryMediaMode getPrimaryMediaMode() {
		return readMediaMode();
	}

	public void setPrimaryMediaMode(@NonNull DownloadSelectionConfig.PrimaryMediaMode mode) {
		kv.encode(KEY_MEDIA_MODE, mode.ordinal());
	}

	@NonNull
	private DownloadSelectionConfig.PrimaryMediaMode readMediaMode() {
		int ordinal = kv.decodeInt(KEY_MEDIA_MODE, DownloadSelectionConfig.PrimaryMediaMode.VIDEO.ordinal());
		DownloadSelectionConfig.PrimaryMediaMode[] modes = DownloadSelectionConfig.PrimaryMediaMode.values();
		return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : DownloadSelectionConfig.PrimaryMediaMode.VIDEO;
	}

	public int getThreadCount() {
		return Math.max(1, kv.decodeInt(KEY_THREADS, DEF_THREADS));
	}

	public void setThreadCount(int threadCount) {
		kv.encode(KEY_THREADS, Math.max(1, threadCount));
	}

	public int getMaxConcurrentDownloads() {
		return Math.max(1, kv.decodeInt(KEY_MAX_CONCURRENT, DEF_MAX_CONCURRENT));
	}

	public boolean isClipboardDetectionEnabled() {
		return kv.decodeBool(KEY_CLIPBOARD_DOWNLOAD, true);
	}
}
