package hu.craftlive.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transparent, in-memory viewer layer used only by the outgoing broadcast encoder.
 * It is deliberately not attached to an Android Window, so the player never sees it.
 */
final class StreamInteractionOverlay {
    private static final int MAX_ROWS = 8;
    private static final int GREEN = Color.rgb(132, 255, 32);
    private static final int PANEL = Color.argb(232, 3, 33, 23);
    private static final int ROW = Color.argb(214, 9, 49, 34);
    private static final int WHITE = Color.rgb(245, 250, 247);
    private static final int MUTED = Color.rgb(174, 200, 187);

    private StreamInteractionOverlay() {
    }

    static Bitmap render(Context context, InteractionStore store, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.TRANSPARENT);

        List<InteractionSlot> slots = store.loadEnabled();
        GiftCatalogStore gifts = new GiftCatalogStore(context);
        boolean hu = "hu".equals(Locale.getDefault().getLanguage());

        float margin = Math.max(18f, width * 0.018f);
        float panelWidth = Math.min(width * 0.285f, 360f);
        float left = width - panelWidth - margin;
        float top = margin;
        float right = width - margin;
        float bottom = height - margin;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor(PANEL);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(GREEN);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);

        float inset = 18f;
        drawText(canvas, paint, "CraftLive LIVE", left + inset, top + 36f,
                25f, GREEN, true);
        drawText(canvas, paint, hu ? "nézői lehetőségek" : "viewer interactions",
                left + inset, top + 62f, 16f, MUTED, false);

        if (slots.isEmpty()) {
            drawText(canvas, paint, hu ? "Nincs aktív interakció" : "No enabled interaction",
                    left + inset, top + 118f, 18f, WHITE, true);
            return output;
        }

        int visible = Math.min(MAX_ROWS, slots.size());
        float available = bottom - (top + 82f) - 48f;
        float rowHeight = Math.min(70f, available / visible);
        float rowTop = top + 82f;
        for (int index = 0; index < visible; index++) {
            InteractionSlot slot = slots.get(index);
            float rowBottom = rowTop + rowHeight - 6f;
            paint.setColor(ROW);
            canvas.drawRoundRect(new RectF(left + 10f, rowTop, right - 10f, rowBottom),
                    15f, 15f, paint);
            drawTrigger(canvas, paint, context, gifts, slot,
                    left + 22f, rowTop + 8f, rowHeight - 22f);
            drawFittedText(canvas, paint, "→ " + actionLabel(slot, hu),
                    left + 88f, rowTop + (rowHeight * 0.58f),
                    Math.max(90f, right - left - 112f), 19f, WHITE, true);
            rowTop += rowHeight;
        }

        if (slots.size() > visible) {
            drawText(canvas, paint,
                    hu ? "+" + (slots.size() - visible) + " további"
                            : "+" + (slots.size() - visible) + " more",
                    left + inset, bottom - 18f, 15f, MUTED, false);
        }
        return output;
    }

    static void preloadGiftArtwork(Context context, InteractionStore store) {
        GiftCatalogStore gifts = new GiftCatalogStore(context);
        ArrayList<String> urls = new ArrayList<>();
        for (InteractionSlot slot : store.loadEnabled()) {
            if (slot.triggerType != InteractionSlot.TriggerType.GIFT) continue;
            GiftCatalogItem gift = gifts.findByName(slot.triggerKey);
            if (gift != null && !gift.imageUrl.isEmpty()) urls.add(gift.imageUrl);
        }
        GiftImageLoader.preloadBlocking(context, urls);
    }

    private static void drawTrigger(Canvas canvas, Paint paint, Context context,
                                    GiftCatalogStore gifts, InteractionSlot slot,
                                    float left, float top, float size) {
        if (slot.triggerType == InteractionSlot.TriggerType.GIFT) {
            GiftCatalogItem gift = gifts.findByName(slot.triggerKey);
            Bitmap art = gift == null ? null : GiftImageLoader.peek(gift.imageUrl);
            if (art != null) {
                float ratio = Math.min(size / art.getWidth(), size / art.getHeight());
                float drawWidth = art.getWidth() * ratio;
                float drawHeight = art.getHeight() * ratio;
                Rect source = new Rect(0, 0, art.getWidth(), art.getHeight());
                RectF target = new RectF(left + (size - drawWidth) / 2f,
                        top + (size - drawHeight) / 2f,
                        left + (size + drawWidth) / 2f,
                        top + (size + drawHeight) / 2f);
                canvas.drawBitmap(art, source, target, paint);
                return;
            }
            String fallback = gift == null ? "🎁" : gift.fallbackIcon;
            drawCenteredText(canvas, paint, fallback, left, top, size, 30f, WHITE);
            return;
        }

        String icon = switch (slot.triggerType) {
            case LIKE -> "👍";
            case FOLLOW -> "➕";
            case SUBSCRIBE -> "⭐";
            case SHARE -> "↗";
            case COMMENT -> "💬";
            default -> "•";
        };
        drawCenteredText(canvas, paint, icon, left, top, size, 29f, WHITE);
        if (slot.triggerType == InteractionSlot.TriggerType.LIKE && slot.threshold > 1) {
            String threshold = String.valueOf(slot.threshold);
            drawText(canvas, paint, threshold, left + size - 4f, top + size - 2f,
                    12f, GREEN, true);
        }
    }

    static String actionLabel(InteractionSlot slot, boolean hungarian) {
        int action = BedrockActionCatalog.detect(slot.command);
        if (action == BedrockActionCatalog.SPAWN_MOB) {
            String id = BedrockActionCatalog.mobId(slot.command);
            for (BedrockMobCatalog.Item item : BedrockMobCatalog.all()) {
                if (item.id.equalsIgnoreCase(id)) {
                    String label = item.label();
                    int separator = label.indexOf("  ·  ");
                    return separator > 0 ? label.substring(0, separator) : label;
                }
            }
            return id.replace('_', ' ');
        }
        return switch (action) {
            case BedrockActionCatalog.LIGHTNING -> hungarian ? "villám" : "lightning";
            case BedrockActionCatalog.GIVE_DIAMOND -> hungarian ? "3 gyémánt" : "3 diamonds";
            case BedrockActionCatalog.HEAL -> hungarian ? "gyógyítás" : "healing";
            case BedrockActionCatalog.DAY -> hungarian ? "nappal" : "daytime";
            case BedrockActionCatalog.THUNDER -> hungarian ? "vihar" : "thunderstorm";
            default -> customActionLabel(slot, hungarian);
        };
    }

    private static String customActionLabel(InteractionSlot slot, boolean hungarian) {
        String name = slot.name == null ? "" : slot.name.trim();
        int arrow = name.lastIndexOf('→');
        if (arrow >= 0 && arrow + 1 < name.length()) return name.substring(arrow + 1).trim();
        int ascii = name.lastIndexOf("->");
        if (ascii >= 0 && ascii + 2 < name.length()) return name.substring(ascii + 2).trim();
        return hungarian ? "egyéni esemény" : "custom event";
    }

    private static void drawCenteredText(Canvas canvas, Paint paint, String value,
                                         float left, float top, float size,
                                         float textSize, int color) {
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = top + (size - metrics.bottom - metrics.top) / 2f;
        canvas.drawText(value, left + size / 2f, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawFittedText(Canvas canvas, Paint paint, String value,
                                       float x, float baseline, float maxWidth,
                                       float textSize, int color, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(textSize);
        while (paint.measureText(value) > maxWidth && paint.getTextSize() > 13f) {
            paint.setTextSize(paint.getTextSize() - 1f);
        }
        if (paint.measureText(value) > maxWidth && value.length() > 4) {
            String shortened = value;
            while (shortened.length() > 4
                    && paint.measureText(shortened + "…") > maxWidth) {
                shortened = shortened.substring(0, shortened.length() - 1);
            }
            value = shortened + "…";
        }
        paint.setColor(color);
        canvas.drawText(value, x, baseline, paint);
    }

    private static void drawText(Canvas canvas, Paint paint, String value,
                                 float x, float baseline, float size,
                                 int color, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(color);
        canvas.drawText(value, x, baseline, paint);
    }
}
