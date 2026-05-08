package com.hhst.youtubelite.player.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.util.StringUtils;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UnstableApi
public final class PlayerUtils {

	public static boolean isPortrait(@NonNull final Engine engine) {
		final int videoWidth = engine.getVideoSize().width;
		final int videoHeight = engine.getVideoSize().height;
		return videoWidth > 0 && videoHeight > 0 && videoHeight > videoWidth;
	}

	@NonNull
	public static List<VideoStream> filterBestStreams(@Nullable final List<VideoStream> streams) {
		if (streams == null || streams.isEmpty()) return new ArrayList<>();

		final Map<String, VideoStream> bestMap = new HashMap<>();

		for (final VideoStream stream : streams) {
			final String res = stream.getResolution();
			final VideoStream existing = bestMap.get(res);

			if (existing == null || isBetterStream(stream, existing)) bestMap.put(res, stream);
		}

		final List<VideoStream> result = new ArrayList<>(bestMap.values());
		result.sort((s1, s2) -> {
			final int h1 = s1.getHeight();
			final int h2 = s2.getHeight();
			if (h1 != h2) return Integer.compare(h2, h1);
			return Integer.compare(s2.getFps(), s1.getFps());
		});
		return result;
	}

	public static boolean isBetterStream(@NonNull final VideoStream s1, @NonNull final VideoStream s2) {
		final int p1 = getCodecPriority(s1.getCodec());
		final int p2 = getCodecPriority(s2.getCodec());
		if (p1 != p2) return p1 > p2;

		if (s1.getFps() != s2.getFps()) return s1.getFps() > s2.getFps();

		return s1.getBitrate() > s2.getBitrate();
	}

	public static int getCodecPriority(@Nullable final String codec) {
		if (codec == null) return 0;
		final String lowerCodec = codec.toLowerCase(Locale.ROOT);
		if (lowerCodec.contains("av01")) return 5;
		if (lowerCodec.contains("vp9")) return 4;
		if (lowerCodec.startsWith("avc") || lowerCodec.startsWith("h264")) return 3;
		if (lowerCodec.contains("h265") || lowerCodec.contains("hevc")) return 2;

		return 0;
	}

	@Nullable
	public static VideoStream selectVideoStream(@Nullable final List<VideoStream> streams, @Nullable final String targetRes) {
		if (streams == null || streams.isEmpty()) return null;

		String res = targetRes;
		if (res == null || "Auto".equalsIgnoreCase(res)) {
			return streams.get(0);
		}

		for (final VideoStream s : streams) if (s.getResolution().equals(res)) return s;

		final int targetHeight = StringUtils.parseHeight(res);
		for (final VideoStream s : streams) if (s.getHeight() <= targetHeight) return s;
		return streams.get(streams.size() - 1);
	}

	@Nullable
	public static AudioStream selectAudioStream(@Nullable final List<AudioStream> streams, @Nullable final String preferredInfo) {
		if (streams == null || streams.isEmpty()) return null;
		if (preferredInfo == null) return streams.get(0);

		for (final AudioStream as : streams) {
			final int bitrate = as.getAverageBitrate();
			final String bitrateStr = bitrate > 0 ? bitrate + "kbps" : "Unknown bitrate";
			final String info = String.format(Locale.getDefault(), "%s - %s - %s", as.getFormat(), as.getCodec(), bitrateStr);
			if (info.equals(preferredInfo)) return as;
		}

		return streams.get(0);
	}
}
