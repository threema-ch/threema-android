/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2015-2020 Threema GmbH
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

#include <string.h>
#include <jni.h>

#define ROUNDS 20

typedef unsigned int uint32;

static const unsigned char sigma[16] = "expand 32-byte k";

int crypto_stream_salsa20_ref(
	unsigned char *c,unsigned long long clen,
	const unsigned char *n,
	const unsigned char *k
);

int crypto_stream_salsa20_ref_xor(
	unsigned char *c,
	const unsigned char *m,unsigned long long mlen,
	const unsigned char *n,
	const unsigned char *k
);

int crypto_stream_salsa20_ref_xor_skip32(
		unsigned char *c0,
        unsigned char *c,unsigned long coffset,
  const unsigned char *m,unsigned long moffset,
  unsigned long long mlen,
  const unsigned char *n,
  const unsigned char *k
);

JNIEXPORT jint JNICALL Java_com_neilalexander_jnacl_crypto_salsa20_crypto_1stream_1native(JNIEnv* env, jclass cls,
	jbyteArray carr, jint clen, jbyteArray narr, jint noffset, jbyteArray karr)
{
	jbyte *c;
	jbyte n[8];
	jbyte k[32];
	int res;

	if ((*env)->GetArrayLength(env, carr) < clen) {
		/* bad length */
		return 1;
	}

	(*env)->GetByteArrayRegion(env, narr, noffset, 8, n);
	(*env)->GetByteArrayRegion(env, karr, 0, 32, k);

	c = (*env)->GetPrimitiveArrayCritical(env, carr, NULL);
	if (c == NULL)
		return 4;

	res = crypto_stream_salsa20_ref((unsigned char *)c, clen, (unsigned char *)n, (unsigned char *)k);

	(*env)->ReleasePrimitiveArrayCritical(env, carr, c, 0);

	return res;
}

JNIEXPORT jint JNICALL Java_com_neilalexander_jnacl_crypto_salsa20_crypto_1stream_1xor_1native(JNIEnv* env, jclass cls,
	jbyteArray carr, jbyteArray marr, jint mlen, jbyteArray narr, jint noffset, jbyteArray karr)
{
	jbyte *c, *m;
	jbyte n[8];
	jbyte k[32];
	int res;

	if ((*env)->GetArrayLength(env, marr) < mlen || (*env)->GetArrayLength(env, carr) < mlen) {
		/* bad length */
		return 1;
	}

	(*env)->GetByteArrayRegion(env, narr, noffset, 8, n);
	(*env)->GetByteArrayRegion(env, karr, 0, 32, k);

	c = (*env)->GetPrimitiveArrayCritical(env, carr, NULL);
	if (c == NULL)
		return 4;

	m = (*env)->GetPrimitiveArrayCritical(env, marr, NULL);
	if (m == NULL) {
		(*env)->ReleasePrimitiveArrayCritical(env, carr, c, 0);
		return 5;
	}

	res = crypto_stream_salsa20_ref_xor((unsigned char *)c, (unsigned char *)m, mlen, (unsigned char *)n, (unsigned char *)k);

	(*env)->ReleasePrimitiveArrayCritical(env, marr, m, 0);
	(*env)->ReleasePrimitiveArrayCritical(env, carr, c, 0);

	return res;
}

JNIEXPORT jint JNICALL Java_com_neilalexander_jnacl_crypto_salsa20_crypto_1stream_1xor_1skip32_1native(JNIEnv* env, jclass cls,
	jbyteArray c0arr, jbyteArray carr, jint coffset, jbyteArray marr, jint moffset, jint mlen, jbyteArray narr, jint noffset, jbyteArray karr)
{
	jbyte c0[32];
	jbyte *c, *m;
	jbyte n[8];
	jbyte k[32];
	int res;

	if ((*env)->GetArrayLength(env, marr) < (moffset+mlen) || (*env)->GetArrayLength(env, carr) < (coffset+mlen)) {
		/* bad length */
		return 1;
	}

	(*env)->GetByteArrayRegion(env, narr, noffset, 8, n);
	(*env)->GetByteArrayRegion(env, karr, 0, 32, k);

	c = (*env)->GetPrimitiveArrayCritical(env, carr, NULL);
	if (c == NULL)
		return 4;

	m = (*env)->GetPrimitiveArrayCritical(env, marr, NULL);
	if (m == NULL) {
		(*env)->ReleasePrimitiveArrayCritical(env, carr, c, 0);
		return 5;
	}

	res = crypto_stream_salsa20_ref_xor_skip32((unsigned char *)c0, (unsigned char *)c, coffset, (unsigned char *)m, moffset, mlen, (unsigned char *)n, (unsigned char *)k);

	(*env)->ReleasePrimitiveArrayCritical(env, marr, m, 0);
	(*env)->ReleasePrimitiveArrayCritical(env, carr, c, 0);

	if (c0arr != NULL)
		(*env)->SetByteArrayRegion(env, c0arr, 0, 32, c0);

	return res;
}




/* Public Domain code copied verbatim from NaCl below */

static uint32 rotate(uint32 u,int c)
{
    return (u << c) | (u >> (32 - c));
}

static uint32 load_littleendian(const unsigned char *x)
{
    return
    (uint32) (x[0]) \
    | (((uint32) (x[1])) << 8) \
    | (((uint32) (x[2])) << 16) \
    | (((uint32) (x[3])) << 24)
    ;
}

static void store_littleendian(unsigned char *x,uint32 u)
{
    x[0] = u; u >>= 8;
    x[1] = u; u >>= 8;
    x[2] = u; u >>= 8;
    x[3] = u;
}

int crypto_core_salsa20_ref(
        unsigned char *out,
  const unsigned char *in,
  const unsigned char *k,
  const unsigned char *c
)
{
  uint32 x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15;
  uint32 j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
  int i;

  j0 = x0 = load_littleendian(c + 0);
  j1 = x1 = load_littleendian(k + 0);
  j2 = x2 = load_littleendian(k + 4);
  j3 = x3 = load_littleendian(k + 8);
  j4 = x4 = load_littleendian(k + 12);
  j5 = x5 = load_littleendian(c + 4);
  j6 = x6 = load_littleendian(in + 0);
  j7 = x7 = load_littleendian(in + 4);
  j8 = x8 = load_littleendian(in + 8);
  j9 = x9 = load_littleendian(in + 12);
  j10 = x10 = load_littleendian(c + 8);
  j11 = x11 = load_littleendian(k + 16);
  j12 = x12 = load_littleendian(k + 20);
  j13 = x13 = load_littleendian(k + 24);
  j14 = x14 = load_littleendian(k + 28);
  j15 = x15 = load_littleendian(c + 12);

  for (i = ROUNDS;i > 0;i -= 2) {
     x4 ^= rotate( x0+x12, 7);
     x8 ^= rotate( x4+ x0, 9);
    x12 ^= rotate( x8+ x4,13);
     x0 ^= rotate(x12+ x8,18);
     x9 ^= rotate( x5+ x1, 7);
    x13 ^= rotate( x9+ x5, 9);
     x1 ^= rotate(x13+ x9,13);
     x5 ^= rotate( x1+x13,18);
    x14 ^= rotate(x10+ x6, 7);
     x2 ^= rotate(x14+x10, 9);
     x6 ^= rotate( x2+x14,13);
    x10 ^= rotate( x6+ x2,18);
     x3 ^= rotate(x15+x11, 7);
     x7 ^= rotate( x3+x15, 9);
    x11 ^= rotate( x7+ x3,13);
    x15 ^= rotate(x11+ x7,18);
     x1 ^= rotate( x0+ x3, 7);
     x2 ^= rotate( x1+ x0, 9);
     x3 ^= rotate( x2+ x1,13);
     x0 ^= rotate( x3+ x2,18);
     x6 ^= rotate( x5+ x4, 7);
     x7 ^= rotate( x6+ x5, 9);
     x4 ^= rotate( x7+ x6,13);
     x5 ^= rotate( x4+ x7,18);
    x11 ^= rotate(x10+ x9, 7);
     x8 ^= rotate(x11+x10, 9);
     x9 ^= rotate( x8+x11,13);
    x10 ^= rotate( x9+ x8,18);
    x12 ^= rotate(x15+x14, 7);
    x13 ^= rotate(x12+x15, 9);
    x14 ^= rotate(x13+x12,13);
    x15 ^= rotate(x14+x13,18);
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

  store_littleendian(out + 0,x0);
  store_littleendian(out + 4,x1);
  store_littleendian(out + 8,x2);
  store_littleendian(out + 12,x3);
  store_littleendian(out + 16,x4);
  store_littleendian(out + 20,x5);
  store_littleendian(out + 24,x6);
  store_littleendian(out + 28,x7);
  store_littleendian(out + 32,x8);
  store_littleendian(out + 36,x9);
  store_littleendian(out + 40,x10);
  store_littleendian(out + 44,x11);
  store_littleendian(out + 48,x12);
  store_littleendian(out + 52,x13);
  store_littleendian(out + 56,x14);
  store_littleendian(out + 60,x15);

  return 0;
}

int crypto_core_hsalsa20_ref(
        unsigned char *out,
  const unsigned char *in,
  const unsigned char *k,
  const unsigned char *c
)
{
  uint32 x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15;
  uint32 j0, j1, j2, j3, j4, j5, j6, j7, j8, j9, j10, j11, j12, j13, j14, j15;
  int i;

  j0 = x0 = load_littleendian(c + 0);
  j1 = x1 = load_littleendian(k + 0);
  j2 = x2 = load_littleendian(k + 4);
  j3 = x3 = load_littleendian(k + 8);
  j4 = x4 = load_littleendian(k + 12);
  j5 = x5 = load_littleendian(c + 4);
  j6 = x6 = load_littleendian(in + 0);
  j7 = x7 = load_littleendian(in + 4);
  j8 = x8 = load_littleendian(in + 8);
  j9 = x9 = load_littleendian(in + 12);
  j10 = x10 = load_littleendian(c + 8);
  j11 = x11 = load_littleendian(k + 16);
  j12 = x12 = load_littleendian(k + 20);
  j13 = x13 = load_littleendian(k + 24);
  j14 = x14 = load_littleendian(k + 28);
  j15 = x15 = load_littleendian(c + 12);

  for (i = ROUNDS;i > 0;i -= 2) {
     x4 ^= rotate( x0+x12, 7);
     x8 ^= rotate( x4+ x0, 9);
    x12 ^= rotate( x8+ x4,13);
     x0 ^= rotate(x12+ x8,18);
     x9 ^= rotate( x5+ x1, 7);
    x13 ^= rotate( x9+ x5, 9);
     x1 ^= rotate(x13+ x9,13);
     x5 ^= rotate( x1+x13,18);
    x14 ^= rotate(x10+ x6, 7);
     x2 ^= rotate(x14+x10, 9);
     x6 ^= rotate( x2+x14,13);
    x10 ^= rotate( x6+ x2,18);
     x3 ^= rotate(x15+x11, 7);
     x7 ^= rotate( x3+x15, 9);
    x11 ^= rotate( x7+ x3,13);
    x15 ^= rotate(x11+ x7,18);
     x1 ^= rotate( x0+ x3, 7);
     x2 ^= rotate( x1+ x0, 9);
     x3 ^= rotate( x2+ x1,13);
     x0 ^= rotate( x3+ x2,18);
     x6 ^= rotate( x5+ x4, 7);
     x7 ^= rotate( x6+ x5, 9);
     x4 ^= rotate( x7+ x6,13);
     x5 ^= rotate( x4+ x7,18);
    x11 ^= rotate(x10+ x9, 7);
     x8 ^= rotate(x11+x10, 9);
     x9 ^= rotate( x8+x11,13);
    x10 ^= rotate( x9+ x8,18);
    x12 ^= rotate(x15+x14, 7);
    x13 ^= rotate(x12+x15, 9);
    x14 ^= rotate(x13+x12,13);
    x15 ^= rotate(x14+x13,18);
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

  x0 -= load_littleendian(c + 0);
  x5 -= load_littleendian(c + 4);
  x10 -= load_littleendian(c + 8);
  x15 -= load_littleendian(c + 12);
  x6 -= load_littleendian(in + 0);
  x7 -= load_littleendian(in + 4);
  x8 -= load_littleendian(in + 8);
  x9 -= load_littleendian(in + 12);

  store_littleendian(out + 0,x0);
  store_littleendian(out + 4,x5);
  store_littleendian(out + 8,x10);
  store_littleendian(out + 12,x15);
  store_littleendian(out + 16,x6);
  store_littleendian(out + 20,x7);
  store_littleendian(out + 24,x8);
  store_littleendian(out + 28,x9);

  return 0;
}

int crypto_stream_salsa20_ref_xor(
        unsigned char *c,
  const unsigned char *m,unsigned long long mlen,
  const unsigned char *n,
  const unsigned char *k
)
{
  unsigned char in[16];
  unsigned char block[64];
  int i;
  unsigned int u;

  if (!mlen) return 0;

  for (i = 0;i < 8;++i) in[i] = n[i];
  for (i = 8;i < 16;++i) in[i] = 0;

  while (mlen >= 64) {
    crypto_core_salsa20_ref(block,in,k,sigma);
    for (i = 0;i < 64;++i) c[i] = m[i] ^ block[i];

    u = 1;
    for (i = 8;i < 16;++i) {
      u += (unsigned int) in[i];
      in[i] = u;
      u >>= 8;
    }

    mlen -= 64;
    c += 64;
    m += 64;
  }

  if (mlen) {
    crypto_core_salsa20_ref(block,in,k,sigma);
    for (i = 0;i < mlen;++i) c[i] = m[i] ^ block[i];
  }
  return 0;
}

int crypto_stream_salsa20_ref_xor_skip32(
		unsigned char *c0,
        unsigned char *c,unsigned long coffset,
  const unsigned char *m,unsigned long moffset,
  unsigned long long mlen,
  const unsigned char *n,
  const unsigned char *k
)
{
  unsigned char in[16];
  unsigned char blk1[64];
  unsigned char blk2[64];
  unsigned char *prevblock;
  unsigned char *curblock;
  unsigned char *tmpblock;
  int i;
  unsigned int u;

  if (!mlen) return 0;

  for (i = 0;i < 8;++i) in[i] = n[i];
  for (i = 8;i < 16;++i) in[i] = 0;

  prevblock = blk1;
  curblock = blk2;
  crypto_core_salsa20_ref(prevblock,in,k,sigma);
  if (c0 != NULL) {
	for (i = 0; i < 32; i++) c0[i] = prevblock[i];
  }

  while (mlen >= 64) {
	u = 1;
    for (i = 8;i < 16;++i) {
      u += (unsigned int) in[i];
      in[i] = u;
      u >>= 8;
    }

    crypto_core_salsa20_ref(curblock,in,k,sigma);

	for (i = 0;i < 32;++i) c[i+coffset] = m[i+moffset] ^ prevblock[i+32];
	for (i = 32;i < 64;++i) c[i+coffset] = m[i+moffset] ^ curblock[i-32];

    mlen -= 64;
    c += 64;
    m += 64;

	tmpblock = prevblock;
	prevblock = curblock;
	curblock = tmpblock;
  }

  if (mlen) {
	u = 1;
    for (i = 8;i < 16;++i) {
      u += (unsigned int) in[i];
      in[i] = u;
      u >>= 8;
    }

    crypto_core_salsa20_ref(curblock,in,k,sigma);
    for (i = 0;i < mlen && i < 32;++i) c[i+coffset] = m[i+moffset] ^ prevblock[i+32];
    for (i = 32;i < mlen && i < 64;++i) c[i+coffset] = m[i+moffset] ^ curblock[i-32];
  }
  return 0;
}

int crypto_stream_salsa20_ref(
        unsigned char *c,unsigned long long clen,
  const unsigned char *n,
  const unsigned char *k
)
{
  unsigned char in[16];
  unsigned char block[64];
  int i;
  unsigned int u;

  if (!clen) return 0;

  for (i = 0;i < 8;++i) in[i] = n[i];
  for (i = 8;i < 16;++i) in[i] = 0;

  while (clen >= 64) {
    crypto_core_salsa20_ref(c,in,k,sigma);

    u = 1;
    for (i = 8;i < 16;++i) {
      u += (unsigned int) in[i];
      in[i] = u;
      u >>= 8;
    }

    clen -= 64;
    c += 64;
  }

  if (clen) {
    crypto_core_salsa20_ref(block,in,k,sigma);
    for (i = 0;i < clen;++i) c[i] = block[i];
  }
  return 0;
}
