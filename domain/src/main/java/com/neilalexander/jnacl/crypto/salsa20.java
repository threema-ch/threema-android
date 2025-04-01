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
//

package com.neilalexander.jnacl.crypto;

public class salsa20 {
    final int crypto_core_salsa20_ref_OUTPUTBYTES = 64;
    final int crypto_core_salsa20_ref_INPUTBYTES = 16;
    final int crypto_core_salsa20_ref_KEYBYTES = 32;
    final int crypto_core_salsa20_ref_CONSTBYTES = 16;
    final int crypto_stream_salsa20_ref_KEYBYTES = 32;
    final int crypto_stream_salsa20_ref_NONCEBYTES = 8;

    final static int ROUNDS = 20;

    static boolean haveNative;

    static {
        try {
            System.loadLibrary("nacl-jni");
            haveNative = true;
        } catch (UnsatisfiedLinkError e) {
            haveNative = false;
            System.out.println("Warning: using Java salsa20 implementation; expect bad performance");
        }
    }

    public static boolean haveNativeCrypto() {
        return haveNative;
    }

    static long rotate(int u, int c) {
        return (u << c) | (u >>> (32 - c));
    }

    static int load_littleendian(byte[] x, int offset) {
        return ((int) (x[offset]) & 0xff) |
            ((((int) (x[offset + 1]) & 0xff)) << 8) |
            ((((int) (x[offset + 2]) & 0xff)) << 16) |
            ((((int) (x[offset + 3]) & 0xff)) << 24);
    }

    static void store_littleendian(byte[] x, int offset, int u) {
        x[offset] = (byte) u;
        u >>>= 8;
        x[offset + 1] = (byte) u;
        u >>>= 8;
        x[offset + 2] = (byte) u;
        u >>>= 8;
        x[offset + 3] = (byte) u;
    }

    public static int crypto_core(byte[] outv, byte[] inv, byte[] k, byte[] c) {
        int x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15;
        int j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
        int i;

        j0 = x0 = load_littleendian(c, 0);
        j1 = x1 = load_littleendian(k, 0);
        j2 = x2 = load_littleendian(k, 4);
        j3 = x3 = load_littleendian(k, 8);
        j4 = x4 = load_littleendian(k, 12);
        j5 = x5 = load_littleendian(c, 4);
        j6 = x6 = load_littleendian(inv, 0);
        j7 = x7 = load_littleendian(inv, 4);
        j8 = x8 = load_littleendian(inv, 8);
        j9 = x9 = load_littleendian(inv, 12);
        j10 = x10 = load_littleendian(c, 8);
        j11 = x11 = load_littleendian(k, 16);
        j12 = x12 = load_littleendian(k, 20);
        j13 = x13 = load_littleendian(k, 24);
        j14 = x14 = load_littleendian(k, 28);
        j15 = x15 = load_littleendian(c, 12);

        for (i = ROUNDS; i > 0; i -= 2) {
            x4 ^= rotate(x0 + x12, 7);
            x8 ^= rotate(x4 + x0, 9);
            x12 ^= rotate(x8 + x4, 13);
            x0 ^= rotate(x12 + x8, 18);
            x9 ^= rotate(x5 + x1, 7);
            x13 ^= rotate(x9 + x5, 9);
            x1 ^= rotate(x13 + x9, 13);
            x5 ^= rotate(x1 + x13, 18);
            x14 ^= rotate(x10 + x6, 7);
            x2 ^= rotate(x14 + x10, 9);
            x6 ^= rotate(x2 + x14, 13);
            x10 ^= rotate(x6 + x2, 18);
            x3 ^= rotate(x15 + x11, 7);
            x7 ^= rotate(x3 + x15, 9);
            x11 ^= rotate(x7 + x3, 13);
            x15 ^= rotate(x11 + x7, 18);
            x1 ^= rotate(x0 + x3, 7);
            x2 ^= rotate(x1 + x0, 9);
            x3 ^= rotate(x2 + x1, 13);
            x0 ^= rotate(x3 + x2, 18);
            x6 ^= rotate(x5 + x4, 7);
            x7 ^= rotate(x6 + x5, 9);
            x4 ^= rotate(x7 + x6, 13);
            x5 ^= rotate(x4 + x7, 18);
            x11 ^= rotate(x10 + x9, 7);
            x8 ^= rotate(x11 + x10, 9);
            x9 ^= rotate(x8 + x11, 13);
            x10 ^= rotate(x9 + x8, 18);
            x12 ^= rotate(x15 + x14, 7);
            x13 ^= rotate(x12 + x15, 9);
            x14 ^= rotate(x13 + x12, 13);
            x15 ^= rotate(x14 + x13, 18);
        }

        x0 += j0;
        x1 += j1;
        x2 += j2;
        x3 += j3;
        x4 += j4;
        x5 += j5;
        x6 += j6;
        x7 += j7;
        x8 += j8;
        x9 += j9;
        x10 += j10;
        x11 += j11;
        x12 += j12;
        x13 += j13;
        x14 += j14;
        x15 += j15;

        store_littleendian(outv, 0, x0);
        store_littleendian(outv, 4, x1);
        store_littleendian(outv, 8, x2);
        store_littleendian(outv, 12, x3);
        store_littleendian(outv, 16, x4);
        store_littleendian(outv, 20, x5);
        store_littleendian(outv, 24, x6);
        store_littleendian(outv, 28, x7);
        store_littleendian(outv, 32, x8);
        store_littleendian(outv, 36, x9);
        store_littleendian(outv, 40, x10);
        store_littleendian(outv, 44, x11);
        store_littleendian(outv, 48, x12);
        store_littleendian(outv, 52, x13);
        store_littleendian(outv, 56, x14);
        store_littleendian(outv, 60, x15);

        return 0;
    }

    public static int crypto_stream(byte[] c, int clen, byte[] n, int noffset, byte[] k) {
        if (haveNative) {
            try {
                return crypto_stream_native(c, clen, n, noffset, k);
            } catch (UnsatisfiedLinkError e) {
                /* fall through to Java implementation */
            }
        }

        byte[] inv = new byte[16];
        byte[] block = new byte[64];

        int coffset = 0;

        if (clen == 0)
            return 0;

        for (int i = 0; i < 8; ++i)
            inv[i] = n[noffset + i];

        for (int i = 8; i < 16; ++i)
            inv[i] = 0;

        while (clen >= 64) {
            salsa20.crypto_core(c, inv, k, xsalsa20.sigma);

            int u = 1;

            for (int i = 8; i < 16; ++i) {
                u += inv[i] & 0xff;
                inv[i] = (byte) u;
                u >>>= 8;
            }

            clen -= 64;
            coffset += 64;
        }

        if (clen != 0) {
            salsa20.crypto_core(block, inv, k, xsalsa20.sigma);

            for (int i = 0; i < clen; ++i)
                c[coffset + i] = block[i];
        }

        return 0;
    }

    public static int crypto_stream_xor(byte[] c, byte[] m, int mlen, byte[] n, int noffset, byte[] k) {
        if (haveNative) {
            try {
                return crypto_stream_xor_native(c, m, mlen, n, noffset, k);
            } catch (UnsatisfiedLinkError e) {
                /* fall through to Java implementation */
            }
        }

        byte[] inv = new byte[16];
        byte[] block = new byte[64];

        int coffset = 0;
        int moffset = 0;

        if (mlen == 0)
            return 0;

        for (int i = 0; i < 8; ++i)
            inv[i] = n[noffset + i];

        for (int i = 8; i < 16; ++i)
            inv[i] = 0;

        while (mlen >= 64) {
            salsa20.crypto_core(block, inv, k, xsalsa20.sigma);

            for (int i = 0; i < 64; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ block[i]);

            int u = 1;

            for (int i = 8; i < 16; ++i) {
                u += inv[i] & 0xff;
                inv[i] = (byte) u;
                u >>>= 8;
            }

            mlen -= 64;
            coffset += 64;
            moffset += 64;
        }

        if (mlen != 0) {
            salsa20.crypto_core(block, inv, k, xsalsa20.sigma);

            for (int i = 0; i < mlen; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ block[i]);
        }

        return 0;
    }

    public static int crypto_stream_xor_skip32(byte[] c0, byte[] c, int coffset, byte[] m, int moffset, int mlen, byte[] n, int noffset, byte[] k) {
        /* Variant of crypto_stream_xor that outputs the first 32 bytes of the cipherstream to c0 */

        if (haveNative) {
            try {
                return crypto_stream_xor_skip32_native(c0, c, coffset, m, moffset, mlen, n, noffset, k);
            } catch (UnsatisfiedLinkError e) {
                /* fall through to Java implementation */
            }
        }

        int u;
        byte[] inv = new byte[16];
        byte[] prevblock = new byte[64];
        byte[] curblock = new byte[64];

        if (mlen == 0)
            return 0;

        for (int i = 0; i < 8; ++i)
            inv[i] = n[noffset + i];

        for (int i = 8; i < 16; ++i)
            inv[i] = 0;

        /* calculate first block */
        salsa20.crypto_core(prevblock, inv, k, xsalsa20.sigma);

        /* extract first 32 bytes of cipherstream into c0 */
        if (c0 != null)
            System.arraycopy(prevblock, 0, c0, 0, 32);

        while (mlen >= 64) {
            u = 1;
            for (int i = 8; i < 16; ++i) {
                u += inv[i] & 0xff;
                inv[i] = (byte) u;
                u >>>= 8;
            }

            salsa20.crypto_core(curblock, inv, k, xsalsa20.sigma);

            for (int i = 0; i < 32; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ prevblock[i + 32]);

            for (int i = 32; i < 64; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ curblock[i - 32]);

            mlen -= 64;
            coffset += 64;
            moffset += 64;

            byte[] tmpblock = prevblock;
            prevblock = curblock;
            curblock = tmpblock;
        }

        if (mlen != 0) {
            u = 1;
            for (int i = 8; i < 16; ++i) {
                u += inv[i] & 0xff;
                inv[i] = (byte) u;
                u >>>= 8;
            }

            salsa20.crypto_core(curblock, inv, k, xsalsa20.sigma);

            for (int i = 0; i < mlen && i < 32; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ prevblock[i + 32]);

            for (int i = 32; i < mlen && i < 64; ++i)
                c[coffset + i] = (byte) (m[moffset + i] ^ curblock[i - 32]);
        }

        return 0;
    }

    public native static int crypto_stream_native(byte[] c, int clen, byte[] n, int noffset, byte[] k);

    public native static int crypto_stream_xor_native(byte[] c, byte[] m, int mlen, byte[] n, int noffset, byte[] k);

    public native static int crypto_stream_xor_skip32_native(byte[] c0, byte[] c, int coffset, byte[] m, int moffset, int mlen, byte[] n, int noffset, byte[] k);
}
