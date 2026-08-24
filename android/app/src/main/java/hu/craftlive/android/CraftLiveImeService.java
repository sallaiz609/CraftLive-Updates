package hu.craftlive.android;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyCharacterMap;
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
        // A chat megnyitása önmagában még nem jelenti azt, hogy a Bedrock valódi
        // szövegmezője fókuszt kapott. A hozzáférhetőségi szolgáltatás előbb
        // megérinti a beviteli sávot, és csak utána engedélyezi a kézbesítést.
        if (!store.preferences().getBoolean("pending_focus_attempted", false)) return;

        if (!hasNewMinecraftInputSessionSince(inputSessionMarker)) return;
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (editorInfo == null || !MINECRAFT_PACKAGE.equals(editorInfo.packageName)) return;

        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        boolean diagnostic = store.preferences().getBoolean("pending_command_diagnostic", false);
        delivering = true;
        replaceSelectionWithCommitText(connection, command);
        handler.postDelayed(() -> verifyOrRetry(store, command, diagnostic, 0, false), 260L);
    }

    /**
     * A Bedrock különböző Android-verziói nem ugyanazt az InputConnection műveletet
     * kezelik. Először a szabványos commitText, majd composing text, végül valódi
     * virtuális billentyűesemények következnek. Enter csak akkor megy ki, ha a
     * parancs visszaolvasható, vagy a nyers billentyűeseményeket a mező fogadta.
     */
    private void verifyOrRetry(InteractionStore store, String command, boolean diagnostic,
                               int stage, boolean rawEventsAccepted) {
        if (!command.equals(store.preferences().getString("pending_command", ""))) {
            delivering = false;
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (connection == null || info == null || !MINECRAFT_PACKAGE.equals(info.packageName)) {
            failDelivery(store, diagnostic);
            return;
        }

        TextState state = inspectEditorText(connection, command);
        if (state == TextState.MATCH
                || (stage >= 2 && state == TextState.UNKNOWN && rawEventsAccepted)) {
            submitCommand(store, diagnostic, connection, info);
            return;
        }

        if (stage == 0) {
            replaceSelectionWithComposingText(connection, command);
            handler.postDelayed(() -> verifyOrRetry(store, command, diagnostic, 1, false), 260L);
            return;
        }
        if (stage == 1) {
            clearEditor(connection);
            boolean accepted = sendRawKeyText(connection, command);
            handler.postDelayed(() -> verifyOrRetry(store, command, diagnostic, 2, accepted), 320L);
            return;
        }
        failDelivery(store, diagnostic);
    }

    private static void replaceSelectionWithCommitText(InputConnection connection, String command) {
        connection.beginBatchEdit();
        connection.finishComposingText();
        connection.performContextMenuAction(android.R.id.selectAll);
        connection.commitText(command, 1);
        connection.endBatchEdit();
    }

    private static void replaceSelectionWithComposingText(InputConnection connection, String command) {
        connection.beginBatchEdit();
        connection.finishComposingText();
        connection.performContextMenuAction(android.R.id.selectAll);
        connection.setComposingText(command, 1);
        connection.finishComposingText();
        connection.endBatchEdit();
    }

    private static void clearEditor(InputConnection connection) {
        connection.beginBatchEdit();
        connection.finishComposingText();
        connection.performContextMenuAction(android.R.id.selectAll);
        connection.commitText("", 1);
        connection.deleteSurroundingText(4096, 4096);
        connection.endBatchEdit();
    }

    private static TextState inspectEditorText(InputConnection connection, String command) {
        CharSequence before = connection.getTextBeforeCursor(Math.max(512, command.length() + 16), 0);
        if (before == null) return TextState.UNKNOWN;
        String value = before.toString().trim();
        if (value.isEmpty()) return TextState.EMPTY;
        if (value.equals(command.trim()) || value.endsWith(command.trim())) return TextState.MATCH;
        return TextState.OTHER;
    }

    private static boolean sendRawKeyText(InputConnection connection, String command) {
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(command.toCharArray());
        if (events != null && events.length > 0) {
            boolean accepted = false;
            for (KeyEvent event : events) accepted |= connection.sendKeyEvent(event);
            return accepted;
        }
        return connection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), command,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0));
    }

    private void submitCommand(InteractionStore store, boolean diagnostic,
                               InputConnection connection, EditorInfo info) {
        boolean handled = sendDefaultEditorAction(true);
        if (!handled) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                handled = connection.performEditorAction(action);
            }
        }
        if (!handled) {
            boolean down = connection.sendKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            boolean up = connection.sendKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            if (!down && !up) {
                connection.performEditorAction(EditorInfo.IME_ACTION_SEND);
                connection.performEditorAction(EditorInfo.IME_ACTION_DONE);
                connection.sendKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_NUMPAD_ENTER));
                connection.sendKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_NUMPAD_ENTER));
            }
        }
        clearPending(store);
        if (diagnostic) {
            Toast.makeText(this, R.string.test_command_submitted, Toast.LENGTH_LONG).show();
        }
        delivering = false;
    }

    private void failDelivery(InteractionStore store, boolean diagnostic) {
        clearPending(store);
        delivering = false;
        CraftLiveAccessibilityService.closeMinecraftChatAfterInputFailure();
        if (diagnostic) {
            Toast.makeText(this, R.string.test_command_input_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static void clearPending(InteractionStore store) {
        store.preferences().edit()
                .remove("pending_command")
                .remove("pending_command_time")
                .remove("pending_command_diagnostic")
                .remove("pending_input_session")
                .remove("pending_focus_attempted")
                .apply();
    }

    private enum TextState {
        MATCH,
        EMPTY,
        OTHER,
        UNKNOWN
    }
}
