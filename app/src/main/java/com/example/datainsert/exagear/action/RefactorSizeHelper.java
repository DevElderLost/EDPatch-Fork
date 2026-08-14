package com.example.datainsert.exagear.action;

import android.content.SharedPreferences;
import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.configuration.UBTLaunchConfiguration;
import com.eltechs.ed.EDApplicationState;
import com.example.datainsert.exagear.QH;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * "Refactor Size" - toggles borderless-fullscreen for the foreground guest
 * window, using the same SetWindowLong/SetWindowPos technique as
 * Winlator-Ludashi-test's wn-refactor-size helper.
 *
 * IMPORTANT (fixed after real-device testing, see x86-stderr.txt):
 * libubt/lkv_LoadApp execve()s guestExecutable *directly* - it does NOT
 * know how to run a Windows PE on its own. Pointing guestExecutable
 * straight at refactorsize.exe fails immediately with ENOEXEC:
 *   "lkv_LoadApp: execve() is guaranteed to fail with -8 (ENOEXEC ...)"
 * Every real launch path in this codebase (CreateLaunchConfiguration,
 * OptionTaskMgr) instead execve()s a real ELF/script (bash, or a tiny
 * shell wrapper with a #!/bin/bash shebang - scripts are exec()-able via
 * the kernel's binfmt_script, a PE is not) which then runs `wine ...`
 * *inside* the guest. This class now follows OptionTaskMgr's proven
 * pattern exactly: reuse the wrapper script + eval trick (OptionTaskMgr's
 * own comments note that pointing guestExecutable straight at "/bin/bash"
 * without going through the wrapper script did not work either - "找不到
 * eval ..." / "couldn't find eval ...").
 *
 * Also critical: a fresh UBTLaunchConfiguration does NOT attach to the
 * already-running wineserver/X session - it must clone fsRoot,
 * guestExecutablePath, guestEnvironmentVariables, vfsHacks and
 * socketPathSuffix from the *currently running* config
 * (EDApplicationState.getUBTLaunchConfiguration()), exactly like
 * OptionTaskMgr does, otherwise this either fails to find the guest
 * environment or spins up a disconnected session that can't see/resize
 * the actual foreground window.
 */
public class RefactorSizeHelper {
    private static final String TAG = "RefactorSizeHelper";
    private static final String PREF_KEY = "refactor_size_enabled";
    private static final long HELPER_EXE_BYTES = 16384L;
    private static final String WRAPPER_SCRIPT_NAME = "run-refactorsize.sh";
    private static final String EXE_GUEST_PATH = "Z:\\opt\\edpatch\\refactorsize.exe";

    public static boolean isEnabled() {
        return QH.getPreference().getBoolean(PREF_KEY, false);
    }

    /** Called from the options grid item. Flips state and runs the helper. */
    public static void toggle() {
        boolean newState = !isEnabled();
        apply(newState);
        SharedPreferences.Editor editor = QH.getPreference().edit();
        editor.putBoolean(PREF_KEY, newState);
        editor.apply();
    }

    private static void apply(boolean enabled) {
        try {
            stageHelperExe();
            String shPath = stageWrapperScript();

            EDApplicationState appState = (EDApplicationState) Globals.getApplicationState();
            UBTLaunchConfiguration oldConfig = appState.getUBTLaunchConfiguration();
            if (oldConfig == null) {
                Log.e(TAG, "apply: no running UBTLaunchConfiguration, is the guest actually started?");
                return;
            }

            String evalArg = "eval \"wine '" + EXE_GUEST_PATH + "' " + (enabled ? "on" : "off") + "\"";

            UBTLaunchConfiguration newConfig = new UBTLaunchConfiguration();
            // Clone the running session's context so we attach to the SAME
            // wineserver/X display instead of a disconnected one - this is
            // the part a from-scratch UBTLaunchConfiguration gets wrong.
            newConfig.setFsRoot(oldConfig.getFsRoot());
            newConfig.setGuestExecutablePath(oldConfig.getGuestExecutablePath());
            newConfig.setGuestEnvironmentVariables(oldConfig.getGuestEnvironmentVariables());
            newConfig.setVfsHacks(oldConfig.getVfsHacks());
            newConfig.setSocketPathSuffix(oldConfig.getSocketPathSuffix());

            // guestExecutable must be something execve()-able directly
            // (ELF or a script with a shebang) - never the .exe itself.
            newConfig.setGuestExecutable(shPath);
            newConfig.setGuestArguments(Arrays.asList(shPath, evalArg));

            appState.getEnvironment()
                    .getComponent(com.eltechs.axs.environmentService.components.GuestApplicationsTrackerComponent.class)
                    .startGuestApplication(newConfig);
        } catch (Exception e) {
            Log.e(TAG, "apply: failed to toggle refactor size", e);
        }
    }

    /**
     * Writes the tiny "#!/bin/bash\neval "$@"" wrapper used by every
     * one-off guest command in this codebase (see OptionTaskMgr.getShPath())
     * and returns its path relative to the exagear image root, i.e. the
     * path the guest side (UBT) understands.
     */
    private static String stageWrapperScript() {
        File imagePath = ((ExagearImageAware) Globals.getApplicationState()).getExagearImage().getPath();
        File shFile = new File(QH.Files.edPatchDir(), WRAPPER_SCRIPT_NAME);
        try {
            if (!shFile.exists()) {
                FileUtils.writeStringToFile(shFile, "#!/bin/bash\neval \"$@\"");
                //noinspection ResultOfMethodCallIgnored
                shFile.setExecutable(true, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "stageWrapperScript: failed to write wrapper script", e);
        }
        return shFile.getAbsolutePath().replace(imagePath.getAbsolutePath(), "");
    }

    /**
     * Copies refactorsize.exe from assets into <exagear image>/opt/edpatch,
     * same convention FuncFAB already uses for pulseaudio/renderer files.
     */
    private static void stageHelperExe() {
        try {
            File dst = new File(QH.Files.edPatchDir(), "refactorsize.exe");
            if (dst.exists() && dst.length() == HELPER_EXE_BYTES) return;

            try (InputStream in = Globals.getAppContext().getAssets().open("refactorsize/refactorsize.exe");
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setExecutable(true, false);
            Log.i(TAG, "stageHelperExe: staged refactorsize.exe (" + dst.length() + " B) at " + dst.getPath());
        } catch (Exception e) {
            Log.e(TAG, "stageHelperExe: helper staging failed", e);
        }
    }
}
