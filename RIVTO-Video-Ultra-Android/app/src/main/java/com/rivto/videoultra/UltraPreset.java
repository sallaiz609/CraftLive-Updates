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

    public static List<Effect> build(boolean enabled) {
        if (!enabled) {
            return Collections.emptyList();
        }

        List<Effect> effects = new ArrayList<>();
        effects.add(new Brightness(0.012f));
        effects.add(new Contrast(0.18f));
        effects.add(
                new HslAdjustment.Builder()
                        .adjustSaturation(14.0f)
                        .adjustLightness(1.2f)
                        .build());
        effects.add(
                new RgbAdjustment.Builder()
                        .setRedScale(1.025f)
                        .setGreenScale(1.010f)
                        .setBlueScale(0.985f)
                        .build());

        return effects;
    }
}
