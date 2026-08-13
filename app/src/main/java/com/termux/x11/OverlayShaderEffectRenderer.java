package com.termux.x11;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;  // tetap disertakan karena interface GLSurfaceView.Renderer memerlukannya

/**
 * Renderer untuk overlay efek shader GLSL partial di atas LorieView.
 * Mengadopsi gaya shader dan struktur dari SceneOfRectangles (MVP ortho, alpha uniform, quad rendering).
 * Efek: vignette + scanline animasi + subtle color tint (semi-transparan).
 */
public class OverlayShaderEffectRenderer implements GLSurfaceView.Renderer {

    private int program = 0;
    private FloatBuffer quadBuffer;

    private int uMVPLocation;
    private int uAlphaLocation;
    private int uTimeLocation;

    private final float[] mvpMatrix = new float[16];

    // Quad full-screen (posisi + texcoord, mirip rectangle di SceneOfRectangles)
    private static final float[] QUAD_VERTICES = {
            -1f, -1f,  0f, 0f,
             1f, -1f,  1f, 0f,
            -1f,  1f,  0f, 1f,

             1f, -1f,  1f, 0f,
             1f,  1f,  1f, 1f,
            -1f,  1f,  0f, 1f
    };

    private boolean enabled = false;

    public void setEnabled(boolean enable) {
        this.enabled = enable;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        program = createShaderProgram();

        if (program == 0) {
            // Jika shader gagal compile/link, log dan return agar tidak crash
            return;
        }

        uMVPLocation   = GLES20.glGetUniformLocation(program, "u_MVPMatrix");
        uAlphaLocation = GLES20.glGetUniformLocation(program, "u_Alpha");
        uTimeLocation  = GLES20.glGetUniformLocation(program, "u_Time");

        // Setup Vertex Buffer Object (VBO) untuk quad
        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        quadBuffer = bb.asFloatBuffer();
        quadBuffer.put(QUAD_VERTICES);
        quadBuffer.position(0);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        // Orthographic projection mirip setSceneViewport di SceneOfRectangles
        Matrix.orthoM(mvpMatrix, 0, -1f, 1f, -1f, 1f, -10f, 10f);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        if (!enabled) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);  // tetap transparan jika efek dimatikan
            return;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        // Set uniform
        GLES20.glUniformMatrix4fv(uMVPLocation, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(uAlphaLocation, 0.25f);  // opacity partial, sesuaikan 0.1 \~ 0.5
        GLES20.glUniform1f(uTimeLocation, System.currentTimeMillis() * 0.001f);

        // Bind attributes (mirip a_Position dan a_TexCoordinate di SceneOfRectangles)
        int posLoc = GLES20.glGetAttribLocation(program, "a_Position");
        int texLoc = GLES20.glGetAttribLocation(program, "a_TexCoordinate");

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    /**
     * Membuat shader program mirip createTexturer di SceneOfRectangles
     */
    private int createShaderProgram() {
        String vertexSrc =
                "uniform mat4 u_MVPMatrix;\n" +
                "attribute vec4 a_Position;\n" +
                "attribute vec2 a_TexCoordinate;\n" +
                "varying vec2 v_TexCoordinate;\n" +
                "void main() {\n" +
                "    v_TexCoordinate = a_TexCoordinate;\n" +
                "    gl_Position = u_MVPMatrix * a_Position;\n" +
                "}\n";

        String fragmentSrc =
                "precision mediump float;\n" +
                "varying vec2 v_TexCoordinate;\n" +
                "uniform float u_Alpha;\n" +
                "uniform float u_Time;\n" +
                "void main() {\n" +
                "    vec2 uv = v_TexCoordinate;\n" +
                "    // Vignette (gelap di pinggir)\n" +
                "    vec2 pos = uv - 0.5;\n" +
                "    float vig = 1.0 - length(pos) * 1.2;\n" +
                "    // Scanline animasi sederhana\n" +
                "    float scan = 0.8 + 0.2 * sin(uv.y * 600.0 + u_Time * 4.0);\n" +
                "    // Tint warna subtle (biru kehijauan ringan)\n" +
                "    vec3 color = vec3(0.95, 1.0, 1.05) * vig * scan;\n" +
                "    gl_FragColor = vec4(color, u_Alpha);\n" +
                "}\n";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc);
        if (vertexShader == 0) return 0;

        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        if (fragmentShader == 0) return 0;

        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vertexShader);
        GLES20.glAttachShader(prog, fragmentShader);
        GLES20.glLinkProgram(prog);

        // Cek status link (opsional tapi direkomendasikan)
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String infoLog = GLES20.glGetProgramInfoLog(prog);
            // Log error di Logcat (tambahkan Log.e jika perlu)
            GLES20.glDeleteProgram(prog);
            return 0;
        }

        // Bersihkan shader individual setelah link berhasil
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        return prog;
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // Cek compile status
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String infoLog = GLES20.glGetShaderInfoLog(shader);
            // Log error jika perlu: Log.e("Shader", "Compile failed: " + infoLog);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public void release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }
}