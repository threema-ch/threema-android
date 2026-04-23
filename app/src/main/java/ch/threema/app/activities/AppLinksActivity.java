package ch.threema.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import ch.threema.android.ToastDuration;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.applock.CheckAppLockContract;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.contactdetails.ContactDetailActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.utils.executor.BackgroundExecutor;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.models.ContactModel;
import kotlin.Lazy;
import kotlin.Unit;

import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.LazyKt.lazy;

public class AppLinksActivity extends ThreemaToolbarActivity {
    private final static Logger logger = getThreemaLogger("AppLinksActivity");

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @NonNull
    private final Lazy<BackgroundExecutor> backgroundExecutor = lazy(BackgroundExecutor::new);

    private final ActivityResultLauncher<Unit> checkLockToHandleIntentLauncher = registerForActivityResult(new CheckAppLockContract(), unlocked -> {
        if (unlocked) {
            dependencies.getLockAppService().unlock(null);
            handleIntent();
        } else {
            showToast(this, R.string.pin_locked_cannot_send, ToastDuration.LONG);
            finish();
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (dependencies.getIdentityProvider().getIdentityString() == null) {
            finish();
            return;
        }
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

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
        if (dependencies.getLockAppService().isLocked()) {
            checkLockToHandleIntentLauncher.launch(Unit.INSTANCE);
        } else {
            handleIntent();
        }
    }

    private void handleIntent() {
        String appLinkAction = getIntent().getAction();
        final Uri appLinkData = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData.getHost().equals(BuildConfig.contactActionUrl)) {
            handleContactUrl(appLinkAction, appLinkData);
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
                showToast(this, R.string.invalid_input, ToastDuration.LONG);
            }
        } else {
            showToast(this, R.string.invalid_input, ToastDuration.LONG);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void addNewContactAndOpenChat(@NonNull String identity, @NonNull Uri appLinkData) {
        backgroundExecutor.getValue().execute(
            new BasicAddOrUpdateContactBackgroundTask(
                identity,
                ContactModel.AcquaintanceLevel.DIRECT,
                dependencies.getUserService().getIdentity(),
                dependencies.getApiConnector(),
                dependencies.getContactModelRepository(),
                AddContactRestrictionPolicy.CHECK,
                dependencies.getAppRestrictions(),
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
                    intent.putExtra(AppConstants.INTENT_DATA_CONTACT, identity);
                    intent.putExtra(AppConstants.INTENT_DATA_EDITFOCUS, Boolean.TRUE);

                    if (text != null) {
                        text = text.trim();
                        intent.putExtra(AppConstants.INTENT_DATA_TEXT, text);
                    }

                    startActivity(intent);
                }
            }
        );
    }
}
