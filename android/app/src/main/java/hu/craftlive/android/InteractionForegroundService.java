package hu.craftlive.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InteractionForegroundService extends Service implements
        TikTokConnector.Listener, BedrockWebSocketServer.Listener {
    public static final String ACTION_START_BRIDGE = "hu.craftlive.android.START_BRIDGE";
    public static final String ACTION_START = "hu.craftlive.android.START";
    public static final String ACTION_STOP = "hu.craftlive.android.STOP";
    public static final String ACTION_TEST = "hu.craftlive.android.TEST";
    public static final String ACTION_TOGGLE_LIVE_OVERLAY = "hu.craftlive.android.TOGGLE_LIVE_OVERLAY";
    public static final String ACTION_STATUS_CHANGED = "hu.craftlive.android.STATUS_CHANGED";
    public static final String EXTRA_COMMAND = "command";

    private static final int NOTIFICATION_ID = 5107;
    private static final String CHANNEL_ID = "craftlive_interactions";
    private static final long RECONNECT_DELAY_SECONDS = 10L;
    private static volatile InteractionForegroundService instance;

    private final LinkedBlockingDeque<QueuedCommand> queue = new LinkedBlockingDeque<>();
    private final Map<String, Integer> likeCounters = new HashMap<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private InteractionStore store;
    private GiftCatalogStore giftCatalog;
    private TikTokConnector connector;
    private BedrockWebSocketServer bedrockServer;
    private WindowManager overlayWindowManager;
    private View liveOverlayView;
    private WindowManager.LayoutParams liveOverlayParams;
    private volatile boolean liveConnected;
    private volatile boolean liveRequested;
    private volatile long lastLiveTick;
    private volatile String currentUsername = "";

    public static boolean isRunning() {
        return instance != null;
    }

    public static int queuedCount() {
        InteractionForegroundService service = instance;
        return service == null ? 0 : service.queue.size();
    }

    public static boolean isLiveActive() {
        InteractionForegroundService service = instance;
        return service != null && service.liveConnected;
    }

    public static boolean isLiveMonitoring() {
        InteractionForegroundService service = instance;
        return service != null && service.liveRequested;
    }

    public static boolean isBedrockConnected() {
        InteractionForegroundService service = instance;
        return service != null && service.bedrockServer != null
                && service.bedrockServer.isMinecraftConnected();
    }

    public static boolean isLiveOverlayVisible() {
        InteractionForegroundService service = instance;
        return service != null && service.liveOverlayView != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new InteractionStore(this);
        giftCatalog = new GiftCatalogStore(this);
        liveRequested = store.preferences().getBoolean("live_monitoring_requested", false);
        store.preferences().edit()
                .putString("bedrock_bridge_status", "starting")
                .putString("bedrock_bridge_detail", "")
                .apply();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        bedrockServer = new BedrockWebSocketServer(this);
        bedrockServer.startSafely();
        startWorker();
        ticker.scheduleAtFixedRate(this::tickLiveTime, 1L, 1L, TimeUnit.SECONDS);
        String savedUsername = store.preferences().getString("tiktok_username", "");
        if (liveRequested && savedUsername != null && !savedUsername.trim().isEmpty()) {
            String normalized = savedUsername.trim().replace("@", "");
            ticker.schedule(() -> connect(normalized), 1L, TimeUnit.SECONDS);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            liveRequested = false;
            store.preferences().edit().putBoolean("live_monitoring_requested", false).apply();
            accumulateLiveTime();
            liveConnected = false;
            currentUsername = "";
            if (connector != null) connector.disconnect();
            connector = null;
            writeStatus("idle", "");
            return START_STICKY;
        }
        if (ACTION_TEST.equals(action)) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            if (command != null && !command.trim().isEmpty()) {
                enqueueRaw(command, "test", "", 1);
            }
            return START_STICKY;
        }
        if (ACTION_TOGGLE_LIVE_OVERLAY.equals(action)) {
            toggleLiveOverlay();
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String username = store.preferences().getString("tiktok_username", "");
            if (username != null && !username.trim().isEmpty()) {
                liveRequested = true;
                store.preferences().edit().putBoolean("live_monitoring_requested", true).apply();
                String normalized = username.trim().replace("@", "");
                if (!normalized.equals(currentUsername) || connector == null || !liveConnected) {
                    connect(normalized);
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        accumulateLiveTime();
        liveRequested = false;
        liveConnected = false;
        if (connector != null) connector.disconnect();
        connector = null;
        if (bedrockServer != null) bedrockServer.stopSafely();
        bedrockServer = null;
        hideLiveOverlay();
        store.preferences().edit()
                .putString("bedrock_bridge_status", "stopped")
                .putString("bedrock_bridge_detail", "")
                .apply();
        workerRunning.set(false);
        ticker.shutdownNow();
        queue.clear();
        instance = null;
        writeStatus("idle", "");
        super.onDestroy();
    }

    @Override
    public void onConnected() {
        markLiveActive();
    }

    @Override
    public void onWaiting() {
        if (!liveRequested) return;
        accumulateLiveTime();
        liveConnected = false;
        writeStatus("waiting", "");
        scheduleReconnect();
    }

    @Override
    public void onLiveEnded() {
        if (!liveRequested) return;
        accumulateLiveTime();
        liveConnected = false;
        writeStatus("waiting", "live_ended");
        scheduleReconnect();
    }

    @Override
    public void onGiftCatalog(List<GiftCatalogItem> gifts) {
        if (giftCatalog != null) giftCatalog.merge(gifts);
        sendStatusBroadcast();
    }

    @Override
    public void onEvent(InteractionSlot.TriggerType type, String key, int amount, String user) {
        if (!liveRequested) return;
        markLiveActive();
        store.preferences().edit()
                .putString("last_tiktok_event_type", type.name())
                .putString("last_tiktok_event_key", key == null ? "" : key)
                .putString("last_tiktok_event_user", user == null ? "" : user)
                .putInt("last_tiktok_event_amount", Math.max(1, amount))
                .putLong("last_tiktok_event_time", System.currentTimeMillis())
                .apply();
        if (type == InteractionSlot.TriggerType.LIKE) {
            handleLikeEvent(Math.max(1, amount), user);
            return;
        }
        List<InteractionSlot> matches = store.findMatches(type, key, amount);
        for (InteractionSlot slot : matches) {
            enqueueRaw(slot.command, key, user, amount);
        }
    }

    private synchronized void handleLikeEvent(int amount, String user) {
        handleLikeSlots(store.loadStandard(), amount, user);
        if (store.isPlusUnlocked()) handleLikeSlots(store.loadPlus(), amount, user);
    }

    private void handleLikeSlots(List<InteractionSlot> slots, int amount, String user) {
        for (InteractionSlot slot : slots) {
            if (!slot.enabled || slot.triggerType != InteractionSlot.TriggerType.LIKE
                    || slot.command.trim().isEmpty()) continue;
            String counterKey = (slot.plus ? "plus:" : "standard:") + slot.index;
            int accumulated = likeCounters.getOrDefault(counterKey, 0) + amount;
            int threshold = Math.max(1, slot.threshold);
            while (accumulated >= threshold) {
                enqueueRaw(slot.command, "like", user, threshold);
                accumulated -= threshold;
            }
            likeCounters.put(counterKey, accumulated);
        }
    }

    @Override
    public void onError(String message) {
        if (!liveRequested) return;
        accumulateLiveTime();
        liveConnected = false;
        writeStatus("waiting", message == null ? "" : message);
        scheduleReconnect();
    }

    @Override
    public void onBedrockListening() {
        writeBedrockStatus("listening", BedrockConnectionAddresses.preferredAddress());
    }

    @Override
    public void onBedrockConnected() {
        writeBedrockStatus("connected", "");
    }

    @Override
    public void onBedrockDisconnected() {
        writeBedrockStatus("listening", BedrockConnectionAddresses.preferredAddress());
    }

    @Override
    public void onBedrockCommandResponse(String requestId, boolean successful, String message) {
        store.preferences().edit()
                .putBoolean("last_bedrock_command_success", successful)
                .putString("last_bedrock_command_response", message == null ? "" : message)
                .putLong("last_bedrock_response_time", System.currentTimeMillis())
                .apply();
        sendStatusBroadcast();
    }

    @Override
    public void onBedrockError(String message) {
        writeBedrockStatus("error", message == null ? "" : message);
    }

    private synchronized void connect(String username) {
        if (!liveRequested || username == null || username.trim().isEmpty()) return;
        reconnectScheduled.set(false);
        if (connector != null) connector.disconnect();
        currentUsername = username;
        writeStatus("starting", "");
        connector = new TikTokConnector(this);
        connector.connect(username);
    }

    private void scheduleReconnect() {
        if (!liveRequested || liveConnected || ticker.isShutdown()
                || !reconnectScheduled.compareAndSet(false, true)) return;
        ticker.schedule(() -> {
            reconnectScheduled.set(false);
            if (!liveRequested || liveConnected) return;
            String username = store.preferences().getString("tiktok_username", currentUsername);
            if (username != null && !username.trim().isEmpty()) {
                connect(username.trim().replace("@", ""));
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void markLiveActive() {
        if (!liveRequested) return;
        boolean becameActive = !liveConnected;
        if (becameActive) {
            liveConnected = true;
            lastLiveTick = System.currentTimeMillis();
            writeStatus("active", "");
            generateLivePoster();
        }
    }

    private void generateLivePoster() {
        try {
            LiveInteractionPoster.Result result = LiveInteractionPoster.generate(
                    this, store, currentUsername);
            store.preferences().edit()
                    .putString("live_poster_path", result.file.getAbsolutePath())
                    .putInt("live_poster_interaction_count", result.interactionCount)
                    .putLong("live_poster_time", System.currentTimeMillis())
                    .remove("live_poster_error")
                    .apply();
        } catch (Exception error) {
            store.preferences().edit()
                    .putString("live_poster_error", error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage())
                    .apply();
        }
        sendStatusBroadcast();
    }

    private void toggleLiveOverlay() {
        if (liveOverlayView == null) showLiveOverlay(); else hideLiveOverlay();
    }

    private void showLiveOverlay() {
        if (!Settings.canDrawOverlays(this) || liveOverlayView != null) return;
        overlayWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (overlayWindowManager == null) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        panel.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(224, 2, 32, 19));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(103, 190, 32));
        panel.setBackground(background);

        TextView header = overlayText(getString(R.string.live_overlay_title), 16f,
                Color.rgb(132, 255, 30), true);
        header.setPadding(0, 0, 0, dp(7));
        panel.addView(header);

        List<InteractionSlot> enabled = store.loadEnabled();
        int visible = Math.min(8, enabled.size());
        for (int index = 0; index < visible; index++) {
            InteractionSlot slot = enabled.get(index);
            String trigger = overlayTrigger(slot);
            String action = slot.name == null || slot.name.trim().isEmpty()
                    ? slot.command : slot.name;
            TextView row = overlayText("• " + trigger + "  →  " + action,
                    13f, Color.WHITE, index < 3);
            row.setPadding(0, dp(3), 0, dp(3));
            panel.addView(row);
        }
        if (enabled.size() > visible) {
            panel.addView(overlayText(getString(R.string.live_overlay_more,
                    enabled.size() - visible), 12f, Color.LTGRAY, false));
        }
        if (enabled.isEmpty()) {
            panel.addView(overlayText(getString(R.string.live_overlay_empty),
                    13f, Color.LTGRAY, false));
        }

        TextView hint = overlayText(getString(R.string.live_overlay_drag_hint),
                11f, Color.LTGRAY, false);
        hint.setPadding(0, dp(7), 0, 0);
        panel.addView(hint);

        int width = Math.min(dp(310), (int) (getResources().getDisplayMetrics().widthPixels * 0.28f));
        liveOverlayParams = new WindowManager.LayoutParams(
                Math.max(dp(220), width), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        liveOverlayParams.gravity = Gravity.TOP | Gravity.START;
        liveOverlayParams.x = Math.max(0, store.preferences().getInt("live_overlay_x", dp(12)));
        liveOverlayParams.y = Math.max(0, store.preferences().getInt("live_overlay_y", dp(80)));
        attachOverlayDrag(header);
        try {
            overlayWindowManager.addView(panel, liveOverlayParams);
            liveOverlayView = panel;
            sendStatusBroadcast();
        } catch (Exception ignored) {
            liveOverlayView = null;
            liveOverlayParams = null;
        }
    }

    private void attachOverlayDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float touchX;
            private float touchY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (liveOverlayParams == null || overlayWindowManager == null) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = liveOverlayParams.x;
                    startY = liveOverlayParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int maxX = Math.max(0, getResources().getDisplayMetrics().widthPixels
                            - liveOverlayParams.width);
                    int maxY = Math.max(0, getResources().getDisplayMetrics().heightPixels - dp(80));
                    liveOverlayParams.x = Math.max(0, Math.min(maxX,
                            startX + Math.round(event.getRawX() - touchX)));
                    liveOverlayParams.y = Math.max(0, Math.min(maxY,
                            startY + Math.round(event.getRawY() - touchY)));
                    overlayWindowManager.updateViewLayout(liveOverlayView, liveOverlayParams);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    store.preferences().edit()
                            .putInt("live_overlay_x", liveOverlayParams.x)
                            .putInt("live_overlay_y", liveOverlayParams.y)
                            .apply();
                    return true;
                }
                return false;
            }
        });
    }

    private void hideLiveOverlay() {
        if (overlayWindowManager != null && liveOverlayView != null) {
            try {
                overlayWindowManager.removeView(liveOverlayView);
            } catch (Exception ignored) {
            }
        }
        liveOverlayView = null;
        liveOverlayParams = null;
        sendStatusBroadcast();
    }

    private TextView overlayText(String value, float size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(null, bold ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        return text;
    }

    private String overlayTrigger(InteractionSlot slot) {
        return switch (slot.triggerType) {
            case GIFT -> slot.triggerKey;
            case LIKE -> Math.max(1, slot.threshold) + " like";
            case FOLLOW -> getString(R.string.trigger_follow);
            case SUBSCRIBE -> getString(R.string.trigger_subscribe);
            case SHARE -> getString(R.string.trigger_share);
            case COMMENT -> slot.triggerKey;
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enqueueRaw(String rawCommand, String key, String user, int amount) {
        Map<String, String> variables = new HashMap<>();
        variables.put("gift", key == null ? "" : key);
        variables.put("user", user == null ? "" : user);
        variables.put("count", String.valueOf(amount));
        boolean diagnostic = "test".equals(key);
        for (String command : BedrockCommandTranslator.translateMany(rawCommand, variables)) {
            queue.offerLast(new QueuedCommand(command, diagnostic));
        }
        updateNotification();
    }

    private void startWorker() {
        if (!workerRunning.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            while (workerRunning.get()) {
                try {
                    QueuedCommand item = queue.takeFirst();
                    BedrockWebSocketServer server = bedrockServer;
                    boolean sent = server != null && server.sendCommand(item.command);
                    if (!sent) {
                        store.preferences().edit()
                                .putString("last_dispatch_error", "bedrock_not_connected")
                                .apply();
                        queue.offerFirst(item);
                        updateNotification();
                        Thread.sleep(1_000L);
                        continue;
                    } else {
                        store.preferences().edit()
                                .putString("last_command", item.command)
                                .putLong("last_command_time", System.currentTimeMillis())
                                .remove("last_dispatch_error")
                                .apply();
                    }
                    updateNotification();
                    Thread.sleep(InteractionStore.FIXED_DELAY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "craftlive-command-queue");
        worker.setDaemon(true);
        worker.start();
    }

    private void tickLiveTime() {
        if (!liveConnected) return;
        long now = System.currentTimeMillis();
        long elapsed = now - lastLiveTick;
        if (elapsed >= 1_000L && elapsed <= 10_000L) {
            store.addVerifiedLiveMillis(elapsed);
            lastLiveTick = now;
            sendStatusBroadcast();
        } else {
            lastLiveTick = now;
        }
    }

    private void accumulateLiveTime() {
        if (!liveConnected || lastLiveTick <= 0L) return;
        long elapsed = System.currentTimeMillis() - lastLiveTick;
        if (elapsed > 0L && elapsed <= 10_000L) store.addVerifiedLiveMillis(elapsed);
        lastLiveTick = System.currentTimeMillis();
    }

    private void writeStatus(String state, String detail) {
        store.preferences().edit()
                .putString("live_status", state)
                .putString("live_status_detail", detail)
                .apply();
        sendStatusBroadcast();
        updateNotification();
    }

    private void writeBedrockStatus(String state, String detail) {
        store.preferences().edit()
                .putString("bedrock_bridge_status", state)
                .putString("bedrock_bridge_detail", detail)
                .apply();
        sendStatusBroadcast();
        updateNotification();
    }

    private void sendStatusBroadcast() {
        Intent intent = new Intent(ACTION_STATUS_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.fixed_delay));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String content;
        if (liveConnected) {
            content = getString(R.string.notification_live_active,
                    store == null ? 0 : store.loadEnabled().size(), queue.size());
        } else if (liveRequested) {
            content = getString(R.string.notification_live_waiting, queue.size());
        } else {
            content = getString(R.string.notification_text, queue.size());
        }
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_craftlive)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private static final class QueuedCommand {
        private final String command;
        private final boolean diagnostic;

        private QueuedCommand(String command, boolean diagnostic) {
            this.command = command;
            this.diagnostic = diagnostic;
        }
    }
}
