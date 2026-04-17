package com.hhst.youtubelite.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import com.hhst.youtubelite.cache.WebViewCachePolicy;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

public class OkHttpWebViewInterceptorTest {

	private File cacheRoot;

	@Before
	public void setUp() throws Exception {
		cacheRoot = Files.createTempDirectory("okhttp-webview-interceptor").toFile();
	}

	@After
	public void tearDown() {
		FileUtils.deleteQuietly(cacheRoot);
	}

	@Test
	public void resourceClient_usesDedicatedDispatcherAndReusesBaseCacheAndPool() {
		final ConnectionPool pool = new ConnectionPool(8, 5, TimeUnit.MINUTES);
		final Cache cache = new Cache(new File(cacheRoot, "okhttp"), 1024L);
		final OkHttpClient baseClient = new OkHttpClient.Builder()
						.connectionPool(pool)
						.cache(cache)
						.build();

		final OkHttpClient resourceClient = OkHttpWebViewInterceptor.createResourceClient(baseClient, new WebViewCachePolicy());

		assertSame(baseClient.connectionPool(), resourceClient.connectionPool());
		assertSame(baseClient.cache(), resourceClient.cache());
		assertNotSame(baseClient.dispatcher(), resourceClient.dispatcher());
		assertEquals(64, resourceClient.dispatcher().getMaxRequests());
		assertEquals(16, resourceClient.dispatcher().getMaxRequestsPerHost());
		assertEquals(10_000, resourceClient.connectTimeoutMillis());
		assertEquals(15_000, resourceClient.writeTimeoutMillis());
		assertEquals(20_000, resourceClient.readTimeoutMillis());
		assertEquals(30_000, resourceClient.callTimeoutMillis());
	}

	@Test
	public void canExecute_acceptsAllowedYoutubeUrls() {
		final OkHttpWebViewInterceptor interceptor = new OkHttpWebViewInterceptor(new OkHttpClient(), new WebViewCachePolicy());
		
		assertTrue(interceptor.canExecute(mockRequest("GET", "https://m.youtube.com/watch?v=abc")));
		assertTrue(interceptor.canExecute(mockRequest("GET", "https://www.youtube.com/s/player/base.js")));
		assertFalse(interceptor.canExecute(mockRequest("POST", "https://m.youtube.com/watch?v=abc")));
		assertFalse(interceptor.canExecute(mockRequest("GET", "https://accounts.google.com/ServiceLogin")));
		assertFalse(interceptor.canExecute(mockRequest("GET", "https://example.com/")));
	}

	private WebResourceRequest mockRequest(String method, String url) {
		WebResourceRequest request = mock(WebResourceRequest.class);
		when(request.getMethod()).thenReturn(method);
		when(request.getUrl()).thenReturn(Uri.parse(url));
		when(request.getRequestHeaders()).thenReturn(new HashMap<>());
		return request;
	}

	private void assertTrue(boolean condition) {
		org.junit.Assert.assertTrue(condition);
	}

	private void assertFalse(boolean condition) {
		org.junit.Assert.assertFalse(condition);
	}
}
