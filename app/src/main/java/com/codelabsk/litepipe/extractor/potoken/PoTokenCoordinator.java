package com.codelabsk.litepipe.extractor.potoken;

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.codelabsk.litepipe.extractor.AuthContext;
import com.codelabsk.litepipe.extractor.ExtractionSession;
import com.codelabsk.litepipe.extractor.ExtractionSessionScope;
import com.tencent.mmkv.MMKV;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Singleton
public final class PoTokenCoordinator {
    private static final String REQUEST_KEY = "O43z0dpjhgX20SCx4KAo";
    private final Gson gson;
    private final PoTokenBridge poTokenBridge;
    private final PoTokenHost poTokenHost;
    private final ExtractionSessionScope scope;
    private final OkHttpClient okHttpClient;
    private final MMKV kv;
    private final Object lock = new Object();
    private final AtomicLong requestCounter = new AtomicLong();
    private PoTokenSession session;

    @Inject
    public PoTokenCoordinator(Gson gson, PoTokenBridge poTokenBridge, PoTokenHost poTokenHost, 
                              ExtractionSessionScope scope, OkHttpClient okHttpClient, MMKV kv) {
        this.gson = gson;
        this.poTokenBridge = poTokenBridge;
        this.poTokenHost = poTokenHost;
        this.scope = scope;
        this.okHttpClient = okHttpClient;
        this.kv = kv;
    }

    @Nullable
    public PoTokenResult getWebClientPoToken(@NonNull String videoId) {
        if (Looper.myLooper() == Looper.getMainLooper()) return null;
        if (!poTokenHost.awaitReady(4000L)) return null;
        String visitorData = fetchVisitorData();
        if (visitorData == null) return null;

        PoTokenSession currentSession;
        long hostGen;
        synchronized (lock) {
            hostGen = poTokenHost.getGeneration();
            if (session == null || !session.matches(hostGen) || session.isExpired(System.currentTimeMillis())) {
                session = initializeSession(hostGen);
            }
            currentSession = session;
        }
        
        if (currentSession == null) return null;
        String playerPoToken = mintPoToken(hostGen, videoId);
        if (playerPoToken == null) return null;
        
        return new PoTokenResult(visitorData, playerPoToken, playerPoToken);
    }

    @Nullable
    public PoTokenResult getAndroidClientPoToken(@NonNull String videoId) {
        return null;
    }

    @Nullable
    public PoTokenResult getIosClientPoToken(@NonNull String videoId) {
        return null;
    }

    @Nullable
    private String fetchVisitorData() {
        ExtractionSession currentSession = scope.get();
        AuthContext auth = currentSession != null ? currentSession.getAuth() : null;
        if (auth != null && auth.visitorData() != null) return auth.visitorData();
        
        String visitorData = kv.decodeString("visitor_data");
        if (visitorData != null) return visitorData;

        try {
            visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo.ofWebClient(),
                org.schabi.newpipe.extractor.localization.Localization.DEFAULT,
                org.schabi.newpipe.extractor.localization.ContentCountry.DEFAULT,
                YoutubeParsingHelper.getYouTubeHeaders(),
                YoutubeParsingHelper.YOUTUBEI_V1_URL,
                null, false);
            
            if (visitorData != null) {
                kv.encode("visitor_data", visitorData);
            }
            return visitorData;
        } catch (Exception e) { return null; }
    }

    private PoTokenSession initializeSession(long hostGeneration) {
        String result = poTokenHost.evaluateJavascript(hostGeneration, "window.__litePoToken ? 'ok' : 'missing'", 2000);
        if (result == null || !result.contains("ok")) return null;

        String createResponse = makeBotguardRequest("https://www.youtube.com/api/jnn/v1/Create", "[\"" + REQUEST_KEY + "\"]");
        if (createResponse == null) return null;

        String requestId = "init-" + requestCounter.incrementAndGet();
        CompletableFuture<String> future = poTokenBridge.prepare(requestId);
        poTokenHost.evaluateJavascript(hostGeneration, "window.__litePoToken.runInit(" + gson.toJson(createResponse) + ",\"" + requestId + "\")", 1000);
        
        try {
            String botguardResponse = future.get(4000, TimeUnit.MILLISECONDS);
            String generateItResponse = makeBotguardRequest("https://www.youtube.com/api/jnn/v1/GenerateIT", "[\"" + REQUEST_KEY + "\",\"" + botguardResponse + "\"]");
            if (generateItResponse == null) return null;
            JsonArray array = JsonParser.parseString(generateItResponse).getAsJsonArray();
            return new PoTokenSession(hostGeneration, System.currentTimeMillis() + (array.get(1).getAsLong() * 1000) - 600000);
        } catch (Exception e) { return null; }
    }

    @Nullable
    private String mintPoToken(long hostGeneration, String identifier) {
        String requestId = "mint-" + requestCounter.incrementAndGet();
        CompletableFuture<String> future = poTokenBridge.prepare(requestId);
        poTokenHost.evaluateJavascript(hostGeneration, "window.__litePoToken.mint(\"" + identifier + "\",\"" + requestId + "\")", 1000);
        try { return future.get(2000, TimeUnit.MILLISECONDS); } catch (Exception e) { return null; }
    }

    private String makeBotguardRequest(String url, String body) {
        Request request = new Request.Builder().url(url)
            .post(RequestBody.create(body, MediaType.get("application/json+protobuf")))
            .header("x-goog-api-key", "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw")
            .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            return response.isSuccessful() ? response.body().string() : null;
        } catch (Exception e) { return null; }
    }
}
