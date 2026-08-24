package hu.craftlive.android;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicLong;

public final class CraftLiveImeService extends InputMethodService {
    private static final String MINECRAFT_PACKAGE = "com.mojang.minecraftpe";
    private static final AtomicLong INPUT_SESSION_COUNTER = new AtomicLong();
    private static volatile CraftLiveImeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean delivering;
    private volatile boolean inputActive;
    private volatile long inputSessionId;

    public static void tryDeliverPendingCommand() {
        CraftLiveImeService service = instance;
        if (service != null) service.handler.postDelayed(service::deliverPendingCommand, 180L);
    }

    public static long currentInputSessionMarker() {
        CraftLiveImeService service = instance;
        return service == null ? 0L : service.inputSessionId;
    }

    public static boolean hasNewMinecraftInputSessionSince(long marker) {
        CraftLiveImeService service = instance;
        if (service == null || !service.inputActive || service.inputSessionId <= marker) return false;
        EditorInfo info = service.getCurrentInputEditorInfo();
        return info != null && MINECRAFT_PACKAGE.equals(info.packageName);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public View onCreateInputView() {
        TextView status = new TextView(this);
        status.setText("CraftLive · Minecraft command sender");
        status.setTextColor(0xFFF1FFF5);
        status.setBackgroundColor(0xFF0C2117);
        status.setTextSize(16f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(12, 18, 12, 18);
        return status;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        inputSessionId = INPUT_SESSION_COUNTER.incrementAndGet();
        inputActive = true;
        handler.postDelayed(this::deliverPendingCommand, 140L);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (!inputActive) inputSessionId = INPUT_SESSION_COUNTER.incrementAndGet();
        inputActive = true;
        handler.postDelayed(this::deliverPendingCommand, 140L);
    }

    @Override
    public void onFinishInput() {
        inputActive = false;
        super.onFinishInput();
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private void deliverPendingCommand() {
        if (delivering) return;
        InteractionStore store = new InteractionStore(this);
        String command = store.preferences().getString("pending_command", "");
        long createdAt = store.preferences().getLong("pending_command_time", 0L);
        long inputSessionMarker = store.preferences().getLong("pending_input_session", Long.MAX_VALUE);
        if (command == null || command.trim().isEmpty()) return;
        if (System.currentTimeMillis() - createdAt > 45_000L) {
            clearPending(store);
            return;
        }

        if (!hasNewMinecraftInputSessionSince(inputSessionMarker)) return;
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (editorInfo == null || !MINECRAFT_PACKAGE.equals(editorInfo.packageName)) return;

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (!connection.commitText(command, 1)) return;
        boolean diagnostic = store.preferences().getBoolean("pending_command_diagnostic", false);
        delivering = true;
        handler.postDelayed(() -> {
            InputConnection current = getCurrentInputConnection();
            if (current == null) {
                delivering = false;
                return;
            }
            boolean down = current.sendKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            boolean up = current.sendKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            // A Bedrock verziótól és a gyártó Android-billentyűzet-kezelésétől függően
            // Entert, SEND vagy DONE szerkesztőműveletet fogad el. Mindhármat megkíséreljük;
            // a már bezárt chatablak a további próbálkozásokat figyelmen kívül hagyja.
            current.performEditorAction(EditorInfo.IME_ACTION_SEND);
            current.performEditorAction(EditorInfo.IME_ACTION_DONE);
            if (!down && !up) {
                current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_NUMPAD_ENTER));
                current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_NUMPAD_ENTER));
            }
            clearPending(store);
            if (diagnostic) {
                Toast.makeText(this, R.string.test_command_submitted, Toast.LENGTH_LONG).show();
            }
            delivering = false;
        }, 180L);
    }

    private static void clearPending(InteractionStore store) {
        store.preferences().edit()
                .remove("pending_command")
                .remove("pending_command_time")
                .remove("pending_command_diagnostic")
                .remove("pending_input_session")
                .apply();
    }
}
