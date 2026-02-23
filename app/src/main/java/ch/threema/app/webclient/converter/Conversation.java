package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTag;

@AnyThread
public class Conversation extends Converter {
    public final static String POSITION = "position";
    public final static String MESSAGE_COUNT = "messageCount";
    public final static String UNREAD_COUNT = "unreadCount";
    public final static String LATEST_MESSAGE = "latestMessage";
    public final static String NOTIFICATIONS = "notifications";
    public final static String IS_STARRED = "isStarred";
    public final static String IS_UNREAD = "isUnread";

    public interface Append {
        void append(MsgpackObjectBuilder builder, ConversationModel conversation, Utils.ModelWrapper modelWrapper);
    }

    /**
     * Converts multiple conversations to MsgpackObjectBuilder instances.
     */
    public static List<MsgpackBuilder> convert(List<ConversationModel> conversations, Append append) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (ConversationModel conversation : conversations) {
            list.add(convert(conversation, append));
        }
        return list;
    }

    /**
     * Converts a conversation to a MsgpackObjectBuilder.
     */
    public static MsgpackBuilder convert(ConversationModel conversation) throws ConversionException {
        return convert(conversation, null);
    }

    /**
     * Converts a conversation to a MsgpackObjectBuilder.
     */
    public static MsgpackBuilder convert(ConversationModel conversation, Append append) throws ConversionException {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        final ServiceManager serviceManager = getServiceManager();
        if (serviceManager == null) {
            throw new ConversionException("Service manager is null");
        }
        try {
            final Utils.ModelWrapper model = Utils.ModelWrapper.getModel(conversation);
            builder.put(Receiver.TYPE, model.getType());
            builder.put(Receiver.ID, model.getId());
            builder.put(POSITION, conversation.getPosition());
            builder.put(MESSAGE_COUNT, conversation.messageCount);
            builder.put(UNREAD_COUNT, conversation.getUnreadCount());
            maybePutLatestMessage(builder, LATEST_MESSAGE, conversation);

            builder.put(NOTIFICATIONS, NotificationSettings.convert(conversation));

            final boolean isStarred = serviceManager.getConversationTagService()
                .isTaggedWith(conversation, ConversationTag.PINNED);
            if (isStarred) {
                builder.put(IS_STARRED, isStarred);
            }

            final boolean isUnread = serviceManager.getConversationTagService()
                .isTaggedWith(conversation, ConversationTag.MARKED_AS_UNREAD);
            builder.put(IS_UNREAD, isUnread);

            if (append != null) {
                append.append(builder, conversation, model);
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    private static void maybePutLatestMessage(
        MsgpackObjectBuilder builder,
        String field,
        ConversationModel conversation
    ) throws ConversionException {
        AbstractMessageModel message = conversation.latestMessage;
        if (message != null) {
            builder.put(field, Message.convert(message, conversation.messageReceiver, false, Message.DETAILS_NO_QUOTE));
        }
    }
}
