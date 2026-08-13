package com.example.datainsert.exagear.controlsV2.edit.props;

import static android.widget.LinearLayout.VERTICAL;
import static com.example.datainsert.exagear.RR.getS;
import static com.example.datainsert.exagear.controlsV2.Const.dp8;
import static com.example.datainsert.exagear.controlsV2.Const.minTouchSize;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.datainsert.exagear.RR;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.TestHelper;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;
import com.example.datainsert.exagear.controlsV2.widget.ColorPicker;
import com.example.datainsert.exagear.QH;

public class Prop0MainColor extends Prop<TouchAreaModel> {

    GradientDrawable mDrawable;
    TextView mTvColorStyle;
    String[] colorStyleNames = RR.getSArr(RR.ctr2_prop_colorStyle_names);

    public Prop0MainColor(Host<TouchAreaModel> host, Context c) {
        super(host, c);
    }

    @Override
    public void updateUIFromModel(TouchAreaModel model) {
        mDrawable.setColor(model.getMainColor());
        mTvColorStyle.setText(colorStyleNames[model.getColorStyle()]);
    }

    /**
     * 因为有多个视图，所以没法用父类的判断是否是修改源的方法
     */
    @Override
    public boolean isChangingSource() {
        return false;
    }

    @Override
    public String getTitle() {
        return getS(RR.global_color);
    }

    @Override
    protected View createMainEditView(Context c) {

        ImageView imageBgColor = new ImageView(c);

        mDrawable = new GradientDrawable();
        mDrawable.setSize(minTouchSize * 2, minTouchSize);

        imageBgColor.setImageDrawable(ColorPicker.wrapAlphaAlertBg(c, mDrawable, 10));
        imageBgColor.setMinimumHeight(minTouchSize);

        imageBgColor.setOnClickListener(v -> {

            ColorPicker picker = new ColorPicker(c, mHost.getModel().getMainColor(), argb -> {
                mHost.getModel().setMainColor(argb);
                onWidgetListener();
            });

            int padding = dp8 * 2;
            picker.setPadding(padding, padding, padding, padding);

            Const.getEditWindow().toNextView(picker, "Color Picker");
        });

        mTvColorStyle = new TextView(c);

        mTvColorStyle.setPadding(dp8 / 2, dp8 / 2, dp8 / 2, dp8 / 2);
        mTvColorStyle.setTag(0);

        TestHelper.setTextViewSwapDrawable(mTvColorStyle);

        // memindahkan icon swap ke kiri text
        mTvColorStyle.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        mTvColorStyle.setCompoundDrawablePadding(dp8 / 2);

        QH.setRippleBackground(mTvColorStyle);

        mTvColorStyle.setOnClickListener(v -> {

            int oldStyle = mHost.getModel().getColorStyle();

            int newStyle = (oldStyle + 1) % colorStyleNames.length;

            mHost.getModel().setColorStyle(newStyle);

            onWidgetListener();
        });

        LinearLayout linearRoot = new LinearLayout(c);

        linearRoot.setOrientation(LinearLayout.HORIZONTAL);
        linearRoot.setVerticalGravity(Gravity.CENTER_VERTICAL);

        linearRoot.addView(imageBgColor);
        linearRoot.addView(mTvColorStyle, QH.LPLinear.one(-2, -2).left().to());

        return linearRoot;
    }

    @Override
    protected View createAltEditView(Context c) {
        return null;
    }
}