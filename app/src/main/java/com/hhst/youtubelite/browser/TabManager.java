package com.hhst.youtubelite.browser;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.Constants;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.player.LitePlayer;
import com.hhst.youtubelite.player.queue.QueueWarmer;
import com.hhst.youtubelite.ui.MainActivity;
import com.hhst.youtubelite.util.StreamIOUtils;
import com.hhst.youtubelite.util.UrlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.scopes.ActivityScoped;
import lombok.Getter;

@ActivityScoped
@UnstableApi
public class TabManager {

	private static final String TAG = "TabManager";
	private static final String SCRIPT_INIT = "init.js";
	private static final String SCRIPT_INIT_MIN = "init.min.js";
	private static final Set<String> NAV_TAGS = Set.of(Constants.PAGE_HOME, Constants.PAGE_SUBSCRIPTIONS, Constants.PAGE_LIBRARY);
	
	private final Activity activity;
	private final Lazy<LitePlayer> player;
	private final ExtensionManager extensionManager;
	private final QueueWarmer queueWarmer;
	private final Deque<YoutubeFragment> tabs = new LinkedList<>();
	
	@Getter
	@Nullable
	private YoutubeFragment tab;
	@Nullable
	private YoutubeFragment suspendedWatchFragment;

	private static final List<String> cachedScripts = new ArrayList<>();
	private static final List<String> cachedStyles = new ArrayList<>();
	private static boolean assetsLoaded = false;

	@Nullable private Consumer<String> onPageFinishedListener;

	@Inject
	public TabManager(@NonNull final Activity activity,
	                  @NonNull final Lazy<LitePlayer> player,
	                  @NonNull final ExtensionManager extensionManager,
	                  @NonNull final QueueWarmer queueWarmer) {
		this.activity = activity;
		this.player = player;
		this.extensionManager = extensionManager;
		this.queueWarmer = queueWarmer;
	}

	@NonNull
	private LitePlayer litePlayer() {
		return Objects.requireNonNull(player.get());
	}

	public void onUrlChanged(@NonNull final YoutubeFragment fragment, @NonNull final String url) {
		if (fragment != tab) return;
		final LitePlayer litePlayer = litePlayer();
		final String pageClass = UrlUtils.getPageClass(url);
		final boolean isWatch = Constants.PAGE_WATCH.equals(pageClass);

		if (activity instanceof MainActivity mainActivity) {
			mainActivity.setUiVisibility(!isWatch);
		}

		if (isWatch) {
			if (litePlayer.isInAppMiniPlayer()) litePlayer.exitInAppMiniPlayer();
			litePlayer.play(url);
			return;
		}

		if (suspendedWatchFragment != null || litePlayer.isInAppMiniPlayer()) return;
		litePlayer.hide();
	}

	public void onPageFinished(@NonNull final YoutubeFragment fragment, @NonNull final String url) {
		if (Constants.PAGE_WATCH.equals(UrlUtils.getPageClass(url))) {
			if (activity instanceof MainActivity mainActivity) {
				mainActivity.hideWatchLoadingOverlay();
			}
		}
		if (fragment == tab && onPageFinishedListener != null) {
			onPageFinishedListener.accept(url);
		}
	}

	public void setOnPageFinishedListener(@Nullable Consumer<String> listener) {
		this.onPageFinishedListener = listener;
	}

	private void onTabChanged() {
		final YoutubeFragment tab = this.tab;
		if (tab == null) return;
		final String url = tab.getUrl();
		if (url == null) return;
		onUrlChanged(tab, url);
	}

	@NonNull
	private FragmentManager getFm() {
		return ((FragmentActivity) activity).getSupportFragmentManager();
	}

	@NonNull
	protected YoutubeFragment createFragment(@NonNull final String url, @NonNull final String tag) {
		return YoutubeFragment.newInstance(url, tag);
	}

	public void openTab(@NonNull final String url, @Nullable String tag) {
		if (tag == null) tag = UrlUtils.getPageClass(url);
		final String targetTag = tag;
		
		if (Constants.PAGE_WATCH.equals(targetTag)) {
			if (activity instanceof MainActivity mainActivity) {
				mainActivity.showWatchLoadingOverlay();
			}
		}

		final YoutubeFragment tab = this.tab;
		if (Constants.PAGE_WATCH.equals(targetTag) && openWatchTab(url)) return;
		if (tab != null && ((targetTag.equals(tab.getMTag()) && NAV_TAGS.contains(targetTag)) || targetTag.equals(Constants.PAGE_SHORTS))) {
			if (!url.equals(tab.getUrl())) tab.loadUrl(url);
			return;
		}
		final String homeTag = Constants.PAGE_HOME;
		final FragmentTransaction ft = getFm().beginTransaction();
		final boolean suspendCurrentWatch = shouldSuspendCurrentWatch(
						tab != null ? tab.getMTag() : null,
						getFragmentPageClass(tab),
						targetTag,
						extensionManager.isEnabled(com.hhst.youtubelite.extension.Constant.ENABLE_IN_APP_MINI_PLAYER),
						litePlayer().canSuspendWatch());
		if (suspendCurrentWatch) suspendCurrentWatch(ft);
		else if (tab != null) ft.hide(tab);
		
		if (!NAV_TAGS.contains(targetTag)) {
			final var first = tabs.peekFirst();
			if (first == null || !Constants.PAGE_HOME.equals(first.getMTag())) {
				final YoutubeFragment home = createFragment(Constants.HOME_URL, Constants.PAGE_HOME);
				tabs.offerFirst(home);
				ft.add(R.id.fragment_container, home, Constants.PAGE_HOME);
				ft.hide(home);
			}

			final YoutubeFragment next = createFragment(url, targetTag);
			this.tab = next;
			tabs.offer(next);
			ft.add(R.id.fragment_container, next, targetTag);
		} else {
			YoutubeFragment home = null;
			YoutubeFragment nav = null;
			for (final YoutubeFragment t : tabs) {
				final String tTag = t.getMTag();
				if (homeTag.equals(tTag)) home = t;
				else if (targetTag.equals(tTag)) nav = t;
				else ft.remove(t);
			}
			tabs.clear();
			if (home == null) {
				home = createFragment(Constants.HOME_URL, homeTag);
				ft.add(R.id.fragment_container, home, homeTag);
			}
			tabs.offer(home);
			if (homeTag.equals(targetTag)) {
				this.tab = home;
			} else {
				if (nav == null) {
					nav = createFragment(url, targetTag);
					ft.add(R.id.fragment_container, nav, targetTag);
				}
				tabs.offer(nav);
				this.tab = nav;
			}
		}
		final YoutubeFragment next = this.tab;
		if (next == null) return;
		ft.show(next);
		commitAndRun(ft, () -> {
			if (suspendCurrentWatch) enterMiniPlayer();
			onTabChanged();
		});
	}

	private synchronized void loadAssets() {
		if (assetsLoaded) return;
		final AssetManager assetManager = activity.getAssets();
		try {
			String[] styles = assetManager.list("style");
			if (styles != null) {
				for (String style : styles) {
					try (InputStream is = assetManager.open("style/" + style)) {
						String content = StreamIOUtils.readInputStream(is);
						if (content != null) cachedStyles.add(content);
					}
				}
			}
			String[] scripts = assetManager.list("script");
			if (scripts != null) {
				List<String> list = new ArrayList<>(Arrays.asList(scripts));
				String initScript = list.contains(SCRIPT_INIT) ? SCRIPT_INIT : list.contains(SCRIPT_INIT_MIN) ? SCRIPT_INIT_MIN : null;
				if (initScript != null) {
					try (InputStream is = assetManager.open("script/" + initScript)) {
						String content = StreamIOUtils.readInputStream(is);
						if (content != null) cachedScripts.add(0, content);
					}
					list.remove(initScript);
				}
				for (String script : list) {
					try (InputStream is = assetManager.open("script/" + script)) {
						String content = StreamIOUtils.readInputStream(is);
						if (content != null) cachedScripts.add(content);
					}
				}
			}
			assetsLoaded = true;
		} catch (IOException e) {
			Log.e(TAG, "Failed to cache assets", e);
		}
	}

	public void injectScripts(@NonNull final YoutubeWebview webview) {
		if (!assetsLoaded) loadAssets();
		for (String css : cachedStyles) {
			webview.injectCssContent(css);
		}
		for (String js : cachedScripts) {
			webview.injectJavaScriptContent(js);
		}
	}

	@Nullable
	public YoutubeWebview getWebview() {
		final YoutubeFragment tab = this.tab;
		return tab != null ? tab.getWebview() : null;
	}

	public void evaluateJavascript(@NonNull final String script, @Nullable final ValueCallback<String> callback) {
		final YoutubeWebview webview = getWebview();
		if (webview != null) webview.evaluateJavascript(script, callback);
	}

	public void evalWatchJs(@NonNull final String script, @Nullable final ValueCallback<String> callback) {
		final YoutubeWebview webview = resolveWatchWebview();
		if (webview != null) {
			webview.evaluateJavascript(script, callback);
			return;
		}
		if (callback != null) callback.onReceiveValue(null);
	}

	public void playInWatch(@NonNull final String url) {
		if (activity instanceof MainActivity mainActivity) {
			mainActivity.showWatchLoadingOverlay();
		}
		queueWarmer.prioritizeUrl(url);
		litePlayer().play(url);
		openTab(url, Constants.PAGE_WATCH);
	}

	public boolean canGoBackInWatch() {
		final YoutubeWebview webview = resolveWatchWebview();
		return webview != null && webview.canGoBack();
	}

	public void goBackInWatch() {
		final YoutubeWebview webview = resolveWatchWebview();
		if (webview != null && webview.canGoBack()) {
			webview.goBack();
		}
	}

	public boolean watchHasPlaylist() {
		final String url = getWatchUrl();
		return url != null && url.contains("list=");
	}

	@Nullable
	public String getWatchUrl() {
		final YoutubeFragment suspended = suspendedWatchFragment;
		if (isWatchTab(suspended)) {
			return suspended.getUrl();
		}
		final YoutubeFragment tab = this.tab;
		return isWatchTab(tab) ? tab.getUrl() : null;
	}

	public void loadUrl(@NonNull final String url) {
		final YoutubeWebview webview = getWebview();
		if (webview != null) {
			if (Constants.PAGE_WATCH.equals(UrlUtils.getPageClass(url))) {
				if (activity instanceof MainActivity mainActivity) {
					mainActivity.showWatchLoadingOverlay();
				}
			}
			webview.loadUrl(url);
		}
	}

	@Nullable
	private YoutubeWebview resolveWatchWebview() {
		final YoutubeFragment suspended = suspendedWatchFragment;
		if (isWatchTab(suspended)) {
			final YoutubeWebview webView = suspended.getWebview();
			if (webView != null) {
				return webView;
			}
		}
		final YoutubeFragment tab = this.tab;
		if (!isWatchTab(tab)) {
			return null;
		}
		return tab.getWebview();
	}

	private boolean isWatchTab(@Nullable final YoutubeFragment fragment) {
		if (fragment == null) return false;
		if (Constants.PAGE_WATCH.equals(fragment.getMTag())) {
			return true;
		}
		return Constants.PAGE_WATCH.equals(getFragmentPageClass(fragment));
	}

	@Nullable
	private String getFragmentPageClass(@Nullable final YoutubeFragment fragment) {
		if (fragment == null) return null;
		final String url = fragment.getUrl();
		if (url != null) {
			return UrlUtils.getPageClass(url);
		}
		return fragment.getMTag();
	}

	private boolean openWatchTab(@NonNull final String url) {
		final YoutubeFragment watch = findWatchTab();
		if (watch == null) return false;
		final YoutubeFragment tab = this.tab;
		if (watch == tab) {
			if (!url.equals(watch.getUrl())) watch.loadUrl(url);
			onUrlChanged(watch, url);
			return true;
		}
		final boolean restoringSuspendedWatch = watch == suspendedWatchFragment;
		final FragmentTransaction ft = getFm().beginTransaction();
		if (tab != null) ft.hide(tab);
		tabs.remove(watch);
		tabs.offerLast(watch);
		this.tab = watch;
		if (restoringSuspendedWatch) {
			suspendedWatchFragment = null;
		}
		if (!url.equals(watch.getUrl())) watch.loadUrl(url);
		ft.show(watch);
		commitAndRun(ft, () -> {
			if (restoringSuspendedWatch) {
				final LitePlayer litePlayer = litePlayer();
				litePlayer.exitInAppMiniPlayer();
				litePlayer.setMiniPlayerCallbacks(null, null);
			}
			onUrlChanged(watch, url);
		});
		return true;
	}

	public void hidePlayer() {
		if (suspendedWatchFragment != null) return;
		litePlayer().hide();
	}

	public boolean goBack() {
		final YoutubeFragment tab = this.tab;
		if (tab == null) return false;
		final YoutubeWebview webview = tab.getWebview();
		final Page prev = prev(tab);
		final boolean hasBackStack = tabs.size() > 1;
		final boolean isMiniPlayerEnabled = extensionManager.isEnabled(com.hhst.youtubelite.extension.Constant.ENABLE_IN_APP_MINI_PLAYER);

		if (shouldSuspendCurrentWatchOnBack(
						tab.getMTag(),
						getFragmentPageClass(tab),
						isMiniPlayerEnabled,
						litePlayer().canSuspendWatch())) {

			if (prev != null
							&& !Constants.PAGE_WATCH.equals(prev.tag())
							&& webview != null && webview.canGoBack()) {
				webview.goBack();
				return true;
			}

			final YoutubeFragment prevTab = getPreviousTab();
			final FragmentTransaction ft = getFm().beginTransaction();
			suspendCurrentWatch(ft);
			final YoutubeFragment next = prevTab != null ? prevTab : home(ft);
			this.tab = next;
			ft.show(next);
			commitAndRun(ft, () -> {
				enterMiniPlayer();
				onTabChanged();
			});
			return true;
		}

		if (webview != null && webview.canGoBack()) {
			webview.goBack();
			return true;
		} else if (hasBackStack) {
			final FragmentTransaction ft = getFm().beginTransaction();
			final YoutubeFragment removed = tabs.pollLast();
			if (removed != null) ft.remove(removed);
			final YoutubeFragment next = tabs.peekLast();
			this.tab = next;
			if (next != null) ft.show(next);
			commitAndRun(ft, this::onTabChanged);
			return true;
		}
		return false;
	}

	private void suspendCurrentWatch(@NonNull final FragmentTransaction ft) {
		final YoutubeFragment tab = this.tab;
		if (tab == null) return;
		suspendedWatchFragment = tab;
		tabs.pollLast();
		ft.hide(tab);
	}

	private void enterMiniPlayer() {
		final LitePlayer litePlayer = litePlayer();
		litePlayer.setMiniPlayerCallbacks(this::restoreSuspendedWatch, this::clearSuspendedWatch);
		litePlayer.enterInAppMiniPlayer();
	}

	private void restoreSuspendedWatch() {
		final YoutubeFragment suspended = suspendedWatchFragment;
		if (suspended == null) return;
		final FragmentTransaction ft = getFm().beginTransaction();
		final YoutubeFragment tab = this.tab;
		if (tab != null) ft.hide(tab);
		tabs.offerLast(suspended);
		this.tab = suspended;
		suspendedWatchFragment = null;
		ft.show(suspended);
		final LitePlayer litePlayer = litePlayer();
		commitAndRun(ft, () -> {
			litePlayer.exitInAppMiniPlayer();
			litePlayer.setMiniPlayerCallbacks(null, null);
			onTabChanged();
		});
	}

	private void clearSuspendedWatch() {
		final LitePlayer litePlayer = litePlayer();
		final YoutubeFragment suspended = suspendedWatchFragment;
		if (suspended != null) {
			final FragmentTransaction ft = getFm().beginTransaction();
			ft.remove(suspended);
			ft.commit();
			suspendedWatchFragment = null;
		}
		litePlayer.exitInAppMiniPlayer();
		litePlayer.setMiniPlayerCallbacks(null, null);
	}

	private void commitAndRun(@NonNull final FragmentTransaction ft, @NonNull final Runnable afterCommit) {
		ft.runOnCommit(afterCommit);
		ft.commit();
	}

	@Nullable
	private YoutubeFragment findWatchTab() {
		if (isWatchTab(tab)) {
			return tab;
		}
		if (isWatchTab(suspendedWatchFragment)) {
			return suspendedWatchFragment;
		}
		for (final YoutubeFragment fragment : tabs) {
			if (isWatchTab(fragment)) {
				return fragment;
			}
		}
		return null;
	}

	@Nullable
	private YoutubeFragment getPreviousTab() {
		if (tabs.size() < 2) return null;
		final var iterator = tabs.descendingIterator();
		iterator.next();
		return iterator.hasNext() ? iterator.next() : null;
	}

	@NonNull
	private YoutubeFragment home(@NonNull final FragmentTransaction ft) {
		for (final YoutubeFragment frag : tabs) {
			if (Constants.PAGE_HOME.equals(frag.getMTag())) {
				return frag;
			}
		}
		final YoutubeFragment home = createFragment(Constants.HOME_URL, Constants.PAGE_HOME);
		tabs.offerFirst(home);
		ft.add(R.id.fragment_container, home, Constants.PAGE_HOME);
		return home;
	}

	@Nullable
	private Page prev(@Nullable final YoutubeFragment frag) {
		final WebBackForwardList hist = history(frag);
		if (hist == null) return null;
		final int i = hist.getCurrentIndex() - 1;
		if (i < 0) return null;
		final WebHistoryItem item = hist.getItemAtIndex(i);
		return item != null ? page(item.getUrl()) : null;
	}

	@Nullable
	private WebBackForwardList history(@Nullable final YoutubeFragment frag) {
		if (frag == null) return null;
		final YoutubeWebview webview = frag.getWebview();
		return webview != null ? webview.copyBackForwardList() : frag.getHistorySnapshot();
	}

	@Nullable
	private Page page(@Nullable final String url) {
		if (url == null) return null;
		return new Page(url, UrlUtils.getPageClass(url));
	}

	static boolean shouldSuspendCurrentWatch(@Nullable final String currentMTag,
	                                         @Nullable final String currentPageClass,
	                                         @Nullable final String targetTag,
	                                         final boolean inAppMiniPlayerEnabled,
	                                         final boolean canSuspendWatch) {
		return (Constants.PAGE_WATCH.equals(currentMTag) || Constants.PAGE_WATCH.equals(currentPageClass))
						&& !Constants.PAGE_WATCH.equals(targetTag)
						&& inAppMiniPlayerEnabled
						&& canSuspendWatch;
	}

	static boolean shouldSuspendCurrentWatchOnBack(@Nullable final String currentMTag,
	                                               @Nullable final String currentPageClass,
	                                               final boolean inAppMiniPlayerEnabled,
	                                               final boolean canSuspendWatch) {
		return (Constants.PAGE_WATCH.equals(currentMTag) || Constants.PAGE_WATCH.equals(currentPageClass))
						&& inAppMiniPlayerEnabled
						&& canSuspendWatch;
	}

	private record Page(String url, String tag) {
	}
}
