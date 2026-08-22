package com.eltechs.axs.postprocess;

final class GLSL {
    private GLSL() {}

    static final String PASSTHROUGH_VERT =
            "precision mediump float;\n" +
            "uniform mat4 u_MVP;\n" +
            "attribute vec4 a_Position;\n" +
            "attribute vec2 a_TexCoordinate;\n" +
            "varying vec2 v_TexCoordinate;\n" +
            "void main() {\n" +
            "    gl_Position = u_MVP * a_Position;\n" +
            "    v_TexCoordinate = a_TexCoordinate;\n" +
            "}\n";

    static final String SOURCE_UPSCALE_FRAG =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES u_SourceTexture;\n" +
            "uniform vec2 u_SourceTexelSize;\n" +
            "uniform float u_Sharpness;\n" +
            "varying vec2 v_TexCoordinate;\n" +
            "vec3 sampleSrc(vec2 uv) { return texture2D(u_SourceTexture, uv).rgb; }\n" +
            "void main() {\n" +
            "    vec2 uv = v_TexCoordinate;\n" +
            "    vec3 center = sampleSrc(uv);\n" +
            "    vec3 up    = sampleSrc(uv + vec2(0.0, -u_SourceTexelSize.y));\n" +
            "    vec3 down  = sampleSrc(uv + vec2(0.0,  u_SourceTexelSize.y));\n" +
            "    vec3 left  = sampleSrc(uv + vec2(-u_SourceTexelSize.x, 0.0));\n" +
            "    vec3 right = sampleSrc(uv + vec2( u_SourceTexelSize.x, 0.0));\n" +
            "    vec3 neighborMin = min(min(up, down), min(left, right));\n" +
            "    vec3 neighborMax = max(max(up, down), max(left, right));\n" +
            "    vec3 avgNeighbor = (up + down + left + right) * 0.25;\n" +
            "    vec3 sharpened = center + (center - avgNeighbor) * u_Sharpness;\n" +
            "    sharpened = clamp(sharpened, neighborMin, neighborMax);\n" +
            "    gl_FragColor = vec4(sharpened, 1.0);\n" +
            "}\n";

    static final String BRIGHTPASS_FRAG =
            "precision mediump float;\n" +
            "uniform sampler2D u_SceneTexture;\n" +
            "uniform float u_Threshold;\n" +
            "uniform float u_Knee;\n" +
            "varying vec2 v_TexCoordinate;\n" +
            "void main() {\n" +
            "    vec3 color = texture2D(u_SceneTexture, v_TexCoordinate).rgb;\n" +
            "    float brightness = max(color.r, max(color.g, color.b));\n" +
            "    float knee = u_Threshold * u_Knee + 1e-5;\n" +
            "    float soft = brightness - u_Threshold + knee;\n" +
            "    soft = clamp(soft, 0.0, 2.0 * knee);\n" +
            "    soft = soft * soft / (4.0 * knee + 1e-5);\n" +
            "    float contribution = max(soft, brightness - u_Threshold);\n" +
            "    contribution /= max(brightness, 1e-5);\n" +
            "    gl_FragColor = vec4(color * contribution, 1.0);\n" +
            "}\n";

    static final String GAUSSIAN_BLUR_FRAG =
            "precision mediump float;\n" +
            "uniform sampler2D u_SceneTexture;\n" +
            "uniform vec2 u_Direction;\n" +
            "uniform float u_Radius;\n" +
            "varying vec2 v_TexCoordinate;\n" +
            "void main() {\n" +
            "    vec2 uv = v_TexCoordinate;\n" +
            "    vec3 result = texture2D(u_SceneTexture, uv).rgb * 0.227027;\n" +
            "    float weights1 = 0.1945946;\n" +
            "    float weights2 = 0.1216216;\n" +
            "    float weights3 = 0.054054;\n" +
            "    float weights4 = 0.016216;\n" +
            "    vec2 o1 = u_Direction * (1.0 * u_Radius);\n" +
            "    result += texture2D(u_SceneTexture, uv + o1).rgb * weights1;\n" +
            "    result += texture2D(u_SceneTexture, uv - o1).rgb * weights1;\n" +
            "    vec2 o2 = u_Direction * (2.0 * u_Radius);\n" +
            "    result += texture2D(u_SceneTexture, uv + o2).rgb * weights2;\n" +
            "    result += texture2D(u_SceneTexture, uv - o2).rgb * weights2;\n" +
            "    vec2 o3 = u_Direction * (3.0 * u_Radius);\n" +
            "    result += texture2D(u_SceneTexture, uv + o3).rgb * weights3;\n" +
            "    result += texture2D(u_SceneTexture, uv - o3).rgb * weights3;\n" +
            "    vec2 o4 = u_Direction * (4.0 * u_Radius);\n" +
            "    result += texture2D(u_SceneTexture, uv + o4).rgb * weights4;\n" +
            "    result += texture2D(u_SceneTexture, uv - o4).rgb * weights4;\n" +
            "    gl_FragColor = vec4(result, 1.0);\n" +
            "}\n";

    static final String COMPOSITE_FRAG =
            "precision mediump float;\n" +
            "uniform sampler2D u_BaseTexture;\n" +
            "uniform sampler2D u_BloomTexture;\n" +
            "uniform float u_BloomIntensity;\n" +
            "uniform float u_Saturation;\n" +
            "uniform float u_Vignette;\n" +
            "varying vec2 v_TexCoordinate;\n" +
            "void main() {\n" +
            "    vec2 uv = v_TexCoordinate;\n" +
            "    vec3 base = texture2D(u_BaseTexture, uv).rgb;\n" +
            "    vec3 bloom = texture2D(u_BloomTexture, uv).rgb;\n" +
            "    vec3 color = base + bloom * u_BloomIntensity;\n" +
            "    color = color / (1.0 + color * 0.15);\n" +
            "    float luma = dot(color, vec3(0.299, 0.587, 0.114));\n" +
            "    color = mix(vec3(luma), color, u_Saturation);\n" +
            "    if (u_Vignette > 0.0) {\n" +
            "        vec2 centered = uv - 0.5;\n" +
            "        float vig = 1.0 - dot(centered, centered) * u_Vignette;\n" +
            "        color *= clamp(vig, 0.0, 1.0);\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";
}
