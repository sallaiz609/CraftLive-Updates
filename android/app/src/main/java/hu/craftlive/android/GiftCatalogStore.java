package hu.craftlive.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GiftCatalogStore {
    private static final String PREFS = "craftlive_gift_catalog";
    private static final String KEY_ITEMS = "items";
    private final SharedPreferences preferences;

    public GiftCatalogStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void merge(List<GiftCatalogItem> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        Map<String, GiftCatalogItem> merged = new LinkedHashMap<>();
        for (GiftCatalogItem item : loadSaved()) merged.put(key(item.name), item);
        for (GiftCatalogItem item : incoming) {
            if (item == null || item.name.isEmpty() || "undefined".equalsIgnoreCase(item.name)) continue;
            GiftCatalogItem old = merged.get(key(item.name));
            if (old == null || !item.imageUrl.isEmpty() || item.diamondCost > 0) {
                merged.put(key(item.name), item);
            }
        }
        JSONArray array = new JSONArray();
        for (GiftCatalogItem item : merged.values()) {
            try {
                array.put(item.toJson());
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public synchronized List<GiftCatalogItem> all() {
        Map<String, GiftCatalogItem> result = new LinkedHashMap<>();
        for (GiftCatalogItem item : builtIns()) result.put(key(item.name), item);
        for (GiftCatalogItem item : loadSaved()) result.put(key(item.name), item);
        ArrayList<GiftCatalogItem> sorted = new ArrayList<>(result.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        sorted.sort((left, right) -> {
            int cost = Integer.compare(left.diamondCost, right.diamondCost);
            return cost != 0 ? cost : collator.compare(left.name, right.name);
        });
        return sorted;
    }

    private List<GiftCatalogItem> loadSaved() {
        ArrayList<GiftCatalogItem> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_ITEMS, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                if (json != null) {
                    GiftCatalogItem item = GiftCatalogItem.fromJson(json);
                    if (!item.name.isEmpty()) result.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static List<GiftCatalogItem> builtIns() {
        ArrayList<GiftCatalogItem> items = new ArrayList<>();
        add(items, "Rose", 1, "🌹");
        add(items, "TikTok", 1, "🎵");
        add(items, "GG", 1, "🎮");
        add(items, "Ice Cream Cone", 1, "🍦");
        add(items, "Finger Heart", 5, "🫰");
        add(items, "Mic", 5, "🎤");
        add(items, "Panda", 5, "🐼");
        add(items, "Doughnut", 30, "🍩");
        add(items, "Love You", 49, "💚");
        add(items, "Paper Crane", 99, "🕊️");
        add(items, "Confetti", 100, "🎉");
        add(items, "Heart Me", 100, "💚");
        add(items, "Swan", 699, "🦢");
        add(items, "Train", 899, "🚂");
        add(items, "Galaxy", 1000, "🌌");
        add(items, "Sports Car", 7000, "🏎️");
        add(items, "Lion", 29999, "🦁");
        add(items, "TikTok Universe", 44999, "🌠");
        return items;
    }

    private static void add(List<GiftCatalogItem> items, String name, int cost, String icon) {
        items.add(new GiftCatalogItem(-1, name, cost, "", icon));
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
