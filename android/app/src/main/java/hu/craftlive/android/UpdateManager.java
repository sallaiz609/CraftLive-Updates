package hu.craftlive.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/sallaiz609/CraftLive-Updates/main/android-latest.json";
    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UpdateInfo pendingPermissionInfo;

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void check(boolean userRequested) {
        if (userRequested) toast(R.string.update_checking);
        executor.execute(() -> {
            try {
                JSONObject manifest = new JSONObject(readUrl(MANIFEST_URL));
                int availableCode = manifest.getInt("versionCode");
                int currentCode = currentVersionCode();
                if (availableCode <= currentCode) {
                    activity.getSharedPreferences("craftlive_settings", Activity.MODE_PRIVATE)
                            .edit().remove("mandatory_update_code").apply();
                    if (userRequested) toast(R.string.update_none);
                    return;
                }
                String language = Locale.getDefault().getLanguage();
                String notes = manifest.optString("notesEn", "");
                if ("hu".equalsIgnoreCase(language)) notes = manifest.optString("notesHu", notes);
                UpdateInfo info = new UpdateInfo(
                        availableCode,
                        manifest.getString("versionName"),
                        manifest.getString("apkUrl"),
                        manifest.optString("sha256", ""),
                        notes,
                        manifest.optBoolean("mandatory", true));
                if (info.mandatory) {
                    activity.getSharedPreferences("craftlive_settings", Activity.MODE_PRIVATE)
                            .edit().putInt("mandatory_update_code", info.versionCode).apply();
                }
                activity.runOnUiThread(() -> showUpdate(info));
            } catch (Throwable error) {
                if (userRequested) toast(activity.getString(R.string.update_error, readable(error)));
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void resumePendingInstallIfAllowed() {
        if (pendingPermissionInfo == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) return;
        UpdateInfo info = pendingPermissionInfo;
        pendingPermissionInfo = null;
        downloadAndInstall(info);
    }

    public boolean hasMandatoryUpdate() {
        int pending = activity.getSharedPreferences("craftlive_settings", Activity.MODE_PRIVATE)
                .getInt("mandatory_update_code", 0);
        try {
            return pending > currentVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) {
            return pending > 0;
        }
    }

    private void showUpdate(UpdateInfo info) {
        String message = info.notes;
        if (info.mandatory) {
            message = message + "\n\n" + activity.getString(R.string.mandatory_update);
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available, info.versionName))
                .setMessage(message.trim())
                .setPositiveButton(R.string.update_download, (d, which) -> downloadAndInstall(info));
        if (!info.mandatory) dialog.setNegativeButton(R.string.update_later, null);
        AlertDialog built = dialog.create();
        built.setCancelable(!info.mandatory);
        built.setCanceledOnTouchOutside(!info.mandatory);
        built.show();
    }

    private void downloadAndInstall(UpdateInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingPermissionInfo = info;
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(permission);
            toast(R.string.update_permission);
            return;
        }
        toast(R.string.update_downloading);
        executor.execute(() -> {
            try {
                File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) throw new IllegalStateException("Download directory unavailable");
                File apk = new File(directory, "CraftLive-Android-update.apk");
                download(info.apkUrl, apk);
                if (!info.sha256.trim().isEmpty()) {
                    String actual = sha256(apk);
                    if (!actual.equalsIgnoreCase(info.sha256.trim())) {
                        if (!apk.delete()) apk.deleteOnExit();
                        throw new SecurityException("SHA-256 mismatch");
                    }
                }
                activity.runOnUiThread(() -> install(apk));
            } catch (Throwable error) {
                toast(activity.getString(R.string.update_error, readable(error)));
            }
        });
    }

    private void install(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".updates", apk);
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            toast(R.string.update_install);
        } catch (Throwable error) {
            toast(activity.getString(R.string.update_error,
                    readable(error) + " " + activity.getString(R.string.update_bad_signature)));
        }
    }

    private int currentVersionCode() throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return (int) activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
        }
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
    }

    private static String readUrl(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String url, File target) throws Exception {
        HttpURLConnection connection = open(url);
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "CraftLive-Android-Updater");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
        return connection;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private void toast(int resource) {
        toast(activity.getString(resource));
    }

    private void toast(String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_LONG).show());
    }

    private static String readable(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName() : message;
    }

    private static final class UpdateInfo {
        private final int versionCode;
        private final String versionName;
        private final String apkUrl;
        private final String sha256;
        private final String notes;
        private final boolean mandatory;

        private UpdateInfo(int versionCode, String versionName, String apkUrl,
                           String sha256, String notes, boolean mandatory) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.notes = notes;
            this.mandatory = mandatory;
        }
    }
}
