package com.hhst.youtubelite.player;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
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
import com.hhst.youtubelite.player.common.PlayerPreferences;
import com.hhst.youtubelite.player.controller.ControllerMachine;
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager;
import com.hhst.youtubelite.player.sponsor.SponsorOverlayView;
import com.hhst.youtubelite.util.ViewUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final int MINI_PLAYER_CORNER_RADIUS_DP = 16;
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
    private int topOffset = 0;
    private int bottomOffset = 0;
    @Getter
    private boolean inAppMiniPlayer = false;
    @Nullable
    private Runnable onMiniPlayerRestore;
    @Nullable
    private Runnable onMiniPlayerClose;
    @Nullable
    private Runnable onMiniPlayerBackgroundTap;
    private int miniPlayerRestoreResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private boolean miniPlayerRestoreFullscreen;
    @NonNull
    private final int[] miniPlayerLocationOnScreen = new int[2];
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
    private boolean miniPlayerResizeFromLeft = true;
    @Nullable
    private View miniPlayerPendingTapTarget;
    private int miniPlayerPinchStartWidthPx;
    private int miniPlayerWidthOverrideDp = MiniPlayerLayout.NO_WIDTH_OVER_DP;
    private boolean miniAnimating;
    private int miniAnimToken;
    private boolean wasInPiP;

    private View resizeGripLeft;
    private View resizeGripRight;

    @NonNull
    private final ViewOutlineProvider miniPlayerOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(final View view, final Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), ViewUtils.dpToPx(activity, MINI_PLAYER_CORNER_RADIUS_DP));
        }
    };
    public LitePlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public LitePlayerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    public LitePlayerView(Context context) {
        super(context);
    }
    public void setup() {
        setControllerAnimationEnabled(false);
        setControllerHideOnTouch(false);
        setControllerAutoShow(false);
        setControllerShowTimeoutMs(0);
        setOutlineProvider(miniPlayerOutlineProvider);
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

        setupResizeGrips();
    }

    private void updateNormalHeight() {
        final int screenWidth = ViewUtils.getScreenWidth(activity);
        normalHeight = (int) (screenWidth * 9 / 16.0);
    }

    private void setupResizeGrips() {
        int gripSize = ViewUtils.dpToPx(getContext(), 32);

        resizeGripLeft = createResizeGrip(true);
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(gripSize, gripSize);
        leftParams.gravity = Gravity.TOP | Gravity.START;
        addView(resizeGripLeft, leftParams);

        resizeGripRight = createResizeGrip(false);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(gripSize, gripSize);
        rightParams.gravity = Gravity.TOP | Gravity.END;
        addView(resizeGripRight, rightParams);
    }

    private View createResizeGrip(boolean left) {
        FrameLayout container = new FrameLayout(getContext());
        container.setVisibility(View.GONE);
        View cornerLine = new View(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(0xCCFFFFFF);
        shape.setCornerRadius(ViewUtils.dpToPx(getContext(), 2));
        cornerLine.setBackground(shape);

        int thickness = ViewUtils.dpToPx(getContext(), 4);
        int length = ViewUtils.dpToPx(getContext(), 16);


        FrameLayout.LayoutParams hParams = new FrameLayout.LayoutParams(length, thickness);
        hParams.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        container.addView(cornerLine, hParams);

        View verticalLine = new View(getContext());
        verticalLine.setBackground(shape);
        FrameLayout.LayoutParams vParams = new FrameLayout.LayoutParams(thickness, length);
        vParams.gravity = Gravity.TOP | (left ? Gravity.START : Gravity.END);
        container.addView(verticalLine, vParams);

        return container;
    }
    public void setTopOffset(int offset) {
        this.topOffset = offset;
        if (!isFs && !inAppMiniPlayer) {
            updatePlayerLayout(false);
        }
    }
    public void setBottomOffset(int offset) {
        this.bottomOffset = offset;
        if (inAppMiniPlayer) {
            updatePlayerLayout(false);
        }
    }
    public void applyControllerState(@NonNull final ControllerMachine.State previousState,
                                     @NonNull final ControllerMachine.State newState,
                                     final boolean isPortraitVideo,
                                     final int fsOrientation,
                                     final int defaultResizeMode) {
        post(() -> {
            switch (newState) {
                case NORMAL, MINI_PLAYER -> applyNormalState(defaultResizeMode);
                case FULLSCREEN_UNLOCKED, FULLSCREEN_LOCKED ->
                        applyFullscreenState(previousState, isPortraitVideo, fsOrientation, defaultResizeMode);
                case PIP -> applyPictureInPictureState(previousState);
            }
        });
    }
    public void updatePlayerLayout(final boolean fullscreen) {
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof ConstraintLayout.LayoutParams params) {
            if (inAppMiniPlayer && !fullscreen) {
                applyMiniPlayerLayout(params);
                restoreMini();
                miniPlayerTranslationStashedForFullscreen = false;
                updateMiniPlayerCornerClipping();
                return;
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
                params.width = 0;
                params.height = 0;
                params.topMargin = 0;
                params.rightMargin = 0;
                params.bottomMargin = 0;
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            } else {
                params.width = 0;
                updateNormalHeight();
                params.height = normalHeight;
                params.topMargin = topOffset + ViewUtils.dpToPx(activity, com.hhst.youtubelite.player.common.Constant.TOP_MARGIN_DP);
                params.rightMargin = 0;
                params.bottomMargin = 0;
                params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            }
            setLayoutParams(params);
            updateMiniPlayerCornerClipping();
        }
    }
    private void updateMiniPlayerCornerClipping() {
        final boolean shouldClipMiniPlayerCorners =
                inAppMiniPlayer && !activity.isInPictureInPictureMode();
        setClipToOutline(shouldClipMiniPlayerCorners);
        invalidateOutline();

        if (resizeGripLeft != null) {
            resizeGripLeft.setVisibility(shouldClipMiniPlayerCorners ? View.VISIBLE : View.GONE);
        }
        if (resizeGripRight != null) {
            resizeGripRight.setVisibility(shouldClipMiniPlayerCorners ? View.VISIBLE : View.GONE);
        }
    }
    public void enterPiP() {
        if (activity.isInPictureInPictureMode()) return;
        final int screenWidth = ViewUtils.getScreenWidth(activity);
        final int baseHeight = (int) (screenWidth * 9 / 16.0);
        if (!isFs && !inAppMiniPlayer) {
            normalHeight = playerHeight > 0 ? playerHeight : baseHeight;
        }
        updatePlayerLayout(true);
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        final Rect rect = new Rect();
        getGlobalVisibleRect(rect);
        int width = rect.width();
        int height = rect.height();
        if (width <= 0) width = getWidth();
        if (height <= 0) height = getHeight();
        if (width <= 0) width = screenWidth;
        if (height <= 0) height = baseHeight;
        float ratio = (float) width / height;
        if (ratio < 0.418410f) {
            width = (int) (height * 0.418410f);
        } else if (ratio > 2.390000f) {
            width = (int) (height * 2.390000f);
        }
        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(width, height));
        if (rect.width() > 0 && rect.height() > 0) {
            builder.setSourceRectHint(rect);
        }
        final SubtitleView sv = getCustomSubtitleView();
        if (sv != null) sv.setCues(Collections.emptyList());
        try {
            activity.enterPictureInPictureMode(builder.build());
            wasInPiP = true;
        } catch (Exception e) {
            activity.enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build());
            wasInPiP = true;
        }
    }
    public void onPiPModeChanged(boolean isInPiP) {
        if (wasInPiP && !isInPiP) {
            wasInPiP = false;
            post(() -> {
                updateNormalHeight();
                updatePlayerLayout(false);
                requestLayout();
            });
        }
        if (isInPiP) {
            final SubtitleView sv = getCustomSubtitleView();
            if (sv != null) sv.setCues(Collections.emptyList());
        }
    }
    public void enterInAppMiniPlayer() {
        if (inAppMiniPlayer) return;
        show();
        final float startX = getX();
        final float startY = getY();
        final int startWidth = getWidth();
        final int startHeight = getHeight();
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
        final float startX = getX();
        final float startY = getY();
        final int startWidth = getWidth();
        final int startHeight = getHeight();
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
    public void setMiniPlayerCallbacks(@Nullable final Runnable onRestore, @Nullable final Runnable onClose) {
        onMiniPlayerRestore = onRestore;
        onMiniPlayerClose = onClose;
        updateMiniPlayerInteractionHandlers();
    }
    public void setOnMiniPlayerBackgroundTap(@Nullable final Runnable onBackgroundTap) {
        onMiniPlayerBackgroundTap = onBackgroundTap;
    }
    private void updateMiniPlayerInteractionHandlers() {
        final ImageButton closeButton = findViewById(R.id.btn_mini_close);
        final ImageButton restoreButton = findViewById(R.id.btn_mini_restore);
        setMiniPlayerButtonAction(closeButton, inAppMiniPlayer ? onMiniPlayerClose : null);
        setMiniPlayerButtonAction(restoreButton, inAppMiniPlayer ? onMiniPlayerRestore : null);
    }
    private void setMiniPlayerButtonAction(@Nullable final ImageButton button, @Nullable final Runnable action) {
        if (button == null) return;
        button.setOnClickListener(action == null ? null : v -> action.run());
    }
    public boolean handleMiniPlayerTouch(@NonNull final MotionEvent event) {
        if (!inAppMiniPlayer) return false;
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                final View tapTarget = resolveMiniPlayerTapTarget(event);
                captureMiniPlayerTouchStart(event);
                miniPlayerPendingTapTarget = tapTarget;
                float localX = event.getX();
                float localY = event.getY();
                final int resizeArea = ViewUtils.dpToPx(getContext(), 48);
                if ((localX < resizeArea || localX > getWidth() - resizeArea) && localY < resizeArea) {
                    boolean fromLeft = localX < resizeArea;
                    startMiniPlayerSingleFingerResize(event, fromLeft);
                    clearMiniPlayerPendingTapTarget();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (miniPlayerResizing) {
                    updateMiniPlayerSizeBySingleFinger(event);
                    return true;
                }
                if (!miniPlayerTouchCaptured) return false;
                final float deltaX = event.getRawX() - miniPlayerTouchDownRawX;
                final float deltaY = event.getRawY() - miniPlayerTouchDownRawY;
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
                final View tap = miniPlayerPendingTapTarget;
                final boolean wasDragging = miniPlayerDragging;
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
                final boolean wasDragging = miniPlayerDragging;
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
    public boolean dispatchTouchEvent(@NonNull final MotionEvent event) {
        if (miniAnimating) {
            return true;
        }
        if (inAppMiniPlayer && handleMiniPlayerTouch(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    private void animateMiniTransition(final float startX,
                                       final float startY,
                                       final int startWidth,
                                       final int startHeight) {
        if (startWidth <= 0 || startHeight <= 0 || !isAttachedToWindow()) return;
        final int token = ++miniAnimToken;
        miniAnimating = true;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                final ViewTreeObserver observer = getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                if (token != miniAnimToken || !isAttachedToWindow() || getWidth() <= 0 || getHeight() <= 0) {
                    finishMiniTransition(token);
                    return true;
                }
                final float endX = getX();
                final float endY = getY();
                final int endWidth = getWidth();
                final int endHeight = getHeight();
                final float endTranslationX = getTranslationX();
                final float endTranslationY = getTranslationY();
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
    private void finishMiniTransition(final int token) {
        if (token != miniAnimToken) return;
        miniAnimating = false;
        setScaleX(1.0f);
        setScaleY(1.0f);
    }
    private void applyMiniPlayerLayout(@NonNull final ConstraintLayout.LayoutParams params) {
        final MiniPlayerLayout.Spec spec = MiniPlayerLayout.computeSpec(
                resolveScreenWidthDp(),
                resolveBottomInsetDp(),
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
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }
    private void applyStandardPlayerAnchors(@NonNull final ConstraintLayout.LayoutParams params) {
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
    private boolean exceedsTouchSlop(final float deltaX, final float deltaY) {
        final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        return Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop;
    }
    @Nullable
    private View resolveMiniPlayerTapTarget(@NonNull final MotionEvent event) {
        return resolveMiniPlayerTapTarget(event, 0);
    }
    @Nullable
    private View resolveMiniPlayerTapTarget(@NonNull final MotionEvent event, final int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return null;
        for (int targetId : MINI_PLAYER_TAP_TARGET_IDS) {
            final View target = findViewById(targetId);
            if (isPointInsideVisibleView(event, pointerIndex, target)) {
                return target;
            }
        }
        return null;
    }
    private boolean isPointInsideVisibleView(@NonNull final MotionEvent event,
                                             final int pointerIndex,
                                             @Nullable final View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) return false;
        final int[] viewLoc = new int[2];
        view.getLocationOnScreen(viewLoc);
        final float pointerRawX = event.getRawX();
        final float pointerRawY = event.getRawY();
        return pointerRawX >= viewLoc[0] && pointerRawX <= viewLoc[0] + view.getWidth() &&
                pointerRawY >= viewLoc[1] && pointerRawY <= viewLoc[1] + view.getHeight();
    }
    private void startMiniPlayerSingleFingerResize(@NonNull final MotionEvent event, boolean fromLeft) {
        miniPlayerResizing = true;
        miniPlayerTouchCaptured = true;
        miniPlayerDragging = false;
        miniPlayerPinchStartWidthPx = resolveCurrentMiniPlayerWidthPx();
        miniPlayerTouchDownRawX = event.getRawX();
        miniPlayerTouchDownRawY = event.getRawY();
        miniPlayerResizeFromLeft = fromLeft;
    }
    private void captureMiniPlayerTouchStart(@NonNull final MotionEvent event) {
        animate().cancel();
        miniPlayerTouchCaptured = true;
        miniPlayerDragging = false;
        miniPlayerResizing = false;
        miniPlayerTouchDownRawX = event.getRawX();
        miniPlayerTouchDownRawY = event.getRawY();
        miniPlayerStartTranslationX = getTranslationX();
        miniPlayerStartTranslationY = getTranslationY();
    }
    private void updateMiniPlayerSizeBySingleFinger(@NonNull final MotionEvent event) {
        if (!miniPlayerResizing) return;
        final float deltaX = event.getRawX() - miniPlayerTouchDownRawX;
        final int targetWidthPx;
        if (miniPlayerResizeFromLeft) {
            targetWidthPx = Math.round(miniPlayerPinchStartWidthPx - deltaX);
        } else {
            targetWidthPx = Math.round(miniPlayerPinchStartWidthPx + deltaX);
        }
        applyMiniPlayerSizeOverridePx(targetWidthPx);
    }
    private int resolveCurrentMiniPlayerWidthPx() {
        if (getWidth() > 0) return getWidth();
        final ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && params.width > 0) return params.width;
        final MiniPlayerLayout.Spec spec = MiniPlayerLayout.computeSpec(
                resolveScreenWidthDp(),
                resolveBottomInsetDp(),
                miniPlayerWidthOverrideDp);
        return ViewUtils.dpToPx(activity, spec.widthDp());
    }
    private void applyMiniPlayerSizeOverridePx(final int widthPx) {
        if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
        final int targetWidthDp = Math.max(1, pxToDp(widthPx));
        miniPlayerWidthOverrideDp = MiniPlayerLayout.clampWidthDp(resolveScreenWidthDp(), targetWidthDp);
        final int nextWidthPx = ViewUtils.dpToPx(activity, miniPlayerWidthOverrideDp);
        final int nextHeightPx = ViewUtils.dpToPx(activity, MiniPlayerLayout.computeHeightDp(miniPlayerWidthOverrideDp));
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
    private boolean moveMini(final float x, final float y) {
        if (!(getParent() instanceof View parent)) return false;
        if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return false;
        final int width = params.width > 0 ? params.width : getWidth();
        final int height = params.height > 0 ? params.height : getHeight();
        if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0) return false;
        
        final int minX = 0;
        final int maxX = parent.getWidth();
        final int minY = resolveTopInsetPx();
        final int maxY = parent.getHeight() - bottomOffset;

        final int left = parent.getWidth() - params.rightMargin - width;
        final int top = parent.getHeight() - params.bottomMargin - height;
        
        miniPlayerSavedTranslationX = MiniPlayerLayout.clampTranslation(x, left, width, parent.getWidth(), minX, maxX);
        miniPlayerSavedTranslationY = MiniPlayerLayout.clampTranslation(y, top, height, parent.getHeight(), minY, maxY);
        setTranslationX(miniPlayerSavedTranslationX);
        setTranslationY(miniPlayerSavedTranslationY);
        return true;
    }
    private void snapMini() {
        if (!(getParent() instanceof View parent)) return;
        if (!(getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) return;
        final int width = params.width > 0 ? params.width : getWidth();
        final int height = params.height > 0 ? params.height : getHeight();
        if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
        
        final int minX = 0;
        final int maxX = parent.getWidth();
        final int minY = resolveTopInsetPx();
        final int maxY = parent.getHeight() - bottomOffset;

        final int left = parent.getWidth() - params.rightMargin - width;
        final int top = parent.getHeight() - params.bottomMargin - height;
        
        final float x = MiniPlayerLayout.snapX(getTranslationX(), left, width, parent.getWidth(), minX, maxX);
        final float y = MiniPlayerLayout.clampTranslation(getTranslationY(), top, height, parent.getHeight(), minY, maxY);
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
        final PlayerPreferences.MiniPlayerLayoutState state = prefs.getMiniPlayerLayoutState();
        final int screenWidthDp = resolveScreenWidthDp();
        final int defaultWidthDp = MiniPlayerLayout.minWidthDpForScreen(screenWidthDp);
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
        float miniPlayerPinchStartDistancePx = 0.0f;
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
        final int width = params.width > 0 ? params.width : getWidth();
        final int height = params.height > 0 ? params.height : getHeight();
        if (width <= 0 || height <= 0 || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            post(this::restoreMini);
            return;
        }
        
        final int minX = 0;
        final int maxX = parent.getWidth();
        
        final int left = parent.getWidth() - params.rightMargin - width;
        miniPlayerSavedTranslationX = MiniPlayerLayout.snapX(miniPlayerSavedTranslationX, left, width, parent.getWidth(), minX, maxX);
        if (!moveMini(miniPlayerSavedTranslationX, miniPlayerSavedTranslationY)) {
            post(this::restoreMini);
            return;
        }
        persistMiniPlayerLayoutState();
    }
    private int resolveScreenWidthDp() {
        final int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        if (screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
            return screenWidthDp;
        }
        return pxToDp(ViewUtils.getScreenWidth(activity));
    }
    private int resolveBottomInsetDp() {
        return pxToDp(resolveBottomInsetPx());
    }
    private int resolveBottomInsetPx() {
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        int systemBottom = 0;
        if (insets != null) {
            final Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
            systemBottom = systemInsets.bottom;
        }
        return Math.max(systemBottom, bottomOffset);
    }
    private int resolveTopInsetPx() {
        final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) {
            final Insets systemInsets = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars());
            return systemInsets.top;
        }
        return 0;
    }
    private int pxToDp(final int px) {
        return Math.round(px / getResources().getDisplayMetrics().density);
    }
    private float pxToDp(final float px) {
        return px / getResources().getDisplayMetrics().density;
    }
    private float dpToPx(final float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
    private void applyNormalState(final int defaultResizeMode) {
        isFs = false;
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        ViewUtils.setFullscreen(activity.getWindow().getDecorView(), false);
        updatePlayerLayout(false);
        setResizeMode(inAppMiniPlayer ? AspectRatioFrameLayout.RESIZE_MODE_FIT : defaultResizeMode);
        updateFullscreenButton(false);
        setUseController(true);
    }
    private void applyFullscreenState(@NonNull final ControllerMachine.State previousState,
                                      final boolean isPortraitVideo,
                                      final int fsOrientation,
                                      final int defaultResizeMode) {
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
        updateFullscreenButton(true);
        setUseController(true);
    }
    private void applyPictureInPictureState(@NonNull final ControllerMachine.State previousState) {
        isFs = false;
        if (previousState == ControllerMachine.State.NORMAL) {
            updateNormalHeight();
        }
        updatePlayerLayout(true);
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        setUseController(true);
    }
    private void updateFullscreenButton(final boolean fullscreen) {
        final ImageButton fullscreenButton = findViewById(R.id.btn_fullscreen);
        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(
                    fullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        }
    }
    public void cueing(@NonNull final CueGroup cueGroup) {
        final SubtitleView sv = getCustomSubtitleView();
        if (sv == null) return;
        if (inAppMiniPlayer || activity.isInPictureInPictureMode()) {
            sv.setCues(Collections.emptyList());
            return;
        }
        final List<Cue> cues = new ArrayList<>();
        for (final Cue cue : cueGroup.cues)
            cues.add(cue.buildUpon()
                    .setLine(SUBTITLE_LINE_FRACTION, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_END)
                    .setPosition(SUBTITLE_POSITION_FRACTION)
                    .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .build());
        sv.setCues(cues);
    }
    @Nullable
    public SubtitleView getCustomSubtitleView() {
        if (subtitleView == null) {
            final SubtitleView defaultSubtitleView = getSubtitleView();
            if (defaultSubtitleView != null) defaultSubtitleView.setVisibility(View.GONE);
            subtitleView = findViewById(R.id.custom_subtitle_view);
        }
        return subtitleView;
    }
    @Override
    public void setResizeMode(int resizeMode) {
        super.setResizeMode(resizeMode);
        final View videoSurfaceView = getVideoSurfaceView();
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
        final TextView titleView = findViewById(R.id.tv_title);
        if (titleView != null) {
            titleView.setText(title);
            titleView.setSelected(true);
        }
    }
    public void updateSkipMarkers(long duration, TimeUnit unit) {
        final List<long[]> segs = sponsor.getSegments();
        final List<long[]> validSegs = new ArrayList<>();
        for (final long[] seg : segs) if (seg != null && seg.length >= 2) validSegs.add(seg);
        final SponsorOverlayView layer = findViewById(R.id.sponsor_overlay);
        if (layer != null) {
            layer.setData(validSegs.isEmpty() ? null : validSegs, duration, unit);
        }
        final DefaultTimeBar bar = findViewById(R.id.exo_progress);
        if (bar != null) {
            if (validSegs.isEmpty()) {
                bar.setAdGroupTimesMs(null, null, 0);
            } else {
                final long[] times = new long[validSegs.size() * 2];
                for (int i = 0; i < validSegs.size(); i++) {
                    times[i * 2] = validSegs.get(i)[0];
                    times[i * 2 + 1] = validSegs.get(i)[1];
                }
                bar.setAdGroupTimesMs(times, new boolean[times.length], times.length);
            }
        }
    }
    public void setHeight(int height) {
        if (activity.isInPictureInPictureMode() || isFs || height <= 0) return;
        final int deviceHeight = ViewUtils.dpToPx(activity, height);
        if (getLayoutParams().height == deviceHeight) return;
        getLayoutParams().height = deviceHeight;
        if (!isFs) normalHeight = deviceHeight;
        requestLayout();
    }
    public void showController() {
        super.showController();
    }
}
