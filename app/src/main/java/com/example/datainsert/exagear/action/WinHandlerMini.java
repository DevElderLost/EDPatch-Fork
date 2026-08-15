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

/**
 * Android-side counterpart to winhandler_mini.exe (see
 * app/src/main/cpp/winhandler-mini/winhandler_mini.c for the guest side).
 *
 * v3: fixes a cross-session state leak introduced in v2. v2 gated
 * prepareForLaunch()/injectIntoCommand() behind an AtomicBoolean that
 * latched permanently "true" the first time a container was launched -
 * the assumption was that in-memory state naturally resets every session
 * because the Android process gets recreated, mirroring how Winlator's
 * XServerDisplayActivity#isRefactorSizeEnabled (a plain instance field)
 * resets automatically when the Activity is recreated.
 *
 * That assumption was wrong for EDPatch-Fork: the app process stays alive
 * across "exit container, re-enter container" (confirmed from
 * x86-stderr(-first).txt - winhandler_mini.exe launches identically both
 * times, so the guest side was always fine; the bug was static Java state
 * on the Android side never getting reset). A permanent latch is exactly
 * as broken as the original SharedPreferences bug, just moved from disk
 * to RAM.
 *
 * Fix: state is now reset EXPLICITLY every time prepareForLaunch() runs,
 * instead of relying on process death. prepareForLaunch() is already
 * called once per container launch from StartGuest.execute() (see the
 * smali patch), so this is a reliable "a new session is starting" signal
 * regardless of whether the process itself is fresh or reused.
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

    /** True only between a prepareForLaunch() call and the matching
     * injectIntoCommand() call later in the SAME StartGuest.execute()
     * invocation - a one-shot "yes, inject" signal, not a permanent
     * latch. */
    private volatile boolean pendingInjection = false;
    private volatile boolean initReceived = false;
    private DatagramSocket listenSocket;

    public static WinHandlerMini get() {
        return INSTANCE;
    }

    private WinHandlerMini() {}

    /**
     * Call once, early in StartGuest.execute(), BEFORE the final guest
     * command line is assembled. Runs EVERY container launch (no permanent
     * latch) - tears down any listener left over from a previous session,
     * resets initReceived, stages winhandler_mini.exe, and resets
     * RefactorSizeHelper's per-session state (its enabled flag and any
     * stale refactorsize.dat from a previous wine/X session).
     */
    public void prepareForLaunch() {
        stopListenSocket();
        initReceived = false;
        pendingInjection = true;

        startListenSocket();
        stageDaemonExe();
        RefactorSizeHelper.resetForNewSession();
    }

    /**
     * Rewrites an `eval "..."` guest command string so winhandler_mini.exe
     * launches in the background (shell `&`) of the SAME guest process
     * spawn as the main exe, right before whatever wine/game command was
     * already there. Consumes the pending-injection flag set by
     * prepareForLaunch() - only actually injects once per call to
     * prepareForLaunch(). No-op if prepareForLaunch() wasn't called first,
     * or if the string doesn't look like an `eval "..."` wrapper.
     */
    public String injectIntoCommand(String evalCmd) {
        if (evalCmd == null || !pendingInjection) return evalCmd;
        pendingInjection = false;

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
     * is currently running, for launch paths that don't go through the
     * patched StartGuest.execute(). Safe to call any time; re-stages/
     * re-arms the listener the same way prepareForLaunch() does.
     */
    public void ensureStartedFallback() {
        if (initReceived) return; // a daemon already answered for this session

        try {
            if (listenSocket == null || listenSocket.isClosed()) {
                startListenSocket();
            }
            stageDaemonExe();
            String shPath = stageWrapperScript();

            EDApplicationState appState = (EDApplicationState) Globals.getApplicationState();
            UBTLaunchConfiguration oldConfig =
                    ((UBTLaunchConfigurationAware) appState).getUBTLaunchConfiguration();
            if (oldConfig == null) {
                Log.e(TAG, "ensureStartedFallback: no running UBTLaunchConfiguration yet");
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
        }
    }

    /** Fire-and-forget: tells the already-running daemon to launch
     * `filename parameters` inside the guest. No-op until the daemon's
     * INIT packet has actually been received for THIS session. */
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

    /** Call when tearing down the guest session (e.g. exitApp), if you
     * want the listener closed immediately rather than waiting for the
     * next prepareForLaunch() to tear it down. Optional - prepareForLaunch()
     * already cleans up any previous listener on its own. */
    public void stop() {
        stopListenSocket();
        initReceived = false;
        pendingInjection = false;
    }

    private void stopListenSocket() {
        if (listenSocket != null) {
            listenSocket.close();
            listenSocket = null;
        }
    }

    private void startListenSocket() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DatagramSocket sock = new DatagramSocket(null);
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));
                listenSocket = sock;

                byte[] recvBuf = new byte[64];
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

                while (listenSocket == sock && !sock.isClosed()) {
                    sock.receive(recvPacket);
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
