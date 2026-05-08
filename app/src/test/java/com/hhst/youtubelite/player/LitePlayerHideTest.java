package com.hhst.youtubelite.player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Activity;

import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.player.controller.Controller;
import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.tencent.mmkv.MMKV;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.Executor;

public class LitePlayerHideTest {
	private LitePlayer player;
	private LitePlayerView playerView;
	private MockedStatic<MMKV> mmkvStatic;

	@Before
	public void setUp() {
		final Activity activity = mock(Activity.class);
		org.mockito.Mockito.doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(activity).runOnUiThread(any(Runnable.class));
		final YoutubeExtractor extractor = mock(YoutubeExtractor.class);
		playerView = mock(LitePlayerView.class);
		final Controller controller = mock(Controller.class);
		final Engine engine = mock(Engine.class);
		final SponsorBlockManager sponsor = mock(SponsorBlockManager.class);
		final QueueRepository queueRepository = mock(QueueRepository.class);
		final Executor executor = Runnable::run;
		mmkvStatic = org.mockito.Mockito.mockStatic(MMKV.class);
		mmkvStatic.when(MMKV::defaultMMKV).thenReturn(mock(MMKV.class));
		player = new LitePlayer(activity, extractor, playerView, controller, engine, sponsor, executor, queueRepository);
	}

	@After
	public void tearDown() {
		if (mmkvStatic != null) mmkvStatic.close();
	}

	@Test
	public void hide_clearsMiniPlayerCallbacksAndExitsMiniPlayer() {
		player.setMiniPlayerCallbacks(() -> {
		}, () -> {
		});
		player.enterInAppMiniPlayer();

		player.hide();

		verify(playerView).setMiniPlayerCallbacks(isNull(), isNull(), any(), any());
		verify(playerView).exitInAppMiniPlayer();
		verify(playerView).hide();
	}
}
