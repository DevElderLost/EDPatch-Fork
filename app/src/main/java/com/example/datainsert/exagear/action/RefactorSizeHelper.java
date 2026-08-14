package com.example.datainsert.exagear.action;

import static com.eltechs.axs.Globals.getApplicationState;

import android.content.SharedPreferences;
import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.EnvironmentAware;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.eltechs.axs.configuration.UBTLaunchConfiguration;
import com.eltechs.axs.environmentService.AXSEnvironment;
import com.eltechs.axs.environmentService.components.GuestApplicationsTrackerComponent;
import com.example.datainsert.exagear.QH;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * "Refactor Size" - toggles borderless-fullscreen for the foreground guest
 * window, using the same SetWindowLong/SetWindowPos technique as
 * Winlator-Ludashi-test's wn-refactor-size helper. Ported to EDPatch's own
 * runtime plumbing:
 *  - staging goes to <exagear image>/opt/edpatch/refactorsize.exe, matching
 *    where FuncFAB already keeps pulseaudio/renderer runtime files
 *  - launching goes through GuestApplicationsTrackerComponent, which is
 *    EDPatch's equivalent of Winlator's WinHandler.exec()
 *
 * Menu wiring lives in AddPopupMenuItems.addBeforeShow(), which is the
 * existing multi-finger-tap popup menu extension point.
 */
public class RefactorSizeHelper {
    private static final String TAG = "RefactorSizeHelper";
    private static final String PREF_KEY = "refactor_size_enabled";
    private static final long HELPER_EXE_BYTES = 16384L;

    public static boolean isEnabled() {
        return QH.getPreference().getBoolean(PREF_KEY, false);
    }

    /**
     * Called from the popup menu item. Flips the stored toggle state and
     * runs the guest helper with "on"/"off".
     */
    public static void toggle() {
        boolean newState = !isEnabled();
        apply(newState);
        SharedPreferences.Editor editor = QH.getPreference().edit();
        editor.putBoolean(PREF_KEY, newState);
        editor.apply();
    }

    private static void apply(boolean enabled) {
        try {
            if (enabled) stageHelperExe();

            // NOTE: unlike the .exe's own internal state-file path (which runs
            // *inside* the guest, so it uses the Wine Z: mapping), the launch
            // config below runs on the Android/host side, so it needs a real
            // Linux path - mirrors CreateLaunchConfiguration's
            // setGuestExecutablePath(applicationWorkingDir.getAbsolutePath()).
            File imagePath = ((ExagearImageAware) getApplicationState()).getExagearImage().getPath();
            File workDir = new File(imagePath, "opt/edpatch");

            UBTLaunchConfiguration cfg = new UBTLaunchConfiguration();
            cfg.setFsRoot(imagePath.getAbsolutePath());
            cfg.setGuestExecutablePath(workDir.getAbsolutePath());
            cfg.setGuestExecutable("refactorsize.exe");
            cfg.setGuestArguments(Arrays.asList("refactorsize.exe", enabled ? "on" : "off"));

            AXSEnvironment environment = ((EnvironmentAware) getApplicationState()).getEnvironment();
            if (environment == null) {
                Log.e(TAG, "apply: environment is null, guest not running?");
                return;
            }
            GuestApplicationsTrackerComponent tracker = environment.getComponent(GuestApplicationsTrackerComponent.class);
            if (tracker == null) {
                Log.e(TAG, "apply: GuestApplicationsTrackerComponent unavailable");
                return;
            }
            tracker.startGuestApplication(cfg);
        } catch (Exception e) {
            Log.e(TAG, "apply: failed to toggle refactor size", e);
        }
    }

    /**
     * Copies refactorsize.exe from assets into the container filesystem,
     * same idea as Winlator's stageRefactorSizeHelper() but landing under
     * <exagear image>/opt/edpatch instead of .wine/drive_c/winlator.
     */
    private static void stageHelperExe() {
        try {
            File imagePath = ((ExagearImageAware) getApplicationState()).getExagearImage().getPath();
            File dir = new File(imagePath, "opt/edpatch");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                Log.e(TAG, "stageHelperExe: could not create " + dir.getPath());
                return;
            }
            File dst = new File(dir, "refactorsize.exe");
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
