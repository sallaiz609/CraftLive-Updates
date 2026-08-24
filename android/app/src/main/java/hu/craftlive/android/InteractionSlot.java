package hu.craftlive.android;

import org.json.JSONException;
import org.json.JSONObject;

public final class InteractionSlot {
    public enum TriggerType {
        GIFT, LIKE, FOLLOW, SUBSCRIBE, SHARE, COMMENT
    }

    public final int index;
    public final boolean plus;
    public String name;
    public TriggerType triggerType;
    public String triggerKey;
    public int threshold;
    public String command;
    public boolean enabled;

    public InteractionSlot(int index, boolean plus) {
        this.index = index;
        this.plus = plus;
        this.name = "";
        this.triggerType = TriggerType.GIFT;
        this.triggerKey = "";
        this.threshold = 1;
        this.command = "";
        this.enabled = false;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("index", index);
        json.put("plus", plus);
        json.put("name", name);
        json.put("triggerType", triggerType.name());
        json.put("triggerKey", triggerKey);
        json.put("threshold", threshold);
        json.put("command", command);
        json.put("enabled", enabled);
        return json;
    }

    public static InteractionSlot fromJson(JSONObject json, int fallbackIndex, boolean plus) {
        InteractionSlot slot = new InteractionSlot(fallbackIndex, plus);
        slot.name = json.optString("name", "");
        try {
            slot.triggerType = TriggerType.valueOf(json.optString("triggerType", "GIFT"));
        } catch (IllegalArgumentException ignored) {
            slot.triggerType = TriggerType.GIFT;
        }
        slot.triggerKey = json.optString("triggerKey", "");
        slot.threshold = Math.max(1, json.optInt("threshold", 1));
        slot.command = json.optString("command", "");
        slot.enabled = json.optBoolean("enabled", false);
        return slot;
    }
}
