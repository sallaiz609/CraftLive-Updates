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
            "uniform float uAsphaltBoost;\n" +
            "varying vec2 vTexSamplingCoord;\n" +
            "float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }\n" +
            "float sat(vec3 c) { float mx=max(c.r,max(c.g,c.b)); float mn=min(c.r,min(c.g,c.b)); return mx-mn; }\n" +
            "void main() {\n" +
            "  vec2 uv = vTexSamplingCoord;\n" +
            "  float s = clamp(uStrength, 0.0, 1.0);\n" +
            "  vec3 c0 = texture2D(uTexSampler, uv).rgb;\n" +
            "  vec3 l = texture2D(uTexSampler, uv + vec2(-uTexelX, 0.0)).rgb;\n" +
            "  vec3 r = texture2D(uTexSampler, uv + vec2( uTexelX, 0.0)).rgb;\n" +
            "  vec3 u = texture2D(uTexSampler, uv + vec2(0.0, -uTexelY)).rgb;\n" +
            "  vec3 d = texture2D(uTexSampler, uv + vec2(0.0,  uTexelY)).rgb;\n" +
            "  vec3 blur = (c0 * 4.0 + l + r + u + d) * 0.125;\n" +
            "  vec3 detail = c0 - blur;\n" +
            "  vec3 c = c0 + detail * (0.70 + 1.20 * s);\n" +
            "  c = max(c, vec3(0.0));\n" +
            "  c = pow(c, vec3(0.965 - 0.025 * s));\n" +
            "  c += vec3(0.010 + 0.020 * s);\n" +
            "  float y = lum(c);\n" +
            "  float chroma = sat(c);\n" +
            "  float vib = 1.18 + (0.42 + 0.36 * s) * (1.0 - smoothstep(0.06, 0.48, chroma));\n" +
            "  c = mix(vec3(y), c, vib);\n" +
            "  y = lum(c);\n" +
            "  float satBoost = 1.12 + 0.36 * s;\n" +
            "  c = mix(vec3(y), c, satBoost);\n" +
            "  c *= vec3(1.040 + 0.020 * s, 1.008 + 0.008 * s, 0.965 - 0.012 * s);\n" +
            "  c = (c - vec3(0.5)) * (1.08 + 0.17 * s) + vec3(0.5);\n" +
            "  y = lum(c);\n" +
            "  float shadowMask = 1.0 - smoothstep(0.08, 0.38, y);\n" +
            "  c += vec3(0.022, 0.018, 0.012) * shadowMask * (0.45 + 0.55 * s);\n" +
            "  float midMask = 1.0 - abs(clamp((y - 0.50) / 0.50, -1.0, 1.0));\n" +
            "  c.r += 0.014 * midMask * s;\n" +
            "  c.g += 0.006 * midMask * s;\n" +
            "  float highlightMask = smoothstep(0.58, 0.92, y);\n" +
            "  c += vec3(0.036, 0.026, 0.012) * highlightMask * (0.35 + 0.65 * s);\n" +
            "  float localSpec = max(lum(c0) - lum(blur), 0.0);\n" +
            "  c += vec3(1.00, 0.92, 0.78) * localSpec * (0.16 + 0.34 * s);\n" +
            "  float nBright = (lum(l) + lum(r) + lum(u) + lum(d)) * 0.25;\n" +
            "  float glow = smoothstep(0.62, 0.96, nBright);\n" +
            "  c += vec3(1.00, 0.91, 0.76) * glow * (0.020 + 0.060 * s);\n" +
            "  if (uAsphaltBoost > 0.5) {\n" +
            "    float yc = lum(c);\n" +
            "    float neutral = 1.0 - smoothstep(0.055, 0.22, sat(c));\n" +
            "    float darkMid = smoothstep(0.07, 0.24, yc) * (1.0 - smoothstep(0.52, 0.72, yc));\n" +
            "    float lowerScreen = 1.0 - smoothstep(0.48, 0.96, uv.y);\n" +
            "    float textureEnergy = clamp(length(detail) * 6.5, 0.0, 1.0);\n" +
            "    float road = neutral * darkMid * (0.68 + 0.32 * textureEnergy) * lowerScreen;\n" +
            "    vec3 refA = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.022), 0.0, 1.0)).rgb;\n" +
            "    vec3 refB = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.052), 0.0, 1.0)).rgb;\n" +
            "    vec3 refC = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.092), 0.0, 1.0)).rgb;\n" +
            "    float reflectedY = lum(refA * 0.50 + refB * 0.33 + refC * 0.17);\n" +
            "    float reflection = smoothstep(0.24, 0.86, reflectedY);\n" +
            "    vec3 asphalt = c * vec3(0.82, 0.81, 0.79);\n" +
            "    asphalt = (asphalt - vec3(0.28)) * 1.16 + vec3(0.28);\n" +
            "    vec3 sheen = vec3(1.00, 0.96, 0.88) * reflection * (0.055 + 0.115 * s);\n" +
            "    float microGlint = max(lum(detail), 0.0) * (0.14 + 0.28 * s);\n" +
            "    asphalt += sheen + vec3(microGlint);\n" +
            "    c = mix(c, asphalt, road * (0.64 + 0.26 * s));\n" +
            "  }\n" +
            "  float cyanCast = max(min(c.g, c.b) - c.r, 0.0);\n" +
            "  float neutralArea = 1.0 - smoothstep(0.10, 0.36, sat(c));\n" +
            "  c.r += cyanCast * neutralArea * (0.30 + 0.28 * s);\n" +
            "  c.b -= cyanCast * neutralArea * (0.08 + 0.10 * s);\n" +
            "  c = clamp(c, 0.0, 1.0);\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    private final GlProgram glProgram;
    private final float strength;
    private final float asphaltBoost;
    private float texelX = 1f;
    private float texelY = 1f;

    public UltraShaderProgram(boolean useHdr, float strength, boolean asphaltBoost)
            throws VideoFrameProcessingException {
        super(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1);
        this.strength = Math.max(0f, Math.min(1f, strength));
        this.asphaltBoost = asphaltBoost ? 1f : 0f;

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
            glProgram.setFloatUniform("uAsphaltBoost", asphaltBoost);
            glProgram.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        } catch (GlUtil.GlException e) {
            throw new VideoFrameProcessingException(e, presentationTimeUs);
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
