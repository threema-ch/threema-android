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

public class xsalsa20
{
	final int crypto_stream_xsalsa20_ref_KEYBYTES = 32;
	final int crypto_stream_xsalsa20_ref_NONCEBYTES = 24;

	protected final static byte[] sigma = {(byte) 'e', (byte) 'x', (byte) 'p', (byte) 'a',
						  (byte) 'n', (byte) 'd', (byte) ' ', (byte) '3',
						  (byte) '2', (byte) '-', (byte) 'b', (byte) 'y',
						  (byte) 't', (byte) 'e', (byte) ' ', (byte) 'k'};

	public static int crypto_stream(byte[] c, int clen, byte[] n, byte[] k)
	{
		byte[] subkey = new byte[32];

		hsalsa20.crypto_core(subkey, n, k, sigma);
		return salsa20.crypto_stream(c, clen, n, 16, subkey);
	}

	public static int crypto_stream_xor(byte[] c, byte[] m, long mlen, byte[] n, byte[] k)
	{
		byte[] subkey = new byte[32];

		hsalsa20.crypto_core(subkey, n, k, sigma);
		return salsa20.crypto_stream_xor(c, m, (int) mlen, n, 16, subkey);
	}

    public static int crypto_stream_xor_skip32(byte[] c0, byte[] c, int coffset, byte[] m, int moffset, long mlen, byte[] n, byte[] k)
    {
        /* Variant of crypto_stream_xor that outputs the first 32 bytes of the cipherstream to c0 */

        byte[] subkey = new byte[32];

        hsalsa20.crypto_core(subkey, n, k, sigma);
        return salsa20.crypto_stream_xor_skip32(c0, c, coffset, m, moffset, (int) mlen, n, 16, subkey);
    }
}
