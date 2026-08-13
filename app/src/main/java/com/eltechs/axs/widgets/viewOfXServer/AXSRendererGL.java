package com.eltechs.axs.widgets.viewOfXServer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView.Renderer;
import com.eltechs.axs.configuration.XServerViewConfiguration;
import com.eltechs.axs.geom.Point;
import com.eltechs.axs.geom.Rectangle;
import com.eltechs.axs.geom.RectangleF;
import com.eltechs.axs.graphicsScene.SceneOfRectangles;
import com.eltechs.axs.xserver.LocksManager.Subsystem;
import com.eltechs.axs.xserver.LocksManager.XLock;
import com.eltechs.axs.xserver.PlacedDrawable;
import com.eltechs.axs.xserver.ScreenInfo;
import com.eltechs.axs.xserver.ViewFacade;
import com.eltechs.axs.xserver.Window;
import com.eltechs.axs.xserver.WindowAttributeNames;
import com.eltechs.axs.xserver.impl.drawables.gl.PersistentGLDrawable;
import com.eltechs.axs.xserver.impl.masks.Mask;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class AXSRendererGL implements Renderer {
    private boolean active;
    private boolean created = false;
    private PlacedDrawable cursorDrawable;
    private double fps = 0.0d;
    private final Bitmap fpsBitmap = Bitmap.createBitmap(256, 64, Config.ARGB_8888);
    private final Canvas fpsCanvas = new Canvas(this.fpsBitmap);
    private final Paint fpsPaint = new Paint();
    private int frameCount = 0;
    private boolean freeze;
    private int glViewportHeight;
    private int glViewportWidth;
    final ViewOfXServer host;
    private final Bitmap invisibleCursorBitmap;
    private long lastTime = System.nanoTime();
    private final Bitmap rootCursorBitmap;
    private int scHeight;
    private int scWidth;
    private int scX;
    private int scY;
    private SceneOfRectangles scene;
    private final ViewFacade viewFacade;
    private List<PersistentGLDrawable> windowDrawables;
    private RectangleF xViewport;

    // Tambahan untuk mendeteksi perubahan mode direct
    private boolean wasDirectMode = false;

    public AXSRendererGL(ViewOfXServer viewOfXServer, ViewFacade viewFacade) {
        this.host = viewOfXServer;
        this.viewFacade = viewFacade;
        this.cursorDrawable = null;
        ScreenInfo screenInfo = viewFacade.getScreenInfo();
        this.xViewport = new RectangleF(0.0f, 0.0f, (float) screenInfo.widthInPixels, (float) screenInfo.heightInPixels);
        this.rootCursorBitmap = createXCursorBitmap();
        this.invisibleCursorBitmap = createInvisibleCursorBitmap();
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        synchronized (this) {
            XLock lock = viewFacade.getXServer().getLocksManager().lock(Subsystem.DRAWABLES_MANAGER);
            try {
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                GLES20.glScissor(scX, glViewportHeight - (scY + scHeight), scWidth, scHeight);

                if (freeze) {
                    scene.draw();
                } else {
                    // ==================== OPTIMASI DIRECT RENDERING ====================
                    PlacedDrawable directCandidate = findExactFullscreenDrawable();

                    boolean isDirectMode = (directCandidate != null);

                    if (isDirectMode != wasDirectMode) {
                        wasDirectMode = isDirectMode;
                        // Opsional: bisa tambah log atau invalidate jika diperlukan
                    }

                    if (isDirectMode) {
                        // Direct path: hanya render satu window besar
                        GLES20.glDisable(GLES20.GL_BLEND);

                        PersistentGLDrawable glDrawable = (PersistentGLDrawable) directCandidate.getDrawable();
                        scene.updateTextureFromDrawable(0, glDrawable);

                        Rectangle loc = directCandidate.getLocation();

                        scene.placeRectangle(
                                0,                          // texture slot
                                (float) loc.x,
                                (float) -loc.y,             // OpenGL Y terbalik
                                (float) loc.width,
                                (float) loc.height,
                                1.0f,                       // z-order tertinggi
                                0,                          // texture index
                                1.0f,                       // alpha
                                false
                        );

                        scene.draw();
                    } else {
                        // Normal path: render semua window
                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                        reloadWindowTextures(0);
                        reloadCursorTexture(windowDrawables != null ? windowDrawables.size() : 0);
                        scene.draw();
                    }

                    // Cursor selalu di atas jika dibutuhkan
                    if (getConfiguration().isCursorShowNeeded()) {
                        GLES20.glEnable(GLES20.GL_BLEND);
                        reloadCursorTexture(windowDrawables != null ? windowDrawables.size() : 0);
                        scene.draw();  // bisa dioptimasi hanya draw cursor jika scene mendukung
                    }
                }

//                calculateFPS();

            } finally {
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
                if (lock != null) {
                    lock.close();
                }
            }
        }
    }

    /**
     * Mencari PlacedDrawable yang ukurannya SAMA PERSIS dengan ukuran screen
     */
    private PlacedDrawable findExactFullscreenDrawable() {
        ScreenInfo si = viewFacade.getScreenInfo();
        int screenW = si.widthInPixels;
        int screenH = si.heightInPixels;

        List<PlacedDrawable> drawables = viewFacade.listNonRootWindowDrawables();

        // Iterasi dari atas (top-most window dulu)
        for (int i = drawables.size() - 1; i >= 0; i--) {
            PlacedDrawable pd = drawables.get(i);
            if (pd == null) continue;

            Rectangle loc = pd.getLocation();

            // Harus sama persis ukurannya
            if (loc.width == screenW && loc.height == screenH) {
                return pd;
            }
        }
        return null;
    }

    private void reloadWindowTextures(int startIndex) {
        if (windowDrawables == null || scene == null) {
            return;
        }

        int idx = startIndex;
        for (PersistentGLDrawable drawable : windowDrawables) {
            scene.updateTextureFromDrawable(idx, drawable);
            idx++;
        }
    }

    private void reloadCursorTexture(int slot) {
        if (!getConfiguration().isCursorShowNeeded()) {
            scene.updateTextureFromBitmap(slot, invisibleCursorBitmap);
            return;
        }

        if (cursorDrawable == null) {
            scene.updateTextureFromBitmap(slot, rootCursorBitmap);
        } else {
            scene.updateTextureFromDrawable(slot, (PersistentGLDrawable) cursorDrawable.getDrawable());
        }
    }

    private void recreateSceneOfXServer() {
        if (!created) {
            return;
        }

        ArrayList listNonRootWindowDrawables = this.viewFacade.listNonRootWindowDrawables();
        int size = listNonRootWindowDrawables.size();
        int totalSlots = size + 1;  // +1 untuk cursor

        this.windowDrawables = new ArrayList(size);
        setScene(new SceneOfRectangles(totalSlots, totalSlots));
        updateSceneViewports();

        int i = 0;
        while (i < size) {
            PlacedDrawable placedDrawable = (PlacedDrawable) listNonRootWindowDrawables.get(i);
            this.windowDrawables.add((PersistentGLDrawable) placedDrawable.getDrawable());
            placeDrawable(i, size - i + 1, placedDrawable, true);
            i++;
        }

        placeCursor(size);
    }

    private void updateSceneViewports() {
        if (this.scene == null) {
            return;
        }

        XServerViewConfiguration configuration = getConfiguration();
        TransformationDescription td = TransformationHelpers.makeTransformationDescription(
                (float) this.glViewportWidth,
                (float) this.glViewportHeight,
                this.xViewport.x,
                this.xViewport.y,
                this.xViewport.width,
                this.xViewport.height,
                configuration.getFitStyleHorizontal(),
                configuration.getFitStyleVertical()
        );

        this.scene.setSceneViewport(this.xViewport.x, -this.xViewport.y, this.xViewport.width, this.xViewport.height);
        this.scene.setViewport(
                td.viewTranslateX / (float) this.glViewportWidth,
                td.viewTranslateY / (float) this.glViewportHeight,
                (this.xViewport.width * td.scaleX) / (float) this.glViewportWidth,
                (this.xViewport.height * td.scaleY) / (float) this.glViewportHeight
        );

        this.scX = (int) Math.ceil((double) (td.xServerTranslateX * td.scaleX + td.viewTranslateX));
        this.scY = (int) Math.ceil((double) (td.xServerTranslateY * td.scaleY + td.viewTranslateY));
        this.scWidth = (int) (((float) this.viewFacade.getScreenInfo().widthInPixels) * td.scaleX);
        this.scHeight = (int) (td.scaleY * (float) this.viewFacade.getScreenInfo().heightInPixels);
    }

    private Bitmap createXCursorBitmap() {
        Bitmap createBitmap = Bitmap.createBitmap(10, 10, Config.ARGB_8888);
        for (int i = 0; i < 10; i++) {
            createBitmap.setPixel(i, i, -1);
            createBitmap.setPixel(i, 9 - i, -1);
        }
        return createBitmap;
    }

    private Bitmap createInvisibleCursorBitmap() {
        Bitmap createBitmap = Bitmap.createBitmap(1, 1, Config.ALPHA_8);
        createBitmap.setPixel(0, 0, 0);
        return createBitmap;
    }

    private void placeDrawable(int i, int i2, PlacedDrawable placedDrawable, boolean z) {
        Rectangle location = placedDrawable.getLocation();
        this.scene.setTextureSize(i, location.width, location.height);
        this.scene.placeRectangle(i, (float) location.x, (float) (-location.y), (float) location.width, (float) location.height, (float) i2, i, 1.0f, z);
    }

    private void placeCursor(int i) {
        this.cursorDrawable = this.viewFacade.getCursorDrawable();
        if (this.cursorDrawable == null) {
            Point pointerLocation = this.viewFacade.getPointerLocation();
            int width = pointerLocation.x - (this.rootCursorBitmap.getWidth() / 2);
            int height = pointerLocation.y - (this.rootCursorBitmap.getHeight() / 2);
            this.scene.setTextureSize(i, this.rootCursorBitmap.getWidth(), this.rootCursorBitmap.getHeight());
            this.scene.placeRectangle(i, (float) width, (float) (-height), (float) this.rootCursorBitmap.getWidth(), (float) this.rootCursorBitmap.getHeight(), 1.0f, i, 1.0f, false);
            return;
        }
        placeDrawable(i, 1, this.cursorDrawable, false);
    }

    private void setScene(SceneOfRectangles sceneOfRectangles) {
        if (this.scene != null) {
            this.scene.destroy();
        }
        this.scene = sceneOfRectangles;
    }

    private void calculateFPS() {
        long nanoTime = System.nanoTime();
        this.frameCount++;
        if (nanoTime - this.lastTime >= 1000000000L) {
            this.fps = (((double) this.frameCount) * 1.0E9d) / ((double) (nanoTime - this.lastTime));
            this.frameCount = 0;
            this.lastTime = nanoTime;
            this.fpsCanvas.drawColor(-16777216, Mode.CLEAR);
            this.fpsCanvas.drawText("FPS: " + String.format("%.1f", this.fps), 10.0f, 40.0f, this.fpsPaint);
        }
    }

    public synchronized void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(2884);   // GL_CULL_FACE
        GLES20.glEnable(2929);   // GL_DEPTH_TEST
        GLES20.glEnable(3042);   // GL_BLEND
        GLES20.glBlendFunc(770, 771);
        this.created = true;
        recreateScene();
    }

    public synchronized void onSurfaceChanged(GL10 gl10, int i, int i2) {
        GLES20.glViewport(0, 0, i, i2);
        this.glViewportWidth = i;
        this.glViewportHeight = i2;
        this.active = true;
        updateSceneViewports();
        recreateSceneOfControls();
    }

    private void recreateScene() {
        recreateSceneOfXServer();
        recreateSceneOfControls();
    }

    private void recreateSceneOfControls() {
        // Kosong atau implementasi kontrol UI tambahan jika ada
    }

    private XServerViewConfiguration getConfiguration() {
        return this.host.getConfiguration();
    }

    public synchronized void setXViewport(RectangleF rectangleF) {
        this.xViewport = rectangleF;
        updateSceneViewports();
    }

    public synchronized void cursorChanged() {
        if (this.windowDrawables != null) {
            placeCursor(this.windowDrawables.size());
        }
    }

    public synchronized void windowGeometryChanged(Window window) {
        if (this.windowDrawables != null) {
            int size = this.windowDrawables.size();
            for (int i = 0; i < size; i++) {
                if (this.windowDrawables.get(i) == window.getActiveBackingStore()) {
                    moveDrawable(i, (size - i) + 1, window.getBoundingRectangle());
                    break;
                }
            }
        }
    }

    private void moveDrawable(int i, int i2, Rectangle rectangle) {
        this.scene.setTextureSize(i, rectangle.width, rectangle.height);
        this.scene.moveRectangle(i, (float) rectangle.x, (float) (-rectangle.y), (float) rectangle.width, (float) rectangle.height, (float) i2);
    }

    public synchronized void windowMapped(Window window) {
        recreateSceneOfXServer();
    }

    public synchronized void windowUnmapped(Window window) {
        recreateSceneOfXServer();
    }

    public synchronized void windowZOrderChanged(Window window) {
        recreateSceneOfXServer();
    }

    public synchronized void freeze() {
        this.freeze = true;
    }

    public synchronized void unFreeze() {
        this.freeze = false;
    }

    public synchronized void frontBufferReplaced(Window window) {
        recreateSceneOfXServer();
    }

    public synchronized void onPause() {
        this.active = false;
    }

    public synchronized void windowAttributesChanged(Window window, Mask<WindowAttributeNames> mask) {
        cursorChanged();
    }

    public synchronized void contentChanged(Window window, int i, int i2, int i3, int i4) {
        // Bisa ditambahkan invalidasi partial jika diperlukan
    }
}