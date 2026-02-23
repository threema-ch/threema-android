package ch.threema.domain.protocol.csp;

import ch.threema.base.ThreemaException;

public class MessageTooLongException extends ThreemaException {
    public MessageTooLongException() {
        super("Message too long");
    }
}
