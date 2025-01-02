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
    pub(crate) use cipher::{KeyInit, KeyIvInit, StreamCipher};
}

/// Minimal abstract interface for hash digests.
pub(crate) mod digest {
    pub(crate) use blake2::digest::FixedOutput;
}

/// Minimal abstract interface for AEAD ciphers.
pub(crate) mod aead {
    pub(crate) use aead::{AeadInPlace, Buffer};
}

/// Argon2id for password-based key derivations as used by Threema protocols.
pub(crate) mod argon2 {
    pub(crate) use argon2::{Algorithm::Argon2id, Argon2, Params};
}

/// BLAKE2b for hashing and key derivations as used by Threema protocols.
pub(crate) mod blake2b {
    use blake2::Blake2bMac;

    /// A BLAKE2b MAC, commonly used for key derivations and authentication challenges in Threema
    /// protocols.
    pub(crate) type Blake2bMac256 = Blake2bMac<super::consts::U32>;
}

/// SHA2 for hashing as used by various internal mechanisms in Threema and some legacy Threema
/// protocols.
pub(crate) mod sha2 {
    pub(crate) use sha2::{Digest, Sha256};
}

/// ChaCha20Poly1305 and XChaCha20Poly1305 ciphers as used by modern Threema protocols.
pub(crate) mod chacha20 {
    pub(crate) use chacha20poly1305::{ChaCha20Poly1305, XChaCha20Poly1305};
}

/// XSalsa20Poly1305 cipher as used by legacy Threema protocols.
pub(crate) mod salsa20 {
    pub(crate) use crypto_secretbox::XSalsa20Poly1305;
}

/// Deprecated stuff that only exists for the sake of backwards compatibility.
pub(crate) mod deprecated {
    pub(crate) mod pbkdf2 {
        pub(crate) use pbkdf2::pbkdf2_hmac_array;
    }

    pub(crate) mod salsa20 {
        pub(crate) use salsa20::XSalsa20;
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

    /// A uniformly distributed [`SharedSecret`], compatible with classic NaCl shared secret
    /// derivation.
    #[derive(Zeroize, ZeroizeOnDrop)]
    pub(crate) struct SharedSecretHSalsa20([u8; 32]);

    impl SharedSecretHSalsa20 {
        /// Convert this shared secret key to a byte array.
        #[inline]
        #[must_use]
        pub(crate) fn to_bytes(&self) -> [u8; 32] {
            self.0
        }

        /// View this shared secret key as a byte array.
        #[inline]
        #[must_use]
        pub(crate) fn as_bytes(&self) -> &[u8; 32] {
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
