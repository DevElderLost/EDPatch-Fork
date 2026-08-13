package com.termux.x11;

import static com.termux.x11.CmdEntryPoint.MSG_CALL_GET_LOGCAT_OUTPUT;
import static com.termux.x11.CmdEntryPoint.MSG_CALL_GET_X_CONNECTION;
import static com.termux.x11.CmdEntryPoint.MSG_GET_PID;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.eltechs.axs.activities.XServerDisplayActivity;
import com.eltechs.axs.helpers.UiThread;
import com.eltechs.axs.widgets.viewOfXServer.ViewOfXServer;

import java.lang.ref.WeakReference;

import android.opengl.GLSurfaceView;
import android.graphics.PixelFormat;
import com.termux.x11.OverlayShaderEffectRenderer;
import com.eltechs.ed.R;

/**
 * 原MainActivity
 */
public class MainActivity extends XServerDisplayActivity {

    static final String ACTION_STOP = "com.termux.x11.ACTION_STOP";
    private static final String TAG = "MainActivity";
    public static Handler handler = new Handler();

    private GLSurfaceView mOverlayView;
    private OverlayShaderEffectRenderer mEffectRenderer;
    private LorieView mLorieView;

    private boolean mClientConnected = false;

    Messenger mService = null;
    public static int servicePid = -1;
    boolean bound;

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onNullBinding(ComponentName name) {
            ServiceConnection.super.onNullBinding(name);
            Log.d(TAG, "ServiceConnection onNullBinding: ");
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "ServiceConnection onServiceConnected: ");
            mService = new Messenger(service);
            bound = true;

            try {
                service.linkToDeath(() -> {
                    Log.e(TAG, "断开连接，: linkToDeath");
                    mService = null;
                    bound = false;
                    Log.v("Lorie", "Disconnected");
                }, 0);

                Message msg = Message.obtain(null, MSG_GET_PID, 0, 0);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "onConnectToService: pid=" + Process.myPid());

            updateLorieView();

            // Inisialisasi overlay GLSurfaceView (fallback ke findViewById jika getRootLayout tidak ada)
            if (mOverlayView == null) {
                mOverlayView = new GLSurfaceView(MainActivity.this);
                mOverlayView.setEGLContextClientVersion(2);
                mOverlayView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                mOverlayView.setZOrderOnTop(true);
                mOverlayView.setBackgroundColor(Color.TRANSPARENT);

                mOverlayView.setClickable(false);
                mOverlayView.setFocusable(false);
                mOverlayView.setFocusableInTouchMode(false);

                // Gunakan findViewById langsung ke R.id.mainView (dari layout main.xml)
                FrameLayout root = findViewById(R.id.mainView);
                if (root != null) {
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    root.addView(mOverlayView, params);
                } else {
                    Log.e(TAG, "R.id.mainView not found - overlay tidak bisa ditambahkan");
                }

                mEffectRenderer = new OverlayShaderEffectRenderer();
                mOverlayView.setRenderer(mEffectRenderer);
                mOverlayView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

                // Mulai render
                mOverlayView.onResume();
            }

            mClientConnected = false;
            retrieveLogcatOutput();
            retrieveXConnection();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "onServiceDisconnected: 断开连接，cmd报错了吗");
            mService = null;
            bound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: 绑定服务");
        bound = bindService(new Intent(this, CmdEntryPoint.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(mConnection);
            bound = false;
        }
    }

    // Override minimal untuk lifecycle overlay saja
    @Override
    protected void onResume() {
        super.onResume();  // Panggil super untuk jalankan kode parent

        if (mOverlayView != null) {
            mOverlayView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mOverlayView != null) {
            mOverlayView.onPause();
        }

        super.onPause();  // Panggil super di akhir
    }

    @Override
    protected void onDestroy() {
        if (mEffectRenderer != null) {
            mEffectRenderer.release();
            mEffectRenderer = null;
        }
        if (mOverlayView != null) {
            mOverlayView.onPause();
            mOverlayView = null;
        }

        super.onDestroy();
    }

    // Method toggle efek (bisa dipanggil kapan saja)
    private void toggleEffect(boolean enable) {
        if (mEffectRenderer != null) {
            mEffectRenderer.setEnabled(enable);
            if (mOverlayView != null) {
                mOverlayView.requestRender();
            }
        }
    }

    public void updateLorieView() {
        // Kode existing Anda (kosong atau custom)
    }

    public void onCreate() {
        // Kode existing Anda
    }

    final Messenger mMessenger = new Messenger(new ReceiveFdHandler(this));

    private void retrieveLogcatOutput() {
        try {
            Message msg = Message.obtain(null, MSG_CALL_GET_LOGCAT_OUTPUT, 0, 0);
            msg.replyTo = mMessenger;
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void retrieveXConnection() {
        try {
            Message msg = Message.obtain(null, MSG_CALL_GET_X_CONNECTION, 0, 0);
            msg.replyTo = mMessenger;
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    boolean sendDone = false;

    private void onReceiveLogcatOutput(ParcelFileDescriptor fd) {
        try {
            Log.v("LorieBroadcastReceiver", "Extracting logcat fd.");
            if (fd != null)
                LorieView.startLogcat(fd.detachFd());
        } catch (Exception e) {
            Log.e("MainActivity", "Something went wrong while we were establishing connection", e);
        }
    }

    private void onReceiveXConnection(ParcelFileDescriptor fd) {
        if (mClientConnected) return;
        try {
            Log.v("LorieBroadcastReceiver", "Extracting X connection socket.");
            if (fd != null) {
                LorieView.connect(fd.detachFd());
                getLorieView().triggerCallback();
                clientConnectedStateChanged(true);
            } else {
                handler.postDelayed(this::retrieveXConnection, 500);
                Log.d(TAG, "tryConnect: 未获取fd无法连接，半秒钟后重试");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Something went wrong while we were establishing connection", e);
            getLorieView().regenerate();
        }
    }

    private void onReceiveServicePid(int pid) {
        servicePid = pid;
    }

    @Deprecated
    void tryConnect() {
    }

    public LorieView getLorieView() {
        return mLorieView;
    }

    @SuppressWarnings("SameParameterValue")
    void clientConnectedStateChanged(boolean connected) {
        UiThread.post(() -> {
            mClientConnected = connected;
            if (!connected) retrieveXConnection();
        });
    }

    static class ReceiveFdHandler extends Handler {
        WeakReference<MainActivity> mHost;

        public ReceiveFdHandler(MainActivity mainActivity) {
            mHost = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CALL_GET_LOGCAT_OUTPUT:
                    if (mHost.get() != null)
                        mHost.get().onReceiveLogcatOutput((ParcelFileDescriptor) msg.obj);
                    break;
                case MSG_CALL_GET_X_CONNECTION:
                    if (mHost.get() != null)
                        mHost.get().onReceiveXConnection((ParcelFileDescriptor) msg.obj);
                    break;
                case MSG_GET_PID:
                    if (mHost.get() != null)
                        mHost.get().onReceiveServicePid(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}