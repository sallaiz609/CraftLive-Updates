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
            "vec3 softClip(vec3 c) { return c / (vec3(0.84) + c * 0.16); }\n" +
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
            "  c += detail * (0.80 + 1.85 * s);\n" +
            "  float y = lum(c);\n" +
            "  float chroma = sat(c);\n" +
            "  float vibrance = 1.0 + (0.34 + 0.72 * s) * (1.0 - clamp(chroma * 1.25, 0.0, 1.0));\n" +
            "  c = mix(vec3(y), c, vibrance);\n" +
            "  y = lum(c);\n" +
            "  float saturation = 1.18 + 0.42 * s;\n" +
            "  c = mix(vec3(y), c, saturation);\n" +
            "  c = (c - vec3(0.5)) * (1.11 + 0.20 * s) + vec3(0.5);\n" +
            "  y = lum(c);\n" +
            "  float shadowMask = 1.0 - smoothstep(0.06, 0.38, y);\n" +
            "  c += vec3(0.014, 0.022, 0.036) * shadowMask * s;\n" +
            "  float highlightMask = smoothstep(0.58, 0.93, y);\n" +
            "  c += vec3(0.045, 0.038, 0.026) * highlightMask * (0.35 + 0.65 * s);\n" +
            "  c -= max(c - vec3(0.96), vec3(0.0)) * (0.08 + 0.11 * s);\n" +
            "  float localSpec = max(y - lum(blur), 0.0);\n" +
            "  c += vec3(0.060, 0.068, 0.082) * localSpec * (7.0 + 10.0 * s);\n" +
            "  float nBright = (lum(l) + lum(r) + lum(u) + lum(d)) * 0.25;\n" +
            "  float glow = max(nBright - 0.56, 0.0);\n" +
            "  c += vec3(1.0, 0.91, 0.78) * glow * (0.055 + 0.115 * s);\n" +
            "  float warm = smoothstep(0.43, 0.92, y);\n" +
            "  float cool = 1.0 - smoothstep(0.10, 0.46, y);\n" +
            "  c.r += 0.040 * warm * s;\n" +
            "  c.g += 0.012 * warm * s;\n" +
            "  c.b -= 0.020 * warm * s;\n" +
            "  c.b += 0.024 * cool * s;\n" +
            "  c.r -= 0.008 * cool * s;\n" +
            "  float greenMask = smoothstep(c.r + 0.015, c.g, c.g) * smoothstep(c.b + 0.005, c.g, c.g);\n" +
            "  c.g += 0.028 * greenMask * s;\n" +
            "  float blueMask = smoothstep(max(c.r,c.g) + 0.015, c.b, c.b);\n" +
            "  c.b += 0.030 * blueMask * s;\n" +
            "  if (uAsphaltBoost > 0.5) {\n" +
            "    float yc = lum(c);\n" +
            "    float neutral = 1.0 - smoothstep(0.045, 0.24, sat(c));\n" +
            "    float darkMid = smoothstep(0.055, 0.22, yc) * (1.0 - smoothstep(0.54, 0.72, yc));\n" +
            "    float lowerScreen = 1.0 - smoothstep(0.44, 0.96, uv.y);\n" +
            "    float textureEnergy = clamp(length(detail) * 7.5, 0.0, 1.0);\n" +
            "    float road = neutral * darkMid * (0.66 + 0.34 * textureEnergy) * lowerScreen;\n" +
            "    vec3 refA = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.020), 0.0, 1.0)).rgb;\n" +
            "    vec3 refB = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.050), 0.0, 1.0)).rgb;\n" +
            "    vec3 refC = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.095), 0.0, 1.0)).rgb;\n" +
            "    vec3 reflected = refA * 0.50 + refB * 0.33 + refC * 0.17;\n" +
            "    float reflectionLuma = smoothstep(0.20, 0.82, lum(reflected));\n" +
            "    vec3 asphaltBase = c * vec3(0.78, 0.82, 0.88);\n" +
            "    asphaltBase = (asphaltBase - vec3(0.25)) * 1.22 + vec3(0.25);\n" +
            "    vec3 sheen = reflected * (0.14 + 0.24 * s) * reflectionLuma;\n" +
            "    float microGlint = max(lum(detail), 0.0) * (0.28 + 0.42 * s);\n" +
            "    float wetLine = pow(clamp(reflectionLuma, 0.0, 1.0), 1.6) * (0.045 + 0.090 * s);\n" +
            "    asphaltBase += sheen + vec3(microGlint + wetLine);\n" +
            "    c = mix(c, asphaltBase, road * (0.72 + 0.24 * s));\n" +
            "  }\n" +
            "  c += vec3(0.010 + 0.014 * s);\n" +
            "  c = softClip(max(c, vec3(0.0)));\n" +
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
