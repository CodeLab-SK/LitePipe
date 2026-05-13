package com.hhst.youtubelite.util;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hhst.youtubelite.Constant;

import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public final class UrlUtils {

	public static final String PAGE_UNKNOWN = "unknown";
	public static final String PAGE_CHANNEL = "channel";
	public static final String PAGE_GAMING = "gaming";
	public static final String PAGE_HISTORY = "history";
	public static final String PAGE_CHANNELS = "channels";
	public static final String PAGE_PLAYLISTS = "playlists";
	public static final String PAGE_SELECT_SITE = "select_site";
	public static final String PAGE_USER_MENTION = "@";
	public static final String PAGE_SEARCHING = "searching";

	private static final Locale NORMALIZATION_LOCALE = Locale.ROOT;

	private static final Set<String> ALLOWED_DOMAINS = Set.of(
					Constant.YOUTUBE_DOMAIN,
					"youtu.be",
					"youtube.googleapis.com",
					"googlevideo.com",
					"ytimg.com",
					"accounts.google",
					"accounts.google.com",
					"google.com",
					"googleusercontent.com",
					"gstatic.com",
					"googleapis.com",
					"ggpht.com",
					"yt.be",
					"google.ad",
					"doubleclick.net"
	);

	public static boolean isAllowedDomain(@Nullable final Uri uri) {
		if (uri == null) return false;
		return isAllowedHost(uri.getHost());
	}

	public static boolean isAllowedUrl(@Nullable final String url) {
		if (url == null || url.isEmpty()) return false;
		try {
			return isAllowedHost(URI.create(url).getHost());
		} catch (final IllegalArgumentException ignored) {
			return false;
		}
	}

	@Nullable
	public static Uri externalUri(@Nullable String url) {
		if (url == null || url.isBlank()) return null;
		try {
			return externalUri(Uri.parse(url));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	public static Uri externalUri(@Nullable Uri uri) {
		if (uri == null) return null;
		String host = uri.getHost();
		if (host == null) return null;
		String lowerHost = host.toLowerCase(NORMALIZATION_LOCALE);
		if (!isYoutubeHost(lowerHost) || !"/redirect".equals(uri.getPath())) return null;

		String target = uri.getQueryParameter("q");
		if (target == null || target.isBlank()) {
			target = uri.getQueryParameter("url");
		}
		if (target == null || target.isBlank()) return null;

		Uri targetUri;
		try {
			targetUri = Uri.parse(target);
		} catch (RuntimeException ignored) {
			return null;
		}
		String scheme = targetUri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
		return isAllowedHost(targetUri.getHost()) ? null : targetUri;
	}

	public static boolean isGoogleAccountsUrl(@Nullable final String url) {
		if (url == null || url.isEmpty()) return false;
		try {
			final String host = URI.create(url).getHost();
			return host != null && isGoogleAccountsHost(host.toLowerCase(NORMALIZATION_LOCALE));
		} catch (final IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean isAllowedHost(@Nullable final String host) {
		if (host == null) return false;
		final String lowerHost = host.toLowerCase(NORMALIZATION_LOCALE);
		if (isGoogleAccountsHost(lowerHost)) return true;
		return ALLOWED_DOMAINS.stream().anyMatch(domain ->
						lowerHost.equals(domain) || lowerHost.endsWith("." + domain));
	}

	private static boolean isYoutubeHost(@NonNull String lowerHost) {
		return lowerHost.equals(Constant.YOUTUBE_DOMAIN)
						|| lowerHost.endsWith("." + Constant.YOUTUBE_DOMAIN);
	}

	private static boolean isGoogleAccountsHost(@NonNull final String lowerHost) {
		return lowerHost.equals("accounts.google")
						|| lowerHost.equals("accounts.google.com")
						|| lowerHost.startsWith("accounts.google.")
						|| lowerHost.equals("accounts.youtube.com")
						|| lowerHost.contains("myaccount.google");
	}

	public static boolean isPlaylistFirstItemUrl(@Nullable String url) {
		if (url == null) return false;
		try {
			URI uri = URI.create(url);
			String query = uri.getRawQuery();
			if (query == null) return false;
			return query.contains("index=1&") || query.endsWith("index=1");
		} catch (Exception e) {
			return false;
		}
	}

	@NonNull
	public static String getPageClass(@Nullable final String url) {
		if (url == null || url.isEmpty()) return PAGE_UNKNOWN;

		try {
			final URI uri = URI.create(url);
			final String host = uri.getHost();
			if (host == null) return PAGE_UNKNOWN;
			final String path = uri.getPath();
			final List<String> segments = path == null || path.isEmpty()
							? List.of()
							: java.util.Arrays.stream(path.split("/"))
											.filter(segment -> !segment.isEmpty())
											.toList();
			return resolvePageClass(host, segments);
		} catch (final IllegalArgumentException ignored) {
			return PAGE_UNKNOWN;
		}
	}

	@NonNull
	static String resolvePageClass(@NonNull final String host, @NonNull final List<String> segments) {
		final String lowerHost = host.toLowerCase(NORMALIZATION_LOCALE);
		if (lowerHost.equals("youtu.be")) {
			return segments.isEmpty() ? PAGE_UNKNOWN : Constant.PAGE_WATCH;
		}
		if (!lowerHost.endsWith(Constant.YOUTUBE_DOMAIN))
			return PAGE_UNKNOWN;

		if (segments.isEmpty()) return Constant.PAGE_HOME;

		final String s0 = segments.get(0).toLowerCase(NORMALIZATION_LOCALE);
		if (s0.startsWith("@")) return PAGE_USER_MENTION;

		return switch (s0) {
			case "shorts" -> Constant.PAGE_SHORTS;
			case "watch" -> Constant.PAGE_WATCH;
			case "channel" -> PAGE_CHANNEL;
			case "gaming" -> PAGE_GAMING;
			case "select_site" -> PAGE_SELECT_SITE;
			case "results" -> PAGE_SEARCHING;
			case "feed" -> (segments.size() > 1) ? switch (segments.get(1).toLowerCase(NORMALIZATION_LOCALE)) {
				case "subscriptions" -> Constant.PAGE_SUBSCRIPTIONS;
				case "library" -> Constant.PAGE_LIBRARY;
				case "history" -> PAGE_HISTORY;
				case "channels" -> PAGE_CHANNELS;
				case "playlists" -> PAGE_PLAYLISTS;
				default -> String.join("/", segments);
			} : String.join("/", segments);
			default -> String.join("/", segments);
		};
	}

	@NonNull
	public static String appendLanguage(@NonNull String url) {
		if (!url.contains("youtube.com") && !url.contains("youtu.be")) return url;
		try {
			Uri uri = Uri.parse(url);
			Uri.Builder builder = uri.buildUpon();
			boolean changed = false;
			Locale locale = Locale.getDefault();
			String hl = locale.getLanguage();
			String gl = locale.getCountry();

			if (uri.getQueryParameter("hl") == null) {
				builder.appendQueryParameter("hl", hl);
				changed = true;
			}
			if (uri.getQueryParameter("gl") == null && !gl.isEmpty()) {
				builder.appendQueryParameter("gl", gl);
				changed = true;
			}
			
			if (uri.getQueryParameter("persist_hl") == null) {
				builder.appendQueryParameter("persist_hl", "1");
				changed = true;
			}
			if (uri.getQueryParameter("persist_gl") == null) {
				builder.appendQueryParameter("persist_gl", "1");
				changed = true;
			}
			return changed ? builder.build().toString() : url;
		} catch (Exception e) {
			return url;
		}
	}

	public static void setYoutubePreferences(@NonNull Context context) {
		CookieManager cookieManager = CookieManager.getInstance();
		Locale locale = Locale.getDefault();
		String lang = locale.getLanguage();
		String country = locale.getCountry();
		boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
		
		String prefValue = "hl=" + lang + (country.isEmpty() ? "" : "&gl=" + country) + (isDark ? "&f6=400" : "&f6=10000");
		
		String[] urls = {"https://www.youtube.com", "https://m.youtube.com", "https://youtube.com"};
		for (String url : urls) {
			cookieManager.setCookie(url, "PREF=" + prefValue + "; Domain=.youtube.com; Path=/; Secure; SameSite=None");
		}
		cookieManager.flush();
	}

    @NonNull
    public static String fetchLocalTitle(@NonNull Context context, @NonNull Uri uri, boolean keepExtension) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }

        if (name == null || name.isEmpty()) {
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(context, uri);
                name = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            } catch (Exception ignored) {}
        }

        if (name != null) {
            if (keepExtension) return name;
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                return name.substring(0, dot);
            }
            return name;
        }
        return "Video";
    }
}
