package com.hhst.youtubelite.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hhst.youtubelite.Constant;
import com.hhst.youtubelite.IncognitoManager;
import com.hhst.youtubelite.LinkDetection;
import com.hhst.youtubelite.PlaybackService;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.browser.TabManager;
import com.hhst.youtubelite.browser.YoutubeWebview;
import com.hhst.youtubelite.downloader.core.history.DownloadHistoryRepository;
import com.hhst.youtubelite.downloader.service.DownloadService;
import com.hhst.youtubelite.downloader.ui.DownloadActivity;
import com.hhst.youtubelite.downloader.ui.DownloadDialog;
import com.hhst.youtubelite.downloader.ui.PlaylistDownloadDialog;
import com.hhst.youtubelite.downloader.ui.PlaylistDownloadItem;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.extractor.VideoDetails;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.player.BottomRecommendationsSheet;
import com.hhst.youtubelite.player.LitePlayer;
import com.hhst.youtubelite.player.common.PlayerLoopMode;
import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.player.model.RecommendationVideo;
import com.hhst.youtubelite.player.queue.QueueItem;
import com.hhst.youtubelite.player.queue.QueueNav;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.player.queue.QueueWarmer;
import com.hhst.youtubelite.ui.queue.QueueAdapter;
import com.hhst.youtubelite.ui.queue.QueueTouch;
import com.hhst.youtubelite.util.DeviceUtils;
import com.hhst.youtubelite.util.UrlUtils;
import com.hhst.youtubelite.util.ViewUtils;
import com.tencent.mmkv.MMKV;

import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@UnstableApi
public final class MainActivity extends AppCompatActivity implements LinkDetection.Listener {
	private static final String YOUTUBE_WWW_HOST = "www.youtube.com";
	private static final int REQUEST_NOTIFICATION_CODE = 100;
	private static final int DOUBLE_TAP_EXIT_INTERVAL_MS = 2_000;

	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@Inject ExtensionManager extensionManager;
	@Inject TabManager tabManager;
	@Inject LitePlayer player;
	@Inject Engine engine;
	@Inject YoutubeExtractor youtubeExtractor;
	@Inject QueueRepository queueRepository;
	@Inject QueueWarmer queueWarmer;
	@Inject DownloadHistoryRepository historyRepository;
	@Inject LinkDetection linkDetection;

	@Nullable private PlaybackService playbackService;
	@Nullable private OnBackPressedCallback appBackCallback;

	private View queueContainer;
	private View expandedQueueContainer;
	private QueueAdapter queueAdapter;
	private NavigationBar navBar;
	private View navBarDivider;
	private View watchLoadingOverlay;
	private int navigationBarHeight = 0;
	private long lastBackTime = 0;
	private boolean suppressNextUserLeaveHintPictureInPicture;

	private AlertDialog activeVideoOptionsDialog;

	private BottomRecommendationsSheet recommendationsSheet;

	private final IncognitoManager.Listener incognitoListener = (isIncognito) -> applyIncognitoUi(isIncognito, true);

	private final ServiceConnection playbackConnection = new ServiceConnection() {
		@Override public void onServiceConnected(ComponentName n, IBinder s) {
			playbackService = ((PlaybackService.PlaybackBinder) s).getService();
			if (player != null) player.attachPlaybackService(playbackService);
		}
		@Override public void onServiceDisconnected(ComponentName n) {
			playbackService = null;
		}
	};

	private final ServiceConnection downloadConnection = new ServiceConnection() {
		@Override public void onServiceConnected(ComponentName n, IBinder s) {
		}
		@Override public void onServiceDisconnected(ComponentName n) {
		}
	};

	@Override
	protected void onCreate(@Nullable final Bundle savedInstanceState) {
		EdgeToEdge.enable(this);
		setContentView(R.layout.activity_main);
		super.onCreate(savedInstanceState);
		UrlUtils.setYoutubePreferences(this);

		final View mainView = findViewById(R.id.main);
		final View bottomNavContainer = findViewById(R.id.bottom_navigation_container);
		ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
			final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			final Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
			navigationBarHeight = navInsets.bottom;
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
			if (bottomNavContainer != null) {
				bottomNavContainer.setPadding(0, 0, 0, navigationBarHeight);
			}
			updateQueueBarPosition();
			return insets;
		});

		navBar = findViewById(R.id.custom_nav_bar);
		navBar.setup(extensionManager, tabManager);
		navBarDivider = findViewById(R.id.nav_bar_divider);
		watchLoadingOverlay = findViewById(R.id.watch_loading_overlay);

		navBar.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
			if (navBar.getVisibility() == View.VISIBLE) {
				int height = navBar.getHeight();
				if (player != null) player.setBottomOffset(height);
			} else {
				if (player != null) player.setBottomOffset(0);
			}
		});

		setupQueueUI();
		setupRecommendationsSheet();
		requestPermissions();

		bindService(new Intent(this, PlaybackService.class), playbackConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, DownloadService.class), downloadConnection, BIND_AUTO_CREATE);

		setupBackNavigation();
		queueWarmer.warmItems(queueRepository.getItems());

		checkOpenByDefault();
		mainView.post(() -> handleIntent(getIntent()));

		IncognitoManager.getInstance().addListener(incognitoListener);
		IncognitoManager.getInstance().resetOnStart(() -> {
			applyIncognitoUi(false, false);
			runOnUiThread(() -> {
				YoutubeWebview web = getWebview();
				if (web != null) web.reload();
			});
		});
	}

	public void showWatchLoadingOverlay() {
		if (watchLoadingOverlay != null) {
			watchLoadingOverlay.animate().cancel();
			if (watchLoadingOverlay.getVisibility() == View.VISIBLE) return;
			
			watchLoadingOverlay.setScaleX(0f);
			watchLoadingOverlay.setScaleY(0f);
			watchLoadingOverlay.setAlpha(0f);
			watchLoadingOverlay.setVisibility(View.VISIBLE);
			
			watchLoadingOverlay.animate()
					.scaleX(1.0f)
					.scaleY(1.0f)
					.alpha(1.0f)
					.setDuration(450)
					.setInterpolator(new OvershootInterpolator(1.0f))
					.setListener(null)
					.start();
		}
	}

	public void hideWatchLoadingOverlay() {
		if (watchLoadingOverlay != null && watchLoadingOverlay.getVisibility() == View.VISIBLE) {
			mainHandler.postDelayed(() -> {
				if (watchLoadingOverlay == null) return;
				watchLoadingOverlay.animate().cancel();
				watchLoadingOverlay.animate()
						.alpha(0f)
						.scaleX(1.1f)
						.scaleY(1.1f)
						.setDuration(450)
						.setInterpolator(new AccelerateDecelerateInterpolator())
						.setListener(new AnimatorListenerAdapter() {
							@Override
							public void onAnimationEnd(Animator animation) {
								if (watchLoadingOverlay != null) {
									watchLoadingOverlay.setVisibility(View.GONE);
								}
							}
						})
						.start();
			}, 1200);
		}
	}

	private void setupRecommendationsSheet() {
		recommendationsSheet = findViewById(R.id.recommendations_sheet);
		
		recommendationsSheet.setOnVideoClickListener(video -> {
			recommendationsSheet.hide();
			tabManager.playInWatch("https://www.youtube.com/watch?v=" + video.getVideoId());
		});

		recommendationsSheet.setOnShowListener(this::triggerRecommendationsExtraction);

		player.getController().setOnRecommendationsRequestedListener(() -> {
			triggerRecommendationsExtraction();
			recommendationsSheet.show();
		});

		player.setOnFullscreenChangeListener(isFullscreen -> recommendationsSheet.setFullscreen(isFullscreen));

		tabManager.setOnPageFinishedListener(url -> {
			if (!url.contains("/watch")) {
				recommendationsSheet.hide();
			}
		});
	}

	private void triggerRecommendationsExtraction() {
		tabManager.evaluateJavascript("if(window.extractRecommendations) window.extractRecommendations();", null);
	}

	public void setRecommendations(List<RecommendationVideo> videos) {
		if (recommendationsSheet != null) {
			recommendationsSheet.loadRecommendations(videos);
		}
	}

	private void applyIncognitoUi(boolean isIncognito, boolean showToast) {
		runOnUiThread(() -> {
			View banner = findViewById(R.id.incognito_banner);
			if (isIncognito) {
				if (banner != null) banner.setVisibility(View.VISIBLE);
				if (showToast) Toast.makeText(this, R.string.incognito_on, Toast.LENGTH_SHORT).show();
			} else {
				if (banner != null) banner.setVisibility(View.GONE);
				if (showToast) Toast.makeText(this, R.string.incognito_off, Toast.LENGTH_SHORT).show();
			}
			if (navBar != null) navBar.update();
		});
	}

	@Override
	protected void onNewIntent(@NonNull Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	protected void onUserLeaveHint() {
		super.onUserLeaveHint();
		final boolean suppressAutoEnterPictureInPicture = suppressNextUserLeaveHintPictureInPicture;
		suppressNextUserLeaveHintPictureInPicture = false;
		if (shouldEnterPictureInPictureOnUserLeaveHint(
				player,
				extensionManager,
				DeviceUtils.isInPictureInPictureMode(this),
				suppressAutoEnterPictureInPicture)) {
			player.enterPictureInPicture();
		}
	}

	@Override
	public void onPictureInPictureModeChanged(final boolean isInPictureInPictureMode, @NonNull final Configuration newConfig) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
		dispatchPictureInPictureModeChanged(player, isInPictureInPictureMode);
		syncQueueUiVisibility(isInPictureInPictureMode);

		final View fragmentContainer = findViewById(R.id.fragment_container);
		if (isInPictureInPictureMode) {
			if (fragmentContainer != null) fragmentContainer.setVisibility(View.GONE);
			if (navBar != null) navBar.setVisibility(View.GONE);
			if (navBarDivider != null) navBarDivider.setVisibility(View.GONE);
			updatePictureInPictureActions();
		} else {
			if (fragmentContainer != null) fragmentContainer.setVisibility(View.VISIBLE);
			updateNavBarVisibility();
		}
	}

	private void updatePictureInPictureActions() {
		final List<RemoteAction> actions = new ArrayList<>();
		final QueueNav nav = engine.getQueueNavigationAvailability();

		final PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
		final Icon prevIcon = Icon.createWithResource(this, R.drawable.ic_previous);
		final RemoteAction prevAction = new RemoteAction(prevIcon, getString(R.string.action_previous), getString(R.string.action_previous), prevIntent);
		prevAction.setEnabled(nav.isPreviousActionEnabled());
		actions.add(prevAction);

		final PendingIntent ppIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE);
		final boolean isPlaying = engine.isPlaying();
		final Icon ppIcon = Icon.createWithResource(this, isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
		final RemoteAction ppAction = new RemoteAction(ppIcon, isPlaying ? getString(R.string.action_pause) : getString(R.string.action_play), isPlaying ? getString(R.string.action_pause) : getString(R.string.action_play), ppIntent);
		actions.add(ppAction);

		final PendingIntent nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
		final Icon nextIcon = Icon.createWithResource(this, R.drawable.ic_next);
		final RemoteAction nextAction = new RemoteAction(nextIcon, getString(R.string.action_next), getString(R.string.action_next), nextIntent);
		nextAction.setEnabled(nav.isNextActionEnabled());
		actions.add(nextAction);

		setPictureInPictureParams(new PictureInPictureParams.Builder()
				.setActions(actions)
				.build());
	}

	@Override
	public void onConfigurationChanged(@NonNull final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (player != null) {
			player.syncRotation(DeviceUtils.isRotateOn(this), newConfig.orientation);
		}

		boolean isFullscreen = player != null && player.isFullscreen();
		if (recommendationsSheet != null) {
			recommendationsSheet.setFullscreen(isFullscreen);
		}

		if (extensionManager.isEnabled(Constant.ENABLE_ORIENTATION_FULLSCREEN) && player != null) {
			if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				if (!player.isFullscreen()) player.enterFullscreen();
			} else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
				if (player.isFullscreen()) player.exitFullscreen();
			}
		}

	}

	static boolean shouldEnterPictureInPictureOnUserLeaveHint(
			@Nullable final LitePlayer player,
			@NonNull final ExtensionManager extensionManager,
			final boolean isInPictureInPictureMode,
			final boolean suppressAutoEnterPictureInPicture) {
		return player != null
				&& !isInPictureInPictureMode
				&& !suppressAutoEnterPictureInPicture
				&& extensionManager.isEnabled(Constant.ENABLE_PIP)
				&& player.shouldAutoEnterPictureInPicture();
	}

	static boolean shouldSuppressPictureInPictureForStartedActivity(@Nullable final Intent intent, @NonNull final String packageName) {
		if (intent == null) return false;
		final ComponentName component = intent.getComponent();
		return component != null && Objects.equals(packageName, component.getPackageName());
	}

	static void dispatchPictureInPictureModeChanged(@Nullable final LitePlayer player, final boolean isInPictureInPictureMode) {
		if (player != null) {
			player.onPictureInPictureModeChanged(isInPictureInPictureMode);
		}
	}

	static boolean shouldShowQueueUi(final boolean isInPictureInPictureMode) {
		return !isInPictureInPictureMode;
	}

	static boolean shouldReleasePlayerOnDestroy(final boolean isChangingConfigurations) {
		return !isChangingConfigurations;
	}

	static boolean shouldRestoreMiniPlayerOnResume(final boolean hasMiniPlayerSession, final boolean isInPictureInPictureMode) {
		return hasMiniPlayerSession && !isInPictureInPictureMode;
	}

	static boolean shouldSuspendMiniPlayerOnStop(final boolean hasMiniPlayerSession, final boolean isChangingConfigurations, final boolean isInPictureInPictureMode) {
		return hasMiniPlayerSession && !isChangingConfigurations && !isInPictureInPictureMode;
	}

	@SuppressWarnings("SameParameterValue")
	static int sheetMax(final int displayHeight, final int topInset, final int playerBottom, final boolean isMiniPlayer) {
		if (isMiniPlayer) return displayHeight - topInset;
		if (playerBottom <= 0 || playerBottom >= displayHeight) return displayHeight;
		return displayHeight - playerBottom;
	}

	@SuppressWarnings("SameParameterValue")
	static int sheetPad(final int systemBarInset, final int additionalPad) {
		return systemBarInset + additionalPad;
	}

	@SuppressWarnings("SameParameterValue")
	static int listPad(final int systemBarInset, final int additionalPad, final int trailingSpace) {
		return systemBarInset + Math.max(additionalPad, trailingSpace);
	}

	@SuppressWarnings("SameParameterValue")
	static int queueAnchor(final int displayHeight, final int topInset) {
		if (displayHeight <= 0) return 0;
		return (displayHeight / 2) - topInset - (displayHeight / 10);
	}

	private void setupQueueUI() {
		queueContainer = findViewById(R.id.queue_container);
		expandedQueueContainer = findViewById(R.id.expanded_queue_container);

		findViewById(R.id.btn_queue_close).setOnClickListener(v -> hideExpandedQueue());

		View.OnClickListener toggleExpand = v -> showExpandedQueue();
		findViewById(R.id.queue_header).setOnClickListener(toggleExpand);
		findViewById(R.id.btn_expand_queue).setOnClickListener(toggleExpand);

		final RecyclerView recyclerView = findViewById(R.id.queue_items_recycler);
		final TextView emptyView = findViewById(R.id.queue_empty);

		queueAdapter = new QueueAdapter(new QueueAdapter.Actions() {
			@Override
			public void onPlayRequested(@NonNull final QueueItem item) {
				if (item.getVideoUrl() != null) tabManager.playInWatch(item.getVideoUrl());
			}

			@Override
			public void onDeleteRequested(@NonNull final QueueItem item) {
				confirmRemove(() -> {
					final String videoId = item.getVideoId();
					if (videoId != null && queueRepository.remove(videoId)) {
						player.refreshQueueNavigationAvailability();
						syncQueueExpandedUI(recyclerView, emptyView);
					}
				});
			}

			@Override
			public void onDownloadRequested(@NonNull final QueueItem item) {
				if (item.getVideoUrl() != null) {
					triggerDownload(item.getVideoUrl());
				}
			}
		});

		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setAdapter(queueAdapter);

		final AtomicBoolean dirty = new AtomicBoolean(false);
		new ItemTouchHelper(new QueueTouch((from, to) -> {
			final boolean moved = queueAdapter.moveItem(from, to);
			if (moved) dirty.set(true);
			return moved;
		}, new QueueTouch.DragStateCallback() {
			@Override public void onDragStateChanged(final boolean dragging) {}
			@Override
			public void onDragFinished() {
				if (dirty.getAndSet(false)) {
					saveQueueOrder(queueAdapter.snapshotItems());
					player.refreshQueueNavigationAvailability();
				}
				syncQueueExpandedUI(recyclerView, emptyView);
			}
		})).attachToRecyclerView(recyclerView);

		final ImageButton orderButton = findViewById(R.id.btn_queue_order);
		if (orderButton != null) {
			renderLoop(orderButton, player.getLoopMode());
			orderButton.setOnClickListener(v -> {
				final PlayerLoopMode newMode = player.getLoopMode().next();
				player.setLoopMode(newMode);
				renderLoop(orderButton, newMode);
			});
		}

		final ImageButton downloadAllButton = findViewById(R.id.btn_queue_download);
		if (downloadAllButton != null) {
			downloadAllButton.setOnClickListener(v -> {
				List<QueueItem> items = queueRepository.getItems();
				if (items.isEmpty()) return;
				List<PlaylistDownloadItem> dItems = new ArrayList<>();
				for (int i = 0; i < items.size(); i++) {
					QueueItem item = items.get(i);
					if (item.getVideoId() == null || item.getVideoUrl() == null) continue;
					PlaylistDownloadItem dItem = new PlaylistDownloadItem(i, item.getVideoId(), item.getVideoUrl());
					dItem.setTitle(item.getTitle() != null ? item.getTitle() : item.getVideoId());
					dItem.setAuthor(item.getAuthor());
					dItem.setThumbnailUrl(item.getThumbnailUrl());
					dItem.setAvailabilityStatus(PlaylistDownloadItem.AvailabilityStatus.READY);
					dItem.setSelected(true);
					dItems.add(dItem);
				}
				new PlaylistDownloadDialog(getString(R.string.Queue), dItems, null, null, this, youtubeExtractor, tabManager).show();
			});
		}

		final ImageButton clearButton = findViewById(R.id.btn_queue_clear);
		if (clearButton != null) {
			clearButton.setOnClickListener(v -> confirmClear(() -> {
				queueRepository.clear();
				player.refreshQueueNavigationAvailability();
				syncQueueExpandedUI(recyclerView, emptyView);
			}));
		}

		engine.addListener(new Player.Listener() {
			@Override
			public void onPlaybackStateChanged(final int state) {
				mainHandler.post(() -> {
					if (expandedQueueContainer != null && expandedQueueContainer.getVisibility() == View.VISIBLE) {
						syncQueueExpandedUI(recyclerView, emptyView);
					}
					if (DeviceUtils.isInPictureInPictureMode(MainActivity.this)) {
						updatePictureInPictureActions();
					}
					updateQueueUI();
				});
			}

			@Override
			public void onIsPlayingChanged(boolean isPlaying) {
				mainHandler.post(() -> {
					if (DeviceUtils.isInPictureInPictureMode(MainActivity.this)) {
						updatePictureInPictureActions();
					}
					updateQueueUI();
				});
			}
		});

		queueRepository.addListener(this::updateQueueUI);
		updateQueueUI();
	}

	private void showExpandedQueue() {
		if (expandedQueueContainer != null) {
			expandedQueueContainer.setVisibility(View.VISIBLE);
			queueContainer.setVisibility(View.GONE);
			syncQueueExpandedUI(findViewById(R.id.queue_items_recycler), findViewById(R.id.queue_empty));
		}
	}

	private void hideExpandedQueue() {
		if (expandedQueueContainer != null) {
			expandedQueueContainer.setVisibility(View.GONE);
			updateQueueUI();
		}
	}

	private void syncQueueExpandedUI(@NonNull final RecyclerView recyclerView,
									 @NonNull final TextView emptyView) {
		if (queueAdapter == null) return;
		final List<QueueItem> items = queueRepository.getItems();
		final String loadedVideoId = player.getLoadedVideoId();
		queueAdapter.replaceItems(items, loadedVideoId);
		emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
		recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

		int playingPos = queueAdapter.playingPos();
		if (playingPos >= 0) {
			recyclerView.scrollToPosition(playingPos);
		}
	}

	private void syncQueueUiVisibility(final boolean isInPictureInPictureMode) {
		if (shouldShowQueueUi(isInPictureInPictureMode)) {
			updateQueueUI();
		} else {
			if (queueContainer != null) queueContainer.setVisibility(View.GONE);
			if (expandedQueueContainer != null) expandedQueueContainer.setVisibility(View.GONE);
		}
	}

	private void saveQueueOrder(@NonNull final List<QueueItem> order) {
		final List<QueueItem> current = queueRepository.getItems();
		for (int to = 0; to < order.size(); to++) {
			final String videoId = order.get(to).getVideoId();
			int from = -1;
			for (int i = 0; i < current.size(); i++) {
				if (Objects.equals(videoId, current.get(i).getVideoId())) {
					from = i;
					break;
				}
			}
			if (from >= 0 && from != to && queueRepository.move(from, to)) {
				current.add(to, current.remove(from));
			}
		}
	}

	private void confirmClear(@NonNull final Runnable onConfirmed) {
		new MaterialAlertDialogBuilder(this)
				.setMessage(R.string.clear_queue_confirmation)
				.setPositiveButton(R.string.confirm, (d, which) -> onConfirmed.run())
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void confirmRemove(@NonNull final Runnable onConfirmed) {
		new MaterialAlertDialogBuilder(this)
				.setMessage(R.string.remove_queue_item_confirmation)
				.setPositiveButton(R.string.confirm, (d, which) -> onConfirmed.run())
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void renderLoop(@NonNull final ImageButton button, @NonNull final PlayerLoopMode mode) {
		switch (mode) {
			case PLAYLIST_NEXT -> button.setImageResource(R.drawable.ic_playback_end_next);
			case LOOP_ONE -> button.setImageResource(R.drawable.ic_playback_end_loop);
			case PAUSE_AT_END -> button.setImageResource(R.drawable.ic_playback_end_pause);
			case PLAYLIST_RANDOM -> button.setImageResource(R.drawable.ic_playback_end_shuffle);
		}
	}

	private void updateQueueBarPosition() {
		if (queueContainer == null) return;

		ViewGroup.LayoutParams params = queueContainer.getLayoutParams();
		if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
			boolean isWatch = isWatchPage();
			if (isWatch) {
				marginParams.bottomMargin = navigationBarHeight;
				marginParams.leftMargin = 0;
				marginParams.rightMargin = 0;
			} else {
				marginParams.bottomMargin = navigationBarHeight + ViewUtils.dpToPx(this, 16);
				marginParams.leftMargin = ViewUtils.dpToPx(this, 8);
				marginParams.rightMargin = ViewUtils.dpToPx(this, 8);
			}
		}
		queueContainer.setLayoutParams(params);
	}

	private void updateQueueUI() {
		runOnUiThread(() -> {
			if (queueContainer == null) return;

			List<QueueItem> queue = queueRepository.getItems();
			if (queue.isEmpty() || !isWatchPage()) {
				queueContainer.setVisibility(View.GONE);
				if (expandedQueueContainer != null) expandedQueueContainer.setVisibility(View.GONE);
				return;
			}

			if (expandedQueueContainer != null && expandedQueueContainer.getVisibility() == View.VISIBLE) {
				syncQueueExpandedUI(findViewById(R.id.queue_items_recycler), findViewById(R.id.queue_empty));
			} else {
				queueContainer.setVisibility(View.VISIBLE);
				updateQueueBarPosition();
			}

			TextView titleText = findViewById(R.id.queue_title);
			if (titleText != null && !queue.isEmpty()) {
				titleText.setText(getString(R.string.queue_title_with_count, queue.size()));
			}
		});
	}

	private boolean isWatchPage() {
		YoutubeWebview webview = getWebview();
		if (webview == null) return false;
		String url = webview.getUrl();
		return (url != null && url.contains("/watch") && !url.contains("/shorts/")) || player.getLoadedVideoId() != null;
	}

	public void setUiVisibility(boolean visible) {
		if (DeviceUtils.isInPictureInPictureMode(this)) return;
		if (navBar != null) navBar.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (navBarDivider != null) navBarDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (visible) updateNavBarVisibility();
	}

	@Override
	protected void onResume() {
		super.onResume();
		linkDetection.setAppVisible(true, this);
		updateNavBarVisibility();
		updateQueueUI();
		if (player != null) player.refreshInternalButtonVisibility();
		suppressNextUserLeaveHintPictureInPicture = false;
		if (player != null && shouldRestoreMiniPlayerOnResume(player.isInAppMiniPlayer(), DeviceUtils.isInPictureInPictureMode(this))) {
			player.restoreInAppMiniPlayerUiIfNeeded();
		}
		linkDetection.checkClipboard();
	}

	@Override
	public void startActivity(@Nullable final Intent intent) {
		if (shouldSuppressPictureInPictureForStartedActivity(intent, getPackageName())) {
			suppressNextUserLeaveHintPictureInPicture = true;
		}
		super.startActivity(intent);
	}

	private void updateNavBarVisibility() {
		if (navBar != null) {
			navBar.update();
			navBarDivider.setVisibility(navBar.getVisibility());
		}
	}

	private void handleIntent(@Nullable Intent intent) {
		if (intent == null) return;
		final String action = intent.getAction();
		if ("OPEN_DOWNLOADS".equals(action)) {
			startActivity(new Intent(this, DownloadActivity.class));
			return;
		}
		if ("PLAY_VIDEO".equals(action)) {
			String url = intent.getStringExtra("url");
			if (url != null) tabManager.playInWatch(url);
			return;
		}

		if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
			Uri data = intent.getData();
			String type = intent.getType();
			if (type != null && type.startsWith("video/")) {
				Intent i = new Intent(this, OfflinePlayerActivity.class);
				i.setAction(Intent.ACTION_VIEW);
				i.setDataAndType(data, type);
				startActivity(i);
				return;
			}
			String url = data.toString();
			final String clean = url.replace(YOUTUBE_WWW_HOST, Constant.YOUTUBE_MOBILE_HOST);
			if (tabManager != null) tabManager.playInWatch(clean);
		} else if (Intent.ACTION_SEND.equals(action)) {
			String text = intent.getStringExtra(Intent.EXTRA_TEXT);
			if (text != null) {
				String url = extractUrlFromText(text);
				if (url != null) {
					final String clean = url.replace(YOUTUBE_WWW_HOST, Constant.YOUTUBE_MOBILE_HOST);
					if (tabManager != null) tabManager.playInWatch(clean);
				}
			}
		} else if (tabManager != null && tabManager.getWebview() == null) {
			tabManager.openTab(Constant.HOME_URL, UrlUtils.getPageClass(Constant.HOME_URL));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
		}
	}

	public void showMediaItemMenuDialog(@NonNull String payloadJson) {
		try {
			JsonObject payload = new JsonObject();
			try {
				payload = new Gson().fromJson(payloadJson, JsonObject.class);
			} catch (Exception ignored) {
			}
			if (payload == null || !payload.has("url")) return;
			showVideoOptionsDialog(payload.get("url").getAsString());
		} catch (Exception ignored) {}
	}

	private String extractUrlFromText(String text) {
		if (text == null) return null;
		Matcher m = Pattern.compile("https?://(?:www\\\\.|m\\\\.)?(?:youtube\\\\.com|youtu\\\\.be)/(?:watch\\\\?v=|v/|embed/|shorts/|playlist\\\\?list=)?[a-zA-Z0-9_-]+(?:[?&]\\\\S*)?", Pattern.CASE_INSENSITIVE).matcher(text);
		if (m.find()) {
			return m.group();
		}
		return null;
	}

	public void showVideoOptionsDialog(String url) {
		if (activeVideoOptionsDialog != null && activeVideoOptionsDialog.isShowing()) {
			activeVideoOptionsDialog.dismiss();
		}

		boolean hasVideoId = url.contains("v=") || url.contains("/watch")
				|| url.contains("video_id=") || url.contains("/live/") || url.contains("youtu.be/");
		boolean hasPlaylistId = url.contains("list=") || url.contains("/playlist");
		boolean isMix = url.contains("list=RD");

		boolean isPlaylist = hasPlaylistId && (!hasVideoId || url.contains("&list=") || url.contains("?list=")) && !isMix;

		@SuppressLint("InflateParams") View view = LayoutInflater.from(this).inflate(R.layout.dialog_video_options, null);
		activeVideoOptionsDialog = new MaterialAlertDialogBuilder(this)
				.setView(view)
				.create();

		TextView dialogTitle = view.findViewById(R.id.dialog_title);
		if (dialogTitle != null) {
			dialogTitle.setText(R.string.video_options);
		}

		View optionEnqueue = view.findViewById(R.id.option_enqueue);
		View optionDownload = view.findViewById(R.id.option_download);
		View optionPlaylist = view.findViewById(R.id.option_playlist);

		TextView enqueueText = view.findViewById(R.id.text_enqueue);
		final String videoId = YoutubeExtractor.getVideoId(url);
		boolean initiallyInQueue = false;
		if (videoId != null) {
			for (QueueItem item : queueRepository.getItems()) {
				if (videoId.equals(item.getVideoId())) {
					initiallyInQueue = true;
					break;
				}
			}
		}

		if (enqueueText != null) {
			enqueueText.setText(initiallyInQueue ? R.string.remove_from_queue : R.string.add_to_queue);
		}

		if (isPlaylist) {
			optionEnqueue.setVisibility(View.GONE);
			optionPlaylist.setVisibility(View.VISIBLE);
			optionDownload.setVisibility(View.GONE);
		} else {
			optionEnqueue.setVisibility(View.VISIBLE);
			optionPlaylist.setVisibility(View.GONE);
			optionDownload.setVisibility(View.VISIBLE);
		}

		optionEnqueue.setOnClickListener(v -> {
			activeVideoOptionsDialog.dismiss();
			toggleQueue(url);
		});

		optionDownload.setOnClickListener(v -> {
			activeVideoOptionsDialog.dismiss();
			triggerDownload(url);
		});

		optionPlaylist.setOnClickListener(v -> {
			activeVideoOptionsDialog.dismiss();
			triggerPlaylistDownload(url);
		});

		view.findViewById(R.id.option_share).setOnClickListener(v -> {
			activeVideoOptionsDialog.dismiss();
			shareUrl(url);
		});

		view.findViewById(R.id.btn_close).setOnClickListener(v -> activeVideoOptionsDialog.dismiss());

		activeVideoOptionsDialog.show();
	}

	public void toggleQueue(String url) {
		toggleQueue(url, null);
	}

	public void toggleQueue(String url, @Nullable QueueItem metadata) {
		final String videoId = YoutubeExtractor.getVideoId(url);
		if (videoId == null) return;

		boolean inQueue = false;
		for (QueueItem item : queueRepository.getItems()) {
			if (videoId.equals(item.getVideoId())) {
				inQueue = true;
				break;
			}
		}

		if (inQueue) {
			queueRepository.remove(videoId);
			Toast.makeText(this, R.string.queue_item_removed, Toast.LENGTH_SHORT).show();
		} else {
			if (metadata != null && metadata.getVideoId() != null && metadata.getTitle() != null) {
				queueRepository.add(metadata);
				queueWarmer.warmItem(metadata);
				player.refreshQueueNavigationAvailability();
				Toast.makeText(this, R.string.queue_item_added, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, R.string.queue_item_adding, Toast.LENGTH_SHORT).show();
				fetchAndEnqueue(url);
			}
		}
		updateQueueUI();
	}

	private void fetchAndEnqueue(String url) {
		executor.execute(() -> {
			try {
				VideoDetails details = youtubeExtractor.getVideoInfo(url);
				QueueItem item = new QueueItem();
				item.setVideoId(details.getId());
				item.setTitle(details.getTitle());
				item.setAuthor(details.getAuthor());
				item.setThumbnailUrl(details.getThumbnailUrl());
				item.setVideoUrl(url);
				queueRepository.add(item);
				runOnUiThread(() -> {
					Toast.makeText(this, R.string.queue_item_added, Toast.LENGTH_SHORT).show();
					player.refreshQueueNavigationAvailability();
					updateQueueUI();
				});
			} catch (Exception ignored) {
				runOnUiThread(() -> Toast.makeText(this, R.string.queue_item_unavailable, Toast.LENGTH_SHORT).show());
			}
		});
	}

	public void triggerDownload(String url) {
		String clean = url.replace(Constant.YOUTUBE_MOBILE_HOST, YOUTUBE_WWW_HOST);
		Toast.makeText(this, R.string.fetching_details, Toast.LENGTH_SHORT).show();
		mainHandler.postDelayed(() -> new DownloadDialog(clean, this, youtubeExtractor).show(), 600);
	}

	private void triggerPlaylistDownload(String url) {
		String clean = url.replace(Constant.YOUTUBE_MOBILE_HOST, YOUTUBE_WWW_HOST);
		Toast.makeText(this, R.string.playlist_download_loading, Toast.LENGTH_SHORT).show();
		executor.execute(() -> {
			try {
				PlaylistExtractor ex = NewPipe.getService(0).getPlaylistExtractor(clean);
				ex.fetchPage();
				String playlistName = ex.getName();
				List<PlaylistDownloadItem> dialogItems = new ArrayList<>();
				InfoItemsPage<StreamInfoItem> p = ex.getInitialPage();
				int index = 0;
				while (p != null) {
					for (StreamInfoItem item : p.getItems()) {
						String videoId = YoutubeExtractor.getVideoId(item.getUrl());
						if (videoId == null) continue;
						PlaylistDownloadItem dItem = new PlaylistDownloadItem(index++, videoId, item.getUrl());
						dItem.setTitle(item.getName());
						dItem.setAuthor(item.getUploaderName());
						dItem.setThumbnailUrl(item.getThumbnails().isEmpty() ? null : item.getThumbnails().get(item.getThumbnails().size() - 1).getUrl());
						dItem.setDurationSeconds(item.getDuration());
						dItem.setAvailabilityStatus(PlaylistDownloadItem.AvailabilityStatus.READY);
						dItem.setSelected(true);
						dialogItems.add(dItem);
					}
					if (!Page.isValid(p.getNextPage())) break;
					p = ex.getPage(p.getNextPage());
				}
				mainHandler.post(() -> new PlaylistDownloadDialog(playlistName, dialogItems, null, null, this, youtubeExtractor, tabManager).show());
			} catch (Exception e) {
				mainHandler.post(() -> Toast.makeText(this, R.string.playlist_download_failed_initial, Toast.LENGTH_SHORT).show());
			}
		});
	}

	private void shareUrl(String url) {
		final Intent i = new Intent(Intent.ACTION_SEND);
		i.putExtra(Intent.EXTRA_TEXT, url);
		i.setType("text/plain");
		startActivity(Intent.createChooser(i, getString(R.string.share_video)));
	}

	private void setupBackNavigation() {
		appBackCallback = new OnBackPressedCallback(true) {
			@Override public void handleOnBackPressed() {
				handleAppBack();
			}
		};
		getOnBackPressedDispatcher().addCallback(this, appBackCallback);
	}

	public void handleAppBack() {
		if (DeviceUtils.isInPictureInPictureMode(this)) {
			if (appBackCallback != null) appBackCallback.setEnabled(false);
			getOnBackPressedDispatcher().onBackPressed();
			if (appBackCallback != null) appBackCallback.setEnabled(true);
			return;
		}

		if (recommendationsSheet != null && recommendationsSheet.getVisibility() == View.VISIBLE) {
			recommendationsSheet.hide();
			return;
		}

		if (player != null && player.isFullscreen()) { player.exitFullscreen(); return; }
		if (expandedQueueContainer != null && expandedQueueContainer.getVisibility() == View.VISIBLE) {
			hideExpandedQueue();
			return;
		}

		if (tabManager != null && getWebview() != null) {
			tabManager.evaluateJavascript(
					"(function() { " +
							"  const closeBtn = document.querySelector(\"ytm-engagement-panel-renderer .engagement-panel-header-action-button, ytm-item-section-renderer[section-identifier=\\\"comment-item-section\\\"] .engagement-panel-header-action-button, .engagement-panel-container .engagement-panel-header-action-button, ytm-bottom-sheet-renderer .bottom-sheet-close-button, ytm-menu-renderer #close-button, .yt-spec-button-shape-next--size-m.yt-spec-button-shape-next--icon-only-btn[aria-label*=\\\"lose\\\"], .ytp-ad-overlay-close-button\");" +
							"  if (closeBtn && closeBtn.offsetParent !== null) { closeBtn.click(); return true; }" +
							"  return false;" +
							"})()",
					value -> {
						if ("true".equals(value)) return;
						runOnUiThread(this::handleAppBackInternal);
					}
			);
		} else {
			handleAppBackInternal();
		}
	}

	private void handleAppBackInternal() {
		boolean miniPlayerTriggered = false;
		if (player != null && player.canSuspendWatch() && !player.isInAppMiniPlayer() && extensionManager.isEnabled(Constant.ENABLE_IN_APP_MINI_PLAYER)) {
			player.enterInAppMiniPlayer();
			miniPlayerTriggered = true;
		}

		if (tabManager != null) {
			tabManager.evaluateJavascript("window.dispatchEvent(new Event(\"onGoBack\"));", null);
			YoutubeWebview web = getWebview();
			if (web != null && web.fullscreen != null && web.fullscreen.getVisibility() == View.VISIBLE) {
				tabManager.evaluateJavascript("document.exitFullscreen()", null);
				return;
			}
			if (tabManager.goBack()) {
				updateNavBarVisibility();
				updateQueueUI();
				return;
			}
		}

		if (miniPlayerTriggered) return;

		long time = System.currentTimeMillis();
		if (time - lastBackTime < DOUBLE_TAP_EXIT_INTERVAL_MS) finish();
		else {
			lastBackTime = time;
			Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
		}
	}

	@Nullable private YoutubeWebview getWebview() { return tabManager != null ? tabManager.getWebview() : null; }

	private void requestPermissions() {
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_CODE);
	}

	private void checkOpenByDefault() {
		MMKV kv = MMKV.defaultMMKV();
		if (kv.getBoolean("asked_open_by_default", false)) return;

		boolean alreadyVerified = false;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			DomainVerificationManager manager = getSystemService(DomainVerificationManager.class);
			try {
				DomainVerificationUserState userState = manager.getDomainVerificationUserState(getPackageName());
				if (userState != null && userState.isLinkHandlingAllowed()) {
					alreadyVerified = true;
				}
			} catch (Exception ignored) {}
		}

		if (!alreadyVerified) {
			new LinkTutorialDialog(this).show();
		}
	}

	@Override protected void onStart() {
		super.onStart();
		linkDetection.setAppVisible(true, this);
	}

	@Override protected void onStop() {
		linkDetection.setAppVisible(false, null);
		if (player != null && shouldSuspendMiniPlayerOnStop(player.isInAppMiniPlayer(), isChangingConfigurations(), DeviceUtils.isInPictureInPictureMode(this))) {
			player.suspendInAppMiniPlayerUiIfNeeded();
		}
		super.onStop();
	}

	@Override protected void onDestroy() {
		IncognitoManager.getInstance().removeListener(incognitoListener);
		super.onDestroy();
		if (playbackConnection != null) unbindService(playbackConnection);
		if (downloadConnection != null) unbindService(downloadConnection);
		if (player != null && shouldReleasePlayerOnDestroy(isChangingConfigurations())) player.release();
		executor.shutdown();
	}

	@Override public Activity getActivity() { return this; }
	@Override public void onPlay(String url) { tabManager.playInWatch(url); }
	@Override public void onDownload(String url) {
		if (url.contains("list=") && !url.contains("list=RD")) triggerPlaylistDownload(url);
		else triggerDownload(url);
	}
}
