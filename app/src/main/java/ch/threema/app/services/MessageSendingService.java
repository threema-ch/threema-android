package ch.threema.app.services;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

/**
 * Handling methods for messages
 */
public interface MessageSendingService {
    interface MessageSendingServiceState {
        void processingFailed(AbstractMessageModel messageModel, MessageReceiver<AbstractMessageModel> receiver);

        void exception(Exception x, int tries);
    }

    interface MessageSendingProcess {
        MessageReceiver<AbstractMessageModel> getReceiver();

        AbstractMessageModel getMessageModel();

        boolean send() throws Exception;
    }

    void addToQueue(MessageSendingProcess process);

    void abort(String messageUid);
}
