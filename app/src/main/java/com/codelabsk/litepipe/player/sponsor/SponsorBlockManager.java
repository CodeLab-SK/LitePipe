package com.codelabsk.litepipe.player.sponsor;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.codelabsk.litepipe.player.common.PlayerPreferences;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.AllArgsConstructor;
import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public final class SponsorBlockManager {
	private static final String TAG = "SponsorBlockManager";
	private static final String API_URL = "https://sponsor.ajay.app/api/skipSegments/";
	@NonNull
	private final OkHttpClient client;
	@NonNull
	private final Gson gson;
	@NonNull
	private final PlayerPreferences preferences;
	@Getter
	@NonNull
	private volatile List<Segment> segments = Collections.emptyList();

	@Inject
	public SponsorBlockManager(@NonNull final OkHttpClient client, @NonNull final Gson gson, @NonNull final PlayerPreferences preferences) {
		this.client = client;
		this.gson = gson;
		this.preferences = preferences;
	}

	@Getter
	@AllArgsConstructor
	public static class Segment {
		private final long start;
		private final long end;
		private final String category;

		public long[] asPair() {
			return new long[]{start, end};
		}
	}


	public void load(@NonNull final String videoId) {
		segments = Collections.emptyList();
		try {
			final Set<String> cats = preferences.getSponsorBlockCategories();
			if (cats.isEmpty()) {
				Log.d(TAG, "No SponsorBlock categories enabled, skipping load.");
				return;
			}
			final String hash = sha256(videoId).substring(0, 4);
			final String categoriesJson = gson.toJson(cats);
			HttpUrl url = HttpUrl.parse(API_URL + hash);
			if (url == null) return;
			url = url.newBuilder()
					.addQueryParameter("service", "YouTube")
					.addQueryParameter("categories", categoriesJson)
					.build();
			
			Log.d(TAG, "Loading segments for video: " + videoId + " (hash prefix: " + hash + ")");
			final Request request = new Request.Builder().url(url).get().build();
			try (final Response response = client.newCall(request).execute()) {
				if (!response.isSuccessful()) {
					Log.w(TAG, "Failed to load segments: " + response.code());
					segments = Collections.emptyList();
					return;
				}
				final ResponseBody body = response.body();
				if (body == null) {
					Log.w(TAG, "Response body is null");
					return;
				}
				try (final InputStreamReader reader = new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)) {
					parseSegments(reader, videoId, cats);
				}
			}
		} catch (final Exception e) {
			Log.e(TAG, "Error loading segments for " + videoId, e);
			segments = Collections.emptyList();
		}
	}

	private void parseSegments(@NonNull final InputStreamReader reader, @NonNull final String videoId, @NonNull final Set<String> targetCats) {
		final JsonElement rootElement = JsonParser.parseReader(reader);
		if (!rootElement.isJsonArray()) {
			Log.w(TAG, "Invalid API response: not a JSON array");
			return;
		}

		final List<Segment> newSegments = new ArrayList<>();
		final JsonArray root = rootElement.getAsJsonArray();

		for (final JsonElement el : root) {
			if (!el.isJsonObject()) continue;
			final JsonObject obj = el.getAsJsonObject();
			if (!obj.has("videoID") || !obj.get("videoID").getAsString().equals(videoId)) continue;
			if (!obj.has("segments")) continue;

			for (final JsonElement segEl : obj.getAsJsonArray("segments")) {
				final JsonObject seg = segEl.getAsJsonObject();
				if (seg.has("category") && targetCats.contains(seg.get("category").getAsString()) && seg.has("segment")) {
					final JsonArray pair = seg.getAsJsonArray("segment");
					if (pair.size() >= 2) {
						long start = (long) (pair.get(0).getAsDouble() * 1000);
						long end = (long) (pair.get(1).getAsDouble() * 1000);
						if (end > start) {
							newSegments.add(new Segment(start, end, seg.get("category").getAsString()));
						}
					}
				}
			}
		}
		
		newSegments.sort(Comparator.comparingLong(Segment::getStart));

		Log.d(TAG, "Loaded " + newSegments.size() + " segments for " + videoId);
		segments = newSegments;
	}

	@NonNull
	private String sha256(@NonNull final String s) throws Exception {
		final byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		final StringBuilder hexString = new StringBuilder(hash.length * 2);
		for (final byte b : hash) {
			final String hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) hexString.append('0');
			hexString.append(hex);
		}
		return hexString.toString();
	}
}