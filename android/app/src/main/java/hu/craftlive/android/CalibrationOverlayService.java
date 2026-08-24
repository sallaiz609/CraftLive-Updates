package hu.craftlive.android;

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A short-lived overlay used only while the user calibrates Minecraft's chat button.
 * Saving or cancelling always removes every overlay view immediately.
 */
public final class CalibrationOverlayService extends Service {
    public static final String ACTION_SHOW = "hu.craftlive.android.action.SHOW_CALIBRATION";

    private static final long MINECRAFT_OPEN_SETTLE_MILLIS = 900L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private TargetView targetView;
    private LinearLayout controlPanel;
    private WindowManager.LayoutParams targetParams;
    private int targetSize;
    private float savedXPercent;
    private float savedYPercent;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_SHOW.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacksAndMessages(null);
        removeOverlayViews();
        if (!Settings.canDrawOverlays(this)) {
            showToast(R.string.calibration_overlay_permission_required);
            stopSelf();
            return START_NOT_STICKY;
        }
        InteractionStore store = new InteractionStore(this);
        savedXPercent = store.preferences().getFloat("chat_x_percent", 0.50f);
        savedYPercent = store.preferences().getFloat("chat_y_percent", 0.035f);
        // The service starts just before Minecraft. Waiting makes the overlay use the
        // game's real landscape dimensions instead of CraftLive's portrait dimensions.
        handler.postDelayed(this::showOverlay, MINECRAFT_OPEN_SETTLE_MILLIS);
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (targetView != null) handler.postDelayed(this::restoreTargetPosition, 180L);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removeOverlayViews();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay() {
        if (windowManager == null || !Settings.canDrawOverlays(this)) {
            showToast(R.string.calibration_overlay_permission_required);
            stopSelf();
            return;
        }

        targetSize = dp(72);
        targetView = new TargetView();
        targetParams = overlayParams(targetSize, targetSize,
                Gravity.TOP | Gravity.START, false);
        restoreTargetPosition();
        targetView.setOnTouchListener(new TargetDragListener());

        controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.HORIZONTAL);
        controlPanel.setGravity(Gravity.CENTER_VERTICAL);
        controlPanel.setPadding(dp(12), dp(9), dp(12), dp(9));
        controlPanel.setBackground(panelBackground());

        TextView instruction = new TextView(this);
        instruction.setText(R.string.calibration_overlay_instruction);
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(16f);
        controlPanel.addView(instruction, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button cancel = overlayButton(R.string.cancel, false);
        Button save = overlayButton(R.string.save, true);
        controlPanel.addView(cancel);
        controlPanel.addView(save);
        cancel.setOnClickListener(v -> closeCalibration(false));
        save.setOnClickListener(v -> closeCalibration(true));

        WindowManager.LayoutParams panelParams = overlayParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, false);
        panelParams.y = dp(14);

        try {
            windowManager.addView(targetView, targetParams);
            windowManager.addView(controlPanel, panelParams);
        } catch (RuntimeException error) {
            removeOverlayViews();
            showToast(R.string.calibration_overlay_permission_required);
            stopSelf();
        }
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, int gravity,
                                                      boolean touchThrough) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (touchThrough) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = gravity;
        return params;
    }

    private void restoreTargetPosition() {
        if (targetParams == null || windowManager == null) return;
        int[] screen = screenSize();
        targetParams.x = clamp(Math.round(screen[0] * savedXPercent - targetSize / 2f),
                -targetSize / 2, Math.max(-targetSize / 2, screen[0] - targetSize / 2));
        targetParams.y = clamp(Math.round(screen[1] * savedYPercent - targetSize / 2f),
                -targetSize / 2, Math.max(-targetSize / 2, screen[1] - targetSize / 2));
        if (targetView != null && targetView.isAttachedToWindow()) {
            try {
                windowManager.updateViewLayout(targetView, targetParams);
            } catch (RuntimeException ignored) {
                // The calibration may have been closed while Android was rotating.
            }
        }
    }

    private void closeCalibration(boolean save) {
        if (save && targetParams != null) {
            int[] screen = screenSize();
            float x = clamp((targetParams.x + targetSize / 2f) / Math.max(1f, screen[0]),
                    0.001f, 0.999f);
            float y = clamp((targetParams.y + targetSize / 2f) / Math.max(1f, screen[1]),
                    0.001f, 0.999f);
            new InteractionStore(this).preferences().edit()
                    .putFloat("chat_x_percent", x)
                    .putFloat("chat_y_percent", y)
                    .putBoolean("chat_calibrated", true)
                    .apply();
        }
        // Remove the crosshair and the control strip before showing the confirmation.
        removeOverlayViews();
        if (save) showToast(R.string.calibration_saved);
        stopSelf();
    }

    private void removeOverlayViews() {
        if (windowManager != null && targetView != null) {
            try {
                windowManager.removeViewImmediate(targetView);
            } catch (RuntimeException ignored) {
            }
        }
        if (windowManager != null && controlPanel != null) {
            try {
                windowManager.removeViewImmediate(controlPanel);
            } catch (RuntimeException ignored) {
            }
        }
        targetView = null;
        controlPanel = null;
        targetParams = null;
    }

    private int[] screenSize() {
        if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            if (bounds.width() > 1 && bounds.height() > 1) {
                return new int[]{bounds.width(), bounds.height()};
            }
        }
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return new int[]{Math.max(1, metrics.widthPixels), Math.max(1, metrics.heightPixels)};
    }

    private Button overlayButton(int text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTextColor(primary ? Color.rgb(3, 24, 14) : Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? Color.rgb(126, 255, 33) : Color.rgb(15, 57, 39));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(95, 186, 36));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        params.setMargins(dp(5), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 5, 31, 20));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(95, 186, 36));
        return background;
    }

    private void showToast(int message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class TargetDragListener implements View.OnTouchListener {
        private float startRawX;
        private float startRawY;
        private int startX;
        private int startY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (targetParams == null || windowManager == null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startX = targetParams.x;
                startY = targetParams.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int[] screen = screenSize();
                targetParams.x = clamp(startX + Math.round(event.getRawX() - startRawX),
                        -targetSize / 2,
                        Math.max(-targetSize / 2, screen[0] - targetSize / 2));
                targetParams.y = clamp(startY + Math.round(event.getRawY() - startRawY),
                        -targetSize / 2,
                        Math.max(-targetSize / 2, screen[1] - targetSize / 2));
                try {
                    windowManager.updateViewLayout(targetView, targetParams);
                } catch (RuntimeException ignored) {
                }
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }
    }

    private final class TargetView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        private TargetView() {
            super(CalibrationOverlayService.this);
            fill.setColor(Color.argb(185, 4, 30, 19));
            stroke.setColor(Color.rgb(126, 255, 33));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(3));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - dp(4);
            canvas.drawCircle(cx, cy, radius, fill);
            canvas.drawCircle(cx, cy, radius, stroke);
            canvas.drawLine(cx - radius, cy, cx + radius, cy, stroke);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, stroke);
            canvas.drawCircle(cx, cy, dp(5), stroke);
        }
    }
}
