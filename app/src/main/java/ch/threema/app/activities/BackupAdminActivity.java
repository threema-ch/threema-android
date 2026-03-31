package ch.threema.app.activities;

import static ch.threema.app.preference.service.PreferenceService.LOCKING_MECH_NONE;
import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;

import ch.threema.app.R;
import ch.threema.app.applock.CheckAppLockContract;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.fragments.BackupDataFragment;
import ch.threema.app.threemasafe.BackupThreemaSafeFragment;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import kotlin.Unit;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class BackupAdminActivity extends ThreemaToolbarActivity {
    private static final Logger logger = getThreemaLogger("BackupAdminActivity");

    private static final String BUNDLE_IS_UNLOCKED = "biu";

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private boolean isUnlocked;
    private ThreemaSafeMDMConfig safeConfig;

    private final ActivityResultLauncher<Unit> checkLockLauncher = registerForActivityResult(new CheckAppLockContract(), unlocked -> {
        if (unlocked) {
            isUnlocked = true;
        } else {
            finish();
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

        isUnlocked = false;
        safeConfig = ThreemaSafeMDMConfig.getInstance();

        if (dependencies.getAppRestrictions().isBackupsDisabled()) {
            this.finish();
            return;
        }

        if (dependencies.getAppRestrictions().isDataBackupsDisabled() && threemaSafeUIDisabled()) {
            this.finish();
            return;
        }

        if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
            logger.debug("Not licensed.");
            this.finish();
            System.exit(0);
            return;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.my_backups_title);
        }

        TabLayout tabLayout = findViewById(R.id.tabs);
        ViewPager viewPager = findViewById(R.id.pager);
        viewPager.setAdapter(new BackupAdminPagerAdapter(getSupportFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);

        if (dependencies.getPreferenceService().getBackupWarningDismissedTime() == null) {
            ((TextView) findViewById(R.id.notice_text)).setText(R.string.backup_explain_text);
            final View noticeLayout = findViewById(R.id.notice_layout);
            noticeLayout.setVisibility(View.VISIBLE);
            findViewById(R.id.close_button).setOnClickListener(v -> {
                dependencies.getPreferenceService().setBackupWarningDismissedTime(Instant.now());
                AnimationUtil.collapse(noticeLayout, null, true);
            });
        } else {
            findViewById(R.id.notice_layout).setVisibility(View.GONE);
        }

        // recover lock state after rotation
        if (savedInstanceState != null) {
            isUnlocked = savedInstanceState.getBoolean(BUNDLE_IS_UNLOCKED, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isUnlocked && !dependencies.getPreferenceService().getLockMechanism().equals(LOCKING_MECH_NONE)) {
            checkLockLauncher.launch(Unit.INSTANCE);
        }
    }

    public int getLayoutResource() {
        return R.layout.activity_backup_admin;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private boolean threemaSafeUIDisabled() {
        return ConfigUtils.isWorkRestricted() && safeConfig.isBackupAdminDisabled();
    }

    private boolean dataBackupUIDisabled() {
        return dependencies.getAppRestrictions().isDataBackupsDisabled();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(BUNDLE_IS_UNLOCKED, isUnlocked);

        super.onSaveInstanceState(outState);
    }

    public class BackupAdminPagerAdapter extends FragmentPagerAdapter {
        BackupAdminPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public int getCount() {
            return (threemaSafeUIDisabled() || dataBackupUIDisabled()) ? 1 : 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return threemaSafeUIDisabled() ? getString(R.string.backup_data) : getString(R.string.threema_safe);
                case 1:
                    return getString(R.string.backup_data);
            }
            return super.getPageTitle(position);
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return threemaSafeUIDisabled() ? new BackupDataFragment() : new BackupThreemaSafeFragment();
                case 1:
                    return new BackupDataFragment();
            }
            return null;
        }
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, BackupAdminActivity.class);
    }
}
