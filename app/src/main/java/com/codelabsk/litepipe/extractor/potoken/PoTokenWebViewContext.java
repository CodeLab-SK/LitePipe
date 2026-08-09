package com.codelabsk.litepipe.extractor.potoken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record PoTokenWebViewContext(@NonNull String url,
                                    @Nullable String visitorData,
                                    @Nullable String dataSyncId,
                                    @Nullable String clientVersion,
                                    @Nullable String sessionIndex,
                                    @Nullable String serializedExperimentFlags,
                                    boolean loggedIn,
                                    boolean premium) {

	@Nullable
	public static PoTokenWebViewContext fromJson(@NonNull String url, long epoch, @Nullable String json) {
		if (json == null) return null;
		try {
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			if (object.has("error")) return null;
			return new PoTokenWebViewContext(
							url,
							getString(object, "visitorData"),
							getString(object, "dataSyncId"),
							getString(object, "clientVersion"),
							getString(object, "sessionIndex"),
							getString(object, "serializedExperimentFlags"),
							getBoolean(object, "loggedIn"),
							getBoolean(object, "premium"));
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	private static String getString(@NonNull JsonObject object, @NonNull String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
	}

	private static boolean getBoolean(@NonNull JsonObject object, @NonNull String key) {
		return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
	}
}
