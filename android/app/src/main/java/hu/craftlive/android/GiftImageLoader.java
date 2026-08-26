package hu.craftlive.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
            Bitmap bitmap = loadBlocking(view.getContext(), imageUrl);
            if (bitmap != null) {
                MAIN.post(() -> {
                    if (imageUrl.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            }
        });
    }

    static Bitmap loadBlocking(Context context, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return null;
        Bitmap cached = CACHE.get(imageUrl);
        if (cached != null) return cached;
        if (context == null) return null;

        File directory = new File(context.getCacheDir(), "gift-artwork");
        File cacheFile = new File(directory,
                Integer.toHexString(imageUrl.hashCode()) + "-" + imageUrl.length() + ".png");
        if (cacheFile.isFile()) {
            Bitmap disk = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (disk != null) {
                CACHE.put(imageUrl, disk);
                return disk;
            }
            //noinspection ResultOfMethodCallIgnored
            cacheFile.delete();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(6_000);
            connection.setInstanceFollowRedirects(true);
            try (InputStream stream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap == null) return null;
                CACHE.put(imageUrl, bitmap);
                if ((directory.isDirectory() || directory.mkdirs())) {
                    try (FileOutputStream output = new FileOutputStream(cacheFile, false)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    } catch (Exception ignored) {
                    }
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Bitmap peek(String imageUrl) {
        return imageUrl == null ? null : CACHE.get(imageUrl);
    }

    /** Downloads the small set used by the poster in parallel, with one overall wait limit. */
    static void preloadBlocking(Context context, List<String> imageUrls) {
        Set<String> unique = new LinkedHashSet<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.trim().isEmpty() && CACHE.get(url) == null) unique.add(url);
            }
        }
        if (unique.isEmpty()) return;
        CountDownLatch latch = new CountDownLatch(unique.size());
        for (String url : unique) {
            EXECUTOR.execute(() -> {
                try {
                    loadBlocking(context, url);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(12L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
