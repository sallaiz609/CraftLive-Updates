package com.rivto.videoultra;

import androidx.media3.common.Effect;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.RgbAdjustment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public final class UltraPreset {
    private UltraPreset() {}

    public static List<Effect> build(boolean enabled, float strength, boolean asphaltBoost) {
        if (!enabled || strength <= 0f) {
            return Collections.emptyList();
        }
        float clamped = Math.max(0f, Math.min(1f, strength));
        List<Effect> effects = new ArrayList<>();
        effects.add(new UltraShaderEffect(clamped, asphaltBoost));
        return effects;
    }

    public static List<Effect> buildFallback(boolean enabled, float strength) {
        if (!enabled || strength <= 0f) {
            return Collections.emptyList();
        }
        float s = Math.max(0f, Math.min(1f, strength));
        float daylightKick = Math.max(0f, Math.min(1f, (s - 0.90f) / 0.10f));
        List<Effect> effects = new ArrayList<>();
        effects.add(new Brightness(0.010f + 0.018f * s + 0.008f * daylightKick));
        effects.add(new Contrast(0.18f + 0.20f * s + 0.12f * daylightKick));
        effects.add(
                new HslAdjustment.Builder()
                        .adjustSaturation(26.0f + 34.0f * s + 16.0f * daylightKick)
                        .adjustLightness(0.6f + 1.5f * s)
                        .build());
        effects.add(
                new RgbAdjustment.Builder()
                        .setRedScale(1.030f + 0.035f * s + 0.020f * daylightKick)
                        .setGreenScale(1.010f + 0.012f * s + 0.006f * daylightKick)
                        .setBlueScale(0.975f - 0.010f * s - 0.018f * daylightKick)
                        .build());
        return effects;
    }
}
