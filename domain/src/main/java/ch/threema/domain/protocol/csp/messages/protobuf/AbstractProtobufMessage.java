package ch.threema.domain.protocol.csp.messages.protobuf;

import androidx.annotation.NonNull;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;

public abstract class AbstractProtobufMessage<D extends ProtobufDataInterface<?>> extends AbstractMessage {

    protected final D data;
    private final int type;

    /**
     * @param type         Protocol type of the message as defined in {@link ch.threema.domain.protocol.csp.ProtocolDefines}
     * @param protobufData Parsed protobuf data
     */
    public AbstractProtobufMessage(int type, @NonNull D protobufData) {
        this.type = type;
        this.data = protobufData;
    }

    public D getData() {
        return data;
    }

    @Override
    public @NonNull byte[] getBody() {
        return this.data.toProtobufBytes();
    }

    @Override
    public int getType() {
        return this.type;
    }
}
