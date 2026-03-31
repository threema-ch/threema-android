package ch.threema.app.webclient.services.instance.message.updater;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.slf4j.Logger;

import java.util.List;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.datatypes.ContactNameFormat;

/**
 * Listen for changes that require the entire list of contacts to be refreshed in Threema Web.
 * <p>
 * Example: When the name format of the contacts changes.
 */
@WorkerThread
public class ReceiversUpdateHandler extends MessageUpdater {
    private static final Logger logger = getThreemaLogger("ReceiversUpdateHandler");

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final ContactSettingsListener contactSettingsListener;
    private final SynchronizeContactsListener synchronizeContactsListener;

    // Dispatchers
    private final MessageDispatcher updateDispatcher;

    // Services
    private final ContactService contactService;
    private final @NonNull PreferenceService preferenceService;

    @AnyThread
    public ReceiversUpdateHandler(
        @NonNull HandlerExecutor handler,
        MessageDispatcher updateDispatcher,
        ContactService contactService,
        @NonNull PreferenceService preferenceService
    ) {
        super(Protocol.SUB_TYPE_RECEIVERS);
        this.handler = handler;
        this.updateDispatcher = updateDispatcher;
        this.contactService = contactService;
        this.preferenceService = preferenceService;
        this.contactSettingsListener = new ContactSettingsListener();
        this.synchronizeContactsListener = new SynchronizeContactsListener();
    }

    @Override
    public void register() {
        logger.debug("register()");
        ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
        ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister()");
        ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
        ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
    }

    /**
     * Update the list of contacts.
     */
    private void updateContacts() {
        try {
            // Prepare args
            final MsgpackObjectBuilder args = new MsgpackObjectBuilder()
                .put(Protocol.ARGUMENT_RECEIVER_TYPE, Receiver.Type.CONTACT);

            // Convert contacts
            final List<MsgpackBuilder> data = Contact.convert(
                contactService.find(Contact.getContactFilter()),
                preferenceService.getContactNameFormat()
            );

            // Send message
            logger.debug("Sending receivers update");
            this.send(this.updateDispatcher, data, args);
        } catch (ConversionException e) {
            logger.error("Exception", e);
        }
    }

    @AnyThread
    private class ContactSettingsListener implements ch.threema.app.listeners.ContactSettingsListener {
        @Override
        public void onSortingChanged() {
            logger.debug("ContactSettingsListener: onSortingChanged");
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ReceiversUpdateHandler.this.updateContacts();
                }
            });
        }

        @Override
        public void onNameFormatChanged(@NonNull ContactNameFormat nameFormat) {
            logger.debug("ContactSettingsListener: onNameFormatChanged");
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ReceiversUpdateHandler.this.updateContacts();
                }
            });
        }

        @Override
        public void onInactiveContactsSettingChanged() {
            logger.debug("ContactSettingsListener: onInactiveContactsSettingChanged");
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ReceiversUpdateHandler.this.updateContacts();
                }
            });
        }

        @Override
        public void onNotificationSettingChanged(String uid) {
            logger.debug("ContactSettingsListener: onNotificationSettingChanged");
            // TODO
        }
    }

    @AnyThread
    private class SynchronizeContactsListener implements ch.threema.app.listeners.SynchronizeContactsListener {
        @Override
        public void onStarted(SynchronizeContactsRoutine startedRoutine) {
            logger.debug("Contact sync started");
        }

        @Override
        public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
            logger.debug("Contact sync finished, sending receivers update");
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ReceiversUpdateHandler.this.updateContacts();
                }
            });
        }

        @Override
        public void onError(SynchronizeContactsRoutine finishedRoutine) {
            logger.warn("Contact sync error, sending receivers update");
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ReceiversUpdateHandler.this.updateContacts();
                }
            });
        }
    }
}
