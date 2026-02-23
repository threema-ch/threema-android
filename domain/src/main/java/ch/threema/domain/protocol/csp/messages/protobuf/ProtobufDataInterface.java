package ch.threema.domain.protocol.csp.messages.protobuf;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

/**
 * Interface for protobuf payload data classes.
 * <p>
 * When implementing this interface, classes must have static {@code parse}-methods returning a new (Non-Null) object
 * of itself (type T) according to these signatures:
 * <ul>
 *   <li>{@code @NonNull T fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException}</li>
 *   <li>{@code @NonNull T fromProtobuf(@NonNull P protobufMessage) throws BadMessageException}</li>
 * </ul>
 *
 * @param <P> Protobuf Message Type
 */
public interface ProtobufDataInterface<P extends MessageLite> {
    @NonNull
    P toProtobufMessage();

    default byte[] toProtobufBytes() {
        return toProtobufMessage().toByteArray();
    }
}
