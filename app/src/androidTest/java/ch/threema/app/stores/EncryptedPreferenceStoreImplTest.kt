/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.stores

import ch.threema.app.ThreemaApplication
import ch.threema.localcrypto.MasterKeyImpl
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject

class EncryptedPreferenceStoreImplTest {

    private var onChangedCalled = false
    private lateinit var store: EncryptedPreferenceStore

    @BeforeTest
    fun setUp() {
        val masterKeyData = ByteArray(32) { it.toByte() }
        store = EncryptedPreferenceStoreImpl(
            context = ThreemaApplication.getAppContext(),
            masterKey = MasterKeyImpl(masterKeyData),
            onChanged = { _, _ -> onChangedCalled = true },
        )
        store.clear()
        onChangedCalled = false
    }

    @Test
    fun checkingForAndRemovingKeys() {
        assertFalse(store.containsKey("foo"))

        store.save("foo", "Hello Wörld")

        assertTrue(store.containsKey("foo"))

        store.remove("foo")

        assertFalse(store.containsKey("foo"))
    }

    @Test
    fun clearDeletesEverything() {
        assertFalse(store.containsKey("foo"))

        store.save("foo", "Hello Wörld")
        store.save("bar", arrayOf("a", "b", "c"))

        store.clear()

        assertFalse(store.containsKey("foo"))
        assertFalse(store.containsKey("bar"))
    }

    @Test
    fun saveAndRestoreString() {
        assertEquals("", store.getString("foo"))

        store.save("foo", "Hello Wörld")

        assertEquals("Hello Wörld", store.getString("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreByteArray() {
        val bytes = byteArrayOf(1, 2, 3)

        store.save("foo", bytes)

        assertContentEquals(bytes, store.getBytes("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreJsonArray() {
        val jsonArray = JSONArray(arrayOf(1, true, "Hello"))

        store.save("foo", jsonArray)

        val readJsonArray = store.getJSONArray("foo")

        assertEquals(3, readJsonArray.length())
        assertEquals(1, readJsonArray.getInt(0))
        assertEquals(true, readJsonArray.getBoolean(1))
        assertEquals("Hello", readJsonArray.getString(2))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreJsonObject() {
        val jsonObject = JSONObject(mapOf("a" to "Hello", "b" to 123))

        store.save("foo", jsonObject)

        val readJsonObject = store.getJSONObject("foo")

        assertNotNull(readJsonObject)
        assertEquals(setOf("a", "b"), readJsonObject.keys().asSequence().toSet())
        assertEquals("Hello", readJsonObject.getString("a"))
        assertEquals(123, readJsonObject.getInt("b"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreStringArray() {
        val strings = arrayOf("Hello", "World")

        store.save("foo", strings)

        assertContentEquals(strings, store.getStringArray("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreStringQuietlyArray() {
        val strings = arrayOf("Hello", "World")

        store.saveQuietly("foo", strings)

        assertContentEquals(strings, store.getStringArray("foo"))
        assertFalse(onChangedCalled)
    }

    @Test
    fun saveAndRestoreMap() {
        val map = mapOf("a" to "Hello", "b" to "World", "c" to null)

        store.save("foo", map)

        assertEquals(map, store.getMap("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun defaultValues() {
        assertEquals("", store.getString("foo"))
        assertNull(store.getStringArray("foo"))
        assertEquals(emptyMap(), store.getMap("foo"))
        assertContentEquals(ByteArray(0), store.getBytes("foo"))
        assertEquals(JSONArray(), store.getJSONArray("foo"))
        assertEquals(null, store.getJSONObject("foo"))
    }

    @Test
    fun restoringFromPreviouslyEncryptedFile() {
        File(ThreemaApplication.getAppContext().filesDir, ".crs-test")
            .writeBytes(
                byteArrayOf(
                    -116, 41, -38, -100, 96, 67, -28, -11, -118, -59, -33, -25,
                    58, -51, 27, -9, -84, -102, -29, 97, 97, 101, -124, 32, 111,
                    57, -54, -68, 37, 100, 119, 42,
                ),
            )

        assertEquals("Hello", store.getString("test"))
    }

    @Test
    fun storingNewValueReplacesThePrevious() {
        store.save("foo", "Hello Wörld! This is a long string, much longer than the second one.")
        store.save("foo", "Hi")

        assertEquals("Hi", store.getString("foo"))
    }

    @Test
    fun stringArrayValuesCannotContainSemicolon() {
        assertFailsWith<IllegalArgumentException> {
            store.save("foo", arrayOf("Hi", "Hello;World"))
        }
    }
}
