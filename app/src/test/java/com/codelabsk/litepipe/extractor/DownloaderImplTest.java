package com.codelabsk.litepipe.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.webkit.CookieManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloaderImplTest {

	private static final String WATCH_URL = "https://www.youtube.com/watch?v=mAdodMaERp0";

	private DownloaderImpl downloader;
	private Call call;
	private OkHttpClient configuredClient;
	private ExtractionSessionScope scope;

	@Before
	public void setUp() throws Exception {
		final OkHttpClient seedClient = mock(OkHttpClient.class);
		final OkHttpClient.Builder builder = mock(OkHttpClient.Builder.class);
		configuredClient = mock(OkHttpClient.class);
		call = mock(Call.class);
		scope = new ExtractionSessionScope();

		when(seedClient.newBuilder()).thenReturn(builder);
		when(builder.readTimeout(anyLong(), any(TimeUnit.class))).thenReturn(builder);
		when(builder.connectTimeout(anyLong(), any(TimeUnit.class))).thenReturn(builder);
		when(builder.followRedirects(true)).thenReturn(builder);
		when(builder.build()).thenReturn(configuredClient);
		when(configuredClient.newCall(any(Request.class))).thenReturn(call);
		when(call.execute()).thenAnswer(invocation -> mockResponse());

		downloader = new DownloaderImpl(seedClient, scope);
	}

	@Test
	public void buildRequestContextFingerprint_returnsDefault() {
		assertEquals("default", downloader.buildRequestContextFingerprint(WATCH_URL));
	}

	@Test
	public void execute_includesCookiesFromWebView() throws Exception {
		final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
		when(configuredClient.newCall(requestCaptor.capture())).thenReturn(call);

		try (MockedStatic<CookieManager> cookieManagerStatic = mockStatic(CookieManager.class)) {
			final CookieManager cookieManager = mock(CookieManager.class);
			cookieManagerStatic.when(CookieManager::getInstance).thenReturn(cookieManager);
			when(cookieManager.getCookie(WATCH_URL)).thenReturn("SID=session");

			final org.schabi.newpipe.extractor.downloader.Request newpipeRequest =
							new org.schabi.newpipe.extractor.downloader.Request(
											"GET",
											WATCH_URL,
											Collections.emptyMap(),
											null,
											null,
											false);

			downloader.execute(newpipeRequest);

			final Request captured = requestCaptor.getValue();
			final String cookieHeader = captured.header("Cookie");
			assertNotNull(cookieHeader);
			assertTrue(cookieHeader.contains("SID=session"));
			assertTrue(cookieHeader.contains("PREF=f2=8000000"));
		}
	}

	@Test
	public void execute_throwsReCaptchaExceptionOn429() throws Exception {
		final Response response429 = mock(Response.class);
		final Headers headers = mock(Headers.class);
		final ResponseBody body = mock(ResponseBody.class);
		when(response429.code()).thenReturn(429);
		when(response429.message()).thenReturn("Too Many Requests");
		when(response429.headers()).thenReturn(headers);
		when(headers.toMultimap()).thenReturn(Collections.emptyMap());
		when(response429.body()).thenReturn(body);
		when(body.string()).thenReturn("");
		when(call.execute()).thenReturn(response429);

		try (MockedStatic<CookieManager> cookieManagerStatic = mockStatic(CookieManager.class)) {
			final CookieManager cookieManager = mock(CookieManager.class);
			cookieManagerStatic.when(CookieManager::getInstance).thenReturn(cookieManager);
			when(cookieManager.getCookie(WATCH_URL)).thenReturn("");

			final org.schabi.newpipe.extractor.downloader.Request request =
							new org.schabi.newpipe.extractor.downloader.Request(
											"GET",
											WATCH_URL,
											Collections.emptyMap(),
											null,
											null,
											false);

			assertThrows(org.schabi.newpipe.extractor.exceptions.ReCaptchaException.class,
							() -> downloader.execute(request));
		}
	}

	private static Response mockResponse() throws Exception {
		final Response response = mock(Response.class);
		final Headers headers = mock(Headers.class);
		final ResponseBody responseBody = mock(ResponseBody.class);

		when(response.code()).thenReturn(200);
		when(response.message()).thenReturn("OK");
		when(response.headers()).thenReturn(headers);
		when(headers.toMultimap()).thenReturn(Collections.emptyMap());
		when(response.body()).thenReturn(responseBody);
		when(responseBody.string()).thenReturn("");
		return response;
	}
}
