package com.hhst.youtubelite.downloader.core;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class MediaMuxer {
	public static void merge(@NonNull final File videoFile, @NonNull final File audioFile, @NonNull final File outputFile) throws IOException {
		final MediaExtractor videoExtractor = new MediaExtractor();
		final MediaExtractor audioExtractor = new MediaExtractor();
		android.media.MediaMuxer muxer = null;

		try {
			videoExtractor.setDataSource(videoFile.getAbsolutePath());
			audioExtractor.setDataSource(audioFile.getAbsolutePath());

			muxer = new android.media.MediaMuxer(outputFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

			int videoInIndex = -1;
			int videoTrackIndex = -1;
			for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
				MediaFormat format = videoExtractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime != null && mime.startsWith("video/")) {
					videoInIndex = i;
					videoTrackIndex = muxer.addTrack(format);
					break;
				}
			}

			int audioInIndex = -1;
			int audioTrackIndex = -1;
			for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
				MediaFormat format = audioExtractor.getTrackFormat(i);
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime != null && mime.startsWith("audio/")) {
					audioInIndex = i;
					audioTrackIndex = muxer.addTrack(format);
					break;
				}
			}

			if (videoTrackIndex == -1 || audioTrackIndex == -1) {
				throw new EmptyTrackException();
			}

			muxer.start();

			videoExtractor.selectTrack(videoInIndex);
			audioExtractor.selectTrack(audioInIndex);

			final ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
			final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

			long videoStart = videoExtractor.getSampleTime();
			long audioStart = audioExtractor.getSampleTime();
			long startOffset = 0;
			if (videoStart != -1 && audioStart != -1) startOffset = Math.min(videoStart, audioStart);
			else if (videoStart != -1) startOffset = videoStart;
			else if (audioStart != -1) startOffset = audioStart;

			while (true) {
				long videoTime = videoExtractor.getSampleTime();
				long audioTime = audioExtractor.getSampleTime();

				if (videoTime == -1 && audioTime == -1) break;

				boolean isVideo;
				if (audioTime == -1) {
					isVideo = true;
				} else if (videoTime == -1) {
					isVideo = false;
				} else {
					isVideo = videoTime <= audioTime;
				}

				MediaExtractor extractor = isVideo ? videoExtractor : audioExtractor;
				int trackIndex = isVideo ? videoTrackIndex : audioTrackIndex;

				info.offset = 0;
				info.size = extractor.readSampleData(buffer, 0);
				info.presentationTimeUs = Math.max(0, extractor.getSampleTime() - startOffset);

				int extractorFlags = extractor.getSampleFlags();
				int codecFlags = 0;
				if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
					codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
				}
				if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
					codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
				}
				
				info.flags = codecFlags;
				muxer.writeSampleData(trackIndex, buffer, info);
				extractor.advance();
			}

			muxer.stop();
		} catch (Exception e) {
			if (e instanceof IOException) throw (IOException) e;
			throw new IOException("Failed to merge media files: " + e.getMessage(), e);
		} finally {
			videoExtractor.release();
			audioExtractor.release();
			if (muxer != null) {
				try {
					muxer.release();
				} catch (Exception ignored) {}
			}
		}
	}

	private static class EmptyTrackException extends RuntimeException {
		public EmptyTrackException() {
			super("No video or audio tracks found in the provided files.");
		}
	}

}
