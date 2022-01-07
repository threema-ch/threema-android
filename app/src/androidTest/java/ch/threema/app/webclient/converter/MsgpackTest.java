/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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
import org.msgpack.core.MessagePacker;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

/**
 * Regression test for Android 4 bug:
 * https://github.com/msgpack/msgpack-java/issues/405
 * https://github.com/threema-ch/threema-web/issues/38
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MsgpackTest {
	@Test
	public void testPutString() throws Exception {
		String sampleText = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.";
		Map<String, String> sampleMap =  new HashMap<>();
		for(int i=0; i<100; i++) {
			sampleMap.put(String.valueOf(i), sampleText);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MessagePacker packer = MessagePack.newDefaultPacker(out);
		packer.packMapHeader(sampleMap.size());
		for (Map.Entry<String, String> entry : sampleMap.entrySet()) {
			packer.packString(entry.getKey());
			packer.packString(entry.getValue());
		}
	}
}
