package com.hhst.youtubelite.player;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Rect;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.hhst.youtubelite.R;
import com.hhst.youtubelite.player.common.Constant;
import com.hhst.youtubelite.player.common.PlayerPreferences;
import com.hhst.youtubelite.player.controller.ControllerMachine;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.hhst.youtubelite.player.sponsor.SponsorOverlayView;
import com.hhst.youtubelite.util.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.scopes.ActivityScoped;
import lombok.Getter;

@UnstableApi
@AndroidEntryPoint
@ActivityScoped
public class LitePlayerView extends PlayerView {

	private static final float SUBTITLE_LINE_FRACTION = 0.92f;
	private static final float SUBTITLE_POSITION_FRACTION = 0.5f;
	private static final int MINI_CONTROL_DEFAULT_SPACE_DP = 18;
	private static final int MINI_SIDE_CONTROL_SIZE_DP = 30;
	private static final int MINI_CENTER_CONTROL_SIZE_DP = 34;
	private static final long MINI_TRANSITION_MS = 260L;
	private static final int[] MINI_PLAYER_TAP_TARGET_IDS = {
					R.id.btn_mini_close,
					R.id.btn_mini_play,
					R.id.btn_mini_pause,
					R.id.btn_mini_restore
	};
	@Inject
	SponsorBlockManager sponsor;
	@Inject
	Activity activity;
	@Inject
	PlayerPreferences prefs;
	@Nullable
	private SubtitleView subtitleView;
	@Getter
	private boolean isFs = false;
	private int playerWidth = 0;
	private int playerHeight = 0;
	private int normalHeight = 0;
	@Getter
	private boolean inAppMiniPlayer = false;
	@Nullable
	private Runnable onMiniPlayerRestore;
	@Nullable
	private Runnable onMiniPlayerClose;
	@Nullable
	private Runnable onMiniPlayerPlay;
	@Nullable
	private Runnable onMiniPlayerPause;
	@Nullable
	private Runnable onMiniPlayerBackgroundTap;
	private int miniPlayerRestoreResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
	private boolean miniPlayerRestoreFullscreen;
	private float miniPlayerTouchDownRawX;
	private float miniPlayerTouchDownRawY;
	private float miniPlayerStartTranslationX;
	private float miniPlayerStartTranslationY;
	private float miniPlayerSavedTranslationX;
	private float miniPlayerSavedTranslationY;
	private boolean miniPlayerTranslationStashedForFullscreen;
	private boolean miniPlayerTouchCaptured;
	private boolean miniPlayerDragging;
	private boolean miniPlayerResizing;
	@Nullable
	private View miniPlayerPendingTapTarget;
	private float miniPlayerPinchStartDistancePx;
	private int miniPlayerPinchStartWidthPx;
	private int miniPlayerWidthOverrideDp = MiniPlayerLayout.NO_WIDTH_OVER_DP;
	private boolean miniAnimating;
	private int miniAnimToken;
	private int bottomOffsetPx = 0;

	public interface TouchInterceptListener {
		boolean onInterceptTouch(MotionEvent event);
	}

	@Nullable
	private TouchInterceptListener touchInterceptListener;

	public LitePlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public LitePlayerView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public LitePlayerView(Context context) {
		super(context);
	}

	public void setTouchInterceptListener(@Nullable TouchInterceptListener listener) {
		this.touchInterceptListener = listener;
	}

	public void setup() {
		setControllerAnimationEnabled(false);
		setControllerHideOnTouch(false);
		setControllerAutoShow(false);
		setControllerShowTimeoutMs(0);
		setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View view, Outline outline) {
				outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), ViewUtils.dpToPx(activity, 16));
			}
		});
		setClipToOutline(false);
		setResizeMode(prefs.getResizeMode());
		updateNormalHeight();
		updatePlayerLayout(false);

		addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			if (right - left != playerWidth || bottom - top != playerHeight) {
				playerWidth = right - left;
				playerHeight = bottom - top;
			}
			if (inAppMiniPlayer) invalidateOutline();
		});
	}

	private void updateNormalHeight() {
		DisplayMetrics dm = activity.getResources().getDisplayMetrics();
		int portraitWidth = Math.min(dm.widthPixels, dm.heightPixels);
		normalHeight = (int) (portraitWidth * 9 / 16.0);
	}

	public void applyControllerState(@NonNull ControllerMachine.State previousState,
	                                 @NonNull ControllerMachine.State newState,
	                                 boolean isPortraitVideo,
	                                 int fsOrientation,
	                                 int defaultResizeMode) {
		post(() -> {
			switch (newState) {
				case NORMAL, MINI_PLAYER -> applyNormalState(defaultResizeMode);
				case FULLSCREEN_UNLOCKED, FULLSCREEN_LOCKED ->
								applyFullscreenState(previousState, isPortraitVideo, fsOrientation, defaultResizeMode);
				case PIP -> applyPictureInPictureState(previousState);
			}
		});
	}

	public void updatePlayerLayout(boolean fullscreen) {
		ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ConstraintLayout.LayoutParams params) {
			if (inAppMiniPlayer && !fullscreen) {
				applyMiniPlayerLayout(params);
				restoreMini();
				miniPlayerTranslationStashedForFullscreen = false;
				updateMiniPlayerCornerClipping();
				return;
			}

			if (isAttachedToWindow() && getParent() instanceof ViewGroup parent) {
				TransitionManager.beginDelayedTransition(parent, new AutoTransition().setDuration(250));
			}

			if (fullscreen && inAppMiniPlayer) {
				if (!miniPlayerTranslationStashedForFullscreen) {
					saveCurrentMiniPlayerTranslation();
					miniPlayerTranslationStashedForFullscreen = true;
				}
				setTranslationX(0.0f);
				setTranslationY(0.0f);
			} else {
				resetMiniPlayerTranslation();
			}
			applyStandardPlayerAnchors(params);
			if (fullscreen) {
				params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
				params.height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
				params.topMargin = 0;
				params.rightMargin = 0;
				params.bottomMargin = 0;
				params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
				params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
			} else {
				params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT;
				updateNormalHeight();
				params.height = normalHeight;
				params.topMargin = ViewUtils.dpToPx(activity, Constant.TOP_MARGIN_DP);
				params.rightMargin = 0;
				params.bottomMargin = 0;
				params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
				params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
			}
			setLayoutParams(params);
			updateMiniPlayerCornerClipping();
		}
	}

	public void enterPiP() {
		if (activity.isInPictureInPictureMode()) return;
		if (!isFs && !inAppMiniPlayer) {
			updateNormalHeight();
		}
		PictureInPictureParams params = buildPiPParams(true);
		try {
			activity.enterPictureInPictureMode(params);
		} catch (Exception e) {
			activity.enterPictureInPictureMode(new PictureInPictureParams.Builder()
							.setAspectRatio(new Rational(16, 9))
							.build());
		}
	}

	public void disableAutoPiP() {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;
		activity.setPictureInPictureParams(buildPiPParams(false));
	}

	public void enterInAppMiniPlayer() {
		if (inAppMiniPlayer) return;
		show();
		float startX = getX();
		float startY = getY();
		int startWidth = getWidth();
		int startHeight = getHeight();
		stopMiniTransition();
		miniPlayerRestoreResizeMode = getResizeMode();
		miniPlayerRestoreFullscreen = isFs;
		inAppMiniPlayer = true;
		final SubtitleView sv = getCustomSubtitleView();
		if (sv != null) sv.setCues(Collections.emptyList());
		loadPersistedMiniPlayerLayoutState();
		updatePlayerLayout(false);
		updateMiniPlayerInteractionHandlers();
		animateMiniTransition(startX, startY, startWidth, startHeight);
	}

	public void exitInAppMiniPlayer() {
		if (!inAppMiniPlayer) return;
		float startX = getX();
		float startY = getY();
		int startWidth = getWidth();
		int startHeight = getHeight();
		stopMiniTransition();
		inAppMiniPlayer = false;
		resetMiniPlayerTouchTracking();
		miniPlayerWidthOverrideDp = MiniPlayerLayout.NO_WIDTH_OVER_DP;
		resetMiniPlayerTranslation();
		updatePlayerLayout(miniPlayerRestoreFullscreen);
		setResizeMode(miniPlayerRestoreResizeMode);
		updateMiniPlayerInteractionHandlers();
		animateMiniTransition(startX, startY, startWidth, startHeight);
	}

	public void setMiniPlayerCallbacks(@Nullable Runnable onRestore, @Nullable Runnable onClose,
	                                   @Nullable Runnable onPlay, @Nullable Runnable onPause) {
		onMiniPlayerRestore = onRestore;
		onMiniPlayerClose = onClose;
		onMiniPlayerPlay = onPlay;
		onMiniPlayerPause = onPause;
		updateMiniPlayerInteractionHandlers();
	}

	public void setOnMiniPlayerBackgroundTap(@Nullable Runnable onBackgroundTap) {
		onMiniPlayerBackgroundTap = onBackgroundTap;
	}

	private void updateMiniPlayerInteractionHandlers() {
		ImageButton closeButton = findViewById(R.id.btn_mini_close);
		ImageButton restoreButton = findViewById(R.id.btn_mini_restore);
		ImageButton playButton = findViewById(R.id.btn_mini_play);
		ImageButton pauseButton = findViewById(R.id.btn_mini_pause);
		setMiniPlayerButtonAction(closeButton, onMiniPlayerClose);
		setMiniPlayerButtonAction(restoreButton, onMiniPlayerRestore);
		setMiniPlayerButtonAction(playButton, onMiniPlayerPlay);
		setMiniPlayerButtonAction(pauseButton, onMiniPlayerPause);
	}

	private void setMiniPlayerButtonAction(@Nullable ImageButton button, @Nullable Runnable action) {
		if (button == null) return;
		button.setOnClickListener(v -> {
			if (inAppMiniPlayer && action != null) action.run();
		});
	}

	public boolean handleMiniPlayerTouch(@NonNull MotionEvent event) {
		if (!inAppMiniPlayer) return false;
		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN -> {
				View tapTarget = getMiniPlayerTapTarget(event);
				captureMiniPlayerTouchStart(event);
				miniPlayerPendingTapTarget = tapTarget;
				return true;
			}
			case MotionEvent.ACTION_POINTER_DOWN -> {
				if (event.getPointerCount() < 2) {
					return miniPlayerTouchCaptured;
				}
				clearMiniPlayerPendingTapTarget();
				startMiniPlayerResize(event);
				return true;
			}
			case MotionEvent.ACTION_MOVE -> {
				if (miniPlayerResizing) {
					updateMiniPlayerSizeByPinch(event);
					return true;
				}
				if (!miniPlayerTouchCaptured) return false;
				float deltaX = event.getRawX() - miniPlayerTouchDownRawX;
				float deltaY = event.getRawY() - miniPlayerTouchDownRawY;
				if (!miniPlayerDragging && exceedsTouchSlop(deltaX, deltaY)) {
					miniPlayerDragging = true;
					clearMiniPlayerPendingTapTarget();
				}
				if (miniPlayerDragging) {
					moveMini(
									miniPlayerStartTranslationX + deltaX,
									miniPlayerStartTranslationY + deltaY);
				}
				return true;
			}
			case MotionEvent.ACTION_POINTER_UP -> {
				if (!miniPlayerResizing) return miniPlayerTouchCaptured;
				finishMiniResize();
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				if (miniPlayerResizing) {
					finishMiniResize();
					return true;
				}
				if (!miniPlayerTouchCaptured) return false;
				View tap = miniPlayerPendingTapTarget;
				boolean wasDragging = miniPlayerDragging;
				resetMiniPlayerTouchTracking();
				if (wasDragging) {
					snapMini();
					return true;
				}
				if (tap != null) {
					tap.performClick();
				} else if (onMiniPlayerBackgroundTap != null) {
					onMiniPlayerBackgroundTap.run();
				}
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				if (miniPlayerResizing) {
					finishMiniResize();
					return true;
				}
				if (!miniPlayerTouchCaptured) return false;
				boolean wasDragging = miniPlayerDragging;
				resetMiniPlayerTouchTracking();
				if (wasDragging) {
					snapMini();
				}
				return true;
			}
			default -> {
				return miniPlayerTouchCaptured;
			}
		}
	}

	@Override
	public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
		if (miniAnimating) {
			return true;
		}
		if (inAppMiniPlayer && handleMiniPlayerTouch(event)) {
			return true;
		}
		if (touchInterceptListener != null && touchInterceptListener.onInterceptTouch(event)) {
			return true;
		}
		return super.dispatchTouchEvent(event);
	}

	private void animateMiniTransition(float startX,
	                                   float startY,
	                                   int startWidth,
	                                   int startHeight) {
		if (startWidth <= 0 || startHeight <= 0 || !isAttachedToWindow()) return;
		int token = ++miniAnimToken;
		miniAnimating = true;
		getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				ViewTreeObserver observer = getViewTreeObserver();
				if (observer.isAlive()) observer.removeOnPreDrawListener(this);
				if (token != miniAnimToken || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0) {
					finishMiniTransition(token);
					return true;
				}
				float endX = getX();
				float endY = getY();
				int endWidth = getWidth();
				int endHeight = getHeight();
				float endTranslationX = getTranslationX();
				float endTranslationY = getTranslationY();
				setPivotX(endWidth / 2.0f);
				setPivotY(endHeight / 2.0f);
				setScaleX(startWidth / (float) endWidth);
				setScaleY(startHeight / (float) endHeight);
				setTranslationX(endTranslationX + startX + startWidth / 2.0f - (endX + endWidth / 2.0f));
				setTranslationY(endTranslationY + startY + startHeight / 2.0f - (endY + endHeight / 2.0f));
				animate().cancel();
				animate()
								.translationX(endTranslationX)
								.translationY(endTranslationY)
								.scaleX(1.0f)
								.scaleY(1.0f)
								.setDuration(MINI_TRANSITION_MS)
								.setInterpolator(new OvershootInterpolator(0.7f))
								.withLayer()
								.withEndAction(() -> finishMiniTransition(token))
								.start();
				return true;
			}
		});
	}

	private void stopMiniTransition() {
		miniAnimating = false;
		miniAnimToken++;
		animate().cancel();
		setScaleX(1.0f);
		setScaleY(1.0f);
	}

	private void finishMiniTransition(int token) {
		if (token != miniAnimToken) return;
		miniAnimating = false;
		setScaleX(1.0f);
		setScaleY(1.0f);
	}

	private void applyMiniPlayerLayout(@NonNull ConstraintLayout.LayoutParams params) {
		MiniPlayerLayout.Spec spec = MiniPlayerLayout.computeSpec(
						getScreenWidthDp(),
						getBottomInsetDp(),
						miniPlayerWidthOverrideDp);
		params.width = ViewUtils.dpToPx(activity, spec.widthDp());
		params.height = ViewUtils.dpToPx(activity, spec.heightDp());
		params.topMargin = 0;
		params.rightMargin = ViewUtils.dpToPx(activity, spec.rightMarginDp());
		params.bottomMargin = ViewUtils.dpToPx(activity, spec.bottomMarginDp());
		params.topToTop = ConstraintLayout.LayoutParams.UNSET;
		params.startToStart = ConstraintLayout.LayoutParams.UNSET;
		params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
		params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
		params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		setLayoutParams(params);
		updateMiniPlayerControlSpacing(spec.widthDp());
		setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
	}

	private void applyStandardPlayerAnchors(@NonNull ConstraintLayout.LayoutParams params) {
		params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		params.topToBottom = ConstraintLayout.LayoutParams.UNSET;
		params.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
		params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
		params.leftToLeft = ConstraintLayout.LayoutParams.UNSET;
		params.leftToRight = ConstraintLayout.LayoutParams.UNSET;
		params.rightToLeft = ConstraintLayout.LayoutParams.UNSET;
		params.rightToRight = ConstraintLayout.LayoutParams.UNSET;
		params.startToEnd = ConstraintLayout.LayoutParams.UNSET;
		params.endToStart = ConstraintLayout.LayoutParams.UNSET;
	}

	private void updateMiniPlayerCornerClipping() {
		boolean shouldClipMiniPlayerCorners =
						inAppMiniPlayer && !activity.isInPictureInPictureMode();
		setClipToOutline(shouldClipMiniPlayerCorners);
		invalidateOutline();
	}

	private boolean exceedsTouchSlop(float deltaX, float deltaY) {
		int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		return Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop;
	}

	@Nullable
	private View getMiniPlayerTapTarget(@NonNull MotionEvent event) {
		for (int targetId : MINI_PLAYER_TAP_TARGET_IDS) {
			View target = findViewById(targetId);
			if (isPointInsideVisibleView(event, target)) {
				return target;
			}
		}
		return null;
	}

	private boolean isPointInsideVisibleView(@NonNull MotionEvent event,
	                                         @Nullable View view) {
		if (view == null || view.getVisibility() != View.VISIBLE) return false;
		final int[] viewLoc = new int[2];
		view.getLocationOnScreen(viewLoc);
		final float pointerRawX = event.getRawX();
		final float pointerRawY = event.getRawY();
		return pointerRawX >= viewLoc[0] && pointerRawX <= viewLoc[0] + view.getWidth() &&
						pointerRawY >= viewLoc[1] && pointerRawY <= viewLoc[1] + view.getHeight();
	}

	private void startMiniPlayerResize(@NonNull MotionEvent event) {
		miniPlayerPinchStartDistancePx = calculatePointerDistancePx(event);
		if (miniPlayerPinchStartDistancePx <= 0.0f) return;
		miniPlayerResizing = true;
		miniPlayerTouchCaptured = true;
		miniPlayerDragging = false;
		miniPlayerPinchStartWidthPx = getMiniPlayerWidthPx();
	}

	private void captureMiniPlayerTouchStart(@NonNull MotionEvent event) {
		animate().cancel();
		miniAnimating = false;
		miniPlayerTouchCaptured = true;
		miniPlayerDragging = false;
		miniPlayerResizing = false;
		miniPlayerTouchDownRawX = event.getRawX();
		miniPlayerTouchDownRawY = event.getRawY();
		miniPlayerStartTranslationX = getTranslationX();
		miniPlayerStartTranslationY = getTranslationY();
	}

	private void updateMiniPlayerSizeByPinch(@NonNull MotionEvent event) {
		if (!miniPlayerResizing || event.getPointerCount() < 2 || miniPlayerPinchStartDistancePx <= 0.0f) {
			return;
		}
		float distancePx = calculatePointerDistancePx(event);
		if (distancePx <= 0.0f) return;
		float scale = distancePx / miniPlayerPinchStartDistancePx;
		int targetWidthPx = Math.round(miniPlayerPinchStartWidthPx * scale);
		applyMiniPlayerSizeOverridePx(targetWidthPx);
	}

	private float calculatePointerDistancePx(@NonNull MotionEvent event) {
		if (event.getPointerCount() < 2) return 0.0f;
		float dx = event.getX(0) - event.getX(1);
		float dy = event.getY(0) - event.getY(1);
		return (float) Math.hypot(dx, dy);
	}

	private int getMiniPlayerWidthPx() {
		if (getWidth() > 0) return getWidth();
		ViewGroup.LayoutParams params = getLayoutParams();
		if (params != null && params.width > 0) return params.width;
		MiniPlayerLayout.Spec spec = MiniPlayerLayout.computeSpec(
						getScreenWidthDp(),
						getBottomInsetDp(),
						miniPlayerWidthOverrideDp);
		return ViewUtils.dpToPx(activity, spec.widthDp());
	}

	private void applyMiniPlayerSizeOverridePx(int widthPx) {
		if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
		int targetWidthDp = Math.max(1, pxToDp(widthPx));
		miniPlayerWidthOverrideDp = MiniPlayerLayout.clampWidthDp(getScreenWidthDp(), targetWidthDp);
		int nextWidthPx = ViewUtils.dpToPx(activity, miniPlayerWidthOverrideDp);
		int nextHeightPx = ViewUtils.dpToPx(activity, MiniPlayerLayout.computeHeightDp(miniPlayerWidthOverrideDp));
		updateMiniPlayerControlSpacing(miniPlayerWidthOverrideDp);
		if (params.width == nextWidthPx && params.height == nextHeightPx) return;
		params.width = nextWidthPx;
		params.height = nextHeightPx;
		setLayoutParams(params);
		restoreMini();
	}

	private void finishMiniResize() {
		resetMiniPlayerTouchTracking();
		snapMini();
	}

	private void updateMiniPlayerControlSpacing(int widthDp) {
		int minWidthDp = MiniPlayerLayout.minWidthDpForScreen(getScreenWidthDp());
		int startSpacingDp = MiniPlayerLayout.computeGapByCenterDistanceRatio(
						widthDp,
						minWidthDp,
						MINI_CONTROL_DEFAULT_SPACE_DP,
						MINI_SIDE_CONTROL_SIZE_DP,
						MINI_CENTER_CONTROL_SIZE_DP);
		int endSpacingDp = MiniPlayerLayout.computeGapByCenterDistanceRatio(
						widthDp,
						minWidthDp,
						MINI_CONTROL_DEFAULT_SPACE_DP,
						MINI_CENTER_CONTROL_SIZE_DP,
						MINI_SIDE_CONTROL_SIZE_DP);
		updateMiniControlSpaceWidth(R.id.mini_controls_space_start, ViewUtils.dpToPx(activity, startSpacingDp));
		updateMiniControlSpaceWidth(R.id.mini_controls_space_end, ViewUtils.dpToPx(activity, endSpacingDp));
	}

	private void updateMiniControlSpaceWidth(int spaceViewId, int widthPx) {
		View space = findViewById(spaceViewId);
		if (space == null) return;
		ViewGroup.LayoutParams params = space.getLayoutParams();
		if (params == null || params.width == widthPx) return;
		params.width = widthPx;
		space.setLayoutParams(params);
	}

	private boolean moveMini(float x, float y) {
		if (!(getParent() instanceof View parent)) return false;
		if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return false;
		int width = params.width > 0 ? params.width : getWidth();
		int height = params.height > 0 ? params.height : getHeight();
		if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0)
			return false;
		
		int topInsetPx = resolveTopInsetPx();
		
		int left = parent.getWidth() - params.rightMargin - width;
		int top = parent.getHeight() - params.bottomMargin - height;

		miniPlayerSavedTranslationX = MiniPlayerLayout.clampTranslation(x, left, width, parent.getWidth(), 0, parent.getWidth());
		miniPlayerSavedTranslationY = MiniPlayerLayout.clampTranslation(y, top, height, parent.getHeight(), topInsetPx, parent.getHeight() - bottomOffsetPx);
		setTranslationX(miniPlayerSavedTranslationX);
		setTranslationY(miniPlayerSavedTranslationY);
		return true;
	}

	private void snapMini() {
		if (!(getParent() instanceof View parent)) return;
		if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
		int width = params.width > 0 ? params.width : getWidth();
		int height = params.height > 0 ? params.height : getHeight();
		if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
		
		int topInsetPx = resolveTopInsetPx();
		
		int left = parent.getWidth() - params.rightMargin - width;
		int top = parent.getHeight() - params.bottomMargin - height;

		float x = MiniPlayerLayout.snapX(getTranslationX(), left, width, parent.getWidth(), 0, parent.getWidth());
		float y = MiniPlayerLayout.clampTranslation(getTranslationY(), top, height, parent.getHeight(), topInsetPx, parent.getHeight() - bottomOffsetPx);
		miniPlayerSavedTranslationX = x;
		miniPlayerSavedTranslationY = y;
		animate().cancel();
		setTranslationY(y);
		if (Math.abs(getTranslationX() - x) < 0.5f && Math.abs(getTranslationY() - y) < 0.5f) {
			setTranslationX(x);
			persistMiniPlayerLayoutState();
			return;
		}
		animate()
						.translationX(x)
						.setDuration(MINI_TRANSITION_MS)
						.setInterpolator(new OvershootInterpolator(0.7f))
						.withLayer()
						.withEndAction(this::persistMiniPlayerLayoutState)
						.start();
	}

	private void loadPersistedMiniPlayerLayoutState() {
		PlayerPreferences.MiniPlayerLayoutState state = prefs.getMiniPlayerLayoutState();
		int screenWidthDp = getScreenWidthDp();
		int defaultWidthDp = MiniPlayerLayout.minWidthDpForScreen(screenWidthDp);
		miniPlayerWidthOverrideDp = state.widthDp() > 0
						? MiniPlayerLayout.clampWidthDp(screenWidthDp, state.widthDp())
						: defaultWidthDp;
		miniPlayerSavedTranslationX = dpToPx(state.translationXDp());
		miniPlayerSavedTranslationY = dpToPx(state.translationYDp());
		miniPlayerTranslationStashedForFullscreen = false;
		clearViewTranslation();
	}

	private void persistMiniPlayerLayoutState() {
		if (!inAppMiniPlayer) return;
		prefs.persistMiniPlayerLayoutState(
						miniPlayerWidthOverrideDp,
						pxToDp(miniPlayerSavedTranslationX),
						pxToDp(miniPlayerSavedTranslationY));
	}

	private void resetMiniPlayerTouchTracking() {
		clearMiniPlayerPendingTapTarget();
		miniPlayerTouchCaptured = false;
		miniPlayerDragging = false;
		miniPlayerResizing = false;
		miniPlayerPinchStartDistancePx = 0.0f;
		miniPlayerPinchStartWidthPx = 0;
	}

	private void clearMiniPlayerPendingTapTarget() {
		miniPlayerPendingTapTarget = null;
	}

	private void resetMiniPlayerTranslation() {
		miniPlayerSavedTranslationX = 0.0f;
		miniPlayerSavedTranslationY = 0.0f;
		miniPlayerTranslationStashedForFullscreen = false;
		clearViewTranslation();
	}

	private void clearViewTranslation() {
		setTranslationX(0.0f);
		setTranslationY(0.0f);
	}

	private void saveCurrentMiniPlayerTranslation() {
		miniPlayerSavedTranslationX = getTranslationX();
		miniPlayerSavedTranslationY = getTranslationY();
	}

	private void restoreMini() {
		if (!inAppMiniPlayer || !isAttachedToWindow()) return;
		if (!(getParent() instanceof View parent)) {
			post(this::restoreMini);
			return;
		}
		if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
		int width = params.width > 0 ? params.width : getWidth();
		int height = params.height > 0 ? params.height : getHeight();
		if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
			post(this::restoreMini);
			return;
		}
		int left = parent.getWidth() - params.rightMargin - width;

		miniPlayerSavedTranslationX = MiniPlayerLayout.snapX(miniPlayerSavedTranslationX, left, width, parent.getWidth(), 0, parent.getWidth());
		if (!moveMini(miniPlayerSavedTranslationX, miniPlayerSavedTranslationY)) {
			post(this::restoreMini);
			return;
		}
		persistMiniPlayerLayoutState();
	}

	private int getScreenWidthDp() {
		int screenWidthDp = getResources().getConfiguration().screenWidthDp;
		if (screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
			return screenWidthDp;
		}
		return pxToDp(ViewUtils.getScreenWidth(activity));
	}

	private int getBottomInsetDp() {
		return pxToDp(resolveBottomInsetPx());
	}
	
	private int resolveBottomInsetPx() {
		WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
		int systemBottom = 0;
		if (insets != null) {
			Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
			systemBottom = systemInsets.bottom;
		}
		return Math.max(systemBottom, bottomOffsetPx);
	}

	private int resolveTopInsetPx() {
		WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
		if (insets != null) {
			Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
			return systemInsets.top;
		}
		return 0;
	}

	private int pxToDp(int px) {
		return Math.round(px / getResources().getDisplayMetrics().density);
	}

	private float pxToDp(float px) {
		return px / getResources().getDisplayMetrics().density;
	}

	private float dpToPx(float dp) {
		return dp * getResources().getDisplayMetrics().density;
	}

	private void applyNormalState(int defaultResizeMode) {
		isFs = false;
		activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		ViewUtils.setFullscreen(activity.getWindow().getDecorView(), false);
		updatePlayerLayout(false);
		setResizeMode(inAppMiniPlayer ? AspectRatioFrameLayout.RESIZE_MODE_FIT : defaultResizeMode);
		updateMiniPlayerCornerClipping();
		updateFullscreenButton(false);
		setUseController(true);
	}

	private void applyFullscreenState(@NonNull ControllerMachine.State previousState,
	                                  boolean isPortraitVideo,
	                                  int fsOrientation,
	                                  int defaultResizeMode) {
		isFs = true;
		if (previousState == ControllerMachine.State.NORMAL && !activity.isInPictureInPictureMode()) {
			updateNormalHeight();
		}
		activity.setRequestedOrientation(isPortraitVideo 
						? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT 
						: fsOrientation);
		ViewUtils.setFullscreen(activity.getWindow().getDecorView(), true);
		updatePlayerLayout(true);
		setResizeMode(defaultResizeMode);
		updateMiniPlayerCornerClipping();
		updateFullscreenButton(true);
		setUseController(true);
	}
	private void applyPictureInPictureState(@NonNull ControllerMachine.State previousState) {
		isFs = false;
		if (previousState == ControllerMachine.State.NORMAL) {
			updateNormalHeight();
		}
		updatePlayerLayout(true);
		setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
		updateMiniPlayerCornerClipping();
		setUseController(true);
	}

	public void onPiPModeChanged() {
		updateMiniPlayerCornerClipping();
	}

	private void updateFullscreenButton(boolean fullscreen) {
		ImageButton fullscreenButton = findViewById(R.id.btn_fullscreen);
		if (fullscreenButton != null) {
			fullscreenButton.setImageResource(
							fullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
		}
	}

	public void cueing(@NonNull CueGroup cueGroup) {
		SubtitleView sv = getCustomSubtitleView();
		if (sv == null) return;
		if (inAppMiniPlayer || activity.isInPictureInPictureMode()) {
			sv.setCues(Collections.emptyList());
			return;
		}
		List<Cue> cues = new ArrayList<>();
		for (Cue cue : cueGroup.cues) {
			cues.add(cue.buildUpon()
							.setLine(SUBTITLE_LINE_FRACTION, Cue.LINE_TYPE_FRACTION)
							.setLineAnchor(Cue.ANCHOR_TYPE_END)
							.setPosition(SUBTITLE_POSITION_FRACTION)
							.setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
							.build());
		}
		sv.setCues(cues);
	}

	@Override
	public void setResizeMode(int resizeMode) {
		super.setResizeMode(resizeMode);
		View videoSurfaceView = getVideoSurfaceView();
		if (videoSurfaceView instanceof AspectRatioFrameLayout frameLayout) {
			frameLayout.setResizeMode(resizeMode);
		}
	}

	public void show() {
		setVisibility(View.VISIBLE);
	}

	public void hide() {
		setVisibility(View.GONE);
	}

	public void setTitle(@Nullable String title) {
		TextView titleView = findViewById(R.id.tv_title);
		if (titleView != null) {
			titleView.setText(title);
			titleView.setSelected(true);
		}
	}

	public void updateSkipMarkers(long duration, TimeUnit unit) {
		List<long[]> segs = sponsor.getSegments();
		List<long[]> validSegs = new ArrayList<>();
		for (long[] seg : segs) if (seg != null && seg.length >= 2) validSegs.add(seg);

		SponsorOverlayView layer = findViewById(R.id.sponsor_overlay);
		if (layer != null) layer.setData(validSegs.isEmpty() ? null : validSegs, duration, unit);

		DefaultTimeBar bar = findViewById(R.id.exo_progress);
		if (bar != null) {
			if (validSegs.isEmpty()) {
				bar.setAdGroupTimesMs(null, null, 0);
			} else {
				long[] times = new long[validSegs.size() * 2];
				for (int i = 0; i < validSegs.size(); i++) {
					times[i * 2] = validSegs.get(i)[0];
					times[i * 2 + 1] = validSegs.get(i)[1];
				}
				bar.setAdGroupTimesMs(times, new boolean[times.length], times.length);
			}
		}
	}

	public void updateRemainingTime(long positionMs, long durationMs, float speed) {
		final TextView remainingView = findViewById(R.id.tv_remaining);
		if (remainingView == null || durationMs <= 0) return;

		long remainingMs = (long) ((durationMs - positionMs) / speed);
		remainingView.setText(String.format(Locale.getDefault(), "(-%s)", 
						formatElapsedTime(remainingMs / 1000)));
	}

	private String formatElapsedTime(long seconds) {
		return android.text.format.DateUtils.formatElapsedTime(seconds);
	}

	public void setHeight(int height) {
		if (activity.isInPictureInPictureMode() || isFs || inAppMiniPlayer) return;
		updateNormalHeight();
		ViewGroup.LayoutParams params = getLayoutParams();
		if (params != null && params.height != normalHeight) {
			params.height = normalHeight;
			requestLayout();
		}
	}

	public void setBottomOffset(int offset) {
		this.bottomOffsetPx = offset;
		if (inAppMiniPlayer) {
			updatePlayerLayout(false);
		}
	}

	@Nullable
	public SubtitleView getCustomSubtitleView() {
		if (subtitleView == null) {
			SubtitleView defaultSubtitleView = getSubtitleView();
			if (defaultSubtitleView != null) defaultSubtitleView.setVisibility(View.GONE);
			subtitleView = findViewById(R.id.custom_subtitle_view);
		}
		return subtitleView;
	}

	@NonNull
	private PictureInPictureParams buildPiPParams(boolean autoEnter) {
		PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
						.setAspectRatio(new Rational(16, 9));
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
			builder.setAutoEnterEnabled(autoEnter);
		}
		Rect sourceRectHint = new Rect();
		if (getGlobalVisibleRect(sourceRectHint)) {
			builder.setSourceRectHint(sourceRectHint);
		}
		return builder.build();
	}

	public void showController() {
		super.showController();
	}

}
