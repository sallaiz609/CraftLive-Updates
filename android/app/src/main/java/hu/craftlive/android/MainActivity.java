package hu.craftlive.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private InteractionStore store;
    private UpdateManager updateManager;
    private EditText usernameInput;
    private TextView statusText;
    private TextView plusProgress;
    private LinearLayout slotContainer;
    private Button liveButton;
    private Button standardTab;
    private Button plusTab;
    private LinearLayout setupContainer;
    private boolean showingPlus;
    private boolean receiverRegistered;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new InteractionStore(this);
        store.clearTikTokUsernameForNewVersion(versionCode());
        updateManager = new UpdateManager(this);
        requestNotificationPermission();
        buildUi();
        handler.postDelayed(() -> updateManager.check(false), 1_200L);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(InteractionForegroundService.ACTION_STATUS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        CraftLiveAccessibilityService.markForegroundPackage(getPackageName());
        if (updateManager != null) updateManager.resumePendingInstallIfAllowed();
        if (statusText != null) {
            refreshStatus();
            refreshSetupVisibility();
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        updateManager.shutdown();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.craft_background));

        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(18), dp(18), dp(40));
        scroll.addView(root, matchWrap());

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBlock = vertical();
        TextView title = text(getString(R.string.title), 27f, R.color.craft_text, true);
        TextView subtitle = text(getString(R.string.subtitle), 16f, R.color.craft_muted, false);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView version = text(getString(R.string.version_format, versionName()), 16f, R.color.craft_green, true);
        version.setGravity(Gravity.END);
        header.addView(version);
        root.addView(header, marginBottom(18));

        LinearLayout tabRow = horizontal();
        standardTab = actionButton(getString(R.string.standard), true);
        plusTab = actionButton(getString(R.string.plus), false);
        tabRow.addView(standardTab, weightedButton());
        tabRow.addView(plusTab, weightedButton());
        root.addView(tabRow, marginBottom(8));
        standardTab.setOnClickListener(v -> switchTab(false));
        plusTab.setOnClickListener(v -> switchTab(true));

        plusProgress = text("", 17f, R.color.craft_green, true);
        plusProgress.setPadding(dp(4), dp(7), dp(4), dp(7));
        root.addView(plusProgress, marginBottom(6));

        TextView safety = panelText(getString(R.string.fixed_delay), R.color.craft_green);
        root.addView(safety, marginBottom(12));

        usernameInput = new EditText(this);
        usernameInput.setHint(R.string.tiktok_username);
        usernameInput.setHintTextColor(color(R.color.craft_muted));
        usernameInput.setTextColor(color(R.color.craft_text));
        usernameInput.setTextSize(18f);
        usernameInput.setSingleLine(true);
        usernameInput.setText(store.preferences().getString("tiktok_username", ""));
        styleField(usernameInput);
        root.addView(usernameInput, marginBottom(8));

        LinearLayout liveRow = horizontal();
        Button save = actionButton(getString(R.string.save), false);
        liveButton = actionButton(getString(R.string.start_live), true);
        liveRow.addView(save, weightedButton());
        liveRow.addView(liveButton, weightedButton());
        root.addView(liveRow, marginBottom(8));
        save.setOnClickListener(v -> saveUsername());
        liveButton.setOnClickListener(v -> toggleLive());

        statusText = panelText(getString(R.string.status_idle), R.color.craft_muted);
        root.addView(statusText, marginBottom(14));

        setupContainer = vertical();
        setupContainer.addView(sectionTitle(getString(R.string.setup_note)));
        setupContainer.addView(fullButton(R.string.enable_accessibility,
                v -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        setupContainer.addView(fullButton(R.string.enable_keyboard,
                v -> openSettings(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        setupContainer.addView(fullButton(R.string.choose_keyboard, v -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
        }));
        setupContainer.addView(fullButton(R.string.battery_settings, v -> {
            Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(details);
            toast(R.string.setup_incomplete);
        }));
        root.addView(setupContainer, matchWrap());

        LinearLayout toolRow = horizontal();
        toolRow.addView(actionButtonWithClick(R.string.open_minecraft, v -> openMinecraft()), weightedButton());
        toolRow.addView(actionButtonWithClick(R.string.test_bedrock,
                v -> testCommand("/summon zombie ~ ~1 ~")), weightedButton());
        root.addView(toolRow, marginBottom(8));

        LinearLayout updateRow = horizontal();
        updateRow.addView(actionButtonWithClick(R.string.check_update,
                v -> updateManager.check(true)), weightedButton());
        updateRow.addView(actionButtonWithClick(R.string.calibration,
                v -> showCalibration()), weightedButton());
        root.addView(updateRow, marginBottom(8));

        Button support = fullButton(R.string.support_creator, v -> {
            Intent profile = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.tiktok.com/@venom_hun_"));
            startActivity(profile);
        });
        root.addView(support);

        TextView slotsTitle = text(getString(R.string.interaction_slots), 22f, R.color.craft_text, true);
        slotsTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(slotsTitle);
        slotContainer = vertical();
        root.addView(slotContainer, matchWrap());
        setContentView(scroll);
        refreshStatus();
        refreshSetupVisibility();
        rebuildSlots();
    }

    private void switchTab(boolean plus) {
        if (plus && !store.isPlusUnlocked()) {
            toast(getString(R.string.plus_locked, store.getVerifiedLiveMillis() / 3_600_000d));
            return;
        }
        showingPlus = plus;
        styleButton(standardTab, !plus);
        styleButton(plusTab, plus);
        rebuildSlots();
    }

    private void rebuildSlots() {
        slotContainer.removeAllViews();
        List<InteractionSlot> slots = showingPlus ? store.loadPlus() : store.loadStandard();
        for (InteractionSlot slot : slots) slotContainer.addView(slotCard(slot), marginBottom(9));
    }

    private View slotCard(InteractionSlot slot) {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(panelBackground(R.color.craft_panel, 1, R.color.craft_panel_alt));

        String visibleName = slot.name == null || slot.name.trim().isEmpty()
                ? getString(R.string.slot_empty) : slot.name;
        TextView name = text(getString(R.string.slot_title, slot.index + 1, visibleName),
                18f, slot.enabled ? R.color.craft_text : R.color.craft_muted, true);
        card.addView(name);
        String details = triggerLabel(slot.triggerType) +
                (slot.triggerKey.trim().isEmpty() ? "" : " · " + slot.triggerKey) +
                "\n" + (slot.command.trim().isEmpty() ? "—" : slot.command);
        TextView detail = text(details, 16f, R.color.craft_muted, false);
        detail.setPadding(0, dp(5), 0, dp(8));
        card.addView(detail);

        LinearLayout actions = horizontal();
        Button edit = actionButton(getString(R.string.edit), false);
        Button test = actionButton(getString(R.string.test), true);
        actions.addView(edit, weightedButton());
        actions.addView(test, weightedButton());
        card.addView(actions);
        edit.setOnClickListener(v -> showSlotEditor(slot));
        test.setOnClickListener(v -> testCommand(slot.command));
        return card;
    }

    private void showSlotEditor(InteractionSlot slot) {
        LinearLayout form = vertical();
        int padding = dp(18);
        form.setPadding(padding, dp(4), padding, 0);

        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.enabled);
        enabled.setTextColor(color(R.color.craft_text));
        enabled.setTextSize(17f);
        enabled.setChecked(slot.enabled);
        form.addView(enabled);

        EditText name = dialogField(slot.name, getString(R.string.slot_empty));
        form.addView(name, marginBottom(6));

        Spinner trigger = new Spinner(this);
        String[] labels = {
                getString(R.string.gift), getString(R.string.like), getString(R.string.follow),
                getString(R.string.subscribe), getString(R.string.share), getString(R.string.comment)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        trigger.setAdapter(adapter);
        trigger.setSelection(slot.triggerType.ordinal());
        form.addView(trigger, marginBottom(6));

        EditText key = dialogField(slot.triggerKey, getString(R.string.trigger_key));
        form.addView(key, marginBottom(6));
        EditText threshold = dialogField(String.valueOf(slot.threshold), getString(R.string.threshold));
        threshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(threshold, marginBottom(6));
        EditText command = dialogField(slot.command, getString(R.string.command));
        command.setSingleLine(false);
        command.setMinLines(2);
        form.addView(command, marginBottom(6));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.slot_title, slot.index + 1,
                        slot.plus ? getString(R.string.plus) : getString(R.string.standard)))
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    slot.enabled = enabled.isChecked();
                    slot.name = name.getText().toString().trim();
                    slot.triggerType = InteractionSlot.TriggerType.values()[trigger.getSelectedItemPosition()];
                    slot.triggerKey = key.getText().toString().trim();
                    try {
                        slot.threshold = Math.max(1, Integer.parseInt(threshold.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                        slot.threshold = 1;
                    }
                    slot.command = command.getText().toString().trim();
                    List<InteractionSlot> slots = slot.plus ? store.loadPlus() : store.loadStandard();
                    slots.set(slot.index, slot);
                    if (slot.plus) store.savePlus(slots); else store.saveStandard(slots);
                    rebuildSlots();
                    toast(R.string.saved);
                })
                .show();
    }

    private void saveUsername() {
        String username = usernameInput.getText().toString().trim().replace("@", "");
        store.preferences().edit().putString("tiktok_username", username).apply();
        toast(R.string.saved);
    }

    private void toggleLive() {
        if (updateManager.hasMandatoryUpdate()) {
            updateManager.check(true);
            return;
        }
        if (InteractionForegroundService.isRunning()) {
            Intent stop = new Intent(this, InteractionForegroundService.class).setAction(
                    InteractionForegroundService.ACTION_STOP);
            startService(stop);
            handler.postDelayed(this::refreshStatus, 300L);
            return;
        }
        String username = usernameInput.getText().toString().trim().replace("@", "");
        if (username.isEmpty()) {
            toast(R.string.username_required);
            return;
        }
        store.preferences().edit().putString("tiktok_username", username).apply();
        Intent start = new Intent(this, InteractionForegroundService.class).setAction(
                InteractionForegroundService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(start); else startService(start);
        handler.postDelayed(this::refreshStatus, 300L);
    }

    private void testCommand(String command) {
        if (updateManager.hasMandatoryUpdate()) {
            updateManager.check(true);
            return;
        }
        if (command == null || command.trim().isEmpty()) return;
        if (!isAccessibilityEnabled()) {
            toast(R.string.accessibility_missing);
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            return;
        }
        if (!isCraftLiveImeEnabled()) {
            toast(R.string.keyboard_missing);
            openSettings(Settings.ACTION_INPUT_METHOD_SETTINGS);
            return;
        }
        if (!isCraftLiveImeSelected()) {
            toast(R.string.keyboard_not_selected);
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
            return;
        }
        if (getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe") == null) {
            toast(R.string.minecraft_missing);
            return;
        }
        Intent test = new Intent(this, InteractionForegroundService.class)
                .setAction(InteractionForegroundService.ACTION_TEST)
                .putExtra(InteractionForegroundService.EXTRA_COMMAND, command);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(test); else startService(test);
        handler.postDelayed(this::openMinecraft, 180L);
        toast(R.string.test_queued);
    }

    private boolean openMinecraft() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe");
        if (launch == null) {
            toast(R.string.minecraft_missing);
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        return true;
    }

    private void refreshStatus() {
        String state = store.preferences().getString("live_status", "idle");
        String detail = store.preferences().getString("live_status_detail", "");
        int textColor = R.color.craft_muted;
        String text;
        if ("connected".equals(state)) {
            text = getString(R.string.status_connected);
            textColor = R.color.craft_green;
        } else if ("starting".equals(state)) {
            text = getString(R.string.status_starting);
        } else if ("waiting".equals(state)) {
            text = getString(R.string.status_waiting);
        } else if ("error".equals(state)) {
            text = getString(R.string.status_error, detail);
            textColor = R.color.craft_red;
        } else {
            text = getString(R.string.status_idle);
        }
        statusText.setText(text + " · " + getString(R.string.queued_count,
                InteractionForegroundService.queuedCount()));
        statusText.setTextColor(color(textColor));
        liveButton.setText(InteractionForegroundService.isRunning()
                ? R.string.stop_live : R.string.start_live);
        double hours = store.getVerifiedLiveMillis() / 3_600_000d;
        if (store.isPlusUnlocked()) {
            plusProgress.setText(R.string.plus_unlocked);
        } else {
            plusProgress.setText(getString(R.string.plus_locked, hours));
        }
        if (!store.isPlusUnlocked() && showingPlus) switchTab(false);
        refreshSetupVisibility();
    }

    private void refreshSetupVisibility() {
        if (setupContainer == null) return;
        boolean ready = isAccessibilityEnabled() && isCraftLiveImeEnabled() && isCraftLiveImeSelected();
        setupContainer.setVisibility(ready ? View.GONE : View.VISIBLE);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isEnabled()) return false;
        ComponentName expected = new ComponentName(this, CraftLiveAccessibilityService.class);
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            ComponentName actual = new ComponentName(
                    info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (expected.equals(actual)) return true;
        }
        return CraftLiveAccessibilityService.isReady();
    }

    private boolean isCraftLiveImeEnabled() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(this, CraftLiveImeService.class);
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
            ComponentName actual = ComponentName.unflattenFromString(info.getId());
            if (expected.equals(actual)) return true;
        }
        return false;
    }

    private boolean isCraftLiveImeSelected() {
        String selected = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName actual = selected == null ? null : ComponentName.unflattenFromString(selected);
        return new ComponentName(this, CraftLiveImeService.class).equals(actual);
    }

    private void showCalibration() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), 0, dp(20), 0);
        TextView help = text(getString(R.string.calibration_help), 16f, R.color.craft_muted, false);
        form.addView(help, marginBottom(10));
        int xInitial = Math.round(store.preferences().getFloat("chat_x_percent", 0.40f) * 100f);
        int yInitial = Math.round(store.preferences().getFloat("chat_y_percent", 0.055f) * 100f);
        TextView xLabel = text(getString(R.string.calibration_x, xInitial), 17f, R.color.craft_text, true);
        SeekBar x = new SeekBar(this);
        x.setMax(100);
        x.setProgress(xInitial);
        form.addView(xLabel);
        form.addView(x);
        TextView yLabel = text(getString(R.string.calibration_y, yInitial), 17f, R.color.craft_text, true);
        SeekBar y = new SeekBar(this);
        y.setMax(100);
        y.setProgress(yInitial);
        form.addView(yLabel);
        form.addView(y);
        x.setOnSeekBarChangeListener(labelListener(xLabel, R.string.calibration_x));
        y.setOnSeekBarChangeListener(labelListener(yLabel, R.string.calibration_y));
        new AlertDialog.Builder(this)
                .setTitle(R.string.calibration)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    store.preferences().edit()
                            .putFloat("chat_x_percent", Math.max(1, x.getProgress()) / 100f)
                            .putFloat("chat_y_percent", Math.max(1, y.getProgress()) / 100f)
                            .apply();
                    toast(R.string.saved);
                }).show();
    }

    private SeekBar.OnSeekBarChangeListener labelListener(TextView label, int formatResource) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(getString(formatResource, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void openSettings(String action) {
        startActivity(new Intent(action));
        toast(R.string.setup_incomplete);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 51);
        }
    }

    private String triggerLabel(InteractionSlot.TriggerType type) {
        return switch (type) {
            case GIFT -> getString(R.string.gift);
            case LIKE -> getString(R.string.like);
            case FOLLOW -> getString(R.string.follow);
            case SUBSCRIBE -> getString(R.string.subscribe);
            case SHARE -> getString(R.string.share);
            case COMMENT -> getString(R.string.comment);
        };
    }

    private int versionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return 1;
        }
    }

    private String versionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "0.1.0" : name;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "0.1.0";
        }
    }

    private Button fullButton(int textResource, View.OnClickListener listener) {
        Button button = actionButtonWithClick(textResource, listener);
        button.setLayoutParams(marginBottom(7));
        return button;
    }

    private Button actionButtonWithClick(int textResource, View.OnClickListener listener) {
        Button button = actionButton(getString(textResource), false);
        button.setOnClickListener(listener);
        return button;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16f);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        button.setPadding(dp(8), dp(7), dp(8), dp(7));
        styleButton(button, primary);
        return button;
    }

    private void styleButton(Button button, boolean primary) {
        button.setTextColor(primary ? color(R.color.craft_background) : color(R.color.craft_text));
        button.setBackground(panelBackground(primary ? R.color.craft_green : R.color.craft_panel_alt,
                1, primary ? R.color.craft_green_dark : R.color.craft_green_dark));
    }

    private EditText dialogField(String value, String hint) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(hint);
        field.setTextSize(17f);
        field.setTextColor(color(R.color.craft_text));
        field.setHintTextColor(color(R.color.craft_muted));
        styleField(field);
        return field;
    }

    private void styleField(EditText field) {
        field.setPadding(dp(13), dp(10), dp(13), dp(10));
        field.setBackground(panelBackground(R.color.craft_panel, 1, R.color.craft_panel_alt));
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 17f, R.color.craft_muted, false);
        view.setPadding(dp(2), dp(8), dp(2), dp(12));
        return view;
    }

    private TextView panelText(String value, int colorResource) {
        TextView view = text(value, 17f, colorResource, false);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(panelBackground(R.color.craft_panel, 1, R.color.craft_panel_alt));
        return view;
    }

    private TextView text(String value, float size, int colorResource, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorResource));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private GradientDrawable panelBackground(int fill, int strokeWidth, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(fill));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(strokeWidth), color(stroke));
        return background;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams marginBottom(int bottomDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(int resource) {
        toast(getString(resource));
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
}
