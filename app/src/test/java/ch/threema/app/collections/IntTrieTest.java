/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.collections;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class IntTrieTest {

	@Test
	public void insert() throws Exception {
		final IntTrie<String> trie = new IntTrie<>();
		trie.insert(new int[] { 1, 2, 3 }, "Yes");

		assertTrue(trie.contains(new int[] { 1, 2, 3 }));
		assertEquals("Yes", trie.get(new int[] { 1, 2, 3 }).getValue());
	}

	@Test
	public void containsAndGet() throws Exception {
		final IntTrie<String> trie = new IntTrie<>();
		trie.insert(new int[] { 1, 2, 3 }, "Hello");

		// Contains the inserted value
		assertTrue(trie.contains(new int[] { 1, 2, 3 }));
		assertEquals("Hello", trie.get(new int[] { 1, 2, 3 }).getValue());

		// Does not contain longer values
		assertFalse(trie.contains(new int[] { 1, 2, 3, 4 }));
		assertEquals(null, trie.get(new int[] { 1, 2, 3, 4 }));

		// Does not contain shorter values
		assertFalse(trie.contains(new int[] { 1, 2 }));

		// Does not contain other values
		assertFalse(trie.contains(new int[] { 1, 3, 4 }));

		// Now we'll insert that other value
		trie.insert(new int[] { 1, 3, 4 }, "Goodbye");
		assertTrue(trie.contains(new int[] { 1, 3, 4 }));
		assertEquals("Goodbye", trie.get(new int[] { 1, 3, 4 }).getValue());

		// It should still contain the first inserted value
		assertTrue(trie.contains(new int[] { 1, 2, 3 }));

		// Now we'll insert a prefix of the first value
		trie.insert(new int[] { 1, 2 }, "Foo");
		assertFalse(trie.contains(new int[] { 1 }));
		assertTrue(trie.contains(new int[] { 1, 2 }));
		assertTrue(trie.contains(new int[] { 1, 2, 3 }));
	}

	@Test
	public void containsEmpty() throws Exception {
		final IntTrie<Integer> trie = new IntTrie<>();
		assertFalse(trie.contains(new int[] { 1 }));
		assertFalse(trie.contains(new int[] { }));
		trie.insert(new int[] {}, 12345);
		assertFalse(trie.contains(new int[] { 1 }));
		assertFalse(trie.contains(new int[] { }));
	}

	@Test
	public void isLeaf() throws Exception {
		final IntTrie<String> trie = new IntTrie<>();

		trie.insert(new int[] { 1, 2, 3 }, "A");
		assertTrue(trie.get(new int[] { 1, 2, 3 }).isLeaf());

		trie.insert(new int[] { 1, 2 }, "B");
		assertTrue(trie.get(new int[] { 1, 2, 3 }).isLeaf());

		trie.insert(new int[] { 1, 2, 3, 4, 5 }, "C");
		assertFalse(trie.get(new int[] { 1, 2, 3 }).isLeaf());
	}

	@Test
	public void containsListPath() throws Exception {
		final IntTrie<String> trie = new IntTrie<>();
		trie.insert(new int[] { 1, 2, 3 }, "Hello");

		// Contains the inserted value
		final List<Integer> list = new ArrayList<>();
		list.add(1);
		list.add(2);
		list.add(3);
		assertTrue(trie.contains(list));
		assertEquals("Hello", trie.get(list).getValue());
	}

	@Test
	public void getNullVsGetEmptyValue() {
		final IntTrie<String> trie = new IntTrie<>();
		trie.insert(new int[] { 1, 2, 3 }, "Bonjour");

		// An element that does not exist returns null
		assertNull(trie.get(new int[] { 2, 3 }));

		// If the path is valid but doesn't lead to a valid node, an empty Value is returned.
		final IntTrie.Value value = trie.get(new int[] { 1, 2 });
		assertNotNull(value);
		assertNull(value.getValue());
		assertFalse(value.isLeaf());
	}
}
