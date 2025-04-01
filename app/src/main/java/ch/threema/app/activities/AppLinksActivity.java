/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.grouplinks.OutgoingGroupRequestActivity;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.LazyProperty;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.models.ContactModel;

public class AppLinksActivity extends ThreemaToolbarActivity {
    private final static Logger logger = LoggingUtil.getThreemaLogger("AppLinksActivity");

    @NonNull
    private final LazyProperty<BackgroundExecutor> backgroundExecutor = new LazyProperty<>(BackgroundExecutor::new);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkLock();
    }

    @Override
    public int getLayoutResource() {
        // invisible activity
        return 0;
    }

    @Override
    protected boolean isPinLockable() {
        // we handle pin locking ourselves
        return false;
    }

    private void checkLock() {
        LockAppService lockAppService;
        try {
            lockAppService = ThreemaApplication.requireServiceManager().getLockAppService();
        } catch (Exception e) {
            logger.error("Exception while checking lock", e);
            finish();
            return;
        }

        if (lockAppService.isLocked()) {
            HiddenChatUtil.launchLockCheckDialog(this, preferenceService);
        } else {
            handleIntent();
        }
    }

    private void handleIntent() {
        String appLinkAction = getIntent().getAction();
        final Uri appLinkData = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData.getHost().equals(BuildConfig.contactActionUrl)) {
            handleContactUrl(appLinkAction, appLinkData);
        } else if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData.getHost().equals(BuildConfig.groupLinkActionUrl)) {
            handleGroupLinkUrl(appLinkData);
        }
        finish();
    }

    private void handleContactUrl(String appLinkAction, Uri appLinkData) {
        final String threemaId = appLinkData.getLastPathSegment();
        if (threemaId != null) {
            if (threemaId.equalsIgnoreCase("compose")) {
                Intent intent = new Intent(this, RecipientListActivity.class);
                intent.setAction(appLinkAction);
                intent.setData(appLinkData);
                startActivity(intent);
            } else if (threemaId.length() == ProtocolDefines.IDENTITY_LEN) {
                addNewContactAndOpenChat(threemaId, appLinkData);
            } else {
                Toast.makeText(this, R.string.invalid_input, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_input, Toast.LENGTH_LONG).show();
        }
    }

    private void handleGroupLinkUrl(Uri appLinkData) {
        logger.info("Handle group link url");
        Intent intent = new Intent(this, OutgoingGroupRequestActivity.class);
        intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_LINK, appLinkData.getEncodedFragment());
        startActivity(intent);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ThreemaActivity.ACTIVITY_ID_CHECK_LOCK:
                if (resultCode == RESULT_OK) {
                    lockAppService.unlock(null);
                    handleIntent();
                } else {
                    Toast.makeText(this, getString(R.string.pin_locked_cannot_send), Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            case ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY:
                if (ThreemaApplication.getMasterKey().isLocked()) {
                    finish();
                } else {
                    ConfigUtils.recreateActivity(this, AppLinksActivity.class, getIntent().getExtras());
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void addNewContactAndOpenChat(@NonNull String identity, @NonNull Uri appLinkData) {
        UserService userService = serviceManager.getUserService();
        APIConnector apiConnector = serviceManager.getAPIConnector();
        ContactModelRepository contactModelRepository = serviceManager.getModelRepositories().getContacts();

        backgroundExecutor.get().execute(
            new BasicAddOrUpdateContactBackgroundTask(
                identity,
                ContactModel.AcquaintanceLevel.DIRECT,
                userService.getIdentity(),
                apiConnector,
                contactModelRepository,
                AddContactRestrictionPolicy.CHECK,
                AppLinksActivity.this,
                null
            ) {
                @Override
                public void onFinished(ContactResult result) {
                    if (!(result instanceof ContactAvailable)) {
                        logger.error("Could not add contact");
                        return;
                    }

                    String text = appLinkData.getQueryParameter("text");

                    Intent intent = new Intent(AppLinksActivity.this, text != null ?
                        ComposeMessageActivity.class :
                        ContactDetailActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
                    intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);

                    if (text != null) {
                        text = text.trim();
                        intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, text);
                    }

                    startActivity(intent);
                }
            }
        );
    }
}
