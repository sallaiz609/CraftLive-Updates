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
        List<Effect> effects = new ArrayList<>();
        effects.add(new Brightness(0.014f + 0.025f * s));
        effects.add(new Contrast(0.14f + 0.22f * s));
        effects.add(
                new HslAdjustment.Builder()
                        .adjustSaturation(24.0f + 36.0f * s)
                        .adjustLightness(1.2f + 2.4f * s)
                        .build());
        effects.add(
                new RgbAdjustment.Builder()
                        .setRedScale(1.018f + 0.036f * s)
                        .setGreenScale(1.008f + 0.020f * s)
                        .setBlueScale(0.992f - 0.010f * s)
                        .build());
        return effects;
    }
}
