package com.codelabsk.litepipe.browser;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.codelabsk.litepipe.Constants;
import com.codelabsk.litepipe.IncognitoManager;
import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.downloader.ui.DownloadActivity;
import com.codelabsk.litepipe.downloader.ui.DownloadDialog;
import com.codelabsk.litepipe.extension.Constant;
import com.codelabsk.litepipe.extension.ExtensionManager;
import com.codelabsk.litepipe.extractor.YoutubeExtractor;
import com.codelabsk.litepipe.gallery.GalleryActivity;
import com.codelabsk.litepipe.player.LitePlayer;
import com.codelabsk.litepipe.player.model.RecommendationVideo;
import com.codelabsk.litepipe.player.queue.QueueItem;
import com.codelabsk.litepipe.player.queue.QueueRepository;
import com.codelabsk.litepipe.player.queue.QueueWarmer;
import com.codelabsk.litepipe.ui.AboutActivity;
import com.codelabsk.litepipe.ui.MainActivity;
import com.codelabsk.litepipe.ui.SettingsActivity;
import com.codelabsk.litepipe.util.ToastUtils;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public final class JavascriptInterface {
    @NonNull private final Context context;
    @NonNull private final YoutubeWebview webview;
    @NonNull private final YoutubeExtractor youtubeExtractor;
    @NonNull private final LitePlayer player;
    @NonNull private final ExtensionManager extensionManager;
    @NonNull private final TabManager tabManager;
    @NonNull private final PoTokenProviderImpl poTokenProvider;
    @NonNull private final QueueRepository queueRepository;
    @NonNull private final QueueWarmer queueWarmer;
    @NonNull private final Gson gson = new Gson();
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());

    public JavascriptInterface(@NonNull final YoutubeWebview webview,
                               @NonNull final YoutubeExtractor youtubeExtractor,
                               @NonNull final LitePlayer player,
                               @NonNull final ExtensionManager extensionManager,
                               @NonNull final TabManager tabManager,
                               @NonNull final PoTokenProviderImpl poTokenProvider,
                               @NonNull final QueueRepository queueRepository,
                               @NonNull final QueueWarmer queueWarmer) {
        this.context = webview.getContext();
        this.webview = webview;
        this.youtubeExtractor = youtubeExtractor;
        this.player = player;
        this.extensionManager = extensionManager;
        this.tabManager = tabManager;
        this.poTokenProvider = poTokenProvider;
        this.queueRepository = queueRepository;
        this.queueWarmer = queueWarmer;
    }

    @android.webkit.JavascriptInterface
    public void showToast(String txt) {
        handler.post(() -> Toast.makeText(context, txt, Toast.LENGTH_SHORT).show());
    }

    @android.webkit.JavascriptInterface
    public void setBgPlay(boolean bgplay) {
        extensionManager.setEnabled(Constant.ENABLE_BACKGROUND_PLAY, bgplay);
    }

    @android.webkit.JavascriptInterface
    public float getVolume() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return (float) cur / max;
    }

    @android.webkit.JavascriptInterface
    public void setVolume(float volume) {
        handler.post(() -> {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (max * volume), 0);
        });
    }

    @android.webkit.JavascriptInterface
    public float getBrightness() {
        MainActivity activity = findMainActivity(context);
        if (activity != null) {
            float brightness = activity.getWindow().getAttributes().screenBrightness;
            if (brightness < 0) {
                try {
                    return (Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f) * 100f;
                } catch (Settings.SettingNotFoundException e) {
                    return 50f;
                }
            }
            return brightness * 100f;
        }
        return 50f;
    }

    @android.webkit.JavascriptInterface
    public void setBrightness(float brightness) {
        handler.post(() -> {
            MainActivity activity = findMainActivity(context);
            if (activity != null) {
                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                lp.screenBrightness = Math.max(0.01f, Math.min(brightness, 1.0f));
                activity.getWindow().setAttributes(lp);
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void notifyNetworkRestored() {
        handler.post(() -> {
            final String currentUrl = webview.getUrl();
            if (currentUrl != null && (currentUrl.contains("/watch") || currentUrl.contains("music.youtube.com"))) {
                ToastUtils.show(context, "Network restored. Resuming...");
                tabManager.playInWatch(currentUrl);
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void finishRefresh() {
        handler.post(() -> {
            if (webview.getParent() instanceof SwipeRefreshLayout)
                ((SwipeRefreshLayout) webview.getParent()).setRefreshing(false);
        });
    }

    @android.webkit.JavascriptInterface
    public void setRefreshLayoutEnabled(final boolean enabled) {
        handler.post(() -> {
            if (webview.getParent() instanceof SwipeRefreshLayout)
                ((SwipeRefreshLayout) webview.getParent()).setEnabled(enabled);
        });
    }

    @android.webkit.JavascriptInterface
    public void download(@Nullable final String url) {
        if (url != null) handler.post(() -> new DownloadDialog(url, context, youtubeExtractor).show());
    }

    @android.webkit.JavascriptInterface
    public void download() {
        handler.post(() -> {
            Intent intent = new Intent(context, DownloadActivity.class);
            context.startActivity(intent);
        });
    }

    @android.webkit.JavascriptInterface
    public void pip() {
        handler.post(player::enterPictureInPicture);
    }

    @android.webkit.JavascriptInterface
    public void showVideoOptions(@Nullable final String url) {
        if (url != null) {
            handler.post(() -> {
                MainActivity mainActivity = findMainActivity(context);
                if (mainActivity != null) {
                    mainActivity.showVideoOptionsDialog(url);
                }
            });
        }
    }

    @android.webkit.JavascriptInterface
    public void showVideoOptions(@Nullable final String url, @Nullable final String title) {
        showVideoOptions(url);
    }

    @android.webkit.JavascriptInterface
    public void showMediaItemMenu(@Nullable final String payloadJson) {
        if (payloadJson != null) {
            handler.post(() -> {
                MainActivity mainActivity = findMainActivity(context);
                if (mainActivity != null) {
                    mainActivity.showMediaItemMenuDialog(payloadJson);
                }
            });
        }
    }

    private MainActivity findMainActivity(Context context) {
        if (context instanceof MainActivity) return (MainActivity) context;
        if (context instanceof ContextWrapper) {
            return findMainActivity(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    @android.webkit.JavascriptInterface
    public void extension() {
        litepipeSettings();
    }

    @android.webkit.JavascriptInterface
    public void litepipeSettings() {
        handler.post(() -> {
            Intent intent = new Intent(context, SettingsActivity.class);
            context.startActivity(intent);
        });
    }

    @android.webkit.JavascriptInterface
    public void addToQueue(@Nullable final String itemJson) {
        if (itemJson == null) return;
        handler.post(() -> {
            try {
                final QueueItem item = gson.fromJson(itemJson, QueueItem.class);
                if (item == null || item.getUrl() == null) return;
                final String vid = item.getVideoId();
                if (vid == null || vid.isBlank()
                        || item.getTitle() == null || item.getTitle().isBlank()
                        || item.getAuthor() == null || item.getAuthor().isBlank()) {
                    MainActivity mainActivity = findMainActivity(context);
                    if (mainActivity != null) {
                        mainActivity.toggleQueue(item.getUrl());
                    } else {
                        ToastUtils.show(context, R.string.queue_item_unavailable);
                    }
                    return;
                }
                item.setVideoId(vid);
                queueRepository.add(item);
                queueWarmer.warmItem(item);
                player.refreshQueueNavigationAvailability();
                ToastUtils.show(context, R.string.queue_item_added);
            } catch (final Exception ignored) {
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void toggleQueue(@Nullable final String url) {
        if (url == null) return;
        handler.post(() -> {
            MainActivity mainActivity = findMainActivity(context);
            if (mainActivity != null) {
                mainActivity.toggleQueue(url);
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void showQueueItemUnavailable() {
        ToastUtils.show(context, R.string.queue_item_unavailable);
    }

    @android.webkit.JavascriptInterface
    public boolean isQueueEnabled() {
        return queueRepository.isEnabled();
    }

    @android.webkit.JavascriptInterface
    public void hidePlayer() {
        handler.post(() -> {
            if (extensionManager.isEnabled(Constant.ENABLE_IN_APP_MINI_PLAYER) && player.getLoadedVideoId() != null) {
                player.enterInAppMiniPlayer();
            } else {
                player.hide();
                tabManager.hidePlayer();
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void about() {
        handler.post(() -> {
            Intent intent = new Intent(context, AboutActivity.class);
            context.startActivity(intent);
        });
    }

    @android.webkit.JavascriptInterface
    public void play(@Nullable final String url) {
        if (url != null) handler.post(() -> tabManager.playInWatch(url));
    }

    @android.webkit.JavascriptInterface
    public void musicPlay() {
        if (webview.isMusicBackgroundActive()) return;
        handler.post(player::play);
    }

    @android.webkit.JavascriptInterface
    public void musicPause() {
        if (webview.isMusicBackgroundActive()) return;
        handler.post(player::pause);
    }

    @android.webkit.JavascriptInterface
    public void musicSeek(final long positionMs) {
        if (webview.isMusicBackgroundActive()) return;
        handler.post(() -> player.seekToIfLoaded(positionMs));
    }
    @android.webkit.JavascriptInterface
    public boolean seekLoadedVideo(@Nullable final String url, final long positionMs) {
        return player.seekLoadedVideo(url, positionMs);
    }

    @android.webkit.JavascriptInterface
    public void enqueue(@Nullable final String url) {
        enqueue(url, null);
    }

    @android.webkit.JavascriptInterface
    public void enqueue(@Nullable final String url, @Nullable final String title) {
        if (url != null) handler.post(() -> player.addToQueue(url, title));
    }

    @android.webkit.JavascriptInterface
    public void setPlayerHeight(final int height) {
        handler.post(() -> player.setHeight(height));
    }

    @android.webkit.JavascriptInterface
    public void setPoToken(@Nullable final String poToken, @Nullable final String visitorData) {
        if (poToken != null && visitorData != null) {
            poTokenProvider.setPoToken(new PoTokenResult(visitorData, poToken, poToken));
        }
    }

    @android.webkit.JavascriptInterface
    public void onPosterLongPress(@Nullable final String urlsJson) {
        if (urlsJson != null) {
            handler.post(() -> {
                final List<String> urls = gson.fromJson(urlsJson, new TypeToken<List<String>>() {}.getType());
                final Intent intent = new Intent(context, GalleryActivity.class);
                intent.putStringArrayListExtra("thumbnails", new ArrayList<>(urls));
                intent.putExtra("filename", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
                context.startActivity(intent);
            });
        }
    }

    @android.webkit.JavascriptInterface
    public String getPreferences() {
        return gson.toJson(extensionManager.getAllPreferences());
    }

    @android.webkit.JavascriptInterface
    public void onSkipByOffset(final int offset) {
        handler.post(() -> {
            if (offset > 0) player.skipToNext();
            else if (offset < 0) player.skipToPrevious();
        });
    }

    @android.webkit.JavascriptInterface
    public void openTab(@Nullable final String url, @Nullable final String tag) {
        if (url != null && tag != null) {
            handler.post(() -> tabManager.openTab(url, tag));
        }
    }

    @android.webkit.JavascriptInterface
    public void goBack() {
        handler.post(() -> {
            MainActivity activity = findMainActivity(context);
            if (activity != null) {
                activity.handleAppBack();
                return;
            }
            tabManager.evaluateJavascript("window.dispatchEvent(new Event('onGoBack'));", null);
            tabManager.goBack();
        });
    }

    @android.webkit.JavascriptInterface
    public long getResumePosition(@Nullable String vid) {
        return player.getResumePosition(vid);
    }

    @android.webkit.JavascriptInterface
    public void toggleIncognito() {
        handler.post(() -> IncognitoManager.getInstance().toggle(() -> handler.post(() -> {
            webview.clearCache(true);
            webview.clearHistory();
            webview.clearFormData();
            tabManager.openTab(Constants.HOME_URL, Constants.PAGE_HOME);
            YoutubeWebview web = tabManager.getWebview();
            if (web != null) {
                web.reload();
            }
        })));
    }

    @android.webkit.JavascriptInterface
    public boolean isIncognito() {
        return IncognitoManager.getInstance().isIncognito();
    }

    @android.webkit.JavascriptInterface
    public void onRecommendationsExtracted(String json) {
        handler.post(() -> {
            try {
                List<RecommendationVideo> videos = gson.fromJson(json, new TypeToken<List<RecommendationVideo>>(){}.getType());
                MainActivity activity = findMainActivity(context);
                if (activity != null && videos != null) {
                    activity.setRecommendations(videos);
                }
            } catch (Exception ignored) {}
        });
    }
}