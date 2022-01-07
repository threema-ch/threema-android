/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

//THANKS TO http://stackoverflow.com/questions/122105/java-what-is-the-best-way-to-filter-a-collections

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Functional functionality (map / filter / select etc)
 */
public class Functional {

	/**
	 * Filter a collection using the predicate.
	 * Null values are always retained and not passed to the predicate.
	 */
	public static <T> Collection<T> filter(Collection<T> target, IPredicateNonNull<T> predicate) {
		Collection<T> result = new ArrayList<>();
		for (T element : target) {
			if (element == null || predicate.apply(element)) {
				result.add(element);
			}
		}
		return result;
	}

	/**
	 * Filter a collection using the predicate.
	 */
	public static <T> Collection<T> filter(Collection<T> target, IPredicate<T> predicate) {
		Collection<T> result = new ArrayList<>();
		for (T element : target) {
			if (predicate.apply(element)) {
				result.add(element);
			}
		}
		return result;
	}

	/**
	 * Filter a collection using the predicate.
	 * Null values are always retained and not passed to the predicate.
	 */
	public static <T> List<T> filter(List<T> target, IPredicateNonNull<T> predicate) {
		return (List<T>) Functional.filter((Collection<T>) target, predicate);
	}

	/**
	 * Filter a collection using the predicate.
	 */
	public static <T> List<T> filter(List<T> target, IPredicate<T> predicate) {
		return (List<T>) Functional.filter((Collection<T>) target, predicate);
	}

	public static <T> T select(Collection<T> target, IPredicateNonNull<T> predicate) {
		T result = null;
		for (T element : target) {
			if (element == null || !predicate.apply(element))
				continue;
			result = element;
			break;
		}
		return result;
	}

	public static <K, T> T select(Map<K, T> target, IPredicateNonNull<T> predicate) {
		for(Map.Entry<K, T> cursor: target.entrySet()) {
			if(cursor.getValue() != null && predicate.apply(cursor.getValue())) {
				return cursor.getValue();
			}
		}
		return null;
	}

	public static <T> T select(SparseArray<T> target, IPredicateNonNull<T> predicate) {
		for(int n = 0; n < target.size(); n++) {
			int key = target.keyAt(n);
			T object = target.get(key);
			if(object != null && predicate.apply(object)) {
				return object;
			}
		}
		return null;
	}

	public static <T> T select(Collection<T> target, IPredicateNonNull<T> predicate, T defaultValue) {
		T result = defaultValue;
		for (T element : target) {
			if (element == null || !predicate.apply(element))
				continue;
			result = element;
			break;
		}
		return result;
	}

	public static <T> T select(T[] target, IPredicateNonNull<T> predicate, T defaultValue) {
		T result = defaultValue;
		for (T element : target) {
			if (element == null || !predicate.apply(element))
				continue;
			result = element;
			break;
		}
		return result;
	}

	/**
	 * Apply a mapping function to all elements of a collection.
	 * Return a new collection with those elements.
	 */
	public static <T, U> Collection<U> map(Collection<T> target, IMap<T, U> mapping) {
		Collection<U> result = new ArrayList<>();
		for (T element : target) {
			result.add(mapping.apply(element));
		}
		return result;
	}

	/**
	 * Apply a mapping function to all elements of a list.
	 * Return a new list with those elements.
	 */
	public static <T, U> List<U> map(List<T> target, IMap<T, U> mapping) {
		return (List<U>) Functional.map((Collection<T>) target, mapping);
	}
}
