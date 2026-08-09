package com.codelabsk.litepipe.extractor;

import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.codelabsk.litepipe.Constants;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public final class DownloaderImpl extends Downloader {

	private static final String TAG = "DownloaderImpl";
	private static final String YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000";

	private final OkHttpClient client;
	private final ExtractionSessionScope scope;

	@Inject
	public DownloaderImpl(final OkHttpClient client, final ExtractionSessionScope scope) {
		this.client = client.newBuilder()
				.readTimeout(20, TimeUnit.SECONDS)
				.connectTimeout(20, TimeUnit.SECONDS)
				.followRedirects(true)
				.build();
		this.scope = scope;
	}

	@Override
	public org.schabi.newpipe.extractor.downloader.Response execute(@NonNull final org.schabi.newpipe.extractor.downloader.Request request) throws IOException, ReCaptchaException {
		final String httpMethod = request.httpMethod() != null ? request.httpMethod() : "GET";
		final String url = request.url();
		final Map<String, List<String>> headers = request.headers();
		final byte[] dataToSend = request.dataToSend();

		RequestBody requestBody = null;
		if (dataToSend != null) requestBody = RequestBody.create(dataToSend);

		final Request.Builder builder = new Request.Builder()
				.url(url)
				.method(httpMethod, requestBody)
				.header("User-Agent", Constants.USER_AGENT);

		ExtractionSession session = scope.get();
		AuthContext auth = session != null ? session.getAuth() : null;

		final boolean isYoutube = url.contains("youtube.com") || url.contains("youtu.be");
		String finalCookies = null;

		if (isYoutube) {
			final String webViewCookies = CookieManager.getInstance().getCookie(url);
			if (webViewCookies != null && !webViewCookies.isEmpty()) {
				finalCookies = webViewCookies;
			} else if (auth != null && auth.cookies() != null) {
				finalCookies = auth.cookies();
			}

			if (finalCookies == null || !finalCookies.contains("PREF=")) {
				if (finalCookies == null || finalCookies.isEmpty()) {
					finalCookies = YOUTUBE_RESTRICTED_MODE_COOKIE;
				} else {
					finalCookies = finalCookies + "; " + YOUTUBE_RESTRICTED_MODE_COOKIE;
				}
			}
		}

		if (finalCookies != null && !finalCookies.isEmpty()) {
			builder.header("Cookie", finalCookies);
		}

		if (headers != null) {
			for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
				final String headerName = entry.getKey();
				builder.removeHeader(headerName);
				for (final String value : entry.getValue()) {
					builder.addHeader(headerName, value);
				}
			}
		}

		if (YoutubeAuth.isWebApi(url)) {
			YoutubeAuth.Result authHeaders = YoutubeAuth.headers(url, auth, System.currentTimeMillis());
			if (authHeaders.note() != null) {
				Log.d(TAG, "Skipped YouTube auth headers: " + authHeaders.note());
			}
			for (Map.Entry<String, String> entry : authHeaders.headers().entrySet()) {
				if (!hasHeader(headers, entry.getKey())) {
					builder.header(entry.getKey(), entry.getValue());
				}
			}
		}

		try (final Response response = client.newCall(builder.build()).execute()) {
			if (response.code() == 429) {
				throw new ReCaptchaException("reCaptcha Challenge requested", url);
			}

			final ResponseBody responseBody = response.body();
			return new org.schabi.newpipe.extractor.downloader.Response(
					response.code(),
					response.message(),
					response.headers().toMultimap(),
					responseBody != null ? responseBody.string() : "",
					url
			);
		}
	}

	private boolean hasHeader(@Nullable Map<String, List<String>> headers, @NonNull String name) {
		if (headers == null) return false;
		for (String key : headers.keySet()) {
			if (name.equalsIgnoreCase(key)) return true;
		}
		return false;
	}

	public <T> T withExtractionSession(@NonNull final ExtractionTask<T> task, @Nullable final ExtractionSession session) throws IOException, org.schabi.newpipe.extractor.exceptions.ExtractionException, InterruptedException {
		try {
			scope.set(session);
			return task.execute();
		} finally {
			scope.set(null);
		}
	}

	public boolean canUsePlaybackMemoryCache(@NonNull final String url) {
		return true;
	}

	@NonNull
	public String buildRequestContextFingerprint(@NonNull final String url) {
		return "default";
	}

	public boolean canPopulatePlaybackMemoryCache(@Nullable final ExtractionSession session) {
		return true;
	}

	public void clearPlaybackMemoryCacheSession(@Nullable final ExtractionSession session) {
	}

	public interface ExtractionTask<T> {
		T execute() throws IOException, org.schabi.newpipe.extractor.exceptions.ExtractionException, InterruptedException;
	}
}
