package com.eltechs.ed.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.eltechs.ed.MSLink;
import com.example.datainsert.exagear.FAB.dialogfragment.DriveD;
import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.*;

import com.eltechs.axs.Globals;
import com.eltechs.axs.helpers.AndroidHelpers;

import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.eltechs.ed.utils.SimpleExeIconExtractor;
import com.eltechs.ed.utils.WallpaperConverter;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.eltechs.ed.fragments.CncDdrawInstallDialogFragment;

public class ChooseFileFragment extends Fragment {

    public static final String ARG_DOWNLOAD_URL = "DOWNLOAD_URL";
    public static final String ARG_ROOT_PATH = "ROOT_PATH";

    private static final String NO_EXE_FILES = AndroidHelpers.getString(2131558559);
    private static final String PARENT_DIR_NAME = "..";

    private static final int VIEW_TYPE_REGULAR = 0;
    private static final int VIEW_TYPE_NO_EXE = 1;

    private File mRootDir;
    private File mCurrentDir;
    private List<File> mCurrentItems;

    private String mDownloadUrl;
    private RecyclerView mRecyclerView;
    private OnFileSelectedListener mListener;

    /* ========================================================= */
    /* ======================= ADAPTER ========================= */
    /* ========================================================= */

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        private final List<File> mItems;

        class ViewHolder extends RecyclerView.ViewHolder {

            ImageView mImage;
            TextView mText;
            View mView;
            File mItem;

            ViewHolder(View view, int viewType) {
                super(view);
                mView = view;
                mImage = view.findViewById(2131296401);
                mText = view.findViewById(2131296508);

                if (viewType == VIEW_TYPE_NO_EXE) {
                    mImage.setVisibility(View.GONE);
                    mText.setText(NO_EXE_FILES);
                    mText.setGravity(Gravity.CENTER);
                    mText.setPadding(0, 0, 0, 0);
                    mView.setClickable(false);
                }
            }
        }

        FileAdapter(List<File> items) {
            this.mItems = items;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).getPath().equals(NO_EXE_FILES)
                    ? VIEW_TYPE_NO_EXE
                    : VIEW_TYPE_REGULAR;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(2131427358, parent, false);
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {

            if (getItemViewType(position) == VIEW_TYPE_NO_EXE) {
                return;
            }

            final File file = mItems.get(position);
            holder.mItem = file;
            
            final String endName = file.getName().toLowerCase();

            // Set icon
            if (file.getPath().equals(PARENT_DIR_NAME) || file.isDirectory()) {
                holder.mImage.setImageResource(2131230882);
            } else if (endName.endsWith(".jpg") || endName.endsWith(".jpeg") || endName.endsWith(".png")) {
                // Thumbnail image
                holder.mImage.setImageBitmap(ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(file.getAbsolutePath()), 128, 128 ));
            } else if (endName.endsWith(".exe")) {
                boolean iconSet = false;
                Context context = Globals.getAppContext();
                File parentDir = file.getParentFile();
                GuestContainersManager guestContainersManager = new GuestContainersManager(context);
                GuestContainer cont = guestContainersManager.getCurrentContainer();
                if (cont != null) {
                    File iconsDir = new File(cont.mIconsPath);
                    if (iconsDir.exists() && iconsDir.isDirectory()) {
                        String exeBase = file.getName().replaceAll("(?i)\\.exe$", "").trim();
                        String baseLower = exeBase.toLowerCase();
                        File[] candidates = iconsDir.listFiles((dir, name) -> {
                            String nLower = name.toLowerCase();
                            return nLower.endsWith(".0.png") && nLower.contains(baseLower);
                        });
                        if (candidates != null && candidates.length > 0) {
                            File iconsFile = candidates[0];
                            Bitmap bmp = BitmapFactory.decodeFile(iconsFile.getAbsolutePath());
                            if (bmp != null) {
                                holder.mImage.setImageDrawable(new BitmapDrawable(getResources(), bmp));
                                holder.mImage.setScaleX(0.9f);
                                holder.mImage.setScaleY(0.9f);
                                holder.mText.setText(file.getName());
                                holder.mView.setOnClickListener(v -> {
                                    File klik = mItems.get(holder.getAdapterPosition());
                                    final String imgFile = file.getName().toLowerCase();
                                    mListener.onFileSelected(klik.getAbsolutePath());
                                });
                                iconSet = true;
                            }
                        }
                    }
                }

        holder.mView.setOnLongClickListener(
            new View.OnLongClickListener() {
              @Override
              public boolean onLongClick(View v) {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenu().add(0, 1002, 0, "Create Shortcut");
                popup.getMenu().add(0, 1001, 0, "Install cnc-ddraw");

                popup.setOnMenuItemClickListener(
                    new PopupMenu.OnMenuItemClickListener() {
                      @Override
                      public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == 1001) {
                          CncDdrawInstallDialogFragment dialog =
                              CncDdrawInstallDialogFragment.newInstance(file.getAbsolutePath());

                          dialog.setTargetFragment(ChooseFileFragment.this, 0);

                          // Untuk support library v4
                          dialog.show(getFragmentManager(), "CncDdrawInstall");

                          return true;
                        } else if (item.getItemId() == 1002) {
                            createShortcut(file);
                        }
                        return false;
                      }
                    });

                popup.show();
                return true;
              }
            });
            } else {
                holder.mImage.setImageResource(2131230879);
            }

            holder.mText.setText(file.getName());

            holder.mView.setOnClickListener(v -> {
                File clicked = mItems.get(holder.getAdapterPosition());
                final String imageFile = file.getName().toLowerCase();

                if (clicked.getPath().equals(PARENT_DIR_NAME)) {
                    mCurrentDir = mCurrentDir.getParentFile();
                    reloadDirectory();
                } else if (clicked.isDirectory()) {
                    mCurrentDir = clicked;
                    reloadDirectory();
                } else if (endName.endsWith(".jpg") || endName.endsWith(".jpeg") || endName.endsWith(".png")) {
                    try {
                    GuestContainersManager manager = GuestContainersManager.getInstance(getContext());
                    WallpaperConverter.setAsWallpaper(getContext(), clicked, manager);

                    } catch (Exception e) {
                        e.printStackTrace();
                      }
                } else {
                    mListener.onFileSelected(clicked.getAbsolutePath());
                }
            });
      
        }
    }

    /* ========================================================= */
    /* ======================= LOGIC =========================== */
    /* ========================================================= */

    public void reloadDirectory() {
        mCurrentItems = getDirContent(mCurrentDir, mRootDir);
        mRecyclerView.setAdapter(new FileAdapter(mCurrentItems));
    }

    List<File> getDirContent(File dir, File root) {

        List<File> directories = new ArrayList<>();
        List<File> executables = new ArrayList<>();

        if (!dir.equals(root)) {
            directories.add(new File(PARENT_DIR_NAME));
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {

                if (file.isDirectory()) {
                    directories.add(file);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".exe") ||
                        name.endsWith(".msi") ||
                        name.endsWith(".bat") ||
                        name.endsWith(".jpg") ||
                        name.endsWith(".jpeg") ||
                        name.endsWith(".png")) {
                        executables.add(file);
                    }
                }
            }
        }

        Collections.sort(directories);
        Collections.sort(executables);

        directories.addAll(executables);

        if (executables.isEmpty()) {
            directories.add(new File(NO_EXE_FILES));
        }

        return directories;
    }

  private String getSmartDisplayName(File file) {
    String name = file.getName()
            .replaceFirst("(?i)\\.exe$", "")
            .replaceFirst("(?i)\\.bat$", "")
            .replaceFirst("(?i)\\.msi$", "")
            .trim();

    String lower = name.toLowerCase();

    // Daftar nama generik yang sebaiknya diabaikan
    String[] generic = {"patch", "loader", "client", "app", "main", "boot", "play", 
                        "application", "shipping", "x64", "x86", "win64", "win32", 
                        "binaries", "mod", "fix", "crack"};

    boolean isGeneric = false;
    for (String g : generic) {
        if (lower.contains(g) || lower.equals(g)) {
            isGeneric = true;
            break;
        }
    }

    if (isGeneric || name.length() < 4) {
        File parent = file.getParentFile();
        if (parent != null) {
            String parentName = parent.getName().toLowerCase();
            // Cek apakah parent adalah folder generik
            String[] skipFolders = {"bin", "bin32", "bin64", "system", "release", "retail"};
            if (java.util.Arrays.asList(skipFolders).contains(parentName)) {
                File grandParent = parent.getParentFile();
                if (grandParent != null) {
                    return cleanGameName(grandParent.getName());
                }
            }
            return cleanGameName(parent.getName());
        }
    }

    return cleanGameName(name);
}

private String cleanGameName(String name) {
    // Bisa ditambah logika lebih lanjut: hapus karakter aneh, capitalize, dll
    return name.trim().replaceAll("[_\\-\\.]+", " ");
}

  private void createShortcut(File exeFile) {
    Context ctx = Globals.getAppContext();

    try {
        GuestContainersManager gcm = GuestContainersManager.getInstance(ctx);
        GuestContainer container = gcm.getCurrentContainer();

        if (container == null) {
            Toast.makeText(ctx, "Tidak ada container aktif", Toast.LENGTH_LONG).show();
            return;
        }

        String displayName = getSmartDisplayName(exeFile);

        String wmClassName = displayName
                .toLowerCase()
                .replace(".exe", "")
                .replace(".Exe", "");

        String desktopDir = container.mDesktopPath;
        File desktopFile = new File(desktopDir, displayName + ".desktop");

        if (desktopFile.exists()) {
            Toast.makeText(ctx, "Shortcut sudah ada: " + desktopFile.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        // ==========================================================
        // ROOT DRIVE D
        File driveDRoot = DriveD.getDriveDDir();
        String driveDRootPath = driveDRoot.getAbsolutePath();

        String hostExePath = exeFile.getAbsolutePath();
        String hostDirPath = exeFile.getParentFile().getAbsolutePath();

        Log.d("SHORTCUT", "Drive D root : " + driveDRootPath);
        Log.d("SHORTCUT", "Exe host path: " + hostExePath);

        // Pastikan exe berada di dalam Drive D
        if (!hostExePath.startsWith(driveDRootPath)) {
            Toast.makeText(ctx, "File harus berada di Drive D", Toast.LENGTH_LONG).show();
            Log.e("SHORTCUT", "Exe bukan bagian dari Drive D");
            return;
        }

        // relative path exe
        String relativeExe = hostExePath.substring(driveDRootPath.length());
        if (relativeExe.startsWith("/")) relativeExe = relativeExe.substring(1);

        // relative working directory
        String relativeDir = hostDirPath.substring(driveDRootPath.length());
        if (relativeDir.startsWith("/")) relativeDir = relativeDir.substring(1);

        // convert path
        String windowsRelativeExe = relativeExe.replace("/", "\\\\\\\\");
        String windowsRelativeDir = relativeDir.replace("\\", "/");

        String fullWindowsPath = "D:\\\\\\\\" + windowsRelativeExe;
        String wineWorkingDir = "d:/" + windowsRelativeDir;

        Log.d("SHORTCUT", "Windows path: " + fullWindowsPath);
        Log.d("SHORTCUT", "Working dir : " + wineWorkingDir);

        // ==========================================================

        try (java.io.PrintWriter writer = new java.io.PrintWriter(desktopFile)) {

            writer.println("[Desktop Entry]");
            writer.println("Type=Application");
            writer.println("Name=" + displayName);

            writer.println("Exec=env WINEPREFIX=\"/home/xdroid/.wine\" wine " + fullWindowsPath);

            writer.println("StartupNotify=true");

            writer.println("Path=/home/xdroid/.wine/dosdevices/" + wineWorkingDir);

            writer.println("Icon=" + displayName + ".0");

            writer.println("StartupWMClass=" + wmClassName);
        }

        Toast.makeText(ctx, "Shortcut dibuat:\n" + desktopFile.getName(), Toast.LENGTH_LONG).show();

    } catch (Exception e) {

        Log.e("SHORTCUT", "Gagal membuat shortcut", e);

        Toast.makeText(ctx,
                "Gagal membuat shortcut:\n" + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }
}


    /* ========================================================= */
    /* =================== FRAGMENT LIFECYCLE ================== */
    /* ========================================================= */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDownloadUrl = getArguments().getString(ARG_DOWNLOAD_URL);
        if (mDownloadUrl != null) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        FrameLayout root =
                (FrameLayout) inflater.inflate(2131427357, container, false);

        mRecyclerView = root.findViewById(2131296411);
        mRecyclerView.setLayoutManager(
                new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.addItemDecoration(
                new DividerItemDecoration(
                        mRecyclerView.getContext(),
                        VIEW_TYPE_NO_EXE));

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mRootDir = new File(getArguments().getString(ARG_ROOT_PATH));
        mCurrentDir = mRootDir;

        reloadDirectory();

        ((AppCompatActivity) getActivity())
                .getSupportActionBar()
                .setTitle(2131558564);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnFileSelectedListener) context;
        } catch (ClassCastException e) {
            throw new RuntimeException(
                    context.toString() +
                            " must implement OnFileSelectedListener");
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(2131492864, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == 2131296326) {
            startActivity(
                    new Intent(Intent.ACTION_VIEW,
                            Uri.parse(mDownloadUrl)));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* ========================================================= */
    /* ===================== INTERFACE ========================= */
    /* ========================================================= */

    public interface OnFileSelectedListener {
        void onFileSelected(String path);
    }
}