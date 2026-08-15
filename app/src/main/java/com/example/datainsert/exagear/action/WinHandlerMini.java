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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Android-side counterpart to winhandler_mini.exe (see
 * app/src/main/cpp/winhandler-mini/winhandler_mini.c for the guest side).
 *
 * v4: switched from a UDP socket protocol to a filesystem command queue.
 * v1-v3 mirrored Winlator's WinHandler UDP protocol (127.0.0.1:7946/7947),
 * but ExaGear's own internal guest<->host IPC uses Unix abstract domain
 * sockets, not IP loopback (see UBT.smali - GuestApplicationsTrackerComponent
 * / VFSTrackerComponent both use "ua:"-prefixed socket paths) - strong
 * evidence ExaGear's guest environment may not bring up a working `lo`
 * interface at all, since ExaGear itself never needed one for its own IPC.
 * That would explain refactorsize.dat never being created even on a fresh
 * container: the EXEC command was never reaching the guest daemon at all.
 *
 * This version sidesteps networking entirely and reuses the SAME shared
 * filesystem mechanism (Z:\opt\edpatch\...) that was already proven to
 * work by the original refactorsize.dat exchange - Android drops a small
 * command file into wh_cmd\, the guest daemon polls that directory every
 * ~200ms and processes whatever it finds. No sockets, no ports, no
 * dependency on any guest network subsystem.
 *
 * Nice side effect: commands are now a durable queue instead of a fire-
 * and-forget UDP packet, so there's no more "daemon not ready yet,
 * dropping the command" race - a command written before the daemon has
 * even started polling just waits in the directory until it does.
 */
public class WinHandlerMini {
    private static final String TAG = "WinHandlerMini";

    private static final long DAEMON_EXE_BYTES = 16384L;
    private static final String DAEMON_EXE_NAME = "winhandler_mini.exe";
    private static final String WRAPPER_SCRIPT_NAME = "run-winhandler-mini.sh";
    private static final String DAEMON_GUEST_PATH = "Z:\\opt\\edpatch\\winhandler_mini.exe";
    private static final String CMD_DIR_NAME = "wh_cmd";

    private static final WinHandlerMini INSTANCE = new WinHandlerMini();

    /** True only between a prepareForLaunch() call and the matching
     * injectIntoCommand() call later in the SAME StartGuest.execute()
     * invocation - a one-shot "yes, inject" signal, not a permanent
     * latch (see RefactorSizeHelper's class doc for why permanent
     * latches are wrong here - the Android process can stay alive across
     * "exit container, re-enter container"). */
    private volatile boolean pendingInjection = false;

    /** Monotonically increasing per-process, used to name command files
     * so they sort (and therefore get processed) in write order. */
    private final AtomicLong cmdCounter = new AtomicLong(0);

    public static WinHandlerMini get() {
        return INSTANCE;
    }

    private WinHandlerMini() {}

    /**
     * Call once, early in StartGuest.execute(), BEFORE the final guest
     * command line is assembled. Ensures the command-queue directory
     * exists, stages winhandler_mini.exe, clears any leftover unprocessed
     * command files from a previous session (they can never be valid for
     * a new wine/X session anyway), and resets RefactorSizeHelper's
     * per-session state.
     */
    public void prepareForLaunch() {
        pendingInjection = true;
        ensureCmdDirClean();
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
     * patched StartGuest.execute(). Safe to call any time.
     */
    public void ensureStartedFallback() {
        try {
            ensureCmdDirClean();
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

    /**
     * Queues `filename parameters` to run inside the guest. Writes to a
     * temp file then renames it into place (atomic on the same
     * filesystem), so the daemon's directory listing never observes a
     * half-written file. Durable - safe to call even before the daemon
     * has started polling, the command just waits.
     */
    public void exec(String filename, String parameters) {
        try {
            File cmdDir = new File(QH.Files.edPatchDir(), CMD_DIR_NAME);
            //noinspection ResultOfMethodCallIgnored
            cmdDir.mkdirs();

            long n = cmdCounter.incrementAndGet();
            String name = String.format("cmd_%016d.txt", n);
            File tmp = new File(cmdDir, name + ".tmp");
            File dst = new File(cmdDir, name);

            String content = filename + "\n" + (parameters == null ? "" : parameters) + "\n";
            FileUtils.writeStringToFile(tmp, content);

            if (!tmp.renameTo(dst)) {
                Log.e(TAG, "exec: rename failed for " + name);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "exec: failed to queue command for " + filename, e);
        }
    }

    /** No persistent resources to release with the filesystem-queue
     * design (no listener socket to close), but kept for API
     * compatibility with callers (e.g. StartupActivity's quit hook). */
    public void stop() {
        pendingInjection = false;
    }

    /**
     * Creates the command directory if missing, and deletes any leftover
     * files from a previous session - they were written for a wine/X
     * session that no longer exists, so leaving them around just risks a
     * new daemon picking up a stale command on startup.
     */
    private void ensureCmdDirClean() {
        try {
            File cmdDir = new File(QH.Files.edPatchDir(), CMD_DIR_NAME);
            //noinspection ResultOfMethodCallIgnored
            cmdDir.mkdirs();
            File[] leftovers = cmdDir.listFiles();
            if (leftovers != null) {
                for (File f : leftovers) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ensureCmdDirClean: failed", e);
        }
    }

    private String stageWrapperScript() {
        File imagePath = ((ExagearImageAware) Globals.getApplicationState()).getExagearImage().getPath();
        File shFile = new File(QH.Files.edPatchDir(), WRAPPER_SCRIPT_NAME);
        try {
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
