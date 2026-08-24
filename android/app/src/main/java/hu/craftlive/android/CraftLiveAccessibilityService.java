package hu.craftlive.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class CraftLiveAccessibilityService extends AccessibilityService {
    private static final String MINECRAFT_PACKAGE = "com.mojang.minecraftpe";
    private static final long MINECRAFT_RESUME_SETTLE_MILLIS = 1_350L;
    private static volatile CraftLiveAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String activePackage = "";
    private volatile long activePackageSince;

    public static boolean isReady() {
        return instance != null;
    }

    public static void markForegroundPackage(String packageName) {
        CraftLiveAccessibilityService service = instance;
        if (service != null) service.updateActivePackage(packageName == null ? "" : packageName);
    }

    public static boolean sendCommand(String command, boolean diagnostic) {
        CraftLiveAccessibilityService service = instance;
        if (service == null || !MINECRAFT_PACKAGE.equals(service.activePackage)) return false;
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
        String packageValue = packageName == null ? "" : packageName.toString();
        if ("com.android.systemui".equals(packageValue)) return;
        // Saját toast vagy billentyűzetablak nem írhatja felül a Minecraft állapotát.
        // A MainActivity külön, közvetlenül jelzi, amikor valóban előtérbe kerül.
        if (getPackageName().equals(packageValue)) return;
        updateActivePackage(packageValue);
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

        float xPercent = store.preferences().getFloat("chat_x_percent", 0.50f);
        float yPercent = store.preferences().getFloat("chat_y_percent", 0.035f);

        List<float[]> positions = new ArrayList<>();
        addUniquePosition(positions, xPercent, yPercent);
        // A jelenlegi Bedrock érintős HUD felül, pontosan középen tartja a chatgombot.
        // A második középső és a bal felső pont a többi elterjedt HUD-hoz marad meg.
        addUniquePosition(positions, 0.50f, 0.035f);
        addUniquePosition(positions, 0.50f, 0.060f);
        addUniquePosition(positions, 0.055f, 0.075f);

        List<ScreenFrame> frames = currentScreenFrames();
        if (diagnostic) showToast(R.string.test_opening_chat);

        // A Minecraft előtérbe kerülési eseménye hamarabb érkezhet, mint hogy a játék
        // ismét fogadná az érintéseket. A pontos célpontot csak a rövid stabilizáció
        // után próbáljuk meg.
        long foregroundAge = Math.max(0L, System.currentTimeMillis() - activePackageSince);
        long warmup = Math.max(150L, MINECRAFT_RESUME_SETTLE_MILLIS - foregroundAge);
        mainHandler.postDelayed(() -> tryChatPosition(store, command, diagnostic, frames,
                positions, 0, 0, inputSessionMarker), warmup);
    }

    private void tryChatPosition(InteractionStore store, String command, boolean diagnostic,
                                 List<ScreenFrame> frames, List<float[]> positions,
                                 int positionIndex, int frameIndex, long inputSessionMarker) {
        if (!isPending(store, command)) return;
        if (CraftLiveImeService.hasNewMinecraftInputSessionSince(inputSessionMarker)) {
            CraftLiveImeService.tryDeliverPendingCommand();
            return;
        }
        if (positionIndex >= positions.size()) {
            if (diagnostic) showToast(R.string.test_chat_not_opened);
            clearPending(store);
            return;
        }

        int safeFrameIndex = Math.min(frameIndex, Math.max(0, frames.size() - 1));
        ScreenFrame frame = frames.get(safeFrameIndex);
        float[] position = positions.get(positionIndex);
        float x = clamp(frame.left + frame.width * position[0],
                frame.left + 1f, frame.left + frame.width - 1f);
        float y = clamp(frame.top + frame.height * position[1],
                frame.top + 1f, frame.top + frame.height - 1f);

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 80L))
                .build();
        GestureResultCallback callback = new GestureResultCallback() {
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
                        int[] next = nextAttempt(frames.size(), positionIndex, safeFrameIndex);
                        tryChatPosition(store, command, diagnostic, frames, positions,
                                next[0], next[1], inputSessionMarker);
                    }
                }, 900L);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                int[] next = nextAttempt(frames.size(), positionIndex, safeFrameIndex);
                mainHandler.postDelayed(() -> tryChatPosition(store, command, diagnostic,
                        frames, positions, next[0], next[1], inputSessionMarker), 180L);
            }
        };
        if (!dispatchGesture(gesture, callback, mainHandler)) {
            int[] next = nextAttempt(frames.size(), positionIndex, safeFrameIndex);
            mainHandler.postDelayed(() -> tryChatPosition(store, command, diagnostic,
                    frames, positions, next[0], next[1], inputSessionMarker), 180L);
        }
    }

    private List<ScreenFrame> currentScreenFrames() {
        List<ScreenFrame> frames = new ArrayList<>();
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addFrameAndLandscapeVariant(frames, manager.getCurrentWindowMetrics().getBounds());
            WindowMetrics maximum = manager.getMaximumWindowMetrics();
            addFrameAndLandscapeVariant(frames, maximum.getBounds());
        }

        DisplayMetrics metrics = new DisplayMetrics();
        if (manager != null) {
            manager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        addFrameAndLandscapeVariant(frames,
                new Rect(0, 0, metrics.widthPixels, metrics.heightPixels));

        DisplayMetrics resources = getResources().getDisplayMetrics();
        addFrameAndLandscapeVariant(frames,
                new Rect(0, 0, resources.widthPixels, resources.heightPixels));

        if (frames.isEmpty()) frames.add(new ScreenFrame(0, 0, 1, 1));
        return frames;
    }

    private static void addFrameAndLandscapeVariant(List<ScreenFrame> frames, Rect bounds) {
        if (bounds == null || bounds.width() <= 1 || bounds.height() <= 1) return;
        addUniqueFrame(frames, new ScreenFrame(bounds.left, bounds.top,
                bounds.width(), bounds.height()));
        if (bounds.width() < bounds.height()) {
            // Néhány gyártói rendszer még álló tájolású méretet ad a szolgáltatásnak,
            // miközben a Minecraft már fekvő módban fut.
            addUniqueFrame(frames, new ScreenFrame(0, 0, bounds.height(), bounds.width()));
        }
    }

    private static void addUniqueFrame(List<ScreenFrame> frames, ScreenFrame candidate) {
        for (ScreenFrame existing : frames) {
            if (Math.abs(existing.left - candidate.left) <= 1
                    && Math.abs(existing.top - candidate.top) <= 1
                    && Math.abs(existing.width - candidate.width) <= 1
                    && Math.abs(existing.height - candidate.height) <= 1) return;
        }
        frames.add(candidate);
    }

    private static int[] nextAttempt(int frameCount, int positionIndex, int frameIndex) {
        if (frameIndex + 1 < frameCount) return new int[]{positionIndex, frameIndex + 1};
        return new int[]{positionIndex + 1, 0};
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void updateActivePackage(String packageName) {
        String value = packageName == null ? "" : packageName;
        if (!value.equals(activePackage)) {
            activePackage = value;
            activePackageSince = System.currentTimeMillis();
        }
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

    private static final class ScreenFrame {
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private ScreenFrame(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }
}
