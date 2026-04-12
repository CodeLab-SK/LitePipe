package com.hhst.youtubelite.downloader.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hhst.youtubelite.extractor.PlaybackDetails;
import com.hhst.youtubelite.extractor.StreamCatalog;
import com.hhst.youtubelite.extractor.VideoDetails;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory that builds download tasks from selected streams.
 */
public final class DownloadTaskFactory {

	@NonNull
	public String sanitizeFileName(@Nullable String rawName) {
		String safeName = rawName == null
						? ""
						: rawName.replaceAll("[<>:\"/|?*]", "_").trim();
		return safeName.isEmpty() ? "download" : safeName;
	}

	@NonNull
	public Map<Integer, String> planPlaylistNames(@NonNull Map<Integer, String> requestedNames) {
		Map<Integer, String> plannedNames = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (Map.Entry<Integer, String> entry : requestedNames.entrySet()) {
			String sanitized = sanitizeFileName(entry.getValue());
			int count = counts.merge(sanitized, 1, Integer::sum);
			String planned = count == 1 ? sanitized : sanitized + " (" + count + ")";
			plannedNames.put(entry.getKey(), planned);
		}
		return plannedNames;
	}

	@NonNull
	public String buildPlaylistBaseName(@Nullable String title,
	                                    @Nullable String author) {
		String safeTitle = sanitizeFileName(title);
		String safeAuthor = author == null
						? ""
						: author.replaceAll("[<>:\"/|?*]", "_").trim();
		return safeAuthor.isBlank() ? safeTitle : sanitizeFileName(safeTitle + "-" + safeAuthor);
	}

	@NonNull
	public List<Task> buildSingleVideoTasks(@NonNull VideoDetails videoDetails,
	                                        @NonNull StreamCatalog catalog,
	                                        @NonNull DownloadSelectionConfig config,
	                                        @Nullable VideoStream selectedVideo,
	                                        @Nullable AudioStream selectedAudio,
	                                        @Nullable SubtitlesStream selectedSubtitle,
	                                        @NonNull String baseFileName,
	                                        @NonNull File destinationDir) {
		List<Task> tasks = new ArrayList<>();
		String fileName = sanitizeFileName(baseFileName);
		String videoId = videoDetails.getId();
		String title = videoDetails.getTitle();
		String thumbnailUrl = videoDetails.getThumbnail();

		switch (config.primaryMediaMode()) {
			case VIDEO -> {
				if (selectedVideo != null) {
					AudioStream audio = selectedAudio != null ? selectedAudio : pickAudioStream(catalog);
					if (audio != null) {
						tasks.add(new Task(
										DownloadTaskIdHelper.buildTaskId(videoId, DownloadTaskIdHelper.ASSET_VIDEO),
										selectedVideo,
										audio,
										null,
										null,
										fileName,
										destinationDir,
										config.threadCount(),
										title,
										thumbnailUrl,
										selectedVideo.getResolution(),
										null,
										null));
					}
				}
			}
			case AUDIO -> {
				if (selectedAudio != null) {
					tasks.add(new Task(
									DownloadTaskIdHelper.buildTaskId(videoId, DownloadTaskIdHelper.ASSET_AUDIO),
									null,
									selectedAudio,
									null,
									null,
									fileName,
									destinationDir,
									config.threadCount(),
									title,
									thumbnailUrl,
									null,
									null,
									null));
				}
			}
			case NONE -> {
			}
		}

		if (config.subtitleEnabled() && selectedSubtitle != null) {
			tasks.add(new Task(
							DownloadTaskIdHelper.buildTaskId(videoId, DownloadTaskIdHelper.ASSET_SUBTITLE),
							null,
							null,
							selectedSubtitle,
							null,
							fileName,
							destinationDir,
							config.threadCount(),
							title,
							thumbnailUrl,
							null,
							null,
							null));
		}

		if (config.thumbnailEnabled() && thumbnailUrl != null && !thumbnailUrl.isBlank()) {
			tasks.add(new Task(
							DownloadTaskIdHelper.buildTaskId(videoId, DownloadTaskIdHelper.ASSET_THUMBNAIL),
							null,
							null,
							null,
							thumbnailUrl,
							fileName,
							destinationDir,
							config.threadCount(),
							title,
							thumbnailUrl,
							null,
							null,
							null));
		}
		return tasks;
	}

	@NonNull
	public List<Task> buildPlaylistTasksForItem(@NonNull PlaybackDetails playbackDetails,
	                                            @NonNull DownloadSelectionConfig config,
	                                            int playlistIndex,
	                                            @NonNull String plannedBaseName,
	                                            @NonNull File destinationDir,
	                                            @Nullable String parentId,
	                                            @Nullable String subFolder) {
		List<Task> tasks = new ArrayList<>();
		VideoDetails videoDetails = playbackDetails.getVideoDetails();
		StreamCatalog catalog = playbackDetails.getCatalog();
		String fileName = sanitizeFileName(plannedBaseName);
		String videoId = videoDetails.getId();
		String title = videoDetails.getTitle();
		String thumbnailUrl = videoDetails.getThumbnail();

		switch (config.primaryMediaMode()) {
			case VIDEO -> {
				VideoStream videoStream = pickPlaylistVideoStream(catalog, config.preferredQuality());
				AudioStream audioStream = pickAudioStream(catalog);
				if (videoStream != null && audioStream != null) {
					tasks.add(new Task(
									DownloadTaskIdHelper.buildPlaylistId(videoId, playlistIndex, parentId, DownloadTaskIdHelper.ASSET_VIDEO),
									videoStream,
									audioStream,
									null,
									null,
									fileName,
									destinationDir,
									config.threadCount(),
									title,
									thumbnailUrl,
									videoStream.getResolution(),
									parentId,
									subFolder));
				}
			}
			case AUDIO -> {
				AudioStream audioStream = pickAudioStream(catalog);
				if (audioStream != null) {
					tasks.add(new Task(
									DownloadTaskIdHelper.buildPlaylistId(videoId, playlistIndex, parentId, DownloadTaskIdHelper.ASSET_AUDIO),
									null,
									audioStream,
									null,
									null,
									fileName,
									destinationDir,
									config.threadCount(),
									title,
									thumbnailUrl,
									null,
									parentId,
									subFolder));
				}
			}
			case NONE -> {
			}
		}

		if (config.subtitleEnabled()) {
			SubtitlesStream subtitleStream = pickSubtitleStream(playbackDetails.getSubtitles());
			if (subtitleStream != null) {
				tasks.add(new Task(
								DownloadTaskIdHelper.buildPlaylistId(videoId, playlistIndex, parentId, DownloadTaskIdHelper.ASSET_SUBTITLE),
								null,
								null,
								subtitleStream,
								null,
								fileName,
								destinationDir,
								config.threadCount(),
								title,
								thumbnailUrl,
								null,
								parentId,
								subFolder));
			}
		}

		if (config.thumbnailEnabled()) {
			if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
				tasks.add(new Task(
								DownloadTaskIdHelper.buildPlaylistId(videoId, playlistIndex, parentId, DownloadTaskIdHelper.ASSET_THUMBNAIL),
								null,
								null,
								null,
								thumbnailUrl,
								fileName,
								destinationDir,
								config.threadCount(),
								title,
								thumbnailUrl,
								null,
								parentId,
								subFolder));
			}
		}

		return tasks;
	}

	@Nullable
	private AudioStream pickAudioStream(@NonNull StreamCatalog catalog) {
		List<AudioStream> streams = catalog.getAudioStreams();
		if (streams.isEmpty()) return null;
		for (AudioStream stream : streams) {
			if (stream.getAudioTrackType() == AudioTrackType.ORIGINAL) return stream;
		}
		return streams.get(0);
	}

	@Nullable
	private SubtitlesStream pickSubtitleStream(@NonNull List<SubtitlesStream> subs) {
		return subs == null || subs.isEmpty() ? null : subs.get(0);
	}

	@Nullable
	private VideoStream pickPlaylistVideoStream(@NonNull StreamCatalog catalog, @Nullable String preferredQuality) {
		List<VideoStream> streams = catalog.getVideoStreams();
		if (streams.isEmpty()) return null;
		
		Comparator<VideoStream> comparator = Comparator
						.comparingInt(this::streamHeight)
						.thenComparingInt(VideoStream::getFps)
						.thenComparingInt(VideoStream::getBitrate);
						
		if (preferredQuality != null && !preferredQuality.isBlank()) {
			int targetHeight = extractHeight(preferredQuality);
			if (targetHeight > 0) {
				VideoStream match = streams.stream()
								.filter(s -> s.getFormat() == MediaFormat.MPEG_4)
								.filter(s -> streamHeight(s) == targetHeight)
								.max(comparator)
								.orElse(null);
				if (match != null) return match;

				VideoStream fallback = streams.stream()
								.filter(s -> s.getFormat() == MediaFormat.MPEG_4)
								.filter(s -> streamHeight(s) <= targetHeight)
								.max(comparator)
								.orElse(null);
				if (fallback != null) return fallback;
			}
		}

		VideoStream limited = streams.stream()
						.filter(s -> s.getFormat() == MediaFormat.MPEG_4)
						.filter(s -> {
							int h = streamHeight(s);
							return h > 0 && h <= 1080;
						})
						.max(comparator)
						.orElse(null);
		if (limited != null) return limited;
		return streams.stream()
						.filter(s -> s.getFormat() == MediaFormat.MPEG_4)
						.max(comparator)
						.orElse(null);
	}

	private int streamHeight(@NonNull VideoStream stream) {
		int height = stream.getHeight();
		return height > 0 ? height : extractResolution(stream);
	}

	private int extractResolution(@NonNull VideoStream stream) {
		return extractHeight(stream.getResolution());
	}
	
	private int extractHeight(@Nullable String res) {
		if (res == null || res.isBlank()) return -1;
		String digits = res.replaceAll("[^0-9]", "");
		if (digits.isBlank()) return -1;
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}
}
