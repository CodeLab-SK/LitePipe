package com.codelabsk.litepipe.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.player.model.RecommendationVideo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BottomRecommendationsSheet extends FrameLayout {

    private RecyclerView recyclerView;
    private View overlay;
    private View sheetCard;
    private View dragHandle;
    private RecommendationsAdapter adapter;

    private float sheetHeight = 0f;
    private float dragStartY = 0f;
    private boolean isDraggingHandle = false;

    private float playerDragStartY = 0f;
    private boolean isDraggingFromPlayer = false;
    private boolean isFullscreen = false;
    private int touchSlop;

    private static final long ANIM_DURATION = 280L;
    private static final float DISMISS_THRESHOLD = 0.25f;
    private final AccelerateDecelerateInterpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();

    public interface OnVideoClickListener {
        void onVideoClick(RecommendationVideo video);
    }

    public interface OnShowListener {
        void onShow();
    }

    private OnVideoClickListener onVideoClickListener;
    private OnShowListener onShowListener;

    public BottomRecommendationsSheet(@NonNull Context context) {
        this(context, null);
    }

    public BottomRecommendationsSheet(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomRecommendationsSheet(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_bottom_recommendations, this, true);
        recyclerView = findViewById(R.id.recycler_recommendations);
        overlay = findViewById(R.id.overlay);
        sheetCard = findViewById(R.id.sheet_card);
        dragHandle = findViewById(R.id.drag_handle);

        if (overlay != null) overlay.setOnClickListener(v -> hide());

        setupRecyclerView();
        if (dragHandle != null) setupDragHandle();

        setVisibility(GONE);
        if (sheetCard != null) {
            sheetCard.post(() -> {
                sheetHeight = sheetCard.getHeight();
                sheetCard.setTranslationY(sheetHeight);
            });
        }
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private float resolveSheetHeight() {
        if (sheetHeight > 0f) return sheetHeight;
        if (sheetCard != null) {
            sheetHeight = sheetCard.getHeight();
        }
        return sheetHeight;
    }

    private void setupRecyclerView() {
        adapter = new RecommendationsAdapter(video -> {
            if (onVideoClickListener != null) onVideoClickListener.onVideoClick(video);
        });
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDragHandle() {
        if (dragHandle == null) return;
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartY = event.getRawY();
                    isDraggingHandle = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isDraggingHandle) {
                        float delta = event.getRawY() - dragStartY;
                        if (delta > 0) {
                            float progress = 1f - Math.min(1f, delta / resolveSheetHeight());
                            applySlideProgress(progress);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDraggingHandle) {
                        float delta = event.getRawY() - dragStartY;
                        if (delta > resolveSheetHeight() * DISMISS_THRESHOLD) {
                            hide();
                        } else {
                            animateTo(0f, 1f);
                        }
                        isDraggingHandle = false;
                    }
                    return true;
            }
            return false;
        });
    }

    public void setOnVideoClickListener(OnVideoClickListener listener) {
        this.onVideoClickListener = listener;
    }

    public void setOnShowListener(OnShowListener listener) {
        this.onShowListener = listener;
    }

    public void loadRecommendations(List<RecommendationVideo> videos) {
        if (videos == null || videos.isEmpty()) {
            if (adapter != null) adapter.setVideos(new ArrayList<>());
            return;
        }
        List<RecommendationVideo> limited = videos.size() > 10 ? videos.subList(0, 10) : videos;
        if (adapter != null) {
            adapter.setVideos(limited);
        }
    }

    public void show() {
        if (onShowListener != null) onShowListener.onShow();
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            if (isFullscreen && overlay != null) {
                overlay.setVisibility(VISIBLE);
                overlay.setAlpha(0f);
            } else if (overlay != null) {
                overlay.setVisibility(GONE);
            }
        }
        
        if (sheetCard == null) return;

        if (sheetHeight == 0f) {
            sheetCard.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    sheetCard.getViewTreeObserver().removeOnPreDrawListener(this);
                    sheetHeight = sheetCard.getHeight();
                    sheetCard.setTranslationY(sheetHeight);
                    animateTo(0f, 1f);
                    return true;
                }
            });
        } else {
            animateTo(0f, 1f);
        }
    }

    public void hide() {
        if (sheetCard == null) {
            setVisibility(GONE);
            return;
        }
        
        float h = resolveSheetHeight();
        sheetCard.animate().cancel();
        sheetCard.animate()
                .translationY(h)
                .setDuration(ANIM_DURATION)
                .setInterpolator(INTERPOLATOR)
                .withEndAction(() -> {
                    setVisibility(GONE);
                    if (overlay != null) overlay.setVisibility(GONE);
                })
                .start();
        
        if (overlay != null) {
            overlay.animate().cancel();
            overlay.animate().alpha(0f).setDuration(ANIM_DURATION).start();
        }
    }

    public void setFullscreen(boolean fullscreen) {
        this.isFullscreen = fullscreen;
        if (getVisibility() == View.VISIBLE && overlay != null) {
            if (fullscreen) {
                overlay.setVisibility(VISIBLE);
                overlay.setAlpha(1f);
            } else {
                overlay.setVisibility(GONE);
            }
        }
    }
    
    private void animateTo(float translationY, float overlayAlpha) {
        if (sheetCard != null) {
            sheetCard.animate().cancel();
            sheetCard.animate()
                    .translationY(translationY)
                    .setDuration(ANIM_DURATION)
                    .setInterpolator(INTERPOLATOR)
                    .start();
        }
        
        if (isFullscreen && overlay != null) {
            overlay.animate().cancel();
            overlay.animate().alpha(overlayAlpha).setDuration(ANIM_DURATION).start();
        }
    }

    private void applySlideProgress(float progress) {
        float h = resolveSheetHeight();
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (sheetCard != null) {
            sheetCard.setTranslationY(h * (1f - clamped));
        }
        if (isFullscreen && overlay != null) {
            overlay.setAlpha(clamped);
        }
    }

    public boolean handlePlayerTouchEvent(View playerView, MotionEvent event) {
        int width = playerView.getWidth();
        int height = playerView.getHeight();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getY() > height * 0.65f
                        && event.getX() > width * 0.25f
                        && event.getX() < width * 0.75f) {
                    playerDragStartY = event.getRawY();
                    isDraggingFromPlayer = true;
                    // Don't return true here so buttons can still receive the touch
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDraggingFromPlayer) {
                    float delta = playerDragStartY - event.getRawY();
                    if (delta > touchSlop) {
                        if (getVisibility() != VISIBLE) {
                            setVisibility(VISIBLE);
                            if (isFullscreen && overlay != null) {
                                overlay.setVisibility(VISIBLE);
                                overlay.setAlpha(0f);
                            }
                            if (sheetCard != null) {
                                sheetCard.setTranslationY(resolveSheetHeight());
                            }
                        }
                        float progress = Math.min(1f, delta / (height * 0.3f));
                        applySlideProgress(progress);
                        return true; // Intercept once we've moved past touch slop
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingFromPlayer) {
                    float delta = playerDragStartY - event.getRawY();
                    isDraggingFromPlayer = false;
                    if (delta > touchSlop) {
                        if (delta > height * 0.04f) {
                            show();
                        } else {
                            hide();
                        }
                        return true;
                    }
                }
                break;
        }
        return false;
    }
    
    private static class RecommendationsAdapter extends RecyclerView.Adapter<RecommendationsAdapter.ViewHolder> {
        private List<RecommendationVideo> videos = new ArrayList<>();
        private final OnVideoClickListener listener;

        RecommendationsAdapter(OnVideoClickListener listener) {
            this.listener = listener;
        }

        void setVideos(List<RecommendationVideo> newVideos) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return videos.size(); }
                @Override public int getNewListSize() { return newVideos.size(); }
                @Override
                public boolean areItemsTheSame(int o, int n) {
                    return Objects.equals(videos.get(o).getVideoId(), newVideos.get(n).getVideoId());
                }
                @Override
                public boolean areContentsTheSame(int o, int n) {
                    return Objects.equals(videos.get(o).getTitle(), newVideos.get(n).getTitle()) &&
                            Objects.equals(videos.get(o).getChannelName(), newVideos.get(n).getChannelName());
                }
            });
            this.videos = new ArrayList<>(newVideos);
            result.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recommendation_video, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            RecommendationVideo v = videos.get(position);
            if (h.title != null) h.title.setText(v.getTitle());
            if (h.channel != null) h.channel.setText(v.getChannelName());
            if (h.thumbnail != null) {
                Glide.with(h.thumbnail.getContext())
                        .load(v.getThumbnailUrl())
                        .placeholder(R.drawable.ic_thumbnail_placeholder)
                        .error(R.drawable.ic_broken_image)
                        .centerCrop()
                        .into(h.thumbnail);
            }
            h.itemView.setOnClickListener(__ -> listener.onVideoClick(v));
        }

        @Override
        public int getItemCount() { return videos.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumbnail;
            final TextView title, channel;
            ViewHolder(View v) {
                super(v);
                thumbnail = v.findViewById(R.id.iv_thumbnail);
                title = v.findViewById(R.id.tv_title);
                channel = v.findViewById(R.id.tv_channel);
            }
        }
    }
}
