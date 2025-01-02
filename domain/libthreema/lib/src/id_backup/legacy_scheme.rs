#[cfg(test)]
use libthreema_macros::concat_fixed_bytes;

use super::{
    decode_backup_data, BackupData, BackupKey, IdentityBackupError, IdentityBackupResult, Salt,
};
use crate::{
    common::{ClientKey, ThreemaId},
    crypto::{
        cipher::{KeyIvInit, StreamCipher},
        deprecated::{pbkdf2::pbkdf2_hmac_array, salsa20::XSalsa20},
        sha2::{Digest, Sha256},
    },
};

const HASH_LENGTH: usize = 2;
const BACKUP_KEY_LENGTH: usize = 32;
const PBKDF_ITERATIONS: u32 = 100_000;

// Fixed zero nonce
const NONCE: [u8; 24] = [0; 24];

// Threema ID || CK
const DATA_LENGTH: usize = ThreemaId::LENGTH + ClientKey::LENGTH;

/// Length of the encrypted ID backup after stripping extra characters and decoding Base32.
///
/// ```text
/// salt || Salsa20(Threema ID || CK || hash)
/// ```
pub(super) const ENCRYPTED_LENGTH: usize = Salt::LENGTH + DATA_LENGTH + HASH_LENGTH;

/// Derive the symmetric backup encryption key
fn derive_key(password: &str, salt: Salt) -> BackupKey {
    BackupKey(pbkdf2_hmac_array::<Sha256, BACKUP_KEY_LENGTH>(
        password.as_bytes(),
        &salt.0,
        PBKDF_ITERATIONS,
    ))
}

/// Return the first two bytes of the SHA256 hash of the concatenation of the Threema ID and the
/// Client Key.
fn get_digest(threema_id: ThreemaId, ck: &ClientKey) -> [u8; HASH_LENGTH] {
    let hash = Sha256::new()
        .chain_update(threema_id.as_bytes())
        .chain_update(ck.as_bytes())
        .finalize();
    hash.get(..HASH_LENGTH)
        .expect("SHA-256 hash should have at least 2 bytes")
        .try_into()
        .expect("SHA-256 hash should have at least 2 bytes")
}

#[cfg(test)]
pub(super) fn encrypt(password: &str, backup_data: &BackupData) -> [u8; ENCRYPTED_LENGTH] {
    // Encode backup data
    let mut data: [u8; DATA_LENGTH + HASH_LENGTH] = concat_fixed_bytes!(
        backup_data.threema_id.0,
        *backup_data.ck.as_bytes(),
        get_digest(backup_data.threema_id, &backup_data.ck),
    );

    // Encrypt backup data
    let salt = Salt::random();
    let key = derive_key(password, salt);

    // XOR with keystream
    XSalsa20::new(&key.0.into(), &NONCE.into()).apply_keystream(&mut data);

    // Prepend the salt to the ciphertext
    concat_fixed_bytes!(salt.0, data)
}

pub(super) fn decrypt(
    password: &str,
    encrypted_backup: &mut [u8],
) -> IdentityBackupResult<BackupData> {
    // Extract salt and encrypted data
    let (salt, encrypted_data) = {
        if encrypted_backup.len() != ENCRYPTED_LENGTH {
            return Err(IdentityBackupError::DecodingFailed(
                "Invalid length for legacy backup",
            ));
        }
        (
            Salt::try_from(
                encrypted_backup
                    .get(..Salt::LENGTH)
                    .expect("Unable to extract salt from encrypted backup"),
            )?,
            encrypted_backup
                .get_mut(Salt::LENGTH..Salt::LENGTH + DATA_LENGTH + HASH_LENGTH)
                .expect("Unable to extract encrypted data from encrypted backup"),
        )
    };

    // Decrypt the backup with the extracted salt and hardcoded nonce
    let decrypted_data = {
        let key = derive_key(password, salt);
        XSalsa20::new(&key.0.into(), &NONCE.into()).apply_keystream(encrypted_data);
        encrypted_data
    };
    let (backup_data, extracted_hash) = (
        decrypted_data
            .get(..DATA_LENGTH)
            .expect("Unable to extract backup data"),
        decrypted_data
            .get(DATA_LENGTH..)
            .expect("Unable to extract backup data integrity hash"),
    );

    // Extract backup data
    let backup_data = decode_backup_data(
        backup_data
            .try_into()
            .map_err(|_| IdentityBackupError::DecodingFailed("Invalid length of backup data"))?,
    )?;

    // Verify backup data integrity
    let computed_hash = get_digest(backup_data.threema_id, &backup_data.ck);
    if computed_hash != extracted_hash {
        return Err(IdentityBackupError::IntegrityFailed);
    }

    // Done
    Ok(backup_data)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        common::ClientKeyPublic,
        crypto::x25519::PublicKey,
        id_backup::{
            decode_chunked_base32,
            tests::{backup_data, PASSWORD},
        },
    };

    #[test]
    fn test_constants() {
        assert_eq!(DATA_LENGTH, 40);
        assert_eq!(ENCRYPTED_LENGTH, 50);
    }

    #[test]
    fn test_roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let mut encrypted_backup = encrypt(PASSWORD, &backup_data);
        let decrypted_backup = decrypt(PASSWORD, &mut encrypted_backup)?;

        assert_eq!(backup_data.threema_id, decrypted_backup.threema_id);
        assert_eq!(backup_data.ck.as_bytes(), decrypted_backup.ck.as_bytes());

        Ok(())
    }

    /// Test that decryption of a backup manually generated with the Android client works.
    #[test]
    fn test_generated_backup() -> anyhow::Result<()> {
        let threema_id = ThreemaId::try_from("0ZAHXXHB").expect("Threema ID should be valid");
        let public_key = PublicKey::from([
            0xac, 0xfc, 0xc3, 0x93, 0xc8, 0x80, 0x0a, 0x25, 0xbc, 0x79, 0x2e, 0x52, 0x90, 0x53,
            0xc4, 0xe9, 0x82, 0xb8, 0xad, 0x10, 0x43, 0xfb, 0xf0, 0x73, 0x3b, 0x8c, 0x8c, 0x74,
            0x09, 0x0f, 0x4a, 0x56,
        ]);
        let password = "testpassword";
        let encrypted_backup =
            "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
            WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6";

        let backup_data = decrypt(password, &mut decode_chunked_base32(encrypted_backup)?)?;
        let public_key_derived = ClientKeyPublic::from(&backup_data.ck);

        assert_eq!(threema_id, backup_data.threema_id);
        assert_eq!(public_key, public_key_derived.0);

        Ok(())
    }
}
