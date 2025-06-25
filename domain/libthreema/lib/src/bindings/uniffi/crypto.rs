//! High-level crypto bindings.
use crate::{
    common::Nonce,
    crypto::{argon2, blake2b, chacha20, deprecated::scrypt, salsa20, sha2, x25519},
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

/// Derive a Blake2b MAC from the provided key, personal and salt.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is present and less than 1 or more than 64
/// bytes and when `personal` or `salt` is more than 8 bytes.
#[uniffi::export]
pub fn blake2b_mac_256(key: &Option<Vec<u8>>, personal: &[u8], salt: &[u8]) -> Result<Vec<u8>, CryptoError> {
    use crate::crypto::digest::FixedOutput as _;

    let mac = blake2b::Blake2bMac256::new_with_salt_and_personal(key.as_deref(), salt, personal)
        .map_err(|_| {
            CryptoError::InvalidParameter(
                "'key' if provided must be between 1 and 64 bytes, 'personal' and 'salt' must be up to 8 \
                 bytes"
                    .to_owned(),
            )
        })?
        .finalize_fixed();
    Ok(mac.to_vec())
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

/// Parameters for [`scrypt`]
#[derive(uniffi::Record)]
pub struct ScryptParameters {
    /// The logarithm of the CPU/memory cost parameter (aka `N`). Less than 64. The resulting
    /// memory cost will be `2 ^ log_memory_cost` in KiB. For example, `log_memory_cost = 17` would
    /// be 128 MiB.
    pub log_memory_cost: u8,
    /// Block size in KiB (aka `r`). Between 1 and (2^32)-1.
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
