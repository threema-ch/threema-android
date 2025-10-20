//! High-level crypto bindings.
use duplicate::duplicate_item;
use js_sys::Error;
use serde::{Deserialize, Serialize};
use tsify::Tsify;
use wasm_bindgen::prelude::*;

use crate::{
    common::Nonce,
    crypto::{argon2, blake2b, chacha20, chunked, salsa20, sha2, x25519},
};

/// Compute the SHA-256 hash of the provided data.
#[must_use]
#[wasm_bindgen(js_name = sha256)]
pub fn sha256(data: &[u8]) -> Vec<u8> {
    use crate::crypto::digest::{Digest as _, FixedOutput as _};

    sha2::Sha256::new().chain_update(data).finalize_fixed().to_vec()
}

/// Compute the HMAC-SHA256 from the provided key and data.
#[allow(clippy::missing_panics_doc, reason = "Panic will never happen")]
#[must_use]
#[wasm_bindgen(js_name = hmacSha256)]
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
/// Returns an error if `key` is present and less than 1 or more than 128
/// bytes and when `personal` or `salt` is more than 32 bytes.
#[allow(
    clippy::needless_pass_by_value,
    reason = "Bindings don't allow to use Option<&Vec<u8>>"
)]
#[wasm_bindgen(js_name = blake2bMac256)]
pub fn blake2b_mac_256(
    key: Option<Vec<u8>>,
    personal: &[u8],
    salt: &[u8],
    data: &[u8],
) -> Result<Vec<u8>, Error> {
    use crate::crypto::digest::{FixedOutput as _, Mac as _};

    Ok(
        blake2b::Blake2bMac256::new_with_salt_and_personal(key.as_deref(), salt, personal)
            .map_err(|_| {
                Error::new(
                    "'key' if provided must be between 1 and 128 bytes, 'personal' and 'salt' must be up to \
                     32 bytes",
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
/// Returns an error if `key` is present and less than 1 or more than 128
/// bytes and when `personal` or `salt` is more than 32 bytes.
#[allow(
    clippy::needless_pass_by_value,
    reason = "Bindings don't allow to use Option<&Vec<u8>>"
)]
#[wasm_bindgen(js_name = blake2bMac512)]
pub fn blake2b_mac_512(
    key: Option<Vec<u8>>,
    personal: &[u8],
    salt: &[u8],
    data: &[u8],
) -> Result<Vec<u8>, Error> {
    use crate::crypto::digest::{FixedOutput as _, Mac as _};
    Ok(
        blake2b::Blake2bMac512::new_with_salt_and_personal(key.as_deref(), salt, personal)
            .map_err(|_| {
                Error::new(
                    "'key' if provided must be between 1 and 128 bytes, 'personal' and 'salt' must be up to \
                     32 bytes",
                )
            })?
            .chain_update(data)
            .finalize_fixed()
            .to_vec(),
    )
}

/// Parameters for [`argon2id`]
#[derive(Clone, Copy, Tsify, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
#[tsify(from_wasm_abi, into_wasm_abi)]
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
    type Error = Error;

    fn try_from(parameters: Argon2idParameters) -> Result<Self, Self::Error> {
        if !(1..=64).contains(&parameters.output_length) {
            return Err(Error::new("output length must be between 1 and 64 bytes"));
        }

        // Other parameters are implicitly within this constructor
        argon2::Params::new(
            parameters.memory_cost,
            parameters.time_cost,
            parameters.parallelism,
            Some(parameters.output_length.into()),
        )
        .map_err(|error| Error::new(&error.to_string()))
    }
}

/// Derive a key from the provided password and salt using Argon2id.
///
/// # Errors
///
/// Returns an error if the passed parameters are invalid (see
/// [`Argon2idParameters`] for the requirements).
#[allow(
    clippy::needless_pass_by_value,
    reason = "Bindings don't allow to use &Argon2idParameters"
)]
#[wasm_bindgen(js_name = argon2id)]
pub fn argon2id(password: &[u8], salt: &[u8], parameters: Argon2idParameters) -> Result<Vec<u8>, Error> {
    let context = argon2::Argon2::new(argon2::Argon2id, argon2::Version::V0x13, parameters.try_into()?);
    let mut output = vec![0; parameters.output_length as usize];
    context
        .hash_password_into(password, salt, &mut output)
        .map_err(|error| Error::new(format!("Invalid Argon2id parameters: {error}").as_ref()))?;

    Ok(output)
}

/// Parameters for [`scrypt()`]
#[derive(Clone, Tsify, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
#[tsify(from_wasm_abi, into_wasm_abi)]
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
    type Error = Error;

    fn try_from(parameters: ScryptParameters) -> Result<Self, Self::Error> {
        scrypt::Params::new(
            parameters.log_memory_cost,
            parameters.block_size,
            parameters.parallelism,
            parameters.output_length.into(),
        )
        .map_err(|error| Error::new(&error.to_string()))
    }
}

/// Derive a key from the provided password and salt using Scrypt.
///
/// # Errors
///
/// Returns an error if the passed parameters are invalid (see [`ScryptParameters`] for the
/// requirements).
#[allow(
    clippy::needless_pass_by_value,
    reason = "Bindings don't allow to use &ScryptParameters"
)]
#[wasm_bindgen()]
pub fn scrypt(password: &[u8], salt: &[u8], parameters: ScryptParameters) -> Result<Vec<u8>, Error> {
    let mut output = vec![0; parameters.output_length as usize];
    scrypt::scrypt(password, salt, &parameters.try_into()?, &mut output)
        .map_err(|error| Error::new(format!("Invalid Scrypt Parameters: {error}").as_ref()))?;

    Ok(output)
}

/// Derive the X25519 public key associated to the provided X25519 secret key.
///
/// # Errors
///
/// Returns an error if `secret_key` is not exactly 32 bytes.
#[wasm_bindgen(js_name = x25519DerivePublicKey)]
pub fn x25519_derive_public_key(secret_key: &[u8]) -> Result<Vec<u8>, Error> {
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key).map_err(|_| Error::new("'secret_key' must be 32 bytes"))?,
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
/// Returns an error if `public_key` or `secret_key` is not exactly 32 bytes.
#[wasm_bindgen(js_name = x25519HSalsa20DeriveSharedSecret)]
pub fn x25519_hsalsa20_derive_shared_secret(public_key: &[u8], secret_key: &[u8]) -> Result<Vec<u8>, Error> {
    let public_key = x25519::PublicKey::from(
        <[u8; 32]>::try_from(public_key).map_err(|_| Error::new("'public_key' must be 32 bytes"))?,
    );
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key).map_err(|_| Error::new("'secret_key' must be 32 bytes"))?,
    );
    let shared_secret = x25519::SharedSecretHSalsa20::from(secret_key.diffie_hellman(&public_key));
    Ok(shared_secret.as_bytes().to_vec())
}

/// Encrypt the provided data using XChaCha20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns an error if `key` is not exactly 32 bytes, `nonce` is not exactly 24 bytes, or if
/// encryption failed.
#[wasm_bindgen(js_name = xChaCha20Poly1305Encrypt)]
pub fn xchacha20_poly1305_encrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, Error> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher =
        chacha20::XChaCha20Poly1305::new_from_slice(key).map_err(|_| Error::new("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| Error::new("'nonce' must be 24 bytes"))?;
    cipher
        .encrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| Error::new("Encryption failed"))?;
    Ok(data)
}

/// Decrypt the provided data using XChaCha20.
///
/// # Errors
///
/// Returns an error if `key` is not exactly 32 bytes, `nonce` is not exactly 24 bytes, or if
/// decryption failed.
#[wasm_bindgen(js_name = xChaCha20Poly1305Decrypt)]
pub fn xchacha20_poly1305_decrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, Error> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher =
        chacha20::XChaCha20Poly1305::new_from_slice(key).map_err(|_| Error::new("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| Error::new("'nonce' must be 24 bytes"))?;
    cipher
        .decrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| Error::new("Decryption failed"))?;
    Ok(data)
}

/// Encrypt the provided data using XSalsa20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns an error if `key` is not exactly 32 bytes, `nonce` is not exactly 24 bytes, or if
/// encryption failed.
#[wasm_bindgen(js_name = xSalsa20Poly1305Encrypt)]
pub fn xsalsa20_poly1305_encrypt(key: &[u8], nonce: &[u8], mut data: Vec<u8>) -> Result<Vec<u8>, Error> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher =
        salsa20::XSalsa20Poly1305::new_from_slice(key).map_err(|_| Error::new("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| Error::new("'nonce' must be 24 bytes"))?;
    cipher
        .encrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| Error::new("Encryption failed"))?;
    Ok(data)
}

/// Decrypt the provided data using XSalsa20.
///
/// # Errors
///
/// Returns an error if `key` is not exactly 32 bytes, `nonce` is not exactly 24 bytes, or if
/// decryption failed.
#[wasm_bindgen(js_name = xSalsa20Poly1305Decrypt)]
pub fn xsalsa20_poly1305_decrypt(key: &[u8], nonce: &[u8], mut data: Vec<u8>) -> Result<Vec<u8>, Error> {
    use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _};

    let cipher =
        salsa20::XSalsa20Poly1305::new_from_slice(key).map_err(|_| Error::new("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| Error::new("'nonce' must be 24 bytes"))?;
    cipher
        .decrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| Error::new("Decryption failed"))?;
    Ok(data)
}

fn ensure_xdance_key_and_nonce<'data>(
    key: &'data [u8],
    nonce: &'data [u8],
) -> Result<(&'data [u8; 32], &'data [u8; Nonce::LENGTH]), Error> {
    let key: &[u8; 32] = key.try_into().map_err(|_| Error::new("'key' must be 32 bytes"))?;
    let nonce: &[u8; Nonce::LENGTH] = nonce
        .try_into()
        .map_err(|_| Error::new("'nonce' must be 24 bytes"))?;
    Ok((key, nonce))
}

/// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor`].
#[wasm_bindgen]
pub struct ChunkedXChaCha20Poly1305Encryptor(chunked::ChunkedXChaCha20Poly1305Encryptor);

/// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor`].
#[wasm_bindgen]
pub struct ChunkedXChaCha20Poly1305Decryptor(chunked::ChunkedXChaCha20Poly1305Decryptor);

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Encryptor ];
    [ ChunkedXChaCha20Poly1305Decryptor ];
)]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::new`] /
    /// [`chunked::ChunkedXChaCha20Poly1305Decryptor::new`].
    ///
    /// # Errors
    ///
    /// Returns an error if `key` is not exactly 32 bytes or `nonce` is not exactly 24 bytes.
    pub fn new(key: &[u8], nonce: &[u8], associated_data: &[u8]) -> Result<Self, Error> {
        let (key, nonce) = ensure_xdance_key_and_nonce(key, nonce)?;
        Ok(Self(chunked::cipher_name::new(key, nonce, associated_data)))
    }
}

/// Binding version of [`chunked::ChunkedXSalsa20Poly1305Encryptor`].
#[wasm_bindgen]
pub struct ChunkedXSalsa20Poly1305Encryptor(chunked::ChunkedXSalsa20Poly1305Encryptor);

/// Binding version of [`chunked::ChunkedXSalsa20Poly1305Decryptor`].
#[wasm_bindgen]
pub struct ChunkedXSalsa20Poly1305Decryptor(chunked::ChunkedXSalsa20Poly1305Decryptor);

#[duplicate_item(
    cipher_name;
    [ ChunkedXSalsa20Poly1305Encryptor ];
    [ ChunkedXSalsa20Poly1305Decryptor ];
)]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXSalsa20Poly1305Encryptor::new`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::new`].
    ///
    /// # Errors
    ///
    /// Returns an error if `key` is not exactly 32 bytes or `nonce` is not exactly 24 bytes.
    pub fn new(key: &[u8], nonce: &[u8]) -> Result<Self, Error> {
        let (key, nonce) = ensure_xdance_key_and_nonce(key, nonce)?;
        Ok(Self(chunked::cipher_name::new(key, nonce)))
    }
}

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Encryptor ];
    [ ChunkedXSalsa20Poly1305Encryptor ];
)]
#[wasm_bindgen]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::encrypt`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Encryptor::encrypt`].
    #[must_use]
    #[wasm_bindgen]
    pub fn encrypt(&mut self, mut chunk: Vec<u8>) -> Vec<u8> {
        self.0.encrypt(&mut chunk);
        chunk
    }

    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Encryptor::finalize`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Encryptor::finalize`].
    #[must_use]
    pub fn finalize(self) -> Vec<u8> {
        self.0.finalize().into()
    }
}

#[duplicate_item(
    cipher_name;
    [ ChunkedXChaCha20Poly1305Decryptor ];
    [ ChunkedXSalsa20Poly1305Decryptor ];
)]
#[wasm_bindgen]
impl cipher_name {
    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor::decrypt`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::decrypt`].
    #[must_use]
    #[wasm_bindgen]
    pub fn decrypt(&mut self, mut chunk: Vec<u8>) -> Vec<u8> {
        self.0.decrypt(&mut chunk);
        chunk
    }

    /// Binding version of [`chunked::ChunkedXChaCha20Poly1305Decryptor::finalize_verify`] /
    /// [`chunked::ChunkedXSalsa20Poly1305Decryptor::finalize_verify`].
    ///
    /// # Errors
    ///
    /// Returns an error in case the tag does not match.
    pub fn finalize_verify(self, expected_tag: &[u8]) -> Result<(), Error> {
        let expected_tag = expected_tag
            .try_into()
            .map_err(|_| Error::new("'tag' must be 16 bytes"))?;
        self.0
            .finalize_verify(expected_tag)
            .map_err(|error| Error::new(&error.to_string()))
    }
}
