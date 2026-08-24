package hu.craftlive.android;

import org.json.JSONException;
import org.json.JSONObject;

public final class GiftCatalogItem {
    public final int id;
    public final String name;
    public final int diamondCost;
    public final String imageUrl;
    public final String fallbackIcon;

    public GiftCatalogItem(int id, String name, int diamondCost, String imageUrl,
                           String fallbackIcon) {
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.diamondCost = Math.max(0, diamondCost);
        this.imageUrl = imageUrl == null ? "" : imageUrl.trim();
        this.fallbackIcon = fallbackIcon == null || fallbackIcon.isEmpty() ? "🎁" : fallbackIcon;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("diamondCost", diamondCost);
        json.put("imageUrl", imageUrl);
        json.put("fallbackIcon", fallbackIcon);
        return json;
    }

    static GiftCatalogItem fromJson(JSONObject json) {
        return new GiftCatalogItem(
                json.optInt("id", -1),
                json.optString("name", ""),
                json.optInt("diamondCost", 0),
                json.optString("imageUrl", ""),
                json.optString("fallbackIcon", "🎁"));
    }
}
