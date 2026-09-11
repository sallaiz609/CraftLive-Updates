package com.rivto.videoultra;

import android.opengl.GLES20;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BaseGlShaderProgram;

@UnstableApi
public final class UltraShaderProgram extends BaseGlShaderProgram {

    private static final String VERTEX_SHADER =
            "#version 100\n" +
            "attribute vec4 aFramePosition;\n" +
            "uniform mat4 uTransformationMatrix;\n" +
            "uniform mat4 uTexTransformationMatrix;\n" +
            "varying vec2 vTexSamplingCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uTransformationMatrix * aFramePosition;\n" +
            "  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);\n" +
            "  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 100\n" +
            "precision highp float;\n" +
            "uniform sampler2D uTexSampler;\n" +
            "uniform float uTexelX;\n" +
            "uniform float uTexelY;\n" +
            "uniform float uStrength;\n" +
            "varying vec2 vTexSamplingCoord;\n" +
            "float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }\n" +
            "void main() {\n" +
            "  vec2 uv = vTexSamplingCoord;\n" +
            "  vec3 c = texture2D(uTexSampler, uv).rgb;\n" +
            "  vec3 l = texture2D(uTexSampler, uv + vec2(-uTexelX, 0.0)).rgb;\n" +
            "  vec3 r = texture2D(uTexSampler, uv + vec2( uTexelX, 0.0)).rgb;\n" +
            "  vec3 u = texture2D(uTexSampler, uv + vec2(0.0, -uTexelY)).rgb;\n" +
            "  vec3 d = texture2D(uTexSampler, uv + vec2(0.0,  uTexelY)).rgb;\n" +
            "  float s = clamp(uStrength, 0.0, 1.0);\n" +
            "  vec3 blur = (c * 4.0 + l + r + u + d) * 0.125;\n" +
            "  vec3 detail = c - blur;\n" +
            "  c += detail * (0.42 + 1.08 * s);\n" +
            "  float y = lum(c);\n" +
            "  float mx = max(c.r, max(c.g, c.b));\n" +
            "  float mn = min(c.r, min(c.g, c.b));\n" +
            "  float chroma = clamp(mx - mn, 0.0, 1.0);\n" +
            "  float vibrance = 1.0 + (0.08 + 0.18 * s) * (1.0 - chroma);\n" +
            "  c = mix(vec3(y), c, vibrance);\n" +
            "  c = (c - vec3(0.5)) * (1.0 + 0.045 + 0.085 * s) + vec3(0.5);\n" +
            "  y = lum(c);\n" +
            "  float shadowMask = 1.0 - smoothstep(0.06, 0.42, y);\n" +
            "  c += vec3(0.014, 0.017, 0.023) * shadowMask * s;\n" +
            "  float highMask = smoothstep(0.60, 1.00, y);\n" +
            "  c -= max(c - vec3(0.91), vec3(0.0)) * (0.10 + 0.14 * s);\n" +
            "  float localSpec = max(y - lum(blur), 0.0);\n" +
            "  c += vec3(0.018, 0.021, 0.026) * localSpec * (4.0 + 5.0 * s);\n" +
            "  float nBright = (lum(l) + lum(r) + lum(u) + lum(d)) * 0.25;\n" +
            "  float glow = max(nBright - 0.76, 0.0);\n" +
            "  c += vec3(1.0, 0.96, 0.90) * glow * (0.018 + 0.040 * s);\n" +
            "  float warm = smoothstep(0.48, 0.92, y);\n" +
            "  float cool = 1.0 - smoothstep(0.14, 0.48, y);\n" +
            "  c.r += 0.016 * warm * s;\n" +
            "  c.g += 0.004 * warm * s;\n" +
            "  c.b -= 0.010 * warm * s;\n" +
            "  c.b += 0.010 * cool * s;\n" +
            "  c.r -= 0.004 * cool * s;\n" +
            "  c += vec3(0.006 + 0.008 * s);\n" +
            "  c = clamp(c, 0.0, 1.0);\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    private final GlProgram glProgram;
    private final float strength;
    private float texelX = 1f;
    private float texelY = 1f;

    public UltraShaderProgram(boolean useHdr, float strength)
            throws VideoFrameProcessingException {
        super(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1);
        this.strength = Math.max(0f, Math.min(1f, strength));

        try {
            glProgram = new GlProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            glProgram.setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
            float[] identity = GlUtil.create4x4IdentityMatrix();
            glProgram.setFloatsUniform("uTransformationMatrix", identity);
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity);
        } catch (GlUtil.GlException e) {
            throw new VideoFrameProcessingException(e);
        }
    }

    @Override
    public Size configure(int inputWidth, int inputHeight) {
        texelX = inputWidth > 0 ? 1.0f / inputWidth : 1.0f;
        texelY = inputHeight > 0 ? 1.0f / inputHeight : 1.0f;
        return new Size(inputWidth, inputHeight);
    }

    @Override
    public void drawFrame(int inputTexId, long presentationTimeUs)
            throws VideoFrameProcessingException {
        try {
            glProgram.use();
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0);
            glProgram.setFloatUniform("uTexelX", texelX);
            glProgram.setFloatUniform("uTexelY", texelY);
            glProgram.setFloatUniform("uStrength", strength);
            glProgram.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        } catch (GlUtil.GlException e) {
            throw new VideoFrameProcessingException(e);
        }
    }

    @Override
    public void release() throws VideoFrameProcessingException {
        super.release();
        try {
            glProgram.delete();
        } catch (GlUtil.GlException e) {
            throw new VideoFrameProcessingException(e);
        }
    }
}
