package com.hhst.youtubelite.downloader.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Configuration for download selection and batching.
 */
public record DownloadSelectionConfig(
				@NonNull PrimaryMediaMode primaryMediaMode,
				boolean subtitleEnabled,
				boolean thumbnailEnabled,
				int threadCount,
				@Nullable String preferredQuality) {

	public DownloadSelectionConfig {
		threadCount = Math.max(1, threadCount);
	}

	public boolean hasAnyOutputEnabled() {
		return primaryMediaMode != PrimaryMediaMode.NONE || subtitleEnabled || thumbnailEnabled;
	}

	/**
	 * Enumeration of app logic.
	 */
	public enum PrimaryMediaMode {
		NONE,
		VIDEO,
		AUDIO
	}
}
