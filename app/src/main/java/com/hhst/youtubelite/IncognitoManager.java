package com.hhst.youtubelite;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import com.tencent.mmkv.MMKV;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

public class IncognitoManager {
    private static IncognitoManager instance;
    @Getter
    private boolean isIncognito;
    private final List<Listener> listeners = new ArrayList<>();
    private final MMKV kv = MMKV.defaultMMKV();

    private static final String[] COOKIE_URLS = {
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://youtube.com",
            "https://www.google.com",
            "https://accounts.google.com",
            "https://myaccount.google.com",
            "https://google.com",
            "https://www.google.co.uk",
            "https://google.co.uk",
            "https://www.google.co.in",
            "https://google.co.in",
            "https://consent.youtube.com",
            "https://consent.google.com",
            "https://accounts.youtube.com"
    };

    private static final String[] PRIMARY_DOMAINS = {
            ".youtube.com",
            ".google.com",
            ".google.co.uk",
            ".google.co.in"
    };

    public interface Listener {
        void onIncognitoChanged(boolean isIncognito);
    }

    private IncognitoManager() {
        this.isIncognito = false;
    }

    public static synchronized IncognitoManager getInstance() {
        if (instance == null) {
            instance = new IncognitoManager();
        }
        return instance;
    }

    public void resetOnStart(Runnable onComplete) {
        boolean wasIncognito = kv.decodeBool("was_incognito", false);
        this.isIncognito = false;
        kv.encode("was_incognito", false);
        
        if (wasIncognito) {
            CookieManager.getInstance().removeAllCookies(result -> {
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
                restoreCookies(() -> {
                    notifyListeners();
                    if (onComplete != null) onComplete.run();
                });
            });
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    public void toggle(Runnable onComplete) {
        setIncognito(!isIncognito, onComplete);
    }

    public void setIncognito(boolean incognito, Runnable onComplete) {
        if (this.isIncognito == incognito) {
            if (onComplete != null) onComplete.run();
            return;
        }

        if (incognito) {
            saveCookies();
            this.isIncognito = true;
            kv.encode("was_incognito", true);
            
            CookieManager.getInstance().removeAllCookies(result -> {
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
                notifyListeners();
                if (onComplete != null) onComplete.run();
            });
        } else {
            this.isIncognito = false;
            kv.encode("was_incognito", false);
            
            CookieManager.getInstance().removeAllCookies(result -> {
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
                restoreCookies(() -> {
                    notifyListeners();
                    if (onComplete != null) onComplete.run();
                });
            });
        }
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onIncognitoChanged(isIncognito);
        }
    }

    private void saveCookies() {
        if (isIncognito) return;
        
        CookieManager cookieManager = CookieManager.getInstance();
        String[] allKeys = kv.allKeys();
        if (allKeys != null) {
            for (String key : allKeys) {
                if (key.startsWith("pers_cookies_v3_")) {
                    kv.removeValueForKey(key);
                }
            }
        }
        for (String url : COOKIE_URLS) {
            try {
                String cookie = cookieManager.getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    kv.encode("pers_cookies_v3_" + url, cookie);
                }
            } catch (Exception ignored) {}
        }
    }

    private void restoreCookies(Runnable onDone) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        
        Set<String> processed = new HashSet<>();
        List<CookieEntry> toSet = new ArrayList<>();

        for (String url : COOKIE_URLS) {
            String cookieStr = kv.decodeString("pers_cookies_v3_" + url);
            if (cookieStr == null || cookieStr.isEmpty()) {
                cookieStr = kv.decodeString("persistent_cookies_" + url);
                if (cookieStr == null || cookieStr.isEmpty()) continue;
            }

            String host = null;
            try {
                host = Uri.parse(url).getHost();
            } catch (Exception ignored) {}
            if (host == null) continue;

            String[] pairs = cookieStr.split(";");
            for (String pair : pairs) {
                String p = pair.trim();
                if (p.isEmpty()) continue;
                
                int eqIndex = p.indexOf('=');
                String name = eqIndex > 0 ? p.substring(0, eqIndex) : p;
                
                String domainToUse = null;
                if (!name.startsWith("__Host-")) {
                    for (String d : PRIMARY_DOMAINS) {
                        if (host.endsWith(d) || host.equals(d.substring(1))) {
                            domainToUse = d;
                            break;
                        }
                    }
                }

                String cookieValue = p + "; Path=/; Secure; SameSite=None";
                String targetUrl = url;

                if (domainToUse != null) {
                    cookieValue += "; Domain=" + domainToUse;
                    targetUrl = "https://" + domainToUse.replaceFirst("^\\.", "");
                }

                String scope = (domainToUse != null ? domainToUse : host);
                if (processed.add(name + "|" + scope)) {
                    toSet.add(new CookieEntry(targetUrl, cookieValue));
                }
            }
        }

        if (toSet.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        final int total = toSet.size();
        final AtomicInteger count = new AtomicInteger(0);

        for (CookieEntry entry : toSet) {
            cookieManager.setCookie(entry.url(), entry.value(), result -> {
                if (count.incrementAndGet() == total) {
                    cookieManager.flush();
                    new Handler(Looper.getMainLooper()).postDelayed(onDone, 1500);
                }
            });
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private record CookieEntry(String url, String value) {
    }
}
