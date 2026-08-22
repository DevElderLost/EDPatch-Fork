package com.eltechs.axs.postprocess;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer post-process untuk output termux-x11 (Lorie).
 *
 * Alur:
 *  1. onSurfaceCreated() bikin 1 texture OES + SurfaceTexture -> jadi "pintu masuk"
 *     frame dari Lorie (native compositor render ke Surface(surfaceTexture) ini,
 *     BUKAN langsung ke SurfaceView yang tampil ke user).
 *  2. onDrawFrame(): updateTexImage() -> sample OES texture -> upscale/sharpen (pass 0)
 *     -> brightpass (pass 1) -> blur horizontal+vertical ping-pong (pass 2/3)
 *     -> composite base+bloom ke layar (pass 4).
 *
 * PENTING: SurfaceTexture di sini HANYA valid dibuat setelah GL context ready
 * (di GL thread, saat onSurfaceCreated). Karena itu getInputSurface() blocking
 * sampai context tersebut siap - dipanggil dari MainActivity SEBELUM
 * ICmdEntryInterface.windowChanged() dikirim.
 */
public class PostProcessRenderer implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {

    // ---- konfigurasi tunable, boleh diexpose ke UI settings nanti ----
    public volatile float sharpness = 0.45f;
    public volatile float bloomThreshold = 0.75f;
    public volatile float bloomKnee = 0.15f;
    public volatile float bloomIntensity = 0.55f;
    public volatile float blurRadius = 1.2f;
    public volatile float saturation = 1.05f;
    public volatile float vignette = 0.0f;
    public volatile boolean enabled = true;

    private int sourceWidth = 1280;
    private int sourceHeight = 720;

    private int oesTextureId;
    private SurfaceTexture surfaceTexture;
    private Surface inputSurface;
    private final float[] oesTransform = new float[16];
    private volatile boolean frameAvailable = false;

    private final CountDownLatch readyLatch = new CountDownLatch(1);

    // Shader programs
    private int progUpscale, progBrightpass, progBlur, progComposite;

    // FBOs: [0]=scene(upscaled base), [1]=bright, [2]/[3]=blur ping-pong
    private final int[] fbo = new int[4];
    private final int[] fboTex = new int[4];

    private final FloatBuffer quadVertices;
    private final FloatBuffer quadTexCoords;
    private final float[] mvpIdentity = new float[16];

    public PostProcessRenderer() {
        Matrix.setIdentityM(mvpIdentity, 0);

        float[] verts = {
                -1f, -1f, 0f,
                 1f, -1f, 0f,
                -1f,  1f, 0f,
                 1f,  1f, 0f
        };
        float[] tex = {
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f
        };
        quadVertices = makeBuffer(verts);
        quadTexCoords = makeBuffer(tex);
    }

    private static FloatBuffer makeBuffer(float[] data) {
        FloatBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(data).position(0);
        return buf;
    }

    /** Dipanggil dari thread manapun (mis. MainActivity.onCreate). Blocking sampai
     *  GL context siap & SurfaceTexture terbentuk, atau timeout. */
    public Surface getInputSurface(long timeoutMs) {
        try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        return inputSurface;
    }

    public void setSourceSize(int w, int h) {
        this.sourceWidth = Math.max(1, w);
        this.sourceHeight = Math.max(1, h);
    }

    private volatile boolean pipelineReady = false;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            oesTextureId = tex[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            surfaceTexture = new SurfaceTexture(oesTextureId);
            surfaceTexture.setOnFrameAvailableListener(this);
            inputSurface = new Surface(surfaceTexture);

            progUpscale = ShaderHelpersBridge.buildProgram(
                    GLSL.PASSTHROUGH_VERT, GLSL.SOURCE_UPSCALE_FRAG);
            progBrightpass = ShaderHelpersBridge.buildProgram(
                    GLSL.PASSTHROUGH_VERT, GLSL.BRIGHTPASS_FRAG);
            progBlur = ShaderHelpersBridge.buildProgram(
                    GLSL.PASSTHROUGH_VERT, GLSL.GAUSSIAN_BLUR_FRAG);
            progComposite = ShaderHelpersBridge.buildProgram(
                    GLSL.PASSTHROUGH_VERT, GLSL.COMPOSITE_FRAG);

            allocateFbos();

            pipelineReady = true;
        } catch (Throwable t) {
            // JANGAN biarkan exception nembus keluar dari GL thread - itu bisa
            // matiin seluruh proses aplikasi. Kalau gagal, kita fallback:
            // - inputSurface tetap null kalau gagal sebelum sempat dibuat
            //   -> lambda$onCreate$0 di MainActivity otomatis pakai Surface asli
            // - kalau inputSurface SUDAH sempat dibuat tapi shader gagal,
            //   pipelineReady tetap false -> onDrawFrame skip semua kerja GL
            android.util.Log.e("PostProcessRenderer",
                    "Gagal init GL pipeline, fallback ke passthrough tanpa efek", t);
            pipelineReady = false;
        } finally {
            // Selalu buka latch supaya getInputSurface() di caller gak nunggu
            // penuh 2 detik kalau memang sudah pasti gagal/berhasil di sini.
            readyLatch.countDown();
        }
    }

    private void allocateFbos() {
        GLES20.glGenFramebuffers(4, fbo, 0);
        GLES20.glGenTextures(4, fboTex, 0);
        for (int i = 0; i < 4; i++) {
            // Bloom pass (index 1..3) dirender di resolusi lebih kecil (1/2) buat hemat GPU;
            // index 0 (base upscaled) full-res.
            int w = (i == 0) ? sourceWidth : Math.max(1, sourceWidth / 2);
            int h = (i == 0) ? sourceHeight : Math.max(1, sourceHeight / 2);
            setupFbo(fbo[i], fboTex[i], w, h);
        }
    }

    private void setupFbo(int fboId, int texId, int w, int h) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texId, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public synchronized void onFrameAvailable(SurfaceTexture st) {
        frameAvailable = true;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!pipelineReady) {
            // Init gagal - jangan lakukan apapun, biar GLSurfaceView cuma nge-clear
            // layar (transparan/hitam) daripada crash. Karena Surface asli LorieView
            // sudah otomatis dipakai lagi oleh MainActivity (fallback timeout), user
            // tetap lihat desktop termux-x11 normal tanpa efek - cuma lewat SurfaceView
            // aslinya, bukan lewat GLSurfaceView overlay ini.
            return;
        }

        try {
            drawFrameInternal();
        } catch (Throwable t) {
            android.util.Log.e("PostProcessRenderer", "Error saat render frame, skip frame ini", t);
        }
    }

    private void drawFrameInternal() {
        synchronized (this) {
            if (frameAvailable) {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(oesTransform);
                frameAvailable = false;
            }
        }

        if (!enabled) {
            // fallback: langsung blit OES texture ke layar tanpa efek apapun
            drawOesDirect();
            return;
        }

        bindQuad();

        // Pass 0: upscale/sharpen dari OES -> fbo[0] (sampler2D biasa mulai sini)
        runPass(progUpscale, fbo[0], sourceWidth, sourceHeight, () -> {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
            setUniform1i(progUpscale, "u_SourceTexture", 0);
            setUniform2f(progUpscale, "u_SourceTexelSize", 1f / sourceWidth, 1f / sourceHeight);
            setUniform1f(progUpscale, "u_Sharpness", sharpness);
        });

        int halfW = Math.max(1, sourceWidth / 2);
        int halfH = Math.max(1, sourceHeight / 2);

        // Pass 1: brightpass fbo[0] -> fbo[1]
        runPass(progBrightpass, fbo[1], halfW, halfH, () -> {
            bindTex2D(0, fboTex[0]);
            setUniform1i(progBrightpass, "u_SceneTexture", 0);
            setUniform1f(progBrightpass, "u_Threshold", bloomThreshold);
            setUniform1f(progBrightpass, "u_Knee", bloomKnee);
        });

        // Pass 2: blur horizontal fbo[1] -> fbo[2]
        runPass(progBlur, fbo[2], halfW, halfH, () -> {
            bindTex2D(0, fboTex[1]);
            setUniform1i(progBlur, "u_SceneTexture", 0);
            setUniform2f(progBlur, "u_Direction", 1f / halfW, 0f);
            setUniform1f(progBlur, "u_Radius", blurRadius);
        });

        // Pass 3: blur vertical fbo[2] -> fbo[3]
        runPass(progBlur, fbo[3], halfW, halfH, () -> {
            bindTex2D(0, fboTex[2]);
            setUniform1i(progBlur, "u_SceneTexture", 0);
            setUniform2f(progBlur, "u_Direction", 0f, 1f / halfH);
            setUniform1f(progBlur, "u_Radius", blurRadius);
        });

        // Pass 4: composite fbo[0] (base) + fbo[3] (bloom) -> layar (framebuffer 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(progComposite);
        bindTex2D(0, fboTex[0]);
        setUniform1i(progComposite, "u_BaseTexture", 0);
        bindTex2D(1, fboTex[3]);
        setUniform1i(progComposite, "u_BloomTexture", 1);
        setUniform1f(progComposite, "u_BloomIntensity", bloomIntensity);
        setUniform1f(progComposite, "u_Saturation", saturation);
        setUniform1f(progComposite, "u_Vignette", vignette);
        drawQuad(progComposite);
    }

    private interface UniformBinder { void bind(); }

    private void runPass(int program, int targetFbo, int w, int h, UniformBinder binder) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(program);
        binder.bind();
        drawQuad(program);
    }

    private void bindQuad() {
        // no-op holder; vertex/texcoord di-bind ulang tiap drawQuad supaya aman
        // dipanggil lintas program (attribute location bisa beda per program).
    }

    private void drawQuad(int program) {
        int posLoc = GLES20.glGetAttribLocation(program, "a_Position");
        int texLoc = GLES20.glGetAttribLocation(program, "a_TexCoordinate");
        int mvpLoc = GLES20.glGetUniformLocation(program, "u_MVP");

        quadVertices.position(0);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, quadVertices);
        GLES20.glEnableVertexAttribArray(posLoc);

        quadTexCoords.position(0);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords);
        GLES20.glEnableVertexAttribArray(texLoc);

        if (mvpLoc >= 0) {
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpIdentity, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    private void drawOesDirect() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, sourceWidth, sourceHeight);
        GLES20.glUseProgram(progUpscale);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
        setUniform1i(progUpscale, "u_SourceTexture", 0);
        setUniform2f(progUpscale, "u_SourceTexelSize", 1f / sourceWidth, 1f / sourceHeight);
        setUniform1f(progUpscale, "u_Sharpness", 0f);
        drawQuad(progUpscale);
    }

    private void bindTex2D(int unit, int texId) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
    }

    private void setUniform1i(int program, String name, int v) {
        int loc = GLES20.glGetUniformLocation(program, name);
        if (loc >= 0) GLES20.glUniform1i(loc, v);
    }

    private void setUniform1f(int program, String name, float v) {
        int loc = GLES20.glGetUniformLocation(program, name);
        if (loc >= 0) GLES20.glUniform1f(loc, v);
    }

    private void setUniform2f(int program, String name, float a, float b) {
        int loc = GLES20.glGetUniformLocation(program, name);
        if (loc >= 0) GLES20.glUniform2f(loc, a, b);
    }
}
