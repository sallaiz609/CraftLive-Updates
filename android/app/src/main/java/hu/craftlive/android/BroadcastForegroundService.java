package hu.craftlive.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.gl.render.filters.object.ImageObjectFilterRender;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.NoVideoSource;
import com.pedro.encoder.input.sources.video.ScreenSource;
import com.pedro.encoder.utils.gl.TranslateTo;
import com.pedro.library.generic.GenericStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Publishes screen capture plus a viewer-only interaction layer to an RTMP endpoint. */
public final class BroadcastForegroundService extends Service implements ConnectChecker {
    public static final String ACTION_START = "hu.craftlive.android.broadcast.START";
    public static final String ACTION_STOP = "hu.craftlive.android.broadcast.STOP";
    public static final String ACTION_REFRESH_OVERLAY = "hu.craftlive.android.broadcast.REFRESH_OVERLAY";
    public static final String ACTION_STATUS_CHANGED = "hu.craftlive.android.broadcast.STATUS";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_ENDPOINT = "endpoint";

    private static final String CHANNEL_ID = "craftlive_broadcast";
    private static final int NOTIFICATION_ID = 2040;
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_BITRATE = 3_500_000;
    private static volatile boolean broadcasting;

    private final ExecutorService overlayExecutor = Executors.newSingleThreadExecutor();
    private GenericStream stream;
    private MediaProjection projection;
    private ImageObjectFilterRender overlayFilter;
    private InteractionStore store;
    private boolean prepared;
    private boolean shuttingDown;

    public static boolean isBroadcasting() {
        return broadcasting;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new InteractionStore(this);
        createNotificationChannel();
        stream = new GenericStream(getApplicationContext(), this,
                new NoVideoSource(), new MicrophoneSource());
        stream.getGlInterface().setForceRender(true, 15);
        try {
            prepared = stream.prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE,
                    30, 2, 0)
                    && stream.prepareAudio(44_100, true, 128_000, true, true);
        } catch (RuntimeException error) {
            prepared = false;
            writeStatus("error", getString(R.string.broadcast_prepare_failed));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown("idle", "");
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH_OVERLAY.equals(action)) {
            if (broadcasting) refreshOverlay(true);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, notification(getString(R.string.broadcast_preparing)));
        if (broadcasting || (stream != null && stream.isStreaming())) return START_NOT_STICKY;
        if (!prepared) {
            writeStatus("error", getString(R.string.broadcast_prepare_failed));
            shutdownKeepingStatus();
            return START_NOT_STICKY;
        }

        String endpoint = intent.getStringExtra(EXTRA_ENDPOINT);
        intent.removeExtra(EXTRA_ENDPOINT);
        Intent resultData = readIntentExtra(intent, EXTRA_RESULT_DATA);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        if (endpoint == null || endpoint.trim().isEmpty() || resultData == null) {
            writeStatus("error", getString(R.string.broadcast_capture_missing));
            shutdownKeepingStatus();
            return START_NOT_STICKY;
        }

        writeStatus("preparing", "");
        try {
            MediaProjectionManager manager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException(
                    getString(R.string.broadcast_capture_missing));
            MediaProjection.Callback callback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    shutdown("idle", "");
                }
            };
            stream.changeVideoSource(new ScreenSource(getApplicationContext(), projection, callback));
            applyOverlay(StreamInteractionOverlay.render(
                    this, store, VIDEO_WIDTH, VIDEO_HEIGHT));
            stream.startStream(endpoint.trim());
            // The endpoint can contain the private stream key. Never persist or log it.
            endpoint = null;
            refreshOverlay(true);
        } catch (RuntimeException error) {
            // An encoder error can include its endpoint. Never persist a message that could
            // accidentally contain the private stream key.
            writeStatus("error", getString(R.string.broadcast_prepare_failed));
            shutdownKeepingStatus();
        }
        return START_NOT_STICKY;
    }

    private void refreshOverlay(boolean preload) {
        overlayExecutor.execute(() -> {
            if (preload) StreamInteractionOverlay.preloadGiftArtwork(this, store);
            Bitmap bitmap = StreamInteractionOverlay.render(
                    this, store, VIDEO_WIDTH, VIDEO_HEIGHT);
            if (!shuttingDown && stream != null) applyOverlay(bitmap);
            else bitmap.recycle();
        });
    }

    private synchronized void applyOverlay(Bitmap bitmap) {
        if (stream == null || shuttingDown) {
            bitmap.recycle();
            return;
        }
        ImageObjectFilterRender filter = new ImageObjectFilterRender();
        stream.getGlInterface().setFilter(filter);
        filter.setImage(bitmap);
        filter.setScale(100f, 100f);
        filter.setPosition(TranslateTo.CENTER);
        overlayFilter = filter;
    }

    @Override
    public void onConnectionStarted(String url) {
        writeStatus("connecting", "");
        updateNotification(getString(R.string.broadcast_connecting));
    }

    @Override
    public void onConnectionSuccess() {
        broadcasting = true;
        writeStatus("active", "");
        updateNotification(getString(R.string.broadcast_notification_active));
    }

    @Override
    public void onNewBitrate(long bitrate) {
        // Intentionally no URL/key or noisy bitrate logging.
    }

    @Override
    public void onConnectionFailed(String reason) {
        broadcasting = false;
        // RootEncoder may include the endpoint in a failure reason. Keep it out of prefs/UI.
        writeStatus("error", getString(R.string.broadcast_disconnected));
        shutdownKeepingStatus();
    }

    @Override
    public void onDisconnect() {
        if (!shuttingDown) {
            broadcasting = false;
            writeStatus("error", getString(R.string.broadcast_disconnected));
            shutdownKeepingStatus();
        }
    }

    @Override
    public void onAuthError() {
        broadcasting = false;
        writeStatus("error", getString(R.string.broadcast_auth_error));
        shutdownKeepingStatus();
    }

    @Override
    public void onAuthSuccess() {
        // Connection success supplies the user-facing state.
    }

    private synchronized void shutdown(String finalState, String detail) {
        if (shuttingDown) return;
        shuttingDown = true;
        broadcasting = false;
        try {
            if (stream != null && stream.isStreaming()) stream.stopStream();
        } catch (RuntimeException ignored) {
        }
        try {
            if (stream != null) stream.release();
        } catch (RuntimeException ignored) {
        }
        MediaProjection activeProjection = projection;
        projection = null;
        if (activeProjection != null) {
            try {
                activeProjection.stop();
            } catch (RuntimeException ignored) {
            }
        }
        writeStatus(finalState, detail);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void shutdownKeepingStatus() {
        String state = store.preferences().getString("broadcast_status", "error");
        String detail = store.preferences().getString("broadcast_status_detail", "");
        shutdown(state, detail);
    }

    @Override
    public void onDestroy() {
        if (!shuttingDown) shutdown("idle", "");
        overlayExecutor.shutdownNow();
        overlayFilter = null;
        super.onDestroy();
    }

    private void writeStatus(String state, String detail) {
        store.preferences().edit()
                .putString("broadcast_status", state)
                .putString("broadcast_status_detail", detail == null ? "" : detail)
                .apply();
        sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(getPackageName()));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.broadcast_notification_channel),
                NotificationManager.IMPORTANCE_LOW));
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_craftlive)
                .setContentTitle(getString(R.string.broadcast_notification_title))
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    @SuppressWarnings("deprecation")
    private static Intent readIntentExtra(Intent source, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return source.getParcelableExtra(key, Intent.class);
        }
        return source.getParcelableExtra(key);
    }

}
