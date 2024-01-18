/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.base.utils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Utils {

	private static final char[] HEX_LOOKUP_TABLE = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};

	private Utils() {}

	public static byte[] hexStringToByteArray(@NonNull String s) {
		int len = s.length();

		if (len % 2 != 0) {
			// not a valid hex string
			return new byte[0];
		}

		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	/**
	 *
	 * @return String with lowercase hex characters. Nullability is the same as with {@param bytes}.
	 */
	public static String byteArrayToHexString(byte[] bytes) {
		if(bytes != null) {
			char[] hexChars = new char[bytes.length * 2];
			int v;
			for (int j = 0; j < bytes.length; j++) {
				v = bytes[j] & 0xFF;
				hexChars[j * 2] = HEX_LOOKUP_TABLE[v >>> 4];
				hexChars[j * 2 + 1] = HEX_LOOKUP_TABLE[v & 0x0F];
			}
			return new String(hexChars);
		}
		return null;
	}

	@NonNull
	public static String byteArrayToSeparatedHexString(@NonNull byte[] bytes, char separator) {
		int stringLength = bytes.length * 3;
		char[] chars = new char[stringLength > 0 ? stringLength - 1 : stringLength];
		for (int byteOffset = 0, charOffset = 0; byteOffset < bytes.length; ++byteOffset) {
			int value = bytes[byteOffset] & 0xFF;
			chars[charOffset++] = HEX_LOOKUP_TABLE[value >>> 4];
			chars[charOffset++] = HEX_LOOKUP_TABLE[value & 0x0F];
			if (byteOffset < bytes.length - 1) {
				chars[charOffset++] = separator;
			}
		}
		return new String(chars);
	}

	public static String byteArrayToSha256HexString(byte[] bytes) throws NoSuchAlgorithmException {
		MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
		messageDigest.update(bytes);
		byte[] sha256bytes = messageDigest.digest();
		return byteArrayToHexString(sha256bytes);
	}

	public static @NonNull byte[] intToByteArrayBigEndian(int value) {
		return new byte[] {
				(byte)(value >>> 24),
				(byte)(value >>> 16),
				(byte)(value >>> 8),
				(byte)value
		};
	}

	public static int byteArrayToIntBigEndian(@NonNull byte[] bytes) {
		 return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}

	public static @NonNull byte[] longToByteArrayBigEndian(long value) {
		byte[] result = new byte[8];
		for (int i = 7; i >= 0; i--) {
			result[i] = (byte)(value & 0xFF);
			value >>= 8;
		}
		return result;
	}

	public static long byteArrayToLongBigEndian(final @NonNull byte[] bytes) {
		long result = 0;
		for (int i = 0; i < 8; i++) {
			result <<= 8;
			result |= (bytes[i] & 0xFF);
		}
		return result;
	}


	private static final String HEX_UPPER = "0123456789ABCDEF";
	private static final String HEX_LOWER = "0123456789abcdef";

	/**
	 * A fast conversion from a byte to a hex string.
	 * Roughly 15-20 times faster than String.format.
	 *
	 * @param b The byte to convert.
	 * @param uppercase Whether the hex alphabet should be uppercase.
	 * @param prefix Whether to prefix the output with "0x".
	 */
	@NonNull
	public static String byteToHex(byte b, boolean uppercase, boolean prefix) {
		final StringBuilder hex = new StringBuilder(prefix ? 4 : 2);
		final String lookup = uppercase ? HEX_UPPER : HEX_LOWER;
		if (prefix) {
			hex.append("0x");
		}
		hex.append(lookup.charAt((b & 0xF0) >> 4));
		hex.append(lookup.charAt(b & 0x0F));
		return hex.toString();
	}

	/**
	 * Start with a string that is the same length in characters as the desired maximum number of
	 * encoded bytes, then keep removing characters at the end until the encoded length is less than
	 * or equal to the desired maximum length. This avoids producing invalid UTF-8 encoded strings
	 * which are possible if the encoded byte array is truncated, potentially in the middle of
	 * an encoded multi-byte character.
	 *
	 * @param str
	 * @param maxLen
	 * @return byte array
	 * @throws UnsupportedEncodingException
	 */
	public static byte[] truncateUTF8StringToByteArray(String str, int maxLen) throws UnsupportedEncodingException {
		if(str == null || maxLen <= 0) {
			return new byte[0];
		}
		String curStr = str.substring(0, Math.min(str.length(), maxLen));
		byte[] encoded = curStr.getBytes(StandardCharsets.UTF_8);
		while (encoded.length > maxLen) {
			curStr = curStr.substring(0, curStr.length()-1);
			encoded = curStr.getBytes(StandardCharsets.UTF_8);
		}
		return encoded;
	}

	/**
	 * Start with a string that is the same length in characters as the desired maximum number of
	 * encoded bytes, then keep removing characters at the end until the encoded length is less than
	 * or equal to the desired maximum length. This avoids producing invalid UTF-8 encoded strings
	 * which are possible if the encoded byte array is truncated, potentially in the middle of
	 * an encoded multi-byte character.
	 *
	 * @param str
	 * @param maxLen
	 * @return truncated string
	 * @throws UnsupportedEncodingException
	 */
	public static @Nullable String truncateUTF8String(@Nullable String str, int maxLen) {
		if (str == null || str.length() == 0) {
			return null;
		}
		try {
			byte[] r = truncateUTF8StringToByteArray(str, maxLen);
			return new String(r);
		} catch (UnsupportedEncodingException e) {
			return str.substring(0, maxLen).trim();
		}
	}

	public static @Nullable String removeLeadingCharacters(@NonNull String str, int maxLeading) {
		List<Integer> codePoints = stringToCodePoints(str);
		StringBuilder result = new StringBuilder();

		int size = codePoints.size();
		int startIndex = size <= maxLeading ? 0 : size - maxLeading;

		for (int i = startIndex; i < size; i++) {
			result.appendCodePoint(codePoints.get(i));
		}

		return result.toString();
	}

	private static List<Integer> stringToCodePoints(@NonNull String in) {
		List<Integer> out = new ArrayList<>();
		final int length = in.length();
		for (int offset = 0; offset < length; ) {
			final int codepoint = in.codePointAt(offset);
			out.add(codepoint);
			offset += Character.charCount(codepoint);
		}
		return out;
	}


	public static boolean isAnyObjectNull(@Nullable Object... objects) {
		for (Object o: objects) {
			if (o == null) {
				return true;
			}
		}
		return false;
	}

	public static byte[] concatByteArrays(byte[] a, byte[] b) {
		return ByteBuffer.allocate(a.length + b.length)
			.put(a)
			.put(b)
			.array();
	}

	public static byte[] concatByteArrays(byte[] a, byte[] b, byte[] c, byte[] d) {
		return ByteBuffer.allocate(a.length + b.length + c.length + d.length)
			.put(a)
			.put(b)
			.put(c)
			.put(d)
			.array();
	}

	/**
	 * Get a timestamp for a date that is guaranteed to be non-negative.
	 * If the provided date is `null` or has a negative timestamp, zero will be returned.
	 *
	 * @param date The date to get the timestamp from
	 * @return If the date is `null` or has a negative timestamp 0, the timestamp otherwise
	 */
	public static long getUnsignedTimestamp(@Nullable Date date) {
		Long time = date == null ? null : date.getTime();
		return time == null || time < 0 ? 0 : time;
	}
}
