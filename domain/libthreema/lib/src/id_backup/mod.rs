//! Implementation of the Threema ID Backup
use core::str;

use data_encoding::BASE32;
use libthreema_macros::concat_fixed_bytes;
use rand::RngCore as _;
use zeroize::ZeroizeOnDrop;

use crate::common::{ThreemaId, keys::ClientKey};

mod argon_chacha_poly_scheme;
mod legacy_scheme;

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

    /// The encryption failed due to an internal error.
    #[error("Encryption failed")]
    EncryptionFailed,
}

#[derive(ZeroizeOnDrop)]
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

/// ID backup version
#[derive(Debug, PartialEq, Eq, strum::FromRepr)]
#[repr(u8)]
pub enum BackupVersion {
    /// Legacy PBKDF2 scheme using XSalsa20 and a custom integrity hash
    Legacy = 0x00,

    /// Argon2id ChaCha20Poly1305 scheme V1
    ArgonChachaPolyV1 = 0x01,
}

/// All information that is stored or can be derived from the ID backup.
#[derive(Debug)]
pub struct IdentityBackupData {
    /// The user's identity.
    pub threema_id: ThreemaId,

    /// Client key.
    pub client_key: ClientKey,
}
impl IdentityBackupData {
    // Encoded length
    const LENGTH: usize = ThreemaId::LENGTH + ClientKey::LENGTH;

    fn decode(backup_data: &[u8]) -> Result<Self, IdentityBackupError> {
        let (threema_id, client_key) = backup_data
            .split_at_checked(ThreemaId::LENGTH)
            .ok_or(IdentityBackupError::DecodingFailed("Invalid backup data length"))?;
        let threema_id = ThreemaId::try_from(threema_id).map_err(|_| {
            IdentityBackupError::DecodingFailed("Invalid Threema ID in decrypted backup data")
        })?;
        let client_key = <[u8; ClientKey::LENGTH]>::try_from(client_key)
            .map_err(|_| IdentityBackupError::DecodingFailed("Invalid CK length in decrypted backup data"))?;
        Ok(IdentityBackupData {
            threema_id,
            client_key: ClientKey::from(client_key),
        })
    }

    fn encode(&self) -> [u8; Self::LENGTH] {
        concat_fixed_bytes!(self.threema_id.to_bytes(), *self.client_key.as_bytes())
    }
}

#[cfg(test)]
impl PartialEq for IdentityBackupData {
    fn eq(&self, other: &Self) -> bool {
        self.threema_id == other.threema_id && self.client_key.as_bytes() == other.client_key.as_bytes()
    }
}

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
fn decode_chunked_base32(encrypted_backup: &str) -> Result<Vec<u8>, IdentityBackupError> {
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

/// Encrypt an [`IdentityBackupData`] with the provided password.
///
/// This automatically uses the best available encryption scheme.
///
/// # Errors
///
/// Returns [`IdentityBackupError`] if the backup data could not be encrypted, most likely due to an internal
/// error.
#[expect(
    clippy::unnecessary_wraps,
    reason = "Result needed when switching to new scheme"
)]
pub fn encrypt_identity_backup(
    password: &str,
    backup_data: &IdentityBackupData,
) -> Result<String, IdentityBackupError> {
    // Encrypt using the legacy scheme for now
    let encrypted_backup = legacy_scheme::encrypt(password, backup_data);

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
pub fn decrypt_identity_backup(
    password: &str,
    encrypted_backup: &str,
) -> Result<(BackupVersion, IdentityBackupData), IdentityBackupError> {
    // All variants use Base32 with every 4th character separated by a dash, so decode it first.
    let encrypted_backup = decode_chunked_base32(encrypted_backup)?;

    // Determine the scheme and decrypt
    if encrypted_backup.len() == legacy_scheme::ENCRYPTED_LENGTH {
        let backup_data = legacy_scheme::decrypt(password, encrypted_backup)?;
        Ok((BackupVersion::Legacy, backup_data))
    } else {
        let version = encrypted_backup
            .first()
            .ok_or(IdentityBackupError::DecodingFailed("Encrypted backup empty"))?;
        let version =
            BackupVersion::from_repr(*version).ok_or(IdentityBackupError::UnknownVersion(*version))?;
        let backup_data = match version {
            BackupVersion::Legacy => Err(IdentityBackupError::DecodingFailed(
                "Unexpected legacy version in backup",
            )),
            BackupVersion::ArgonChachaPolyV1 => argon_chacha_poly_scheme::decrypt(password, encrypted_backup),
        }?;
        Ok((version, backup_data))
    }
}

#[cfg(feature = "slow_tests")]
#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use data_encoding::HEXLOWER;

    use super::*;

    const PASSWORD: &str = "ThisIsABadPassword";

    pub(crate) fn backup_data() -> IdentityBackupData {
        let threema_id = ThreemaId::try_from("0ZAHXXHB").expect("Threema ID should be valid");
        let client_key = {
            let client_key = HEXLOWER
                .decode(b"255d619ebec82341a5abe0b3ff736f900faa3eda1cb86b34f102394f86a41b2c")
                .unwrap();
            let client_key: [u8; ClientKey::LENGTH] = client_key.as_slice().try_into().unwrap();
            ClientKey::from(client_key)
        };
        IdentityBackupData {
            threema_id,
            client_key,
        }
    }

    #[test]
    fn invalid_base32() {
        assert_matches!(
            decrypt_identity_backup(
                PASSWORD,
                "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
                WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S"
            ),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn empty() {
        assert_matches!(
            decrypt_identity_backup(PASSWORD, ""),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn unexpected_explicit_legacy_version() {
        assert_matches!(
            decrypt_identity_backup(PASSWORD, "AD77-7777"),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn unknown_version() {
        assert_matches!(
            decrypt_identity_backup(PASSWORD, "7777-7777"),
            Err(IdentityBackupError::UnknownVersion(0xff))
        );
    }

    #[test]
    fn default_scheme_roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encrypt_identity_backup(PASSWORD, &backup_data)?;
        let (backup_version, decrypted_backup) = decrypt_identity_backup(PASSWORD, &encrypted_backup)?;

        assert_eq!(backup_version, BackupVersion::Legacy);
        assert_eq!(decrypted_backup.threema_id, backup_data.threema_id);
        assert_eq!(
            decrypted_backup.client_key.as_bytes(),
            backup_data.client_key.as_bytes(),
        );

        Ok(())
    }

    #[test]
    fn legacy_static_decrypt() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
            WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6";
        let (backup_version, decrypted_backup) = decrypt_identity_backup("testpassword", encrypted_backup)?;

        assert_eq!(backup_version, BackupVersion::Legacy);
        assert_eq!(decrypted_backup.threema_id, backup_data.threema_id);
        assert_eq!(
            decrypted_backup.client_key.as_bytes(),
            backup_data.client_key.as_bytes(),
        );

        Ok(())
    }

    #[test]
    fn argon_chacha_poly_static_decrypt() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = "AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
            RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL";
        let (backup_version, decrypted_backup) = decrypt_identity_backup(PASSWORD, encrypted_backup)?;

        assert_eq!(backup_version, BackupVersion::ArgonChachaPolyV1);
        assert_eq!(decrypted_backup.threema_id, backup_data.threema_id);
        assert_eq!(
            decrypted_backup.client_key.as_bytes(),
            backup_data.client_key.as_bytes(),
        );

        Ok(())
    }
}
