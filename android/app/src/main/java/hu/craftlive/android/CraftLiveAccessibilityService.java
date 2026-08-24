package hu.craftlive.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class CraftLiveAccessibilityService extends AccessibilityService {
    private static volatile CraftLiveAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String activePackage = "";

    public static boolean isReady() {
        return instance != null;
    }

    public static void markForegroundPackage(String packageName) {
        CraftLiveAccessibilityService service = instance;
        if (service != null) service.activePackage = packageName == null ? "" : packageName;
    }

    public static boolean sendCommand(String command, boolean diagnostic) {
        CraftLiveAccessibilityService service = instance;
        if (service == null || !"com.mojang.minecraftpe".equals(service.activePackage)) return false;
        service.openMinecraftChat(command, diagnostic);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence packageName = event == null ? null : event.getPackageName();
        CharSequence className = event == null ? null : event.getClassName();
        String packageValue = packageName == null ? "" : packageName.toString();
        String classValue = className == null ? "" : className.toString();
        if ("com.android.systemui".equals(packageValue)) return;
        if (getPackageName().equals(packageValue)
                && (classValue.contains("SoftInputWindow") || classValue.contains("InputMethod"))) {
            return;
        }
        activePackage = packageValue;
        // A szolgáltatás nem olvassa a képernyő tartalmát; kizárólag a csomagnevet figyeli.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private void openMinecraftChat(String command, boolean diagnostic) {
        InteractionStore store = new InteractionStore(this);
        long inputSessionMarker = CraftLiveImeService.currentInputSessionMarker();
        store.preferences().edit()
                .putString("pending_command", command)
                .putLong("pending_command_time", System.currentTimeMillis())
                .putBoolean("pending_command_diagnostic", diagnostic)
                .putLong("pending_input_session", inputSessionMarker)
                .apply();

        DisplayMetrics metrics = currentDisplayMetrics();
        float xPercent = store.preferences().getFloat("chat_x_percent", 0.50f);
        float yPercent = store.preferences().getFloat("chat_y_percent", 0.035f);

        List<float[]> positions = new ArrayList<>();
        addUniquePosition(positions, xPercent, yPercent);
        // A jelenlegi Bedrock érintős HUD felül, pontosan középen tartja a chatgombot.
        // A második középső és a bal felső pont a többi elterjedt HUD-hoz marad meg.
        addUniquePosition(positions, 0.50f, 0.035f);
        addUniquePosition(positions, 0.50f, 0.060f);
        addUniquePosition(positions, 0.055f, 0.075f);

        if (diagnostic) showToast(R.string.test_opening_chat);
        tryChatPosition(store, command, diagnostic, metrics, positions, 0, inputSessionMarker);
    }

    private void tryChatPosition(InteractionStore store, String command, boolean diagnostic,
                                 DisplayMetrics metrics, List<float[]> positions, int index,
                                 long inputSessionMarker) {
        if (!isPending(store, command)) return;
        if (CraftLiveImeService.hasNewMinecraftInputSessionSince(inputSessionMarker)) {
            CraftLiveImeService.tryDeliverPendingCommand();
            return;
        }
        if (index >= positions.size()) {
            if (diagnostic) showToast(R.string.test_chat_not_opened);
            clearPending(store);
            return;
        }

        float[] position = positions.get(index);
        float x = Math.max(1f, Math.min(metrics.widthPixels - 1f,
                metrics.widthPixels * position[0]));
        float y = Math.max(1f, Math.min(metrics.heightPixels - 1f,
                metrics.heightPixels * position[1]));

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 80L))
                .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                mainHandler.postDelayed(CraftLiveImeService::tryDeliverPendingCommand, 250L);
                mainHandler.postDelayed(() -> {
                    if (!isPending(store, command)) return;
                    if (CraftLiveImeService.hasNewMinecraftInputSessionSince(inputSessionMarker)) {
                        store.preferences().edit()
                                .putFloat("chat_x_percent", position[0])
                                .putFloat("chat_y_percent", position[1])
                                .apply();
                        CraftLiveImeService.tryDeliverPendingCommand();
                    } else {
                        tryChatPosition(store, command, diagnostic, metrics, positions, index + 1,
                                inputSessionMarker);
                    }
                }, 850L);
            }
        }, mainHandler);
    }

    private DisplayMetrics currentDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager != null) {
            manager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return metrics;
    }

    private static void addUniquePosition(List<float[]> positions, float x, float y) {
        for (float[] existing : positions) {
            if (Math.abs(existing[0] - x) < 0.005f && Math.abs(existing[1] - y) < 0.005f) return;
        }
        positions.add(new float[]{x, y});
    }

    private static boolean isPending(InteractionStore store, String command) {
        return command.equals(store.preferences().getString("pending_command", ""));
    }

    private static void clearPending(InteractionStore store) {
        store.preferences().edit()
                .remove("pending_command")
                .remove("pending_command_time")
                .remove("pending_command_diagnostic")
                .remove("pending_input_session")
                .apply();
    }

    private void showToast(int message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}
