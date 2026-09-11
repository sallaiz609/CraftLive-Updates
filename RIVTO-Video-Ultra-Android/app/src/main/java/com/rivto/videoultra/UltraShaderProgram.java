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
            "vec3 brightOnly(vec3 c, float threshold) { float y=lum(c); float k=smoothstep(threshold, 1.0, y); return c*k; }\n" +
            "void main() {\n" +
            "  vec2 uv = vTexSamplingCoord;\n" +
            "  float s = clamp(uStrength, 0.0, 1.0);\n" +
            "  float dayBrutal = smoothstep(0.90, 0.995, s);\n" +
            "  vec3 c0 = texture2D(uTexSampler, uv).rgb;\n" +
            "  vec3 l = texture2D(uTexSampler, uv + vec2(-uTexelX, 0.0)).rgb;\n" +
            "  vec3 r = texture2D(uTexSampler, uv + vec2( uTexelX, 0.0)).rgb;\n" +
            "  vec3 u = texture2D(uTexSampler, uv + vec2(0.0, -uTexelY)).rgb;\n" +
            "  vec3 d = texture2D(uTexSampler, uv + vec2(0.0,  uTexelY)).rgb;\n" +
            "  vec3 blur = (c0 * 4.0 + l + r + u + d) * 0.125;\n" +
            "  vec3 detail = c0 - blur;\n" +
            "  vec3 c = c0;\n" +
            "  float sourceY = lum(c0);\n" +
            "  float dayMask = smoothstep(0.20, 0.50, sourceY);\n" +
            "  float liftedBlack = 0.020 + 0.012 * s + 0.010 * dayBrutal * dayMask;\n" +
            "  c = max(c - vec3(liftedBlack), vec3(0.0)) / max(0.80, 1.0 - liftedBlack);\n" +
            "  c = pow(max(c, vec3(0.0)), vec3(0.955 - 0.024 * s - 0.018 * dayBrutal * dayMask));\n" +
            "  c = (c - vec3(0.5)) * (1.12 + 0.18 * s + 0.12 * dayBrutal * dayMask) + vec3(0.5);\n" +
            "  float y = lum(c);\n" +
            "  float hazeMid = 1.0 - smoothstep(0.16, 0.58, abs(y - 0.46));\n" +
            "  c += (c - vec3(y)) * hazeMid * (0.20 + 0.20 * s + 0.18 * dayBrutal * dayMask);\n" +
            "  c += detail * (0.70 + 1.35 * s + 0.25 * dayBrutal * dayMask);\n" +
            "  c *= vec3(1.045 + 0.025 * s + 0.018 * dayBrutal * dayMask,\n" +
            "            1.012 + 0.010 * s + 0.006 * dayBrutal * dayMask,\n" +
            "            0.968 - 0.010 * s - 0.020 * dayBrutal * dayMask);\n" +
            "  y = lum(c);\n" +
            "  float chroma = sat(c);\n" +
            "  float vibrance = 1.20 + (0.45 + 0.45 * s) * (1.0 - smoothstep(0.07, 0.44, chroma));\n" +
            "  vibrance += dayBrutal * dayMask * 0.42 * (1.0 - smoothstep(0.08, 0.48, chroma));\n" +
            "  c = mix(vec3(y), c, vibrance);\n" +
            "  y = lum(c);\n" +
            "  c = mix(vec3(y), c, 1.13 + 0.30 * s + 0.20 * dayBrutal * dayMask);\n" +
            "  y = lum(c);\n" +
            "  float neutral = 1.0 - smoothstep(0.055, 0.30, sat(c));\n" +
            "  float mid = smoothstep(0.10, 0.32, y) * (1.0 - smoothstep(0.68, 0.90, y));\n" +
            "  c += vec3(0.030, 0.020, 0.006) * neutral * mid * (0.55 + 0.45 * s);\n" +
            "  c += vec3(0.050, 0.032, 0.008) * neutral * mid * dayBrutal * dayMask;\n" +
            "  float cyanCast = max(min(c.g, c.b) - c.r, 0.0);\n" +
            "  c.r += cyanCast * neutral * (0.42 + 0.28 * s + 0.20 * dayBrutal * dayMask);\n" +
            "  c.b -= cyanCast * neutral * (0.12 + 0.10 * s + 0.14 * dayBrutal * dayMask);\n" +
            "  y = lum(c);\n" +
            "  float shadowMask = 1.0 - smoothstep(0.07, 0.34, y);\n" +
            "  c += vec3(0.012, 0.010, 0.008) * shadowMask * (0.40 + 0.40 * s);\n" +
            "  float highlightMask = smoothstep(0.50, 0.90, y);\n" +
            "  c += vec3(0.040, 0.028, 0.012) * highlightMask * (0.40 + 0.60 * s);\n" +
            "  c += vec3(0.026, 0.014, -0.004) * highlightMask * dayBrutal * dayMask;\n" +
            "  float blueDom = clamp((c.b - max(c.r, c.g)) * 5.0, 0.0, 1.0);\n" +
            "  float greenDom = clamp((c.g - max(c.r, c.b)) * 5.0, 0.0, 1.0);\n" +
            "  float redDom = clamp((c.r - max(c.g, c.b)) * 5.0, 0.0, 1.0);\n" +
            "  c.b += blueDom * 0.045 * dayBrutal * dayMask;\n" +
            "  c.g += greenDom * 0.035 * dayBrutal * dayMask;\n" +
            "  c.r += redDom * 0.035 * dayBrutal * dayMask;\n" +
            "  if (uAsphaltBoost > 0.5) {\n" +
            "    float yc = lum(c);\n" +
            "    float roadNeutral = 1.0 - smoothstep(0.055, 0.22, sat(c));\n" +
            "    float darkMid = smoothstep(0.06, 0.22, yc) * (1.0 - smoothstep(0.58, 0.76, yc));\n" +
            "    float lowerScreen = 1.0 - smoothstep(0.46, 0.96, uv.y);\n" +
            "    float textureEnergy = clamp(length(detail) * 7.4, 0.0, 1.0);\n" +
            "    float road = roadNeutral * darkMid * (0.64 + 0.36 * textureEnergy) * lowerScreen;\n" +
            "    vec3 refA = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.022), 0.0, 1.0)).rgb;\n" +
            "    vec3 refB = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.052), 0.0, 1.0)).rgb;\n" +
            "    vec3 refC = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.092), 0.0, 1.0)).rgb;\n" +
            "    vec3 reflected = refA * 0.50 + refB * 0.33 + refC * 0.17;\n" +
            "    float reflection = smoothstep(0.22, 0.84, lum(reflected));\n" +
            "    vec3 asphalt = c * vec3(0.74, 0.74, 0.72);\n" +
            "    asphalt = (asphalt - vec3(0.27)) * (1.20 + 0.08 * s + 0.06 * dayBrutal) + vec3(0.27);\n" +
            "    vec3 sheen = mix(reflected, vec3(lum(reflected)), 0.48) * reflection * (0.11 + 0.17 * s + 0.05 * dayBrutal);\n" +
            "    float microGlint = max(lum(detail), 0.0) * (0.22 + 0.36 * s + 0.10 * dayBrutal);\n" +
            "    asphalt += sheen * vec3(1.00, 0.95, 0.86) + vec3(microGlint);\n" +
            "    c = mix(c, asphalt, road * (0.70 + 0.24 * s));\n" +
            "  }\n" +
            "  float bx = uTexelX * (4.0 + 3.0 * s);\n" +
            "  float by = uTexelY * (4.0 + 3.0 * s);\n" +
            "  float threshold = 0.70 - 0.07 * s - 0.02 * dayBrutal * dayMask;\n" +
            "  vec3 bloom = brightOnly(texture2D(uTexSampler, uv + vec2( bx, 0.0)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx, 0.0)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(0.0,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(0.0, -by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2( bx,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2( bx, -by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx, -by)).rgb, threshold);\n" +
            "  bloom *= 0.125;\n" +
            "  bloom = mix(vec3(lum(bloom)), bloom, 0.68) * vec3(1.00, 0.95, 0.86);\n" +
            "  c += bloom * (0.075 + 0.105 * s + 0.040 * dayBrutal * dayMask);\n" +
            "  float localSpec = max(lum(c0) - lum(blur), 0.0);\n" +
            "  c += vec3(1.00, 0.94, 0.84) * localSpec * (0.13 + 0.24 * s + 0.08 * dayBrutal * dayMask);\n" +
            "  c += vec3(0.006 + 0.008 * s);\n" +
            "  vec3 over = max(c - vec3(0.94), vec3(0.0));\n" +
            "  c = min(c, vec3(0.94)) + over / (vec3(1.0) + over * 1.8);\n" +
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
