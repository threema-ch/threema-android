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

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.app.ThreemaApplication
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class PreferencesStoreImplTest : KoinComponent {

    private var onChangedCalled = false
    private lateinit var store: PreferenceStore

    @BeforeTest
    fun setUp() {
        store = PreferenceStoreImpl(
            sharedPreferences = get(),
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
        assertNull(store.getString("foo"))

        store.save("foo", "Hello Wörld")

        assertEquals("Hello Wörld", store.getString("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreInt() {
        assertEquals(-1, store.getInt("foo", defaultValue = -1))

        store.save("foo", 123)

        assertEquals(123, store.getInt("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreBoolean() {
        assertEquals(false, store.getBoolean("foo"))

        store.save("foo", true)

        assertEquals(true, store.getBoolean("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreLong() {
        assertEquals(-1L, store.getLong("foo", defaultValue = -1L))

        store.save("foo", 123_000_000_000_000L)

        assertEquals(123_000_000_000_000L, store.getLong("foo"))
        assertTrue(onChangedCalled)
    }

    @Test
    fun saveAndRestoreFloat() {
        assertEquals(-1f, store.getFloat("foo", -1f))

        store.save("foo", 123.456f)

        assertEquals(123.456f, store.getFloat("foo", -1f))
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
        val jsonArray = JSONArray(arrayOf<Any>(1, true, "Hello"))

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
        assertNull(store.getString("foo"))
        assertEquals(0f, store.getFloat("foo"))
        assertEquals(0L, store.getLong("foo"))
        assertEquals(0, store.getInt("foo"))
        assertEquals(false, store.getBoolean("foo"))
        assertNull(store.getStringArray("foo"))
        assertEquals(emptyMap(), store.getMap("foo"))
        assertContentEquals(ByteArray(0), store.getBytes("foo"))
        assertEquals(JSONArray(), store.getJSONArray("foo"))
        assertNull(store.getJSONObject("foo"))
    }

    @Test
    fun restorePreviouslyStoredValue() {
        PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())
            .edit {
                putString("test", "Hello")
            }

        assertEquals("Hello", store.getString("test"))
    }

    @Test
    fun stringArrayValuesCannotContainSemicolon() {
        assertFailsWith<IllegalArgumentException> {
            store.save("foo", arrayOf("Hi", "Hello;World"))
        }
    }
}
