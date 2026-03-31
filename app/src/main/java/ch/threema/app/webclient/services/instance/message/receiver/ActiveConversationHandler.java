package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.GroupService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.group.GroupModelOld;

@WorkerThread
public class ActiveConversationHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ActiveConversationHandler");
    private final ConversationService conversationService;
    private final ConversationTagService conversationTagService;
    private final ContactService contactService;
    private final GroupService groupService;

    @AnyThread
    public ActiveConversationHandler(ContactService contactService,
                                     GroupService groupService,
                                     ConversationService conversationService,
                                     ConversationTagService conversationTagService) {
        super(Protocol.SUB_TYPE_ACTIVE_CONVERSATION);
        this.contactService = contactService;
        this.groupService = groupService;
        this.conversationService = conversationService;
        this.conversationTagService = conversationTagService;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received active conversation update");

        // Get args
        final Map<String, Value> args = this.getArguments(message, false, new String[]{
            Protocol.ARGUMENT_RECEIVER_TYPE,
            Protocol.ARGUMENT_RECEIVER_ID,
        });
        final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
        final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

        // get MessageReceiver from message
        ch.threema.app.messagereceiver.MessageReceiver messageReceiver = null;
        switch (type) {
            case Receiver.Type.CONTACT:
                ContactModel contactModel = contactService.getByIdentity(receiverId);
                if (contactModel != null) {
                    messageReceiver = contactService.createReceiver(contactModel);
                }
                break;
            case Receiver.Type.GROUP:
                GroupModelOld groupModel = groupService.getById(Integer.valueOf(receiverId));
                if (groupModel != null) {
                    messageReceiver = groupService.createReceiver(groupModel);
                }
                break;
        }

        // get ConversationModel from MessageReceiver
        if (messageReceiver != null) {
            final ConversationModel conversationModel = this.conversationService.refresh(messageReceiver);
            if (conversationModel != null) {
                conversationTagService.removeTagAndNotify(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL);
            }
        }
    }

    @Override
    protected boolean maybeNeedsConnection() {
        // We don't need to send or receive Threema messages in reaction to this webclient message
        return false;
    }
}
