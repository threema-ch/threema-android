//! Implementation of the Threema ID Backup
use core::str;

use data_encoding::BASE32;
use rand::RngCore as _;
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::common::{ClientKey, ThreemaId};

mod argon_chacha_poly_scheme;
mod legacy_scheme;

#[derive(Zeroize, ZeroizeOnDrop)]
struct BackupKey([u8; BackupKey::LENGTH]);
impl BackupKey {
    const LENGTH: usize = 32;
}

#[derive(Clone, Copy)]
struct Salt([u8; Salt::LENGTH]);
impl Salt {
    const LENGTH: usize = 8;

    fn random() -> Self {
        let mut res = [0; Salt::LENGTH];
        rand::thread_rng().fill_bytes(&mut res);
        Self(res)
    }
}
impl TryFrom<&[u8]> for Salt {
    type Error = IdentityBackupError;

    fn try_from(salt: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self(salt.try_into().map_err(|_| {
            IdentityBackupError::DecodingFailed("Invalid salt length")
        })?))
    }
}

#[derive(strum::FromRepr)]
#[repr(u8)]
enum BackupVersion {
    Legacy = 0x00,
    ArgonChachaPolyV1 = 0x01,
}

/// All information that is stored or can be derived from the backup.
pub struct BackupData {
    /// The Threema ID.
    pub threema_id: ThreemaId,

    /// The Client Key.
    pub ck: ClientKey,
}

#[cfg(test)]
impl PartialEq for BackupData {
    fn eq(&self, other: &Self) -> bool {
        self.threema_id == other.threema_id && self.ck.as_bytes() == other.ck.as_bytes()
    }
}

/// An error occurred while encrypting/decrypting an identity backup
///
/// Note: Errors can occur when using the API incorrectly or when the passed encrypted backups are
/// invalid.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`IdentityBackupError`].
/// 2. Abort the encryption/decryption due to `error`.
#[derive(Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum IdentityBackupError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// The decoding failed.
    #[error("Decoding failed: {0}")]
    DecodingFailed(&'static str),

    /// The decrypted backup scheme version is unknown.
    #[error("Unknown backup scheme version: {0}")]
    UnknownVersion(u8),

    /// The key derivation failed. This is most likely an internal error.
    #[error("Key derivation failed")]
    KdfFailed,

    /// The decryption failed, either due to an internal error or due to an invalid ciphertext.
    #[error("Decryption failed")]
    DecryptionFailed,

    /// The integrity check due to invalid ciphertext.
    #[error("Integrity check failed")]
    IntegrityFailed,

    /// The encryption failed due to an internal error.
    #[error("Encryption failed")]
    EncryptionFailed,
}

/// A wrapper containing an [`Ok`] or an [`IdentityBackupError`].
pub type IdentityBackupResult<T> = Result<T, IdentityBackupError>;

/// Encode the encrypted backup with Base32 and then separate every 4th character by a dash for
/// better readability, i.e., "ABCDEFGH..." becomes "ABCD-EFGH..."
fn encode_chunked_base32(encrypted_backup: &[u8]) -> String {
    let mut chunks = String::with_capacity(encrypted_backup.len().saturating_mul(2));
    for (position, chunk) in BASE32.encode(encrypted_backup).as_bytes().chunks(4).enumerate() {
        if position != 0 {
            // Prepend divider
            chunks.push('-');
        }

        chunks.push_str(str::from_utf8(chunk).expect("Base32 should be ASCII"));
    }

    chunks
}

/// Strip the extra characters that were added for readability and then decode the encrypted backup
/// with Base32.
fn decode_chunked_base32(encrypted_backup: &str) -> IdentityBackupResult<Vec<u8>> {
    BASE32
        .decode(
            encrypted_backup
                .chars()
                .filter(|char| *char != '-')
                .collect::<String>()
                .as_bytes(),
        )
        .map_err(|_| IdentityBackupError::DecodingFailed("Base32 decoding failed"))
}

/// Decode a packed Threema ID, followed by the Client Key (i.e. Threema ID || CK)
fn decode_backup_data(data: [u8; ThreemaId::LENGTH + ClientKey::LENGTH]) -> IdentityBackupResult<BackupData> {
    let (threema_id, client_key) = data
        .split_at_checked(ThreemaId::LENGTH)
        .ok_or(IdentityBackupError::DecodingFailed("Invalid backup data length"))?;
    let threema_id = ThreemaId::try_from(threema_id)
        .map_err(|_| IdentityBackupError::DecodingFailed("Invalid Threema ID in decrypted backup data"))?;
    let ck = <[u8; ClientKey::LENGTH]>::try_from(client_key)
        .map_err(|_| IdentityBackupError::DecodingFailed("Invalid CK length in decrypted backup data"))?;
    Ok(BackupData {
        threema_id,
        ck: ClientKey::from(ck),
    })
}

/// Encrypt a [`BackupData`] with the provided password.
///
/// This automatically uses the best available encryption scheme.
///
/// # Errors
///
/// Returns [`IdentityBackupError`] if the backup data could not be encrypted, most likely due to an
/// internal error.
pub fn encrypt_identity_backup(password: &str, backup_data: &BackupData) -> IdentityBackupResult<String> {
    // Encrypt using the newest scheme
    let encrypted_backup = argon_chacha_poly_scheme::encrypt(password, backup_data)?;

    // Encode the encrypted backup with Base32 and then separate every 4th character by a dash for
    // better readability, i.e., "ABCDEFGH..." becomes "ABCD-EFGH..."
    Ok(encode_chunked_base32(&encrypted_backup))
}

/// Decrypt the encrypted backup from the provided password.
///
/// This detects the used backup scheme and handles it accordingly.
///
/// # Errors
///
/// Returns [`IdentityBackupError`] if the backup data could not be decrypted, decoded/parsed or
/// otherwise contained invalid data.
pub fn decrypt_identity_backup(password: &str, encrypted_backup: &str) -> IdentityBackupResult<BackupData> {
    // All variants use Base32 with every 4th character separated by a dash, so decode it first.
    let mut encrypted_backup = decode_chunked_base32(encrypted_backup)?;

    // Determine the scheme and decrypt
    if encrypted_backup.len() == legacy_scheme::ENCRYPTED_LENGTH {
        legacy_scheme::decrypt(password, &mut encrypted_backup)
    } else {
        let version = encrypted_backup
            .first()
            .ok_or(IdentityBackupError::DecodingFailed(
                "Encrypted backup contained zero bytes",
            ))?;
        let version =
            BackupVersion::from_repr(*version).ok_or(IdentityBackupError::UnknownVersion(*version))?;
        match version {
            BackupVersion::Legacy => Err(IdentityBackupError::DecodingFailed(
                "Unexpected version in legacy backup",
            )),
            BackupVersion::ArgonChachaPolyV1 => argon_chacha_poly_scheme::decrypt(password, encrypted_backup),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    pub(crate) const PASSWORD: &str = "ThisIsABadPassword";

    pub(crate) fn backup_data() -> BackupData {
        let threema_id = ThreemaId::try_from("MYTESTID").expect("Threema ID should be valid");
        let ck = ClientKey::from([0x8; 32]);
        BackupData { threema_id, ck }
    }

    #[test]
    fn test_default_roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encrypt_identity_backup(PASSWORD, &backup_data)?;
        let decrypted_backup = decrypt_identity_backup(PASSWORD, &encrypted_backup)?;

        assert_eq!(backup_data.threema_id, decrypted_backup.threema_id);
        assert_eq!(backup_data.ck.as_bytes(), decrypted_backup.ck.as_bytes());

        Ok(())
    }

    #[test]
    fn test_legacy_roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encode_chunked_base32(&legacy_scheme::encrypt(PASSWORD, &backup_data));
        let decrypted_backup = decrypt_identity_backup(PASSWORD, &encrypted_backup)?;

        assert_eq!(backup_data.threema_id, decrypted_backup.threema_id);
        assert_eq!(backup_data.ck.as_bytes(), decrypted_backup.ck.as_bytes());

        Ok(())
    }
}
