package com.eltechs.ed.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.text.TextUtils;
import com.eltechs.axs.Globals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;
import java.io.FileOutputStream;
import android.widget.Toast;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.eltechs.axs.helpers.Assert;
import com.eltechs.ed.R;
import com.eltechs.ed.XDGLink;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.eltechs.ed.fragments.EditXDGLinkDialogFragment;
import com.example.datainsert.exagear.shortcut.MoreShortcut;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseXDGLinkFragment extends Fragment {

    public static final String ARG_IS_START_MENU = "IS_START_MENU";
    private static final String PARENT_DIR_NAME = "..";
    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_LINK = 0;

    private List<GuestContainer> mContainers;
    private List<XDGNode> mCurrentItems;
    private XDGNode mCurrentNode;
    private int mDepth;
    private TextView mEmptyTextView;
    private GuestContainersManager mGcm;
    private boolean mIsStartMenu;
    private OnXDGLinkSelectedListener mListener;
    private RecyclerView mRecyclerView;
    private XDGLink currentSelectedLink;
    private XDGNode currentSelectedNode;
    private static final int REQUEST_CODE_PICK_ICON = 1001;

    public interface OnXDGLinkSelectedListener {
        void onXDGLinkSelected(XDGLink xDGLink);
    }

    public static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) outRect.top = spacing;
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                outRect.top = 0;
                outRect.bottom = spacing;
            }
        }
    }

    public class XDGNode implements Comparable<XDGNode> {
        GuestContainer mCont;
        File mFile;
        XDGLink mLink;

        XDGNode(GuestContainer guestContainer, File file, XDGLink xDGLink) {
            this.mCont = guestContainer;
            this.mFile = file;
            this.mLink = xDGLink;
        }

        public boolean isUpNode() {
            return this.mFile.getPath().equals(PARENT_DIR_NAME);
        }

        @Override
        public int compareTo(@NonNull XDGNode other) {
            if (isUpNode()) return -1;
            if (other.isUpNode()) return 1;

            if (mFile.isDirectory() && !other.mFile.isDirectory()) return -1;
            if (!mFile.isDirectory() && other.mFile.isDirectory()) return 1;

            return mFile.compareTo(other.mFile);
        }

        @NonNull
        @Override
        public String toString() {
            if (isUpNode()) return PARENT_DIR_NAME;
            if (mFile.isDirectory()) return mFile.getName();
            Assert.state(mLink.name != null);
            return mLink.name;
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mListener = (OnXDGLinkSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnXDGLinkSelectedListener");
        }
    }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    FrameLayout root = (FrameLayout) inflater.inflate(2131427453, container, false);

    mRecyclerView = root.findViewById(2131296411);
    mEmptyTextView = root.findViewById(2131296377);

    // Hitung spanCount secara dinamis
    int spanCount = calculateSpanCount();

    GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
    mRecyclerView.setLayoutManager(layoutManager);

    // Spacing tetap (bisa disesuaikan)
    float spacingDp = 12f;
    int spacingPx = (int) (spacingDp * getResources().getDisplayMetrics().density + 0.5f);
    mRecyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacingPx, true));

    mRecyclerView.setClipToPadding(false);
    mRecyclerView.setPadding(spacingPx, spacingPx, spacingPx, spacingPx);

    return root;
}

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mGcm = GuestContainersManager.getInstance(getContext());
        mContainers = mGcm.getContainersList();
        mIsStartMenu = getArguments().getBoolean(ARG_IS_START_MENU, false);

        mDepth = 0;
        mCurrentNode = null;
        mCurrentItems = getCurrentNodeContent();

        updateUI();

        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.getSupportActionBar().setTitle(
                mIsStartMenu ? 2131558565 : 2131558561
        );
    }

    private void updateUI() {
        if (mCurrentItems.isEmpty()) {
            mEmptyTextView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyTextView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            if (mRecyclerView.getAdapter() instanceof XDGNodeAdapter) {
                XDGNodeAdapter adapter = (XDGNodeAdapter) mRecyclerView.getAdapter();
                adapter.mItems.clear();
                adapter.mItems.addAll(mCurrentItems);
                adapter.notifyDataSetChanged();
            } else {
                mRecyclerView.setAdapter(new XDGNodeAdapter(mCurrentItems));
            }
        }
    }

    private List<XDGNode> getRootNodeContent() {
        GuestContainer current = mGcm.getCurrentContainer();
        if (current != null) {
            String path = mIsStartMenu ? current.mStartMenuPath : current.mDesktopPath;
            return getNodeContent(new XDGNode(current, new File(path), null), true);
        }

        List<XDGNode> items = new ArrayList<>();
        for (GuestContainer c : mContainers) {
            String path = mIsStartMenu ? c.mStartMenuPath : c.mDesktopPath;
            items.addAll(getNodeContent(new XDGNode(c, new File(path), null), true));
        }
        return items;
    }

  private List<XDGNode> getNodeContent(XDGNode parentNode, boolean isRoot) {
        List<XDGNode> items = new ArrayList<>();
        if (!isRoot) {
            items.add(new XDGNode(parentNode.mCont, new File(PARENT_DIR_NAME), null));
        }

        File[] files = parentNode.mFile.listFiles();
        if (files == null) return items;

        for (File file : files) {
            if (file.isDirectory()) {
                items.add(new XDGNode(parentNode.mCont, file, null));
            } else if (file.getName().toLowerCase().endsWith(".desktop")) {
                try {
                    items.add(new XDGNode(parentNode.mCont, file, new XDGLink(parentNode.mCont, file)));
                } catch (IOException e) {
                    e.printStackTrace();
                }

        }
        return items;
  }

    private List<XDGNode> getCurrentNodeContent() {
        List<XDGNode> content;
        if (mDepth == 0) {
            content = getRootNodeContent();
        } else {
            content = getNodeContent(mCurrentNode, false);
        }
        Collections.sort(content);
        return content;
    }

    public void refresh() {
        if (mCurrentNode != null && mCurrentNode.mFile.exists()) {
            mCurrentItems = getCurrentNodeContent();
        } else {
            mDepth = 0;
            mCurrentNode = null;
            mCurrentItems = getCurrentNodeContent();
        }
        updateUI();
    }
    
    private int calculateSpanCount() {
    // Ambil lebar layar dalam pixel
    int screenWidthPx = getResources().getDisplayMetrics().widthPixels;

    // Konversi ke dp (ini otomatis menyesuaikan dengan density/DPI)
    float screenWidthDp = screenWidthPx / getResources().getDisplayMetrics().density;

    // Tentukan lebar minimal per kolom (item) dalam dp
    // Contoh: 160dp → cocok untuk ikon + text di hp kecil
    //        180dp → lebih lega, mirip tampilan emulator/grid game
    final int minItemWidthDp = 160;   // ← sesuaikan nilai ini sesuai desainmu

    // Hitung jumlah kolom maksimal
    int calculatedSpan = (int) (screenWidthDp / minItemWidthDp);

    // Batasi minimal 2 kolom (agar tidak jadi 1 di hp sangat kecil)
    // Batasi maksimal (misal 6, agar tidak terlalu banyak di tablet 10"+)
    return Math.max(2, Math.min(calculatedSpan, 6));
}
    
    @Override
public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (mRecyclerView != null && mRecyclerView.getLayoutManager() instanceof GridLayoutManager) {
        ((GridLayoutManager) mRecyclerView.getLayoutManager()).setSpanCount(calculateSpanCount());
    }
}
    
        // ────────────────────────────────────────────────
    //           BAGIAN BARU: CHANGE ICON
    // ────────────────────────────────────────────────

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"image/png", "image/jpeg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Pilih ikon (PNG atau JPG)"),
                    REQUEST_CODE_PICK_ICON
            );
        } catch (Exception e) {
            Toast.makeText(getContext(), "Tidak ada aplikasi untuk memilih file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Context context = Globals.getAppContext();
        GuestContainersManager guestContainersManager = new GuestContainersManager(context);
        GuestContainer cont = guestContainersManager.getCurrentContainer();

        if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null || currentSelectedLink == null) return;

            try {
                // 1. Tentukan nama ikon yang diinginkan (dari field Icon= atau nama file .desktop)
                String iconBaseName = currentSelectedLink.icon;
                if (TextUtils.isEmpty(iconBaseName)) {
                    String desktopName = currentSelectedLink.linkFile.getName();
                    iconBaseName = desktopName.substring(0, desktopName.lastIndexOf('.'));
                }

                String newIconFilename = iconBaseName + ".png";

                // 2. Lokasi tujuan: biasanya di folder yang sama dengan .desktop
                File parentDir = new File(cont.mIconsPath);
                File destFile = new File(parentDir, newIconFilename);

                // 3. Copy file
                processAndSaveIcon(uri, destFile);

                // 4. Update baris Icon= di .desktop
                updateDesktopIconLine(currentSelectedLink.linkFile, iconBaseName); // tanpa .png

//                Toast.makeText(getContext(), "Ikon berhasil diubah", Toast.LENGTH_SHORT).show();

                // 5. Refresh daftar
                refresh();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Gagal mengubah ikon: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            currentSelectedLink = null;
            currentSelectedNode = null;
        }
    }
    
    private void processAndSaveIcon(Uri sourceUri, File destFile) throws IOException {
    final float TARGET_RATIO = 3f / 4f;  // width / height = 3:4 (portrait, mirip cover AoE2 DE)
    // Alternatif: 1f / 1f untuk square, atau 4f / 5f, atau 2f / 3f — sesuaikan sesuai keinginan
    final int TARGET_WIDTH = 512;   // ukuran output (bisa 256, 512, 1024 tergantung kebutuhan sharpness)

    // Baca bitmap dari URI (sample size untuk hemat memori)
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(sourceUri), null, options);

    options.inSampleSize = calculateInSampleSize(options, TARGET_WIDTH * 2, (int)(TARGET_WIDTH / TARGET_RATIO) * 2);
    options.inJustDecodeBounds = false;

    Bitmap original = BitmapFactory.decodeStream(
            requireContext().getContentResolver().openInputStream(sourceUri), null, options);

    if (original == null) throw new IOException("Gagal decode gambar");

    int origWidth = original.getWidth();
    int origHeight = original.getHeight();
    float origRatio = (float) origWidth / origHeight;

    Bitmap result;

    if (Math.abs(origRatio - TARGET_RATIO) < 0.05f) {
        // Sudah hampir sama rasio → cukup resize
        result = Bitmap.createScaledBitmap(original, TARGET_WIDTH, (int)(TARGET_WIDTH / TARGET_RATIO), true);
    } else {
        // Crop tengah agar cocok rasio target
        int cropWidth, cropHeight;
        if (origRatio > TARGET_RATIO) {
            // Gambar lebih lebar → crop sisi kiri-kanan
            cropHeight = origHeight;
            cropWidth = (int)(cropHeight * TARGET_RATIO);
        } else {
            // Gambar lebih tinggi → crop atas-bawah
            cropWidth = origWidth;
            cropHeight = (int)(cropWidth / TARGET_RATIO);
        }

        int left = (origWidth - cropWidth) / 2;
        int top = (origHeight - cropHeight) / 2;

        Bitmap cropped = Bitmap.createBitmap(original, left, top, cropWidth, cropHeight);

        // Resize ke target size
        result = Bitmap.createScaledBitmap(cropped, TARGET_WIDTH, (int)(TARGET_WIDTH / TARGET_RATIO), true);

        cropped.recycle();
    }

    // Simpan ke file
    try (FileOutputStream fos = new FileOutputStream(destFile)) {
        result.compress(Bitmap.CompressFormat.PNG, 90, fos);
    }

    original.recycle();
    result.recycle();
}

// Helper untuk sampling agar tidak OOM pada gambar besar
private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
    final int height = options.outHeight;
    final int width = options.outWidth;
    int inSampleSize = 1;

    if (height > reqHeight || width > reqWidth) {
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2;
        }
    }
    return inSampleSize;
}

    private void copyUriToFile(Uri uri, File destFile) throws IOException {
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private void updateDesktopIconLine(File desktopFile, String newIconValue) throws IOException {
        List<String> lines = Files.readAllLines(desktopFile.toPath(), StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        boolean found = false;

        for (String line : lines) {
            if (line.trim().startsWith("Icon=")) {
                newLines.add("Icon=" + newIconValue);
                found = true;
            } else {
                newLines.add(line);
            }
        }

        if (!found) {
            // Cari posisi setelah [Desktop Entry]
            int insertPos = 0;
            for (int i = 0; i < newLines.size(); i++) {
                if (newLines.get(i).trim().equals("[Desktop Entry]")) {
                    insertPos = i + 1;
                    break;
                }
            }
            newLines.add(insertPos, "Icon=" + newIconValue);
        }

        Files.write(desktopFile.toPath(), newLines, StandardCharsets.UTF_8);
    }

  // Method helper di ChooseXDGLinkFragment
  private void editXDGLink(XDGNode clicked) {
    if (clicked == null || clicked.mLink == null) {
        Toast.makeText(getContext(), "Tidak ada shortcut yang valid", Toast.LENGTH_SHORT).show();
        return;
    }

    File linkFile = clicked.mLink.linkFile;
    if (linkFile == null || !linkFile.exists()) {
        Toast.makeText(getContext(), "File shortcut tidak ditemukan", Toast.LENGTH_SHORT).show();
        return;
    }

    String desktopPath = linkFile.getAbsolutePath();

    EditXDGLinkDialogFragment dialog = EditXDGLinkDialogFragment.createDialog(desktopPath);

    // Pasang callback refresh
    dialog.setOnEditSavedListener(new EditXDGLinkDialogFragment.OnEditSavedListener() {
        @Override
        public void onXDGLinkEdited() {
            refresh();  // ← langsung panggil method refresh() milik fragment ini
            // Optional: bisa scroll ke posisi tertentu atau highlight item
        }
    });

    dialog.show(getFragmentManager(), "EDIT_XDG_LINK");
    // atau getSupportFragmentManager() tergantung setup Activity kamu
  }
  

    private class XDGNodeAdapter extends RecyclerView.Adapter<XDGNodeAdapter.ViewHolder> {

        private final List<XDGNode> mItems;

        XDGNodeAdapter(List<XDGNode> items) {
            this.mItems = new ArrayList<>(items);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(2131427454, parent, false);
            return new ViewHolder(view, viewType);
        }

        @SuppressLint("NonConstantResourceId")
        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            XDGNode node = mItems.get(position);
            holder.mItem = node;

            if (node.mLink == null) {
                holder.mImage.setImageResource(2131230882);
                holder.mImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                holder.mImage.setImageDrawable(
                        new BitmapDrawable(getResources(), mGcm.getIconPath(node.mLink))
                );
                holder.mImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.mImage.setAdjustViewBounds(true);
                holder.mImage.setCropToPadding(false);
            }

            holder.mText.setText(node.toString());
            holder.mSubText.setText(node.isUpNode() ? "" : node.mCont.mConfig.getName());

            // Short click: masuk folder atau jalankan shortcut
            holder.mView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                XDGNode clicked = mItems.get(pos);

                if (clicked.mLink != null) {
                    mListener.onXDGLinkSelected(clicked.mLink);
                    return;
                }

                if (clicked.isUpNode()) {
                    if (mDepth <= 0) return;
                    mDepth--;
                    if (mDepth == 0) {
                        mCurrentNode = null;
                    } else {
                        File parent = mCurrentNode.mFile.getParentFile();
                        if (parent != null && parent.exists()) {
                            mCurrentNode = new XDGNode(mCurrentNode.mCont, parent, null);
                        } else {
                            mCurrentNode = null;
                            mDepth = 0;
                        }
                    }
                } else {
                    mDepth++;
                    mCurrentNode = new XDGNode(clicked.mCont, clicked.mFile, null);
                }

                mCurrentItems = getCurrentNodeContent();
                updateUI();
            });

      holder.mView.setOnLongClickListener(
          v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            XDGNode clicked = mItems.get(pos);
            if (clicked.mLink == null && !mIsStartMenu) {
              // Opsional: skip popup jika folder di mode Desktop
              return false;
            }

            PopupMenu popup = new PopupMenu(getContext(), v);
            // Tetap panggil ini untuk opsi tambahan dari modul lain
            MoreShortcut.addOptionsToMenu(mIsStartMenu, popup, clicked.mLink);

            // Inflate menu dari XML (satu atau dua resource)
            popup.inflate(mIsStartMenu ? 2131492869 : 2131492866);
            // atau gunakan satu menu: popup.inflate(R.menu.menu_xdg_context);
            try {
              Field fieldPopup = popup.getClass().getDeclaredField("mPopup");
              fieldPopup.setAccessible(true);
              Object mPopup = fieldPopup.get(popup);
              mPopup
                  .getClass()
                  .getDeclaredMethod("setForceShowIcon", boolean.class)
                  .invoke(mPopup, true);
            } catch (Exception e) {
              e.printStackTrace();
            }

            // Tangani semua klik di sini
            popup.setOnMenuItemClickListener(
                item -> {
                  int itemId = item.getItemId();

                  if (itemId == 2131296543) {
                    new AlertDialog.Builder(requireActivity())
                        .setTitle("Shortcut deletion")
                        .setIcon(R.drawable.ic_warning_24dp)
                        .setMessage(
                            "This will only delete shortcut, not application or associated container.\n\nDelete shortcut?")
                        .setPositiveButton(
                            "Delete",
                            (dialog, which) -> {
                              clicked.mLink.linkFile.delete();
                              refresh();
                              dialog.dismiss();
                            })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
                    return true;
                  } else if (itemId == 2131296542) {
                    // Hanya proses jika benar-benar shortcut dan di Start Menu
                    if (mIsStartMenu && clicked.mLink != null) {
                      mGcm.copyXDGLinkToDesktop(clicked.mLink);
                    }
                    return true;
                  } else if (itemId == 2131300840) {
                    GuestContainer container = clicked.mCont;
                    if (container != null
                        && container.mConfig.getRunGuide() != null
                        && !container.mConfig.getRunGuide().isEmpty()) {
                      ContainerRunGuideDFragment.createDialog(container, true)
                          .show(getActivity().getSupportFragmentManager(), "CONT_RUN_GUIDE");
                    }
                    return true;
                  } else if (itemId == 2131300841) { // GANTI DENGAN ID ASLI ANDA
                    if (clicked.mLink == null) return false;
                    currentSelectedLink = clicked.mLink;
                    currentSelectedNode = clicked;
                    openImagePicker();
                    return true;
                  } else if (itemId == 2131300835) { // ID menu "Edit"
                    // clicked adalah XDGNode yang dipilih (dari adapter atau variable member)
                    editXDGLink(clicked); // panggil method di atas
                    return true;
                  }

                  return false;
                });

            // Opsional: nonaktifkan item yang tidak relevan
            Menu menu = popup.getMenu();
            if (!mIsStartMenu) {
              MenuItem copyItem = menu.findItem(2131296542);
              if (copyItem != null) copyItem.setVisible(false);
            }

            // Nonaktifkan Show run guide jika tidak ada konten
            GuestContainer container = clicked.mCont;
            MenuItem guideItem = menu.findItem(2131300840);
            if (guideItem != null) {
              boolean hasGuide =
                  (container != null
                      && container.mConfig.getRunGuide() != null
                      && !container.mConfig.getRunGuide().isEmpty());
              guideItem.setVisible(hasGuide);
            }

            if (menu.size() > 0) { // atau gunakan hasVisibleItems() di API lebih baru
              popup.show();
            }

            return true;
          });
            // Sembunyikan tombol titik tiga sepenuhnya
            holder.mButton.setVisibility(View.GONE);
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).mLink == null ? VIEW_TYPE_FOLDER : VIEW_TYPE_LINK;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View mView;
            ImageView mImage;
            TextView mText;
            TextView mSubText;
            ImageButton mButton;
            XDGNode mItem;

            ViewHolder(View itemView, int viewType) {
                super(itemView);
                mView = itemView;
                mImage = itemView.findViewById(2131296401);
                mText = itemView.findViewById(2131296508);
                mSubText = itemView.findViewById(2131296504);
                mButton = itemView.findViewById(2131296309);

                // Tombol titik tiga tidak digunakan lagi
                mButton.setVisibility(View.GONE);
            }
        }
    }
}