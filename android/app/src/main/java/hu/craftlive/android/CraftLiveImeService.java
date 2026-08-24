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
        InteractionStore store = new InteractionStore(this);
        String command = store.preferences().getString("pending_command", "");
        long createdAt = store.preferences().getLong("pending_command_time", 0L);
        if (command == null || command.trim().isEmpty()) return;
        if (System.currentTimeMillis() - createdAt > 15_000L) {
            clearPending(store);
            return;
        }

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        connection.commitText(command, 1);
        handler.postDelayed(() -> {
            InputConnection current = getCurrentInputConnection();
            if (current == null) return;
            current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            current.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            clearPending(store);
        }, 100L);
    }

    private static void clearPending(InteractionStore store) {
        store.preferences().edit()
                .remove("pending_command")
                .remove("pending_command_time")
                .apply();
    }
}
