package com.eltechs.ed.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Toast;

import com.eltechs.axs.AppConfig;
import com.eltechs.axs.Globals;
import com.eltechs.axs.activities.FrameworkActivity;
import com.eltechs.axs.applicationState.ApplicationStateBase;
import com.eltechs.axs.helpers.AndroidHelpers;
import com.eltechs.ed.ContainerPackage;
import com.eltechs.ed.InstallRecipe;
import com.eltechs.ed.R;
import com.eltechs.ed.XDGLink;
import com.eltechs.ed.fragments.ChooseFileFragment;
import com.eltechs.ed.fragments.ChoosePackagesDFragment;
import com.eltechs.ed.fragments.ChooseRecipeFragment;
import com.eltechs.ed.fragments.ChooseXDGLinkFragment;
import com.eltechs.ed.fragments.ContainerRunGuideDFragment;
import com.eltechs.ed.fragments.ContainerSettingsFragment;
import com.eltechs.ed.fragments.ManageContainersFragment;
import com.eltechs.ed.guestContainers.GuestContainer;
import com.eltechs.ed.guestContainers.GuestContainersManager;
import com.eltechs.ed.startupActions.StartGuest;
import com.eltechs.ed.startupActions.WDesktop;
import com.example.datainsert.exagear.FAB.FabMenu;
import com.example.datainsert.exagear.virgloverlay.OverlayBuildUI;

import java.io.File;
import java.util.List;

/* loaded from: classes.dex */
public class EDMainActivity<StateClass extends ApplicationStateBase<StateClass>> 
    extends FrameworkActivity<StateClass> 
    implements ChooseRecipeFragment.OnRecipeSelectedListener,
               ChooseFileFragment.OnFileSelectedListener,
               ChooseXDGLinkFragment.OnXDGLinkSelectedListener,
               ManageContainersFragment.OnManageContainersActionListener,
               ChoosePackagesDFragment.OnPackagesSelectedListener,
               ContainerRunGuideDFragment.OnContRunGuideResListener {

    private static final String FRAGMENT_TAG_CHOOSE_FILE = "CHOOSE_FILE";
    private static final String FRAGMENT_TAG_CONTAINER_PROP = "CONTAINER_PROP";
    private static final String FRAGMENT_TAG_DESKTOP = "DESKTOP";
    private static final String FRAGMENT_TAG_INSTALL_NEW = "INSTALL_NEW";
    private static final String FRAGMENT_TAG_MANAGE_CONTAINERS = "MANAGE_CONTAINERS";
    private static final String FRAGMENT_TAG_START_MENU = "START_MENU";
    private static final int ON_START_ACTION_SHOW_MANAGE_CONTAINERS = 0;
    private static final String TAG = "EDMainActivity";
    private static final File mUserAreaDir = new File(AndroidHelpers.getMainSDCard(), "Exagear");

    private AppConfig mAppCfg = AppConfig.getInstance(this);
    private GuestContainer mChoosenCont;
    private XDGLink mChoosenXDGLink;
    private InstallRecipe mChosenRecipe;
    private DrawerLayout mDrawerLayout;
    private boolean mIsHomeActionBack;
    private NavigationView mNavigationView;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.ed_main);
        this.mDrawerLayout = findViewById(R.id.ed_main_drawer);
        this.mNavigationView = findViewById(R.id.ed_main_nav_view);
        NavigationItemSelectedListener navigationItemSelectedListener = new NavigationItemSelectedListener();
        this.mNavigationView.setNavigationItemSelectedListener(navigationItemSelectedListener);
        setSupportActionBar(findViewById(R.id.ed_main_toolbar));
        ActionBar supportActionBar = getSupportActionBar();
        supportActionBar.setDisplayHomeAsUpEnabled(true);
        supportActionBar.setHomeAsUpIndicator(R.drawable.ic_menu_24dp);
        getSupportFragmentManager().addOnBackStackChangedListener(new BackStackChangedListener());

        if (bundle == null) {
            Integer eDMainOnStartAction = this.mAppCfg.getEDMainOnStartAction();
            navigationItemSelectedListener.onNavigationItemSelected(this.mNavigationView.getMenu().findItem(R.id.ed_main_menu_desktop));
            if (eDMainOnStartAction.intValue() == 0) {
                navigationItemSelectedListener.onNavigationItemSelected(this.mNavigationView.getMenu().findItem(R.id.ed_main_menu_manage_containers));
            }
            this.mAppCfg.setEDMainOnStartAction(-1);
        }
        // new OverlayBuildUI(this);
        // new FabMenu(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        changeUIByCurFragment();
    }

    private void setHomeIsActionBack(boolean z) {
        this.mIsHomeActionBack = z;
        getSupportActionBar().setHomeAsUpIndicator(this.mIsHomeActionBack ? 0 : R.drawable.ic_menu_24dp);
    }

    public void changeUIByCurFragment() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.ed_main_fragment_container);
        if (currentFragment == null || currentFragment.getTag() == null) {
            setHomeIsActionBack(false);
            return;
        }

        String tag = currentFragment.getTag();
        setHomeIsActionBack(tag.equals(FRAGMENT_TAG_CONTAINER_PROP) || tag.equals(FRAGMENT_TAG_CHOOSE_FILE));

        switch (tag) {
            case FRAGMENT_TAG_DESKTOP:
                this.mNavigationView.setCheckedItem(R.id.ed_main_menu_desktop);
                break;
            case FRAGMENT_TAG_START_MENU:
                this.mNavigationView.setCheckedItem(R.id.ed_main_menu_start_menu);
                break;
            case FRAGMENT_TAG_INSTALL_NEW:
                this.mNavigationView.setCheckedItem(R.id.ed_main_menu_install_new);
                break;
            case FRAGMENT_TAG_MANAGE_CONTAINERS:
                this.mNavigationView.setCheckedItem(R.id.ed_main_menu_manage_containers);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == 16908332) { // android.R.id.home
            if (this.mIsHomeActionBack) {
                getSupportFragmentManager().popBackStack();
                return true;
            }
            this.mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onRecipeSelected(InstallRecipe installRecipe) {
        this.mChosenRecipe = installRecipe;
        ChooseFileFragment chooseFileFragment = new ChooseFileFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ChooseFileFragment.ARG_ROOT_PATH, mUserAreaDir.getAbsolutePath());
        bundle.putString(ChooseFileFragment.ARG_DOWNLOAD_URL, this.mChosenRecipe.getDownloadURL());
        chooseFileFragment.setArguments(bundle);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.ed_main_fragment_container, chooseFileFragment, FRAGMENT_TAG_CHOOSE_FILE);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public void onFileSelected(String str) {
        getApplicationState().getStartupActionsCollection().addAction(
            new StartGuest(new StartGuest.InstallApp(null, str, this.mChosenRecipe))
        );
        signalUserInteractionFinished(WDesktop.UserRequestedAction.GO_FURTHER);
    }

    @Override
    public void onXDGLinkSelected(XDGLink xDGLink) {
        this.mChoosenXDGLink = xDGLink;
        GuestContainer guestContainer = xDGLink.guestCont;
        if (guestContainer != null && guestContainer.mConfig.getRunGuide() != null 
            && !guestContainer.mConfig.getRunGuide().isEmpty() 
            && !guestContainer.mConfig.getRunGuideShown().booleanValue()) {
            
            ContainerRunGuideDFragment.createDialog(guestContainer, false)
                .show(getSupportFragmentManager(), "CONT_RUN_GUIDE");
        } else {
            startXDGLink(xDGLink);
        }
    }

    @Override
    public void onContRunGuideRes(boolean z) {
        if (this.mChoosenXDGLink != null) {
            startXDGLink(this.mChoosenXDGLink);
        }
    }

    private void startXDGLink(XDGLink xDGLink) {
        getApplicationState().getStartupActionsCollection().addAction(
            new StartGuest(new StartGuest.RunXDGLink(xDGLink))
        );
        signalUserInteractionFinished(WDesktop.UserRequestedAction.GO_FURTHER);
    }

    @Override
    public void onManageContainersRunExplorer(GuestContainer guestContainer) {
        getApplicationState().getStartupActionsCollection().addAction(
            new StartGuest(new StartGuest.RunExplorer(guestContainer))
        );
        signalUserInteractionFinished(WDesktop.UserRequestedAction.GO_FURTHER);
    }

    @Override
    public void onManageContainersInstallPackages(GuestContainer guestContainer) {
        this.mChoosenCont = guestContainer;
        new ChoosePackagesDFragment().show(getSupportFragmentManager(), "CHOOSE_PACKAGES");
    }

    @Override
    public void onPackagesSelected(List<ContainerPackage> list) {
        getApplicationState().getStartupActionsCollection().addAction(
            new StartGuest(new StartGuest.InstallPackage(this.mChoosenCont, list))
        );
        this.mAppCfg.setEDMainOnStartAction(0);
        signalUserInteractionFinished(WDesktop.UserRequestedAction.GO_FURTHER);
    }

    @Override
    public void onManageContainerSettingsClick(GuestContainer guestContainer) {
        this.mChoosenCont = guestContainer;
        ContainerSettingsFragment fragment = new ContainerSettingsFragment();
        Bundle bundle = new Bundle();
        bundle.putLong("CONT_ID", guestContainer.mId);
        fragment.setArguments(bundle);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.ed_main_fragment_container, fragment, FRAGMENT_TAG_CONTAINER_PROP);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.addToBackStack(null);
        ft.commit();
    }

    /* loaded from: classes.dex */
    private class NavigationItemSelectedListener implements NavigationView.OnNavigationItemSelectedListener {

        @SuppressLint("NonConstantResourceId")
        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            Fragment fragment = null;
            String tag = null;

            switch (menuItem.getItemId()) {
                case R.id.ed_main_menu_desktop:
                    menuItem.setChecked(true);
                    fragment = new ChooseXDGLinkFragment();
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(ChooseXDGLinkFragment.ARG_IS_START_MENU, false);
                    fragment.setArguments(bundle);
                    tag = FRAGMENT_TAG_DESKTOP;
                    break;

                case R.id.ed_main_menu_help:
                    startActivity(EDHelpActivity.class);
                    mDrawerLayout.closeDrawers();
                    return true;

                case R.id.ed_main_menu_install_new:
                    menuItem.setChecked(true);

                    GuestContainersManager manager = GuestContainersManager.getInstance(EDMainActivity.this);
                    GuestContainer currentContainer = manager.getCurrentContainer();

                    if (currentContainer == null) {
                        Toast.makeText(EDMainActivity.this, 
                            "Belum ada container aktif. Buat atau pilih container terlebih dahulu.", 
                            Toast.LENGTH_LONG).show();
                        mDrawerLayout.closeDrawers();
                        return true;
                    }

                    fragment = new ChooseRecipeFragment();
                    // Tidak perlu kirim objek container lewat Bundle
                    // ChooseRecipeFragment akan mengambil current container sendiri
                    tag = FRAGMENT_TAG_INSTALL_NEW;
                    break;

                case R.id.ed_main_menu_manage_containers:
                    menuItem.setChecked(true);
                    fragment = new ManageContainersFragment();
                    tag = FRAGMENT_TAG_MANAGE_CONTAINERS;
                    break;

                case R.id.ed_main_menu_start_menu:
                    menuItem.setChecked(true);
                    fragment = new ChooseXDGLinkFragment();
                    Bundle bundle2 = new Bundle();
                    bundle2.putBoolean(ChooseXDGLinkFragment.ARG_IS_START_MENU, true);
                    fragment.setArguments(bundle2);
                    tag = FRAGMENT_TAG_START_MENU;
                    break;

                default:
                    return false;
            }

            if (fragment != null) {
                FragmentManager fm = getSupportFragmentManager();
                // Bersihkan back stack agar tidak menumpuk
                for (int i = 0; i < fm.getBackStackEntryCount(); i++) {
                    fm.popBackStack();
                }

                FragmentTransaction ft = fm.beginTransaction();
                ft.replace(R.id.ed_main_fragment_container, fragment, tag);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                
                // Hanya tambah ke back stack jika bukan menu utama (desktop)
                if (menuItem.getItemId() != R.id.ed_main_menu_desktop) {
                    ft.addToBackStack(null);
                }
                
                ft.commit();
            }

            mDrawerLayout.closeDrawers();
            return true;
        }
    }

    /* loaded from: classes.dex */
    private class BackStackChangedListener implements FragmentManager.OnBackStackChangedListener {
        @Override
        public void onBackStackChanged() {
            changeUIByCurFragment();
        }
    }
}