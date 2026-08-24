package hu.craftlive.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

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
    public static final String ACTION_STATUS_CHANGED = "hu.craftlive.android.STATUS_CHANGED";
    public static final String EXTRA_COMMAND = "command";

    private static final int NOTIFICATION_ID = 5107;
    private static final String CHANNEL_ID = "craftlive_interactions";
    private static volatile InteractionForegroundService instance;

    private final LinkedBlockingDeque<QueuedCommand> queue = new LinkedBlockingDeque<>();
    private final Map<String, Integer> likeCounters = new HashMap<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private InteractionStore store;
    private TikTokConnector connector;
    private BedrockWebSocketServer bedrockServer;
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
        return service != null && service.connector != null;
    }

    public static boolean isBedrockConnected() {
        InteractionForegroundService service = instance;
        return service != null && service.bedrockServer != null
                && service.bedrockServer.isMinecraftConnected();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new InteractionStore(this);
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            liveRequested = false;
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
        if (ACTION_START.equals(action)) {
            String username = store.preferences().getString("tiktok_username", "");
            if (username != null && !username.trim().isEmpty()
                    && (!username.equals(currentUsername) || connector == null)) {
                liveRequested = true;
                connect(username.trim().replace("@", ""));
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
        if (!liveRequested) return;
        lastLiveTick = System.currentTimeMillis();
        liveConnected = true;
        writeStatus("connected", "");
    }

    @Override
    public void onWaiting() {
        if (!liveRequested) return;
        accumulateLiveTime();
        liveConnected = false;
        writeStatus("waiting", "");
    }

    @Override
    public void onEvent(InteractionSlot.TriggerType type, String key, int amount, String user) {
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
        writeStatus("error", message == null ? "" : message);
    }

    @Override
    public void onBedrockListening() {
        writeBedrockStatus("listening", "");
    }

    @Override
    public void onBedrockConnected() {
        writeBedrockStatus("connected", "");
    }

    @Override
    public void onBedrockDisconnected() {
        writeBedrockStatus("listening", "");
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
        if (connector != null) connector.disconnect();
        currentUsername = username;
        writeStatus("starting", "");
        connector = new TikTokConnector(this);
        connector.connect(username);
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
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_craftlive)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text, queue.size()))
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
