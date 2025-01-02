use core::{array::TryFromSliceError, str};

use once_cell::sync::Lazy;
use rand::{self, Rng as _};
use regex::Regex;
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::{
    crypto::{consts::U24, generic_array::GenericArray, x25519},
    protobuf,
    time::utc_now_ms,
};

/// Regular expression validating a Threema ID.
pub static THREEMA_ID_RE: Lazy<Regex> =
    Lazy::new(|| Regex::new("^([0-9A-Z\\*][0-9A-Z]{7}|\\*)$").expect("Threema ID regex invalid"));

/// Invalid [`ThreemaId`].
#[derive(Debug, thiserror::Error)]
pub enum ThreemaIdError {
    /// Invalid length (must be exactly 8 bytes).
    #[error("Threema ID must be exactly 8 bytes: {0}")]
    InvalidLength(String),

    /// Invalid UTF-8 sequence.
    #[error("Invalid UTF-8 sequence: {0}")]
    InvalidUtf8(#[from] str::Utf8Error),

    /// Invalid symbols (only accepts the regular expression [`THREEMA_ID_RE`]).
    #[error("Threema ID contains invalid symbols: {0}")]
    InvalidSymbols(String),
}

/// A valid Threema ID.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct ThreemaId(pub [u8; Self::LENGTH]);
impl ThreemaId {
    /// Byte length of a Threema ID.
    pub const LENGTH: usize = 8;

    /// Byte representation of the Threema ID.
    #[inline]
    #[must_use]
    pub fn as_bytes(&self) -> &[u8; Self::LENGTH] {
        &self.0
    }

    /// String representation of the Threema ID.
    #[inline]
    #[must_use]
    pub fn as_str(&self) -> &str {
        // SAFETY: This is safe because the creation of a `ThreemaId` requires that it is a valid
        // UTF-8 sequence.
        unsafe { str::from_utf8_unchecked(&self.0) }
    }
}
impl TryFrom<&str> for ThreemaId {
    type Error = ThreemaIdError;

    fn try_from(id: &str) -> Result<Self, Self::Error> {
        let id_bytes: [u8; Self::LENGTH] = id
            .as_bytes()
            .try_into()
            .map_err(|_| ThreemaIdError::InvalidLength(id.into()))?;
        THREEMA_ID_RE
            .is_match(id)
            .then_some(ThreemaId(id_bytes))
            .ok_or_else(|| ThreemaIdError::InvalidSymbols(id.into()))
    }
}
impl TryFrom<&[u8; Self::LENGTH]> for ThreemaId {
    type Error = ThreemaIdError;

    fn try_from(id: &[u8; Self::LENGTH]) -> Result<Self, Self::Error> {
        ThreemaId::try_from(str::from_utf8(id)?)
    }
}

#[cfg(test)]
mod threema_id_tests {
    use super::{ThreemaId, ThreemaIdError};

    #[test]
    fn test_valid() {
        assert!(ThreemaId::try_from("ECHOECHO").is_ok());
        assert!(ThreemaId::try_from(&[0x45, 0x43, 0x48, 0x4f, 0x45, 0x43, 0x48, 0x4f]).is_ok());
        assert!(ThreemaId::try_from("*RICHTIG").is_ok());
        assert!(ThreemaId::try_from(&[0x2a, 0x52, 0x49, 0x43, 0x48, 0x54, 0x49, 0x47]).is_ok());
    }

    #[test]
    fn test_invalid() {
        assert!(matches!(
            ThreemaId::try_from(""),
            Err(ThreemaIdError::InvalidLength(_))
        ));
        assert!(matches!(
            ThreemaId::try_from("ZUWENIG"),
            Err(ThreemaIdError::InvalidLength(_))
        ));
        assert!(matches!(
            ThreemaId::try_from("*NEINNEIN"),
            Err(ThreemaIdError::InvalidLength(_))
        ));
        assert!(matches!(
            ThreemaId::try_from("ECHÜECHÜ"),
            Err(ThreemaIdError::InvalidLength(_))
        ));
        assert!(matches!(
            ThreemaId::try_from(&[0x00, 0x9f, 0x92, 0x96, 0x00, 0x00, 0x00, 0x00]),
            Err(ThreemaIdError::InvalidUtf8(_))
        ));
        assert!(matches!(
            ThreemaId::try_from(&[0_u8; 8]),
            Err(ThreemaIdError::InvalidSymbols(_))
        ));
        assert!(matches!(
            ThreemaId::try_from("ECH_ECH_"),
            Err(ThreemaIdError::InvalidSymbols(_))
        ));
        assert!(matches!(
            ThreemaId::try_from("********"),
            Err(ThreemaIdError::InvalidSymbols(_))
        ));
    }
}

/// The Client Key (often internally referred to as `ck` in the code and documentation) is a 32
/// bytes long, permanent secret key associated to the Threema ID.
///
/// IMPORTANT: This is **THE** key which requires ultimate care!
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct ClientKey(pub(crate) x25519::StaticSecret);
impl ClientKey {
    /// Byte length of the client key.
    pub const LENGTH: usize = 32;

    /// Byte representation of the client key.
    #[must_use]
    pub fn as_bytes(&self) -> &[u8; ClientKey::LENGTH] {
        self.0.as_bytes()
    }
}
impl From<[u8; ClientKey::LENGTH]> for ClientKey {
    fn from(bytes: [u8; ClientKey::LENGTH]) -> Self {
        Self(x25519::StaticSecret::from(bytes))
    }
}

/// Public portion associated to a [`ClientKey`].
pub struct ClientKeyPublic(pub x25519::PublicKey);
impl From<&ClientKey> for ClientKeyPublic {
    fn from(ck: &ClientKey) -> Self {
        Self(x25519::PublicKey::from(&ck.0))
    }
}

/// A unique group identity.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct GroupIdentity {
    /// Group ID as chosen by the group's creator
    pub group_id: u64,
    /// Threema ID of the group's creator
    pub creator_identity: ThreemaId,
}
impl TryFrom<&protobuf::common::GroupIdentity> for GroupIdentity {
    type Error = ThreemaIdError;

    fn try_from(group_identity: &protobuf::common::GroupIdentity) -> Result<Self, Self::Error> {
        Ok(GroupIdentity {
            group_id: group_identity.group_id,
            creator_identity: ThreemaId::try_from(group_identity.creator_identity.as_str())?,
        })
    }
}
impl From<&GroupIdentity> for protobuf::common::GroupIdentity {
    fn from(group_identity: &GroupIdentity) -> Self {
        protobuf::common::GroupIdentity {
            group_id: group_identity.group_id,
            creator_identity: group_identity.creator_identity.as_str().into(),
        }
    }
}

/// A 24-byte nonce for use with XSalsa20Poly1305 or XChaCha20Poly1305.
#[derive(Clone)]
pub struct Nonce(pub [u8; 24]);
impl TryFrom<&[u8]> for Nonce {
    type Error = TryFromSliceError;

    fn try_from(nonce: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self(<[u8; 24]>::try_from(nonce)?))
    }
}
impl<'array, 'nonce: 'array> From<&'nonce Nonce> for &'array GenericArray<u8, U24> {
    fn from(nonce: &'nonce Nonce) -> Self {
        Self::from(&nonce.0)
    }
}

/// A message ID.
///
/// May or may not be unique, depending on the context it is used for.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct MessageId(pub u64);
impl MessageId {
    /// Generate a random message ID.
    #[must_use]
    pub fn random() -> Self {
        Self(rand::thread_rng().gen())
    }
}

/// Metadata associated to a message.
#[derive(Clone)]
pub struct MessageMetadata {
    /// Unique message ID. Must match the message ID of the outer struct.
    pub message_id: MessageId,
    /// Unix-ish timestamp in milliseconds for when the message has been created.
    pub created_at: u64,
    /// Nickname of the sender at the time the message had been created.
    pub nickname: Option<String>,
}
impl MessageMetadata {
    const NICKNAME_PADDING: usize = 16;

    /// Create for a new outgoing message.
    #[must_use]
    pub fn new_outgoing(nickname: Option<String>, message_id: MessageId) -> Self {
        Self {
            message_id,
            created_at: utc_now_ms(),
            nickname,
        }
    }
}
impl From<protobuf::csp_e2e::MessageMetadata> for MessageMetadata {
    fn from(metadata: protobuf::csp_e2e::MessageMetadata) -> Self {
        MessageMetadata {
            message_id: MessageId(metadata.message_id),
            created_at: metadata.created_at,
            nickname: metadata.nickname,
        }
    }
}
impl From<&MessageMetadata> for protobuf::csp_e2e::MessageMetadata {
    fn from(metadata: &MessageMetadata) -> Self {
        // Determine padding needed for the nickname
        let padding_length = metadata.nickname.as_ref().map_or(16, |nickname| {
            MessageMetadata::NICKNAME_PADDING.saturating_sub(nickname.len())
        });

        // Rewrap
        Self {
            padding: vec![0; padding_length],
            message_id: metadata.message_id.0,
            created_at: metadata.created_at,
            nickname: metadata.nickname.clone(),
        }
    }
}
