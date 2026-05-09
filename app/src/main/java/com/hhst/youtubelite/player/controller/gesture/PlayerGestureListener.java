package com.hhst.youtubelite.player.controller.gesture;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.Constant;
import com.hhst.youtubelite.player.LitePlayerView;
import com.hhst.youtubelite.player.controller.Controller;
import com.hhst.youtubelite.player.engine.Engine;
import com.tencent.mmkv.MMKV;

import java.util.Locale;

@UnstableApi
public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
	private static final int AUTO_HIDE_DELAY_MS = 200;
	private static final int SEEK_CONTINUATION_WINDOW_MS = 600;
	private static final int HINT_HIDE_FAST_MS = 500;
	private static final float FULLSCREEN_SWIPE_THRESHOLD_RATIO = 0.08f;
	private static final float MINI_PLAYER_SWIPE_THRESHOLD_RATIO = 0.15f;
	private static final float LEFT_ZONE_MAX_RATIO = 1f / 3f;
	private static final float RIGHT_ZONE_MIN_RATIO = 2f / 3f;
	private static final float GESTURE_VERTICAL_LIMIT_TOP = 0.2f;
	private static final float GESTURE_VERTICAL_LIMIT_BOTTOM = 0.8f;

	private final Activity activity;
	private final LitePlayerView playerView;
	private final Engine engine;
	private final Controller controller;
	private final Handler handler;
	private final Runnable hideHintRunnable;
	private final MMKV kv = MMKV.defaultMMKV();

	private int gestureMode = 0;
	private float bri = -1, preLongPressSpeed = 1.0f;
	private boolean isLongPressing = false, isGesturing = false, fullscreenSwipeTriggered = false;
	private boolean miniPlayerSwipeTriggered = false;
	private long scrollStartPosition = 0;

	private int cumulativeSeekAmount = 0;
	private final Runnable resetSeekRunnable = () -> cumulativeSeekAmount = 0;
	private long lastTapTime = 0;
	private float vol = -1;
	private boolean volumeWarningShowing = false;

	enum DoubleTapAction {
		SEEK_BACKWARD,
		TOGGLE_PLAYBACK,
		SEEK_FORWARD
	}

	public PlayerGestureListener(Activity activity, LitePlayerView playerView, Engine engine, Controller controller) {
		this.activity = activity;
		this.playerView = playerView;
		this.engine = engine;
		this.controller = controller;
		this.handler = new Handler(activity.getMainLooper());
		this.hideHintRunnable = controller::hideHint;
	}

	private boolean isEnabled() {
		return controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURES);
	}

	public void onTouchRelease() {
		if (!isEnabled()) return;
		if (isLongPressing) {
			engine.setPlaybackRate(preLongPressSpeed);
			controller.updateSpeedButtonUI(preLongPressSpeed);
			controller.hideHint();
			isLongPressing = false;
		}
		if (isGesturing) {
			handler.postDelayed(hideHintRunnable, AUTO_HIDE_DELAY_MS);
			isGesturing = false;
		}
	}

	@Override
	public boolean onDown(@NonNull MotionEvent e) {
		if (!isEnabled()) return false;
		handler.removeCallbacks(hideHintRunnable);
		gestureMode = 0;
		bri = -1;
		vol = -1;
		isGesturing = false;
		fullscreenSwipeTriggered = false;
		miniPlayerSwipeTriggered = false;
		scrollStartPosition = engine.position();
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
		controller.setControlsVisible(!controller.isControlsVisible());
		return true;
	}

	@Override
	public boolean onSingleTapUp(@NonNull MotionEvent e) {
		if (!isEnabled()) return false;
		if (!controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_SEEK)) return super.onSingleTapUp(e);
		
		long currentTime = System.currentTimeMillis();
		float x = e.getX();
		float width = playerView.getWidth();
		final DoubleTapAction action = resolveDoubleTapAction(x, width);

		if (cumulativeSeekAmount != 0 && (currentTime - lastTapTime < SEEK_CONTINUATION_WINDOW_MS)) {
			if ((cumulativeSeekAmount < 0 && action == DoubleTapAction.SEEK_BACKWARD)
							|| (cumulativeSeekAmount > 0 && action == DoubleTapAction.SEEK_FORWARD)) {
				processSeek(action == DoubleTapAction.SEEK_BACKWARD);
				lastTapTime = currentTime;
				return true;
			}
		}
		return super.onSingleTapUp(e);
	}

	@Override
	public boolean onDoubleTap(@NonNull MotionEvent e) {
		if (!isEnabled()) return false;
		DoubleTapAction action = resolveDoubleTapAction(e.getX(), playerView.getWidth());
		
		if (action == DoubleTapAction.TOGGLE_PLAYBACK) {
			if (engine.isPlaying()) {
				engine.pause();
			} else {
				engine.play();
			}
			controller.setControlsVisible(true);
			return true;
		}
		
		if (!controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_SEEK)) return false;

		switch (action) {
			case SEEK_BACKWARD:
				processSeek(true);
				lastTapTime = System.currentTimeMillis();
				return true;
			case SEEK_FORWARD:
				processSeek(false);
				lastTapTime = System.currentTimeMillis();
				return true;
			default:
				return false;
		}
	}

	static DoubleTapAction resolveDoubleTapAction(final float x, final float width) {
		if (width <= 0f) {
			return DoubleTapAction.TOGGLE_PLAYBACK;
		}
		if (x < width * LEFT_ZONE_MAX_RATIO) {
			return DoubleTapAction.SEEK_BACKWARD;
		}
		if (x > width * RIGHT_ZONE_MIN_RATIO) {
			return DoubleTapAction.SEEK_FORWARD;
		}
		return DoubleTapAction.TOGGLE_PLAYBACK;
	}

	private void processSeek(boolean isLeft) {
		handler.removeCallbacks(resetSeekRunnable);
		String seekAmountStr = kv.decodeString("preferences:" + com.hhst.youtubelite.extension.Constant.DOUBLE_TAP_SEEK_AMOUNT, "10s");
		int seekSeconds = 10;
		if (seekAmountStr != null) {
			try {
				seekSeconds = Integer.parseInt(seekAmountStr.replace("s", ""));
			} catch (Exception ignored) {}
		}
		
		long seekStep = seekSeconds * 1000L;
		if (isLeft) {
			cumulativeSeekAmount -= seekSeconds;
			engine.seekBy(-seekStep);
			controller.showHint(cumulativeSeekAmount + "s", HINT_HIDE_FAST_MS);
		} else {
			cumulativeSeekAmount += seekSeconds;
			engine.seekBy(seekStep);
			controller.showHint("+" + cumulativeSeekAmount + "s", HINT_HIDE_FAST_MS);
		}
		handler.postDelayed(resetSeekRunnable, SEEK_CONTINUATION_WINDOW_MS);
	}

	@Override
	public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
		if (!isEnabled() || e1 == null || e2.getPointerCount() > 1 || isLongPressing || volumeWarningShowing) return false;
		
		float x = e1.getX(), width = playerView.getWidth();
		float y = e1.getY(), height = playerView.getHeight();

		if (gestureMode == 0) {
			if (Math.abs(dy) > Math.abs(dx)) {
				if ((x < width * 0.35f || x > width * 0.65f) && (y < height * GESTURE_VERTICAL_LIMIT_TOP || y > height * GESTURE_VERTICAL_LIMIT_BOTTOM)) {
					return false;
				}
				gestureMode = 1;
			} else if (Math.abs(dx) > Math.abs(dy)) {
				gestureMode = 2;
			}
		}
		
		if (gestureMode == 1) {
			isGesturing = true;
			handler.removeCallbacks(hideHintRunnable);
			if (x < width * 0.35f) {
				if (controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_BRIGHTNESS)) {
					adjustBrightness(dy);
				}
			} else if (x > width * 0.65f) {
				if (controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_VOLUME)) {
					adjustVolume(dy);
				}
			} else {
				handleVerticalSwipe(e1, e2);
			}
			handler.postDelayed(hideHintRunnable, AUTO_HIDE_DELAY_MS);
		} else if (gestureMode == 2) {
			if (controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_SEEK)) {
				isGesturing = true;
				handler.removeCallbacks(hideHintRunnable);
				adjustSeek(e1, e2);
				handler.postDelayed(hideHintRunnable, AUTO_HIDE_DELAY_MS);
			}
		}
		return true;
	}

	private void handleVerticalSwipe(@NonNull MotionEvent e1, @NonNull MotionEvent e2) {
		final float deltaY = e2.getY() - e1.getY();
		if (playerView.isFs()) {
			handleFullscreenVerticalGesture(deltaY);
		} else {
			handlePortraitVerticalGesture(deltaY);
		}
	}

	private void handleFullscreenVerticalGesture(float deltaY) {
		if (fullscreenSwipeTriggered || !controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_FULLSCREEN_SWIPE)) return;
		final float threshold = playerView.getHeight() * FULLSCREEN_SWIPE_THRESHOLD_RATIO;
		if (Math.abs(deltaY) < threshold) return;

		if (deltaY > 0) {
			fullscreenSwipeTriggered = true;
			controller.exitFullscreen();
		}
	}

	private void handlePortraitVerticalGesture(float deltaY) {
		if (playerView.isFs()) return;
		final float threshold = playerView.getHeight() * MINI_PLAYER_SWIPE_THRESHOLD_RATIO;
		if (Math.abs(deltaY) < threshold) return;

		if (deltaY > 0) {
			if (miniPlayerSwipeTriggered || !controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_MINIPLAYER_SWIPE)) return;
			miniPlayerSwipeTriggered = true;
			if (controller.getExtensionManager().isEnabled(Constant.ENABLE_IN_APP_MINI_PLAYER)) {
				activity.onBackPressed();
			}
		} else {
			if (fullscreenSwipeTriggered || !controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_FULLSCREEN_SWIPE)) return;
			fullscreenSwipeTriggered = true;
			controller.enterFullscreen();
		}
	}

	private void adjustSeek(MotionEvent e1, MotionEvent e2) {
		float distanceX = e2.getX() - e1.getX();
		float width = playerView.getWidth();
		long maxSeekRange = 120000;
		long seekOffset = (long) ((distanceX / width) * maxSeekRange);
		long targetPosition = scrollStartPosition + seekOffset;
		engine.seekTo(targetPosition);
		controller.showHint(formatTime(targetPosition), -1);
	}

	private String formatTime(long ms) {
		if (ms < 0) ms = 0;
		int seconds = (int) (ms / 1000) % 60;
		int minutes = (int) ((ms / (1000 * 60)) % 60);
		int hours = (int) ((ms / (1000 * 60 * 60)) % 24);
		if (hours > 0)
			return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
		return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
	}

	private void adjustBrightness(float dy) {
		WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
		if (bri == -1) {
			bri = lp.screenBrightness;
			if (bri < 0) {
				try {
					float systemBri = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
					bri = systemBri / 255.0f;
				} catch (Settings.SettingNotFoundException e) {
					bri = 0.5f;
				}
			}
		}
		float delta = (dy / playerView.getHeight()) * 1.5f;
		bri = Math.min(Math.max(bri + delta, 0.01f), 1.0f);
		lp.screenBrightness = bri;
		activity.getWindow().setAttributes(lp);
		controller.showHint(Math.round(bri * 100) + "%", -1);
	}

	private void adjustVolume(float dy) {
		final AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
		if (am == null) return;
		final int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		boolean boosterEnabled = controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_VOLUME_BOOSTER);
		
		if (vol == -1) {
			float systemVol = (float) am.getStreamVolume(AudioManager.STREAM_MUSIC);
			float engineVol = engine.getVolume();
			if (engineVol > 1.0f && boosterEnabled) {
				vol = (float) max + (engineVol - 1.0f) * (float) max;
			} else {
				vol = systemVol;
			}
		}

		float delta = (dy / playerView.getHeight()) * (float) max * 1.2f;
		float targetVol = vol + delta;
		float limit = boosterEnabled ? (float) max * 2.0f : (float) max;
		final float newVol = Math.min(Math.max(targetVol, 0), limit);

		int percentage = Math.round((newVol / (float) max) * 100);

		if (percentage > 100 && !kv.decodeBool("volume_booster_warning_dont_show", false) && vol <= (float) max) {
			volumeWarningShowing = true;
			controller.showVolumeWarning(confirmed -> {
				volumeWarningShowing = false;
				if (confirmed) {
					applyVolume(newVol, max, am);
				}
			});
			return;
		}

		applyVolume(newVol, max, am);
	}

	private void applyVolume(float newVol, int max, AudioManager am) {
		vol = newVol;
		int percentage = Math.round((vol / (float) max) * 100);
		
		if (vol > (float) max) {
			am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
			engine.setVolume(1.0f + (vol - (float) max) / (float) max);
			controller.showHint(percentage + "%", -1, Color.RED);
		} else {
			am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(vol), 0);
			engine.setVolume(1.0f);
			controller.showHint(percentage + "%", -1);
		}
	}

	@Override
	public void onLongPress(@NonNull MotionEvent e) {
		if (!isEnabled() || !engine.isPlaying()) return;
		if (!controller.getExtensionManager().isEnabled(com.hhst.youtubelite.extension.Constant.PLAYER_GESTURE_2X)) return;

		preLongPressSpeed = engine.getPlaybackRate();
		isLongPressing = true;
		engine.setPlaybackRate(2.0f);
		controller.updateSpeedButtonUI(2.0f);
		controller.showHint("2x", -1);
	}
}
