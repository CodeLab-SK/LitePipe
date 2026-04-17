package com.hhst.youtubelite.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.tencent.mmkv.MMKV;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class YoutubeExtractorTest {
	private static final String VIDEO_ID = "mAdodMaERp0";
	private static final String WATCH_URL = "https://m.youtube.com/watch?v=" + VIDEO_ID;

	private MMKV kv;
	private Gson gson;
	private InfoCache cache;
	private Executor executor;
	private AuthContextFactory auth;

	@Before
	public void setUp() {
		kv = mock(MMKV.class);
		gson = new Gson();
		cache = new InfoCache(kv, gson);
		executor = Runnable::run;
		auth = mock(AuthContextFactory.class);
	}

	@Test
	public void getVideoId_supportsCommonYoutubeUrlShapes() {
		assertEquals(VIDEO_ID, YoutubeExtractor.getVideoId("https://m.youtube.com/watch?v=" + VIDEO_ID));
		assertEquals(VIDEO_ID, YoutubeExtractor.getVideoId("https://youtu.be/" + VIDEO_ID));
		assertEquals(VIDEO_ID, YoutubeExtractor.getVideoId("https://www.youtube.com/shorts/" + VIDEO_ID));
	}

	@Test
	public void getVideoId_returnsNullForUnsupportedUrls() {
		assertNull(YoutubeExtractor.getVideoId("https://example.com/video"));
		assertNull(YoutubeExtractor.getVideoId("https://youtu.be/too-short"));
		assertNull(YoutubeExtractor.getVideoId(null));
	}

	@Test
	public void getPlaybackDetails_fetchesOnceAndCachesVideoDetailsWhenCacheMisses() throws Exception {
		final AtomicInteger fetchCount = new AtomicInteger();
		final StreamInfo streamInfo = mockStreamInfo("fresh-title", "https://example.com/dash.mpd", StreamType.VIDEO_STREAM);
		when(kv.decodeString(anyString(), any())).thenReturn(null);

		final YoutubeExtractor extractor = new YoutubeExtractor(
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return new ExtractedInfo(streamInfo, null);
						},
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return new ExtractedInfo(streamInfo, null);
						},
						cache,
						executor,
						gson,
						auth);

		final PlaybackDetails playbackDetails = extractor.getPlaybackDetails(WATCH_URL, new ExtractionSession());

		assertEquals(1, fetchCount.get());
		assertEquals("fresh-title", playbackDetails.video().getTitle());
		assertEquals("https://example.com/dash.mpd", playbackDetails.plan().getManifestUrl());
		verify(kv).encode(eq("extractor:stream:" + VIDEO_ID), anyString());
	}

	@Test
	public void getPlaybackDetails_usesCachedVideoDetailsAndStillFetchesFreshStreamInfo() throws Exception {
		final AtomicInteger fetchCount = new AtomicInteger();
		final VideoDetails cachedVideoDetails = cachedVideoDetails("cached-title");
		final StreamInfo streamInfo = mockStreamInfo("fresh-title", "https://example.com/fresh-dash.mpd", StreamType.VIDEO_STREAM);
		
		when(kv.decodeString(eq("extractor:info:" + VIDEO_ID), any())).thenReturn(gson.toJson(new Slot(System.currentTimeMillis() + 100000, gson.toJson(cachedVideoDetails))));
		when(kv.decodeString(eq("extractor:stream:" + VIDEO_ID), any())).thenReturn(null);

		final YoutubeExtractor extractor = new YoutubeExtractor(
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return new ExtractedInfo(streamInfo, null);
						},
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return new ExtractedInfo(streamInfo, null);
						},
						cache,
						executor,
						gson,
						auth);

		final PlaybackDetails playbackDetails = extractor.getPlaybackDetails(WATCH_URL, new ExtractionSession());

		assertEquals(1, fetchCount.get());
		assertEquals("cached-title", playbackDetails.video().getTitle());
		assertEquals("https://example.com/fresh-dash.mpd", playbackDetails.plan().getManifestUrl());
	}

	@Test
	public void getPlaybackDetails_usesPersistentVideoCacheAndMemoryStreamCacheWithoutFetching() throws Exception {
		final AtomicInteger fetchCount = new AtomicInteger();
		final PlaybackDetails cachedDetails = mock(PlaybackDetails.class, Answers.RETURNS_DEEP_STUBS);
		when(cachedDetails.video().getTitle()).thenReturn("cached-title");
		when(cachedDetails.plan().getManifestUrl()).thenReturn("https://example.com/cached-dash.mpd");

		when(kv.decodeString(eq("extractor:stream:" + VIDEO_ID), any())).thenReturn(gson.toJson(new Slot(System.currentTimeMillis() + 100000, gson.toJson(cachedDetails))));

		final YoutubeExtractor extractor = new YoutubeExtractor(
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return null;
						},
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return null;
						},
						cache,
						executor,
						gson,
						auth);

		final PlaybackDetails playbackDetails = extractor.getPlaybackDetails(WATCH_URL, new ExtractionSession());

		assertEquals(0, fetchCount.get());
		assertEquals("cached-title", playbackDetails.video().getTitle());
		assertEquals("https://example.com/cached-dash.mpd", playbackDetails.plan().getManifestUrl());
	}

	@Test
	public void getVideoInfo_returnsCachedDetailsWithoutFetching() throws Exception {
		final AtomicInteger fetchCount = new AtomicInteger();
		final VideoDetails cachedVideoDetails = cachedVideoDetails("cached-only");
		when(kv.decodeString(eq("extractor:info:" + VIDEO_ID), any())).thenReturn(gson.toJson(new Slot(System.currentTimeMillis() + 100000, gson.toJson(cachedVideoDetails))));

		final YoutubeExtractor extractor = new YoutubeExtractor(
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return null;
						},
						(videoId, session) -> {
							fetchCount.incrementAndGet();
							return null;
						},
						cache,
						executor,
						gson,
						auth);

		final VideoDetails videoDetails = extractor.getVideoInfo(WATCH_URL);

		assertEquals(0, fetchCount.get());
		assertEquals("cached-only", videoDetails.getTitle());
	}

	@Test
	public void getPlaybackDetails_doesNotFetchWhenSessionAlreadyCancelled() {
		final ExtractionSession session = new ExtractionSession();
		session.cancel();

		final YoutubeExtractor extractor = new YoutubeExtractor(
						(videoId, session1) -> null,
						(videoId, session1) -> null,
						cache,
						executor,
						gson,
						auth);

		assertThrows(Exception.class, () -> extractor.getPlaybackDetails(WATCH_URL, session));
	}

	private static VideoDetails cachedVideoDetails(final String title) {
		final VideoDetails videoDetails = new VideoDetails();
		videoDetails.setId(VIDEO_ID);
		videoDetails.setTitle(title);
		videoDetails.setAuthor("cached-author");
		return videoDetails;
	}

	private static StreamInfo mockStreamInfo(final String title,
	                                         final String dashUrl,
	                                         final StreamType streamType) {
		final StreamInfo streamInfo = mock(StreamInfo.class, Answers.RETURNS_DEEP_STUBS);
		when(streamInfo.getId()).thenReturn(VIDEO_ID);
		when(streamInfo.getName()).thenReturn(title);
		when(streamInfo.getUploaderName()).thenReturn("author");
		when(streamInfo.getDescription().getContent()).thenReturn("description");
		when(streamInfo.getDuration()).thenReturn(42L);
		when(streamInfo.getThumbnails()).thenReturn(Collections.emptyList());
		when(streamInfo.getLikeCount()).thenReturn(7L);
		when(streamInfo.getDislikeCount()).thenReturn(1L);
		when(streamInfo.getUploadDate().offsetDateTime().toInstant()).thenReturn(Instant.EPOCH);
		when(streamInfo.getUploaderUrl()).thenReturn("https://example.com/channel");
		when(streamInfo.getUploaderAvatars()).thenReturn(Collections.emptyList());
		when(streamInfo.getViewCount()).thenReturn(99L);
		when(streamInfo.getStreamSegments()).thenReturn(Collections.emptyList());
		when(streamInfo.getVideoOnlyStreams()).thenReturn(Collections.emptyList());
		when(streamInfo.getAudioStreams()).thenReturn(Collections.emptyList());
		when(streamInfo.getSubtitles()).thenReturn(Collections.emptyList());
		when(streamInfo.getDashMpdUrl()).thenReturn(dashUrl);
		when(streamInfo.getHlsUrl()).thenReturn("https://example.com/hls.m3u8");
		when(streamInfo.getStreamType()).thenReturn(streamType);
		return streamInfo;
	}

	private record Slot(long until, String json) {}
}
