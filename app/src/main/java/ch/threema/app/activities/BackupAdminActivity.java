/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.activities;

import static ch.threema.app.preference.service.PreferenceService.LockingMech_NONE;
import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.fragments.BackupDataFragment;
import ch.threema.app.threemasafe.BackupThreemaSafeFragment;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class BackupAdminActivity extends ThreemaToolbarActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BackupAdminActivity");

    private static final String BUNDLE_IS_UNLOCKED = "biu";

    private boolean isUnlocked;
    private ThreemaSafeMDMConfig safeConfig;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

        isUnlocked = false;
        safeConfig = ThreemaSafeMDMConfig.getInstance();

        if (!this.requiredInstances() || AppRestrictionUtil.isBackupsDisabled(this)) {
            this.finish();
            return;
        }

        if (AppRestrictionUtil.isDataBackupsDisabled(this) && threemaSafeUIDisabled()) {
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

        if (preferenceService.getBackupWarningDismissedTime() == 0L) {
            ((TextView) findViewById(R.id.notice_text)).setText(R.string.backup_explain_text);
            final View noticeLayout = findViewById(R.id.notice_layout);
            noticeLayout.setVisibility(View.VISIBLE);
            findViewById(R.id.close_button).setOnClickListener(v -> {
                preferenceService.setBackupWarningDismissedTime(System.currentTimeMillis());
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

        if (!isUnlocked) {
            if (!preferenceService.getLockMechanism().equals(LockingMech_NONE)) {
                HiddenChatUtil.launchLockCheckDialog(this, preferenceService);
            }
        }
    }

    public int getLayoutResource() {
        return R.layout.activity_backup_admin;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ThreemaActivity.ACTIVITY_ID_CHECK_LOCK) {
            if (resultCode == RESULT_OK) {
                isUnlocked = true;
            } else {
                finish();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    @Override
    protected boolean checkInstances() {
        return TestUtil.required(
            this.serviceManager,
            this.preferenceService
        );
    }

    private boolean threemaSafeUIDisabled() {
        return ConfigUtils.isWorkRestricted() && safeConfig.isBackupAdminDisabled();
    }

    private boolean dataBackupUIDisabled() {
        return AppRestrictionUtil.isDataBackupsDisabled(this);
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
}
