//! Higher-level wrappers around crypto libraries used and some commonly used abstractions.
//!
//! Note: Always use this module instead of using the crypto dependencies directly!
pub mod chunked;

/// Minimal interface of a generic fixed-size array (used for some ciphers).
pub(crate) mod generic_array {
    pub(crate) use cipher::generic_array::GenericArray;
}

/// Type aliases for all constants used by Threema protocols.
pub(crate) mod consts {
    pub(crate) use cipher::consts::{U16, U24, U32, U64};
}

/// Minimal abstract interface for general ciphers.
pub(crate) mod cipher {
    pub(crate) use cipher::{KeyInit, KeyIvInit, StreamCipher, StreamCipherSeek, Unsigned};
}

/// Minimal abstract interface for hash digests.
pub(crate) mod digest {
    pub(crate) use digest::{Digest, FixedOutput, Mac, block_buffer::EagerBuffer};

    pub(crate) const MAC_256_LENGTH: usize = 32;
}

/// Minimal utility functions.
pub(crate) mod subtle {
    pub(crate) use subtle::ConstantTimeEq;
}

/// Minimal abstract interface for AEAD ciphers.
pub(crate) mod aead {
    pub(crate) use aead::{AeadInPlace, Buffer, Error};
    use aead::{Nonce, Payload, Result};

    use super::cipher::Unsigned as _;
    use crate::utils::bytes::InsertSlice as _;

    // TODO(LIB-31): Use `ByteWriter`?
    pub(crate) trait AeadRandomNonceAhead: AeadInPlace {
        /// Encrypt the given buffer containing a plaintext message in-place, place a random nonce ahead and
        /// return the used nonce.
        ///
        /// The buffer must have sufficient capacity to store the random nonce ahead of the ciphertext
        /// message, which will always be larger than the original plaintext. The exact size needed is
        /// cipher-dependent, but generally includes the size of an authentication tag.
        ///
        /// Returns an error if the buffer has insufficient capacity to store the resulting ciphertext
        /// message.
        fn encrypt_in_place_random_nonce_ahead(
            &self,
            associated_data: &[u8],
            buffer: &mut Vec<u8>,
        ) -> Result<Nonce<Self>> {
            let nonce = Self::generate_nonce(rand::thread_rng());
            self.encrypt_in_place(&nonce, associated_data, buffer)?;
            buffer.insert_at(0, &nonce);
            Ok(nonce)
        }

        /// Decrypt the message with the random nonce ahead in-place, returning the nonce or an error in the
        /// event the provided authentication tag does not match the given ciphertext.
        ///
        /// The buffer will be truncated to the original plaintext message upon success.
        fn decrypt_in_place_random_nonce_ahead(
            &self,
            associated_data: &[u8],
            buffer: &mut Vec<u8>,
        ) -> Result<Nonce<Self>> {
            if buffer.len() < Self::NonceSize::to_usize() {
                return Err(Error);
            }
            let nonce = Nonce::<Self>::from(buffer.drain(..Self::NonceSize::to_usize()).collect());
            self.decrypt_in_place(&nonce, associated_data, buffer)?;
            Ok(nonce)
        }

        /// Encrypt the given plaintext payload and a random nonce ahead, and return the used nonce along the
        /// resulting ciphertext as a vector of bytes.
        ///
        /// The [`Payload`] type can be used to provide Additional Associated Data (AAD) along with the
        /// message: this is an optional bytestring which is not encrypted, but *is* authenticated along with
        /// the message. Failure to pass the same AAD that was used during encryption will cause decryption to
        /// fail, which is useful if you would like to "bind" the ciphertext to some other identifier, like a
        /// digital signature key or other identifier.
        ///
        /// If you don't care about AAD and just want to encrypt a plaintext message, `&[u8]` will
        /// automatically be coerced into a `Payload`:
        ///
        /// ```nobuild
        /// let plaintext = b"Top secret message, handle with care";
        /// let ciphertext = cipher.encrypt(nonce, plaintext);
        /// ```
        ///
        /// The default implementation assumes a postfix tag (e.g AES-GCM, AES-GCM-SIV, ChaCha20Poly1305).
        /// [`Aead`] implementations which do not use a postfix tag (e.g. Salsa20Poly1305) will need to
        /// override this to correctly assemble the ciphertext message.
        fn encrypt_random_nonce_ahead<'message, 'aad, TPlaintext: Into<Payload<'message, 'aad>>>(
            &self,
            plaintext: TPlaintext,
        ) -> Result<(Nonce<Self>, Vec<u8>)> {
            let payload: Payload<'message, 'aad> = plaintext.into();
            let mut buffer = payload.msg.to_vec();
            let nonce = self.encrypt_in_place_random_nonce_ahead(payload.aad, &mut buffer)?;
            Ok((nonce, buffer))
        }

        /// Decrypt the given ciphertext slice with the random nonce ahead, and return the resulting nonce
        /// along the plaintext as a vector of bytes.
        ///
        /// See notes on [`Aead::encrypt()`] about allowable message payloads and Associated Additional Data
        /// (AAD).
        ///
        /// If you have no AAD, you can call this as follows:
        ///
        /// ```nobuild
        /// let ciphertext = b"...";
        /// let plaintext = cipher.decrypt(nonce, ciphertext)?;
        /// ```
        ///
        /// The default implementation assumes a postfix tag (e.g AES-GCM, AES-GCM-SIV, ChaCha20Poly1305).
        /// [`Aead`] implementations which do not use a postfix tag (e.g. Salsa20Poly1305) will need to
        /// override this to correctly parse the ciphertext message.
        #[expect(dead_code, reason = "May use later")]
        fn decrypt_random_nonce_ahead<'message, 'aad, TCiphertext: Into<Payload<'message, 'aad>>>(
            &self,
            ciphertext: TCiphertext,
        ) -> Result<(Nonce<Self>, Vec<u8>)> {
            let payload: Payload<'message, 'aad> = ciphertext.into();
            let mut buffer = payload.msg.to_vec();
            let nonce = self.decrypt_in_place_random_nonce_ahead(payload.aad, &mut buffer)?;
            Ok((nonce, buffer))
        }
    }

    impl<TAlgorithm: AeadInPlace> AeadRandomNonceAhead for TAlgorithm {}
}

/// Argon2id for password-based key derivations as used by Threema protocols.
pub(crate) mod argon2 {
    pub(crate) use argon2::{Algorithm::Argon2id, Argon2, Params, Version};
}

/// BLAKE2b for hashing and key derivations as used by Threema protocols.
pub(crate) mod blake2b {
    use blake2::Blake2bMac;

    /// A BLAKE2b MAC, commonly used for key derivations and authentication challenges in Threema
    /// protocols.
    pub(crate) type Blake2bMac256 = Blake2bMac<super::consts::U32>;
    pub(crate) type Blake2bMac512 = Blake2bMac<super::consts::U64>;

    pub(crate) const MAC_256_LENGTH: usize = 32;
}

/// SHA2 and HMAC-SHA-2 for hashing as used by various internal mechanisms in Threema and some
/// legacy Threema protocols.
pub(crate) mod sha2 {
    use hmac::Hmac;
    pub(crate) use sha2::Sha256;

    pub(crate) type HmacSha256 = Hmac<Sha256>;
}

/// ChaCha20Poly1305 and XChaCha20Poly1305 ciphers as used by modern Threema protocols.
pub(crate) mod chacha20 {
    pub(crate) use chacha20::XChaCha20;
    pub(crate) use chacha20poly1305::{ChaCha20Poly1305, Key, XChaCha20Poly1305};

    pub(crate) const KEY_LENGTH: usize = 32;
    pub(crate) const NONCE_LENGTH: usize = 24;
    pub(crate) const TAG_LENGTH: usize = 16;
}

/// XSalsa20Poly1305 cipher as used by legacy Threema protocols.
pub(crate) mod salsa20 {
    pub(crate) use crypto_secretbox::{Key, XSalsa20Poly1305};
    pub(crate) use salsa20::XSalsa20;

    use crate::utils::bytes;

    pub(crate) const KEY_LENGTH: usize = 32;
    pub(crate) const NONCE_LENGTH: usize = 24;
    pub(crate) const TAG_LENGTH: usize = 16;

    pub(crate) type EncryptedDataRange = bytes::EncryptedDataRange<{ TAG_LENGTH }>;
}

/// Poly1305 for hashing in combination with XSalsa20/XChaCha20. Should only be used for chunked crypto.
pub(crate) mod poly1305 {
    pub(crate) use poly1305::{Key, Tag};
    use poly1305::{Poly1305, universal_hash::UniversalHash as _};

    use super::cipher::KeyInit as _;

    /// Poly1305 as a MAC construction with an internal buffer handling incomplete blocks.
    pub(crate) struct ChunkedPoly1305 {
        hash: Poly1305,
        buffer: super::digest::EagerBuffer<super::consts::U16>,
    }
    impl ChunkedPoly1305 {
        #[inline]
        pub(crate) fn new(key: &Key) -> Self {
            Self {
                hash: Poly1305::new(key),
                buffer: super::digest::EagerBuffer::default(),
            }
        }

        /// Update with any full blocks and store any leftover bytes for later processing.
        #[inline]
        pub(crate) fn update(&mut self, chunk: &[u8]) {
            // Update the hash from fully constructed blocks and store any leftover bytes in the internal
            // buffer
            self.buffer.digest_blocks(chunk, |blocks| {
                self.hash.update(blocks);
            });
        }
    }

    pub(crate) trait ChunkedPoly1305XChaCha20 {
        /// Zero-pad a _pending_ block.
        ///
        /// If no bytes are in the internal buffer, the block is not considered _pending_. In other words,
        /// this prevents a full block of just zero-padding.
        fn zeropad_pending_block(&mut self);

        /// Hand out the resulting tag.
        ///
        /// WARNING: This disregards any pending blocks. The caller must ensure that all pending blocks have
        /// been written.
        fn finalize_complete_block(self) -> Tag;
    }

    impl ChunkedPoly1305XChaCha20 for ChunkedPoly1305 {
        #[inline]
        fn zeropad_pending_block(&mut self) {
            if self.buffer.get_pos() == 0 {
                return;
            }
            self.hash.update(&[*self.buffer.pad_with_zeros()]);
        }

        #[inline]
        fn finalize_complete_block(self) -> Tag {
            self.hash.finalize()
        }
    }

    pub(crate) trait ChunkedPoly1305XSalsa20 {
        /// Hand out the hash unpadded (yet still hashing the remaining bytes).
        fn finalize_unpadded(self) -> Tag;
    }

    impl ChunkedPoly1305XSalsa20 for ChunkedPoly1305 {
        #[inline]
        fn finalize_unpadded(self) -> Tag {
            self.hash.compute_unpadded(self.buffer.get_data())
        }
    }
}

/// Deprecated stuff that only exists for the sake of backwards compatibility.
pub(crate) mod deprecated {
    pub(crate) mod pbkdf2 {
        pub(crate) use pbkdf2::pbkdf2_hmac_array;
    }

    pub(crate) mod salsa20 {
        pub(crate) use salsa20::XSalsa20;
    }

    pub(crate) mod scrypt {
        pub(crate) use scrypt::{Params, scrypt};
    }
}

// Ed25519-related keys and signatures.
pub(crate) mod ed25519 {
    pub(crate) use ed25519_dalek::{PUBLIC_KEY_LENGTH, SIGNATURE_LENGTH, Signature, VerifyingKey};
}

/// X25519-related derivation and keys, including the intermediate X25519HSalsa20 hash on top of a
/// montgomery point as used by many Threema protocols to derive further keys from.
pub(crate) mod x25519 {
    use aead::consts::U10;
    use salsa20::hsalsa;
    pub(crate) use x25519_dalek::{EphemeralSecret, PublicKey, SharedSecret, StaticSecret};
    use zeroize::ZeroizeOnDrop;

    use super::generic_array::GenericArray;

    pub(crate) const KEY_LENGTH: usize = 32;

    /// A uniformly distributed [`SharedSecret`], compatible with classic NaCl shared secret
    /// derivation.
    #[derive(ZeroizeOnDrop)]
    pub(crate) struct SharedSecretHSalsa20([u8; Self::LENGTH]);

    impl SharedSecretHSalsa20 {
        /// The byte length
        pub(crate) const LENGTH: usize = KEY_LENGTH;

        /// Convert this shared secret key to a byte array.
        #[inline]
        #[must_use]
        pub(crate) fn to_bytes(&self) -> [u8; Self::LENGTH] {
            self.0
        }

        /// View this shared secret key as a byte array.
        #[inline]
        #[must_use]
        pub(crate) fn as_bytes(&self) -> &[u8; Self::LENGTH] {
            &self.0
        }
    }

    impl From<SharedSecret> for SharedSecretHSalsa20 {
        fn from(secret: SharedSecret) -> Self {
            // Use HSalsa20 to create a uniformly random key from the shared secret
            Self(
                hsalsa::<U10>(
                    GenericArray::from_slice(secret.as_bytes()),
                    &GenericArray::default(),
                )
                .into(),
            )
        }
    }
}
