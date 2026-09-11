package com.rivto.videoultra;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private Button pickVideoButton;
    private Button exportButton;
    private Switch ultraSwitch;
    private ProgressBar progressBar;
    private TextView statusText;

    private ExoPlayer player;
    private Transformer transformer;
    private Uri selectedVideoUri;
    private File currentTempOutput;

    private final ActivityResultLauncher<String> videoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                selectedVideoUri = uri;
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
        exportButton = findViewById(R.id.exportButton);
        ultraSwitch = findViewById(R.id.ultraSwitch);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        pickVideoButton.setOnClickListener(v -> videoPicker.launch("video/*"));

        ultraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (player != null) {
                applyPreviewEffects();
            }
        });

        exportButton.setOnClickListener(v -> startExport());
    }

    private List<Effect> currentEffects() {
        return UltraPreset.build(ultraSwitch.isChecked());
    }

    private void loadPreview(Uri uri) {
        releasePlayer();

        player = new ExoPlayer.Builder(this).build();
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
        player.setVideoEffects(currentEffects());
    }

    private void startExport() {
        if (selectedVideoUri == null || transformer != null) {
            return;
        }

        exportButton.setEnabled(false);
        pickVideoButton.setEnabled(false);
        ultraSwitch.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText(R.string.exporting);

        File outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (outDir == null) {
            failExport("Nem elérhető az alkalmazás videó mappája.");
            return;
        }

        currentTempOutput = new File(outDir, "RIVTO_export_" + System.currentTimeMillis() + ".mp4");
        if (currentTempOutput.exists() && !currentTempOutput.delete()) {
            failExport("Nem sikerült előkészíteni a kimeneti fájlt.");
            return;
        }

        MediaItem input = MediaItem.fromUri(selectedVideoUri);
        Effects effects = new Effects(Collections.emptyList(), currentEffects());
        EditedMediaItem edited =
                new EditedMediaItem.Builder(input)
                        .setEffects(effects)
                        .build();

        transformer =
                new Transformer.Builder(this)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(new Transformer.Listener() {
                            @Override
                            public void onCompleted(Composition composition, ExportResult exportResult) {
                                transformer = null;
                                onExportCompleted();
                            }

                            @Override
                            public void onError(
                                    Composition composition,
                                    ExportResult exportResult,
                                    ExportException exportException) {
                                transformer = null;
                                failExport("Export hiba: " + exportException.getMessage());
                            }
                        })
                        .build();

        transformer.start(edited, currentTempOutput.getAbsolutePath());
    }

    private void onExportCompleted() {
        try {
            copyToGallery(currentTempOutput);
            statusText.setText("Kész! A videó a Galériában: Movies/RIVTO");
            Toast.makeText(this, "RIVTO export kész", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            statusText.setText("A feldolgozás elkészült, de a Galériába mentés nem sikerült: " + e.getMessage());
        } finally {
            if (currentTempOutput != null && currentTempOutput.exists()) {
                currentTempOutput.delete();
            }
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

    private void failExport(String message) {
        transformer = null;
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        resetControlsAfterExport();
    }

    private void resetControlsAfterExport() {
        progressBar.setVisibility(View.GONE);
        exportButton.setEnabled(selectedVideoUri != null);
        pickVideoButton.setEnabled(true);
        ultraSwitch.setEnabled(true);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (transformer != null) {
            transformer.cancel();
            transformer = null;
        }
        releasePlayer();
        super.onDestroy();
    }
}
