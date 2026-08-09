package com.codelabsk.litepipe.downloader.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;

public record Task(@NonNull String videoId, 
                   @Nullable VideoStream video,
                   @Nullable AudioStream audio, 
                   @Nullable SubtitlesStream subtitle,
                   @Nullable String thumbnail, 
                   @NonNull String fileName, 
                   @NonNull File desDir,
                   int threadCount, 
                   @Nullable String title, 
                   @Nullable String thumbnailUrl,
                   @Nullable String quality,
                   @Nullable String parentId,
                   @Nullable String subFolder) {



	public String vid() {
		return videoId;
	}
	public String thumbUrl() {
		return thumbnailUrl;
	}
}
