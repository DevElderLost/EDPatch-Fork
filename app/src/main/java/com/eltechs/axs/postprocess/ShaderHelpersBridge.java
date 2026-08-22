package com.eltechs.axs.postprocess;

import com.eltechs.axs.helpers.ShaderHelpers;

/**
 * Wrapper tipis di atas Lcom/eltechs/axs/helpers/ShaderHelpers; yang SUDAH ADA
 * di codebase (dipakai SceneOfRectangles.createTexturer()). Tidak menulis ulang
 * logic compile shader - cuma reuse.
 *
 * Catatan: ShaderHelpers.compileShader() pakai GLES30 (glCreateShader dst),
 * jadi PostProcessRenderer ini juga otomatis butuh GLES30-capable context
 * (aman, GLES30 itu superset GLES20 - device modern semua support).
 */
final class ShaderHelpersBridge {
    private ShaderHelpersBridge() {}

    static int buildProgram(String vertexSrc, String fragmentSrc) {
        int vs = ShaderHelpers.compileShader(ShaderHelpers.ShaderType.VERTEX, vertexSrc);
        int fs = ShaderHelpers.compileShader(ShaderHelpers.ShaderType.FRAGMENT, fragmentSrc);
        if (vs == 0 || fs == 0) {
            throw new IllegalStateException("PostProcessRenderer: gagal compile shader (vs=" + vs + " fs=" + fs + ")");
        }
        String[] attribs = { "a_Position", "a_TexCoordinate" };
        int program = ShaderHelpers.createAndLinkProgram(vs, fs, attribs);
        if (program == 0) {
            throw new IllegalStateException("PostProcessRenderer: gagal link program");
        }
        return program;
    }
}
