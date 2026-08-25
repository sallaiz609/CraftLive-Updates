package hu.craftlive.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InteractionStore {
    public static final int STANDARD_SLOT_COUNT = 20;
    public static final int PLUS_SLOT_COUNT = 8;
    public static final long PLUS_UNLOCK_MILLIS = 5L * 60L * 60L * 1000L;
    public static final long FIXED_DELAY_MILLIS = 5_000L;

    private static final String PREFS = "craftlive_settings";
    private static final String KEY_STANDARD = "standard_slots";
    private static final String KEY_PLUS = "plus_slots";
    private static final String KEY_LIVE_MILLIS = "verified_live_millis";

    private final SharedPreferences preferences;

    public InteractionStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    public SharedPreferences preferences() {
        return preferences;
    }

    public List<InteractionSlot> loadStandard() {
        return load(KEY_STANDARD, STANDARD_SLOT_COUNT, false);
    }

    public List<InteractionSlot> loadPlus() {
        return load(KEY_PLUS, PLUS_SLOT_COUNT, true);
    }

    public List<InteractionSlot> loadEnabled() {
        ArrayList<InteractionSlot> result = new ArrayList<>();
        appendEnabled(result, loadStandard());
        if (isPlusUnlocked()) appendEnabled(result, loadPlus());
        return result;
    }

    public void saveStandard(List<InteractionSlot> slots) {
        save(KEY_STANDARD, slots);
    }

    public void savePlus(List<InteractionSlot> slots) {
        save(KEY_PLUS, slots);
    }

    public long getVerifiedLiveMillis() {
        return preferences.getLong(KEY_LIVE_MILLIS, 0L);
    }

    public void addVerifiedLiveMillis(long millis) {
        if (millis <= 0L) return;
        long next = Math.min(Long.MAX_VALUE, getVerifiedLiveMillis() + millis);
        preferences.edit().putLong(KEY_LIVE_MILLIS, next).apply();
    }

    public boolean isPlusUnlocked() {
        return getVerifiedLiveMillis() >= PLUS_UNLOCK_MILLIS;
    }

    public void clearTikTokUsernameForNewVersion(int currentVersionCode) {
        int lastVersion = preferences.getInt("username_version_code", -1);
        if (lastVersion != currentVersionCode) {
            preferences.edit()
                    .remove("tiktok_username")
                    .putInt("username_version_code", currentVersionCode)
                    .apply();
        }
    }

    public List<InteractionSlot> findMatches(InteractionSlot.TriggerType type, String key, int amount) {
        List<InteractionSlot> result = new ArrayList<>();
        appendMatches(result, loadStandard(), type, key, amount);
        if (isPlusUnlocked()) {
            appendMatches(result, loadPlus(), type, key, amount);
        }
        return result;
    }

    private void appendMatches(List<InteractionSlot> result, List<InteractionSlot> slots,
                               InteractionSlot.TriggerType type, String key, int amount) {
        String normalizedKey = normalize(key);
        for (InteractionSlot slot : slots) {
            if (!slot.enabled || slot.triggerType != type || slot.command.trim().isEmpty()) continue;
            if (type == InteractionSlot.TriggerType.LIKE && amount < slot.threshold) continue;
            if ((type == InteractionSlot.TriggerType.GIFT || type == InteractionSlot.TriggerType.COMMENT)
                    && !slot.triggerKey.trim().isEmpty()
                    && !normalize(slot.triggerKey).equals(normalizedKey)) continue;
            result.add(slot);
        }
    }

    private static void appendEnabled(List<InteractionSlot> result, List<InteractionSlot> slots) {
        for (InteractionSlot slot : slots) {
            if (slot.enabled && slot.command != null && !slot.command.trim().isEmpty()) {
                result.add(slot);
            }
        }
    }

    private static String normalize(String value) {
        String safe = value == null ? "" : value;
        String withoutMarks = Normalizer.normalize(safe, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return withoutMarks.trim().toLowerCase(Locale.ROOT);
    }

    private List<InteractionSlot> load(String key, int count, boolean plus) {
        ArrayList<InteractionSlot> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key, "[]"));
            for (int i = 0; i < count; i++) {
                JSONObject json = i < array.length() ? array.optJSONObject(i) : null;
                result.add(json == null ? new InteractionSlot(i, plus) : InteractionSlot.fromJson(json, i, plus));
            }
        } catch (JSONException ignored) {
            for (int i = 0; i < count; i++) result.add(new InteractionSlot(i, plus));
        }
        return result;
    }

    private void save(String key, List<InteractionSlot> slots) {
        JSONArray array = new JSONArray();
        for (InteractionSlot slot : slots) {
            try {
                array.put(slot.toJson());
            } catch (JSONException ignored) {
                // A slot kizárólag egyszerű szöveges és számos adatot tartalmaz.
            }
        }
        preferences.edit().putString(key, array.toString()).apply();
    }

    private void ensureDefaults() {
        if (!preferences.contains(KEY_STANDARD)) saveStandard(defaultStandard());
        if (!preferences.contains(KEY_PLUS)) savePlus(defaultPlus());
    }

    private static List<InteractionSlot> defaultStandard() {
        ArrayList<InteractionSlot> slots = blank(STANDARD_SLOT_COUNT, false);
        configure(slots.get(0), "Rózsa → zombi", InteractionSlot.TriggerType.GIFT,
                "Rose", 1, "/summon zombie ~ ~1 ~", true);
        configure(slots.get(1), "100 like → villám", InteractionSlot.TriggerType.LIKE,
                "", 100, "/summon lightning_bolt ~ ~ ~", true);
        configure(slots.get(2), "Követés → gyógyítás", InteractionSlot.TriggerType.FOLLOW,
                "", 1, "/effect @p regeneration 10 1 true", true);
        configure(slots.get(3), "Feliratkozás → gyémánt", InteractionSlot.TriggerType.SUBSCRIBE,
                "", 1, "/give @p diamond 3", true);
        configure(slots.get(4), "Megosztás → nappal", InteractionSlot.TriggerType.SHARE,
                "", 1, "/time set day", false);
        return slots;
    }

    private static List<InteractionSlot> defaultPlus() {
        ArrayList<InteractionSlot> slots = blank(PLUS_SLOT_COUNT, true);
        configure(slots.get(0), "Galaxy → TNT-kamra", InteractionSlot.TriggerType.GIFT,
                "Galaxy", 1, "/fill ~-6 ~-1 ~-6 ~6 ~8 ~6 tnt;;/summon lightning_bolt ~ ~1 ~", true);
        configure(slots.get(1), "Universe → üres kráter", InteractionSlot.TriggerType.GIFT,
                "Universe", 1, "/fill ~-8 ~-8 ~-8 ~8 ~8 ~8 air destroy", true);
        configure(slots.get(2), "Lion → Wither", InteractionSlot.TriggerType.GIFT,
                "Lion", 1, "/summon wither ~ ~3 ~", true);
        configure(slots.get(3), "TikTok Universe → végzet", InteractionSlot.TriggerType.GIFT,
                "TikTok Universe", 1, "/weather thunder 99999;;/summon lightning_bolt ~ ~ ~", true);
        return slots;
    }

    private static ArrayList<InteractionSlot> blank(int count, boolean plus) {
        ArrayList<InteractionSlot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) slots.add(new InteractionSlot(i, plus));
        return slots;
    }

    private static void configure(InteractionSlot slot, String name, InteractionSlot.TriggerType type,
                                  String key, int threshold, String command, boolean enabled) {
        slot.name = name;
        slot.triggerType = type;
        slot.triggerKey = key;
        slot.threshold = threshold;
        slot.command = command;
        slot.enabled = enabled;
    }
}
