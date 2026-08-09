package com.codelabsk.litepipe.downloader.core.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.codelabsk.litepipe.downloader.core.ProgressCallback;
import com.tencent.mmkv.MMKV;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class StreamDownloaderImplTest {
	private static final long TEST_CHUNK_TOTAL_BYTES = 1024L * 1024L;
	private static final String TEST_URL = "https://example.com/video";
	private static final String TEST_KEY = "test-key";

	private StreamDownloaderImpl downloader;
	private File cacheRoot;
	private MMKV mmkv;

	@Before
	public void setUp() throws Exception {
		cacheRoot = Files.createTempDirectory("stream-downloader-test").toFile();
		mmkv = mock(MMKV.class);
		downloader = new StreamDownloaderImpl(new OkHttpClient.Builder().build(), mmkv);
	}

	@After
	public void tearDown() throws Exception {
		shutdown(downloader);
		FileUtils.deleteQuietly(cacheRoot);
	}

	@Test
	public void setMaxThreadCount_updatesDispatcherLimits() throws Exception {
		downloader.setMaxThreadCount(12);

		final Dispatcher dispatcher = getClient(downloader).dispatcher();
		assertEquals(16, dispatcher.getMaxRequests()); // Fixed in impl
		assertEquals(8, dispatcher.getMaxRequestsPerHost()); // Fixed in impl
	}

	@Test
	public void headFailure_completesExceptionally() throws Exception {
		replaceDownloader(chain -> {
			final Request request = chain.request();
			if ("HEAD".equals(request.method())) {
				return response(request, 403, "forbidden".getBytes(StandardCharsets.UTF_8));
			}
			return response(request, 200, "ok".getBytes(StandardCharsets.UTF_8));
		});
		final File output = new File(cacheRoot, "output.tmp");

		final ExecutionException failure = assertThrows(ExecutionException.class,
						() -> downloader.download(TEST_KEY, TEST_URL, output, null).get(5, TimeUnit.SECONDS));

		assertTrue(rootCause(failure).getMessage().contains("Probing failed: 403"));
	}

	@Test
	public void download_successfulSingleRequest() throws Exception {
		final byte[] body = "test content".getBytes(StandardCharsets.UTF_8);
		replaceDownloader(chain -> {
			final Request request = chain.request();
			if ("HEAD".equals(request.method())) {
				return response(request, 200, new byte[0], "Content-Length", String.valueOf(body.length));
			}
			return response(request, 200, body);
		});
		final File output = new File(cacheRoot, "output-success.tmp");

		final File result = downloader.download(TEST_KEY, TEST_URL, output, null).get(5, TimeUnit.SECONDS);
		assertEquals(output.getAbsolutePath(), result.getAbsolutePath());
		assertArrayEquals(body, Files.readAllBytes(output.toPath()));
	}

	@Test
	public void download_reportsProgress() throws Exception {
		final byte[] body = new byte[1024 * 1024];
		replaceDownloader(chain -> {
			final Request request = chain.request();
			if ("HEAD".equals(request.method())) {
				return response(request, 200, new byte[0], "Content-Length", String.valueOf(body.length));
			}
			return response(request, 200, body);
		});
		final File output = new File(cacheRoot, "output-progress.tmp");
		final boolean[] progressCalled = {false};
		final ProgressCallback callback = new ProgressCallback() {
			@Override public void onProgress(int progress) { progressCalled[0] = true; }
			@Override public void onComplete(File file) {}
			@Override public void onError(Exception error) {}
			@Override public void onCancel() {}
		};

		downloader.download(TEST_KEY, TEST_URL, output, callback).get(5, TimeUnit.SECONDS);
		assertTrue(progressCalled[0]);
	}

	private OkHttpClient getClient(final StreamDownloaderImpl target) throws Exception {
		final Field field = StreamDownloaderImpl.class.getDeclaredField("client");
		field.setAccessible(true);
		return (OkHttpClient) field.get(target);
	}

	private ThreadPoolExecutor getExecutor(final StreamDownloaderImpl target) throws Exception {
		final Field field = StreamDownloaderImpl.class.getDeclaredField("executor");
		field.setAccessible(true);
		return (ThreadPoolExecutor) field.get(target);
	}

	private void replaceDownloader(final Interceptor interceptor) throws Exception {
		shutdown(downloader);
		downloader = new StreamDownloaderImpl(new OkHttpClient.Builder().addInterceptor(interceptor).build(), mmkv);
	}

	private void shutdown(final StreamDownloaderImpl target) throws Exception {
		if (target != null) {
			getExecutor(target).shutdownNow();
		}
	}

	private Response response(final Request request, final int code, final byte[] body, final String... headers) {
		final Response.Builder builder = new Response.Builder()
						.request(request)
						.protocol(Protocol.HTTP_1_1)
						.code(code)
						.message("HTTP " + code)
						.body(ResponseBody.create(body, null));
		for (int i = 0; i < headers.length; i += 2) {
			builder.header(headers[i], headers[i + 1]);
		}
		return builder.build();
	}

	private Throwable rootCause(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}
}
