/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.webclient.services.instance;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.WorkerThread;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;

@WorkerThread
abstract public class MessageHandler {
    protected final String subType;

    @AnyThread
    public MessageHandler(String subType) {
        this.subType = subType;
    }

    @AnyThread
    public final String getSubType() {
        return this.subType;
    }

    protected void send(MessageDispatcher dispatcher, MsgpackBuilder data, MsgpackBuilder args) {
        dispatcher.send(this.subType, data, args);
    }

    protected void send(MessageDispatcher dispatcher, List<MsgpackBuilder> data, MsgpackBuilder args) {
        dispatcher.send(this.subType, data, args);
    }

    protected void send(MessageDispatcher dispatcher, String data, MsgpackBuilder args) {
        dispatcher.send(this.subType, data, args);
    }

    protected void send(MessageDispatcher dispatcher, byte[] data, MsgpackBuilder args) {
        dispatcher.send(this.subType, data, args);
    }

    protected void sendConfirmActionSuccess(MessageDispatcher responseDispatcher, String temporaryId) {
        if (!Protocol.TYPE_RESPONSE.equals(responseDispatcher.type)) {
            throw new AssertionError("Cannot send a confirmAction message with a '"
                + responseDispatcher.type + "' dispatcher (must be '"
                + Protocol.TYPE_RESPONSE + "')");
        }

        final MsgpackBuilder args = new MsgpackObjectBuilder()
            .put(Protocol.ARGUMENT_SUCCESS, true)
            .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId);
        final MsgpackBuilder data = new MsgpackObjectBuilder();
        responseDispatcher.send(Protocol.SUB_TYPE_CONFIRM_ACTION, data, args);
    }

    protected void sendConfirmActionFailure(MessageDispatcher responseDispatcher, String temporaryId, String errorCode) {
        if (!Protocol.TYPE_RESPONSE.equals(responseDispatcher.type)) {
            throw new AssertionError("Cannot send a confirmAction message with a '"
                + responseDispatcher.type + "' dispatcher (must be '"
                + Protocol.TYPE_RESPONSE + "')");
        }

        final MsgpackBuilder args = new MsgpackObjectBuilder()
            .put(Protocol.ARGUMENT_SUCCESS, false)
            .put(Protocol.ARGUMENT_ERROR, errorCode)
            .put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId);
        final MsgpackBuilder data = new MsgpackObjectBuilder();
        responseDispatcher.send(Protocol.SUB_TYPE_CONFIRM_ACTION, data, args);
    }

    @AnyThread
    protected Utils.ModelWrapper getModel(Map<String, Value> args) throws MessagePackException, ConversionException {
        // Get receiver model
        String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
        String id = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();
        return Receiver.getModel(type, id);
    }

    @AnyThread
    protected MessageReceiver getReceiver(Map<String, Value> args) throws MessagePackException, ConversionException {
        // Get receiver instance
        String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
        String id = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();
        return Receiver.getReceiver(type, id);
    }

    /**
     * Extract data without list of required keys.
     */
    @AnyThread
    protected Map<String, Value> getData(Map<String, Value> message, boolean isOptional)
        throws MessagePackException {
        return this.getData(message, isOptional, null);
    }

    /**
     * Extract data from a message value.
     *
     * @param message    The message value map.
     * @param isOptional Set this to `false` to throw an exception if arguments are missing.
     */
    @AnyThread
    protected Map<String, Value> getData(Map<String, Value> message, boolean isOptional,
                                         @Nullable String[] requiredArguments)
        throws MessagePackException {
        return this.getMap(message, Protocol.FIELD_DATA, isOptional, requiredArguments);
    }

    /**
     * Extract arguments without list of required keys.
     */
    @AnyThread
    protected Map<String, Value> getArguments(Map<String, Value> message, boolean isOptional)
        throws MessagePackException {
        return this.getArguments(message, isOptional, null);
    }

    /**
     * Extract arguments from a message value.
     *
     * @param message           The message value map.
     * @param isOptional        Set this to `false` to throw an exception if arguments are missing.
     * @param requiredArguments Optional list of required arguments.
     */
    @AnyThread
    protected Map<String, Value> getArguments(Map<String, Value> message, boolean isOptional,
                                              @Nullable String[] requiredArguments)
        throws MessagePackException {
        return this.getMap(message, Protocol.FIELD_ARGUMENTS, isOptional, requiredArguments);
    }

    @AnyThread
    protected Map<String, Value> getMap(@NonNull Map<String, Value> message, @NonNull String fieldName,
                                        boolean isOptional, @Nullable String[] requiredFields)
        throws MessagePackException {
        // Get map field
        if (!message.containsKey(fieldName) && isOptional) {
            return null;
        } else if (!message.containsKey(fieldName)) {
            throw new MessagePackException("Field '" + fieldName + "' is not optional");
        }

        // Convert to a map
        final Value argumentsValue = message.get(fieldName);
        final Map<String, Value> map = new HashMap<>();
        if (!argumentsValue.isMapValue()) {
            throw new MessagePackException("Field '" + fieldName + "' must be a map");
        }
        for (Map.Entry<Value, Value> entry : argumentsValue.asMapValue().entrySet()) {
            map.put(entry.getKey().asStringValue().asString(), entry.getValue());
        }

        // Check required fields
        if (requiredFields != null && requiredFields.length > 0) {
            for (String requiredKey : requiredFields) {
                if (!map.containsKey(requiredKey)) {
                    throw new MessagePackException(
                        "Required " + fieldName + " field " + requiredKey + " not found");
                }
            }
        }

        return map;
    }

    /**
     * Get a string value, if the value is null a blank string will be returned
     *
     * @param value
     * @return
     */
    @AnyThread
    protected String getValueString(Value value) {
        return this.getValueString(value, "");
    }

    @AnyThread
    protected String getValueString(Value value, String defaultValue) {
        if (value == null || value.isNilValue()) {
            return defaultValue;
        }

        return value.asStringValue().toString();
    }

}
