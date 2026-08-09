package com.codelabsk.litepipe.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.UnstableApi;

import com.codelabsk.litepipe.R;

@SuppressLint("CustomSplashScreen")
@UnstableApi
public class SplashActivity extends AppCompatActivity {

    private static final long LOGO_DELAY        = 100L;
    private static final long APP_NAME_DELAY    = 250L;
    private static final long TAG_SIMPLE_DELAY  = 400L;
    private static final long TAG_FAST_DELAY    = 550L;
    private static final long TAG_MINIMAL_DELAY = 700L;
    private static final long BOTTOM_TAG_DELAY  = 800L;
    private static final long LAUNCH_DELAY      = 1300L;

    private static final long   WORD_DURATION   = 450L;
    private static final long   DOT_DELAY       = 100L;
    private static final float  TRANSLATE_Y_DP  = 40f;
    private static final float  LOGO_SCALE_FROM = 0.7f;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            final ObjectAnimator slideUp = ObjectAnimator.ofFloat(
                    splashScreenView.getView(),
                    View.TRANSLATION_Y,
                    0f,
                    -splashScreenView.getView().getHeight()
            );
            slideUp.setInterpolator(new AnticipateInterpolator());
            slideUp.setDuration(400L);
            slideUp.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });
            slideUp.start();
        });

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        prepareViews();

        handler.postDelayed(this::animateLogo, LOGO_DELAY);
        handler.postDelayed(() -> animateFadeUp(findViewById(R.id.app_name), WORD_DURATION), APP_NAME_DELAY);
        handler.postDelayed(() -> animateWordWithDot(findViewById(R.id.tagline_simple), findViewById(R.id.tagline_dot1)), TAG_SIMPLE_DELAY);
        handler.postDelayed(() -> animateWordWithDot(findViewById(R.id.tagline_fast), findViewById(R.id.tagline_dot2)), TAG_FAST_DELAY);
        handler.postDelayed(() -> animateFadeUp(findViewById(R.id.tagline_modern), WORD_DURATION), TAG_MINIMAL_DELAY);
        handler.postDelayed(() -> animateFadeUp(findViewById(R.id.bottom_tagline), WORD_DURATION + 100L), BOTTOM_TAG_DELAY);
        handler.postDelayed(this::launchMain, LAUNCH_DELAY);
    }

    private void prepareViews() {
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setAlpha(0f);
            logo.setScaleX(LOGO_SCALE_FROM);
            logo.setScaleY(LOGO_SCALE_FROM);
        }

        View glow = findViewById(R.id.logo_glow);
        if (glow != null) {
            glow.setAlpha(0f);
            glow.setScaleX(0.5f);
            glow.setScaleY(0.5f);
        }

        int[] textIds = {
                R.id.app_name,
                R.id.tagline_simple, R.id.tagline_dot1,
                R.id.tagline_fast,   R.id.tagline_dot2,
                R.id.tagline_modern,
                R.id.bottom_tagline
        };
        for (int id : textIds) {
            View v = findViewById(id);
            if (v != null) {
                v.setAlpha(0f);
                v.setTranslationY(TRANSLATE_Y_DP);
            }
        }
    }

    private void animateLogo() {
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500L)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }

        View glow = findViewById(R.id.logo_glow);
        if (glow != null) {
            glow.animate()
                    .alpha(0.6f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(800L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void animateFadeUp(View v, long duration) {
        if (v == null) return;
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    private void animateWordWithDot(View word, View dot) {
        animateFadeUp(word, WORD_DURATION);
        handler.postDelayed(() -> animateFadeUp(dot, WORD_DURATION - 50L), DOT_DELAY);
    }

    private void launchMain() {
        if (isDestroyed() || isFinishing()) return;
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}