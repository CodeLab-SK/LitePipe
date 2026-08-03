package com.hhst.youtubelite.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.hhst.youtubelite.Constants;
import com.hhst.youtubelite.IncognitoManager;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.extension.Constant;
import com.hhst.youtubelite.extension.ExtensionManager;
import com.hhst.youtubelite.extractor.YoutubeExtractor;
import com.hhst.youtubelite.extractor.potoken.PoTokenContextStore;
import com.hhst.youtubelite.extractor.potoken.PoTokenJsonUtils;
import com.hhst.youtubelite.extractor.potoken.PoTokenWebViewContext;
import com.hhst.youtubelite.player.LitePlayer;
import com.hhst.youtubelite.player.queue.QueueRepository;
import com.hhst.youtubelite.player.queue.QueueWarmer;
import com.hhst.youtubelite.util.ToastUtils;
import com.hhst.youtubelite.util.UrlUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Response;

@UnstableApi
public class YoutubeWebview extends WebView {

	private static final String PO_TOKEN_CONTEXT_SCRIPT = """
                    (function(){
                    try{
                    var ytcfgObject=globalThis.ytcfg||null;
                    var ytcfgData=ytcfgObject&&ytcfgObject.data_?ytcfgObject.data_:null;
                    var getCfg=function(key){
                    try{
                    if(ytcfgObject&&typeof ytcfgObject.get==='function'){
                    var value=ytcfgObject.get(key);
                    if(value!==undefined&&value!==null&&value!==''){return value;}
                    }
                    }catch(ignored){}
                    return ytcfgData&&ytcfgData[key]!==undefined?ytcfgData[key]:null;
                    };
                    var initialDataContext=globalThis.ytInitialData&&globalThis.ytInitialData.responseContext?globalThis.ytInitialData.responseContext:null;
                    var initialPlayerContext=globalThis.ytInitialPlayerResponse&&globalThis.ytInitialPlayerResponse.responseContext?globalThis.ytInitialPlayerResponse.responseContext:null;
                    var innertubeContext=getCfg('INNERTUBE_CONTEXT')||initialDataContext||initialPlayerContext||null;
                    var client=innertubeContext&&innertubeContext.client?innertubeContext.client:null;
                    var initialData=globalThis.ytInitialData||null;
                    var rawFlags=getCfg('EXPERIMENT_FLAGS')||getCfg('serializedExperimentFlags')||null;
                    var serializedExperimentFlags=null;
                    if(typeof rawFlags==='string'){serializedExperimentFlags=rawFlags;}
                    else if(rawFlags&&typeof rawFlags==='object'){
                    try{serializedExperimentFlags=Object.keys(rawFlags).map(function(key){return key+'='+rawFlags[key];}).join(',');}catch(ignored){}
                    }
                    var premium=false;
                    try{
                    var topbar=initialData&&initialData.topbar&&initialData.topbar.desktopTopbarRenderer?initialData.topbar.desktopTopbarRenderer:null;
                    var logo=topbar&&topbar.logo&&topbar.logo.topbarLogoRenderer?topbar.logo.topbarLogoRenderer:null;
                    var iconType=logo&&logo.iconImage?logo.iconImage.iconType:null;
                    var tooltip=logo&&typeof logo.tooltipText==='string'?logo.tooltipText.toLowerCase():null;
                    premium=!!(getCfg('IS_SUBSCRIBED_TO_PREMIUM')||getCfg('IS_PREMIUM_USER')||iconType==='YOUTUBE_PREMIUM_LOGO'||(tooltip&&tooltip.indexOf('premium')>=0));
                    }catch(ignored){}
                    return JSON.stringify({
                    url:location.href,
                    visitorData:getCfg('VISITOR_DATA')||(client?client.visitorData:null),
                    dataSyncId:getCfg('DATASYNC_ID')||getCfg('DELEGATED_SESSION_ID')||null,
                    clientVersion:getCfg('INNERTUBE_CLIENT_VERSION')||getCfg('INNERTUBE_CONTEXT_CLIENT_VERSION')||(client?client.clientVersion:null),
                    sessionIndex:getCfg('SESSION_INDEX')||null,
                    serializedExperimentFlags:serializedExperimentFlags,
                    loggedIn:!!(getCfg('LOGGED_IN')||getCfg('DATASYNC_ID')||getCfg('DELEGATED_SESSION_ID')),
                    premium:premium
                    });
                    }catch(error){
                    return JSON.stringify({error:String(error&&error.stack?error.stack:error)});
                    }
                    })();
                    """;
	private final ArrayList<String> scripts = new ArrayList<>();
	@NonNull
	private final Frame frame = new Frame();
	@Nullable
	public View fullscreen;
	@Nullable
	private OkHttpWebViewInterceptor okHttpWebViewInterceptor;
	@Nullable
	private Consumer<String> updateVisitedHistory;
	@Nullable private Consumer<String> onPageStartedListener;
	@Nullable private Consumer<String> onPageFinishedListener;
	private YoutubeExtractor youtubeExtractor;
	private LitePlayer player;
	private ExtensionManager extensionManager;
	private TabManager tabManager;
	private PoTokenProviderImpl poTokenProvider;
	private QueueRepository queueRepository;
	private QueueWarmer queueWarmer;
	@Nullable
	private PoTokenContextStore poTokenContextStore;
	private volatile boolean initialized;
	@Nullable
	private volatile String poTokenInflightKey;
	@Nullable
	private volatile String poTokenDoneKey;
	private boolean isDestroyed = false;
	private volatile long prefVersion = -1L;
    @Getter
	private volatile boolean musicBackgroundActive = false;

    @Nullable
    private View customView;
    @Nullable
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int originalSystemUiVisibility;

	public YoutubeWebview(@NonNull final Context context) { this(context, null); }
	public YoutubeWebview(@NonNull final Context context, @Nullable final AttributeSet attrs) { this(context, attrs, 0); }
	public YoutubeWebview(@NonNull final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) { super(context, attrs, defStyleAttr); }

	public void setOkHttpClient(@NonNull final OkHttpClient okHttpClient) {
		okHttpWebViewInterceptor = new OkHttpWebViewInterceptor(okHttpClient, new com.hhst.youtubelite.cache.WebViewCachePolicy());
	}

	public void setUpdateVisitedHistory(@Nullable final Consumer<String> updateVisitedHistory) {
		this.updateVisitedHistory = updateVisitedHistory;
	}

	public void setOnPageStartedListener(@Nullable final Consumer<String> onPageStartedListener) {
		this.onPageStartedListener = onPageStartedListener;
	}

	public void setOnPageFinishedListener(@Nullable final Consumer<String> onPageFinishedListener) {
		this.onPageFinishedListener = onPageFinishedListener;
	}

	public void setYoutubeExtractor(@NonNull final YoutubeExtractor youtubeExtractor) {
		this.youtubeExtractor = youtubeExtractor;
	}

	public void setPlayer(@NonNull final LitePlayer player) {
		this.player = player;
	}

	public void setExtensionManager(@NonNull final ExtensionManager extensionManager) {
		this.extensionManager = extensionManager;
	}

	public void setTabManager(@NonNull final TabManager tabManager) {
		this.tabManager = tabManager;
	}

	public void setPoTokenProvider(@NonNull final PoTokenProviderImpl poTokenProvider) {
		this.poTokenProvider = poTokenProvider;
	}

	public void setQueueRepository(@NonNull final QueueRepository queueRepository) {
		this.queueRepository = queueRepository;
	}

	public void setQueueWarmer(@NonNull final QueueWarmer queueWarmer) {
		this.queueWarmer = queueWarmer;
	}

	public void setPoTokenContextStore(@NonNull PoTokenContextStore poTokenContextStore) {
		this.poTokenContextStore = poTokenContextStore;
	}

	private boolean isWebViewPlayerMode() {
		return extensionManager != null && extensionManager.isEnabled(Constant.USE_WEBVIEW_PLAYER);
	}

	public boolean isPoTokenReadyCandidate() {
		String url = frame.url;
		return initialized && frame.finished && UrlUtils.isAllowedUrl(url) && !UrlUtils.isGoogleAccountsUrl(url) && !url.startsWith("file:");
	}

	@Override
	public void loadUrl(@NonNull final String url) {
		if (isDestroyed) return;
		final String resolvesUrl = UrlUtils.appendLanguage(sanitizeLoadUrl(url));
		Uri external = UrlUtils.externalUri(resolvesUrl);
		if (external != null) {
			openExternal(external);
			return;
		}
		if (canLoad(resolvesUrl)) {
			super.loadUrl(resolvesUrl);
			return;
		}
		if (canOpenExternal(resolvesUrl)) {
			openExternal(Uri.parse(resolvesUrl));
			return;
		}
		Log.w("YoutubeWebview", "Blocked unauthorized URL: " + resolvesUrl);
	}

	static boolean canLoad(@NonNull final String url) {
		if (UrlUtils.externalUri(url) != null) return false;
		if (UrlUtils.isAllowedUrl(url)) return true;
		final String scheme = scheme(url);
		return isScheme(scheme, "file")
				|| isScheme(scheme, "about")
				|| isScheme(scheme, "data")
				|| isScheme(scheme, "javascript");
	}

	static boolean canOpenExternal(@NonNull final String url) {
		if (UrlUtils.externalUri(url) != null) return true;
		if (UrlUtils.isAllowedUrl(url)) return false;
		final String scheme = scheme(url);
		return isScheme(scheme, "http") || isScheme(scheme, "https");
	}

	@Nullable
	private static String scheme(@NonNull final String url) {
		try {
			return URI.create(url).getScheme();
		} catch (final IllegalArgumentException ignored) {
			return null;
		}
	}

	private static boolean isScheme(@Nullable final String actual, @NonNull final String expected) {
		return expected.equalsIgnoreCase(actual);
	}

	private void openExternal(@NonNull final Uri uri) {
		try {
			getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
		} catch (final ActivityNotFoundException e) {
			ToastUtils.show(getContext(), R.string.application_not_found);
		}
	}
	private void injectWebViewPlayerScript() {
		try (InputStream is = getContext().getAssets().open("script/webview_player.js")) {
			byte[] buffer = new byte[is.available()];
			int read = is.read(buffer);
			if (read > 0) {
				String webviewPlayerJs = new String(buffer, StandardCharsets.UTF_8);
				postEvaluateJavascript(webviewPlayerJs);
			}
		} catch (Exception e) {
			Log.e("YoutubeWebview", "Failed to load webview_player.js", e);
		}
	}

	@NonNull
	String sanitizeLoadUrl(@NonNull final String url) {
		return sanitizeLoadUrl(url, queueRepository != null && queueRepository.isEnabled());
	}

	@NonNull
	static String sanitizeLoadUrl(@NonNull final String url, final boolean queueEnabled) {
		if (!queueEnabled || (!Constants.PAGE_WATCH.equals(UrlUtils.getPageClass(url)) && !Constants.PAGE_MUSIC_WATCH.equals(UrlUtils.getPageClass(url)))) {
			return url;
		}
		try {
			final URI uri = URI.create(url);
			final String rawQuery = uri.getRawQuery();
			if (TextUtils.isEmpty(rawQuery)) return url;
			boolean removed = false;
			final StringBuilder filteredQuery = new StringBuilder();
			for (final String part : rawQuery.split("&")) {
				if (part.isEmpty()) continue;
				final int separatorIndex = part.indexOf('=');
				final String key = separatorIndex >= 0 ? part.substring(0, separatorIndex) : part;
				if ("list".equalsIgnoreCase(key)) {
					removed = true;
					continue;
				}
				if (filteredQuery.length() > 0) filteredQuery.append('&');
				filteredQuery.append(part);
			}
			if (!removed) return url;
			return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(),
					filteredQuery.length() > 0 ? filteredQuery.toString() : null, uri.getRawFragment()).toString();
		} catch (final Exception ignored) { return url; }
	}

	@SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
	public void init() {
		if (isDestroyed) return;
		initialized = true;

		CookieManager.getInstance().flush();

		setFocusable(true);
		setFocusableInTouchMode(true);
		setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false);
		setOnLongClickListener(v -> true);

		CookieManager.getInstance().setAcceptCookie(true);
		CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

		final WebSettings settings = getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setCacheMode(WebSettings.LOAD_DEFAULT);
		settings.setLoadWithOverviewMode(true);
		settings.setUseWideViewPort(true);
		settings.setLoadsImagesAutomatically(true);
		settings.setSupportZoom(false);
		settings.setBuiltInZoomControls(false);
		settings.setMediaPlaybackRequiresUserGesture(false);
		settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
		settings.setUserAgentString(Constants.USER_AGENT);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			boolean isDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
			settings.setForceDark(isDark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
		}

		final JavascriptInterface jsInterface = new JavascriptInterface(this, youtubeExtractor, player, extensionManager, tabManager, poTokenProvider, queueRepository, queueWarmer);
		addJavascriptInterface(jsInterface, "android");
		addJavascriptInterface(jsInterface, "lite");
		setTag(jsInterface);

		if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
			if (!isWebViewPlayerMode()) {
				String criticalCss = readCriticalCssFromAssets();
				if (criticalCss != null && !criticalCss.isEmpty()) {
					String encoded = Base64.getEncoder().encodeToString(criticalCss.getBytes(StandardCharsets.UTF_8));
					String js = String.format("""
							(function(){
							try {
								var path = location.pathname;
								var pc = path.indexOf('/watch') === 0 ? 'watch' : (path === '/' ? 'home' : '');
								if (pc) document.documentElement.setAttribute('page-class', pc);
							} catch(e) {}

							let style = document.createElement('style');
							style.textContent = window.atob('%s');
							(document.head || document.documentElement).appendChild(style);
							})();
							""", encoded);
					WebViewCompat.addDocumentStartJavaScript(this, js, Collections.singleton("*"));
				}
			} else {
				String nativeJs = """
						(function(){
						try {
							var path = location.pathname;
							var pc = path.indexOf('/watch') === 0 ? 'watch' : (path === '/' ? 'home' : '');
							if (pc) document.documentElement.setAttribute('page-class', pc);
						} catch(e) {}
						window.__lp_webview_player = true;
						})();
						""";
				WebViewCompat.addDocumentStartJavaScript(this, nativeJs, Collections.singleton("*"));
			}
		}

		setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(@NonNull final WebView view, @NonNull final WebResourceRequest request) {
				if (isDestroyed) return true;
				final Uri uri = request.getUrl();
				final String host = uri.getHost();
				final String scheme = uri.getScheme();
				final String urlStr = uri.toString();

				if (Constants.PAGE_WATCH.equals(UrlUtils.getPageClass(urlStr)) || Constants.PAGE_MUSIC_WATCH.equals(UrlUtils.getPageClass(urlStr))) {
					if (queueWarmer != null) {
						queueWarmer.prioritizeUrl(urlStr);
					}
				}

				if (Objects.equals(scheme, "intent")) {
					try {
						final Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
						getContext().startActivity(intent);
					} catch (final ActivityNotFoundException | URISyntaxException e) {
						Log.e("WebView", e.toString());
					}
					return true;
				}

				if (Objects.equals(scheme, "vnd.youtube") || (host != null && host.equals("m.youtube.com") && uri.getPath() != null && uri.getPath().startsWith("/app"))) {
					return true;
				}

				if (host != null && (host.contains("accounts.google.com") || host.contains("google.com/accounts") || host.contains("accounts.youtube.com"))) {
					return false;
				}

				Uri external = UrlUtils.externalUri(uri);
				if (external != null) {
					openExternal(external);
					return true;
				}

				if (UrlUtils.isAllowedDomain(uri)) return false;
				openExternal(uri);
				return true;
			}

			@Nullable
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
				if (isDestroyed) return null;
				final Uri uri = request.getUrl();
				final String path = uri.getPath();
				if (path != null && path.equals("/live_chat") && okHttpWebViewInterceptor != null && okHttpWebViewInterceptor.canExecute(request)) {
					final String url = uri.toString();
					Response response = null;
					try {
						response = okHttpWebViewInterceptor.execute(request);
						if (response == null) return super.shouldInterceptRequest(view, request);

						final InputStream sourceStream = response.body().byteStream();
						final String injectedScript = "<script>(function(){ " +
								"document.addEventListener('tap', (e) => { " +
								"const msg = e.target.closest('yt-live-chat-text-message-renderer'); " +
								"if (!msg) return; " +
								"e.preventDefault(); " +
								"e.stopImmediatePropagation(); " +
								"}, true); " +
								"})();</script>";
						final InputStream injectedStream = new ByteArrayInputStream(injectedScript.getBytes(StandardCharsets.UTF_8));
						final Enumeration<InputStream> streams = Collections.enumeration(Arrays.asList(injectedStream, sourceStream));
						final SequenceInputStream sequenceInputStream = new SequenceInputStream(streams);
						return okHttpWebViewInterceptor.toWebResourceResponse(url, response, sequenceInputStream);
					} catch (final Exception ignored) {
						if (response != null) response.close();
					}
				}
				if (okHttpWebViewInterceptor != null) {
					final WebResourceResponse response = okHttpWebViewInterceptor.intercept(request);
					if (response != null) return response;
				}
				return super.shouldInterceptRequest(view, request);
			}

			@Override
			public void doUpdateVisitedHistory(@NonNull final WebView view, @NonNull final String url, final boolean isReload) {
				if (isDestroyed) return;
				if (IncognitoManager.getInstance().isIncognito()) return;
				super.doUpdateVisitedHistory(view, url, isReload);
				YoutubeWebview.this.postEvaluateJavascript("window.dispatchEvent(new Event('doUpdateVisitedHistory'));");
				injectJavaScript(url);
				if (updateVisitedHistory != null) updateVisitedHistory.accept(url);
				post(YoutubeWebview.this::refreshPoTokenContext);
			}

			@Override
			public void onPageStarted(@NonNull final WebView view, @NonNull final String url, @Nullable final Bitmap favicon) {
				if (isDestroyed) return;
				super.onPageStarted(view, url, favicon);
				frame.epoch.incrementAndGet();
				frame.finished = false;
				frame.url = url;
				poTokenInflightKey = null;
				poTokenDoneKey = null;

				injectJavaScript(url);
				YoutubeWebview.this.postEvaluateJavascript("window.dispatchEvent(new Event('onPageStarted'));");
				if (onPageStartedListener != null) onPageStartedListener.accept(url);
			}

			@Override
			public void onPageFinished(@NonNull final WebView view, @NonNull final String url) {
				if (isDestroyed) return;
				super.onPageFinished(view, url);
				frame.finished = true;
				frame.url = url;
				injectJavaScript(url);
				YoutubeWebview.this.postEvaluateJavascript("window.dispatchEvent(new Event('onPageFinished'));");
				YoutubeWebview.this.postEvaluateJavascript("window.dispatchEvent(new Event('onProgressChangeFinish'));");

				refreshPoTokenContext();
				enableBackgroundPlayback();
				injectWebViewPlayerScript();

				if (onPageFinishedListener != null) onPageFinishedListener.accept(url);
				postDelayed(() -> {
					if (view.getParent() instanceof SwipeRefreshLayout) {
						((SwipeRefreshLayout) view.getParent()).setRefreshing(false);
					}
				}, 500);
			}

			@Override
			public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
				if (request.isForMainFrame()) {
					int errorCode = error.getErrorCode();
					if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
						return;
					}
					String failingUrl = request.getUrl().toString();
					String description = error.getDescription().toString();

					String encodedDescription = URLEncoder.encode(description, StandardCharsets.UTF_8);
					String encodedUrl = URLEncoder.encode(failingUrl, StandardCharsets.UTF_8);
					String url = "file:///android_asset/page/error.html?description=" + encodedDescription + "&errorCode=" + errorCode + "&url=" + encodedUrl;
					post(() -> view.loadUrl(url));
				}
			}

			@Override
			public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
				if (request.isForMainFrame()) {
					int statusCode = errorResponse.getStatusCode();
					String failingUrl = request.getUrl().toString();
					String reason = errorResponse.getReasonPhrase();

					String encodedDescription = URLEncoder.encode("HTTP Error " + statusCode + ": " + reason, StandardCharsets.UTF_8);
					String encodedUrl = URLEncoder.encode(failingUrl, StandardCharsets.UTF_8);
					String url = "file:///android_asset/page/error.html?description=" + encodedDescription + "&errorCode=" + statusCode + "&url=" + encodedUrl;
					post(() -> view.loadUrl(url));
				}
			}
		});

		setWebChromeClient(new WebChromeClient() {
			@Override
			public boolean onConsoleMessage(@NonNull final ConsoleMessage consoleMessage) {
				Log.d("js-log", consoleMessage.message() + " -- From line " + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
				return super.onConsoleMessage(consoleMessage);
			}

			@Override
			public void onProgressChanged(@NonNull final WebView view, final int progress) {
				if (isDestroyed) return;
				if (progress > 10 && progress < 100 && progress % 20 == 0) {
					injectJavaScript(frame.url);
				}
				if (progress >= 100) {
					YoutubeWebview.this.postEvaluateJavascript("window.dispatchEvent(new Event('onProgressChangeFinish'));");
				}
				super.onProgressChanged(view, progress);
			}

			@Override
			public Bitmap getDefaultVideoPoster() {
				return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
			}

			@Override
			public void onShowCustomView(@NonNull final View view, @NonNull final CustomViewCallback callback) {
				if (isDestroyed) return;
                if (customView != null) {
                    onHideCustomView();
                    return;
                }

                Activity activity = getActivity();
                if (activity == null) return;

                customView = view;
                originalSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
                customViewCallback = callback;
                
                if (player != null) player.setNativeFullscreen(true);
                
                ((FrameLayout) activity.getWindow().getDecorView()).addView(customView, new FrameLayout.LayoutParams(-1, -1));
                activity.getWindow().getDecorView().setSystemUiVisibility(3846);
			}

			@Override
			public void onHideCustomView() {
                hideCustomView();
			}
		});
	}

    public boolean isInFullscreen() {
        return customView != null;
    }

    public void exitFullscreen() {
        if (isInFullscreen()) {
            hideCustomView();
        }
    }

    private void hideCustomView() {
        if (isDestroyed || customView == null) {
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
            return;
        }

        Activity activity = getActivity();
        if (activity != null) {
            ((FrameLayout) activity.getWindow().getDecorView()).removeView(customView);
            activity.getWindow().getDecorView().setSystemUiVisibility(originalSystemUiVisibility);
        }
        customView = null;

        if (player != null) player.setNativeFullscreen(false);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Nullable
    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

	 private void enableBackgroundPlayback() {
		if (!UrlUtils.isMusicUrl(getUrl())) return;
		evaluateJavascript("""
        (function(){
            if(window._lp_bg_injected) return;
            window._lp_bg_injected = true;
            window._lp_bg_active = false;
        
            const block = e => {
                if(window._lp_bg_active) {
                    if (e.type === 'visibilitychange' || e.type === 'webkitvisibilitychange') {
                        e.stopImmediatePropagation();
                    }
                }
            };
            window.addEventListener('visibilitychange', block, true);
            window.addEventListener('webkitvisibilitychange', block, true);
        })();
        """, null);
	}

	public void setMusicBackgroundActive(boolean active) {
		if (initialized && !isDestroyed && UrlUtils.isMusicUrl(getUrl())) {
			musicBackgroundActive = active;
			evaluateJavascript("window._lp_bg_active = " + active + ";", null);
			if (!active && player != null) {
				player.resyncMusicUi();
			}
		}
	}

	public void refreshPoTokenContext() {
		PoTokenContextStore contextStore = poTokenContextStore;
		String pageUrl = frame.url;
		long capturedPageEpoch = frame.epoch.get();
		if (contextStore == null || !isShown() || !isPoTokenReadyCandidate() || pageUrl == null) {
			return;
		}
		String key = capturedPageEpoch + "|" + pageUrl;
		if (key.equals(poTokenInflightKey) || key.equals(poTokenDoneKey)) return;
		poTokenInflightKey = key;
		super.evaluateJavascript(PO_TOKEN_CONTEXT_SCRIPT, rawValue -> {
			if (contextStore != poTokenContextStore || capturedPageEpoch != frame.epoch.get() || !Objects.equals(pageUrl, frame.url)) {
				poTokenInflightKey = null;
				return;
			}
			PoTokenWebViewContext context = PoTokenWebViewContext.fromJson(pageUrl, capturedPageEpoch, PoTokenJsonUtils.normalizeEvaluateJavascriptResult(rawValue));
			if (context != null) {
				contextStore.update(context);
				poTokenDoneKey = key;
			}
			poTokenInflightKey = null;
		});
	}

	@Nullable
	private String readCriticalCssFromAssets() {
		try (InputStream is = getContext().getAssets().open("style/critical.css")) {
			byte[] buffer = new byte[is.available()];
			int read = is.read(buffer);
			return read > 0 ? new String(buffer, StandardCharsets.UTF_8) : null;
		} catch (Exception e) {
			Log.e("YoutubeWebview", "Failed to read critical.css", e);
			return null;
		}
	}

	private void injectJavaScript(@Nullable String url) {
		if (UrlUtils.isGoogleAccountsUrl(url)) return;
		for (String js : scripts) postEvaluateJavascript(js);
	}

	public void injectJavaScriptContent(@NonNull String js) {
		addScript(js);
	}

	public void syncPreferences() {
		if (extensionManager == null) return;
		long version = extensionManager.version();
		if (version == prefVersion) return;
		prefVersion = version;
		evaluateJavascript("window.dispatchEvent(new Event('litePreferencesChanged'));", null);
	}

	private void addScript(@NonNull String js) {
		Runnable task = () -> {
			if (isDestroyed) return;
			scripts.add(js);
			if (frame.url != null && !UrlUtils.isGoogleAccountsUrl(frame.url)) {
				evaluateJavascript(js, null);
			}
		};
		if (Looper.myLooper() == Looper.getMainLooper()) {
			task.run();
		} else {
			post(task);
		}
	}

	private void postEvaluateJavascript(@NonNull String script) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			try {
				evaluateJavascript(script, null);
			} catch (Exception e) {
				Log.e("YoutubeWebview", "Error evaluating javascript", e);
			}
		} else {
			post(() -> evaluateJavascript(script, null));
		}
	}

	@Override
	public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String> resultCallback) {
		if (isDestroyed) return;
		try {
			super.evaluateJavascript(script, resultCallback);
		} catch (Exception e) {
			Log.e("YoutubeWebview", "Error evaluating javascript", e);
		}
	}

	@Override
	public void destroy() {
		isDestroyed = true;
		removeJavascriptInterface("android");
		removeJavascriptInterface("lite");
		super.destroy();
	}

	private static final class Frame {
		@NonNull
		private final AtomicLong epoch = new AtomicLong();
		private volatile boolean finished;
		@Nullable
		private volatile String url;
	}
}