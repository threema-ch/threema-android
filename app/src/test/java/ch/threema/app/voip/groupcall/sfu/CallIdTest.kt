/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.storage.models.GroupModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

internal class CallIdTest {
    /**
     * ## Group Call ID Derivation

    For group calls scoped to groups, the Group Call ID is derived by running
    BLAKE2b on specific data provided by the `GroupCallStart`:

    group-call-id = BLAKE2b(
        out-length=32,
        input=
            group-creator-identity
            || group-id
            || u8(GroupCallStart.protocol_version)
            || GroupCallStart.gck
            || utf8-encode(GroupCallStart.base_url),
    )
     */
    @Test
    fun testCallIdCreation() {
        val callId = createCallId()

        /*
        The resulting id should be the blake2b hash of
        [
            0x41, 0x42, 0x43, 0x44, 0x31, 0x32, 0x33, 0x34, // group creator identity
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, // group id
            0x01, // u8(GroupCallStart.protocol_version)
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x00, 0x00, 0x00, 0x00, // gck
            0x61, 0x62, 0x63, 0x64, 0x65, 0x66 // utf8-encode(GroupCallStart.base_url)
        ]
         */
        // calculated with blake2b in node.js (https://github.com/emilbayes/blake2b)
        // see example implementation below
        val expected = byteArrayOf(
            0x19.toByte(), 0x8B.toByte(), 0xB2.toByte(), 0x1C.toByte(),
            0x12.toByte(), 0x54.toByte(), 0x11.toByte(), 0x6A.toByte(),
            0x5B.toByte(), 0x42.toByte(), 0x0E.toByte(), 0x47.toByte(),
            0x44.toByte(), 0xAD.toByte(), 0x56.toByte(), 0x81.toByte(),
            0xA9.toByte(), 0x55.toByte(), 0xFB.toByte(), 0xBF.toByte(),
            0xBA.toByte(), 0xB2.toByte(), 0xCD.toByte(), 0xC2.toByte(),
            0xA8.toByte(), 0x3F.toByte(), 0xBF.toByte(), 0x17.toByte(),
            0x51.toByte(), 0xEB.toByte(), 0x8A.toByte(), 0xE0.toByte()
        )
        assertTrue(expected.contentEquals(callId.bytes))
    }
}

/*
Example implementation in Javascript to calculate the expected values with blake2b (`npm install blake2b`)
```js
const blake2b = require('blake2b')

const salt = Buffer.of(0x69, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // 'i' (filled up with zeroes)
const personal = Buffer.of(0x33, 0x6d, 0x61, 0x2d, 0x63, 0x61, 0x6c, 0x6c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // '3ma-call' (filled up with zeroes)
const creatorIdentity = Buffer.of(0x41, 0x42, 0x43, 0x44, 0x31, 0x32, 0x33, 0x34); // group creator identity
const groupId = Buffer.of(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08); // group id
const protocol = Buffer.of(0x01); // u8(GroupCallStart.protocol_version)
const gck = Buffer.of(
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00
);
const baseUrl = Buffer.of(0x61, 0x62, 0x63, 0x64, 0x65, 0x66); // utf8-encode(GroupCallStart.base_url)

const output = new Uint8Array(32)

const digest = blake2b(output.length, null, salt, personal)
    .update(creatorIdentity)
    .update(groupId)
    .update(protocol)
    .update(gck)
    .update(baseUrl)
    .digest()

const hexStrings = [...digest]
    .map(value => `0x${value.toString(16).toUpperCase()}.toByte()`);

console.log(hexStrings.join(', '))
```
 */

private const val GROUP_CREATOR_IDENTITY = "ABCD1234" // [ 0x41, 0x42, 0x43, 0x44, 0x31, 0x32, 0x33, 0x34 ]
private val GROUP_ID = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
val GCK = byteArrayOf(
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00
)
private const val PROTOCOL_VERSION = 1
private const val BASE_URL = "abcdef" // [ 0x61, 0x62, 0x63, 0x64, 0x65, 0x66 ]

private fun createCallId(): CallId {
    val groupId = mock(GroupId::class.java)
    `when`(groupId.groupId).thenReturn(GROUP_ID)

    val group = mock(GroupModel::class.java)
    `when`(group.apiGroupId).thenReturn(groupId)
    `when`(group.creatorIdentity).thenReturn(GROUP_CREATOR_IDENTITY)

    val callStartData = GroupCallStartData(PROTOCOL_VERSION.toUInt(), GCK, BASE_URL)
    return CallId.create(group, callStartData)
}
