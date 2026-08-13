package com.eltechs.ed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.widget.Toast;
import com.eltechs.axs.helpers.AndroidHelpers;
import com.eltechs.axs.xserver.ScreenInfo;
import com.eltechs.ed.MSBitmap;
import com.eltechs.ed.WineRegistryEditor;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import java.io.File;
import java.io.IOException;

public class WallpaperConverter {
    public static final String KEY_SCREEN_SIZE = "SCREEN_SIZE";

    public static File convertToBmpAndMove(Context context, File file, GuestContainersManager guestContainersManager) throws IOException {
        SharedPreferences sharedPreferences = context.getSharedPreferences("com.eltechs.axs.CONFIG", 0);
        if (!sharedPreferences.contains("CURRENT_GUEST_CONT_ID")) {
            return (File) null;
        }
        String stringBuffer = new StringBuffer().append("com.eltechs.ed.CONTAINER_CONFIG_").append(sharedPreferences.getLong("CURRENT_GUEST_CONT_ID", 0)).toString();
        SharedPreferences sharedPreferences2 = context.getSharedPreferences(stringBuffer, 0);
        if (stringBuffer.contains(KEY_SCREEN_SIZE)) {
            return (File) null;
        }
        int i;
        int i2;
        String string = sharedPreferences2.getString(KEY_SCREEN_SIZE, "Default");
        if (string.equals("Default")) {
            int[] defaultScreenSize = getDefaultScreenSize();
            i = defaultScreenSize[0];
            i2 = defaultScreenSize[1];
        } else {
            String[] split = string.split(",");
            i = Integer.parseInt(split[0]);
            i2 = Integer.parseInt(split[1]);
        }
        ScreenInfo screenInfo = new ScreenInfo(i, i2, i / 10, i2 / 10, 24);
        int ceil = (int) Math.ceil(((double) ((float) screenInfo.widthInPixels)) * ((double) (((float) i2) / ((float) screenInfo.heightInPixels))));
        Bitmap createBitmap = Bitmap.createBitmap(ceil, i2, Config.ARGB_8888);
        Paint paint = new Paint(1);
        Canvas canvas = new Canvas(createBitmap);
        if (isImageFile(file)) {
            Bitmap decodeFile = BitmapFactory.decodeFile(file.getPath());
            canvas.drawBitmap(decodeFile, new Rect(0, 0, decodeFile.getWidth(), decodeFile.getHeight()), new Rect(0, 0, ceil, i2), paint);
        }
        File file2 = new File(new StringBuffer().append(new StringBuffer().append(guestContainersManager.getGuestWinePrefixPath()).append("/").toString()).append("wallpaper.bmp").toString());
        MSBitmap.create(createBitmap, file2);
        return file2;
    }

    public static void setAsWallpaper(Context context, File file, GuestContainersManager guestContainersManager) {
        WineRegistryEditor wineRegistryEditor = new WineRegistryEditor(new File(guestContainersManager.getHostPath("/home/xdroid/.wine/user.reg")));
        try {
            wineRegistryEditor.read();
            String str = "Control Panel\\Desktop";
            if (isImageFile(file)) {
                File convertToBmpAndMove = convertToBmpAndMove(context, file, guestContainersManager);
                if (convertToBmpAndMove != null) {
                    wineRegistryEditor.setStringParam(str, "Wallpaper", new StringBuffer().append(new StringBuffer().append("Z://home//xdroid//.wine").append("/").toString()).append(convertToBmpAndMove.getName()).toString());
                    wineRegistryEditor.write();
                    Toast.makeText(context, "Wallpaper set successfully", 0).show();
                    return;
                }
            }
            Toast.makeText(context, "Failed to set wallpaper. Selected file is not an image.", 0).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to set wallpaper", 0).show();
        }
    }

    public static int[] getDefaultScreenSize() {
        DisplayMetrics displayMetrics = AndroidHelpers.getDisplayMetrics();
        int i = (int) (((float) displayMetrics.widthPixels) / displayMetrics.density);
        int i2 = (int) (((float) displayMetrics.heightPixels) / displayMetrics.density);
        int max = Math.max(i, i2);
        i2 = Math.min(i, i2);
        return new int[]{Math.max(max, 800), Math.max(i2, 600)};
    }

    public static boolean isImageFile(File file) {
        String toLowerCase = file.getName().toLowerCase();
        return toLowerCase.endsWith(".jpg") || toLowerCase.endsWith(".jpeg") || toLowerCase.endsWith(".png");
    }
}