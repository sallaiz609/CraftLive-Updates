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
            "  vec3 c0 = texture2D(uTexSampler, uv).rgb;\n" +
            "  vec3 l = texture2D(uTexSampler, uv + vec2(-uTexelX, 0.0)).rgb;\n" +
            "  vec3 r = texture2D(uTexSampler, uv + vec2( uTexelX, 0.0)).rgb;\n" +
            "  vec3 u = texture2D(uTexSampler, uv + vec2(0.0, -uTexelY)).rgb;\n" +
            "  vec3 d = texture2D(uTexSampler, uv + vec2(0.0,  uTexelY)).rgb;\n" +
            "  vec3 blur = (c0 * 4.0 + l + r + u + d) * 0.125;\n" +
            "  vec3 detail = c0 - blur;\n" +
            "  vec3 c = c0;\n" +
            "  // Screen Capture Recovery: restore blacks/midtones lost by YouTube + screen recording.\n" +
            "  float y0 = lum(c);\n" +
            "  float liftedBlack = 0.020 + 0.010 * s;\n" +
            "  c = max(c - vec3(liftedBlack), vec3(0.0)) / (1.0 - liftedBlack);\n" +
            "  c = pow(max(c, vec3(0.0)), vec3(0.955 - 0.020 * s));\n" +
            "  c = (c - vec3(0.5)) * (1.12 + 0.18 * s) + vec3(0.5);\n" +
            "  float y = lum(c);\n" +
            "  float hazeMid = 1.0 - smoothstep(0.18, 0.60, abs(y - 0.46));\n" +
            "  c += (c - vec3(y)) * hazeMid * (0.20 + 0.20 * s);\n" +
            "  c += detail * (0.70 + 1.35 * s);\n" +
            "  // Warm neutral balance: remove the cold cyan/grey GTA IV capture cast.\n" +
            "  c *= vec3(1.045 + 0.025 * s, 1.012 + 0.010 * s, 0.968 - 0.010 * s);\n" +
            "  y = lum(c);\n" +
            "  float chroma = sat(c);\n" +
            "  float vibrance = 1.20 + (0.45 + 0.45 * s) * (1.0 - smoothstep(0.07, 0.44, chroma));\n" +
            "  c = mix(vec3(y), c, vibrance);\n" +
            "  y = lum(c);\n" +
            "  c = mix(vec3(y), c, 1.13 + 0.30 * s);\n" +
            "  // Neutral midtone de-grey without turning already colourful objects fluorescent.\n" +
            "  y = lum(c);\n" +
            "  float neutral = 1.0 - smoothstep(0.055, 0.30, sat(c));\n" +
            "  float mid = smoothstep(0.12, 0.34, y) * (1.0 - smoothstep(0.68, 0.90, y));\n" +
            "  c += vec3(0.030, 0.020, 0.006) * neutral * mid * (0.55 + 0.45 * s);\n" +
            "  float cyanCast = max(min(c.g, c.b) - c.r, 0.0);\n" +
            "  c.r += cyanCast * neutral * (0.42 + 0.28 * s);\n" +
            "  c.b -= cyanCast * neutral * (0.12 + 0.10 * s);\n" +
            "  // Natural warm highlights, nearly-neutral shadows.\n" +
            "  y = lum(c);\n" +
            "  float shadowMask = 1.0 - smoothstep(0.07, 0.34, y);\n" +
            "  c += vec3(0.012, 0.010, 0.008) * shadowMask * (0.40 + 0.40 * s);\n" +
            "  float highlightMask = smoothstep(0.56, 0.92, y);\n" +
            "  c += vec3(0.040, 0.028, 0.012) * highlightMask * (0.40 + 0.60 * s);\n" +
            "  // Wet Asphalt Pro: deeper graphite road with restrained warm specular sheen.\n" +
            "  if (uAsphaltBoost > 0.5) {\n" +
            "    float yc = lum(c);\n" +
            "    float roadNeutral = 1.0 - smoothstep(0.055, 0.22, sat(c));\n" +
            "    float darkMid = smoothstep(0.07, 0.23, yc) * (1.0 - smoothstep(0.56, 0.74, yc));\n" +
            "    float lowerScreen = 1.0 - smoothstep(0.48, 0.96, uv.y);\n" +
            "    float textureEnergy = clamp(length(detail) * 7.0, 0.0, 1.0);\n" +
            "    float road = roadNeutral * darkMid * (0.66 + 0.34 * textureEnergy) * lowerScreen;\n" +
            "    vec3 refA = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.022), 0.0, 1.0)).rgb;\n" +
            "    vec3 refB = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.052), 0.0, 1.0)).rgb;\n" +
            "    vec3 refC = texture2D(uTexSampler, clamp(uv + vec2(0.0, 0.092), 0.0, 1.0)).rgb;\n" +
            "    vec3 reflected = refA * 0.50 + refB * 0.33 + refC * 0.17;\n" +
            "    float reflection = smoothstep(0.24, 0.86, lum(reflected));\n" +
            "    vec3 asphalt = c * vec3(0.76, 0.755, 0.74);\n" +
            "    asphalt = (asphalt - vec3(0.27)) * (1.18 + 0.06 * s) + vec3(0.27);\n" +
            "    vec3 sheen = mix(reflected, vec3(lum(reflected)), 0.45) * reflection * (0.10 + 0.16 * s);\n" +
            "    float microGlint = max(lum(detail), 0.0) * (0.20 + 0.34 * s);\n" +
            "    asphalt += sheen * vec3(1.00, 0.96, 0.88) + vec3(microGlint);\n" +
            "    c = mix(c, asphalt, road * (0.68 + 0.25 * s));\n" +
            "  }\n" +
            "  // CityBloom: bright signs, sky and highlights glow; dark areas stay clean.\n" +
            "  float bx = uTexelX * (4.0 + 3.0 * s);\n" +
            "  float by = uTexelY * (4.0 + 3.0 * s);\n" +
            "  float threshold = 0.70 - 0.07 * s;\n" +
            "  vec3 bloom = brightOnly(texture2D(uTexSampler, uv + vec2( bx, 0.0)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx, 0.0)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(0.0,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(0.0, -by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2( bx,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx,  by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2( bx, -by)).rgb, threshold);\n" +
            "  bloom += brightOnly(texture2D(uTexSampler, uv + vec2(-bx, -by)).rgb, threshold);\n" +
            "  bloom *= 0.125;\n" +
            "  bloom = mix(vec3(lum(bloom)), bloom, 0.65) * vec3(1.00, 0.95, 0.87);\n" +
            "  c += bloom * (0.075 + 0.105 * s);\n" +
            "  // Small local sparkle after bloom, then soft highlight protection.\n" +
            "  float localSpec = max(lum(c0) - lum(blur), 0.0);\n" +
            "  c += vec3(1.00, 0.94, 0.84) * localSpec * (0.13 + 0.24 * s);\n" +
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
