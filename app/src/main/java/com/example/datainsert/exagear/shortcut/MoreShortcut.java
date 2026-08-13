package com.example.datainsert.exagear.shortcut;

import static com.example.datainsert.exagear.RR.dimen.dialogPadding;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.PopupMenu;

import com.eltechs.axs.Globals;
import com.eltechs.axs.applicationState.ApplicationStateBase;
import com.eltechs.ed.BuildConfig;
import com.eltechs.ed.EDApplicationState;
import com.eltechs.ed.XDGLink;
import com.eltechs.ed.activities.EDStartupActivity;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.eltechs.ed.startupActions.StartGuest;
import com.eltechs.ed.startupActions.WDesktop;
import com.example.datainsert.exagear.QH;
import com.example.datainsert.exagear.RR;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class MoreShortcut {
    /**
     * 1: 初次添加
     * 2: 支持图标（如果有）
     */
    private static final int VERSION_FOR_EDPATCH = 2;
    private static final String TAG = "MoreShortcut";
    private static final String DESKTOP_FILE_ABSOLUTE_PATH = "desktop_file_absolute_path";
    private static final String CONTAINER_ID = "container_id";
    private static final String SHOULD_SHOW_TIP = "should_show_tip";

    /**
     * 插入位置：com.eltechs.ed.fragments.ChooseXDGLinkFragment.XDGNodeAdapter.onBindViewHolder popupMenu.show();之前
     * 进入安卓视图桌面板块后，为快捷方式的菜单项添加一些内容。
     *
     * @param isStartMenu 目前先只考虑桌面的吧，开始菜单不管？
     * @param popupMenu   （需要显示的弹窗菜单）
     * @param xdgLink     待保存的快捷方式
     */
    public static void addOptionsToMenu(boolean isStartMenu, PopupMenu popupMenu, XDGLink xdgLink) {
        if (isStartMenu) {
            return;
        }

        // Tambahkan menu item
        MenuItem addItem = popupMenu.getMenu().add(RR.getS(RR.shortcut_menuItem_addAppSc));

        // Set ikon dari resource (bukan dari file XDG/icon container)
        // Ganti RR.drawable.ic_add_shortcut dengan drawable yang sesuai di project Anda
        // Contoh: RR.drawable.ic_add_to_home, RR.drawable.ic_shortcut, dll.
        addItem.setIcon(2131231295);  // <-- ikon di samping kiri

        // Jika project Anda belum punya icon custom, bisa pakai bawaan sementara:
        // addItem.setIcon(android.R.drawable.ic_menu_add);

        addItem.setOnMenuItemClickListener(item -> {
            Log.d(TAG, "onMenuItemClick: Tambah ke home screen dipilih");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                Intent intent = new Intent(Globals.getAppContext(), EDStartupActivity.class);
                intent.setAction(Intent.ACTION_MAIN);
                intent.putExtra(DESKTOP_FILE_ABSOLUTE_PATH, xdgLink.linkFile.getAbsolutePath());
                intent.putExtra(CONTAINER_ID, xdgLink.guestCont.mId);

                PersistableBundle persistableBundle = new PersistableBundle();
                persistableBundle.putString(DESKTOP_FILE_ABSOLUTE_PATH, xdgLink.linkFile.getAbsolutePath());
                persistableBundle.putLong(CONTAINER_ID, xdgLink.guestCont.mId);

                ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(Globals.getAppContext(), xdgLink.name)
                        .setShortLabel(xdgLink.name)
                        .setExtras(persistableBundle)
                        .setIntent(intent)
                        .setActivity(new ComponentName(Globals.getAppContext().getPackageName(), Globals.getAppContext().getPackageName() + ".activities.EDStartupActivity"))
                        .build();

                ShortcutManager shortcutManager = Globals.getAppContext().getSystemService(ShortcutManager.class);
                List<ShortcutInfo> shortcutInfoList = shortcutManager.getDynamicShortcuts();
                shortcutInfoList.add(shortcutInfo);

                if (shortcutInfoList.size() > 4) {
                    shortcutInfoList.remove(0);
                }

                setDynamicShortcuts(shortcutInfoList);
                shortcutManager.setDynamicShortcuts(shortcutInfoList);
            }

            QH.showTipDialogWithDisable(
                    ((ApplicationStateBase) Globals.getApplicationState()).getCurrentActivity(),
                    RR.getS(RR.shortcut_TipAfterAdd),
                    SHOULD_SHOW_TIP);

            return true;
        });

        // Paksa tampilkan icon di PopupMenu (sangat penting!)
        try {
            Field field = popupMenu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popupMenu);

            Class<?> classPopupHelper = menuPopupHelper.getClass();
            Method setForceIcons = classPopupHelper.getDeclaredMethod("setForceShowIcon", boolean.class);
            setForceIcons.setAccessible(true);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            Log.w(TAG, "Gagal memaksa tampilkan ikon via reflection", e);
            // Jika gagal, pastikan activity menggunakan AppCompatActivity
            // Alternatif: coba panggil popupMenu.show() dulu, baru reflection
        }
    }

    /**
     * 插入位置：com.eltechs.ed.activities.EDStartupActivity.initialiseStartupActions()结尾
     * 删掉 startupActionsCollection.addAction(new WDesktop<>());
     */
    public static void launchFromShortCutOrNormally(AppCompatActivity a) {
        setDynamicShortcuts(null);
        try {
            String shortcutPath = a.getIntent().getStringExtra(DESKTOP_FILE_ABSOLUTE_PATH);
            if (shortcutPath == null) {
                throw new Exception("正常启动app，进入WDesktop");
            }

            GuestContainer container = GuestContainersManager.getInstance(a).getContainerById(
                    a.getIntent().getLongExtra(CONTAINER_ID, 0));
            File desktopFile = new File(shortcutPath);

            if (container == null || !desktopFile.exists()) {
                throw new Exception("快捷方式不存在，正常启动");
            }

            Log.d(TAG, "launchFromShortCut: 从快捷方式入口直接进入xserver");
            XDGLink xdgLink = new XDGLink(container, desktopFile);
            ((EDApplicationState) Globals.getApplicationState()).getStartupActionsCollection()
                    .addAction(new StartGuest<>(new StartGuest.RunXDGLink(xdgLink)));
        } catch (Exception e) {
            Log.d(TAG, "launchFromShortCut: 正常启动 " + e.getMessage());
            ((EDApplicationState) Globals.getApplicationState()).getStartupActionsCollection()
                    .addAction(new WDesktop<>());
        }
    }

    /**
     * 添加/更新动态快捷方式，同时检查旧的快捷方式是否失效
     */
    private static void setDynamicShortcuts(List<ShortcutInfo> list) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) {
            Log.d(TAG, "updateCurrentShortcuts: 低于安卓7，无法使用快捷方式功能");
            return;
        }

        ShortcutManager shortcutManager = Globals.getAppContext().getSystemService(ShortcutManager.class);
        if (list == null) {
            list = shortcutManager.getDynamicShortcuts();
        }

        for (int i = 0; i < list.size(); i++) {
            ShortcutInfo info = list.get(i);
            PersistableBundle bundle = info.getExtras();
            File desktopFile;

            if (bundle == null || !(desktopFile = new File(bundle.getString(DESKTOP_FILE_ABSOLUTE_PATH))).exists()) {
                list.remove(i);
                i--;
                continue;
            }

            // Perbaiki ikon yang hilang saat diambil dari getDynamicShortcuts()
            try {
                GuestContainersManager manager = GuestContainersManager.getInstance(Globals.getAppContext());
                GuestContainer container = manager.getContainerById(bundle.getLong(CONTAINER_ID, 0));
                XDGLink xdgLink = new XDGLink(container, desktopFile);

                Bitmap icon = BitmapFactory.decodeFile(manager.getIconPath(xdgLink));
                ShortcutInfo.Builder builder = new ShortcutInfo.Builder(Globals.getAppContext(), xdgLink.name)
                        .setShortLabel(xdgLink.name)
                        .setExtras(info.getExtras())
                        .setIntent(info.getIntent())
                        .setActivity(info.getActivity());

                if (icon != null) {
                    builder.setIcon(Icon.createWithBitmap(icon));
                } else {
                    Log.d(TAG, "setDynamicShortcuts: Tidak ada ikon untuk " + xdgLink.name);
                }

                list.remove(i);
                list.add(i, builder.build());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        shortcutManager.setDynamicShortcuts(list);
    }
}