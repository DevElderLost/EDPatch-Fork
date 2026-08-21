package com.eltechs.ed.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.example.datainsert.exagear.controlsV2.axs.XKeyButton;

/**
 * Layar daftar SEMUA tombol gamepad virtual EDPatch (A/B/X/Y, LB/RB, LT/RT, L3/R3,
 * Select/Start, D-Pad 4 arah, L-Stick 4 arah, R-Stick 4 arah). Tap salah satu baris
 * -> muncul AlertDialog yang minta user menekan tombol pada gamepad fisik/Bluetooth
 * yang terhubung, untuk di-bind ke tombol virtual tersebut — seperti alur binding
 * controller di Winlator (ExternalControllerBindingsActivity), tapi di sini per-baris
 * "tap nama tombol dulu, baru tekan tombol fisik" bukan auto-detect-tekan-apapun.
 *
 * Hasil binding disimpan di SharedPreferences ("gamepad_bindings"): key = keyCode
 * fisik (String), value = kode GAMEPAD_* virtual (int, lihat {@link XKeyButton}).
 * Nanti tinggal dibaca dari sini oleh mekanisme forwarding input fisik -> GamepadServer
 * saat gameplay (belum termasuk di fragment ini — ini baru bagian UI + penyimpanan binding-nya).
 */
public class GamepadBindingsFragment extends Fragment {

    public static final String PREFS_NAME = "gamepad_bindings";

    /** {label ditampilkan, kode GAMEPAD_* dari XKeyButton (tanpa GAMEPAD_MASK)} */
    private static final Object[][] GAMEPAD_BUTTONS = {
            {"A", XKeyButton.GAMEPAD_A},
            {"B", XKeyButton.GAMEPAD_B},
            {"X", XKeyButton.GAMEPAD_X},
            {"Y", XKeyButton.GAMEPAD_Y},
            {"LB", XKeyButton.GAMEPAD_L1},
            {"RB", XKeyButton.GAMEPAD_R1},
            {"LT", XKeyButton.GAMEPAD_L2},
            {"RT", XKeyButton.GAMEPAD_R2},
            {"L3 (klik stick kiri)", XKeyButton.GAMEPAD_L3},
            {"R3 (klik stick kanan)", XKeyButton.GAMEPAD_R3},
            {"Select", XKeyButton.GAMEPAD_SELECT},
            {"Start", XKeyButton.GAMEPAD_START},
            {"D-Pad ↑", XKeyButton.GAMEPAD_DPAD_UP},
            {"D-Pad →", XKeyButton.GAMEPAD_DPAD_RIGHT},
            {"D-Pad ↓", XKeyButton.GAMEPAD_DPAD_DOWN},
            {"D-Pad ←", XKeyButton.GAMEPAD_DPAD_LEFT},
            {"L-Stick ↑", XKeyButton.GAMEPAD_LEFT_THUMB_UP},
            {"L-Stick →", XKeyButton.GAMEPAD_LEFT_THUMB_RIGHT},
            {"L-Stick ↓", XKeyButton.GAMEPAD_LEFT_THUMB_DOWN},
            {"L-Stick ←", XKeyButton.GAMEPAD_LEFT_THUMB_LEFT},
            {"R-Stick ↑", XKeyButton.GAMEPAD_RIGHT_THUMB_UP},
            {"R-Stick →", XKeyButton.GAMEPAD_RIGHT_THUMB_RIGHT},
            {"R-Stick ↓", XKeyButton.GAMEPAD_RIGHT_THUMB_DOWN},
            {"R-Stick ←", XKeyButton.GAMEPAD_RIGHT_THUMB_LEFT},
    };

    private LinearLayout mListContainer;
    // Simpan referensi TextView "keterangan binding" per baris supaya bisa di-refresh
    // langsung tanpa rebuild seluruh list tiap habis bind 1 tombol.
    private final TextView[] mBindingLabels = new TextView[GAMEPAD_BUTTONS.length];

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context c = getContext() != null ? getContext() : Globals.getAppContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(c);
        title.setText("Gamepad Bindings");
        title.setTextSize(18);
        title.setTextColor(0xffffffff);
        title.setPadding(24, 24, 24, 4);
        root.addView(title);

        TextView hint = new TextView(c);
        hint.setText("Tap salah satu tombol di bawah, lalu tekan tombol pada gamepad fisik/Bluetooth yang terhubung untuk mem-bind.");
        hint.setTextSize(12);
        hint.setTextColor(0xffaaaaaa);
        hint.setPadding(24, 0, 24, 16);
        root.addView(hint);

        ScrollView scrollView = new ScrollView(c);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mListContainer = new LinearLayout(c);
        mListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mListContainer);
        root.addView(scrollView);

        buildRows(c);

        return root;
    }

    private void buildRows(Context c) {
        mListContainer.removeAllViews();
        SharedPreferences prefs = getPrefs(c);

        for (int i = 0; i < GAMEPAD_BUTTONS.length; i++) {
            final String label = (String) GAMEPAD_BUTTONS[i][0];
            final int gamepadCode = (int) GAMEPAD_BUTTONS[i][1];
            final int rowIndex = i;

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(24, 20, 24, 20);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);

            TextView tvLabel = new TextView(c);
            tvLabel.setText(label);
            tvLabel.setTextColor(0xffffffff);
            tvLabel.setTextSize(15);
            tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvLabel);

            TextView tvBinding = new TextView(c);
            tvBinding.setTextColor(0xff64B5F6);
            tvBinding.setTextSize(13);
            tvBinding.setGravity(Gravity.END);
            row.addView(tvBinding);
            mBindingLabels[rowIndex] = tvBinding;
            updateBindingLabel(c, rowIndex, gamepadCode);

            row.setOnClickListener(v -> showBindDialog(c, rowIndex, label, gamepadCode));

            mListContainer.addView(row);

            View divider = new View(c);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2));
            divider.setBackgroundColor(0x33ffffff);
            mListContainer.addView(divider);
        }
    }

    /** Update teks "sedang di-bind ke tombol fisik apa" untuk 1 baris, baca dari SharedPreferences. */
    private void updateBindingLabel(Context c, int rowIndex, int gamepadCode) {
        SharedPreferences prefs = getPrefs(c);
        String physicalKeyName = findBoundKeyName(prefs, gamepadCode);
        mBindingLabels[rowIndex].setText(physicalKeyName != null ? physicalKeyName : "Belum di-bind");
    }

    /** Cari nama tombol fisik (keyCode) yang saat ini terikat ke gamepadCode tertentu, kalau ada. */
    private String findBoundKeyName(SharedPreferences prefs, int gamepadCode) {
        for (String prefKey : prefs.getAll().keySet()) {
            int boundGamepadCode = prefs.getInt(prefKey, -1);
            if (boundGamepadCode == gamepadCode) {
                try {
                    int physicalKeyCode = Integer.parseInt(prefKey);
                    return KeyEvent.keyCodeToString(physicalKeyCode);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private void showBindDialog(Context c, int rowIndex, String label, int gamepadCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle("Bind tombol: " + label);
        builder.setMessage("Tekan tombol pada gamepad fisik/Bluetooth Anda sekarang...");
        builder.setCancelable(true);
        builder.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());

        final AlertDialog dialog = builder.create();

        // AlertDialog perlu bisa nangkep KeyEvent — pasang listener di window dialog.
        dialog.setOnKeyListener((DialogInterface di, int keyCode, KeyEvent event) -> {
            // Abaikan tombol back/volume Android & event ACTION_UP, cuma proses ACTION_DOWN
            // dari device yang memang gamepad/joystick (bukan keyboard fisik/remote biasa).
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (event.getRepeatCount() != 0) return true;

            InputDevice device = event.getDevice();
            boolean isGamepadSource = device != null && (
                    (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                            || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            );

            if (keyCode == KeyEvent.KEYCODE_BACK && !isGamepadSource) {
                // Tombol back Android beneran (bukan tombol B gamepad yg biasa dipetakan ke BACK)
                dialog.dismiss();
                return true;
            }

            if (!isGamepadSource) {
                // Bukan dari gamepad/joystick (mis. keyboard fisik nyasar) -> abaikan, tetap tunggu
                return true;
            }

            saveBinding(c, keyCode, gamepadCode);
            updateBindingLabel(c, rowIndex, gamepadCode);
            Toast.makeText(c, label + " di-bind ke " + KeyEvent.keyCodeToString(keyCode), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return true;
        });

        dialog.show();
    }

    /** Simpan binding: 1 tombol fisik cuma boleh terikat ke 1 tombol virtual (hapus binding lama kalau ada bentrok). */
    private void saveBinding(Context c, int physicalKeyCode, int gamepadCode) {
        SharedPreferences prefs = getPrefs(c);
        SharedPreferences.Editor editor = prefs.edit();

        // Lepas dulu binding gamepadCode ini dari tombol fisik LAIN (kalau sebelumnya sudah pernah di-bind)
        for (String prefKey : prefs.getAll().keySet()) {
            if (prefs.getInt(prefKey, -1) == gamepadCode) {
                editor.remove(prefKey);
            }
        }

        editor.putInt(String.valueOf(physicalKeyCode), gamepadCode);
        editor.apply();
    }

    private SharedPreferences getPrefs(Context c) {
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
