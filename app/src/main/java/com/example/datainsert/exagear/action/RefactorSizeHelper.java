package com.example.datainsert.exagear.action;

import android.util.Log;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ExagearImageAware;
import com.example.datainsert.exagear.QH;

import java.io.File;

/**
 * "Refactor Size" - toggles borderless-fullscreen for the foreground guest
 * window, using the same SetWindowLong/SetWindowPos technique as
 * Winlator-Ludashi-test's wn-refactor-size helper.
 *
 * v3 (this revision): fixes the "works the first time a container is
 * created, but re-entering the container makes it run with no visible
 * effect" bug. Root cause (confirmed by code inspection + comparison with
 * Winlator-Ludashi-test's XServerDisplayActivity, which does NOT have
 * this bug):
 *
 *   1. The old code stored on/off state in a SharedPreferences boolean.
 *      That boolean survives app restarts, but the HWND saved inside
 *      refactorsize.dat is only valid for ONE wine/X session. Re-entering
 *      the container starts a brand new session with a brand new HWND,
 *      but the leftover pref still said "enabled" from last time, so the
 *      very next toggle() computed newState=false and called disable()
 *      instead of enable() - disable() read the stale HWND from the old
 *      session, IsWindow() correctly rejected it, so NOTHING visible
 *      happened, then the .dat file got deleted. The exe genuinely ran;
 *      it just had nothing valid left to act on.
 *      Winlator-Ludashi-test never hits this because its equivalent flag
 *      (XServerDisplayActivity#isRefactorSizeEnabled) is a plain in-memory
 *      field, not a SharedPreference - it naturally resets to false every
 *      time the Activity (i.e. the container session) is recreated. This
 *      revision does the same: state lives in a plain instance field.
 *
 *   2. Every toggle click used to spin up a brand new
 *      UBTLaunchConfiguration and spawn a whole new guest process just to
 *      run refactorsize.exe once - slow, and fragile (has to land in the
 *      exact same wine/X session as the game). This revision starts a
 *      single persistent guest-side daemon (winhandler_mini.exe, see
 *      app/src/main/cpp/winhandler-mini/) eagerly, backgrounded in the
 *      SAME guest process spawn as the main exe/game (patched into
 *      StartGuest.execute()) - matching exactly how Winlator launches its
 *      own winhandler.exe alongside the game - and sends it lightweight
 *      UDP commands instead of spawning anything new per toggle.
 */
public class RefactorSizeHelper {
    private static final String TAG = "RefactorSizeHelper";
    private static final String EXE_GUEST_PATH = "Z:\\opt\\edpatch\\refactorsize.exe";
    private static final long HELPER_EXE_BYTES = 16384L;

    /** Deliberately NOT persisted (see class doc, point 1). Resets to
     * false every time the process/Activity is recreated, which is
     * exactly what re-entering the container should mean for this
     * feature - matches Winlator-Ludashi-test's behavior. */
    private static volatile boolean enabled = false;
    private static volatile boolean staleStateCleared = false;

    public static boolean isEnabled() {
        return enabled;
    }

    /** Called from the options grid item. Flips state and runs the helper. */
    public static void toggle() {
        // Normally a no-op: StartGuest.execute() already launched
        // winhandler_mini.exe eagerly (see WinHandlerMini.prepareForLaunch()/
        // injectIntoCommand()). Only actually spawns anything if that eager
        // path was somehow skipped for this launch.
        WinHandlerMini.get().ensureStartedFallback();
        clearStaleStateOnce();

        enabled = !enabled;
        apply(enabled);
    }

    private static void apply(boolean enabled) {
        stageHelperExe();
        WinHandlerMini.get().exec(EXE_GUEST_PATH, enabled ? "on" : "off");
    }

    /**
     * Deletes any refactorsize.dat left over from a previous, possibly
     * force-killed session, exactly once per session, the first time this
     * feature is used. That file's saved HWND can never be valid again
     * once the wine/X session that created it is gone, so leaving it
     * around only invites the exact stale-state bug this revision fixes.
     * Safe/idempotent - a missing file is simply a no-op.
     */
    private static void clearStaleStateOnce() {
        if (staleStateCleared) return;
        staleStateCleared = true;
        try {
            File dat = new File(QH.Files.edPatchDir(), "refactorsize.dat");
            if (dat.exists() && dat.delete()) {
                Log.i(TAG, "clearStaleStateOnce: removed stale refactorsize.dat from a previous session");
            }
        } catch (Exception e) {
            Log.e(TAG, "clearStaleStateOnce: failed to clear stale state", e);
        }
    }

    /**
     * Copies refactorsize.exe from assets into <exagear image>/opt/edpatch,
     * same convention FuncFAB already uses for pulseaudio/renderer files.
     */
    private static void stageHelperExe() {
        try {
            File dst = new File(QH.Files.edPatchDir(), "refactorsize.exe");
            if (dst.exists() && dst.length() == HELPER_EXE_BYTES) return;

            try (java.io.InputStream in = Globals.getAppContext().getAssets().open("refactorsize/refactorsize.exe");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
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
