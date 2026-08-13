package com.eltechs.ed.utils;

import android.content.Context;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ActionHandlerOne {
    public static String getExternalStorageId(Context context) {
        if (!isExternalStorageAvailable()) {
            return (String) null;
        }
        File externalStorageDirectory = getExternalStorageDirectory(context);
        if (externalStorageDirectory == null) {
            return (String) null;
        }
        String[] split = externalStorageDirectory.getAbsolutePath().split("/");
        return split.length > 2 ? split[2] : (String) null;
    }

    private static void addFoldersToList(File file, List<String> list) {
        if (file != null && file.isDirectory()) {
            File[] listFiles = file.listFiles();
            if (listFiles != null) {
                for (File file2 : listFiles) {
                    if (file2.isDirectory()) {
                        list.add(file2.getName());
                    }
                }
            }
        }
    }

    public static List<String> getFolderList(File file) {
        ArrayList arrayList = new ArrayList();
        addFoldersToList(file, arrayList);
        if (arrayList.isEmpty()) {
            File file2 = new File(file, "ExaGear");
            if (!file2.exists()) {
                file2.mkdir();
            }
            arrayList.add(file2.getName());
        }
        return arrayList;
    }

    private static File getExternalStorageDirectory(Context context) {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, (String) null);
        return (externalFilesDirs.length <= 1 || externalFilesDirs[1] == null) ? (File) null : externalFilesDirs[1];
    }

    public static boolean isExternalStorageAvailable() {
        return Environment.getExternalStorageState().equals("mounted");
    }
}