/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.utils;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.Date;

public class TestUtil {
	@Deprecated
	public static boolean required(Object o) {
		return o != null;
	}

	public static boolean required(Object... o) {
		for(Object x: o) {
			if(!required(x)) {
				return false;
			}
		}
		return true;
	}

	public static boolean requireOne(Object... o) {
		for(Object x: o) {
			if(x != null) {
				return true;
			}
		}
		return false;
	}

	public static boolean compare(Object[] a, Object[] b) {
		if(a == null) {
			return b == null;
		}

		if(b == null) {
			return a == null;
		}

		//not the same length
		if(a.length != b.length) {
			return false;
		}

		for(int n = 0; n < a.length; n++) {
			if(b.length < n) {
				return false;
			}

			if(!compare(a[n], b[n])) {
				return false;
			}
		}

		return true;
	}

	public static boolean compare(Object a, Object b) {
		if(a == null) {
			return b == null;
		}

		if(b == null) {
			return a == null;
		}

		if(a instanceof byte[]) {
			return compare((byte[])a, (byte[])b);
		}

		return a == null ? b == null : a.equals(b);
	}

	public static boolean compare(byte[] a, byte[] b) {
		return a == null ? b == null : Arrays.equals(a, b);
	}

	public static boolean compare(int a, int b) {
		return a == b;
	}

	public static boolean compare(float a, float b) {
		return a == b;
	}

	public static boolean compare(double a, double b) {
		return a == b;
	}

	public static boolean compare(Date a, Date b) {
		return a == null ? b == null :
				a.compareTo(b) == 0;
	}

	public static boolean empty(String string) {
		return string == null || string.length() == 0;
	}

	public static boolean empty(String... string) {
		for(String s: string) {
			if(!empty(s)) {
				return false;
			}
		}
		return true;
	}
	public static boolean contains(String search, String string) {
		return contains(search, string, false);
	}

	public static boolean contains(String search, String string, boolean caseSensitive) {
		return string != null
				&& search != null
				&& (!caseSensitive ?
					string.toLowerCase().contains(search.toLowerCase()) :
					string.contains(search));
	}

	public static boolean empty(CharSequence charSequence) {
		if (!TextUtils.isEmpty(charSequence)) {
			String messageString = charSequence.toString();
			return (messageString.trim().length() == 0);
		}
		return true;
	}
}
