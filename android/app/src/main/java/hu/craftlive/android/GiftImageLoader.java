package hu.craftlive.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GiftImageLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GiftImageLoader() {
    }

    static void load(String imageUrl, ImageView view) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        view.setTag(imageUrl);
        Bitmap cached = CACHE.get(imageUrl);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(7_000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        CACHE.put(imageUrl, bitmap);
                        MAIN.post(() -> {
                            if (imageUrl.equals(view.getTag())) view.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
