package com.hhst.youtubelite.util;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;

public final class ViewUtils {

	private static final float ALPHA_VISIBLE = 1.0f;
	private static final float ALPHA_INVISIBLE = 0.0f;
	private static final int ANIMATION_DURATION_MS = 100;
	public static int dpToPx(@NonNull final Context context, final float dp) {
		return (int) (dp * context.getResources().getDisplayMetrics().density);
	}

	public static int getScreenWidth(@NonNull final Context context) {
		return context.getResources().getDisplayMetrics().widthPixels;
	}
	public static void animateViewAlpha(@NonNull final View v, final float alpha, final int visibilityIfGone) {
		if (Float.compare(alpha, ALPHA_VISIBLE) == 0) {
			v.animate().cancel();
			v.setAlpha(ALPHA_VISIBLE);
			v.setVisibility(View.VISIBLE);
		} else if (v.getVisibility() != View.VISIBLE) {
			v.setAlpha(ALPHA_INVISIBLE);
			v.setVisibility(visibilityIfGone);
		} else {
			v.setVisibility(View.VISIBLE);
			v.animate().alpha(alpha).setDuration(ANIMATION_DURATION_MS).withEndAction(() -> {
				if (Float.compare(alpha, ALPHA_INVISIBLE) == 0) {
					v.setVisibility(visibilityIfGone);
				}
			}).start();
		}
	}

	public static void setFullscreen(@NonNull final View view, final boolean fullscreen) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			WindowInsetsController controller = view.getWindowInsetsController();
			if (controller != null) {
				if (fullscreen) {
					controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
					controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
				} else {
					controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
				}
			}
		}
		
		if (fullscreen) {
			view.setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LOW_PROFILE
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			);
		} else {
			view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		}
	}
	public static void setFullscreen(@NonNull final Window window, final boolean fullscreen) {
		if (fullscreen) {
			window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		setFullscreen(window.getDecorView(), fullscreen);
	}
}
