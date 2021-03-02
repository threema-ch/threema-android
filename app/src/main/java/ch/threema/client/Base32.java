/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

/* (PD) 2001 The Bitzi Corporation
 * Please see http://bitzi.com/publicdomain for more info.
 *
 * Base32.java
 *
 * As modified by Jacob Davies:
 *
 * Copyright © 2010, Data Base Architects, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the names of Kalinda Software, DBA Software, Data Base Architects,
 *       Itemscript nor the names
 *       of its contributors may be used to endorse or promote products derived from this
 *       software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

/**
 * Base32 encoding/decoding class.
 *
 * @author Robert Kaye & Gordon Mohr
 */
public final class Base32 {
	/* lookup table used to encode() groups of 5 bits of data */
	private static final String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	/* lookup table used to decode() characters in Base32 strings */
	private static final byte[] base32Lookup = { 26, 27, 28, 29, 30, 31, -1,
			-1, -1, -1, -1, -1, -1, -1, // 23456789:;<=>?
			-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, // @ABCDEFGHIJKLMNO
			15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, // PQRSTUVWXYZ[\]^_
			-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, // `abcdefghijklmno
			15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25 // pqrstuvwxyz
	};
	/* Messsages for Illegal Parameter Exceptions in decode() */
	private static final String errorCanonicalLength = "non canonical Base32 string length";
	private static final String errorCanonicalEnd = "non canonical bits at end of Base32 string";
	private static final String errorInvalidChar = "invalid character in Base32 string";

	/**
	 * Decode a Base32 string into an array of binary bytes. May fail if the
	 * parameter is a non canonical Base32 string (the only other possible
	 * exception is that the returned array cannot be allocated in memory)
	 */
	static public byte[] decode(final String base32)
			throws IllegalArgumentException {
		// Note that the code below detects could detect non canonical
		// Base32 length within the loop. However canonical Base32 length
		// can be tested before entering the loop.
		// A canonical Base32 length modulo 8 cannot be:
		// 1 (aborts discarding 5 bits at STEP n=0 which produces no byte),
		// 3 (aborts discarding 7 bits at STEP n=2 which produces no byte),
		// 6 (aborts discarding 6 bits at STEP n=1 which produces no byte)
		// So these tests could be avoided within the loop.
		switch (base32.length() % 8) { // test the length of last subblock
			case 1: // 5 bits in subblock: 0 useful bits but 5 discarded
			case 3: // 15 bits in subblock: 8 useful bits but 7 discarded
			case 6: // 30 bits in subblock: 24 useful bits but 6 discarded
				throw new IllegalArgumentException(errorCanonicalLength);
		}
		byte[] bytes = new byte[base32.length() * 5 / 8];
		int offset = 0, i = 0, lookup;
		byte nextByte, digit;
		// Also the code below does test that other discarded bits
		// (1 to 4 bits at end) are effectively 0.
		while (i < base32.length()) {
			// Read the 1st char in a 8-chars subblock
			// check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 0: leave 5 bits
			nextByte = (byte) (digit << 3);
			// Assert(i < base32.length) // tested before loop
			// Read the 2nd char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 5: insert 3 bits, leave 2 bits
			bytes[offset++] = (byte) (nextByte | (digit >> 2));
			nextByte = (byte) ((digit & 3) << 6);
			if (i >= base32.length()) {
				if (nextByte != (byte) 0) {
					throw new IllegalArgumentException(errorCanonicalEnd);
				}
				break; // discard the remaining 2 bits
			}
			// Read the 3rd char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 2: leave 7 bits
			nextByte |= (byte) (digit << 1);
			// Assert(i < base32.length) // tested before loop
			// Read the 4th char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 7: insert 1 bit, leave 4 bits
			bytes[offset++] = (byte) (nextByte | (digit >> 4));
			nextByte = (byte) ((digit & 15) << 4);
			if (i >= base32.length()) {
				if (nextByte != (byte) 0) {
					throw new IllegalArgumentException(errorCanonicalEnd);
				}
				break; // discard the remaining 4 bits
			}
			// Read the 5th char in a 8-chars subblock
			// Assert that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 4: insert 4 bits, leave 1 bit
			bytes[offset++] = (byte) (nextByte | (digit >> 1));
			nextByte = (byte) ((digit & 1) << 7);
			if (i >= base32.length()) {
				if (nextByte != (byte) 0) {
					throw new IllegalArgumentException(errorCanonicalEnd);
				}
				break; // discard the remaining 1 bit
			}
			// Read the 6th char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 1: leave 6 bits
			nextByte |= (byte) (digit << 2);
			// Assert(i < base32.length) // tested before loop
			// Read the 7th char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 6: insert 2 bits, leave 3 bits
			bytes[offset++] = (byte) (nextByte | (digit >> 3));
			nextByte = (byte) ((digit & 7) << 5);
			if (i >= base32.length()) {
				if (nextByte != (byte) 0) {
					throw new IllegalArgumentException(errorCanonicalEnd);
				}
				break; // discard the remaining 3 bits
			}
			// Read the 8th char in a 8-chars subblock
			// Check that chars are not outside the lookup table and valid
			lookup = base32.charAt(i++) - '2';
			if (lookup < 0 || lookup >= base32Lookup.length) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			digit = base32Lookup[lookup];
			if (digit == -1) {
				throw new IllegalArgumentException(errorInvalidChar);
			}
			// // STEP n = 3: insert 5 bits, leave 0 bit
			bytes[offset++] = (byte) (nextByte | digit);
			// // possible end of string here with no trailing bits
		}
		// On loop exit, discard trialing n bits.
		return bytes;
	}

	/**
	 * Encode an array of binary bytes into a Base32 string. Should not fail
	 * (the only possible exception is that the returned string cannot be
	 * allocated in memory)
	 */
	static public String encode(final byte[] bytes) {
		StringBuilder base32 = new StringBuilder((bytes.length * 8 + 4) / 5);
		int currByte, digit, i = 0;
		while (i < bytes.length) {
			// INVARIANTS FOR EACH STEP n in [0..5[; digit in [0..31[;
			// The remaining n bits are already aligned on top positions
			// of the 5 least bits of digit, the other bits are 0.
			// //// STEP n = 0; insert new 5 bits, leave 3 bits
			currByte = bytes[i++] & 255;
			base32.append(base32Chars.charAt(currByte >> 3));
			digit = (currByte & 7) << 2;
			if (i >= bytes.length) { // put the last 3 bits
				base32.append(base32Chars.charAt(digit));
				break;
			}
			// //// STEP n = 3: insert 2 new bits, then 5 bits, leave 1 bit
			currByte = bytes[i++] & 255;
			base32.append(base32Chars.charAt(digit | (currByte >> 6)));
			base32.append(base32Chars.charAt((currByte >> 1) & 31));
			digit = (currByte & 1) << 4;
			if (i >= bytes.length) { // put the last 1 bit
				base32.append(base32Chars.charAt(digit));
				break;
			}
			// //// STEP n = 1: insert 4 new bits, leave 4 bit
			currByte = bytes[i++] & 255;
			base32.append(base32Chars.charAt(digit | (currByte >> 4)));
			digit = (currByte & 15) << 1;
			if (i >= bytes.length) { // put the last 4 bits
				base32.append(base32Chars.charAt(digit));
				break;
			}
			// //// STEP n = 4: insert 1 new bit, then 5 bits, leave 2 bits
			currByte = bytes[i++] & 255;
			base32.append(base32Chars.charAt(digit | (currByte >> 7)));
			base32.append(base32Chars.charAt((currByte >> 2) & 31));
			digit = (currByte & 3) << 3;
			if (i >= bytes.length) { // put the last 2 bits
				base32.append(base32Chars.charAt(digit));
				break;
			}
			// /// STEP n = 2: insert 3 new bits, then 5 bits, leave 0 bit
			currByte = bytes[i++] & 255;
			base32.append(base32Chars.charAt(digit | (currByte >> 5)));
			base32.append(base32Chars.charAt(currByte & 31));
			// // This point is reached for bytes.length multiple of 5
		}
		return base32.toString();
	}
}
