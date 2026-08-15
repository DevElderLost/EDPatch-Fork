package com.example.datainsert.exagear.action;

import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.applicationState.UBTLaunchConfigurationAware;
import com.eltechs.axs.configuration.UBTLaunchConfiguration;
import com.eltechs.ed.EDApplicationState;
import com.example.datainsert.exagear.QH;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android-side counterpart to winhandler_mini.exe (see
 * app/src/main/cpp/winhandler-mini/winhandler_mini.c for the guest side).
 *
 * v2: eager start. Checked against how Winlator itself actually launches
 * its own winhandler.exe (XServerDisplayActivity#setupXEnvironment() /
 * getWineStartCommand()): it's baked directly into the SAME guestExecutable
 * string as the main exe -
 *
 *   wine explorer /desktop=shell,WxH winhandler.exe /dir <path> "game.exe" ...
 *
 * - i.e. one single guest process spawn, winhandler.exe running from the
 * very first frame, long before the user could ever reach an options menu.
 * v1 of this class instead lazily spawned the daemon on the FIRST toggle
 * click (a second, separate UBTLaunchConfiguration) - that has a real race:
 * exec() drops silently if the daemon's INIT packet hasn't arrived yet, so
 * the very first click right after entering a container could silently do
 * nothing. This revision fixes that by hooking into StartGuest.execute()
 * (see the two call sites added there: prepareForLaunch() +
 * injectIntoCommand()) so winhandler_mini.exe launches in the background of
 * the SAME guest process spawn as the main exe/game - matching Winlator's
 * pattern exactly, just via a shell `&` instead of making the daemon itself
 * the parent process (keeps winhandler_mini.c simpler, no need for it to
 * know how to CreateProcess the actual game).
 *
 * ensureStartedFallback() is kept as a defensive fallback for any launch
 * path that doesn't go through the patched StartGuest.execute() (e.g. a
 * future/alternate startup action) - it's a no-op once the eager path has
 * already claimed daemonClaimed.
 */
public class WinHandlerMini {
    private static final String TAG = "WinHandlerMini";

    /** Matches winhandler_mini.exe's CLIENT_PORT - guest listens here. */
    private static final int CLIENT_PORT = 7946;
    /** Matches winhandler_mini.exe's SERVER_PORT - Android listens here. */
    private static final int SERVER_PORT = 7947;

    private static final byte REQ_EXEC = 2;

    private static final long DAEMON_EXE_BYTES = 16384L;
    private static final String DAEMON_EXE_NAME = "winhandler_mini.exe";
    private static final String WRAPPER_SCRIPT_NAME = "run-winhandler-mini.sh";
    private static final String DAEMON_GUEST_PATH = "Z:\\opt\\edpatch\\winhandler_mini.exe";

    private static final WinHandlerMini INSTANCE = new WinHandlerMini();

    /** Whichever path (eager StartGuest hook or the fallback) gets there
     * first wins; the other becomes a no-op. In-memory only (see class doc
     * on RefactorSizeHelper for why this must never be a SharedPreference). */
    private final AtomicBoolean daemonClaimed = new AtomicBoolean(false);
    private volatile boolean initReceived = false;
    private DatagramSocket listenSocket;

    public static WinHandlerMini get() {
        return INSTANCE;
    }

    private WinHandlerMini() {}

    /**
     * Call once, early in StartGuest.execute(), BEFORE the final guest
     * command line is assembled. Stages winhandler_mini.exe on disk and
     * starts the Android-side UDP listener. Does not launch anything in
     * the guest by itself - pair with injectIntoCommand().
     */
    public void prepareForLaunch() {
        if (!daemonClaimed.compareAndSet(false, true)) return;
        startListenSocket();
        stageDaemonExe();
    }

    /**
     * Rewrites an `eval "..."` guest command string so winhandler_mini.exe
     * launches in the background (shell `&`) of the SAME guest process
     * spawn as the main exe, right before whatever wine/game command was
     * already there. No-op if prepareForLaunch() was never called (so it's
     * safe to call unconditionally), or if the string doesn't look like an
     * `eval "..."` wrapper (defensive - never breaks an unrecognized
     * command format, just leaves it untouched).
     */
    public String injectIntoCommand(String evalCmd) {
        if (evalCmd == null || !daemonClaimed.get()) return evalCmd;
        final String marker = "eval \"";
        int idx = evalCmd.indexOf(marker);
        if (idx < 0) return evalCmd;
        int insertAt = idx + marker.length();
        String daemonLaunch = "wine '" + DAEMON_GUEST_PATH + "' & ";
        return evalCmd.substring(0, insertAt) + daemonLaunch + evalCmd.substring(insertAt);
    }

    /**
     * Defensive fallback only - spawns winhandler_mini.exe as its own
     * separate guest process attached to whatever UBTLaunchConfiguration
     * is currently running. Prefer prepareForLaunch()+injectIntoCommand()
     * wired into the container's actual startup path; this exists so
     * RefactorSizeHelper degrades gracefully instead of hard-failing if
     * that wiring is ever missing for some launch path.
     */
    public void ensureStartedFallback() {
        if (!daemonClaimed.compareAndSet(false, true)) return;

        startListenSocket();

        try {
            stageDaemonExe();
            String shPath = stageWrapperScript();

            EDApplicationState appState = (EDApplicationState) Globals.getApplicationState();
            UBTLaunchConfiguration oldConfig =
                    ((UBTLaunchConfigurationAware) appState).getUBTLaunchConfiguration();
            if (oldConfig == null) {
                Log.e(TAG, "ensureStartedFallback: no running UBTLaunchConfiguration yet");
                daemonClaimed.set(false); // allow retry once a session exists
                return;
            }

            String evalArg = "eval \"wine '" + DAEMON_GUEST_PATH + "'\"";

            UBTLaunchConfiguration newConfig = new UBTLaunchConfiguration();
            newConfig.setFsRoot(oldConfig.getFsRoot());
            newConfig.setGuestExecutablePath(oldConfig.getGuestExecutablePath());
            newConfig.setGuestEnvironmentVariables(oldConfig.getGuestEnvironmentVariables());
            newConfig.setVfsHacks(oldConfig.getVfsHacks());
            newConfig.setSocketPathSuffix(oldConfig.getSocketPathSuffix());
            newConfig.setGuestExecutable(shPath);
            newConfig.setGuestArguments(Arrays.asList(shPath, evalArg));

            appState.getEnvironment()
                    .getComponent(com.eltechs.axs.environmentService.components.GuestApplicationsTrackerComponent.class)
                    .startGuestApplication(newConfig);

            Log.i(TAG, "ensureStartedFallback: winhandler_mini.exe launch requested (fallback path)");
        } catch (Exception e) {
            Log.e(TAG, "ensureStartedFallback: failed to start daemon", e);
            daemonClaimed.set(false); // allow a retry on the next call
        }
    }

    /** Fire-and-forget: tells the already-running daemon to launch
     * `filename parameters` inside the guest. No-op until the daemon's
     * INIT packet has actually been received (mirrors real WinHandler's
     * initReceived gating). With the eager path wired into StartGuest,
     * INIT should already have landed long before any caller could
     * plausibly reach this. */
    public void exec(String filename, String parameters) {
        if (!initReceived) {
            Log.w(TAG, "exec: daemon not ready yet (no INIT received), dropping: " + filename);
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                byte[] filenameBytes = filename.getBytes("UTF-8");
                byte[] parametersBytes = (parameters == null ? "" : parameters).getBytes("UTF-8");

                ByteBuffer buf = ByteBuffer.allocate(13 + filenameBytes.length + parametersBytes.length)
                        .order(ByteOrder.LITTLE_ENDIAN);
                buf.put(REQ_EXEC);
                buf.putInt(filenameBytes.length + parametersBytes.length + 8);
                buf.putInt(filenameBytes.length);
                buf.putInt(parametersBytes.length);
                buf.put(filenameBytes);
                buf.put(parametersBytes);

                DatagramSocket sendSocket = new DatagramSocket();
                InetAddress localhost = InetAddress.getByName("127.0.0.1");
                DatagramPacket packet = new DatagramPacket(buf.array(), buf.array().length, localhost, CLIENT_PORT);
                sendSocket.send(packet);
                sendSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "exec: failed to send EXEC packet for " + filename, e);
            }
        });
    }

    /** Call when tearing down the guest session (e.g. exitApp) so the
     * daemon doesn't linger as a zombie UDP listener and so the next
     * container session is allowed to actually relaunch it. */
    public void stop() {
        daemonClaimed.set(false);
        initReceived = false;
        if (listenSocket != null) {
            listenSocket.close();
            listenSocket = null;
        }
    }

    private void startListenSocket() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                listenSocket = new DatagramSocket(null);
                listenSocket.setReuseAddress(true);
                listenSocket.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));

                byte[] recvBuf = new byte[64];
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

                while (listenSocket != null && !listenSocket.isClosed()) {
                    listenSocket.receive(recvPacket);
                    if (recvPacket.getLength() < 1) continue;
                    byte requestCode = recvBuf[0];
                    if (requestCode == 1 /* REQ_INIT */) {
                        initReceived = true;
                        Log.i(TAG, "startListenSocket: received INIT from winhandler_mini.exe");
                    }
                    // No other request codes are sent by winhandler_mini.exe
                    // today; extend here if the guest side grows more.
                }
            } catch (Exception e) {
                if (!(e instanceof java.net.SocketException)) {
                    Log.e(TAG, "startListenSocket: listener died", e);
                }
            }
        });
    }

    private String stageWrapperScript() {
        File imagePath = ((ExagearImageAware) Globals.getApplicationState()).getExagearImage().getPath();
        File shFile = new File(QH.Files.edPatchDir(), WRAPPER_SCRIPT_NAME);
        try {
            // Always rewrite, same as OptionTaskMgr.getShPath() - never
            // trust a wrapper script left over from a previous build.
            shFile.delete();
            FileUtils.writeStringToFile(shFile, "#!/bin/bash\neval \"$@\"");
            //noinspection ResultOfMethodCallIgnored
            shFile.setExecutable(true, false);
        } catch (Exception e) {
            Log.e(TAG, "stageWrapperScript: failed to write wrapper script", e);
        }
        return shFile.getAbsolutePath().replace(imagePath.getAbsolutePath(), "");
    }

    private void stageDaemonExe() {
        try {
            File dst = new File(QH.Files.edPatchDir(), DAEMON_EXE_NAME);
            if (dst.exists() && dst.length() == DAEMON_EXE_BYTES) return;

            try (InputStream in = Globals.getAppContext().getAssets().open("winhandler_mini/winhandler_mini.exe");
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setExecutable(true, false);
            Log.i(TAG, "stageDaemonExe: staged winhandler_mini.exe (" + dst.length() + " B) at " + dst.getPath());
        } catch (Exception e) {
            Log.e(TAG, "stageDaemonExe: staging failed", e);
        }
    }
}
