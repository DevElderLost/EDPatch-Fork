package com.example.datainsert.exagear.controlsV2;

import static com.example.datainsert.exagear.controlsV2.axs.XKeyButton.POINTER_CENTER;
import static com.example.datainsert.exagear.controlsV2.axs.XKeyButton.POINTER_LEFT;
import static com.example.datainsert.exagear.controlsV2.axs.XKeyButton.POINTER_RIGHT;
import static com.example.datainsert.exagear.controlsV2.axs.XKeyButton.POINTER_SCROLL_DOWN;
import static com.example.datainsert.exagear.controlsV2.axs.XKeyButton.POINTER_SCROLL_UP;

import android.graphics.Matrix;
import android.support.annotation.IntDef;
import android.util.Log;

import com.eltechs.axs.geom.Point;
import com.example.datainsert.exagear.controlsV2.axs.XKeyButton;
import com.example.datainsert.exagear.controlsV2.gamepad.GamepadServer;
import com.example.datainsert.exagear.controlsV2.gamepad.GamepadState;

import java.util.List;

/**
 * 有关xserver相关的视图，输入事件
 * <br/>内部存储的xserver可能是null，但是外部调用此类方法时，不应报错，只是不会运行。
 * <br/> 也应该注意，此类方法如果返回了xserver相关的类对象，那么后续在外面对相关对象的调用可能还会报错。
 */
public interface XServerViewHolder {

    public final static int SCALE_FULL_WITH_RATIO = 1;
    public final static int SCALE_FULL_IGNORE_RATIO = 2;

    Matrix getXServerToViewTransformationMatrix();

    Matrix getViewToXServerTransformationMatrix();

    void setViewToXServerTransformationMatrix(Matrix matrix);

    /**
     * 用于控制屏幕缩放。如果没有实现，可以传入XZoomHandler.EMPTY
     */
    XZoomHandler getZoomHandler();

    /**
     * 设置x屏幕上可见的范围的起始位置和宽高（相对完整可见区域）,用于屏幕缩放时
     */
    void setXViewport(float l, float t, float r, float b);

    /**
     * pressKeyOrPointer 这几个改到接口作为default，因为这个pointer还是key的区分是我自己定义的，不属于通用规则，子类不应该管这些的实现
     */
    default void pressKeyOrPointer(int keycode) {
        if (keycode >= XKeyButton.GAMEPAD_MASK) {
            setGamepadInput(keycode - XKeyButton.GAMEPAD_MASK, true);
        } else if ((keycode & XKeyButton.POINTER_MASK) == 0)
            injectKeyPress(keycode); //+8在impl里加吧
        else {
            int buttonCode = keycode - XKeyButton.POINTER_MASK;
            if (buttonCode == POINTER_SCROLL_UP || buttonCode == POINTER_SCROLL_DOWN)
                XServerViewHolder_MouseWheelInjector.getByCode(buttonCode).start();
            else if(buttonCode == POINTER_LEFT || buttonCode == POINTER_RIGHT || buttonCode == POINTER_CENTER)
                injectPointerButtonPress(buttonCode);
            else
                Log.w("KEY", "pressKeyOrPointer: 输入鼠标按键码无法识别："+buttonCode);
        }
    }

    default void releaseKeyOrPointer(int keycode) {
        if (keycode >= XKeyButton.GAMEPAD_MASK) {
            setGamepadInput(keycode - XKeyButton.GAMEPAD_MASK, false);
        } else if ((keycode & XKeyButton.POINTER_MASK) == 0)
            injectKeyRelease(keycode); //+8在impl里加吧
        else {
            int buttonCode = keycode - XKeyButton.POINTER_MASK;
            if (buttonCode == POINTER_SCROLL_UP || buttonCode == POINTER_SCROLL_DOWN)
                XServerViewHolder_MouseWheelInjector.getByCode(buttonCode).stop();
            else if(buttonCode == POINTER_LEFT || buttonCode == POINTER_RIGHT || buttonCode == POINTER_CENTER)
                injectPointerButtonRelease(buttonCode);
            else
                Log.w("KEY", "releaseKeyOrPointer: 输入鼠标按键码无法识别："+buttonCode);
        }
    }

    /**
     * Rute input tombol/arah-stick GAMEPAD_* (lihat {@link XKeyButton#GAMEPAD_MASK}) ke
     * {@link GamepadServer} alih-alih ke X11 (jalur ini TIDAK lewat X server sama sekali,
     * karena XInput/DirectInput di guest dibaca lewat socket UDP GamepadServer, bukan X11).
     * @param gamepadCode nilai GAMEPAD_* di {@link XKeyButton} (setelah dikurangi GAMEPAD_MASK)
     * @param down true = ditekan, false = dilepas
     */
    default void setGamepadInput(int gamepadCode, boolean down) {
        GamepadServer.getInstance().switchToVirtual(); // pastikan mode virtual aktif, bukan fisik (lihat GamepadServer)
        GamepadState state = GamepadServer.getInstance().getState();
        switch (gamepadCode) {
            case XKeyButton.GAMEPAD_A: state.setPressed(GamepadState.IDX_BUTTON_A, down); break;
            case XKeyButton.GAMEPAD_B: state.setPressed(GamepadState.IDX_BUTTON_B, down); break;
            case XKeyButton.GAMEPAD_X: state.setPressed(GamepadState.IDX_BUTTON_X, down); break;
            case XKeyButton.GAMEPAD_Y: state.setPressed(GamepadState.IDX_BUTTON_Y, down); break;
            case XKeyButton.GAMEPAD_L1: state.setPressed(GamepadState.IDX_BUTTON_L1, down); break;
            case XKeyButton.GAMEPAD_R1: state.setPressed(GamepadState.IDX_BUTTON_R1, down); break;
            case XKeyButton.GAMEPAD_L3: state.setPressed(GamepadState.IDX_BUTTON_L3, down); break;
            case XKeyButton.GAMEPAD_R3: state.setPressed(GamepadState.IDX_BUTTON_R3, down); break;
            case XKeyButton.GAMEPAD_SELECT: state.setPressed(GamepadState.IDX_BUTTON_SELECT, down); break;
            case XKeyButton.GAMEPAD_START: state.setPressed(GamepadState.IDX_BUTTON_START, down); break;
            case XKeyButton.GAMEPAD_L2:
                state.triggerL = down ? 1f : 0f;
                state.setPressed(GamepadState.IDX_BUTTON_L2, down);
                break;
            case XKeyButton.GAMEPAD_R2:
                state.triggerR = down ? 1f : 0f;
                state.setPressed(GamepadState.IDX_BUTTON_R2, down);
                break;
            case XKeyButton.GAMEPAD_DPAD_UP: state.dpad[0] = down; break;
            case XKeyButton.GAMEPAD_DPAD_RIGHT: state.dpad[1] = down; break;
            case XKeyButton.GAMEPAD_DPAD_DOWN: state.dpad[2] = down; break;
            case XKeyButton.GAMEPAD_DPAD_LEFT: state.dpad[3] = down; break;
            case XKeyButton.GAMEPAD_LEFT_THUMB_UP: state.thumbLY = down ? -1f : 0f; break;
            case XKeyButton.GAMEPAD_LEFT_THUMB_DOWN: state.thumbLY = down ? 1f : 0f; break;
            case XKeyButton.GAMEPAD_LEFT_THUMB_RIGHT: state.thumbLX = down ? 1f : 0f; break;
            case XKeyButton.GAMEPAD_LEFT_THUMB_LEFT: state.thumbLX = down ? -1f : 0f; break;
            case XKeyButton.GAMEPAD_RIGHT_THUMB_UP: state.thumbRY = down ? -1f : 0f; break;
            case XKeyButton.GAMEPAD_RIGHT_THUMB_DOWN: state.thumbRY = down ? 1f : 0f; break;
            case XKeyButton.GAMEPAD_RIGHT_THUMB_RIGHT: state.thumbRX = down ? 1f : 0f; break;
            case XKeyButton.GAMEPAD_RIGHT_THUMB_LEFT: state.thumbRX = down ? -1f : 0f; break;
            default:
                Log.w("KEY", "setGamepadInput: kode gamepad tidak dikenal: " + gamepadCode);
                return;
        }
        GamepadServer.getInstance().pushStateToClients();
    }

    default void pressKeyOrPointer(List<Integer> keycodes) {
        for (int keycode : keycodes)
            pressKeyOrPointer(keycode);
    }

    default void releaseKeyOrPointer(List<Integer> keycodes) {
        for (int keycode : keycodes)
            releaseKeyOrPointer(keycode);
    }

    /**同 {@link #injectKeyPress(int, int)}*/
    default void injectKeyPress(int keycode){
        injectKeyPress(keycode,0);
    }

    /**
     * 输入（键盘）按键按下事件。
     * @param keycode <a href="https://elixir.bootlin.com/linux/v6.0.2/source/include/uapi/linux/input-event-codes.h">linux的keycode</a>,不像exa中那样+8
     * @param keySym 用于表示同键位不同字符，或unicode字符  <a href="https://github.com/D-Programming-Deimos/libX11/blob/master/c/X11/keysymdef.h">参考此代码</a>
     */
    void injectKeyPress(int keycode, int keySym);

    /**同 {@link  #injectKeyRelease(int, int)}*/
    default void injectKeyRelease(int keycode){
        injectKeyRelease(keycode,0);
    }

    /** 参考 {@link #injectKeyPress(int, int)}*/
    void injectKeyRelease(int keycode, int keySym);

    void injectPointerMove(float x, float y);

    default void injectPointerDelta(float x, float y) {
        injectPointerDelta(x, y, 1);
    }

    void injectPointerDelta(float x, float y, int times);

    /**
     * Pindahkan pointer x-server langsung ke koordinat (x, y) lewat jalur "warp" (setara
     * XWarpPointer/SetCursorPos), BUKAN lewat jalur gerakan mouse biasa (injectPointerMove).
     * <br/>Dipakai oleh mode force-center-cursor pada joystick-mouse-move, supaya pointer bisa
     * "diteleport" balik ke tengah layar setiap kali mendekati batas x-server, tanpa recenter
     * ini ikut dihitung sebagai gerakan kamera oleh game/guest.
     */
    void injectPointerWarp(float x, float y);

    /**
     * Shortcut: warp pointer ke tengah layar x-server saat ini. Aman dipanggil berkali-kali;
     * tidak melakukan apa pun jika ukuran layar x-server belum tersedia (misal xserver belum siap).
     */
    default void injectPointerWarpToCenter() {
        int[] screen = getXScreenPixels();
        if (screen == null || screen.length < 2 || screen[0] <= 0 || screen[1] <= 0)
            return;
        injectPointerWarp(screen[0] / 2f, screen[1] / 2f);
    }

    void injectPointerButtonPress(int button);

    void injectPointerButtonRelease(int button);

    void injectPointerWheelUp(int times);

    void injectPointerWheelDown(int times);

    int[] getXScreenPixels();

    int[] getAndroidViewPixels();

    Point getPointerLocation();

    boolean isShowCursor();

    void setShowCursor(boolean showCursor);

    @ScaleStyle
    int getScaleStyle();

    void setScaleStyle(@ScaleStyle int scaleStyle);

    @IntDef(value = {SCALE_FULL_WITH_RATIO, SCALE_FULL_IGNORE_RATIO})
    @interface ScaleStyle {
    }

}

