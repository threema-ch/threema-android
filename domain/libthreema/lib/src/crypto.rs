//! Higher-level wrappers around crypto libraries used and some commonly used abstractions.
//!
//! Note: Always use this module instead of using the crypto dependencies directly!

/// Minimal interface of a generic fixed-size array (used for some ciphers).
pub(crate) mod generic_array {
    pub(crate) use cipher::generic_array::GenericArray;
}

/// Type aliases for all constants used by Threema protocols.
pub(crate) mod consts {
    pub(crate) use cipher::consts::{U24, U32};
}

/// Minimal abstract interface for general ciphers.
pub(crate) mod cipher {
    pub(crate) use cipher::{KeyInit, KeyIvInit, StreamCipher, Unsigned};
}

/// Minimal abstract interface for hash digests.
pub(crate) mod digest {
    pub(crate) use digest::{Digest, FixedOutput, Mac};

    pub(crate) const MAC_256_LENGTH: usize = 32;
}

/// Minimal abstract interface for AEAD ciphers.
pub(crate) mod aead {
    pub(crate) use aead::{AeadInPlace, Buffer, Error};
    use aead::{Nonce, Result};

    use super::cipher::Unsigned as _;
    use crate::utils::bytes::InsertSlice as _;

    // TODO(LIB-31): Use `ByteWriter`?
    pub(crate) trait AeadRandomNonceAhead: AeadInPlace {
        /// Encrypt the given buffer containing a plaintext message in-place and return the used
        /// nonce.
        ///
        /// The buffer must have sufficient capacity to store the random nonce ahead of the
        /// ciphertext message, which will always be larger than the original plaintext. The exact
        /// size needed is cipher-dependent, but generally includes the size of an authentication
        /// tag.
        ///
        /// Returns an error if the buffer has insufficient capacity to store the resulting
        /// ciphertext message.
        fn encrypt_in_place_random_nonce_ahead(
            &self,
            associated_data: &[u8],
            buffer: &mut Vec<u8>,
        ) -> Result<Nonce<Self>>;

        /// Decrypt the message with the random nonce ahead in-place, returning the nonce or an
        /// error in the event the provided authentication tag does not match the given ciphertext.
        ///
        /// The buffer will be truncated to the original plaintext message upon success.
        fn decrypt_in_place_random_nonce_ahead(
            &self,
            associated_data: &[u8],
            buffer: &mut Vec<u8>,
        ) -> Result<Nonce<Self>>;
    }

    impl<Alg: AeadInPlace> AeadRandomNonceAhead for Alg {
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

        fn decrypt_in_place_random_nonce_ahead(
            &self,
            associated_data: &[u8],
            buffer: &mut Vec<u8>,
        ) -> Result<Nonce<Self>> {
            let nonce = Nonce::<Self>::from(buffer.drain(..Self::NonceSize::to_usize()).collect());
            self.decrypt_in_place(&nonce, associated_data, buffer)?;
            Ok(nonce)
        }
    }
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
    pub(crate) use chacha20poly1305::{ChaCha20Poly1305, Key, XChaCha20Poly1305};
}

/// XSalsa20Poly1305 cipher as used by legacy Threema protocols.
pub(crate) mod salsa20 {
    pub(crate) use crypto_secretbox::{Key, XSalsa20Poly1305};

    use crate::utils::bytes;

    pub(crate) const TAG_LENGTH: usize = 16;

    pub(crate) type EncryptedDataRange = bytes::EncryptedDataRange<{ TAG_LENGTH }>;
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

/// X25519-related derivation and keys, including the intermediate X25519HSalsa20 hash on top of a
/// montgomery point as used by many Threema protocols to derive further keys from.
pub(crate) mod x25519 {
    use aead::consts::U10;
    use salsa20::hsalsa;
    pub(crate) use x25519_dalek::{EphemeralSecret, PublicKey, SharedSecret, StaticSecret};
    use zeroize::{Zeroize, ZeroizeOnDrop};

    use super::generic_array::GenericArray;

    pub(crate) const KEY_LENGTH: usize = 32;

    /// A uniformly distributed [`SharedSecret`], compatible with classic NaCl shared secret
    /// derivation.
    #[derive(Zeroize, ZeroizeOnDrop)]
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
