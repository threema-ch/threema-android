//! This scheme uses Argon2id for the key derivation and ChaCha20Poly1305 for authenticated
//! encryption.
//!
//! The encrypted backup is a grouped base32 encoded value, such as:
//!
//! AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-
//! RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL
//!
//! (without line breaks)
#[cfg(all(test, feature = "slow_tests"))]
use libthreema_macros::concat_fixed_bytes;

use super::{BackupKey, BackupVersion, IdentityBackupData, IdentityBackupError, Salt};
use crate::crypto::{
    aead::AeadInPlace as _,
    argon2::{Argon2, Argon2id, Params},
    chacha20::ChaCha20Poly1305,
    cipher::KeyInit as _,
};

// Authentication tag length
const TAG_LENGTH: usize = 16;

// Fixed zero nonce
const NONCE: [u8; 12] = [0; 12];

// version || salt
const ASSOCIATED_DATA_LENGTH: usize = size_of::<BackupVersion>() + Salt::LENGTH;

// ChaCha20-Poly1305(Threema ID || CK)
const ENCRYPTED_DATA_LENGTH: usize = IdentityBackupData::LENGTH + TAG_LENGTH;

// version || salt || ChaCha20-Poly1305(Threema ID || CK)
const ENCRYPTED_LENGTH: usize = ASSOCIATED_DATA_LENGTH + ENCRYPTED_DATA_LENGTH;

/// Derive the symmetric backup encryption key
fn derive_key(password: &str, salt: Salt) -> Result<BackupKey, IdentityBackupError> {
    let mut key = [0; BackupKey::LENGTH];
    Argon2::new(
        Argon2id,
        argon2::Version::V0x13,
        Params::new(
            128 * 1024,              // 128 MiB memory
            8,                       // iterations
            1,                       // No parallelization
            Some(BackupKey::LENGTH), // Output length
        )
        .map_err(|_| IdentityBackupError::KdfFailed)?,
    )
    .hash_password_into(password.as_bytes(), &salt.0, &mut key)
    .map_err(|_| IdentityBackupError::KdfFailed)?;
    Ok(BackupKey(key))
}

#[cfg(all(test, feature = "slow_tests"))]
pub(super) fn encrypt(
    password: &str,
    backup_data: &IdentityBackupData,
) -> Result<[u8; ENCRYPTED_LENGTH], IdentityBackupError> {
    // Encode backup data
    let mut backup_data = backup_data.encode().to_vec();

    // Encrypt backup data
    let salt = Salt::random();
    let key = derive_key(password, salt)?;
    let cipher = ChaCha20Poly1305::new(&key.0.into());
    let associated_data: [u8; ASSOCIATED_DATA_LENGTH] =
        concat_fixed_bytes!([BackupVersion::ArgonChachaPolyV1 as u8], salt.0);
    cipher
        .encrypt_in_place(&NONCE.into(), &associated_data, &mut backup_data)
        .map_err(|_| IdentityBackupError::EncryptionFailed)?;
    let backup_data: [u8; ENCRYPTED_DATA_LENGTH] = backup_data
        .try_into()
        .expect("Invalid length after ChaChaPoly1305 encryption");

    // Prepend the associated data to the ciphertext
    Ok(concat_fixed_bytes!(associated_data, backup_data))
}

pub(super) fn decrypt(
    password: &str,
    mut encrypted_backup: Vec<u8>,
) -> Result<IdentityBackupData, IdentityBackupError> {
    // Extract associated data and encrypted data
    let (associated_data, mut encrypted_data) = {
        if encrypted_backup.len() != ENCRYPTED_LENGTH {
            return Err(IdentityBackupError::DecodingFailed(
                "Invalid length for ArgonChachaPolyV1 backup",
            ));
        }
        let associated_data = encrypted_backup
            .drain(..ASSOCIATED_DATA_LENGTH)
            .collect::<Vec<u8>>();
        (associated_data, encrypted_backup)
    };

    // Decrypt the backup with the extracted associated data and hardcoded nonce
    let backup_data = {
        let key = derive_key(
            password,
            Salt::try_from(
                associated_data
                    .get(size_of::<BackupVersion>()..)
                    .expect("Unable to extract salt from associated data"),
            )?,
        )?;
        ChaCha20Poly1305::new(&key.0.into())
            .decrypt_in_place(&NONCE.into(), &associated_data, &mut encrypted_data)
            .map_err(|_| IdentityBackupError::DecryptionFailed)?;
        encrypted_data
    };

    // Extract backup data
    IdentityBackupData::decode(&backup_data)
}

#[cfg(feature = "slow_tests")]
#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;

    use super::*;
    use crate::{
        common::{ThreemaId, keys::ClientKey},
        id_backup::{decode_chunked_base32, tests::backup_data},
    };

    const PASSWORD: &str = "ThisIsABadPassword";

    #[test]
    fn constants() {
        assert_eq!(ASSOCIATED_DATA_LENGTH, 9);
        assert_eq!(ENCRYPTED_DATA_LENGTH, 56);
        assert_eq!(ENCRYPTED_LENGTH, 65);
    }

    #[test]
    fn invalid_length() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
                    RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL-\
                    OZPR-NZK4-CHVE-LYVL"
                ).unwrap()
            ),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn invalid_salt() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "AHCV-2VN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
                    RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL"
                ).unwrap()
            ),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn invalid_tag() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
                    RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYX7"
                ).unwrap()
            ),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn invalid_content() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "AHCV-YVN5-MZF6-H47E-75DA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
                    RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL"
                ).unwrap()
            ),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn invalid_backup_data() {
        let backup_data = IdentityBackupData {
            threema_id: ThreemaId::predefined(*b"!$%&/()="),
            client_key: ClientKey::from([0_u8; ClientKey::LENGTH]),
        };

        let encrypted_backup = encrypt(PASSWORD, &backup_data).unwrap();
        assert_matches!(
            decrypt(PASSWORD, encrypted_backup.to_vec()),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn invalid_password() {
        let backup_data = backup_data();

        let encrypted_backup = encrypt(PASSWORD, &backup_data).unwrap();
        assert_matches!(
            decrypt("nopedinope", encrypted_backup.to_vec()),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encrypt(PASSWORD, &backup_data)?;
        let decrypted_backup = decrypt(PASSWORD, encrypted_backup.to_vec())?;

        assert_eq!(backup_data.threema_id, decrypted_backup.threema_id);
        assert_eq!(
            backup_data.client_key.as_bytes(),
            decrypted_backup.client_key.as_bytes()
        );

        Ok(())
    }

    #[test]
    fn static_decrypt() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = "AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-\
            RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL";
        let decrypted_backup = decrypt(PASSWORD, decode_chunked_base32(encrypted_backup)?)?;

        assert_eq!(decrypted_backup.threema_id, backup_data.threema_id);
        assert_eq!(
            decrypted_backup.client_key.as_bytes(),
            backup_data.client_key.as_bytes(),
        );

        Ok(())
    }
}
