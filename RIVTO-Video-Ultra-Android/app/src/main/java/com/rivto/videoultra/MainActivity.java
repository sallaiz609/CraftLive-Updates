package com.rivto.videoultra;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private Button pickVideoButton;
    private Button naturalButton;
    private Button ultraButton;
    private Button brutalButton;
    private Button exportButton;
    private Button cancelButton;
    private Button shareButton;
    private Switch ultraSwitch;
    private Switch asphaltSwitch;
    private SeekBar strengthSeekBar;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView strengthText;
    private TextView videoInfoText;
    private TextView statusText;

    private ExoPlayer player;
    private Transformer transformer;
    private Uri selectedVideoUri;
    private Uri lastExportUri;
    private File currentTempOutput;

    private boolean safeFallbackMode = false;
    private boolean exportRetryUsed = false;
    private boolean exporting = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ProgressHolder progressHolder = new ProgressHolder();

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (transformer == null || !exporting) {
                return;
            }
            try {
                int state = transformer.getProgress(progressHolder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    int value = Math.max(0, Math.min(100, progressHolder.progress));
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(value);
                    progressText.setText(getString(R.string.progress_percent, value));
                } else {
                    progressBar.setIndeterminate(true);
                    progressText.setText(R.string.preparing_export);
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 400);
                }
            } catch (IllegalStateException ignored) {
                // Transformer már befejeződhetett a két UI-frissítés között.
            }
        }
    };

    private final ActivityResultLauncher<String[]> videoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Néhány dokumentumszolgáltató csak az aktuális munkamenetre ad engedélyt.
                }

                selectedVideoUri = uri;
                lastExportUri = null;
                shareButton.setEnabled(false);
                safeFallbackMode = false;
                exportRetryUsed = false;
                updateVideoInfo(uri);
                loadPreview(uri);
                exportButton.setEnabled(true);
                statusText.setText(R.string.ready);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.playerView);
        pickVideoButton = findViewById(R.id.pickVideoButton);
        naturalButton = findViewById(R.id.naturalButton);
        ultraButton = findViewById(R.id.ultraButton);
        brutalButton = findViewById(R.id.brutalButton);
        exportButton = findViewById(R.id.exportButton);
        cancelButton = findViewById(R.id.cancelButton);
        shareButton = findViewById(R.id.shareButton);
        ultraSwitch = findViewById(R.id.ultraSwitch);
        asphaltSwitch = findViewById(R.id.asphaltSwitch);
        strengthSeekBar = findViewById(R.id.strengthSeekBar);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        strengthText = findViewById(R.id.strengthText);
        videoInfoText = findViewById(R.id.videoInfoText);
        statusText = findViewById(R.id.statusText);

        pickVideoButton.setOnClickListener(v -> videoPicker.launch(new String[]{"video/*"}));

        ultraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            safeFallbackMode = false;
            applyPreviewEffects();
            updateStrengthLabel();
        });

        asphaltSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            safeFallbackMode = false;
            applyPreviewEffects();
        });

        strengthSeekBar.setProgress(88);
        strengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateStrengthLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                safeFallbackMode = false;
                applyPreviewEffects();
            }
        });

        naturalButton.setOnClickListener(v -> setStrengthPreset(50));
        ultraButton.setOnClickListener(v -> setStrengthPreset(88));
        brutalButton.setOnClickListener(v -> setStrengthPreset(100));

        exportButton.setOnClickListener(v -> startExport(false));
        cancelButton.setOnClickListener(v -> cancelExport());
        shareButton.setOnClickListener(v -> shareLastExport());

        updateStrengthLabel();
    }

    private void setStrengthPreset(int strength) {
        strengthSeekBar.setProgress(strength);
        ultraSwitch.setChecked(true);
        safeFallbackMode = false;
        applyPreviewEffects();
    }

    private int strengthPercent() {
        return strengthSeekBar.getProgress();
    }

    private float strength01() {
        return strengthPercent() / 100.0f;
    }

    private void updateStrengthLabel() {
        if (!ultraSwitch.isChecked()) {
            strengthText.setText(R.string.effect_off);
            return;
        }
        strengthText.setText(getString(R.string.strength_value, strengthPercent()));
    }

    private List<Effect> currentEffects() {
        if (safeFallbackMode) {
            return UltraPreset.buildFallback(ultraSwitch.isChecked(), strength01());
        }
        return UltraPreset.build(
                ultraSwitch.isChecked(), strength01(), asphaltSwitch.isChecked());
    }

    private void loadPreview(Uri uri) {
        releasePlayer();

        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (ultraSwitch.isChecked() && !safeFallbackMode) {
                    safeFallbackMode = true;
                    Toast.makeText(
                            MainActivity.this,
                            R.string.shader_fallback,
                            Toast.LENGTH_LONG).show();
                    loadPreview(selectedVideoUri);
                } else {
                    statusText.setText(getString(R.string.preview_error, error.getMessage()));
                }
            }
        });
        playerView.setPlayer(player);

        player.setVideoEffects(currentEffects());
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
    }

    private void applyPreviewEffects() {
        if (player == null) {
            return;
        }
        try {
            player.setVideoEffects(currentEffects());
        } catch (RuntimeException e) {
            if (!safeFallbackMode) {
                safeFallbackMode = true;
                player.setVideoEffects(currentEffects());
                Toast.makeText(this, R.string.shader_fallback, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateVideoInfo(Uri uri) {
        String name = "Videó";
        long size = -1L;
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (RuntimeException ignored) {}

        String resolution = "?";
        String duration = "?";
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (width != null && height != null) {
                resolution = width + "×" + height;
            }
            if (durationMs != null) {
                long ms = Long.parseLong(durationMs);
                long seconds = ms / 1000L;
                duration = String.format(Locale.getDefault(), "%d:%02d", seconds / 60L, seconds % 60L);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {}
        }

        String sizeText = size >= 0
                ? String.format(Locale.getDefault(), "%.1f MB", size / 1024.0 / 1024.0)
                : "?";
        videoInfoText.setText(getString(R.string.video_info, name, resolution, duration, sizeText));
    }

    private void startExport(boolean retryingWithFallback) {
        if (selectedVideoUri == null || transformer != null || exporting) {
            return;
        }

        if (!retryingWithFallback) {
            exportRetryUsed = false;
        }

        exporting = true;
        setControlsForExport(true);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText(R.string.preparing_export);
        statusText.setText(safeFallbackMode ? R.string.exporting_fallback : R.string.exporting);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        File outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (outDir == null) {
            failExport(getString(R.string.temp_folder_error));
            return;
        }

        currentTempOutput = new File(outDir, "RIVTO_export_" + System.currentTimeMillis() + ".mp4");
        if (currentTempOutput.exists() && !currentTempOutput.delete()) {
            failExport(getString(R.string.temp_prepare_error));
            return;
        }

        MediaItem input = MediaItem.fromUri(selectedVideoUri);
        Effects effects = new Effects(Collections.emptyList(), currentEffects());
        EditedMediaItem edited = new EditedMediaItem.Builder(input).setEffects(effects).build();

        transformer = new Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        transformer = null;
                        exporting = false;
                        mainHandler.removeCallbacks(progressRunnable);
                        onExportCompleted(exportResult);
                    }

                    @Override
                    public void onError(
                            Composition composition,
                            ExportResult exportResult,
                            ExportException exportException) {
                        transformer = null;
                        exporting = false;
                        mainHandler.removeCallbacks(progressRunnable);

                        if (ultraSwitch.isChecked() && !safeFallbackMode && !exportRetryUsed) {
                            exportRetryUsed = true;
                            safeFallbackMode = true;
                            deleteTempOutput();
                            Toast.makeText(
                                    MainActivity.this,
                                    R.string.export_retry_fallback,
                                    Toast.LENGTH_LONG).show();
                            mainHandler.postDelayed(() -> startExport(true), 300);
                            return;
                        }

                        failExport(getString(R.string.export_error, exportException.getMessage()));
                    }
                })
                .build();

        transformer.start(edited, currentTempOutput.getAbsolutePath());
        mainHandler.removeCallbacks(progressRunnable);
        mainHandler.post(progressRunnable);
    }

    private void cancelExport() {
        if (transformer != null) {
            try {
                transformer.cancel();
            } catch (IllegalStateException ignored) {}
            transformer = null;
        }
        exporting = false;
        mainHandler.removeCallbacks(progressRunnable);
        deleteTempOutput();
        statusText.setText(R.string.export_cancelled);
        resetControlsAfterExport();
    }

    private void onExportCompleted(ExportResult exportResult) {
        try {
            lastExportUri = copyToGallery(currentTempOutput);
            shareButton.setEnabled(lastExportUri != null);
            long bytes = exportResult.fileSizeBytes;
            String sizeText = bytes > 0
                    ? String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
                    : "";
            statusText.setText(getString(R.string.export_complete, sizeText));
            progressBar.setIndeterminate(false);
            progressBar.setProgress(100);
            progressText.setText(getString(R.string.progress_percent, 100));
            Toast.makeText(this, R.string.export_done_toast, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            statusText.setText(getString(R.string.gallery_save_error, e.getMessage()));
        } finally {
            deleteTempOutput();
            resetControlsAfterExport();
        }
    }

    private Uri copyToGallery(File source) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "RIVTO_ULTRA_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RIVTO");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("A MediaStore nem adott célfájlt.");
        }

        boolean success = false;
        try (FileInputStream in = new FileInputStream(source);
             OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) {
                throw new IOException("Nem nyitható meg a célfájl.");
            }
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            success = true;
        } finally {
            if (!success) {
                resolver.delete(uri, null, null);
            }
        }

        values.clear();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    private void shareLastExport() {
        if (lastExportUri == null) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("video/mp4");
        share.putExtra(Intent.EXTRA_STREAM, lastExportUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.share_title)));
    }

    private void failExport(String message) {
        transformer = null;
        exporting = false;
        mainHandler.removeCallbacks(progressRunnable);
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        deleteTempOutput();
        resetControlsAfterExport();
    }

    private void setControlsForExport(boolean exportRunning) {
        exportButton.setEnabled(!exportRunning && selectedVideoUri != null);
        pickVideoButton.setEnabled(!exportRunning);
        ultraSwitch.setEnabled(!exportRunning);
        asphaltSwitch.setEnabled(!exportRunning);
        strengthSeekBar.setEnabled(!exportRunning);
        naturalButton.setEnabled(!exportRunning);
        ultraButton.setEnabled(!exportRunning);
        brutalButton.setEnabled(!exportRunning);
        cancelButton.setVisibility(exportRunning ? View.VISIBLE : View.GONE);
        shareButton.setEnabled(!exportRunning && lastExportUri != null);
    }

    private void resetControlsAfterExport() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setControlsForExport(false);
        cancelButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
    }

    private void deleteTempOutput() {
        if (currentTempOutput != null && currentTempOutput.exists()) {
            //noinspection ResultOfMethodCallIgnored
            currentTempOutput.delete();
        }
        currentTempOutput = null;
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onPause() {
        if (player != null) {
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && selectedVideoUri != null && !exporting) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (transformer != null) {
            try {
                transformer.cancel();
            } catch (IllegalStateException ignored) {}
            transformer = null;
        }
        exporting = false;
        deleteTempOutput();
        releasePlayer();
        super.onDestroy();
    }
}
