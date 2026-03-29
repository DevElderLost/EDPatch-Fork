package com.example.datainsert.exagear.controlsV2.edit.props;

import static com.example.datainsert.exagear.controlsV2.edit.Edit1KeyView.buildOptionsGroup;

import android.content.Context;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.example.datainsert.exagear.RR;
import com.example.datainsert.exagear.controlsV2.Const;
import com.example.datainsert.exagear.controlsV2.model.OneButton;
import com.example.datainsert.exagear.controlsV2.TouchAreaModel;

public class Prop1ShowName extends Prop<TouchAreaModel> {

    private RadioGroup groupShowName;

    public Prop1ShowName(Host<TouchAreaModel> host, Context c) {
        super(host, c);
    }

    @Override
    public String getTitle() {
        // Bisa disesuaikan teksnya di strings.xml
        return "Text Display";  
        // atau RR.getS(RR.ctr2_prop_show_name) jika sudah ada string resource
    }

    @Override
    protected View createMainEditView(Context c) {
        // Dua pilihan: Tampilkan / Sembunyikan
        HorizontalScrollView scrollGroup = buildOptionsGroup(c,
                new String[]{"Show", "Hide"},  // atau ambil dari RR.getSArr(...)
                new int[]{
                        Const.NAME_DISPLAY_NORMAL,   // 0 = tampilkan
                        Const.NAME_DISPLAY_HIDE      // 1 = sembunyikan
                },
                (group, btn, intValue) -> {
                    if (mHost.getModel() instanceof OneButton) {
                        ((OneButton) mHost.getModel()).setNameDisplayMode(intValue);
                    }
                    onWidgetListener();
                });

        groupShowName = (RadioGroup) scrollGroup.getChildAt(0);
        return scrollGroup;
    }

    @Override
    protected View createAltEditView(Context c) {
        return null;  // tidak perlu tampilan alternatif
    }

    @Override
    public void updateUIFromModel(TouchAreaModel model) {
        if (model instanceof OneButton) {
            int mode = ((OneButton) model).getNameDisplayMode();
            // Cari radio button yang sesuai dengan nilai mode
            for (int i = 0; i < groupShowName.getChildCount(); i++) {
                RadioButton rb = (RadioButton) groupShowName.getChildAt(i);
                if (rb.getId() == mode || i == mode) {  // tergantung bagaimana buildOptionsGroup memberi ID
                    rb.setChecked(true);
                    break;
                }
            }
        }
    }
}