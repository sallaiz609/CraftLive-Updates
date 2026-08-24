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

public final class CraftLiveImeService extends InputMethodService {
    private static volatile CraftLiveImeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean delivering;

    public static void tryDeliverPendingCommand() {
        CraftLiveImeService service = instance;
        if (service != null) service.handler.postDelayed(service::deliverPendingCommand, 180L);
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
        handler.postDelayed(this::deliverPendingCommand, 140L);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        handler.postDelayed(this::deliverPendingCommand, 140L);
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
        if (command == null || command.trim().isEmpty()) return;
        if (System.currentTimeMillis() - createdAt > 45_000L) {
            clearPending(store);
            return;
        }

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (!connection.commitText(command, 1)) return;
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
            if (!down && !up) current.performEditorAction(EditorInfo.IME_ACTION_DONE);
            clearPending(store);
            delivering = false;
        }, 180L);
    }

    private static void clearPending(InteractionStore store) {
        store.preferences().edit()
                .remove("pending_command")
                .remove("pending_command_time")
                .apply();
    }
}
