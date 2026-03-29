package com.eltechs.ed.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.eltechs.axs.Globals;
import com.eltechs.axs.helpers.Assert;
import com.eltechs.ed.R;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.eltechs.ed.guestContainers.WineTheme;
import com.example.datainsert.exagear.FAB.dialogfragment.DriveD;
import com.example.datainsert.exagear.FAB.dialogfragment.PulseAudio;
import com.example.datainsert.exagear.QH;
import com.eltechs.ed.fragments.ContainerOperationProgressDialog;

import java.util.List;

public class ManageContainersFragment extends Fragment {

    private static final int ACTION_NEW = 0;
    private static final int ACTION_CLONE = 1;
    private static final int ACTION_DELETE = 2;

    private List<GuestContainer> mContainers;
    private GuestContainersManager mGcm;

    private RecyclerView mRecyclerView;
    private TextView mEmptyTextView;

    private ContainerOperationProgressDialog mProgressDialogFragment;

    private OnManageContainersActionListener mListener;

    private static final String PREFS_NAME = "com.eltechs.axs.CONFIG";
    private static final String KEY_CURRENT_GUEST_CONT_ID = "CURRENT_GUEST_CONT_ID";

    // ========================================================================
    // AsyncTask dengan custom progress dialog
    // ========================================================================
    private class ContAsyncTask extends AsyncTask<GuestContainer, String, Boolean> {

        private final int mAction;
        private String mOperationName;

        ContAsyncTask(int action) {
            this.mAction = action;
            switch (action) {
                case ACTION_NEW:
                    mOperationName = "Membuat container baru";
                    break;
                case ACTION_CLONE:
                    mOperationName = "Mengkloning container";
                    break;
                case ACTION_DELETE:
                    mOperationName = "Menghapus container";
                    break;
                default:
                    mOperationName = "Operasi container";
            }
        }

        @Override
        protected void onPreExecute() {
            if (getActivity() == null) return;

            mProgressDialogFragment = ContainerOperationProgressDialog.newInstance(
                    mOperationName,
                    "Sedang mempersiapkan..."
            );
            mProgressDialogFragment.show(getChildFragmentManager(), "container_progress");
        }

        @Override
        protected Boolean doInBackground(GuestContainer... params) {
            try {
                publishProgress("Memulai operasi...");

                switch (mAction) {
                    case ACTION_NEW:
                        publishProgress("Membuat struktur direktori...");
                        mGcm.createContainer();
                        publishProgress("Container baru berhasil dibuat");
                        break;

                    case ACTION_CLONE:
                        if (params.length == 0) return false;
                        GuestContainer source = params[0];
                        publishProgress("Menyalin container: " + source.mConfig.getName());
                        mGcm.cloneContainer(source);
                        publishProgress("Kloning selesai");
                        break;

                    case ACTION_DELETE:
                        if (params.length == 0) return false;
                        GuestContainer target = params[0];
                        publishProgress("Menghapus container: " + target.mConfig.getName());
                        mGcm.deleteContainer(target);
                        publishProgress("Penghapusan selesai");
                        break;

                    default:
                        return false;
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                publishProgress("Terjadi kesalahan: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (mProgressDialogFragment != null && values.length > 0) {
                mProgressDialogFragment.updateMessage(values[0]);
            }
        }

    @Override
    protected void onPostExecute(Boolean success) {
    if (mProgressDialogFragment == null) {
        refreshContainersList();
        return;
    }

    final String message = success 
        ? mOperationName + " berhasil" 
        : mOperationName + " gagal";

    final boolean isSuccess = success;

    mProgressDialogFragment.finishOperation(message);

    // Tunggu dialog benar-benar hilang (karena ada delay 600ms di finishOperation)
    mProgressDialogFragment.getDialog().getWindow().getDecorView()
        .postDelayed(() -> {
            if (isAdded() && getContext() != null) {
                refreshContainersList();
//                Toast.makeText(getContext(), message, isSuccess ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
            mProgressDialogFragment = null;
        }, 900);   // 600ms (dari dialog) + margin kecil
}
    }

    // ========================================================================
    // RecyclerView Adapter
    // ========================================================================
    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {

        private final List<GuestContainer> mItems;

        ContainersAdapter(List<GuestContainer> items) {
            this.mItems = items;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View root;
            final ImageView image;
            final TextView title;
            final TextView subTitle;
            final ImageButton button;         // titik tiga
            final ImageButton selectButton;   // tombol pilih

            GuestContainer item;

            ViewHolder(View view) {
                super(view);
                root = view;
                image = view.findViewById(2131296401);
                title = view.findViewById(2131296508);
                subTitle = view.findViewById(2131296504);
                button = view.findViewById(2131296309);
                selectButton = view.findViewById(2131300839);

                title.setGravity(Gravity.CENTER_VERTICAL);
                subTitle.setVisibility(View.GONE);
                root.setClickable(false);
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
            holder.item = mItems.get(position);

            holder.image.setImageResource(2131230876);
            holder.title.setText(holder.item.mConfig.getName());

            GuestContainer current = mGcm.getCurrentContainer();
            boolean isCurrent = (current != null && holder.item == current);

            holder.root.setBackgroundResource(isCurrent ? 2131099742 : 0);

            holder.button.setOnClickListener(v -> showPopupMenu(holder, v));

            holder.selectButton.setOnClickListener(v -> {
                setCurrentGuestContainer(holder.item);
                notifyDataSetChanged();
            });
        }

        private void showPopupMenu(final ViewHolder holder, View anchor) {
            final GuestContainer container = mItems.get(holder.getAdapterPosition());

            PopupMenu popup = new PopupMenu(getContext(), anchor);
            popup.inflate(2131492865);

            // Force icons to show (optional)
            try {
                java.lang.reflect.Field fieldPopup = popup.getClass().getDeclaredField("mPopup");
                fieldPopup.setAccessible(true);
                Object mPopup = fieldPopup.get(popup);
                mPopup.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(mPopup, true);
            } catch (Exception ignored) {
            }

            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 2131296332: // clone
                        new ContAsyncTask(ACTION_CLONE).execute(container);
                        break;
                    case 2131296333: // delete
                        new ContAsyncTask(ACTION_DELETE).execute(container);
                        break;
                    case 2131296334:
                        mListener.onManageContainersInstallPackages(container);
                        break;
                    case 2131296335:
                        mListener.onManageContainerSettingsClick(container);
                        break;
                    case 2131296336:
                        mListener.onManageContainersRunExplorer(container);
                        break;
                    case 2131300751:
                        WineTheme.ShowTheme(getContext());
                        break;
                }
                return true;
            });

            popup.setOnDismissListener(menu -> refreshContainersList());
            popup.show();
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    // ========================================================================
    // Lifecycle & UI Setup
    // ========================================================================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mGcm = GuestContainersManager.getInstance(getContext());
        refreshContainersList();

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(2131558562);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(2131427357, container, false);

        mRecyclerView = root.findViewById(2131296411);
        mEmptyTextView = root.findViewById(2131296377);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mProgressDialogFragment != null && mProgressDialogFragment.isVisible()) {
            mProgressDialogFragment.dismissAllowingStateLoss();
            mProgressDialogFragment = null;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnManageContainersActionListener) {
            mListener = (OnManageContainersActionListener) context;
        } else {
            throw new ClassCastException(context + " must implement OnManageContainersActionListener");
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(2131492868, menu);
        inflater.inflate(2131492877, menu);
        inflater.inflate(2131492880, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == 2131296417) {           // New container
            new ContAsyncTask(ACTION_NEW).execute((GuestContainer) null);
            return true;
        }
        if (itemId == 2131300755) {           // Drive D
            showDriveDFragment();
            return true;
        }
        if (itemId == 2131300758) {           // PulseAudio
            showPulseAudio();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ========================================================================
    // Helper methods
    // ========================================================================
    private void refreshContainersList() {
        mContainers = mGcm.getContainersList();
        mRecyclerView.setAdapter(new ContainersAdapter(mContainers));

        if (mEmptyTextView != null) {
            mEmptyTextView.setVisibility(mContainers.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setCurrentGuestContainer(GuestContainer container) {
        if (container == null || getContext() == null) return;

        long containerId = container.mId;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_CURRENT_GUEST_CONT_ID, containerId)
                .apply();

//        Toast.makeText(getContext(), "Container aktif: " + container.mConfig.getName(), Toast.LENGTH_SHORT).show();
    }

    private void showPulseAudio() {
        PulseAudio pulseAudio = new PulseAudio();
        pulseAudio.show(getChildFragmentManager(), "PulseAudio");
    }

    private void showDriveDFragment() {
        DriveD driveD = new DriveD();
        driveD.show(getChildFragmentManager(), "DriveDDialog");
    }

    public interface OnManageContainersActionListener {
        void onManageContainerSettingsClick(GuestContainer container);
        void onManageContainersInstallPackages(GuestContainer container);
        void onManageContainersRunExplorer(GuestContainer container);
    }
}