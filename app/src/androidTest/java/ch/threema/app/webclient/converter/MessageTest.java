/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.webclient.converter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static org.junit.Assert.assertEquals;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MessageTest {
    @Test
    public void testFixFileName() {
        // Fix file extension if missing
        assertEquals("file.jpg", Message.fixFileName("file", "image/jpeg"));
        assertEquals("file.png", Message.fixFileName("file", "image/png"));
        assertEquals("file.txt", Message.fixFileName("file", "text/plain"));

        // Ignore files containing a dot
        assertEquals("file.something", Message.fixFileName("file.something", "text/plain"));

        // Don't change existing extensions
        assertEquals("file.txt", Message.fixFileName("file.txt", "image/jpeg"));
    }

    private static String testMaybePutFileImpl(
        @NonNull String inputMimeType,
        @Nullable Date createdAt,
        @Nullable String messageId
    ) throws IOException {
        // The Threema protocol does not require a file name in a file message,
        // but ARP does!
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        final FileDataModel fileDataModel = new FileDataModel(
            inputMimeType,
            "image/jpeg",
            100,
            null,
            FileData.RENDERING_MEDIA,
            "A photo without name",
            true,
            new HashMap<>()
        );
        final AbstractMessageModel messageModel = new MessageModel();
        messageModel.setFileDataModel(fileDataModel);
        messageModel.setCreatedAt(createdAt);
        messageModel.setApiMessageId(messageId);
        Message.maybePutFile(builder, "file", messageModel, fileDataModel);

        // Create Msgpack message
        final ByteBuffer buf = builder.consume();

        // Decode message again
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(buf)) {
            final Map<String, Value> map = new HashMap<>();
            for (Map.Entry<Value, Value> entry : unpacker.unpackValue().asMapValue().map().entrySet()) {
                map.put(entry.getKey().asStringValue().toString(), entry.getValue());
            }
            final Map<String, Value> fileData = new HashMap<>();
            for (Map.Entry<Value, Value> entry : map.get("file").asMapValue().map().entrySet()) {
                fileData.put(entry.getKey().asStringValue().toString(), entry.getValue());
            }
            return fileData.get("name").asStringValue().asString();
        }
    }

    @Test
    public void testMaybePutFile() throws IOException {
        assertEquals("threema-20201212-000000-null.png", testMaybePutFileImpl("image/png", new Date(2020 - 1900, 12 - 1, 12), null));
        assertEquals("threema-20100130-131400-msgidasdf.txt", testMaybePutFileImpl("text/plain", new Date(2010 - 1900, 1 - 1, 30, 13, 14), "msgidasdf"));
    }
}
