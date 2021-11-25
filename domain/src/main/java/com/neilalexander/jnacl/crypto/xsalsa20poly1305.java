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

public class xsalsa20poly1305
{
	final int crypto_secretbox_KEYBYTES = 32;
	final int crypto_secretbox_NONCEBYTES = 24;
	final int crypto_secretbox_ZEROBYTES = 32;
	final int crypto_secretbox_BOXZEROBYTES = 16;

	static public int crypto_secretbox(byte[] c, byte[] m, long mlen, byte[] n, byte[] k)
	{
		if (mlen < 32)
			return -1;

		xsalsa20.crypto_stream_xor(c, m, mlen, n, k);
		poly1305.crypto_onetimeauth(c, 16, c, 32, mlen - 32, c);

		for (int i = 0; i < 16; ++i)
			c[i] = 0;

		return 0;
	}

    static public int crypto_secretbox_nopad(byte[] c, int coffset, byte[] m, int moffset, long mlen, byte[] n, byte[] k)
    {
        /* variant of crypto_secretbox that doesn't require 32 zero bytes before m and doesn't output
         * 16 zero bytes before c */
        byte[] c0 = new byte[32];

        xsalsa20.crypto_stream_xor_skip32(c0, c, coffset+16, m, moffset, mlen, n, k);
        poly1305.crypto_onetimeauth(c, coffset, c, coffset+16, mlen, c0);

        return 0;
    }

	static public int crypto_secretbox_open(byte[] m, byte[] c, long clen, byte[] n, byte[] k)
	{
		if (clen < 32)
			return -1;

		byte[] subkeyp = new byte[32];

		xsalsa20.crypto_stream(subkeyp, 32, n, k);

		if (poly1305.crypto_onetimeauth_verify(c, 16, c, 32, clen - 32, subkeyp) != 0)
			return -1;

		xsalsa20.crypto_stream_xor(m, c, clen, n, k);

		for (int i = 0; i < 32; ++i)
			m[i] = 0;

		return 0;
	}

    static public int crypto_secretbox_open_nopad(byte[] m, int moffset, byte[] c, int coffset, long clen, byte[] n, byte[] k)
    {
        /* variant of crypto_secretbox_open that doesn't require 16 zero bytes before c and doesn't output
         * 32 zero bytes before m */

        if (clen < 16)
            return -1;

        byte[] subkeyp = new byte[32];

        xsalsa20.crypto_stream(subkeyp, 32, n, k);

        if (poly1305.crypto_onetimeauth_verify(c, coffset, c, coffset+16, clen - 16, subkeyp) != 0)
            return -1;

        xsalsa20.crypto_stream_xor_skip32(null, m, moffset, c, coffset+16, clen - 16, n, k);

        return 0;
    }
}
