//! Bindings for the Threema ID backup.
use crate::{
    common::{ThreemaId, keys::ClientKey},
    id_backup::{self, IdentityBackupError},
};

/// Binding version of [`id_backup::IdentityBackupData`].
#[derive(uniffi::Record)]
pub struct IdentityBackupData {
    /// The user's identity.
    pub threema_id: String,

    /// Client key (32 bytes).
    pub client_key: Vec<u8>,
}

impl From<id_backup::IdentityBackupData> for IdentityBackupData {
    fn from(backup_data: id_backup::IdentityBackupData) -> Self {
        Self {
            threema_id: backup_data.threema_id.into(),
            client_key: backup_data.client_key.as_bytes().to_vec(),
        }
    }
}

impl TryFrom<IdentityBackupData> for id_backup::IdentityBackupData {
    type Error = IdentityBackupError;

    fn try_from(backup_data: IdentityBackupData) -> Result<Self, Self::Error> {
        let client_key: [u8; ClientKey::LENGTH] = backup_data
            .client_key
            .try_into()
            .map_err(|_| IdentityBackupError::InvalidParameter("'client_key' must be 32 bytes"))?;
        Ok(id_backup::IdentityBackupData {
            threema_id: ThreemaId::try_from(backup_data.threema_id.as_str())
                .map_err(|_| IdentityBackupError::InvalidParameter("'threema_id' invalid"))?,
            client_key: ClientKey::from(client_key),
        })
    }
}

/// Binding version of [`id_backup::encrypt_identity_backup`].
///
/// # Errors
///
/// Returns [`IdentityBackupError::InvalidParameter`] if `backup_data` is invalid.
#[uniffi::export]
pub fn encrypt_identity_backup(
    password: &str,
    backup_data: IdentityBackupData,
) -> Result<String, IdentityBackupError> {
    id_backup::encrypt_identity_backup(password, &id_backup::IdentityBackupData::try_from(backup_data)?)
}

/// Binding version of [`id_backup::decrypt_identity_backup`].
#[expect(clippy::missing_errors_doc, reason = "Binding version")]
#[uniffi::export]
pub fn decrypt_identity_backup(
    password: &str,
    encrypted_backup: &str,
) -> Result<IdentityBackupData, IdentityBackupError> {
    let (_, backup_data) = id_backup::decrypt_identity_backup(password, encrypted_backup)?;
    Ok(IdentityBackupData::from(backup_data))
}
