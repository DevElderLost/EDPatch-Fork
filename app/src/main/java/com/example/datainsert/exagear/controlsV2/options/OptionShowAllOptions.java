package com.example.datainsert.exagear.controlsV2.options;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;

import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.TestHelper;
import com.example.datainsert.exagear.RR;
import com.example.datainsert.exagear.controlsV2.options.OptionShowLogTracer;

/**
 * Menampilkan semua opsi dalam bentuk grid kecil & ringkas
 */
public class OptionShowAllOptions extends AbstractOption {

    private AlertDialog mDialog;

    @SuppressLint("SetTextI18n")
    @Override
    public void run() {
        Log.d("TAG", "run: menampilkan semua opsi dalam grid kecil");
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w("TAG", "run dipanggil bukan dari main thread!");
            return;
        }

        Context c = Const.getContext();

        AbstractOption[] options = {
                new OptionEditInputControl(),
                new OptionTouchAreaDisplay(),
                new OptionToggleSoftInput(),
                new OptionToggleFullScreen(),
                new OptionCmdTool(),
                new OptionTaskMgr(),
                new OptionShowLogTracer(),   // <-- opsi baru ditambahkan di sini
                new OptionQuit()
        };

        GridLayout grid = new GridLayout(c);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setColumnCount(3);
        grid.setPadding(8, 12, 8, 12);

        for (AbstractOption option : options) {
            grid.addView(createGridItem(c, option));
        }

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 0);

        root.addView(grid);

        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        root.setLayoutTransition(transition);

        mDialog = new AlertDialog.Builder(c)
                .setView(root)
                .setCancelable(true)
                .create();

        mDialog.show();

        if (mDialog.getWindow() != null) {
            int displayWidth = c.getResources().getDisplayMetrics().widthPixels;
            int desiredWidth = (int) (displayWidth * 0.82);

            mDialog.getWindow().setLayout(
                    desiredWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );

            mDialog.getWindow().getAttributes().y = -dpToPx(20);
        }
    }

    private View createGridItem(Context context, AbstractOption option) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(8, 12, 8, 12);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        item.setLayoutParams(params);

        ImageView icon = new ImageView(context);
        int iconRes;
        String name = option.getName().toLowerCase();

        if (name.contains("edit")) {
            iconRes = 2131231038;
        } else if (name.contains("control")) {
            iconRes = 2131231090;
        } else if (name.contains("keyboard")) {
            iconRes = 2131230886;
        } else if (name.contains("fullscreen")) {
            iconRes = 2131231094;
        } else if (name.contains("terminal")) {
            iconRes = 2131231029;
        } else if (name.contains("task") || name.contains("manager") || name.contains("mgr")) {
            iconRes = 2131231096;
        } else if (name.contains("log") || name.contains("debug")) {
            iconRes = 2131231092;  // icon info/detail untuk log/debug
        } else if (name.contains("quit") || name.contains("exit") || name.contains("close")) {
            iconRes = 2131231093;
        } else {
            iconRes = android.R.drawable.ic_menu_info_details;
        }

        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dpToPx(64), dpToPx(64));
        icon.setLayoutParams(iconParams);

        TextView text = new TextView(context);
        text.setText(option.getName());
        text.setTextSize(12);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, 4, 0, 0);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);

        item.addView(icon);
        item.addView(text);

        item.setOnClickListener(v -> {
            option.run();
            dismissDialog();
        });

        item.setClickable(true);
        item.setFocusable(true);
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        item.setForeground(ta.getDrawable(0));
        ta.recycle();

        return item;
    }

    private void dismissDialog() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = null;
    }

    private int dpToPx(int dp) {
        float density = Const.getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    @Override
    public String getName() {
        return RR.getS(RR.ctr2_option_showAll);
    }
}