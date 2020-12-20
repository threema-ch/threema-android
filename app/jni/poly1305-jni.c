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

int crypto_onetimeauth(unsigned char *out,const unsigned char *in,unsigned long long inlen,const unsigned char *k);
int crypto_onetimeauth_verify(const unsigned char *h,const unsigned char *in,unsigned long long inlen,const unsigned char *k);

JNIEXPORT jint JNICALL Java_com_neilalexander_jnacl_crypto_poly1305_crypto_1onetimeauth_1native(JNIEnv* env, jclass cls,
	jbyteArray outvarr, jint outvoffset, jbyteArray invarr, jint invoffset, jlong inlen, jbyteArray karr)
{
	jbyte outv[16];
	jbyte *inv;
	jbyte k[32];
	int res;

	if ((*env)->GetArrayLength(env, invarr) < (inlen + invoffset)) {
		/* bad length */
		return 1;
	}

	(*env)->GetByteArrayRegion(env, karr, 0, 32, k);

	inv = (*env)->GetPrimitiveArrayCritical(env, invarr, NULL);
	if (inv == NULL)
		return 4;

	res = crypto_onetimeauth((unsigned char *)outv, (unsigned char *)(inv + invoffset), inlen, (unsigned char *)k);

	(*env)->ReleasePrimitiveArrayCritical(env, invarr, inv, JNI_ABORT);

	(*env)->SetByteArrayRegion(env, outvarr, outvoffset, 16, outv);

	return res;
}

JNIEXPORT jint JNICALL Java_com_neilalexander_jnacl_crypto_poly1305_crypto_1onetimeauth_1verify_1native(JNIEnv* env, jclass cls,
	jbyteArray harr, jint hoffset, jbyteArray invarr, jint invoffset, jlong inlen, jbyteArray karr)
{
	jbyte h[16];
	jbyte *inv;
	jbyte k[32];
	int res;

	if ((*env)->GetArrayLength(env, invarr) < (inlen + invoffset)) {
		/* bad length */
		return 1;
	}

	(*env)->GetByteArrayRegion(env, karr, 0, 32, k);
	(*env)->GetByteArrayRegion(env, harr, hoffset, 16, h);

	inv = (*env)->GetPrimitiveArrayCritical(env, invarr, NULL);
	if (inv == NULL)
		return 4;

	res = crypto_onetimeauth_verify((unsigned char *)h, (unsigned char *)(inv + invoffset), inlen, (unsigned char *)k);

	(*env)->ReleasePrimitiveArrayCritical(env, invarr, inv, JNI_ABORT);

	return res;
}

/* Public Domain code copied verbatim from NaCl below */

static void add(unsigned int h[17],const unsigned int c[17])
{
  unsigned int j;
  unsigned int u;
  u = 0;
  for (j = 0;j < 17;++j) { u += h[j] + c[j]; h[j] = u & 255; u >>= 8; }
}

static void squeeze(unsigned int h[17])
{
  unsigned int j;
  unsigned int u;
  u = 0;
  for (j = 0;j < 16;++j) { u += h[j]; h[j] = u & 255; u >>= 8; }
  u += h[16]; h[16] = u & 3;
  u = 5 * (u >> 2);
  for (j = 0;j < 16;++j) { u += h[j]; h[j] = u & 255; u >>= 8; }
  u += h[16]; h[16] = u;
}

static const unsigned int minusp[17] = {
  5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 252
} ;

static void freeze(unsigned int h[17])
{
  unsigned int horig[17];
  unsigned int j;
  unsigned int negative;
  for (j = 0;j < 17;++j) horig[j] = h[j];
  add(h,minusp);
  negative = -(h[16] >> 7);
  for (j = 0;j < 17;++j) h[j] ^= negative & (horig[j] ^ h[j]);
}

static void mulmod(unsigned int h[17],const unsigned int r[17])
{
  unsigned int hr[17];
  unsigned int i;
  unsigned int j;
  unsigned int u;

  for (i = 0;i < 17;++i) {
    u = 0;
    for (j = 0;j <= i;++j) u += h[j] * r[i - j];
    for (j = i + 1;j < 17;++j) u += 320 * h[j] * r[i + 17 - j];
    hr[i] = u;
  }
  for (i = 0;i < 17;++i) h[i] = hr[i];
  squeeze(h);
}

int crypto_verify_16(const unsigned char *x,const unsigned char *y)
{
  unsigned int differentbits = 0;
#define F(i) differentbits |= x[i] ^ y[i];
  F(0)
  F(1)
  F(2)
  F(3)
  F(4)
  F(5)
  F(6)
  F(7)
  F(8)
  F(9)
  F(10)
  F(11)
  F(12)
  F(13)
  F(14)
  F(15)
  return (1 & ((differentbits - 1) >> 8)) - 1;
}

int crypto_onetimeauth(unsigned char *out,const unsigned char *in,unsigned long long inlen,const unsigned char *k)
{
  unsigned int j;
  unsigned int r[17];
  unsigned int h[17];
  unsigned int c[17];

  r[0] = k[0];
  r[1] = k[1];
  r[2] = k[2];
  r[3] = k[3] & 15;
  r[4] = k[4] & 252;
  r[5] = k[5];
  r[6] = k[6];
  r[7] = k[7] & 15;
  r[8] = k[8] & 252;
  r[9] = k[9];
  r[10] = k[10];
  r[11] = k[11] & 15;
  r[12] = k[12] & 252;
  r[13] = k[13];
  r[14] = k[14];
  r[15] = k[15] & 15;
  r[16] = 0;

  for (j = 0;j < 17;++j) h[j] = 0;

  while (inlen > 0) {
    for (j = 0;j < 17;++j) c[j] = 0;
    for (j = 0;(j < 16) && (j < inlen);++j) c[j] = in[j];
    c[j] = 1;
    in += j; inlen -= j;
    add(h,c);
    mulmod(h,r);
  }

  freeze(h);

  for (j = 0;j < 16;++j) c[j] = k[j + 16];
  c[16] = 0;
  add(h,c);
  for (j = 0;j < 16;++j) out[j] = h[j];
  return 0;
}

int crypto_onetimeauth_verify(const unsigned char *h,const unsigned char *in,unsigned long long inlen,const unsigned char *k)
{
  unsigned char correct[16];
  crypto_onetimeauth(correct,in,inlen,k);
  return crypto_verify_16(h,correct);
}
