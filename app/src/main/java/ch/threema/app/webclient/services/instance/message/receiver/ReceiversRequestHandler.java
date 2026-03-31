package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Webclient is requesting list of receivers.
 */
@WorkerThread
public class ReceiversRequestHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ReceiversRequestHandler");

    private final MessageDispatcher dispatcher;
    private final ContactService contactService;
    private final GroupService groupService;
    private final DistributionListService distributionListService;
    private final PreferenceService preferenceService;

    private Listener listener;

    @WorkerThread
    public interface Listener {
        void onReceived();

        void onAnswered();
    }

    @AnyThread
    public ReceiversRequestHandler(
        @NonNull MessageDispatcher dispatcher,
        @NonNull ContactService contactService,
        @NonNull GroupService groupService,
        @NonNull DistributionListService distributionListService,
        @NonNull PreferenceService preferenceService,
        @Nullable Listener listener
    ) {
        super(Protocol.SUB_TYPE_RECEIVERS);
        this.dispatcher = dispatcher;
        this.contactService = contactService;
        this.groupService = groupService;
        this.distributionListService = distributionListService;
        this.preferenceService = preferenceService;
        this.listener = listener;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received receivers request");
        this.respond();
    }

    private void respond() {
        try {
            final MsgpackObjectBuilder data = Receiver.convert(
                this.contactService.find(Contact.getContactFilter()),
                this.groupService.getAll(Group.getGroupFilter()),
                this.distributionListService.getAll(),
                this.preferenceService.getContactNameFormat()
            );

            // Notify listeners
            this.listener.onReceived();

            // Send response
            logger.debug("Sending receivers response");
            final MsgpackObjectBuilder args = new MsgpackObjectBuilder();
            this.send(this.dispatcher, data, args);

            // Notify listeners
            if (this.listener != null) {
                this.listener.onAnswered();
            }
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
