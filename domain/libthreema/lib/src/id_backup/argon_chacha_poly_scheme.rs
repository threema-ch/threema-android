//! This scheme uses Argon2id for the key derivation and ChaCha20Poly1305 for authenticated
//! encryption.
//!
//! The encrypted backup is a grouped base32 encoded value, such as:
//!
//! AFSZ-4HG5-GUAK-N7WG-JRZB-EH36-64DH-YOKP-LL5G-O3JS-O3DW-S3QL-BUT5-
//! 7IG5-GGM5-TMMD-2DDC-BDLF-PYNN-PQDS-PN5C-ZRAU-UDMR-IZ3H-X6ZD-TDS4
//!
//! (without line breaks)

use libthreema_macros::concat_fixed_bytes;

use super::{
    decode_backup_data, BackupData, BackupKey, BackupVersion, IdentityBackupError,
    IdentityBackupResult, Salt,
};
use crate::{
    common::{ClientKey, ThreemaId},
    crypto::{
        aead::AeadInPlace,
        argon2::{Argon2, Argon2id, Params},
        chacha20::ChaCha20Poly1305,
        cipher::KeyInit,
    },
};

// Authentication tag length
const TAG_LENGTH: usize = 16;

// Fixed zero nonce
const NONCE: [u8; 12] = [0; 12];

// Threema ID || CK
const DATA_LENGTH: usize = ThreemaId::LENGTH + ClientKey::LENGTH;

// version || salt
const ASSOCIATED_DATA_LENGTH: usize = size_of::<BackupVersion>() + Salt::LENGTH;

// ChaCha20-Poly1305(Threema ID || CK)
const ENCRYPTED_DATA_LENGTH: usize = DATA_LENGTH + TAG_LENGTH;

// version || salt || ChaCha20-Poly1305(Threema ID || CK)
const ENCRYPTED_LENGTH: usize = ASSOCIATED_DATA_LENGTH + ENCRYPTED_DATA_LENGTH;

/// Derive the symmetric backup encryption key
fn derive_key(password: &str, salt: Salt) -> IdentityBackupResult<BackupKey> {
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

pub(super) fn encrypt(
    password: &str,
    backup_data: &BackupData,
) -> IdentityBackupResult<[u8; ENCRYPTED_LENGTH]> {
    // Encode backup data
    let backup_data: [u8; DATA_LENGTH] = concat_fixed_bytes!(
        *backup_data.threema_id.as_bytes(),
        *backup_data.ck.as_bytes()
    );
    let mut backup_data = backup_data.to_vec();

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
) -> IdentityBackupResult<BackupData> {
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
    decode_backup_data(
        backup_data
            .try_into()
            .map_err(|_| IdentityBackupError::DecodingFailed("Invalid length of backup data"))?,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::id_backup::tests::{backup_data, PASSWORD};

    #[test]
    fn test_constants() {
        assert_eq!(DATA_LENGTH, 40);
        assert_eq!(ASSOCIATED_DATA_LENGTH, 9);
        assert_eq!(ENCRYPTED_DATA_LENGTH, 56);
        assert_eq!(ENCRYPTED_LENGTH, 65);
    }

    #[test]
    fn test_roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encrypt(PASSWORD, &backup_data)?;
        let decrypted_backup = decrypt(PASSWORD, encrypted_backup.to_vec())?;

        assert_eq!(backup_data.threema_id, decrypted_backup.threema_id);
        assert_eq!(backup_data.ck.as_bytes(), decrypted_backup.ck.as_bytes());

        Ok(())
    }
}
