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

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.fragments.ConversationsFragment;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.IntentDataUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ComposeMessageActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
    private static final Logger logger = getThreemaLogger("ComposeMessageActivity");

    private static final int ID_HIDDEN_CHECK_ON_NEW_INTENT = 9291;
    private static final int ID_HIDDEN_CHECK_ON_CREATE = 9292;
    private static final String DIALOG_TAG_HIDDEN_NOTICE = "hidden";

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private ComposeMessageFragment composeMessageFragment;
    private ConversationsFragment conversationsFragment;

    private Intent currentIntent;
    private int savedSoftInputMode;

    private final String COMPOSE_FRAGMENT_TAG = "compose_message_fragment";
    private final String MESSAGES_FRAGMENT_TAG = "message_section_fragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.info("onCreate");

        getWindow().setAllowEnterTransitionOverlap(true);
        getWindow().setAllowReturnTransitionOverlap(true);
        this.currentIntent = getIntent();
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.appbar),
            InsetSides.ltr()
        );
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        logger.info("initActivity");

        this.getFragments();

        if (findViewById(R.id.messages) != null) {
            // add messages fragment in tablet layout
            if (conversationsFragment == null) {
                conversationsFragment = new ConversationsFragment();
                getSupportFragmentManager().beginTransaction().add(R.id.messages, conversationsFragment, MESSAGES_FRAGMENT_TAG).commit();
            }
        }

        boolean isHidden = checkHiddenChatLock(getIntent(), ID_HIDDEN_CHECK_ON_CREATE);
        if (composeMessageFragment == null) {
            // fragment no longer around
            composeMessageFragment = new ComposeMessageFragment();
            if (isHidden) {
                getSupportFragmentManager().beginTransaction().add(R.id.compose, composeMessageFragment, COMPOSE_FRAGMENT_TAG).hide(composeMessageFragment).commit();
            } else {
                getSupportFragmentManager().beginTransaction().add(R.id.compose, composeMessageFragment, COMPOSE_FRAGMENT_TAG).commit();
            }
        } else {
            if (!isHidden) {
                getSupportFragmentManager().beginTransaction().show(composeMessageFragment).commit();
            }
        }
        return true;
    }

    @Override
    public int getLayoutResource() {
        return ConfigUtils.isTabletLayout(this) ? R.layout.activity_compose_message_tablet : R.layout.activity_compose_message;
    }

    private void getFragments() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        composeMessageFragment = (ComposeMessageFragment) fragmentManager.findFragmentByTag(COMPOSE_FRAGMENT_TAG);
        conversationsFragment = (ConversationsFragment) fragmentManager.findFragmentByTag(MESSAGES_FRAGMENT_TAG);
    }

    @Override
    public void onNewIntent(@NonNull Intent intent) {
        logger.info("onNewIntent");

        super.onNewIntent(intent);

        this.currentIntent = intent;

        this.getFragments();

        if (composeMessageFragment != null) {
            if (!checkHiddenChatLock(intent, ID_HIDDEN_CHECK_ON_NEW_INTENT)) {
                getSupportFragmentManager().beginTransaction().show(composeMessageFragment).commit();
                composeMessageFragment.onNewIntent(intent);
            }
        }
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        logger.info("handleOnBackPressed");
        if (ConfigUtils.isTabletLayout()) {
            if (conversationsFragment != null) {
                if (conversationsFragment.onBackPressed()) {
                    return;
                }
            }
        }
        if (composeMessageFragment != null) {
            if (!composeMessageFragment.onBackPressed()) {
                finish();
                if (ConfigUtils.isTabletLayout()) {
                    overridePendingTransition(0, 0);
                }
            }
            return;
        }
        finish();
    }

    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        super.onDestroy();
    }

    @Override
    public void onStop() {
        logger.info("onStop");
        super.onStop();
    }

    @Override
    public void onResume() {
        logger.info("onResume");
        super.onResume();

        // Set the soft input mode to resize when activity resumes because it is set to adjust nothing while it is paused
        savedSoftInputMode = getWindow().getAttributes().softInputMode;
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onPause() {
        logger.info("onPause");
        super.onPause();

        // Set the soft input mode to adjust nothing while paused. This is needed when the keyboard is opened to edit the contact before sending.
        if (savedSoftInputMode > 0) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        switch (requestCode) {
            case ID_HIDDEN_CHECK_ON_CREATE:
                super.onActivityResult(requestCode, resultCode, intent);

                if (resultCode == RESULT_OK) {
                    if (composeMessageFragment != null) {
                        getSupportFragmentManager().beginTransaction().show(composeMessageFragment).commit();
                        // mark conversation as read as soon as it's unhidden
                        composeMessageFragment.markAsRead();
                    }
                } else {
                    finish();
                }
                break;
            case ID_HIDDEN_CHECK_ON_NEW_INTENT:
                super.onActivityResult(requestCode, resultCode, intent);

                if (resultCode == RESULT_OK) {
                    if (composeMessageFragment != null) {
                        getSupportFragmentManager().beginTransaction().show(composeMessageFragment).commit();
                        composeMessageFragment.onNewIntent(this.currentIntent);
                    }
                } else {
                    if (!ConfigUtils.isTabletLayout()) {
                        finish();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, intent);

                // required for result of qr code scanner
                if (composeMessageFragment != null) {
                    composeMessageFragment.onActivityResult(requestCode, resultCode, intent);
                }
        }
    }

    private boolean checkHiddenChatLock(Intent intent, int requestCode) {
        MessageReceiver<?> messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(getApplicationContext(), intent);

        if (messageReceiver == null) {
            logger.info("Intent does not have any extras. Check \"Don't keep activities\" option in developer settings.");
            return false;
        }

        if (dependencies.getConversationCategoryService().isPrivateChat(messageReceiver.getUniqueIdString())) {
            if (ConfigUtils.hasProtection(dependencies.getPreferenceService())) {
                HiddenChatUtil.launchLockCheckDialog(this, null, dependencies.getPreferenceService(), requestCode);
            } else {
                GenericAlertDialog.newInstance(R.string.hide_chat, R.string.hide_chat_enter_message_explain, R.string.set_lock, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_HIDDEN_NOTICE);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        ConfigUtils.adjustToolbar(this, getToolbar());

        FrameLayout messagesLayout = findViewById(R.id.messages);

        if (messagesLayout != null) {
            // adjust width of messages fragment in tablet layout
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messagesLayout.getLayoutParams();
            layoutParams.width = getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
            messagesLayout.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onYes(String tag, Object data) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SHOW_SECURITY_FRAGMENT, true);
        startActivity(intent);
        finish();
    }

    @Override
    public void onNo(String tag, Object data) {
        finish();
    }
}
