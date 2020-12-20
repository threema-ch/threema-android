// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import static com.lambdaworks.codec.Base64.decode;
import static com.lambdaworks.codec.Base64.encode;

/**
 * Simple {@link SCrypt} interface for hashing passwords using the
 * <a href="http://www.tarsnap.com/scrypt.html">scrypt</a> key derivation function
 * and comparing a plain text password to a hashed one. The hashed output is an
 * extended implementation of the Modular Crypt Format that also includes the scrypt
 * algorithm parameters.
 *
 * Format: <code>$s0$PARAMS$SALT$KEY</code>.
 *
 * <dl>
 * <dd>PARAMS</dd><dt>32-bit hex integer containing log2(N) (16 bits), r (8 bits), and p (8 bits)</dt>
 * <dd>SALT</dd><dt>base64-encoded salt</dt>
 * <dd>KEY</dd><dt>base64-encoded derived key</dt>
 * </dl>
 *
 * <code>s0</code> identifies version 0 of the scrypt format, using a 128-bit salt and 256-bit derived key.
 *
 * @author  Will Glozer
 */
public class SCryptUtil {
    private static final int SALT_BITS = 128;
    private static final int DERIVED_KEY_BITS = 256;
    private static final SecureRandom SECURE_RANDOM;
    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM doesn't support SHA1PRNG?");
        }
    }

    /**
     * Hash the supplied plaintext password and generate output in the format described
     * in {@link SCryptUtil}.
     *
     * @param passwd    Password.
     * @param N         CPU cost parameter.
     * @param r         Memory cost parameter.
     * @param p         Parallelization parameter.
     *
     * @return The hashed password.
     */
    public static String scrypt(String passwd, int N, int r, int p) {
        byte[] bytes = passwd.getBytes(Charset.forName("UTF-8"));
        try {
            return scrypt(bytes, N, r, p);
        } finally {
            wipeArray(bytes);
        }
    }

    /**
     * Hash the supplied plaintext password and generate output in the format described
     * in {@link SCryptUtil}.
     *
     * @param passwd Password.
     * @param N      CPU cost parameter.
     * @param r      Memory cost parameter.
     * @param p      Parallelization parameter.
     *
     * @return The hashed password.
     */
    public static String scrypt(char[] passwd, int N, int r, int p) {
        byte[] bytes = toBytes(passwd);
        try {
            return scrypt(bytes, N, r, p);
        } finally {
            wipeArray(bytes);
        }
    }

    /**
     * Hash the supplied plaintext password and generate output in the format described
     * in {@link SCryptUtil}.
     *
     * @param passwordBytes Password.
     * @param N             CPU cost parameter.
     * @param r             Memory cost parameter.
     * @param p             Parallelization parameter.
     *
     * @return The hashed password.
     */
    public static String scrypt(byte[] passwordBytes, int N, int r, int p) {
        final byte[] salt = generateSalt();
        return scrypt(passwordBytes, salt, N, r, p);
    }

    /**
     * Hash the supplied plaintext password and generate output in the format described
     * in {@link SCryptUtil}.
     *
     * Allows for passing in the salt in the rare case where you actually want to
     * hash something to the same hash value. An example is hashing credit card
     * numbers in order to detect duplicates without storing the actual card number.
     *
     * @param passwordBytes Password.
     * @param salt          128 bit salt.
     * @param N             CPU cost parameter.
     * @param r             Memory cost parameter.
     * @param p             Parallelization parameter.
     *
     * @return The hashed password.
     * @see #generateSalt()
     */
    public static String scrypt(byte[] passwordBytes, byte[] salt, int N, int r, int p) {
        try {
            if (salt == null || salt.length != SALT_BITS / 8) {
                throw new IllegalArgumentException("Salt must be " + SALT_BITS + " bits");
            }

            byte[] derived = SCrypt.scrypt(passwordBytes, salt, N, r, p, DERIVED_KEY_BITS / 8);

            String params = Long.toString(log2(N) << 16L | r << 8 | p, 16);

            StringBuilder sb = new StringBuilder((salt.length + derived.length) * 2);
            sb.append("$s0$").append(params).append('$');
            sb.append(encode(salt)).append('$');
            sb.append(encode(derived));

            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM doesn't support HMAC_SHA256?");
        }
    }

    private static byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
        try {
            return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        } finally {
            wipeArray(byteBuffer.array());
        }
    }

    /**
     * Compare the supplied plaintext password to a hashed password.
     *
     * @param   passwd  Plaintext password.
     * @param   hashed  scrypt hashed password.
     * @return true if passwd matches hashed value.
     */
    public static boolean check(String passwd, String hashed) {
        byte[] bytes = passwd.getBytes(Charset.forName("UTF-8"));
        try {
            return checkInternal(bytes, hashed);
        } finally {
            wipeArray(bytes);
        }
    }

    /**
     * Compare the supplied plaintext password to a hashed password.
     *
     * @param   passwd  Plaintext password.
     * @param   hashed  scrypt hashed password.
     * @return true if passwd matches hashed value.
     */
    public static boolean check(char[] passwd, String hashed) {
        byte[] bytes = toBytes(passwd);
        try {
            return checkInternal(bytes, hashed);
        } finally {
            wipeArray(bytes);
        }
    }

    private static boolean checkInternal(byte[] passwordBytes, String hashed) {
        try {
            String[] parts = hashed.split("\\$");

            if (parts.length != 5 || !parts[1].equals("s0")) {
                throw new IllegalArgumentException("Invalid hashed value");
            }

            long params = Long.parseLong(parts[2], 16);
            byte[] salt = decode(parts[3].toCharArray());
            byte[] derived0 = decode(parts[4].toCharArray());

            int N = (int) Math.pow(2, params >> 16 & 0xffff);
            int r = (int) params >> 8 & 0xff;
            int p = (int) params      & 0xff;

            byte[] derived1 = SCrypt.scrypt(passwordBytes, salt, N, r, p, DERIVED_KEY_BITS / 8);

            if (derived0.length != derived1.length) return false;

            int result = 0;
            for (int i = 0; i < derived0.length; i++) {
                result |= derived0[i] ^ derived1[i];
            }
            return result == 0;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM doesn't support HMAC_SHA256?");
        }
    }

    /**
     * Generate a random 128 bit salt, in accordance with version 0 of the {@link SCryptUtil scrypt format}.
     *
     * @return 128 bit salt
     */
    public static byte[] generateSalt() {
        final byte[] salt = new byte[SALT_BITS / 8];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    private static int log2(int n) {
        int log = 0;
        if ((n & 0xffff0000 ) != 0) { n >>>= 16; log = 16; }
        if (n >= 256) { n >>>= 8; log += 8; }
        if (n >= 16 ) { n >>>= 4; log += 4; }
        if (n >= 4  ) { n >>>= 2; log += 2; }
        return log + (n >>> 1);
    }

    private static void wipeArray(byte[] array) {
        Arrays.fill(array, (byte) 0);
    }
}
