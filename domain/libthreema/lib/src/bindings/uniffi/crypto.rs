//! High-level crypto bindings.
use std::sync::{Arc, Mutex};

use duplicate::duplicate_item;
use rand::Rng as _;

use crate::{
    common::{
        Nonce,
        keys::{RemoteSecret, WonkyFieldCipherKey},
    },
    crypto::{
        argon2, blake2b, chacha20,
        chunked::{self, InvalidTag},
        deprecated::scrypt,
        salsa20, sha2, x25519,
    },
    utils::sync::MutexIgnorePoison as _,
};

/// A general crypto-related error.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CryptoError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),

    /// Unable to encrypt/decrypt.
    #[error("Unable to encrypt/decrypt")]
    CipherFailed,
}

impl From<InvalidTag> for CryptoError {
    fn from(_: InvalidTag) -> Self {
        CryptoError::CipherFailed
    }
}

/// Compute the SHA-256 hash of the provided data.
#[must_use]
#[uniffi::export]
pub fn sha256(data: &[u8]) -> Vec<u8> {
    use crate::crypto::digest::{Digest as _, FixedOutput as _};

    sha2::Sha256::new().chain_update(data).finalize_fixed().to_vec()
}

/// Compute the HMAC-SHA256 from the provided key and data.
#[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
#[must_use]
#[uniffi::export]
pub fn hmac_sha256(key: &[u8], data: &[u8]) -> Vec<u8> {
    use crate::crypto::digest::{FixedOutput as _, Mac as _};

    sha2::HmacSha256::new_from_slice(key)
        .expect("HMAC can take key of any size")
        .chain_update(data)
        .finalize_fixed()
        .to_vec()
}

/// Derive a Blake2b MAC of length 256 bits (32 bytes) of the data from the provided key, personal and salt.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is present and less than 1 or more than 128
/// bytes and when `personal` or `salt` is more than 32 bytes.
#[expect(clippy::ref_option, reason = "Bindings don't allow to use Option<&Vec<u8>>")]
#[uniffi::export]
pub fn blake2b_mac_256(
    key: &Option<Vec<u8>>,
    personal: &[u8],
    salt: &[u8],
    data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::digest::{FixedOutput as _, Mac as _};

    Ok(
        blake2b::Blake2bMac256::new_with_salt_and_personal(key.as_deref(), salt, personal)
            .map_err(|_| {
                CryptoError::InvalidParameter(
                    "'key' if provided must be between 1 and 128 bytes, 'personal' and 'salt' must be up to \
                     32 bytes"
                        .to_owned(),
                )
            })?
            .chain_update(data)
            .finalize_fixed()
            .to_vec(),
    )
}

/// Derive a Blake2b MAC of length 512 bits (64 bytes) of the data from the provided key, personal and salt.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is present and less than 1 or more than 128
/// bytes and when `personal` or `salt` is more than 32 bytes.
#[expect(clippy::ref_option, reason = "Bindings don't allow to use Option<&Vec<u8>>")]
#[uniffi::export]
pub fn blake2b_mac_512(
    key: &Option<Vec<u8>>,
    personal: &[u8],
    salt: &[u8],
    data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::digest::{FixedOutput as _, Mac as _};

    Ok(
        blake2b::Blake2bMac512::new_with_salt_and_personal(key.as_deref(), salt, personal)
            .map_err(|_| {
                CryptoError::InvalidParameter(
                    "'key' if provided must be between 1 and 128 bytes, 'personal' and 'salt' must be up to \
                     32 bytes"
                        .to_owned(),
                )
            })?
            .chain_update(data)
            .finalize_fixed()
            .to_vec(),
    )
}

/// Parameters for [`argon2id`]
#[derive(Clone, Copy, uniffi::Record)]
pub struct Argon2idParameters {
    /// Memory size in 1 KiB blocks. Between 8*`parallelism` and (2^32)-1.
    pub memory_cost: u32,
    /// Number of iterations. Between 1 and (2^32)-1.
    pub time_cost: u32,
    /// Degree of parallelism. Between 1 and (2^24)-1.
    pub parallelism: u32,
    /// Size of the output in bytes. Between 1 and 64.
    pub output_length: u8,
}
impl TryFrom<Argon2idParameters> for argon2::Params {
    type Error = CryptoError;

    fn try_from(parameters: Argon2idParameters) -> Result<Self, Self::Error> {
        if !(1..=64).contains(&parameters.output_length) {
            return Err(CryptoError::InvalidParameter(
                "output length must be between 1 and 64 bytes".to_owned(),
            ));
        }

        argon2::Params::new(
            parameters.memory_cost,
            parameters.time_cost,
            parameters.parallelism,
            Some(parameters.output_length.into()),
        )
        .map_err(|error| CryptoError::InvalidParameter(error.to_string()))
    }
}

/// Derive a key from the provided password and salt using Argon2id.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if the passed parameters are invalid (see
/// [`Argon2idParameters`] for the requirements).
#[uniffi::export]
pub fn argon2id(
    password: &[u8],
    salt: &[u8],
    parameters: Argon2idParameters,
) -> Result<Vec<u8>, CryptoError> {
    let context = argon2::Argon2::new(argon2::Argon2id, argon2::Version::V0x13, parameters.try_into()?);
    let mut output = vec![0; parameters.output_length as usize];
    context
        .hash_password_into(password, salt, &mut output)
        .map_err(|error| CryptoError::InvalidParameter(error.to_string()))?;

    Ok(output)
}

/// Parameters for [`scrypt()`]
#[derive(uniffi::Record)]
pub struct ScryptParameters {
    /// The logarithm of the CPU/memory cost parameter (aka `N`). Less than 64. The resulting
    /// memory cost will be `2 ^ log_memory_cost` in KiB. For example, `log_memory_cost = 17` would
    /// be 128 MiB.
    pub log_memory_cost: u8,
    /// Block size as multiplicator of 128 bytes (aka `r`). Between 1 and (2^32)-1.
    pub block_size: u32,
    /// Degree of parallelism / amount of threads (aka `p`). Between 1 and (2^32)-1.
    pub parallelism: u32,
    /// Size of the output in bytes. Between 10 and 64.
    pub output_length: u8,
}
impl TryFrom<ScryptParameters> for scrypt::Params {
    type Error = CryptoError;

    fn try_from(parameters: ScryptParameters) -> Result<Self, Self::Error> {
        scrypt::Params::new(
            parameters.log_memory_cost,
            parameters.block_size,
            parameters.parallelism,
            parameters.output_length.into(),
        )
        .map_err(|error| CryptoError::InvalidParameter(error.to_string()))
    }
}

/// Derive a key from the provided password and salt using Scrypt.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if the passed parameters are invalid (see
/// [`ScryptParameters`] for the requirements).
#[uniffi::export]
pub fn scrypt(password: &[u8], salt: &[u8], parameters: ScryptParameters) -> Result<Vec<u8>, CryptoError> {
    let mut output = vec![0; parameters.output_length as usize];
    scrypt::scrypt(password, salt, &parameters.try_into()?, &mut output)
        .map_err(|error| CryptoError::InvalidParameter(error.to_string()))?;

    Ok(output)
}

/// Derive the X25519 public key associated to the provided X25519 secret key.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `secret_key` is not exactly 32 bytes.
#[uniffi::export]
pub fn x25519_derive_public_key(secret_key: &[u8]) -> Result<Vec<u8>, CryptoError> {
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key)
            .map_err(|_| CryptoError::InvalidParameter("'secret_key' must be 32 bytes".to_owned()))?,
    );
    let public_key = x25519::PublicKey::from(&secret_key);
    Ok(public_key.as_bytes().to_vec())
}

/// Derive the X25519 shared secret from the provided X25519 public and secret key.
///
/// Note: The resulting shared secret will be hashed with HSalsa20 to ensure uniform distribution.
/// This is compatible with classic NaCl implementations.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `public_key` or `secret_key` is not exactly 32
/// bytes.
#[uniffi::export]
pub fn x25519_hsalsa20_derive_shared_secret(
    public_key: &[u8],
    secret_key: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    let public_key = x25519::PublicKey::from(
        <[u8; 32]>::try_from(public_key)
            .map_err(|_| CryptoError::InvalidParameter("'public_key' must be 32 bytes".to_owned()))?,
    );
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key)
            .map_err(|_| CryptoError::InvalidParameter("'secret_key' must be 32 bytes".to_owned()))?,
    );
    let shared_secret = x25519::SharedSecretHSalsa20::from(secret_key.diffie_hellman(&public_key));
    Ok(shared_secret.as_bytes().to_vec())
}

/// Encrypt the provided data using XChaCha20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if encryption failed.
#[uniffi::export]
pub fn xchacha20_poly1305_encrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher = chacha20::XChaCha20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes".to_owned()))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes".to_owned()))?;
    cipher
        .encrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Decrypt the provided data using XChaCha20.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if decryption failed.
#[uniffi::export]
pub fn xchacha20_poly1305_decrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher = chacha20::XChaCha20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes".to_owned()))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes".to_owned()))?;
    cipher
        .decrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Encrypt the provided data using XSalsa20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if encryption failed.
#[uniffi::export]
pub fn xsalsa20_poly1305_encrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher = salsa20::XSalsa20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes".to_owned()))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes".to_owned()))?;
    cipher
        .encrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Decrypt the provided data using XSalsa20.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if decryption failed.
#[uniffi::export]
pub fn xsalsa20_poly1305_decrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher = salsa20::XSalsa20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes".to_owned()))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes".to_owned()))?;
    cipher
        .decrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

fn ensure_xdance_key_and_nonce<'data>(
    key: &'data [u8],
    nonce: &'data [u8],
) -> Result<(&'data [u8; 32], &'data [u8; Nonce::LENGTH]), CryptoError> {
    let key: &[u8; 32] = key
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes".to_owned()))?;
    let nonce: &[u8; Nonce::LENGTH] = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes".to_owned()))?;
    Ok((key, nonce))
}

/// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor`].
#[derive(uniffi::Object)]
pub struct ChunkedXChaCha20Poly1305Encryptor(Mutex<Option<chunked::ChunkedXChaCha20Poly1305Encryptor>>);

/// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor`].
#[derive(uniffi::Object)]
pub struct ChunkedXChaCha20Poly1305Decryptor(Mutex<Option<chunked::ChunkedXChaCha20Poly1305Decryptor>>);

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Encryptor ];
    [ ChunkedXChaCha20Poly1305Decryptor ];
)]
#[uniffi::export]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::new`] /
    /// [`chunked::ChunkedXChaCha20Poly1305Decryptor::new`].
    ///
    /// # Errors
    ///
    /// Returns an error if `key` is not exactly 32 bytes or `nonce` is not exactly 24 bytes.
    #[uniffi::constructor]
    pub fn new(key: &[u8], nonce: &[u8], associated_data: &[u8]) -> Result<Self, CryptoError> {
        let (key, nonce) = ensure_xdance_key_and_nonce(key, nonce)?;
        Ok(Self(Mutex::new(Some(chunked::cipher_name::new(
            key,
            nonce,
            associated_data,
        )))))
    }
}

/// Binding version of [`chunked::ChunkedXSalsa20Poly1305Encryptor`].
#[derive(uniffi::Object)]
pub struct ChunkedXSalsa20Poly1305Encryptor(Mutex<Option<chunked::ChunkedXSalsa20Poly1305Encryptor>>);

/// Binding version of [`chunked::ChunkedXSalsa20Poly1305Decryptor`].
#[derive(uniffi::Object)]
pub struct ChunkedXSalsa20Poly1305Decryptor(Mutex<Option<chunked::ChunkedXSalsa20Poly1305Decryptor>>);

#[duplicate_item(
    cipher_name;
    [ ChunkedXSalsa20Poly1305Encryptor ];
    [ ChunkedXSalsa20Poly1305Decryptor ];
)]
#[uniffi::export]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXSalsa20Poly1305Encryptor::new`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::new`].
    ///
    /// # Errors
    ///
    /// Returns an error if `key` is not exactly 32 bytes or `nonce` is not exactly 24 bytes.
    #[uniffi::constructor]
    pub fn new(key: &[u8], nonce: &[u8]) -> Result<Self, CryptoError> {
        let (key, nonce) = ensure_xdance_key_and_nonce(key, nonce)?;
        Ok(Self(Mutex::new(Some(chunked::cipher_name::new(key, nonce)))))
    }
}

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Encryptor ];
    [ ChunkedXSalsa20Poly1305Encryptor ];
)]
#[uniffi::export]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::encrypt`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Encryptor::encrypt`].
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::CipherFailed`] in case of an internal error.
    pub fn encrypt(&self, mut chunk: Vec<u8>) -> Result<Vec<u8>, CryptoError> {
        self.0
            .lock_ignore_poison()
            .as_mut()
            .ok_or(CryptoError::CipherFailed)?
            .encrypt(&mut chunk);
        Ok(chunk)
    }

    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::finalize`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Encryptor::finalize`].
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::CipherFailed`] in case of an internal error.
    pub fn finalize(&self) -> Result<Vec<u8>, CryptoError> {
        Ok(self
            .0
            .lock_ignore_poison()
            .take()
            .ok_or(CryptoError::CipherFailed)?
            .finalize()
            .into())
    }
}

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Decryptor ];
    [ ChunkedXSalsa20Poly1305Decryptor ];
)]
#[uniffi::export]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor::decrypt`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::decrypt`].
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::CipherFailed`] in case of an internal error.
    pub fn decrypt(&self, mut chunk: Vec<u8>) -> Result<Vec<u8>, CryptoError> {
        self.0
            .lock_ignore_poison()
            .as_mut()
            .ok_or(CryptoError::CipherFailed)?
            .decrypt(&mut chunk);
        Ok(chunk)
    }

    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor::finalize_verify`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::finalize_verify`].
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::CipherFailed`] in case of an internal error or in case the tag does not match.
    pub fn finalize_verify(&self, expected_tag: &[u8]) -> Result<(), CryptoError> {
        let expected_tag = expected_tag
            .try_into()
            .map_err(|_| CryptoError::InvalidParameter("'tag' must be 16 bytes".to_owned()))?;
        Ok(self
            .0
            .lock_ignore_poison()
            .take()
            .ok_or(CryptoError::CipherFailed)?
            .finalize_verify(expected_tag)?)
    }
}

/// A wonky field encryptor context created by [`WonkyFieldCipher::encryptor`].
#[derive(uniffi::Record)]
pub struct WonkyFieldEncryptorContext {
    /// The nonce that must be prepended to the ciphertext.
    pub nonce: Vec<u8>,

    /// The chunked encryptor to encrypt the field.
    pub encryptor: Arc<ChunkedXChaCha20Poly1305Encryptor>,
}

/// Wonky field cipher for the wonky database field encryption.
///
/// A word of warning: This should generally be avoided. We only need it since the Threema iOS app would
/// require a large refactoring prior to being able to leverage an encrypted database (`SQLCipher`) like all
/// other Threema apps. Ultimately, that is still our goal but until that is reached, we need this wonky field
/// encryption implementation footgun thingy.
///
/// See [`Self::encryptor()`] and [`Self::decryptor()`] for the encryption resp. decryption flows.
#[derive(uniffi::Object)]
pub struct WonkyFieldCipher {
    key: WonkyFieldCipherKey,
}

#[uniffi::export]
impl WonkyFieldCipher {
    /// Create a new wonky field cipher.
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::InvalidParameter`] if `remote_secret` is not exactly 32 bytes.
    #[uniffi::constructor]
    pub fn new(remote_secret: Vec<u8>) -> Result<Self, CryptoError> {
        let remote_secret = RemoteSecret(
            remote_secret
                .try_into()
                .map_err(|_| CryptoError::InvalidParameter("'remote_secret' must be 32 bytes".to_owned()))?,
        );
        Ok(Self {
            key: remote_secret.wonky_field_cipher_key(),
        })
    }

    /// Generate a new random nonce and instantiate a new [`ChunkedXChaCha20Poly1305Encryptor`].
    ///
    /// The flow to encrypt a database field is as follows:
    ///
    /// 1. Call this function and let `nonce` and `encryptor` as defined in the resulting
    ///    [`WonkyFieldEncryptorContext`].
    /// 2. Let `data` be the database field serialized to bytes.
    /// 3. Let `encrypted_data` be the chunkwise encryption of `data` using the `encryptor`'s
    ///    [`ChunkedXChaCha20Poly1305Encryptor::encrypt()`] method.
    /// 4. Let `tag` be the result of calling the `encryptor`'s
    ///    [`ChunkedXChaCha20Poly1305Encryptor::finalize()`] method.
    /// 5. Compose the encrypted database field by concatenating `nonce`, `encrypted_data`, and `tag`, i.e.,
    ///    `encrypted_database_field = nonce || encrypted_data || tag`.
    #[must_use]
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    pub fn encryptor(&self) -> WonkyFieldEncryptorContext {
        let nonce = {
            let mut nonce = [0; chacha20::NONCE_LENGTH];
            rand::thread_rng().fill(&mut nonce);
            nonce
        };

        WonkyFieldEncryptorContext {
            nonce: nonce.to_vec(),
            encryptor: Arc::new(
                ChunkedXChaCha20Poly1305Encryptor::new(&self.key.0, &nonce, &[])
                    .expect("'key' and 'nonce' should have correct size."),
            ),
        }
    }

    /// Instantiate a new [`ChunkedXChaCha20Poly1305Decryptor`] from the given `nonce`.
    ///
    /// The flow to decrypt an encrypted database field is as follows:
    ///
    /// 1. Parse the encrypted database field (stored as bytes) into `nonce || encrypted_data || tag` where
    ///    `nonce` is 24 bytes long, and `tag` is 16 bytes long.
    /// 2. Let `decryptor` be the result of calling this function with `nonce` as argument.
    /// 3. Let `data` be the chunkwise decryption of `encrypted_data` using the `decryptor`'s
    ///    [`ChunkedXChaCha20Poly1305Decryptor::decrypt()`] method.
    /// 4. Verify the `tag` by calling the `decryptor`'s
    ///    [`ChunkedXChaCha20Poly1305Decryptor::finalize_verify()`] method. Abort if this fails.
    /// 5. Deserialize `data` into the data type of the corresponding database field.
    ///
    /// # Errors
    ///
    /// Returns [`CryptoError::InvalidParameter`] if `nonce` is not exactly 24 bytes.
    pub fn decryptor(&self, nonce: &[u8]) -> Result<ChunkedXChaCha20Poly1305Decryptor, CryptoError> {
        ChunkedXChaCha20Poly1305Decryptor::new(&self.key.0, nonce, &[])
    }
}
