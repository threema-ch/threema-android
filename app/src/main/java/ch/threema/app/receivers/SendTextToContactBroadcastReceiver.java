package ch.threema.app.receivers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.AddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.Failed;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.notifications.BackgroundErrorNotification;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.stores.IdentityProvider;
import ch.threema.storage.models.ContactModel;

public class SendTextToContactBroadcastReceiver extends ActionBroadcastReceiver {
    private static final Logger logger = getThreemaLogger("SendTextToContactBroadcastReceiver");

    @NonNull
    private final IdentityProvider identityProvider = KoinJavaComponent.get(IdentityProvider.class);

    @Override
    @SuppressLint("StaticFieldLeak")
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null) {
            return;
        }

        int id = intent.getIntExtra(BackgroundErrorNotification.EXTRA_NOTIFICATION_ID, 0);
        if (id != 0) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
            notificationManagerCompat.cancel(id);
        }

        String text = intent.getStringExtra(BackgroundErrorNotification.EXTRA_TEXT_TO_SEND);
        if (text != null) {

            final PendingResult pendingResult = goAsync();

            String identity = intent.getStringExtra(AppConstants.INTENT_DATA_CONTACT);
            if (identity == null) {
                logger.error("Provided identity is null");
                return;
            }

            String myIdentity = identityProvider.getIdentityString();
            if (myIdentity == null) {
                logger.error("User identity is null");
                return;
            }

            AddOrUpdateContactBackgroundTask<Boolean> sendMessageTask = new AddOrUpdateContactBackgroundTask<>(
                identity,
                ContactModel.AcquaintanceLevel.DIRECT,
                myIdentity,
                apiConnector,
                contactModelRepository,
                AddContactRestrictionPolicy.CHECK,
                KoinJavaComponent.get(AppRestrictions.class),
                null
            ) {
                @Override
                public void onBefore() {
                    // We need to make sure there's a connection during delivery
                    lifetimeService.acquireConnection(TAG);
                }

                @Override
                @NonNull
                public Boolean onContactResult(@NonNull ContactResult result) {
                    if (result instanceof Failed) {
                        logger.error("Could not add contact: {}", context.getString(((Failed) result).message));
                        return false;
                    }

                    try {
                        final ContactModel contactModel = contactService.getByIdentity(identity);
                        if (contactModel == null) {
                            return false;
                        }
                        MessageReceiver<?> messageReceiver = contactService.createReceiver(contactModel);
                        messageService.sendText(text, messageReceiver);
                        messageService.markConversationAsRead(messageReceiver, notificationService);
                        logger.debug("Message sent to: {}", messageReceiver);
                        return true;
                    } catch (Exception e) {
                        logger.error("Exception", e);
                        return false;
                    } finally {
                        lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);
                    }
                }

                @Override
                public void onFinished(@NonNull Boolean success) {
                    Toast.makeText(context, success ? R.string.message_sent : R.string.verify_failed, Toast.LENGTH_LONG).show();
                    pendingResult.finish();
                }
            };

            backgroundExecutor.execute(sendMessageTask);
        }
    }
}
