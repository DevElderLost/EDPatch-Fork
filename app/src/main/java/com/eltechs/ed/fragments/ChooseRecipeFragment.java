package com.eltechs.ed.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.eltechs.ed.InstallRecipe;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;

import java.util.List;

public class ChooseRecipeFragment extends Fragment {

    private OnRecipeSelectedListener mListener;
    private RecyclerView mRecyclerView;
    private GuestContainer mGuestContainer;
    private GuestContainersManager mManager;

    public interface OnRecipeSelectedListener {
        void onRecipeSelected(InstallRecipe installRecipe);
    }

    private class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.ViewHolder> {

        private final List<InstallRecipe> mItems;

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public ImageView mImage;
            public TextView mText;
            public InstallRecipe mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mImage = (ImageView) view.findViewById(2131296401);
                mText = (TextView) view.findViewById(2131296508);
            }
        }

        public RecipeAdapter(List<InstallRecipe> items) {
            this.mItems = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(2131427358, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mItems.get(position);

            if (position == getItemCount() - 1) {
                holder.mImage.setImageResource(2131230882);
            } else {
                holder.mImage.setImageResource(2131230879);
            }

            holder.mText.setText(holder.mItem.toString());

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onRecipeSelected(holder.mItem);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FrameLayout root = (FrameLayout) inflater.inflate(2131427357, container, false);
        mRecyclerView = (RecyclerView) root.findViewById(2131296411);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getArguments() != null) {
            mGuestContainer = (GuestContainer) getArguments().getSerializable("guest_container");
            mManager = (GuestContainersManager) getArguments().getSerializable("guest_manager");
        }

        if (mGuestContainer == null || mManager == null) {
            // Fallback atau tampilkan pesan error
            return;
        }

        List<InstallRecipe> recipes = InstallRecipe.getAllRecipes(
                getContext(),
                mGuestContainer,
                mManager
        );

        mRecyclerView.setAdapter(new RecipeAdapter(recipes));

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(2131558563);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnRecipeSelectedListener) {
            mListener = (OnRecipeSelectedListener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement OnRecipeSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}