//! This scheme uses PBKDF2 for the key derivation and XSalsa20 unauthenticated encryption with a custom
//! integrity check.
//!
//! The encrypted backup is a grouped base32 encoded value, such as:
//!
//! 4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-
//! WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6
//!
//! (without line breaks)
use libthreema_macros::concat_fixed_bytes;
use subtle::ConstantTimeEq as _;

use super::{BackupKey, IdentityBackupData, IdentityBackupError, Salt};
use crate::crypto::{
    cipher::{KeyIvInit as _, StreamCipher as _},
    deprecated::{pbkdf2::pbkdf2_hmac_array, salsa20::XSalsa20},
    digest::Digest as _,
    sha2::Sha256,
};

const HASH_LENGTH: usize = 2;
const BACKUP_KEY_LENGTH: usize = 32;
const PBKDF_ITERATIONS: u32 = 100_000;

// Fixed zero nonce
const NONCE: [u8; 24] = [0; 24];

/// Length of the encrypted ID backup after stripping extra characters and decoding Base32.
///
/// ```text
/// salt || Salsa20(Threema ID || CK || hash)
/// ```
pub(super) const ENCRYPTED_LENGTH: usize = Salt::LENGTH + IdentityBackupData::LENGTH + HASH_LENGTH;

/// Derive the symmetric backup encryption key
fn derive_key(password: &str, salt: Salt) -> BackupKey {
    BackupKey(pbkdf2_hmac_array::<Sha256, BACKUP_KEY_LENGTH>(
        password.as_bytes(),
        &salt.0,
        PBKDF_ITERATIONS,
    ))
}

/// Return the first two bytes of the SHA256 hash of the concatenation of the Threema ID and the client key.
fn get_digest(data: &[u8]) -> [u8; HASH_LENGTH] {
    let hash = Sha256::new().chain_update(data).finalize();
    hash.get(..HASH_LENGTH)
        .expect("SHA-256 hash should have at least 2 bytes")
        .try_into()
        .expect("SHA-256 hash should have at least 2 bytes")
}

pub(super) fn encrypt(password: &str, backup_data: &IdentityBackupData) -> [u8; ENCRYPTED_LENGTH] {
    // Encode backup data
    let mut backup_data: [u8; IdentityBackupData::LENGTH + HASH_LENGTH] = {
        let backup_data = backup_data.encode();
        concat_fixed_bytes!(backup_data, get_digest(&backup_data))
    };

    // Encrypt backup data
    let salt = Salt::random();
    let key = derive_key(password, salt);

    // XOR with keystream
    XSalsa20::new(&key.0.into(), &NONCE.into()).apply_keystream(&mut backup_data);

    // Prepend the salt to the ciphertext
    concat_fixed_bytes!(salt.0, backup_data)
}

pub(super) fn decrypt(
    password: &str,
    mut encrypted_backup: Vec<u8>,
) -> Result<IdentityBackupData, IdentityBackupError> {
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
                .get_mut(Salt::LENGTH..Salt::LENGTH + IdentityBackupData::LENGTH + HASH_LENGTH)
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
            .get(..IdentityBackupData::LENGTH)
            .expect("Unable to extract backup data"),
        decrypted_data
            .get(IdentityBackupData::LENGTH..)
            .expect("Unable to extract backup data integrity hash"),
    );

    // Verify backup data integrity
    let computed_hash = get_digest(backup_data);
    if bool::from(computed_hash.ct_ne(extracted_hash)) {
        return Err(IdentityBackupError::DecryptionFailed);
    }

    // Decode backup data
    let backup_data = IdentityBackupData::decode(backup_data)?;

    // Done
    Ok(backup_data)
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

    const PASSWORD: &str = "testpassword";

    #[test]
    fn constants() {
        assert_eq!(ENCRYPTED_LENGTH, 50);
    }

    #[test]
    fn invalid_length() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-WLEU-5DS4-XF7S-OPH4-CTCL-N2CF"
                )
                .unwrap()
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
                    "764M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
                    WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6"
                )
                .unwrap()
            ),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn invalid_hash() {
        assert_matches!(
            decrypt(
                PASSWORD,
                decode_chunked_base32(
                    "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
                    WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3X7"
                )
                .unwrap()
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
                    "4K4M-5Q6T-KFUH-L735-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
                    WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6"
                )
                .unwrap()
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

        let encrypted_backup = encrypt(PASSWORD, &backup_data);
        assert_matches!(
            decrypt(PASSWORD, encrypted_backup.to_vec()),
            Err(IdentityBackupError::DecodingFailed(_))
        );
    }

    #[test]
    fn invalid_password() {
        let backup_data = backup_data();

        let encrypted_backup = encrypt(PASSWORD, &backup_data);
        assert_matches!(
            decrypt("nopedinope", encrypted_backup.to_vec()),
            Err(IdentityBackupError::DecryptionFailed)
        );
    }

    #[test]
    fn roundtrip() -> anyhow::Result<()> {
        let backup_data = backup_data();

        let encrypted_backup = encrypt(PASSWORD, &backup_data);
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

        let encrypted_backup = "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-\
            WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6";
        let decrypted_backup = decrypt(PASSWORD, decode_chunked_base32(encrypted_backup)?)?;

        assert_eq!(decrypted_backup.threema_id, backup_data.threema_id);
        assert_eq!(
            decrypted_backup.client_key.as_bytes(),
            backup_data.client_key.as_bytes(),
        );

        Ok(())
    }
}
