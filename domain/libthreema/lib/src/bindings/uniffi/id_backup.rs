//! Bindings for the Threema ID backup.
use crate::{
    common::{ClientKey, ThreemaId},
    id_backup::{self, IdentityBackupError},
};

/// Binding-friendly version of [`id_backup::BackupData`].
#[derive(uniffi::Record)]
pub struct BackupData {
    /// The Threema ID (8 bytes).
    pub threema_id: String,

    /// The Client Key (32 bytes).
    pub ck: Vec<u8>,
}

impl From<id_backup::BackupData> for BackupData {
    fn from(backup_data: id_backup::BackupData) -> Self {
        Self {
            threema_id: backup_data.threema_id.into(),
            ck: backup_data.ck.as_bytes().to_vec(),
        }
    }
}

impl TryFrom<BackupData> for id_backup::BackupData {
    type Error = IdentityBackupError;

    fn try_from(backup_data: BackupData) -> Result<Self, Self::Error> {
        let ck: [u8; ClientKey::LENGTH] = backup_data
            .ck
            .try_into()
            .map_err(|_| Self::Error::InvalidParameter("'backup_data.ck' must be 32 bytes"))?;
        Ok(id_backup::BackupData {
            threema_id: ThreemaId::try_from(backup_data.threema_id.as_str())
                .map_err(|_| IdentityBackupError::InvalidParameter("'backup_data.threema_id' invalid"))?,
            ck: ClientKey::from(ck),
        })
    }
}

/// Binding-friendly version of [`id_backup::encrypt_identity_backup`].
///
/// # Errors
///
/// Returns [`IdentityBackupError::InvalidParameter`] if `backup_data.ck` is not exactly 32 bytes.
#[uniffi::export]
pub fn encrypt_identity_backup(
    password: &str,
    backup_data: BackupData,
) -> Result<String, IdentityBackupError> {
    id_backup::encrypt_identity_backup(password, &id_backup::BackupData::try_from(backup_data)?)
}

/// Binding-friendly version of [`id_backup::decrypt_identity_backup`].
#[expect(clippy::missing_errors_doc, reason = "Binding-friendly version")]
#[uniffi::export]
pub fn decrypt_identity_backup(
    password: &str,
    encrypted_backup: &str,
) -> Result<BackupData, IdentityBackupError> {
    id_backup::decrypt_identity_backup(password, encrypted_backup).map(BackupData::from)
}
