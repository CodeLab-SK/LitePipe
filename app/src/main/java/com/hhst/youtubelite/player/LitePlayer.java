package com.hhst.youtubelite.player;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.SubtitleView;

import com.hhst.youtubelite.PlaybackService;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.downloader.core.history.DownloadRecord;
import com.hhst.youtubelite.extractor.ExtractionSession;
import com.hhst.youtubelite.extractor.PlaybackDetails;
import com.hhst.youtubelite.extractor.PlaybackPlan;
import com.hhst.youtubelite.extractor.PlaybackPlanner;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.player.common.PlayerLoopMode;
import com.hhst.youtubelite.player.common.PlayerPreferences;
import com.hhst.youtubelite.player.controller.Controller;
import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.player.queue.QueueItem;
import com.hhst.youtubelite.player.queue.QueueNav;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.hhst.youtubelite.player.sponsor.SponsorOverlayView;
import com.hhst.youtubelite.ui.ErrorDialog;
import com.hhst.youtubelite.util.DeviceUtils;
import com.hhst.youtubelite.util.StringUtils;
import com.hhst.youtubelite.util.ToastUtils;
import com.hhst.youtubelite.util.UrlUtils;
import com.tencent.mmkv.MMKV;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import lombok.Getter;

@UnstableApi
@ActivityScoped
public class LitePlayer {

	public static final String KEY_SUBTITLE_STYLE = "subtitle_style_id";
	public static final String KEY_SUBTITLE_CUSTOM_TEXT_SIZE = "subtitle_custom_text_size";
	public static final String KEY_SUBTITLE_CUSTOM_TEXT_COLOR = "subtitle_custom_text_color";
	public static final String KEY_SUBTITLE_CUSTOM_BG_COLOR = "subtitle_custom_bg_color";
	public static final String KEY_SUBTITLE_CUSTOM_BG_OPACITY = "subtitle_custom_bg_opacity";
	public static final String KEY_SUBTITLE_CUSTOM_EDGE_TYPE = "subtitle_custom_edge_type";
	public static final String KEY_SUBTITLE_CUSTOM_EDGE_COLOR = "subtitle_custom_edge_color";

	@NonNull
	private final Activity activity;
	@NonNull
	private final YoutubeExtractor extractor;
	@NonNull
	private final LitePlayerView playerView;
	@Getter
	@NonNull
	private final Controller controller;
	@NonNull
	private final Engine engine;
	@NonNull
	private final SponsorBlockManager sponsor;
	@NonNull
	private final QueueRepository queueRepository;
	@NonNull
	private final PlayerPreferences prefs;

	private final MMKV kv = MMKV.defaultMMKV();

	@Nullable
	private PlaybackService playbackService;
	@Nullable
	private String vid = null;
	@Nullable
	private volatile String loadedVideoId;
	@Nullable
	private String currentUrl = null;
	private int retryCount = 0;
	@Nullable
	private ExtractionSession extractionSession;
	@Nullable
	private Runnable onMiniPlayerRestore;
	@Nullable
	private Runnable onMiniPlayerClose;
	@Getter
	private boolean inAppMiniPlayer;
	private boolean wasInPictureInPicture;
	private final Set<Long> ignoredSponsorStarts = new HashSet<>();

	public interface OnFullscreenChangeListener {
		void onFullscreenChanged(boolean isFullscreen);
	}
	@Nullable private OnFullscreenChangeListener fullscreenChangeListener;

	@Inject
	public LitePlayer(@NonNull final Activity activity,
					  @NonNull final YoutubeExtractor extractor,
					  @NonNull final LitePlayerView playerView,
					  @NonNull final Controller controller,
					  @NonNull final Engine engine,
					  @NonNull final SponsorBlockManager sponsor,
					  @NonNull final QueueRepository queueRepository,
					  @NonNull final PlayerPreferences prefs) {
		this.activity = activity;
		this.extractor = extractor;
		this.playerView = playerView;
		this.controller = controller;
		this.engine = engine;
		this.sponsor = sponsor;
		this.queueRepository = queueRepository;
		this.prefs = prefs;

		playerView.setup();
		setupEngineListeners();

		this.controller.setOnFullscreenChangeListener(isFullscreen -> {
			if (fullscreenChangeListener != null) {
				fullscreenChangeListener.onFullscreenChanged(isFullscreen);
			}
		});
	}

	public void setOnFullscreenChangeListener(@Nullable OnFullscreenChangeListener listener) {
		this.fullscreenChangeListener = listener;
	}

	private void setupEngineListeners() {
		engine.addListener(new Player.Listener() {
			@Override
			public void onIsPlayingChanged(boolean isPlaying) {
				if (isPlaying) retryCount = 0;
				updateServiceProgress(isPlaying);
			}

			@Override
			public void onTracksChanged(@NonNull Tracks tracks) {
				applySubtitleStyle();
			}

			@Override
			public void onPlaybackStateChanged(int playbackState) {
				if (playbackState == Player.STATE_READY) {
					updateServiceProgress(engine.isPlaying());
					applySubtitleStyle();
				} else if (playbackState == Player.STATE_ENDED) {
					skipToNext();
				}
			}

			@Override
			public void onPlayerError(@NonNull PlaybackException error) {
				if (engine.recoverFromPlaybackError(error)) {
					return;
				}

				if (isCodecError(error)) {
					handleCodecError(error);
					return;
				}

				if (shouldRetryOnSourceError(error) && retryCount < 3 && currentUrl != null) {
					retryCount++;
					final long pos = engine.position();
					final String url = currentUrl;
					vid = null;
					play(url, pos);
					return;
				}
				ErrorDialog.show(activity, error.getMessage(), error);
			}
		});

		engine.setSponsorDetectedListener(segment -> {
			if (ignoredSponsorStarts.contains(segment.getStart())) return;

			activity.runOnUiThread(() -> {
				engine.seekTo(segment.getEnd());
				String category = segment.getCategory();
				String hint;
				if (category != null) {
					hint = "Skipped " + StringUtils.capitalize(category.replace("selfpromo", "promotion"));
				} else {
					hint = "Skipped Sponsor";
				}
				controller.showHint(hint, 2000);

				controller.showUndoSkip(segment.asPair(), s -> {
					engine.seekTo(s[0]);
					ignoredSponsorStarts.add(s[0]);
				});
			});
		});
	}

	private boolean isCodecError(@NonNull PlaybackException error) {
		if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
				|| error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
			return true;
		}
		Throwable cause = error.getCause();
		while (cause != null) {
			if (cause instanceof android.media.MediaCodec.CodecException) return true;
			if (cause instanceof IllegalArgumentException) {
				StackTraceElement[] stack = cause.getStackTrace();
				if (stack.length > 0 && stack[0].getClassName().contains("MediaCodec")) return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	private void handleCodecError(@NonNull PlaybackException error) {
		String quality = engine.getQuality();
		if (quality != null && (quality.contains("2160") || quality.contains("1440") || quality.contains("av01") || quality.contains("vp9"))) {
			activity.runOnUiThread(() -> {
				ToastUtils.show(activity, "Codec issue detected, falling back to 1080p...");
				engine.onQualitySelected("1080p");
			});
		} else if (quality != null && !quality.equals("720p")) {
			activity.runOnUiThread(() -> {
				ToastUtils.show(activity, "Codec issue detected, falling back to 720p...");
				engine.onQualitySelected("720p");
			});
		} else {
			ErrorDialog.show(activity, "Hardware decoder failure: " + error.getMessage(), error);
		}
	}

	private boolean shouldRetryOnSourceError(@NonNull PlaybackException error) {
		if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
				|| error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
				|| error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
				|| error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
				|| error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
				|| error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
			return true;
		}
		Throwable cause = error.getCause();
		if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
			int code = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
			return code == 403 || code == 429 || code == 410;
		}
		return false;
	}

	public void skipToNext() {
		engine.skipToNext();
	}

	public void skipToPrevious() {
		engine.skipToPrevious();
	}

	public void addToQueue(String url, @Nullable String title) {
		QueueItem item = new QueueItem();
		item.setVideoUrl(url);
		item.setTitle(title != null ? title : "Loading...");
		item.setVideoId(YoutubeExtractor.getVideoId(url));
		queueRepository.add(item);
		ToastUtils.show(activity, R.string.queue_item_added);
	}

	public List<QueueItem> getQueue() {
		return queueRepository.getItems();
	}

	public void applySubtitleStyle() {
		playerView.post(() -> {
			SubtitleView subView = playerView.getCustomSubtitleView();
			if (subView == null) return;

			int styleId = kv.decodeInt(KEY_SUBTITLE_STYLE, 1);
			subView.setViewType(SubtitleView.VIEW_TYPE_CANVAS);
			subView.setApplyEmbeddedStyles(false);
			subView.setApplyEmbeddedFontSizes(false);
			subView.setBottomPaddingFraction(0.08f);

			if (styleId == 4) {
				float size = kv.decodeFloat(KEY_SUBTITLE_CUSTOM_TEXT_SIZE, 20f);
				int textColor = kv.decodeInt(KEY_SUBTITLE_CUSTOM_TEXT_COLOR, Color.WHITE);
				int bgColor = kv.decodeInt(KEY_SUBTITLE_CUSTOM_BG_COLOR, Color.BLACK);
				int opacity = kv.decodeInt(KEY_SUBTITLE_CUSTOM_BG_OPACITY, 128);
				int edgeType = kv.decodeInt(KEY_SUBTITLE_CUSTOM_EDGE_TYPE, CaptionStyleCompat.EDGE_TYPE_NONE);
				int edgeColor = kv.decodeInt(KEY_SUBTITLE_CUSTOM_EDGE_COLOR, Color.BLACK);

				int finalBgColor = Color.argb(opacity, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));
				subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size);

				CaptionStyleCompat style = new CaptionStyleCompat(textColor, finalBgColor, Color.TRANSPARENT, edgeType, edgeColor, null);
				subView.setStyle(style);
			} else {
				subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
				CaptionStyleCompat style = switch (styleId) {
					case 2 ->
							new CaptionStyleCompat(Color.YELLOW, Color.parseColor("#CC000000"), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT, null);
					case 3 ->
							new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
					default ->
							new CaptionStyleCompat(Color.WHITE, Color.parseColor("#CC000000"), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT, null);
				};
				subView.setStyle(style);
			}
			subView.invalidate();
			subView.requestLayout();
		});
	}

	private void updateServiceProgress(boolean isPlaying) {
		if (playbackService != null)
			playbackService.updateProgress(engine.position(), engine.getPlaybackRate(), isPlaying);
	}

	public void attachPlaybackService(@Nullable PlaybackService service) {
		this.playbackService = service;
		if (service != null) {
			service.initialize(engine);
			refreshQueueNavigationAvailability();
		}
	}

	public void refreshQueueNavigationAvailability() {
		final QueueNav availability = engine.getQueueNavigationAvailability();
		activity.runOnUiThread(() -> controller.refreshQueueNavigationAvailability(availability));
		if (playbackService != null) {
			playbackService.updateQueueNavigationAvailability(availability);
		}
	}

	public void refreshInternalButtonVisibility() {
		activity.runOnUiThread(controller::refreshInternalButtonVisibility);
	}

	public void reload() {
		if (currentUrl != null) {
			final String url = currentUrl;
			this.vid = null;
			this.loadedVideoId = null;
			play(url);
		}
	}

	public void play(String url) {
		play(url, -1L);
	}

	public void play(String url, final long initialPositionMs) {
		final boolean isMusic = UrlUtils.isMusicUrl(url);
		this.currentUrl = url;
		final String videoId = YoutubeExtractor.getVideoId(url);
		if (videoId == null || (Objects.equals(this.vid, videoId) && initialPositionMs < 0L)) return;
		this.vid = videoId;
		activity.runOnUiThread(() -> {
			if (inAppMiniPlayer) exitInAppMiniPlayer();
			engine.clear();
			playerView.setTitle(null);
			final SponsorOverlayView layer = playerView.findViewById(R.id.sponsor_overlay);
			if (layer != null) layer.setData(null, 0, TimeUnit.MILLISECONDS);
			final DefaultTimeBar bar = playerView.findViewById(R.id.exo_progress);
			if (bar != null) bar.setAdGroupTimesMs(null, null, 0);

			if (isMusic) {
				playerView.hide();
				playerView.resetMiniPlayerTouchTracking();
			} else {
				playerView.show();
				playerView.resetMiniPlayerTouchTracking();
			}
			controller.syncRotation(DeviceUtils.isRotateOn(activity), activity.getResources().getConfiguration().orientation);
		});

		cancelCurrentExtraction();
		final ExtractionSession session = new ExtractionSession();
		extractionSession = session;

		extractor.getInfo(url, session).thenAccept(er -> {
			CompletableFuture.runAsync(() -> sponsor.load(videoId));

			activity.runOnUiThread(() -> {
				if (this.extractionSession == session) this.extractionSession = null;
				if (!Objects.equals(this.vid, videoId)) return;
				this.loadedVideoId = videoId;
				playerView.setTitle(er.video().getTitle());

				if (!isMusic) {
					playerView.updateSkipMarkers(er.video().getDuration(), TimeUnit.SECONDS);
				}

				final List<QueueItem> items = queueRepository.getItems();
				for (QueueItem item : items) {
					if (Objects.equals(item.getVideoId(), videoId)) {
						item.setTitle(er.video().getTitle());
						item.setAuthor(er.video().getAuthor());
						item.setThumbnailUrl(er.video().getThumbnailUrl());
						queueRepository.add(item);
						break;
					}
				}

				String preferredQuality = isMusic ? "Audio" : prefs.getQuality();
				PlaybackPlan plan = PlaybackPlanner.plan(er.deliveries(), preferredQuality, null);
				PlaybackDetails details = new PlaybackDetails(er.video(), er.catalog(), er.deliveries(), plan, er.segments(), er.subtitles());

				engine.play(details);
				if (initialPositionMs >= 0L) {
					engine.seekTo(initialPositionMs);
				}

				if (!isMusic) {
					controller.updateSegmentsButtonState();
					controller.updateSubtitleButtonState();
				}

				if (playbackService != null) {
					PlaybackService.start(activity);
					playbackService.showNotification(er.video().getTitle(), er.video().getAuthor(), er.video().getThumbnailUrl(), er.video().getDuration() * 1000);
				}
				refreshQueueNavigationAvailability();
			});
		}).exceptionally(e -> {
			if (this.extractionSession == session) this.extractionSession = null;
			Throwable cause = e instanceof CompletionException ? e.getCause() : e;
			activity.runOnUiThread(() -> {
				if (!Objects.equals(this.vid, videoId)) return;
				ErrorDialog.show(activity, cause != null ? cause.getMessage() : "Unknown error", cause);
			});
			return null;
		});
	}

	public void playLocal(@NonNull Uri uri, @Nullable String title, @Nullable List<DownloadRecord> subtitles) {
		this.vid = null;
		this.currentUrl = null;
		cancelCurrentExtraction();
		activity.runOnUiThread(() -> {
			if (inAppMiniPlayer) exitInAppMiniPlayer();
			engine.clear();
			playerView.setTitle(title);
			playerView.show();
			playerView.resetMiniPlayerTouchTracking();
			engine.playLocal(uri, title, subtitles);
			controller.syncRotation(DeviceUtils.isRotateOn(activity), activity.getResources().getConfiguration().orientation);
		});
	}

	public void addLocalSubtitle(@NonNull Uri uri, @NonNull String label) {
		engine.addLocalSubtitle(uri, label);
	}

	public void hide() {
		this.vid = null;
		this.loadedVideoId = null;
		cancelCurrentExtraction();
		activity.runOnUiThread(() -> {
			playerView.hide();
			playerView.resetMiniPlayerTouchTracking();
			engine.clear();
			controller.clearRotation();
			playerView.disableAutoPiP();
			exitInAppMiniPlayer();
			setMiniPlayerCallbacks(null, null);
			if (playbackService != null) {
				playbackService.hideNotification();
			}
		});
	}

	public void play() {
		engine.play();
	}

	public void pause() {
		engine.pause();
	}

	public float getVolume() {
		return engine.getVolume();
	}

	public void setVolume(float volume) {
		engine.setVolume(volume);
	}

	public void seekToIfLoaded(final long positionMs) {
		if (loadedVideoId == null || positionMs < 0L) return;
		activity.runOnUiThread(() -> engine.seekTo(positionMs));
	}

	public boolean seekLoadedVideo(@Nullable final String url, final long positionMs) {
		if (positionMs < 0L || url == null) return false;
		final String videoId = YoutubeExtractor.getVideoId(url);
		if (videoId == null || !Objects.equals(loadedVideoId, videoId)) return false;
		seekToIfLoaded(positionMs);
		return true;
	}

	public long getResumePosition(@Nullable String videoId) {
		return prefs.getResumePosition(videoId);
	}

	public boolean isFullscreen() {
		return controller.isFullscreen();
	}

	public void enterFullscreen() {
		controller.enterFullscreen();
	}

	public void exitFullscreen() {
		controller.exitFullscreen();
	}

	public void syncRotation(final boolean autoRotate, final int orientation) {
		controller.syncRotation(autoRotate, orientation);
	}

	public void enterPictureInPicture() {
		playerView.enterPiP();
	}

	public boolean canSuspendWatch() {
		return playerView.getVisibility() == View.VISIBLE;
	}

	public void enterInAppMiniPlayer() {
		inAppMiniPlayer = true;
		playerView.enterInAppMiniPlayer();
		controller.enterMiniPlayer();
	}

	public void exitInAppMiniPlayer() {
		inAppMiniPlayer = false;
		playerView.exitInAppMiniPlayer();
		controller.exitMiniPlayer();
	}

	public void restoreInAppMiniPlayerUiIfNeeded() {
		if (!inAppMiniPlayer) return;
		playerView.show();
		playerView.enterInAppMiniPlayer();
		controller.enterMiniPlayer();
	}

	public void suspendInAppMiniPlayerUiIfNeeded() {
		if (!inAppMiniPlayer) return;
		playerView.hide();
	}

	public void stopAndCloseFromMiniPlayer() {
		hide();
	}

	public void setMiniPlayerCallbacks(@Nullable final Runnable onRestore, @Nullable final Runnable onClose) {
		onMiniPlayerRestore = onRestore;
		onMiniPlayerClose = onClose;
		playerView.setMiniPlayerCallbacks(
				onRestore != null ? this::dispatchMiniPlayerRestore : null,
				onClose != null ? this::dispatchMiniPlayerClose : null,
				engine::play,
				engine::pause);
	}

	public boolean shouldAutoEnterPictureInPicture() {
		return playerView.getVisibility() == View.VISIBLE;
	}

	public void onPictureInPictureModeChanged(final boolean isInPiP) {
		controller.onPictureInPictureModeChanged(isInPiP);
		playerView.onPiPModeChanged();
		if (!isInPiP) playerView.disableAutoPiP();
		if (wasInPictureInPicture && !isInPiP && inAppMiniPlayer && onMiniPlayerRestore != null) {
			dispatchMiniPlayerRestore();
		}
		wasInPictureInPicture = isInPiP;
	}

	public void setHeight(int height) {
		playerView.post(() -> playerView.setHeight(height));
	}

	public void setBottomOffset(int offset) {
		playerView.post(() -> playerView.setBottomOffset(offset));
	}

	@Nullable
	public String getLoadedVideoId() {
		return loadedVideoId;
	}

	@NonNull
	public PlayerLoopMode getLoopMode() {
		return controller.getLoopMode();
	}

	public void setLoopMode(@NonNull final PlayerLoopMode mode) {
		controller.setLoopMode(mode);
	}

	public void release() {
		cancelCurrentExtraction();
		loadedVideoId = null;
		wasInPictureInPicture = false;
		onMiniPlayerRestore = null;
		onMiniPlayerClose = null;
		activity.runOnUiThread(() -> {
			playerView.disableAutoPiP();
			playerView.setMiniPlayerCallbacks(null, null, null, null);
		});
		inAppMiniPlayer = false;
		engine.release();
	}

	private void dispatchMiniPlayerRestore() {
		if (onMiniPlayerRestore != null) onMiniPlayerRestore.run();
	}

	private void dispatchMiniPlayerClose() {
		final Runnable onClose = onMiniPlayerClose;
		if (onClose == null) return;

		playerView.closeInAppMiniPlayerWithFade(() -> {
			stopAndCloseFromMiniPlayer();
			onClose.run();
		});
	}

	private void cancelCurrentExtraction() {
		if (extractionSession == null) return;
		extractionSession.cancel();
		extractionSession = null;
	}
}