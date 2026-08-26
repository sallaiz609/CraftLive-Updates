package hu.craftlive.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Creates a shareable portrait PNG from the interactions enabled by the streamer. */
public final class LiveInteractionPoster {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int BACKGROUND = Color.rgb(2, 22, 13);
    private static final int PANEL = Color.rgb(9, 48, 31);
    private static final int PANEL_BORDER = Color.rgb(54, 118, 32);
    private static final int GREEN = Color.rgb(132, 255, 30);
    private static final int TEXT = Color.rgb(240, 248, 242);
    private static final int MUTED = Color.rgb(178, 201, 187);

    private LiveInteractionPoster() {
    }

    public static Result generate(Context context, InteractionStore store, String username)
            throws IOException {
        List<InteractionSlot> enabled = store.loadEnabled();
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BACKGROUND);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(GREEN);
        paint.setTextSize(76f);
        canvas.drawText("CraftLive LIVE", 56f, 116f, paint);

        paint.setColor(TEXT);
        paint.setTextSize(42f);
        String safeUser = username == null ? "" : username.trim().replace("@", "");
        canvas.drawText(safeUser.isEmpty() ? "TikTok LIVE" : "@" + safeUser, 58f, 178f, paint);

        boolean hungarian = "hu".equals(Locale.getDefault().getLanguage());
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setColor(MUTED);
        paint.setTextSize(34f);
        canvas.drawText(hungarian ? "Küldd el, és változtasd meg a Minecraftot!"
                : "Send one and change the Minecraft world!", 58f, 236f, paint);

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(GREEN);
        paint.setTextSize(34f);
        canvas.drawText(hungarian ? "AKTÍV INTERAKCIÓK" : "ACTIVE INTERACTIONS",
                58f, 304f, paint);

        int columns = enabled.size() > 18 ? 3 : 2;
        int gap = 18;
        int left = 56;
        int top = 338;
        int bottom = 1750;
        int rows = Math.max(1, (enabled.size() + columns - 1) / columns);
        int cardWidth = (WIDTH - left * 2 - gap * (columns - 1)) / columns;
        int cardHeight = Math.max(96, (bottom - top - gap * (rows - 1)) / rows);

        if (enabled.isEmpty()) {
            drawEmpty(canvas, paint, hungarian, left, top, WIDTH - left * 2, 250);
        } else {
            for (int index = 0; index < enabled.size(); index++) {
                int row = index / columns;
                int column = index % columns;
                float x = left + column * (cardWidth + gap);
                float y = top + row * (cardHeight + gap);
                drawCard(canvas, paint, enabled.get(index), hungarian,
                        x, y, cardWidth, cardHeight, columns);
            }
        }

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(GREEN);
        paint.setTextSize(31f);
        canvas.drawText(hungarian ? "CraftLive · 5 másodperces biztonsági sor"
                : "CraftLive · fixed 5-second safety queue", 56f, 1820f, paint);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setColor(MUTED);
        paint.setTextSize(27f);
        canvas.drawText(hungarian ? "Csak a bekapcsolt lehetőségek láthatók ezen a képen."
                : "This image only shows interactions currently enabled.", 56f, 1866f, paint);

        File base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) base = new File(context.getFilesDir(), "pictures");
        File directory = new File(base, "CraftLive");
        if (!directory.exists() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("Cannot create CraftLive picture folder");
        }
        String fileUser = safeUser.replaceAll("[^A-Za-z0-9._-]", "_");
        if (fileUser.isEmpty()) fileUser = "LIVE";
        File file = new File(directory, "CraftLive-LIVE-" + fileUser + ".png");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("PNG encoding failed");
            }
        } finally {
            bitmap.recycle();
        }
        return new Result(file, enabled.size());
    }

    private static void drawCard(Canvas canvas, Paint paint, InteractionSlot slot,
                                 boolean hungarian, float x, float y, int width, int height,
                                 int columns) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        RectF rect = new RectF(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, 26f, 26f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(PANEL_BORDER);
        canvas.drawRoundRect(rect, 26f, 26f, paint);
        paint.setStyle(Paint.Style.FILL);

        float horizontalPadding = columns == 3 ? 18f : 25f;
        float triggerSize = columns == 3 ? 22f : 27f;
        float actionSize = columns == 3 ? 27f : 34f;
        float triggerY = y + Math.min(43f, height * 0.38f);
        float actionY = y + Math.min(91f, height * 0.78f);

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(GREEN);
        paint.setTextSize(triggerSize);
        canvas.drawText(fit(trigger(slot, hungarian), columns == 3 ? 25 : 38),
                x + horizontalPadding, triggerY, paint);

        paint.setColor(TEXT);
        paint.setTextSize(actionSize);
        canvas.drawText(fit(action(slot), columns == 3 ? 21 : 31),
                x + horizontalPadding, actionY, paint);
    }

    private static void drawEmpty(Canvas canvas, Paint paint, boolean hungarian,
                                  int x, int y, int width, int height) {
        paint.setColor(PANEL);
        canvas.drawRoundRect(new RectF(x, y, x + width, y + height), 30f, 30f, paint);
        paint.setColor(TEXT);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextSize(40f);
        canvas.drawText(hungarian ? "Még nincs bekapcsolt interakció."
                : "No interactions are enabled yet.", x + 35f, y + 115f, paint);
    }

    private static String trigger(InteractionSlot slot, boolean hungarian) {
        return switch (slot.triggerType) {
            case GIFT -> (hungarian ? "AJÁNDÉK · " : "GIFT · ") + slot.triggerKey;
            case LIKE -> Math.max(1, slot.threshold) + (hungarian ? " LIKE" : " LIKES");
            case FOLLOW -> hungarian ? "KÖVETÉS" : "FOLLOW";
            case SUBSCRIBE -> hungarian ? "FELIRATKOZÁS" : "SUBSCRIPTION";
            case SHARE -> hungarian ? "MEGOSZTÁS" : "SHARE";
            case COMMENT -> (hungarian ? "KOMMENT · " : "COMMENT · ") + slot.triggerKey;
        };
    }

    private static String action(InteractionSlot slot) {
        String name = slot.name == null ? "" : slot.name.trim();
        int arrow = name.indexOf('→');
        if (arrow >= 0 && arrow + 1 < name.length()) return name.substring(arrow + 1).trim();
        if (!name.isEmpty()) return name;
        String command = slot.command == null ? "" : slot.command.trim();
        if (command.startsWith("/")) command = command.substring(1);
        return command.isEmpty() ? "Minecraft" : command;
    }

    private static String fit(String text, int limit) {
        String safe = text == null ? "" : text.replace('\n', ' ').trim();
        if (safe.length() <= limit) return safe;
        return safe.substring(0, Math.max(1, limit - 1)).trim() + "…";
    }

    public static final class Result {
        public final File file;
        public final int interactionCount;

        private Result(File file, int interactionCount) {
            this.file = file;
            this.interactionCount = interactionCount;
        }
    }
}
