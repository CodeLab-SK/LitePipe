package com.codelabsk.litepipe.browser;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import com.codelabsk.litepipe.cache.WebViewCachePolicy;

import org.junit.Test;

import java.util.HashMap;

public class AuthenticationHostIsolationTest {

	@Test
	public void webViewInterceptor_rejectsAuthenticationHosts() {
		final OkHttpWebViewInterceptor interceptor = new OkHttpWebViewInterceptor(new okhttp3.OkHttpClient(), new WebViewCachePolicy());

		assertFalse(interceptor.canExecute(mockRequest("https://accounts.google.com/signin/v2/identifier")));
		assertFalse(interceptor.canExecute(mockRequest("https://accounts.youtube.com/accounts/CheckConnection")));
	}

	private WebResourceRequest mockRequest(String url) {
		WebResourceRequest request = mock(WebResourceRequest.class);
		when(request.getMethod()).thenReturn("GET");
		when(request.getUrl()).thenReturn(Uri.parse(url));
		when(request.getRequestHeaders()).thenReturn(new HashMap<>());
		return request;
	}
}
