package hu.craftlive.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private InteractionStore store;
    private GiftCatalogStore giftCatalog;
    private UpdateManager updateManager;
    private EditText usernameInput;
    private TextView statusText;
    private TextView bridgeStatusText;
    private TextView commandResultText;
    private TextView plusProgress;
    private LinearLayout slotContainer;
    private Button liveButton;
    private Button standardTab;
    private Button plusTab;
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
        giftCatalog = new GiftCatalogStore(this);
        store.clearTikTokUsernameForNewVersion(versionCode());
        updateManager = new UpdateManager(this);
        requestNotificationPermission();
        buildUi();
        ensureBridgeService();
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
        if (updateManager != null) updateManager.resumePendingInstallIfAllowed();
        if (statusText != null) {
            refreshStatus();
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

        bridgeStatusText = panelText(getString(R.string.bridge_starting), R.color.craft_muted);
        root.addView(bridgeStatusText, marginBottom(7));

        TextView bridgeHelp = panelText(getString(R.string.bridge_setup_note,
                preferredConnectCommand()), R.color.craft_text);
        root.addView(bridgeHelp, marginBottom(7));

        LinearLayout bridgeRow = horizontal();
        bridgeRow.addView(actionButtonWithClick(R.string.copy_connect_command,
                v -> copyConnectCommand()), weightedButton());
        bridgeRow.addView(actionButtonWithClick(R.string.open_minecraft,
                v -> openMinecraft()), weightedButton());
        root.addView(bridgeRow, marginBottom(12));

        commandResultText = panelText("", R.color.craft_muted);
        commandResultText.setVisibility(View.GONE);
        root.addView(commandResultText, marginBottom(7));

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

        root.addView(fullButton(R.string.test_bedrock,
                v -> testCommand("/summon zombie ~ ~1 ~")));

        root.addView(fullButton(R.string.check_update, v -> updateManager.check(true)));

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

        form.addView(sectionTitle(getString(R.string.trigger_type)));
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

        String initialGift = slot.triggerType == InteractionSlot.TriggerType.GIFT
                && slot.triggerKey != null && !slot.triggerKey.trim().isEmpty()
                ? slot.triggerKey.trim() : "Rose";
        String[] selectedGift = {initialGift};
        Button giftButton = actionButton(giftButtonLabel(initialGift), false);
        form.addView(giftButton, marginBottom(4));
        TextView giftHint = sectionTitle(getString(R.string.gift_catalog_hint));
        form.addView(giftHint);
        giftButton.setOnClickListener(v -> showGiftPicker(selectedGift[0], giftName -> {
            selectedGift[0] = giftName;
            giftButton.setText(giftButtonLabel(giftName));
        }));

        EditText key = dialogField(slot.triggerKey, getString(R.string.trigger_key));
        form.addView(key, marginBottom(6));
        EditText threshold = dialogField(String.valueOf(slot.threshold), getString(R.string.threshold));
        threshold.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(threshold, marginBottom(6));

        form.addView(sectionTitle(getString(R.string.action_type)));
        Spinner action = new Spinner(this);
        boolean hungarian = "hu".equals(Locale.getDefault().getLanguage());
        String[] actionLabels = BedrockActionCatalog.labels(hungarian);
        action.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, actionLabels));
        int initialAction = BedrockActionCatalog.detect(slot.command);
        action.setSelection(initialAction);
        form.addView(action, marginBottom(6));

        TextView mobTitle = sectionTitle(getString(R.string.mob_select));
        form.addView(mobTitle);
        Spinner mob = new Spinner(this);
        List<BedrockMobCatalog.Item> mobs = BedrockMobCatalog.all();
        String[] mobLabels = new String[mobs.size()];
        for (int index = 0; index < mobs.size(); index++) mobLabels[index] = mobs.get(index).label();
        mob.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, mobLabels));
        mob.setSelection(BedrockMobCatalog.indexOf(BedrockActionCatalog.mobId(slot.command)));
        form.addView(mob, marginBottom(6));

        EditText command = dialogField(slot.command, getString(R.string.custom_command));
        command.setSingleLine(false);
        command.setMinLines(2);
        form.addView(command, marginBottom(6));

        Runnable updateTriggerVisibility = () -> {
            InteractionSlot.TriggerType type = InteractionSlot.TriggerType.values()[
                    trigger.getSelectedItemPosition()];
            boolean isGift = type == InteractionSlot.TriggerType.GIFT;
            boolean isLike = type == InteractionSlot.TriggerType.LIKE;
            boolean isComment = type == InteractionSlot.TriggerType.COMMENT;
            giftButton.setVisibility(isGift ? View.VISIBLE : View.GONE);
            giftHint.setVisibility(isGift ? View.VISIBLE : View.GONE);
            key.setVisibility(isComment ? View.VISIBLE : View.GONE);
            threshold.setVisibility(isLike ? View.VISIBLE : View.GONE);
        };
        Runnable updateActionVisibility = () -> {
            int selected = action.getSelectedItemPosition();
            mobTitle.setVisibility(selected == BedrockActionCatalog.SPAWN_MOB
                    ? View.VISIBLE : View.GONE);
            mob.setVisibility(selected == BedrockActionCatalog.SPAWN_MOB
                    ? View.VISIBLE : View.GONE);
            command.setVisibility(selected == BedrockActionCatalog.CUSTOM
                    ? View.VISIBLE : View.GONE);
        };
        trigger.setOnItemSelectedListener(simpleSelection(updateTriggerVisibility));
        action.setOnItemSelectedListener(simpleSelection(updateActionVisibility));
        updateTriggerVisibility.run();
        updateActionVisibility.run();

        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(form, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.slot_title, slot.index + 1,
                        slot.plus ? getString(R.string.plus) : getString(R.string.standard)))
                .setView(formScroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    slot.enabled = enabled.isChecked();
                    slot.name = name.getText().toString().trim();
                    slot.triggerType = InteractionSlot.TriggerType.values()[trigger.getSelectedItemPosition()];
                    if (slot.triggerType == InteractionSlot.TriggerType.GIFT) {
                        slot.triggerKey = selectedGift[0];
                    } else if (slot.triggerType == InteractionSlot.TriggerType.COMMENT) {
                        slot.triggerKey = key.getText().toString().trim();
                    } else {
                        slot.triggerKey = "";
                    }
                    try {
                        slot.threshold = Math.max(1, Integer.parseInt(threshold.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                        slot.threshold = 1;
                    }
                    int actionIndex = action.getSelectedItemPosition();
                    String mobId = mobs.get(mob.getSelectedItemPosition()).id;
                    slot.command = BedrockActionCatalog.command(
                            actionIndex, mobId, command.getText().toString());
                    if (slot.name.isEmpty()) {
                        String triggerName = slot.triggerType == InteractionSlot.TriggerType.GIFT
                                ? selectedGift[0] : triggerLabel(slot.triggerType);
                        slot.name = triggerName + " → " + actionLabels[actionIndex];
                    }
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
        if (InteractionForegroundService.isLiveActive()) {
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
        if (!InteractionForegroundService.isBedrockConnected()) {
            putConnectCommandOnClipboard();
            toast(R.string.bridge_not_connected_test);
            return;
        }
        if (getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe") == null) {
            toast(R.string.minecraft_missing);
            return;
        }
        store.preferences().edit()
                .remove("last_bedrock_command_success")
                .remove("last_bedrock_command_response")
                .remove("last_bedrock_response_time")
                .apply();
        if (commandResultText != null) commandResultText.setVisibility(View.GONE);
        Intent test = new Intent(this, InteractionForegroundService.class)
                .setAction(InteractionForegroundService.ACTION_TEST)
                .putExtra(InteractionForegroundService.EXTRA_COMMAND, command);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(test); else startService(test);
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
        liveButton.setText(InteractionForegroundService.isLiveActive()
                ? R.string.stop_live : R.string.start_live);

        if (bridgeStatusText != null) {
            String bridgeState = store.preferences().getString("bedrock_bridge_status", "starting");
            String bridgeDetail = store.preferences().getString("bedrock_bridge_detail", "");
            if (InteractionForegroundService.isBedrockConnected()) {
                bridgeStatusText.setText(R.string.bridge_connected);
                bridgeStatusText.setTextColor(color(R.color.craft_green));
            } else if ("error".equals(bridgeState)) {
                bridgeStatusText.setText(getString(R.string.bridge_error, bridgeDetail));
                bridgeStatusText.setTextColor(color(R.color.craft_red));
            } else if ("listening".equals(bridgeState)) {
                String address = bridgeDetail == null || bridgeDetail.trim().isEmpty()
                        ? BedrockConnectionAddresses.preferredAddress() : bridgeDetail;
                bridgeStatusText.setText(getString(R.string.bridge_listening,
                        address, BedrockWebSocketServer.PORT));
                bridgeStatusText.setTextColor(color(R.color.craft_muted));
            } else {
                bridgeStatusText.setText(R.string.bridge_starting);
                bridgeStatusText.setTextColor(color(R.color.craft_muted));
            }
        }

        if (commandResultText != null) {
            long commandTime = store.preferences().getLong("last_command_time", 0L);
            long responseTime = store.preferences().getLong("last_bedrock_response_time", 0L);
            if (commandTime > 0L && responseTime >= commandTime) {
                boolean successful = store.preferences().getBoolean(
                        "last_bedrock_command_success", false);
                String response = store.preferences().getString(
                        "last_bedrock_command_response", "");
                commandResultText.setText(successful
                        ? getString(R.string.command_accepted)
                        : getString(R.string.command_rejected, response));
                commandResultText.setTextColor(color(successful
                        ? R.color.craft_green : R.color.craft_red));
                commandResultText.setVisibility(View.VISIBLE);
            } else {
                commandResultText.setVisibility(View.GONE);
            }
        }
        double hours = store.getVerifiedLiveMillis() / 3_600_000d;
        if (store.isPlusUnlocked()) {
            plusProgress.setText(R.string.plus_unlocked);
        } else {
            plusProgress.setText(getString(R.string.plus_locked, hours));
        }
        if (!store.isPlusUnlocked() && showingPlus) switchTab(false);
    }

    private void ensureBridgeService() {
        Intent bridge = new Intent(this, InteractionForegroundService.class)
                .setAction(InteractionForegroundService.ACTION_START_BRIDGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(bridge);
        } else {
            startService(bridge);
        }
        handler.postDelayed(this::refreshStatus, 350L);
    }

    private void copyConnectCommand() {
        List<String> addresses = BedrockConnectionAddresses.addresses();
        if (addresses.size() <= 1) {
            putConnectCommandOnClipboard(preferredConnectCommand());
            toast(R.string.connect_command_copied);
            return;
        }
        String[] commands = new String[addresses.size()];
        for (int index = 0; index < addresses.size(); index++) {
            commands[index] = BedrockConnectionAddresses.command(addresses.get(index));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.bridge_address_picker_title)
                .setItems(commands, (dialog, which) -> {
                    putConnectCommandOnClipboard(commands[which]);
                    toast(R.string.connect_command_copied);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void putConnectCommandOnClipboard() {
        putConnectCommandOnClipboard(preferredConnectCommand());
    }

    private void putConnectCommandOnClipboard(String command) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.app_name), command));
        }
    }

    private String preferredConnectCommand() {
        return BedrockConnectionAddresses.command(BedrockConnectionAddresses.preferredAddress());
    }

    private String giftButtonLabel(String giftName) {
        for (GiftCatalogItem item : giftCatalog.all()) {
            if (item.name.equalsIgnoreCase(giftName)) {
                String icon = item.fallbackIcon.isEmpty() ? "🎁" : item.fallbackIcon;
                return icon + "  " + item.name + (item.diamondCost > 0
                        ? "  ·  " + item.diamondCost + " ♦" : "");
            }
        }
        return "🎁  " + giftName;
    }

    private void showGiftPicker(String current, Consumer<String> selected) {
        LinearLayout list = vertical();
        list.setPadding(dp(8), dp(6), dp(8), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.gift_select)
                .setNegativeButton(R.string.cancel, null)
                .create();
        for (GiftCatalogItem item : giftCatalog.all()) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(panelBackground(item.name.equalsIgnoreCase(current)
                            ? R.color.craft_panel_alt : R.color.craft_panel,
                    1, R.color.craft_green_dark));

            if (item.imageUrl.isEmpty()) {
                TextView icon = text(item.fallbackIcon.isEmpty() ? "🎁" : item.fallbackIcon,
                        28f, R.color.craft_text, false);
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
            } else {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(image, new LinearLayout.LayoutParams(dp(54), dp(54)));
                GiftImageLoader.load(item.imageUrl, image);
            }

            LinearLayout labels = vertical();
            labels.setPadding(dp(12), 0, 0, 0);
            labels.addView(text(item.name, 17f, R.color.craft_text, true));
            labels.addView(text(item.diamondCost > 0
                            ? getString(R.string.gift_cost, item.diamondCost)
                            : getString(R.string.gift_cost_unknown),
                    15f, R.color.craft_muted, false));
            row.addView(labels, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                selected.accept(item.name);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.setMargins(0, 0, 0, dp(5));
            list.addView(row, rowParams);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list, matchWrap());
        dialog.setView(scroll);
        dialog.show();
    }

    private AdapterView.OnItemSelectedListener simpleSelection(Runnable callback) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                callback.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
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
