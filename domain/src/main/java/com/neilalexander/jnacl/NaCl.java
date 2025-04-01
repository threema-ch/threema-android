//
//  Copyright (c) 2011, Neil Alexander T.
//  Copyright (c) 2013, Threema GmbH.
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with
//  or without modification, are permitted provided that the following
//  conditions are met:
//
//  - Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
//  - Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
//  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
//  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
//  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
//  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
//  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
//  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
//  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
//  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
//  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
//  POSSIBILITY OF SUCH DAMAGE.

package com.neilalexander.jnacl;

import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;
import com.neilalexander.jnacl.crypto.salsa20;
import com.neilalexander.jnacl.crypto.xsalsa20;
import com.neilalexander.jnacl.crypto.xsalsa20poly1305;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Formatter;

public class NaCl {
    public static final int PUBLICKEYBYTES = 32;
    public static final int SECRETKEYBYTES = 32;
    public static final int BEFORENMBYTES = 32;
    public static final int NONCEBYTES = 24;
    public static final int ZEROBYTES = 32;
    public static final int BOXZEROBYTES = 16;
    public static final int BOXOVERHEAD = ZEROBYTES - BOXZEROBYTES;
    public static final int SYMMKEYBYTES = 32;
    public static final int STREAMKEYBYTES = 32;

    private final byte[] precomputed = new byte[BEFORENMBYTES];

    /* Perform self test before anything else */
    static {
        selfTest();
    }

    public NaCl(byte[] privatekey, byte[] publickey) {
        if (privatekey.length != SECRETKEYBYTES)
            throw new Error("Invalid private key length");

        if (publickey.length != PUBLICKEYBYTES)
            throw new Error("Invalid public key length");

        curve25519xsalsa20poly1305.crypto_box_beforenm(this.precomputed, publickey, privatekey);
    }

    public NaCl(String privatekey, String publickey) {
        this(getBinary(privatekey), getBinary(publickey));
    }

    public byte[] encrypt(byte[] input, byte[] nonce) {
        return encrypt(input, input.length, nonce);
    }

    public byte[] encrypt(byte[] input, int inputlength, byte[] nonce) {
        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        byte[] output = new byte[inputlength + BOXOVERHEAD];
        curve25519xsalsa20poly1305.crypto_box_afternm_nopad(output, 0, input, 0, input.length, nonce, this.precomputed);

        return output;
    }

    public byte[] decrypt(byte[] input, byte[] nonce) {
        return decrypt(input, input.length, nonce);
    }

    public byte[] decrypt(byte[] input, int inputlength, byte[] nonce) {
        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        if (inputlength < BOXOVERHEAD)
            return null;

        byte[] output = new byte[inputlength - BOXOVERHEAD];
        if (curve25519xsalsa20poly1305.crypto_box_open_afternm_nopad(output, 0, input, 0, input.length, nonce, this.precomputed) != 0)
            return null;

        return output;
    }

    public byte[] getPrecomputed() {
        return precomputed;
    }

    public static void genkeypair(byte[] publickey, byte[] privatekey) {
        genkeypair(publickey, privatekey, null);
    }

    public static void genkeypair(byte[] publickey, byte[] privatekey, byte[] seed) {
        SecureRandom random = new SecureRandom();

        random.nextBytes(privatekey);

        if (seed != null) {
            if (seed.length != SECRETKEYBYTES)
                throw new Error("Invalid seed length");

            for (int i = 0; i < SECRETKEYBYTES; i++)
                privatekey[i] ^= seed[i];
        }

        curve25519xsalsa20poly1305.crypto_box_getpublickey(publickey, privatekey);
    }

    public static byte[] derivePublicKey(byte[] privatekey) {
        if (privatekey.length != SECRETKEYBYTES)
            throw new Error("Invalid private key length");

        byte[] publickey = new byte[PUBLICKEYBYTES];
        curve25519xsalsa20poly1305.crypto_box_getpublickey(publickey, privatekey);
        return publickey;
    }

    public static byte[] symmetricEncryptData(byte[] input, byte[] key, byte[] nonce) {
        if (key.length != SYMMKEYBYTES)
            throw new Error("Invalid symmetric key length");

        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        byte[] output = new byte[input.length + BOXOVERHEAD];
        xsalsa20poly1305.crypto_secretbox_nopad(output, 0, input, 0, input.length, nonce, key);

        return output;
    }

    /**
     * In-place version of {@link #symmetricEncryptData(byte[], byte[], byte[])} that stores the output
     * in the same byte array as the input. The input data must begin at offset {@link #BOXOVERHEAD} in
     * the array (the first BOXOVERHEAD bytes are ignored and will be overwritten with the message
     * authentication code during encryption).
     *
     * @param io    plaintext on input (starting at offset BOXOVERHEAD), ciphertext on return (full array)
     * @param key   encryption key
     * @param nonce encryption nonce
     */
    public static void symmetricEncryptDataInplace(byte[] io, byte[] key, byte[] nonce) {

        if (key.length != SYMMKEYBYTES)
            throw new Error("Invalid symmetric key length");

        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        if (io.length < BOXOVERHEAD)
            throw new Error("Invalid I/O length");

        xsalsa20poly1305.crypto_secretbox_nopad(io, 0, io, BOXOVERHEAD, io.length - BOXOVERHEAD, nonce, key);
    }

    public static byte[] symmetricDecryptData(byte[] input, byte[] key, byte[] nonce) {
        if (key.length != SYMMKEYBYTES)
            throw new Error("Invalid symmetric key length");

        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        byte[] output = new byte[input.length - BOXOVERHEAD];
        if (xsalsa20poly1305.crypto_secretbox_open_nopad(output, 0, input, 0, input.length, nonce, key) != 0)
            return null;

        return output;
    }

    /**
     * In-place version of {@link #symmetricDecryptData(byte[], byte[], byte[])} that stores the output in
     * the same byte array as the input. Note that the decrypted output is shorter than the input, so the
     * last {@link #BOXOVERHEAD} bytes should be ignored in the decrypted output.
     *
     * @param io    ciphertext on input (full array), plaintext on output (last BOXOVERHEAD bytes set to zero)
     * @param key   encryption key
     * @param nonce encryption nonce
     * @return decryption successful true/false
     */
    public static boolean symmetricDecryptDataInplace(byte[] io, byte[] key, byte[] nonce) {
        if (key.length != SYMMKEYBYTES)
            throw new Error("Invalid symmetric key length");

        if (nonce.length != NONCEBYTES)
            throw new Error("Invalid nonce length");

        if (io.length < BOXOVERHEAD)
            throw new Error("Invalid I/O length");

        if (xsalsa20poly1305.crypto_secretbox_open_nopad(io, 0, io, 0, io.length, nonce, key) != 0)
            return false;

        /* zeroize last bytes */
        for (int i = io.length - BOXOVERHEAD; i < io.length; i++)
            io[i] = 0;

        return true;
    }

    public static byte[] streamCryptData(byte[] input, byte[] key, byte[] nonce) {
        if (key.length != STREAMKEYBYTES)
            throw new Error("Invalid symmetric key length");

        byte[] output = new byte[input.length];
        xsalsa20.crypto_stream_xor(output, input, input.length, nonce, key);

        return output;
    }

    public static byte[] getBinary(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));

        return data;
    }

    public static String asHex(byte[] buf) {
        try (Formatter formatter = new Formatter()) {
            for (byte b : buf)
                formatter.format("%02x", b);
            return formatter.toString();
        }
    }

    public static String asHex(int[] buf) {
        try (Formatter formatter = new Formatter()) {
            for (int b : buf)
                formatter.format("%02x", b);
            return formatter.toString();
        }
    }

    public boolean haveNativeCrypto() {
        return salsa20.haveNativeCrypto();
    }

    public static void selfTest() {
        /* test vectors from tests/box.* in nacl distribution */
        byte alicepk[] = new byte[]{
            (byte) 0x85, (byte) 0x20, (byte) 0xf0, (byte) 0x09, (byte) 0x89, (byte) 0x30, (byte) 0xa7, (byte) 0x54,
            (byte) 0x74, (byte) 0x8b, (byte) 0x7d, (byte) 0xdc, (byte) 0xb4, (byte) 0x3e, (byte) 0xf7, (byte) 0x5a,
            (byte) 0x0d, (byte) 0xbf, (byte) 0x3a, (byte) 0x0d, (byte) 0x26, (byte) 0x38, (byte) 0x1a, (byte) 0xf4,
            (byte) 0xeb, (byte) 0xa4, (byte) 0xa9, (byte) 0x8e, (byte) 0xaa, (byte) 0x9b, (byte) 0x4e, (byte) 0x6a
        };

        byte alicesk[] = {
            (byte) 0x77, (byte) 0x07, (byte) 0x6d, (byte) 0x0a, (byte) 0x73, (byte) 0x18, (byte) 0xa5, (byte) 0x7d,
            (byte) 0x3c, (byte) 0x16, (byte) 0xc1, (byte) 0x72, (byte) 0x51, (byte) 0xb2, (byte) 0x66, (byte) 0x45,
            (byte) 0xdf, (byte) 0x4c, (byte) 0x2f, (byte) 0x87, (byte) 0xeb, (byte) 0xc0, (byte) 0x99, (byte) 0x2a,
            (byte) 0xb1, (byte) 0x77, (byte) 0xfb, (byte) 0xa5, (byte) 0x1d, (byte) 0xb9, (byte) 0x2c, (byte) 0x2a
        };

        byte bobpk[] = {
            (byte) 0xde, (byte) 0x9e, (byte) 0xdb, (byte) 0x7d, (byte) 0x7b, (byte) 0x7d, (byte) 0xc1, (byte) 0xb4,
            (byte) 0xd3, (byte) 0x5b, (byte) 0x61, (byte) 0xc2, (byte) 0xec, (byte) 0xe4, (byte) 0x35, (byte) 0x37,
            (byte) 0x3f, (byte) 0x83, (byte) 0x43, (byte) 0xc8, (byte) 0x5b, (byte) 0x78, (byte) 0x67, (byte) 0x4d,
            (byte) 0xad, (byte) 0xfc, (byte) 0x7e, (byte) 0x14, (byte) 0x6f, (byte) 0x88, (byte) 0x2b, (byte) 0x4f
        };

        byte bobsk[] = {
            (byte) 0x5d, (byte) 0xab, (byte) 0x08, (byte) 0x7e, (byte) 0x62, (byte) 0x4a, (byte) 0x8a, (byte) 0x4b,
            (byte) 0x79, (byte) 0xe1, (byte) 0x7f, (byte) 0x8b, (byte) 0x83, (byte) 0x80, (byte) 0x0e, (byte) 0xe6,
            (byte) 0x6f, (byte) 0x3b, (byte) 0xb1, (byte) 0x29, (byte) 0x26, (byte) 0x18, (byte) 0xb6, (byte) 0xfd,
            (byte) 0x1c, (byte) 0x2f, (byte) 0x8b, (byte) 0x27, (byte) 0xff, (byte) 0x88, (byte) 0xe0, (byte) 0xeb
        };

        byte nonce[] = {
            (byte) 0x69, (byte) 0x69, (byte) 0x6e, (byte) 0xe9, (byte) 0x55, (byte) 0xb6, (byte) 0x2b, (byte) 0x73,
            (byte) 0xcd, (byte) 0x62, (byte) 0xbd, (byte) 0xa8, (byte) 0x75, (byte) 0xfc, (byte) 0x73, (byte) 0xd6,
            (byte) 0x82, (byte) 0x19, (byte) 0xe0, (byte) 0x03, (byte) 0x6b, (byte) 0x7a, (byte) 0x0b, (byte) 0x37
        };

        byte m[] = {
            (byte) 0xbe, (byte) 0x07, (byte) 0x5f, (byte) 0xc5, (byte) 0x3c, (byte) 0x81, (byte) 0xf2, (byte) 0xd5,
            (byte) 0xcf, (byte) 0x14, (byte) 0x13, (byte) 0x16, (byte) 0xeb, (byte) 0xeb, (byte) 0x0c, (byte) 0x7b,
            (byte) 0x52, (byte) 0x28, (byte) 0xc5, (byte) 0x2a, (byte) 0x4c, (byte) 0x62, (byte) 0xcb, (byte) 0xd4,
            (byte) 0x4b, (byte) 0x66, (byte) 0x84, (byte) 0x9b, (byte) 0x64, (byte) 0x24, (byte) 0x4f, (byte) 0xfc,
            (byte) 0xe5, (byte) 0xec, (byte) 0xba, (byte) 0xaf, (byte) 0x33, (byte) 0xbd, (byte) 0x75, (byte) 0x1a,
            (byte) 0x1a, (byte) 0xc7, (byte) 0x28, (byte) 0xd4, (byte) 0x5e, (byte) 0x6c, (byte) 0x61, (byte) 0x29,
            (byte) 0x6c, (byte) 0xdc, (byte) 0x3c, (byte) 0x01, (byte) 0x23, (byte) 0x35, (byte) 0x61, (byte) 0xf4,
            (byte) 0x1d, (byte) 0xb6, (byte) 0x6c, (byte) 0xce, (byte) 0x31, (byte) 0x4a, (byte) 0xdb, (byte) 0x31,
            (byte) 0x0e, (byte) 0x3b, (byte) 0xe8, (byte) 0x25, (byte) 0x0c, (byte) 0x46, (byte) 0xf0, (byte) 0x6d,
            (byte) 0xce, (byte) 0xea, (byte) 0x3a, (byte) 0x7f, (byte) 0xa1, (byte) 0x34, (byte) 0x80, (byte) 0x57,
            (byte) 0xe2, (byte) 0xf6, (byte) 0x55, (byte) 0x6a, (byte) 0xd6, (byte) 0xb1, (byte) 0x31, (byte) 0x8a,
            (byte) 0x02, (byte) 0x4a, (byte) 0x83, (byte) 0x8f, (byte) 0x21, (byte) 0xaf, (byte) 0x1f, (byte) 0xde,
            (byte) 0x04, (byte) 0x89, (byte) 0x77, (byte) 0xeb, (byte) 0x48, (byte) 0xf5, (byte) 0x9f, (byte) 0xfd,
            (byte) 0x49, (byte) 0x24, (byte) 0xca, (byte) 0x1c, (byte) 0x60, (byte) 0x90, (byte) 0x2e, (byte) 0x52,
            (byte) 0xf0, (byte) 0xa0, (byte) 0x89, (byte) 0xbc, (byte) 0x76, (byte) 0x89, (byte) 0x70, (byte) 0x40,
            (byte) 0xe0, (byte) 0x82, (byte) 0xf9, (byte) 0x37, (byte) 0x76, (byte) 0x38, (byte) 0x48, (byte) 0x64,
            (byte) 0x5e, (byte) 0x07, (byte) 0x05
        };

        byte c_expected[] = {
            (byte) 0xf3, (byte) 0xff, (byte) 0xc7, (byte) 0x70, (byte) 0x3f, (byte) 0x94, (byte) 0x00, (byte) 0xe5,
            (byte) 0x2a, (byte) 0x7d, (byte) 0xfb, (byte) 0x4b, (byte) 0x3d, (byte) 0x33, (byte) 0x05, (byte) 0xd9,
            (byte) 0x8e, (byte) 0x99, (byte) 0x3b, (byte) 0x9f, (byte) 0x48, (byte) 0x68, (byte) 0x12, (byte) 0x73,
            (byte) 0xc2, (byte) 0x96, (byte) 0x50, (byte) 0xba, (byte) 0x32, (byte) 0xfc, (byte) 0x76, (byte) 0xce,
            (byte) 0x48, (byte) 0x33, (byte) 0x2e, (byte) 0xa7, (byte) 0x16, (byte) 0x4d, (byte) 0x96, (byte) 0xa4,
            (byte) 0x47, (byte) 0x6f, (byte) 0xb8, (byte) 0xc5, (byte) 0x31, (byte) 0xa1, (byte) 0x18, (byte) 0x6a,
            (byte) 0xc0, (byte) 0xdf, (byte) 0xc1, (byte) 0x7c, (byte) 0x98, (byte) 0xdc, (byte) 0xe8, (byte) 0x7b,
            (byte) 0x4d, (byte) 0xa7, (byte) 0xf0, (byte) 0x11, (byte) 0xec, (byte) 0x48, (byte) 0xc9, (byte) 0x72,
            (byte) 0x71, (byte) 0xd2, (byte) 0xc2, (byte) 0x0f, (byte) 0x9b, (byte) 0x92, (byte) 0x8f, (byte) 0xe2,
            (byte) 0x27, (byte) 0x0d, (byte) 0x6f, (byte) 0xb8, (byte) 0x63, (byte) 0xd5, (byte) 0x17, (byte) 0x38,
            (byte) 0xb4, (byte) 0x8e, (byte) 0xee, (byte) 0xe3, (byte) 0x14, (byte) 0xa7, (byte) 0xcc, (byte) 0x8a,
            (byte) 0xb9, (byte) 0x32, (byte) 0x16, (byte) 0x45, (byte) 0x48, (byte) 0xe5, (byte) 0x26, (byte) 0xae,
            (byte) 0x90, (byte) 0x22, (byte) 0x43, (byte) 0x68, (byte) 0x51, (byte) 0x7a, (byte) 0xcf, (byte) 0xea,
            (byte) 0xbd, (byte) 0x6b, (byte) 0xb3, (byte) 0x73, (byte) 0x2b, (byte) 0xc0, (byte) 0xe9, (byte) 0xda,
            (byte) 0x99, (byte) 0x83, (byte) 0x2b, (byte) 0x61, (byte) 0xca, (byte) 0x01, (byte) 0xb6, (byte) 0xde,
            (byte) 0x56, (byte) 0x24, (byte) 0x4a, (byte) 0x9e, (byte) 0x88, (byte) 0xd5, (byte) 0xf9, (byte) 0xb3,
            (byte) 0x79, (byte) 0x73, (byte) 0xf6, (byte) 0x22, (byte) 0xa4, (byte) 0x3d, (byte) 0x14, (byte) 0xa6,
            (byte) 0x59, (byte) 0x9b, (byte) 0x1f, (byte) 0x65, (byte) 0x4c, (byte) 0xb4, (byte) 0x5a, (byte) 0x74,
            (byte) 0xe3, (byte) 0x55, (byte) 0xa5
        };

        /* encrypt data and compare with expected result */
        NaCl nacl = new NaCl(alicesk, bobpk);
        byte[] c = nacl.encrypt(m, nonce);
        if (!Arrays.equals(c, c_expected))
            throw new RuntimeException("Crypto self-test failed (1)");

        /* decrypt data and compare with plaintext */
        nacl = new NaCl(bobsk, alicepk);
        byte[] p_d = nacl.decrypt(c, nonce);
        if (!Arrays.equals(p_d, m))
            throw new RuntimeException("Crypto self-test failed (2)");
    }
}
