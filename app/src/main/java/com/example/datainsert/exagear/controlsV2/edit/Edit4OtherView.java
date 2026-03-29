package com.example.datainsert.exagear.controlsV2.edit;

import static com.example.datainsert.exagear.RR.getS;
import static com.example.datainsert.exagear.RR.getSArr;
import static com.example.datainsert.exagear.controlsV2.Const.OPTION_TASKMGR_START_SH_ENV;
import static com.example.datainsert.exagear.controlsV2.model.ModelProvider.readBundledProfilesFromAssets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.example.datainsert.exagear.RR;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.TestHelper;
import com.example.datainsert.exagear.controlsV2.XServerViewHolder;
import com.example.datainsert.exagear.controlsV2.model.ModelProvider;
import com.example.datainsert.exagear.QH;

public class Edit4OtherView extends LinearLayout {

    @SuppressLint("SetTextI18n")
    public Edit4OtherView(Context c) {
        super(c);
        setOrientation(VERTICAL);

        // ───────────────────────────────────────────────
        //           Tombol: Save | Save & Exit
        // ───────────────────────────────────────────────

        Button btnSave = new Button(c);
        btnSave.setAllCaps(false);
        btnSave.setText(getS(RR.global_save));
        btnSave.setOnClickListener(v -> ModelProvider.saveProfile(Const.getActiveProfile()));

        Button btnSaveExit = new Button(c);
        btnSaveExit.setAllCaps(false);
        btnSaveExit.setText(getS(RR.ctr2_other_saveExit));
        btnSaveExit.setOnClickListener(v -> Const.getTouchView().exitEdit());

        LinearLayout linearSaves = new LinearLayout(c);
        linearSaves.setOrientation(HORIZONTAL);
        linearSaves.setVerticalGravity(Gravity.CENTER);
        linearSaves.setPadding(12, 8, 12, 8);

        linearSaves.addView(btnSave,     QH.LPLinear.one(0, -2).weight().to());
        linearSaves.addView(btnSaveExit, QH.LPLinear.one(0, -2).weight().to());

        // ───────────────────────────────────────────────
        //             Mouse Move Speed → SeekBar + %
        // ───────────────────────────────────────────────

        float currentSpeed = Const.getActiveProfile().getMouseMoveSpeed();
        currentSpeed = Math.max(0f, Math.min(10f, currentSpeed));
        int initialProgress = Math.round(currentSpeed * 10f);

        TextView tvSpeedValue = new TextView(c);
        tvSpeedValue.setText(initialProgress + "%");
        tvSpeedValue.setTextSize(16);
        tvSpeedValue.setGravity(Gravity.CENTER);
        tvSpeedValue.setPadding(12, 8, 12, 8);

        SeekBar seekSpeed = new SeekBar(c);
        seekSpeed.setMax(100);
        seekSpeed.setProgress(initialProgress);

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float speed = progress / 10f;
                    Const.getActiveProfile().setMouseMoveSpeed(speed);
                    tvSpeedValue.setText(progress + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        LinearLayout speedContainer = new LinearLayout(c);
        speedContainer.setOrientation(LinearLayout.HORIZONTAL);
        speedContainer.setGravity(Gravity.CENTER_VERTICAL);
        speedContainer.setPadding(16, 12, 16, 12);

        TextView labelSpeed = new TextView(c);
        labelSpeed.setText(getS(RR.ctr2_other_mouseSpeed));
        labelSpeed.setLayoutParams(QH.LPLinear.one(0, -2).weight(1.2f).to());

        speedContainer.addView(labelSpeed);
        speedContainer.addView(seekSpeed, QH.LPLinear.one(0, -2).weight(2.8f).to());
        speedContainer.addView(tvSpeedValue, QH.LPLinear.one(-2, -2).left(16).to());

        // ───────────────────────────────────────────────
        //                    Elemen lainnya
        // ───────────────────────────────────────────────

        Switch switchShowArea = new Switch(c);
        switchShowArea.setText(getS(RR.ctr2_other_showTouchArea));
        switchShowArea.setChecked(Const.getActiveProfile().isShowTouchArea());
        switchShowArea.setOnCheckedChangeListener((buttonView, isChecked) ->
                Const.getActiveProfile().setShowTouchArea(isChecked));

        Switch switchDetailDebug = new Switch(c);
        switchDetailDebug.setText("Detailed Debug (only this time)");
        switchDetailDebug.setChecked(Const.detailDebug);
        switchDetailDebug.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Const.detailDebug = isChecked;
            Const.getTouchView().invalidate();
        });

        Button btnGc = new Button(c);
        btnGc.setText("Java Garbage Collection");
        btnGc.setOnClickListener(v -> System.gc());

        Button btnBundledProfiles = new Button(c);
        btnBundledProfiles.setAllCaps(false);
        String[] strBundledProfiles = getSArr(RR.ctr2_other_reExtract);
        btnBundledProfiles.setText(strBundledProfiles[0]);
        btnBundledProfiles.setOnClickListener(v -> TestHelper.showConfirmDialog(v.getContext(),
                strBundledProfiles[1], (dialog, which) -> {
                    Const.getTouchView().exitEdit();
                    readBundledProfilesFromAssets(v.getContext(), true);
                    Const.getTouchView().setProfile(ModelProvider.readCurrentProfile());
                    Const.getTouchView().startEdit();
                }));

        Button btnSyncFallout = new Button(c);
        btnSyncFallout.setAllCaps(false);
        btnSyncFallout.setText(getS(RR.ctr2_other_syncFallout));
        btnSyncFallout.setOnClickListener(v -> {
            btnSaveExit.performClick();  // keluar edit dulu supaya inject pointer berhasil
            XServerViewHolder holder = Const.getXServerHolder();
            int[] screenSize = holder.getXScreenPixels();
            holder.injectPointerMove(0, 0);
            QH.sleep(50);
            holder.injectPointerMove(screenSize[0], 0);
            QH.sleep(50);
            holder.injectPointerMove(screenSize[0], screenSize[1]);
            QH.sleep(50);
            holder.injectPointerMove(0, screenSize[1]);
            QH.sleep(50);
            holder.injectPointerMove(0, 0);
            QH.sleep(50);
            holder.injectPointerMove(50, 50);
        });
        LinearLayout linearSyncFallout = TestHelper.wrapWithTipBtn(btnSyncFallout, getS(RR.ctr2_other_syncFalloutTip));

        Switch btnEnablePerContainer = new Switch(c);
        btnEnablePerContainer.setText(getS(RR.ctr2_other_isProfPerCont));
        btnEnablePerContainer.setOnCheckedChangeListener((buttonView, isChecked) ->
                Const.Pref.setProfilePerContainer(isChecked));
        btnEnablePerContainer.setChecked(Const.Pref.isProfilePerContainer());
        LinearLayout linearEnablePerCont = TestHelper.wrapWithTipBtn(btnEnablePerContainer, getS(RR.ctr2_other_isProfPerContTip));

        String[] strTaskmgrAltCmd = RR.getS(RR.ctr2_other_taskmgrAlt).split("\\$", 2);
        EditText editTaskmgrAltCmd = new EditText(c);
        editTaskmgrAltCmd.setText(Const.Pref.getRunTaskmgrAlt());
        editTaskmgrAltCmd.addTextChangedListener((QH.SimpleTextWatcher) s ->
                Const.Pref.setRunTaskmgrAlt(s.toString()));
        LinearLayout linearTaskmgrAltCmd = QH.getOneLineWithTitle(c, strTaskmgrAltCmd[0], editTaskmgrAltCmd, true);
        View tmpView = linearTaskmgrAltCmd.getChildAt(0);
        tmpView.setLayoutParams(QH.LPLinear.one(-2, -2).to());
        linearTaskmgrAltCmd.removeView(tmpView);
        linearTaskmgrAltCmd.addView(TestHelper.wrapWithTipBtn(tmpView, strTaskmgrAltCmd[1]), 0);

        // ───────────────────────────────────────────────
        //                    Tambah ke layout utama
        // ───────────────────────────────────────────────

        addView(linearSaves);
        addView(speedContainer, QH.LPLinear.one(-1, -2).top(12).left().right().to());
        addView(switchShowArea, QH.LPLinear.one(-1, -2).top(8).left().right().to());
        addView(linearEnablePerCont, QH.LPLinear.one(-1, -2).top(8).left().right().to());
        addView(btnBundledProfiles, QH.LPLinear.one(-1, -2).top(8).left().right().to());
        addView(linearSyncFallout, QH.LPLinear.one(-1, -2).top(8).left().right().to());
        addView(linearTaskmgrAltCmd, QH.LPLinear.one(-1, -2).top(8).left().right().to());

        addView(new View(c), QH.LPLinear.one(-1, -2).weight(1).to()); // spacer bawah
    }

    public static void setProfileShowTouchArea(boolean showTouchArea) {
        Const.getActiveProfile().setShowTouchArea(showTouchArea);
        Const.getTouchView().invalidate();
        ModelProvider.saveProfile(Const.getActiveProfile());
    }
}