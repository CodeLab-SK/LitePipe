package com.codelabsk.litepipe.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

import com.codelabsk.litepipe.PlaybackService;
import com.codelabsk.litepipe.R;
import com.codelabsk.litepipe.downloader.core.history.DownloadHistoryRepository;
import com.codelabsk.litepipe.downloader.core.history.DownloadRecord;
import com.codelabsk.litepipe.downloader.core.history.DownloadStatus;
import com.codelabsk.litepipe.downloader.core.history.DownloadType;
import com.codelabsk.litepipe.player.LitePlayer;
import com.codelabsk.litepipe.player.engine.Engine;
import com.codelabsk.litepipe.player.queue.QueueNav;
import com.codelabsk.litepipe.util.DeviceUtils;
import com.codelabsk.litepipe.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@UnstableApi
public final class OfflinePlayerActivity extends AppCompatActivity {

    @Inject public Lazy<LitePlayer> player;
    @Inject public Lazy<Engine> engine;
    @Inject public DownloadHistoryRepository historyRepository;

    @Nullable private PlaybackService playbackService;

    private final ServiceConnection playbackConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder s) {
            playbackService = ((PlaybackService.PlaybackBinder) s).getService();
            player.get().attachPlaybackService(playbackService);
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            playbackService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_offline_player);
        super.onCreate(null);

        bindService(new Intent(this, PlaybackService.class), playbackConnection, BIND_AUTO_CREATE);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        engine.get().addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (DeviceUtils.isInPictureInPictureMode(OfflinePlayerActivity.this)) {
                    updatePictureInPictureActions();
                }
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (DeviceUtils.isInPictureInPictureMode(OfflinePlayerActivity.this)) {
                    updatePictureInPictureActions();
                }
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        final String action = intent.getAction();

        if ("PLAY_LOCAL_VIDEO".equals(action)) {
            Uri uri = intent.getParcelableExtra("uri");
            String title = intent.getStringExtra("title");
            String videoId = intent.getStringExtra("video_id");
            if (uri != null) {
                if (title == null) title = UrlUtils.fetchLocalTitle(this, uri, false);
                List<DownloadRecord> subs = null;
                if (videoId != null) {
                    subs = new ArrayList<>();
                    for (DownloadRecord r : historyRepository.getAllSorted()) {
                        if (Objects.equals(getShortVideoId(r.getVideoId()), getShortVideoId(videoId)) 
                                && r.getType() == DownloadType.SUBTITLE 
                                && r.getStatus() == DownloadStatus.COMPLETED) {
                            subs.add(r);
                        }
                    }
                }
                play(uri, title, subs);
            }
            return;
        }

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            Uri data = intent.getData();
            String type = intent.getType();
            if (type != null && type.startsWith("video/")) {
                play(data, UrlUtils.fetchLocalTitle(this, data, false), null);
                return;
            }
        }
        
        finish();
    }

    private void play(Uri uri, String title, List<DownloadRecord> subs) {
        player.get().playLocal(uri, title, subs);
        player.get().enterFullscreen();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String label = UrlUtils.fetchLocalTitle(this, uri, true);
                player.get().addLocalSubtitle(uri, label);
            }
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player.get().shouldAutoEnterPictureInPicture()) {
            player.get().enterPictureInPicture();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        player.get().onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            updatePictureInPictureActions();
        }
    }

    private void updatePictureInPictureActions() {
        final List<RemoteAction> actions = new ArrayList<>();
        final QueueNav nav = engine.get().getQueueNavigationAvailability();

        final PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        final Icon prevIcon = Icon.createWithResource(this, R.drawable.ic_previous);
        final RemoteAction prevAction = new RemoteAction(prevIcon, getString(R.string.action_previous), getString(R.string.action_previous), prevIntent);
        prevAction.setEnabled(nav.isPreviousActionEnabled());
        actions.add(prevAction);

        final PendingIntent ppIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE);
        final boolean isPlaying = engine.get().isPlaying();
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        player.get().syncRotation(DeviceUtils.isRotateOn(this), newConfig.orientation);
    }

    @Override
    protected void onDestroy() {
        if (playbackConnection != null) unbindService(playbackConnection);
        player.get().release();
        super.onDestroy();
    }

    private static String getShortVideoId(String videoId) {
        if (videoId == null) return "";
        int idx = videoId.indexOf(':');
        return idx == -1 ? videoId : videoId.substring(0, idx);
    }
}
