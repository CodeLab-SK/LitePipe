package com.hhst.youtubelite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.util.UnstableApi;

import com.hhst.youtubelite.player.queue.QueueNav;
import com.hhst.youtubelite.player.engine.Engine;
import com.hhst.youtubelite.ui.MainActivity;

import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
@UnstableApi
public class PlaybackService extends Service {
	private static final String TAG = "PlaybackService";
	private static final String CHANNEL_ID = "player_channel";
	private static final int NOTIFICATION_ID = 100;
	private static final int CONNECT_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 10000;
	private static final int SEEK_RESET_DELAY = 1000;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	@NonNull
	private QueueNav queueNavigationAvailability = QueueNav.INACTIVE;
	private MediaSessionCompat mediaSession;
	private NotificationManager notificationManager;
	private boolean isSeeking = false;
	private final Runnable resetSeekFlagRunnable = () -> isSeeking = false;
	private boolean lastIsPlayingState = false;
	private volatile boolean destroyed = false;

	@Nullable
	@Override
	public IBinder onBind(@NonNull final Intent intent) {
		return new PlaybackBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		destroyed = false;
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		
		final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Player Controls", NotificationManager.IMPORTANCE_LOW);
		channel.setDescription("Media playback controls");
		channel.setShowBadge(false);
		channel.setSound(null, null);
		channel.enableLights(false);
		channel.enableVibration(false);
		channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		if (notificationManager != null) notificationManager.createNotificationChannel(channel);

		mediaSession = new MediaSessionCompat(this, TAG);
		mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
				MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

		final PlaybackStateCompat initialState = buildPlaybackState(
				PlaybackStateCompat.STATE_NONE,
				0L,
				1.0f,
				queueNavigationAvailability);
		mediaSession.setPlaybackState(initialState);
	}

	@Override
	public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
		MediaSessionCompat session = mediaSession;
		if (intent != null && session != null) MediaButtonReceiver.handleIntent(session, intent);
		return super.onStartCommand(intent, flags, startId);
	}

	public void initialize(@NonNull final Engine engine) {
		MediaSessionCompat session = mediaSession;
		if (shouldAbort() || session == null) return;
		session.setCallback(new MediaSessionCompat.Callback() {
			@Override
			public void onPlay() {
				engine.play();
			}

			@Override
			public void onPause() {
				engine.pause();
			}

			@Override
			public void onSkipToNext() {
				engine.skipToNext();
			}

			@Override
			public void onSkipToPrevious() {
				engine.skipToPrevious();
			}

			@Override
			public void onSeekTo(final long pos) {
				isSeeking = true;
				handler.removeCallbacks(resetSeekFlagRunnable);
				handler.postDelayed(resetSeekFlagRunnable, SEEK_RESET_DELAY);
				engine.seekTo(pos);
			}
		});
		session.setActive(true);
	}

	@Nullable
	private Bitmap fetchThumbnail(@Nullable final String urlStr) {
		if (urlStr == null || urlStr.isEmpty() || shouldAbort()) return null;
		Bitmap bitmap = null;
		HttpURLConnection conn = null;
		try {
			final URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(CONNECT_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			conn.connect();
			if (shouldAbort()) return null;
			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				try (final InputStream inputStream = conn.getInputStream()) {
					if (shouldAbort()) return null;
					final Bitmap original = BitmapFactory.decodeStream(inputStream);
					if (original != null) {
						final int size = Math.min(original.getWidth(), original.getHeight());
						bitmap = Bitmap.createBitmap(original, (original.getWidth() - size) / 2, (original.getHeight() - size) / 2, size, size);
						if (bitmap != original) original.recycle();
					}
				}
			}
		} catch (InterruptedIOException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			Log.e(TAG, "fetchThumbnail error: " + e.getMessage());
		} finally {
			if (conn != null) conn.disconnect();
		}
		return bitmap;
	}

	@Nullable
	private Notification buildNotification(final boolean isPlaying) {
		MediaSessionCompat session = mediaSession;
		if (shouldAbort() || session == null) return null;
		final MediaMetadataCompat metadata = session.getController().getMetadata();
		if (metadata == null) return null;
		final String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
		final String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
		final Bitmap largeIcon = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
		final int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
		final String playPauseTitle = isPlaying ? getString(R.string.action_pause) : getString(R.string.action_play);
		final PendingIntent playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE);
		final PendingIntent prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
		final PendingIntent nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
		Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
		if (launchIntent == null) launchIntent = new Intent(this, MainActivity.class);
		launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		final PendingIntent contentIntent = PendingIntent.getActivity(this, 101, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_launcher_monochrome)
				.setContentTitle(title)
				.setContentText(artist)
				.setLargeIcon(largeIcon)
				.setContentIntent(contentIntent)
				.setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setOngoing(isPlaying)
				.setSilent(true)
				.setGroup("playback_notification")
				.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);

		final MediaStyle style = new MediaStyle().setMediaSession(session.getSessionToken());
		final boolean includePrevious = shouldIncludePreviousAction(queueNavigationAvailability);
		final boolean includeNext = shouldIncludeNextAction(queueNavigationAvailability);

		if (includePrevious && includeNext) {
			builder.addAction(R.drawable.ic_previous, getString(R.string.action_previous), prevIntent);
			builder.addAction(playPauseIcon, playPauseTitle, playPauseIntent);
			builder.addAction(R.drawable.ic_next, getString(R.string.action_next), nextIntent);
			style.setShowActionsInCompactView(0, 1, 2);
		} else if (includePrevious) {
			builder.addAction(R.drawable.ic_previous, getString(R.string.action_previous), prevIntent);
			builder.addAction(playPauseIcon, playPauseTitle, playPauseIntent);
			style.setShowActionsInCompactView(0, 1);
		} else if (includeNext) {
			builder.addAction(playPauseIcon, playPauseTitle, playPauseIntent);
			builder.addAction(R.drawable.ic_next, getString(R.string.action_next), nextIntent);
			style.setShowActionsInCompactView(0, 1);
		} else {
			builder.addAction(playPauseIcon, playPauseTitle, playPauseIntent);
			style.setShowActionsInCompactView(0);
		}
		return builder.setStyle(style).build();
	}

	public void showNotification(@Nullable final String title, @Nullable final String author, @Nullable final String thumbnail, final long duration) {
		if (shouldAbort()) return;
		try {
			executorService.execute(() -> {
				if (shouldAbort()) return;
				final Bitmap largeIcon = fetchThumbnail(thumbnail);
				if (shouldAbort()) return;
				MediaSessionCompat session = mediaSession;
				if (session == null) return;
				final MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
						.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
						.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, author)
						.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeIcon)
						.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
						.build();
				if (shouldAbort()) return;
				session.setMetadata(metadata);
				final PlaybackStateCompat initialState = buildPlaybackState(
						PlaybackStateCompat.STATE_PAUSED,
						0L,
						1.0f,
						queueNavigationAvailability);
				if (shouldAbort()) return;
				session.setPlaybackState(initialState);
				final Notification notification = buildNotification(false);
				if (notification != null && !shouldAbort()) {
					try {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
							startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
						} else {
							startForeground(NOTIFICATION_ID, notification);
						}
					} catch (Exception e) {
						Log.e(TAG, "startForeground failed: " + e.getMessage());
					}
				}
			});
		} catch (RejectedExecutionException ignored) {
		}
	}

	public void hideNotification() {
		stopForeground(STOP_FOREGROUND_REMOVE);
		if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
	}

	public void updateProgress(final long pos, final float speed, final boolean isPlaying) {
		if (isSeeking) return;
		MediaSessionCompat session = mediaSession;
		NotificationManager manager = notificationManager;
		if (shouldAbort() || session == null) return;
		final int stateCompat = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
		final PlaybackStateCompat playbackState = buildPlaybackState(stateCompat, pos, speed, queueNavigationAvailability);
		if (shouldAbort()) return;
		session.setPlaybackState(playbackState);
		if (isPlaying != lastIsPlayingState) {
			final Notification updatedNotification = buildNotification(isPlaying);
			if (updatedNotification != null && manager != null && !shouldAbort())
				manager.notify(NOTIFICATION_ID, updatedNotification);
		}
		lastIsPlayingState = isPlaying;
	}

	public void updateQueueNavigationAvailability(@NonNull final QueueNav availability) {
		queueNavigationAvailability = availability;
		MediaSessionCompat session = mediaSession;
		NotificationManager manager = notificationManager;
		if (shouldAbort() || session == null) return;
		final PlaybackStateCompat currentState = session.getController().getPlaybackState();
		final int state = currentState != null ? currentState.getState() : PlaybackStateCompat.STATE_NONE;
		final long position = currentState != null ? currentState.getPosition() : 0L;
		final float speed = currentState != null ? currentState.getPlaybackSpeed() : 1.0f;
		if (shouldAbort()) return;
		session.setPlaybackState(buildPlaybackState(state, position, speed, queueNavigationAvailability));
		final Notification updatedNotification = buildNotification(state == PlaybackStateCompat.STATE_PLAYING);
		if (updatedNotification != null && manager != null && !shouldAbort()) {
			manager.notify(NOTIFICATION_ID, updatedNotification);
		}
	}

	static long playbackActionsFor(@NonNull final QueueNav availability) {
		long actions = PlaybackStateCompat.ACTION_PLAY
				| PlaybackStateCompat.ACTION_PAUSE
				| PlaybackStateCompat.ACTION_PLAY_PAUSE
				| PlaybackStateCompat.ACTION_SEEK_TO
				| PlaybackStateCompat.ACTION_STOP;
		if (shouldIncludeNextAction(availability)) {
			actions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
		}
		if (shouldIncludePreviousAction(availability)) {
			actions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
		}
		return actions;
	}

	static boolean shouldIncludePreviousAction(@NonNull final QueueNav availability) {
		return availability.isPreviousActionEnabled();
	}

	static boolean shouldIncludeNextAction(@NonNull final QueueNav availability) {
		return availability.isNextActionEnabled();
	}

	@NonNull
	private static PlaybackStateCompat buildPlaybackState(final int state,
	                                                      final long position,
	                                                      final float speed,
	                                                      @NonNull final QueueNav availability) {
		return new PlaybackStateCompat.Builder()
				.setActions(playbackActionsFor(availability))
				.setState(state, position, speed)
				.build();
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		stopForeground(STOP_FOREGROUND_REMOVE);
		stopSelf();
	}

	@Override
	public void onDestroy() {
		destroyed = true;
		handler.removeCallbacksAndMessages(null);
		executorService.shutdownNow();
		stopForeground(STOP_FOREGROUND_REMOVE);
		MediaSessionCompat session = mediaSession;
		mediaSession = null;
		if (session != null) {
			session.setActive(false);
			session.release();
		}
		NotificationManager manager = notificationManager;
		notificationManager = null;
		if (manager != null) {
			manager.cancel(NOTIFICATION_ID);
		}
		super.onDestroy();
	}

	private boolean shouldAbort() {
		return destroyed || Thread.currentThread().isInterrupted();
	}

	public class PlaybackBinder extends Binder {
		public PlaybackService getService() {
			return PlaybackService.this;
		}
	}
}
