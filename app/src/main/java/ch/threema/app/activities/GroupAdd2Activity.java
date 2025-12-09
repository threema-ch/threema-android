/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.AppConstants;
import androidx.compose.ui.platform.ComposeView;
import ch.threema.app.R;
import ch.threema.app.compose.common.interop.ComposeJavaBridge;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.groupflows.GroupFlowResult;
import ch.threema.app.groupflows.GroupCreateProperties;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.profilepicture.CheckedProfilePicture;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.CoroutinesExtensionKt;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.GroupModel;
import kotlin.Unit;
import kotlinx.coroutines.Deferred;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.app.groupflows.GroupFlowResultKt.GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS;

public class GroupAdd2Activity extends GroupEditActivity implements ContactEditDialog.ContactEditDialogClickListener {
    private static final Logger logger = getThreemaLogger("GroupAdd2Activity");

    private static final String BUNDLE_GROUP_IDENTITIES = "grId";

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private String[] groupIdentities;

    @Override
    public int getLayoutResource() {
        return R.layout.activity_group_add2;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.debug("onCreate");
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("");
        }

        if (getIntent() != null) {
            this.groupIdentities = IntentDataUtil.getContactIdentities(getIntent());
        }

        if (savedInstanceState == null) {
            launchGroupSetNameAndAvatarDialog();
        } else {
            groupIdentities = savedInstanceState.getStringArray(BUNDLE_GROUP_IDENTITIES);
        }
    }

    private void createGroup(
        @NonNull final String groupName,
        @NonNull final Set<String> groupIdentities,
        @Nullable final File avatarFile
    ) {
        final @NonNull ComposeView composeDialogView = findViewById(R.id.loading_dialog_container);
        composeDialogView.setVisibility(View.VISIBLE);
        ComposeJavaBridge.setLoadingWithTimeoutDialog(
            composeDialogView,
            R.string.creating_group,
            GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            this::onLoadingDialogDismissRequest,
            true
        );

        Deferred<GroupFlowResult> createGroupFlowResultDeferred = dependencies.getGroupFlowDispatcher().runCreateGroupFlow(
            this,
            new GroupCreateProperties(
                groupName,
                CheckedProfilePicture.getOrConvertFromFile(avatarFile),
                groupIdentities
            )
        );

        CoroutinesExtensionKt.onCompleted(
            createGroupFlowResultDeferred,
            exception -> {
                logger.error("The create-group-flow failed exceptionally", exception);
                onGroupCreationFailed(
                    GroupFlowResult.Failure.Other.INSTANCE,
                    composeDialogView
                );
                return Unit.INSTANCE;
            },
            groupFlowResult -> {
                if (groupFlowResult instanceof GroupFlowResult.Success) {
                    onGroupCreatedSuccessfully(((GroupFlowResult.Success) groupFlowResult).getGroupModel());
                } else if (groupFlowResult instanceof GroupFlowResult.Failure) {
                    onGroupCreationFailed(
                        (GroupFlowResult.Failure) groupFlowResult,
                        composeDialogView
                    );
                }
                return Unit.INSTANCE;
            }
        );
    }

    private Unit onLoadingDialogDismissRequest() {
        setResult(RESULT_CANCELED);
        finish();
        return Unit.INSTANCE;
    }

    @AnyThread
    private void onGroupCreatedSuccessfully(@NonNull GroupModel newModel) {
        RuntimeUtil.runOnUiThread(() -> {
            Intent intent = new Intent(this, ComposeMessageActivity.class);
            intent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, newModel.getDatabaseId());
            setResult(RESULT_OK);
            startActivity(intent);
            finish();
        });
    }

    @AnyThread
    private void onGroupCreationFailed(
        @NonNull GroupFlowResult.Failure createGroupFlowResultFailure,
        @NonNull ComposeView composeDialogView
    ) {
        RuntimeUtil.runOnUiThread(() -> {
            ComposeJavaBridge.setLoadingWithTimeoutDialog(
                composeDialogView,
                R.string.creating_group,
                GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
                this::onLoadingDialogDismissRequest,
                false
            );
            final @StringRes int errorMessageRes;
            if (createGroupFlowResultFailure instanceof GroupFlowResult.Failure.Network) {
                errorMessageRes = R.string.error_creating_group_network;
            } else {
                errorMessageRes = R.string.error_creating_group_internal;
            }
            new MaterialAlertDialogBuilder(GroupAdd2Activity.this)
                .setTitle(R.string.error)
                .setMessage(errorMessageRes)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener((dialog) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
        });
    }

    @Override
    public void onYes(String tag, String text1, String text2, @Nullable File avatarFile) {
        String groupName = text1 != null ? text1 : "";
        createGroup(groupName, Set.of(this.groupIdentities), avatarFile);
    }

    @Override
    public void onNo(String tag) {
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putSerializable(BUNDLE_GROUP_IDENTITIES, groupIdentities);
        super.onSaveInstanceState(outState);
    }
}
