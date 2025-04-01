/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.MainThread;

import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListAddActivity extends MemberChooseActivity implements TextEntryDialog.TextEntryDialogClickListener {

    private static final String DIALOG_TAG_ENTER_NAME = "enterName";
    private DistributionListService distributionListService;
    private DistributionListModel distributionListModel;
    private List<ContactModel> selectedContacts;

    private boolean isEdit = false;

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        try {
            this.distributionListService = serviceManager.getDistributionListService();
        } catch (Exception e) {
            LogUtil.exception(e, this);
            return false;
        }

        initData(savedInstanceState);

        return true;
    }

    @Override
    @MainThread
    protected void initData(final Bundle savedInstanceState) {
        if (this.getIntent().hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST)) {
            this.distributionListModel = this.distributionListService.getById(
                this.getIntent().getLongExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0));
            this.isEdit = this.distributionListModel != null;
        }

        if (isEdit && savedInstanceState == null) {
            for (final DistributionListMemberModel model : distributionListService.getDistributionListMembers(distributionListModel)) {
                preselectedIdentities.add(model.getIdentity());
            }
        }

        if (isEdit) {
            updateToolbarTitle(R.string.title_edit_distribution_list, R.string.title_select_contacts);
        } else {
            updateToolbarTitle(R.string.title_add_distribution_list, R.string.title_select_contacts);
        }

        initList();
    }

    @Override
    protected int getNotice() {
        return 0;
    }

    @Override
    protected int getMode() {
        return MODE_NEW_DISTRIBUTION_LIST;
    }

    @Override
    protected void menuNext(final List<ContactModel> contacts) {
        selectedContacts = contacts;

        if (selectedContacts.size() > 0) {
            String defaultString = null;
            if (this.isEdit && this.distributionListModel != null) {
                defaultString = this.distributionListModel.getName();
            }

            TextEntryDialog.newInstance(isEdit ? R.string.title_edit_distribution_list : R.string.title_add_distribution_list,
                R.string.enter_distribution_list_name,
                R.string.ok,
                0,
                R.string.cancel,
                defaultString,
                0,
                TextEntryDialog.INPUT_FILTER_TYPE_NONE,
                DistributionListModel.DISTRIBUTIONLIST_NAME_MAX_LENGTH_BYTES).show(getSupportFragmentManager(), DIALOG_TAG_ENTER_NAME);
        } else {
            Toast.makeText(this, getString(R.string.group_select_at_least_two), Toast.LENGTH_LONG).show();
        }
    }

    private void launchComposeActivity() {
        Intent intent = new Intent(DistributionListAddActivity.this, ComposeMessageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        distributionListService.createReceiver(distributionListModel).prepareIntent(intent);

        startActivity(intent);
        finish();
    }

    @Override
    public void onYes(@NonNull String tag, @NonNull String text) {
        try {
            String[] identities = new String[selectedContacts.size()];
            int pos = 0;
            for (ContactModel cm : selectedContacts) {
                if (cm != null) {
                    identities[pos++] = cm.getIdentity();
                }
            }

            if (isEdit) {
                if (pos > 0) {
                    distributionListService.updateDistributionList(
                        distributionListModel,
                        text,
                        identities
                    );
                }
                RuntimeUtil.runOnUiThread(this::launchComposeActivity);
            } else {
                distributionListModel = distributionListService.createDistributionList(
                    text,
                    identities);

                RuntimeUtil.runOnUiThread(this::launchComposeActivity);
            }


        } catch (Exception e) {
            LogUtil.exception(e, DistributionListAddActivity.this);
        }
    }

    @Override
    public void onNo(String tag) {
    }

    @Override
    public void onNeutral(String tag) {
    }
}
