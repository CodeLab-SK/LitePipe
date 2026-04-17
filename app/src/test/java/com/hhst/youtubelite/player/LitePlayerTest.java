package com.hhst.youtubelite.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.ui.DefaultTimeBar;

import com.hhst.youtubelite.PlaybackService;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.extractor.DeliveryCatalog;
import com.hhst.youtubelite.extractor.PlaybackDetails;
import com.hhst.youtubelite.extractor.PlaybackPlan;
import com.hhst.youtubelite.extractor.StreamCatalog;
import com.hhst.youtubelite.extractor.VideoDetails;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.player.controller.Controller;
import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.player.queue.QueueNav;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.hhst.youtubelite.player.sponsor.SponsorOverlayView;
import com.hhst.youtubelite.ui.ErrorDialog;
import com.hhst.youtubelite.util.DeviceUtils;
import com.tencent.mmkv.MMKV;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class LitePlayerTest {
	private static final String VIDEO_ID = "mAdodMaERp0";
	private static final String WATCH_URL = "https://www.youtube.com/watch?v=" + VIDEO_ID;

	private LitePlayer player;
	private YoutubeExtractor extractor;
	private MMKV kv;
	private MockedStatic<MMKV> mmkvStatic;
	private MockedStatic<DeviceUtils> deviceUtilsStatic;
	private Activity activity;
	private LitePlayerView playerView;
	private Controller controller;
	private Engine engine;
	private SponsorBlockManager sponsor;
	private SponsorOverlayView sponsorOverlayView;
	private DefaultTimeBar timeBar;
	private QueueRepository queueRepository;
	private Player.Listener listener;

	@Before
	public void setUp() throws Exception {
		activity = mock(Activity.class);
		extractor = mock(YoutubeExtractor.class);
		playerView = mock(LitePlayerView.class);
		controller = mock(Controller.class);
		engine = mock(Engine.class);
		sponsor = mock(SponsorBlockManager.class);
		queueRepository = mock(QueueRepository.class);
		final Executor executor = Runnable::run;
		kv = mock(MMKV.class);
		sponsorOverlayView = mock(SponsorOverlayView.class);
		timeBar = mock(DefaultTimeBar.class);
		final Resources resources = mock(Resources.class);
		final Configuration configuration = new Configuration();
		configuration.orientation = Configuration.ORIENTATION_PORTRAIT;

		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(activity).runOnUiThread(any(Runnable.class));
		doAnswer(invocation -> {
			final int viewId = invocation.getArgument(0);
			if (viewId == R.id.sponsor_overlay) return sponsorOverlayView;
			if (viewId == R.id.exo_progress) return timeBar;
			return null;
		}).when(playerView).findViewById(any(Integer.class));
		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(0).run();
			return true;
		}).when(playerView).post(any(Runnable.class));
		when(activity.getResources()).thenReturn(resources);
		when(resources.getConfiguration()).thenReturn(configuration);
		when(engine.position()).thenReturn(321L);
		when(engine.getPlaybackRate()).thenReturn(1.25f);
		when(engine.isPlaying()).thenReturn(true);

		mmkvStatic = org.mockito.Mockito.mockStatic(MMKV.class);
		mmkvStatic.when(MMKV::defaultMMKV).thenReturn(kv);
		deviceUtilsStatic = org.mockito.Mockito.mockStatic(DeviceUtils.class);
		deviceUtilsStatic.when(() -> DeviceUtils.isRotateOn(activity)).thenReturn(false);
		player = new LitePlayer(activity, extractor, playerView, controller, engine, sponsor, executor, queueRepository);
		final ArgumentCaptor<Player.Listener> listenerCaptor = ArgumentCaptor.forClass(Player.Listener.class);
		verify(engine).addListener(listenerCaptor.capture());
		listener = listenerCaptor.getValue();
	}

	@After
	public void tearDown() {
		if (mmkvStatic != null) mmkvStatic.close();
		if (deviceUtilsStatic != null) deviceUtilsStatic.close();
	}

	@Test
	public void play_successLoadsPlaybackAndUpdatesUi() throws Exception {
		final VideoDetails videoDetails = new VideoDetails();
		videoDetails.setTitle("Demo title");
		videoDetails.setDuration(60L);
		final PlaybackDetails details = new PlaybackDetails(videoDetails, new StreamCatalog(), new DeliveryCatalog(), new PlaybackPlan(), Collections.emptyList(), Collections.emptyList());
		when(extractor.getInfo(eq(WATCH_URL), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(details));

		player.play(WATCH_URL);

		verify(sponsor).load(VIDEO_ID);
		verify(engine).clear();
		verify(playerView).show();
		verify(playerView).setTitle("Demo title");
		verify(playerView).updateSkipMarkers(60L, TimeUnit.SECONDS);
		verify(engine).play(details);
	}

	@Test
	public void pause_delegatesToEngine() {
		player.pause();
		verify(engine).pause();
	}

	@Test
	public void seekLoadedVideo_seeksWhenTimestampMatchesLoadedVideo() throws Exception {
		setField(player, "loadedVideoId", VIDEO_ID);
		assertTrue(player.seekLoadedVideo(WATCH_URL + "&t=173s", 173_000L));
		verify(engine).seekTo(173_000L);
	}

	@Test
	public void fullscreenAndPictureInPictureApis_delegateToController() {
		player.enterFullscreen();
		player.exitFullscreen();
		player.syncRotation(true, Configuration.ORIENTATION_LANDSCAPE);
		player.onPictureInPictureModeChanged(true);

		verify(controller).enterFullscreen();
		verify(controller).exitFullscreen();
		verify(controller).syncRotation(true, Configuration.ORIENTATION_LANDSCAPE);
		verify(controller).onPictureInPictureModeChanged(true);
	}

	@Test
	public void enterInAppMiniPlayer_delegatesToPlayerView() {
		player.enterInAppMiniPlayer();
		verify(playerView).enterInAppMiniPlayer();
		verify(controller).enterMiniPlayer();
	}

	@Test
	public void hide_clearsEngineAndHidesView() {
		player.hide();
		verify(playerView).hide();
		verify(engine).clear();
	}

	private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
		final Field field = LitePlayer.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
