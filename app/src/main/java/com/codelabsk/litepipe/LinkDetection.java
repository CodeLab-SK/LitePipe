package com.codelabsk.litepipe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.codelabsk.litepipe.downloader.core.DownloadPrefs;
import com.codelabsk.litepipe.extractor.VideoDetails;
import com.codelabsk.litepipe.extractor.YoutubeExtractor;
import com.codelabsk.litepipe.gallery.GalleryActivity;
import com.codelabsk.litepipe.util.ViewUtils;
import com.tencent.mmkv.MMKV;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class LinkDetection {
	private final Context context;
	private final YoutubeExtractor youtubeExtractor;
	private final DownloadPrefs downloadPrefs;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private AlertDialog activeDialog;
	private String currentUrl;
	private boolean isAppVisible = false;
	@Nullable private Listener currentListener;

	private final ClipboardManager.OnPrimaryClipChangedListener primaryClipListener = this::checkClipboard;

	@Inject
	public LinkDetection(@ApplicationContext Context context, YoutubeExtractor youtubeExtractor) {
		this.context = context;
		this.youtubeExtractor = youtubeExtractor;
		this.downloadPrefs = new DownloadPrefs(MMKV.defaultMMKV());
	}

	public void setAppVisible(boolean visible, @Nullable Listener listener) {
		this.isAppVisible = visible;
		this.currentListener = listener;
		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		if (clipboard != null) {
			if (visible) {
				clipboard.addPrimaryClipChangedListener(primaryClipListener);
				mainHandler.postDelayed(this::checkClipboard, 500);
			} else {
				clipboard.removePrimaryClipChangedListener(primaryClipListener);
			}
		}
	}

	public void checkClipboard() {
		if (!downloadPrefs.isClipboardDetectionEnabled()) return;

		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		if (clipboard == null || !clipboard.hasPrimaryClip()) return;

		ClipDescription description = clipboard.getPrimaryClipDescription();
		MMKV kv = MMKV.defaultMMKV();

		boolean isNewCopy = false;
		if (description != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			long timestamp = description.getTimestamp();
			long lastTimestamp = kv.decodeLong("last_clipboard_timestamp", 0);
			if (timestamp > 0 && timestamp <= lastTimestamp) return;
			isNewCopy = true;
		}

		ClipData data = clipboard.getPrimaryClip();
		if (data == null || data.getItemCount() <= 0) return;

		CharSequence text = data.getItemAt(0).getText();
		if (text == null) return;

		String url = extractUrl(text.toString());
		if (url == null) return;

		boolean isVideo = YoutubeExtractor.getVideoId(url) != null;
		boolean isPlaylist = url.contains("list=") && !url.contains("list=RD");

		if (isVideo || isPlaylist) {
			String lastUrl = kv.decodeString("last_clipboard_url", "");
			if (!url.equals(lastUrl) || isNewCopy) {
				kv.encode("last_clipboard_url", url);
				if (description != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					kv.encode("last_clipboard_timestamp", description.getTimestamp());
				}

				if (isAppVisible && currentListener != null) {
					mainHandler.post(() -> showLoadingDialogAndFetch(url, currentListener));
				}
			}
		}
	}

	private String extractUrl(String text) {
		Matcher m = Pattern.compile("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)/(?:watch\\?v=|v/|embed/|shorts/|playlist\\?list=)?[a-zA-Z0-9_-]+(?:[?&]\\S*)?", Pattern.CASE_INSENSITIVE).matcher(text);
		if (m.find()) return m.group();
		return null;
	}

	public void showLoadingDialogAndFetch(String url, Listener listener) {
		if (url.equals(currentUrl) && activeDialog != null && activeDialog.isShowing()) return;

		Activity activity = listener.getActivity();
		if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

		if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
		currentUrl = url;

		View view = LayoutInflater.from(activity).inflate(R.layout.dialog_clipboard_detected, null);
		activeDialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_YoutubeLite_Dialog_Transparent)
				.setView(view)
				.setCancelable(true)
				.create();

		TextView msg = view.findViewById(R.id.dialog_message);
		if (msg != null) msg.setText(url);

		view.findViewById(R.id.btn_close).setOnClickListener(v -> activeDialog.dismiss());
		
		Window w = activeDialog.getWindow();
		if (w != null) {
			w.setBackgroundDrawableResource(android.R.color.transparent);
			WindowManager.LayoutParams lp = w.getAttributes();
			lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			lp.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.95);
			lp.y = ViewUtils.dpToPx(activity, 64);
			w.setAttributes(lp);
		}
		activeDialog.show();

		executor.execute(() -> {
			try {
				if (url.contains("list=") && !url.contains("list=RD")) {
					PlaylistExtractor ex = NewPipe.getService(0).getPlaylistExtractor(url);
					ex.fetchPage();
					String title = ex.getName();
					String thumbnail = null;
					if (!ex.getInitialPage().getItems().isEmpty()) {
						thumbnail = ex.getInitialPage().getItems().get(0).getThumbnails().get(0).getUrl();
					}
					final String finalThumb = thumbnail;
					mainHandler.post(() -> updateDialogContent(url, title, finalThumb, listener));
				} else {
					VideoDetails details = youtubeExtractor.getVideoInfo(url);
					mainHandler.post(() -> updateDialogContent(url, details.getTitle(), details.getThumbnail(), listener));
				}
			} catch (Exception e) {
				mainHandler.post(() -> updateDialogContent(url, null, null, listener));
			}
		});
	}

    @OptIn(markerClass = UnstableApi.class)
    private void updateDialogContent(String url, @Nullable String title, @Nullable String thumbnailUrl, Listener listener) {
		if (activeDialog == null || !activeDialog.isShowing() || !url.equals(currentUrl)) return;

		View view = activeDialog.findViewById(R.id.content_layout);
		ProgressBar progress = activeDialog.findViewById(R.id.loading_progress);
		TextView msg = activeDialog.findViewById(R.id.dialog_message);
		ImageView img = activeDialog.findViewById(R.id.dialog_image);
		View thumbContainer = activeDialog.findViewById(R.id.thumbnail_container);
		MaterialButton btnPlay = activeDialog.findViewById(R.id.btn_play);
		MaterialButton btnDownload = activeDialog.findViewById(R.id.btn_download);

		if (progress != null) progress.setVisibility(View.GONE);
		if (view != null) view.setVisibility(View.VISIBLE);
		if (msg != null) msg.setText(title != null ? title : url);
		if (btnPlay != null) {
			btnPlay.setEnabled(true);
			btnPlay.setOnClickListener(v -> {
				listener.onPlay(url);
				activeDialog.dismiss();
			});
		}
		if (btnDownload != null) {
			btnDownload.setEnabled(true);
			btnDownload.setOnClickListener(v -> {
				listener.onDownload(url);
				activeDialog.dismiss();
			});
		}

		Activity activity = listener.getActivity();
		if (activity != null && img != null && thumbnailUrl != null) {
			Glide.with(activity).load(thumbnailUrl).diskCacheStrategy(DiskCacheStrategy.ALL).into(img);
			if (thumbContainer != null) {
				thumbContainer.setOnClickListener(v -> {
					Intent intent = new Intent(activity, GalleryActivity.class);
					ArrayList<String> urls = new ArrayList<>();
					urls.add(thumbnailUrl);
					intent.putStringArrayListExtra("thumbnails", urls);
					intent.putExtra("filename", title != null ? title : "thumbnail");
					activity.startActivity(intent);
				});
			}
		}
	}

	public interface Listener {
		Activity getActivity();
		void onPlay(String url);
		void onDownload(String url);
	}
}
