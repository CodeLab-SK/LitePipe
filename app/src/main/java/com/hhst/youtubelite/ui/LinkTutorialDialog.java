package com.hhst.youtubelite.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.hhst.youtubelite.R;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.List;

public class LinkTutorialDialog {

    private static final boolean FORCE_LEGACY_TEST = true;

    private final Context context;
    private AlertDialog dialog;

    public LinkTutorialDialog(Context context) {
        this.context = context;
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_link_tutorial, null);
        ViewPager2 viewPager = view.findViewById(R.id.tutorial_view_pager);
        TabLayout indicator = view.findViewById(R.id.tutorial_indicator);
        
        MaterialButton btnNext = view.findViewById(R.id.btn_next);
        MaterialButton btnPrev = view.findViewById(R.id.btn_prev);
        MaterialButton btnSkip = view.findViewById(R.id.btn_skip);
        MaterialButton btnSettings = view.findViewById(R.id.btn_settings);

        List<TutorialSlide> slides = new ArrayList<>();
        
        boolean isLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || FORCE_LEGACY_TEST;

        if (!isLegacy) {
            slides.add(new TutorialSlide(getDrawableId("tutorial_12_step2"), context.getString(R.string.tutorial_step_12_1)));
            slides.add(new TutorialSlide(getDrawableId("tutorial_12_step3"), context.getString(R.string.tutorial_step_12_2)));
        } else {
            slides.add(new TutorialSlide(getDrawableId("tutorial_12_step1"), context.getString(R.string.tutorial_step_legacy_1)));
            slides.add(new TutorialSlide(getDrawableId("tutorial_old_step2"), context.getString(R.string.tutorial_step_legacy_2)));
        }

        TutorialAdapter adapter = new TutorialAdapter(slides);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);
        
        viewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1.0f - absPos);
            float scale = 0.9f + (1.0f - absPos) * 0.1f;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });

        new TabLayoutMediator(indicator, viewPager, (tab, position) -> {}).attach();


        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateButtonStates(position, slides.size(), btnSkip, btnPrev, btnNext, btnSettings);
            }
        });


        updateButtonStates(0, slides.size(), btnSkip, btnPrev, btnNext, btnSettings);

        btnNext.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true));
        btnPrev.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true));
        
        btnSkip.setOnClickListener(v -> {
            MMKV.defaultMMKV().putBoolean("asked_open_by_default", true);
            dialog.dismiss();
        });

        btnSettings.setOnClickListener(v -> {
            openSettings();
            dialog.dismiss();
        });

        dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private void updateButtonStates(int position, int total, View skip, View prev, View next, View settings) {
        if (position == 0) {
            skip.setVisibility(View.VISIBLE);
            prev.setVisibility(View.GONE);
            next.setVisibility(View.VISIBLE);
            settings.setVisibility(View.GONE);
        } else if (position == total - 1) {
            skip.setVisibility(View.GONE);
            prev.setVisibility(View.VISIBLE);
            next.setVisibility(View.GONE);
            settings.setVisibility(View.VISIBLE);
        } else {
            skip.setVisibility(View.GONE);
            prev.setVisibility(View.VISIBLE);
            next.setVisibility(View.VISIBLE);
            settings.setVisibility(View.GONE);
        }
    }

    private int getDrawableId(String name) {
        int resId = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return resId != 0 ? resId : R.drawable.ic_broken_image;
    }

    private void openSettings() {
        MMKV.defaultMMKV().putBoolean("asked_open_by_default", true);
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intent = new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + context.getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())));
        }
    }

    private static class TutorialSlide {
        int imageRes;
        String text;
        TutorialSlide(int imageRes, String text) {
            this.imageRes = imageRes;
            this.text = text;
        }
    }

    private static class TutorialAdapter extends RecyclerView.Adapter<TutorialAdapter.ViewHolder> {
        private final List<TutorialSlide> slides;
        TutorialAdapter(List<TutorialSlide> slides) { this.slides = slides; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tutorial_slide, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TutorialSlide slide = slides.get(position);
            holder.image.setImageResource(slide.imageRes);
            holder.text.setText(slide.text);
        }

        @Override public int getItemCount() { return slides.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView text;
            ViewHolder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.tutorial_image);
                text = itemView.findViewById(R.id.tutorial_text);
            }
        }
    }
}
