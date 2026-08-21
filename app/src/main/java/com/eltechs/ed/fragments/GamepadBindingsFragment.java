package com.eltechs.ed.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.ed.R;
import com.example.datainsert.exagear.controlsV2.axs.XKeyButton;

/**
 * Layar daftar SEMUA tombol gamepad virtual EDPatch. Tap salah satu baris -> AlertDialog
 * minta tekan tombol gamepad fisik/Bluetooth untuk di-bind. Tampilan dibuat konsisten
 * dengan fragment lain (mis. ManageContainersFragment/ChooseFileFragment): RecyclerView +
 * layout ex_basic_list_item_with_button (title/subtext theme-aware, BUKAN warna hardcode),
 * dan judul toolbar di-set lewat getSupportActionBar() di onActivityCreated().
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
            {"D-Pad \u2191", XKeyButton.GAMEPAD_DPAD_UP},
            {"D-Pad \u2192", XKeyButton.GAMEPAD_DPAD_RIGHT},
            {"D-Pad \u2193", XKeyButton.GAMEPAD_DPAD_DOWN},
            {"D-Pad \u2190", XKeyButton.GAMEPAD_DPAD_LEFT},
            {"L-Stick \u2191", XKeyButton.GAMEPAD_LEFT_THUMB_UP},
            {"L-Stick \u2192", XKeyButton.GAMEPAD_LEFT_THUMB_RIGHT},
            {"L-Stick \u2193", XKeyButton.GAMEPAD_LEFT_THUMB_DOWN},
            {"L-Stick \u2190", XKeyButton.GAMEPAD_LEFT_THUMB_LEFT},
            {"R-Stick \u2191", XKeyButton.GAMEPAD_RIGHT_THUMB_UP},
            {"R-Stick \u2192", XKeyButton.GAMEPAD_RIGHT_THUMB_RIGHT},
            {"R-Stick \u2193", XKeyButton.GAMEPAD_RIGHT_THUMB_DOWN},
            {"R-Stick \u2190", XKeyButton.GAMEPAD_RIGHT_THUMB_LEFT},
    };

    private RecyclerView mRecyclerView;
    private BindingsAdapter mAdapter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle("Gamepad Bindings");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRecyclerView = new RecyclerView(inflater.getContext());
        mRecyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(inflater.getContext()));

        mAdapter = new BindingsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        return mRecyclerView;
    }

    private class BindingsAdapter extends RecyclerView.Adapter<BindingsAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            View root;
            ImageView image;
            TextView text;
            TextView subtext;
            ImageButton button;
            ImageButton swapButton;

            ViewHolder(View itemView) {
                super(itemView);
                root = itemView;
                image = itemView.findViewById(2131296401);
                text = itemView.findViewById(2131296508);
                subtext = itemView.findViewById(2131296504);
                button = itemView.findViewById(2131296309);
                if (button != null) button.setVisibility(View.GONE); // tidak butuh tombol titik-tiga
                if (image != null) image.setVisibility(View.GONE); // tidak butuh icon di kiri

                // ImageButton "current_cont" (ic_swap_24dp) dipakai ulang sebagai tombol reset binding.
                swapButton = itemView.findViewById(2131300839);
                if (swapButton != null) {
                    swapButton.setVisibility(View.VISIBLE);
                    swapButton.setContentDescription("Reset binding");
                }
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(2131427359, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final String label = (String) GAMEPAD_BUTTONS[position][0];
            final int gamepadCode = (int) GAMEPAD_BUTTONS[position][1];

            holder.text.setText(label);
            holder.subtext.setText(getBindingStatusText(gamepadCode));

            holder.root.setOnClickListener(v -> showBindDialog(holder, position, label, gamepadCode));

            if (holder.swapButton != null) {
                holder.swapButton.setOnClickListener(v -> showResetBindingDialog(holder, position, label, gamepadCode));
            }
        }

        @Override
        public int getItemCount() {
            return GAMEPAD_BUTTONS.length;
        }
    }

    private String getBindingStatusText(int gamepadCode) {
        SharedPreferences prefs = getPrefs();
        String physicalKeyName = findBoundKeyName(prefs, gamepadCode);
        return physicalKeyName != null ? physicalKeyName : "Belum di-bind";
    }

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

    private void showBindDialog(final BindingsAdapter.ViewHolder holder, final int position, String label, final int gamepadCode) {
        Context c = getContext();
        if (c == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle("Bind tombol: " + label);
        builder.setMessage("Tekan tombol pada gamepad fisik/Bluetooth Anda sekarang...");
        builder.setCancelable(true);
        builder.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());

        final AlertDialog dialog = builder.create();

        dialog.setOnKeyListener((DialogInterface di, int keyCode, KeyEvent event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (event.getRepeatCount() != 0) return true;

            InputDevice device = event.getDevice();
            boolean isGamepadSource = device != null && (
                    (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                            || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            );

            if (keyCode == KeyEvent.KEYCODE_BACK && !isGamepadSource) {
                dialog.dismiss();
                return true;
            }

            if (!isGamepadSource) {
                return true;
            }

            saveBinding(keyCode, gamepadCode);
            if (mAdapter != null) mAdapter.notifyItemChanged(position);
            Toast.makeText(c, label + " di-bind ke " + KeyEvent.keyCodeToString(keyCode), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return true;
        });

        dialog.show();
    }

    private void showResetBindingDialog(final BindingsAdapter.ViewHolder holder, final int position, final String label, final int gamepadCode) {
        Context c = getContext();
        if (c == null) return;

        SharedPreferences prefs = getPrefs();
        if (findBoundKeyName(prefs, gamepadCode) == null) {
            Toast.makeText(c, label + " belum di-bind", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(c)
                .setTitle("Reset binding: " + label)
                .setMessage("Hapus binding tombol fisik untuk \"" + label + "\"?")
                .setNegativeButton("Batal", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Reset", (dialog, which) -> {
                    resetBinding(gamepadCode);
                    if (mAdapter != null) mAdapter.notifyItemChanged(position);
                    Toast.makeText(c, "Binding " + label + " direset", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .show();
    }

    private void resetBinding(int gamepadCode) {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor editor = prefs.edit();

        for (String prefKey : prefs.getAll().keySet()) {
            if (prefs.getInt(prefKey, -1) == gamepadCode) {
                editor.remove(prefKey);
            }
        }

        editor.apply();
    }

    private void saveBinding(int physicalKeyCode, int gamepadCode) {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor editor = prefs.edit();

        for (String prefKey : prefs.getAll().keySet()) {
            if (prefs.getInt(prefKey, -1) == gamepadCode) {
                editor.remove(prefKey);
            }
        }

        editor.putInt(String.valueOf(physicalKeyCode), gamepadCode);
        editor.apply();
    }

    private SharedPreferences getPrefs() {
        Context c = getContext();
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
