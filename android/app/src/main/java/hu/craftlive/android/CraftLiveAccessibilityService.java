package hu.craftlive.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

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

    public static boolean sendCommand(String command) {
        CraftLiveAccessibilityService service = instance;
        if (service == null || !"com.mojang.minecraftpe".equals(service.activePackage)) return false;
        service.openMinecraftChat(command);
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

    private void openMinecraftChat(String command) {
        InteractionStore store = new InteractionStore(this);
        store.preferences().edit()
                .putString("pending_command", command)
                .putLong("pending_command_time", System.currentTimeMillis())
                .apply();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float xPercent = store.preferences().getFloat("chat_x_percent", 0.40f);
        float yPercent = store.preferences().getFloat("chat_y_percent", 0.055f);
        float x = Math.max(1f, Math.min(metrics.widthPixels - 1f, metrics.widthPixels * xPercent));
        float y = Math.max(1f, Math.min(metrics.heightPixels - 1f, metrics.heightPixels * yPercent));

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 80L))
                .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                // A Minecraft megnyitja a chatet, majd az aktív CraftLive IME több biztonságos
                // próbálkozással elküldi a függő parancsot. Siker után a parancs törlődik.
                mainHandler.postDelayed(CraftLiveImeService::tryDeliverPendingCommand, 350L);
                mainHandler.postDelayed(CraftLiveImeService::tryDeliverPendingCommand, 850L);
                mainHandler.postDelayed(CraftLiveImeService::tryDeliverPendingCommand, 1_500L);
            }
        }, mainHandler);
    }
}
