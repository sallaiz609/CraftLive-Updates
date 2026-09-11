package com.rivto.videoultra;

import android.content.Context;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;

@UnstableApi
public final class UltraShaderEffect implements GlEffect {
    private final float strength;
    private final boolean asphaltBoost;

    public UltraShaderEffect(float strength, boolean asphaltBoost) {
        this.strength = Math.max(0f, Math.min(1f, strength));
        this.asphaltBoost = asphaltBoost;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
            throws VideoFrameProcessingException {
        return new UltraShaderProgram(useHdr, strength, asphaltBoost);
    }

    @Override
    public boolean isNoOp(int inputWidth, int inputHeight) {
        return strength <= 0.001f;
    }
}
