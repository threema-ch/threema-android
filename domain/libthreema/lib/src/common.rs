//! Common items needed in many places.
use core::{array::TryFromSliceError, fmt, str};

use data_encoding::HEXLOWER;
use educe::Educe;
use libthreema_macros::{Name, concat_fixed_bytes};
use rand::{self, Rng as _};
use serde::{Deserialize, Serialize};
use tracing::warn;
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::{
    crypto::{
        blake2b,
        cipher::KeyInit as _,
        consts::U24,
        digest::{FixedOutput as _, Mac as _},
        generic_array::GenericArray,
        salsa20, x25519,
    },
    protobuf,
    utils::{debug::debug_static_secret, sequence_numbers::SequenceNumberValue, time::utc_now_ms},
};

/// Invalid [`ThreemaId`].
#[derive(Debug, thiserror::Error)]
pub enum ThreemaIdError {
    /// Invalid length (must be exactly 8 bytes).
    #[error("Threema ID must be exactly 8 bytes")]
    InvalidLength,

    /// Invalid symbols provided.
    #[error("Threema ID contains invalid symbols")]
    InvalidSymbols,
}

/// A valid Threema ID.
#[expect(
    clippy::unsafe_derive_deserialize,
    reason = "False positive triggered by the unsafe block in as_str, \
    see https://github.com/rust-lang/rust-clippy/issues/10349"
)]
#[derive(Clone, Copy, Eq, Hash, PartialEq, Serialize, Deserialize, Name)]
#[serde(into = "String")]
pub struct ThreemaId([u8; Self::LENGTH]);
impl ThreemaId {
    /// Byte length of a Threema ID.
    pub const LENGTH: usize = 8;

    /// Construct a predefined Threema ID.
    ///
    /// IMPORTANT: This skips the validation, so `identity` must be known to be valid!
    #[must_use]
    pub const fn predefined(identity: [u8; Self::LENGTH]) -> Self {
        ThreemaId(identity)
    }

    /// Convert a CLI parameter string to a [`ThreemaId`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_str_cli(string: &str) -> Result<Self, String> {
        Self::try_from(string).map_err(|error| format!("invalid Threema ID: {error}"))
    }

    /// Byte representation of the Threema ID.
    #[inline]
    #[must_use]
    pub fn to_bytes(&self) -> [u8; Self::LENGTH] {
        self.0
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
impl fmt::Display for ThreemaId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.as_str())
    }
}
impl fmt::Debug for ThreemaId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.debug_tuple(Self::NAME).field(&self.as_str()).finish()
    }
}
impl From<ThreemaId> for String {
    fn from(id: ThreemaId) -> Self {
        id.as_str().to_owned()
    }
}
impl TryFrom<&[u8]> for ThreemaId {
    type Error = ThreemaIdError;

    fn try_from(id: &[u8]) -> Result<Self, Self::Error> {
        let id = <[u8; Self::LENGTH]>::try_from(id).map_err(|_| ThreemaIdError::InvalidLength)?;
        if ![b'*'..=b'*', b'0'..=b'9', b'A'..=b'Z']
            .iter()
            .any(|range| range.contains(id.first().expect("id must be >= 8 bytes")))
        {
            return Err(ThreemaIdError::InvalidSymbols);
        }
        if !id.get(1..8).expect("id must be >= 8 bytes").iter().all(|byte| {
            [b'0'..=b'9', b'A'..=b'Z']
                .iter()
                .any(|range| range.contains(byte))
        }) {
            return Err(ThreemaIdError::InvalidSymbols);
        }
        Ok(ThreemaId(id))
    }
}
impl TryFrom<&str> for ThreemaId {
    type Error = ThreemaIdError;

    fn try_from(id: &str) -> Result<Self, Self::Error> {
        ThreemaId::try_from(id.as_bytes())
    }
}

#[cfg(test)]
mod threema_id_tests {
    use super::{ThreemaId, ThreemaIdError};

    #[test]
    fn test_valid() {
        assert!(ThreemaId::try_from("ECHOECHO").is_ok());
        assert!(ThreemaId::try_from([0x45, 0x43, 0x48, 0x4f, 0x45, 0x43, 0x48, 0x4f].as_slice()).is_ok());
        assert!(ThreemaId::try_from("*RICHTIG").is_ok());
        assert!(ThreemaId::try_from([0x2a, 0x52, 0x49, 0x43, 0x48, 0x54, 0x49, 0x47].as_slice()).is_ok());
    }

    #[test]
    fn test_invalid() {
        assert!(matches!(
            ThreemaId::try_from(""),
            Err(ThreemaIdError::InvalidLength)
        ));
        assert!(matches!(
            ThreemaId::try_from("ZUWENIG"),
            Err(ThreemaIdError::InvalidLength)
        ));
        assert!(matches!(
            ThreemaId::try_from("*NEINNEIN"),
            Err(ThreemaIdError::InvalidLength)
        ));
        assert!(matches!(
            ThreemaId::try_from("ECHÜECHÜ"),
            Err(ThreemaIdError::InvalidLength)
        ));
        assert!(matches!(
            ThreemaId::try_from([0x00, 0x9f, 0x92, 0x96, 0x00, 0x00, 0x00, 0x00].as_slice()),
            Err(ThreemaIdError::InvalidSymbols)
        ));
        assert!(matches!(
            ThreemaId::try_from([0_u8; 8].as_slice()),
            Err(ThreemaIdError::InvalidSymbols)
        ));
        assert!(matches!(
            ThreemaId::try_from("ECH_ECH_"),
            Err(ThreemaIdError::InvalidSymbols)
        ));
        assert!(matches!(
            ThreemaId::try_from("********"),
            Err(ThreemaIdError::InvalidSymbols)
        ));
    }
}

/// Shared secret for authentication against the chat server (aka Vouch Key).
#[derive(Zeroize, ZeroizeOnDrop)]
pub(crate) struct CspAuthenticationKey(pub(crate) [u8; 32]);

/// Cipher associated to the Message Key (MK).
pub(crate) struct MessageCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Message Metadata Key (MMK).
pub(crate) struct MessageMetadataCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Shared secret context for usage between two identities (i.e. client to client).
pub(crate) struct CspE2eKey(x25519::SharedSecretHSalsa20);
impl CspE2eKey {
    /// Get the Message Key (MK).
    ///
    /// IMPORTANT: This key should not be used any other purpose but for messages. Otherwise, it's
    /// just another story of _payload confusion_.
    #[must_use]
    pub(crate) fn message_cipher(&self) -> MessageCipher {
        MessageCipher(salsa20::XSalsa20Poly1305::new(self.0.as_bytes().into()))
    }

    /// Derive the Message Metadata Key (MMK).
    #[must_use]
    pub(crate) fn message_metadata_cipher(&self) -> MessageMetadataCipher {
        let mmk =
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(self.0.as_bytes()), b"mm", b"3ma-csp")
                .expect("Blake2bMac256 failed")
                .finalize_fixed();
        MessageMetadataCipher(salsa20::XSalsa20Poly1305::new(&mmk))
    }
}

/// The Client Key (often internally referred to as `ck` in the code and documentation) is a 32
/// bytes long, permanent secret key associated to the Threema ID.
///
/// IMPORTANT: This is **THE** key which requires ultimate care!
#[derive(Name, Zeroize, ZeroizeOnDrop)]
pub struct ClientKey(x25519::StaticSecret);
impl ClientKey {
    /// Byte length of the client key.
    pub const LENGTH: usize = x25519::KEY_LENGTH;

    /// Get the public key associated with this client key secret.
    #[must_use]
    pub fn public_key(&self) -> PublicKey {
        PublicKey(x25519::PublicKey::from(&self.0))
    }

    /// Byte representation of the client key.
    #[must_use]
    pub(crate) fn as_bytes(&self) -> &[u8; Self::LENGTH] {
        self.0.as_bytes()
    }

    /// Derive the shared secret for authentication (aka _vouch key_) during the CSP handshake.
    #[must_use]
    pub(crate) fn derive_csp_authentication_key(
        &self,
        permanent_server_key: &PublicKey,
        temporary_server_key: &PublicKey,
    ) -> CspAuthenticationKey {
        // Calculate the secret as the concatenation of two shared secrets
        let secret: [u8; 2 * x25519::SharedSecretHSalsa20::LENGTH] = concat_fixed_bytes!(
            // Compute first half as X25519HSalsa20(CK.secret, SK.public)
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&permanent_server_key.0)).to_bytes(),
            // Compute second half as X25519HSalsa20(CK.secret, temporary_server_key.public)
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&temporary_server_key.0)).to_bytes(),
        );

        // Apply Blake2b to obtain the CSP authentication secret (aka _vouch key_)
        CspAuthenticationKey(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&secret), b"v2", b"3ma-csp")
                .expect("Blake2bMac256 failed")
                .finalize()
                .into_bytes()
                .into(),
        )
    }

    /// Derive the shared secret for usage between two identities (i.e. client to client).
    #[must_use]
    pub(crate) fn derive_csp_e2e_key(&self, client_public_key: &PublicKey) -> CspE2eKey {
        CspE2eKey(x25519::SharedSecretHSalsa20::from(
            self.0.diffie_hellman(&client_public_key.0),
        ))
    }
}
impl From<[u8; Self::LENGTH]> for ClientKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::StaticSecret::from(bytes))
    }
}
impl fmt::Debug for ClientKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "{}(PublicKey({}))",
            Self::NAME,
            &self.public_key().to_string()
        )
    }
}
#[cfg(feature = "cli")]
impl From<RawClientKey> for ClientKey {
    fn from(client_key: RawClientKey) -> Self {
        Self(client_key.0.clone())
    }
}

/// Also the Client Key but [`Clone`].
///
/// For example needed for the CLI which requires this struct to be [`Clone`] but we don't want
/// [`ClientKey`] to be [`Clone`].
#[cfg(feature = "cli")]
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct RawClientKey(x25519::StaticSecret);
#[cfg(feature = "cli")]
impl RawClientKey {
    /// Convert a CLI parameter hex string to a [`ClientKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_hex_cli(string: &str) -> Result<Self, String> {
        let bytes = HEXLOWER
            .decode(string.as_bytes())
            .map_err(|error| error.to_string())?;
        let bytes: [u8; ClientKey::LENGTH] = bytes
            .as_slice()
            .try_into()
            .map_err(|_| format!("must be {} bytes, got {}", ClientKey::LENGTH, bytes.len()))?;
        Ok(Self(x25519::StaticSecret::from(bytes)))
    }
}

/// Public portion associated to an X25519 secret key.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct PublicKey(pub x25519::PublicKey);
impl PublicKey {
    /// Byte length of the public portion of an X25519 secret key.
    pub const LENGTH: usize = x25519::KEY_LENGTH;

    /// Convert a CLI parameter hex string to a [`PublicKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_hex_cli(string: &str) -> Result<Self, String> {
        let bytes = HEXLOWER
            .decode(string.as_bytes())
            .map_err(|error| error.to_string())?;
        Self::try_from(bytes.as_slice())
            .map_err(|_| format!("must be {} bytes, got {}", Self::LENGTH, bytes.len()))
    }
}
impl From<&x25519::StaticSecret> for PublicKey {
    fn from(private_key: &x25519::StaticSecret) -> Self {
        Self(x25519::PublicKey::from(private_key))
    }
}
impl From<[u8; Self::LENGTH]> for PublicKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::PublicKey::from(bytes))
    }
}
impl TryFrom<&[u8]> for PublicKey {
    type Error = TryFromSliceError;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self::from(TryInto::<[u8; Self::LENGTH]>::try_into(bytes)?))
    }
}
impl fmt::Display for PublicKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(self.0.as_bytes()))
    }
}
impl fmt::Debug for PublicKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({})", Self::NAME, &self.to_string())
    }
}

/// Cipher associated to the key computed as `X25519HSalsa20(DGPK.secret, ephemeral_server_key)`.
#[expect(dead_code, reason = "Will use later")]
pub(crate) struct DeviceGroupPathAuthenticationCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// The Device Group Path Key (DGPK)
pub(crate) struct DeviceGroupPathKey(x25519_dalek::StaticSecret);
impl DeviceGroupPathKey {
    /// Byte length of the device group key.
    pub(crate) const LENGTH: usize = 32;

    /// Get the public key associated with this client key secret.
    ///
    /// This public key is used as the Mediator Device ID.
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn public_key(&self) -> PublicKey {
        PublicKey::from(&self.0)
    }

    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn authentication_cipher(
        self,
        ephemeral_server_key: &PublicKey,
    ) -> DeviceGroupPathAuthenticationCipher {
        DeviceGroupPathAuthenticationCipher(salsa20::XSalsa20Poly1305::new(
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&ephemeral_server_key.0))
                .as_bytes()
                .into(),
        ))
    }
}

/// Cipher associated to the Device Group Reflect Key (DGRK).
pub(crate) struct DeviceGroupReflectCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Device Group Device Info Key (DGDIK).
#[expect(dead_code, reason = "Will use later")]
pub(crate) struct DeviceGroupDeviceInfoCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Device Group Transaction Scope Key (DGTSK).
pub(crate) struct DeviceGroupTransactionScopeCipher(pub(crate) salsa20::XSalsa20Poly1305);

#[derive(Educe, Zeroize, ZeroizeOnDrop)]
#[educe(Debug)]
/// The Device Group Key (DGK)
pub struct DeviceGroupKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);
impl DeviceGroupKey {
    /// Byte length of the device group key.
    pub const LENGTH: usize = 32;

    /// Sample a random Device Group Key
    #[cfg(feature = "cli")]
    #[must_use]
    pub fn random() -> Self {
        let mut device_group_key = [0_u8; Self::LENGTH];
        rand::thread_rng().fill(&mut device_group_key);
        Self::from(device_group_key)
    }

    /// Derive the Device Group Path Key (DGPK).
    #[must_use]
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path_key(&self) -> DeviceGroupPathKey {
        let path_key: [u8; DeviceGroupPathKey::LENGTH] = self.derive_key(b"p").into();
        DeviceGroupPathKey(x25519::StaticSecret::from(path_key))
    }

    /// Derive the Device Group Reflect Key (DGRK).
    #[must_use]
    pub(crate) fn reflect_key(&self) -> DeviceGroupReflectCipher {
        DeviceGroupReflectCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"r")))
    }

    /// Derive the Device Group Device Info Key (DGDIK).
    #[must_use]
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn device_info_key(&self) -> DeviceGroupDeviceInfoCipher {
        DeviceGroupDeviceInfoCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"di")))
    }

    /// Derive the Device Group Transaction Scope Key (DGTSK).
    #[must_use]
    pub(crate) fn transaction_scope_key(&self) -> DeviceGroupTransactionScopeCipher {
        DeviceGroupTransactionScopeCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"ts")))
    }

    fn derive_key(&self, salt: &[u8]) -> salsa20::Key {
        blake2b::Blake2bMac256::new_with_salt_and_personal(Some(self.0.as_bytes()), salt, b"3ma-mdev")
            .expect("Blake2bMac256 failed")
            .finalize_fixed()
    }
}
impl From<[u8; DeviceGroupKey::LENGTH]> for DeviceGroupKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::StaticSecret::from(bytes))
    }
}
impl TryFrom<&[u8]> for DeviceGroupKey {
    type Error = TryFromSliceError;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self::from(TryInto::<[u8; Self::LENGTH]>::try_into(bytes)?))
    }
}

/// Also the Device Group Key but [`Clone`].
///
/// For example needed for the CLI which requires this struct to be [`Clone`] but we don't want
/// [`DeviceGroupKey`] to be [`Clone`].
#[cfg(feature = "cli")]
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct RawDeviceGroupKey(x25519::StaticSecret);
#[cfg(feature = "cli")]
impl RawDeviceGroupKey {
    /// Convert a CLI parameter hex string to a [`DeviceGroupKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_hex_cli(string: &str) -> Result<Self, String> {
        let bytes = HEXLOWER
            .decode(string.as_bytes())
            .map_err(|error| error.to_string())?;
        let bytes: [u8; DeviceGroupKey::LENGTH] = bytes
            .as_slice()
            .try_into()
            .map_err(|_| format!("must be {} bytes, got {}", DeviceGroupKey::LENGTH, bytes.len()))?;
        Ok(Self(x25519::StaticSecret::from(bytes)))
    }
}

/// A (D2X) device ID.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct DeviceId(pub u64);
impl fmt::Debug for DeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.swap_bytes())
    }
}

/// A CSP device ID.
///
/// WARNING: This should never be equal to the D2X [`DeviceId`].
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct CspDeviceId(pub u64);
impl CspDeviceId {
    pub(crate) const LENGTH: usize = 8;
}
impl fmt::Debug for CspDeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.swap_bytes())
    }
}

/// Work variant credentials.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct WorkCredentials {
    /// Work username
    pub username: String,
    /// Work password
    pub password: String,
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
            creator_identity: group_identity.creator_identity.into(),
        }
    }
}

/// A specific conversation (aka _receiver_).
#[derive(Clone)]
pub enum Conversation {
    /// A specific 1:1 contact.
    Contact(ThreemaId),
    /// A specific distribution list.
    DistributionList(u64),
    /// A specific group.
    Group(GroupIdentity),
}
impl TryFrom<&protobuf::d2d::conversation_id::Id> for Conversation {
    type Error = ThreemaIdError;

    fn try_from(conversation: &protobuf::d2d::conversation_id::Id) -> Result<Self, Self::Error> {
        Ok(match conversation {
            protobuf::d2d::conversation_id::Id::Contact(contact_identity) => {
                Self::Contact(ThreemaId::try_from(contact_identity.as_str())?)
            },
            protobuf::d2d::conversation_id::Id::DistributionList(distribution_list_id) => {
                Self::DistributionList(*distribution_list_id)
            },
            protobuf::d2d::conversation_id::Id::Group(group_identity) => {
                Self::Group(GroupIdentity::try_from(group_identity)?)
            },
        })
    }
}
impl From<&Conversation> for protobuf::d2d::ConversationId {
    fn from(conversation: &Conversation) -> Self {
        let id = match conversation {
            Conversation::Contact(contact_identity) => {
                protobuf::d2d::conversation_id::Id::Contact(contact_identity.as_str().to_owned())
            },
            Conversation::DistributionList(distribution_list_id) => {
                protobuf::d2d::conversation_id::Id::DistributionList(*distribution_list_id)
            },
            Conversation::Group(group_identity) => {
                protobuf::d2d::conversation_id::Id::Group(group_identity.into())
            },
        };
        protobuf::d2d::ConversationId { id: Some(id) }
    }
}

/// CSP features supported by a device or available for a contact.
///
/// IMPORTANT: The flags determine what a device/contact is capable of, not
/// whether the settings allow for it. For example, group calls may be supported
/// but ignored if disabled in the settings.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FeatureMask(pub u64);
#[rustfmt::skip]
impl FeatureMask {
    /// No features available
    pub const NONE: u64 =                     0b_0000_0000_0000_0000;
    /// Supports voice messages.
    pub const VOICE_MESSAGE_SUPPORT: u64 =    0b_0000_0000_0000_0001;
    /// Supports groups.
    pub const GROUP_SUPPORT: u64 =            0b_0000_0000_0000_0010;
    /// Supports polls.
    pub const POLL_SUPPORT: u64 =             0b_0000_0000_0000_0100;
    /// Supports file messages.
    pub const FILE_MESSAGE_SUPPORT: u64 =     0b_0000_0000_0000_1000;
    /// Supports 1:1 audio calls.
    pub const O2O_AUDIO_CALL_SUPPORT: u64 =   0b_0000_0000_0001_0000;
    /// Supports 1:1 video calls.
    pub const O2O_VIDEO_CALL_SUPPORT: u64 =   0b_0000_0000_0010_0000;
    /// Supports forward security.
    pub const FORWARD_SECURITY_SUPPORT: u64 = 0b_0000_0000_0100_0000;
    /// Supports group calls.
    pub const GROUP_CALL_SUPPORT: u64 =       0b_0000_0000_1000_0000;
    /// Supports editing messages.
    pub const EDIT_MESSAGE_SUPPORT: u64 =     0b_0000_0001_0000_0000;
    /// Supports deleting messages.
    pub const DELETE_MESSAGE_SUPPORT: u64 =   0b_0000_0010_0000_0000;
}

/// 16 byte random cookie
#[derive(Clone, Copy, PartialEq, Eq, Name)]
pub struct Cookie(pub [u8; Self::LENGTH]);
impl Cookie {
    /// Byte length of a cookie
    pub const LENGTH: usize = 16;

    /// Generate a random cookie
    #[must_use]
    pub fn random() -> Self {
        let mut cookie = Self([0_u8; Self::LENGTH]);
        rand::thread_rng().fill(&mut cookie.0);
        cookie
    }
}
impl fmt::Display for Cookie {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(&self.0))
    }
}
impl fmt::Debug for Cookie {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({})", Self::NAME, &self.to_string())
    }
}

/// A 24-byte nonce for use with XSalsa20Poly1305 or XChaCha20Poly1305.
#[derive(Clone, Eq, PartialEq)]
pub struct Nonce(pub [u8; Self::LENGTH]);
impl Nonce {
    /// Byte length of a nonce.
    pub const LENGTH: usize = 24;

    /// Concatenate a cookie and a u64-le sequence number
    ///
    /// Note that the sequence number is not incremented within this function!
    #[must_use]
    #[expect(clippy::needless_pass_by_value, reason = "Prevent sequence number re-use")]
    pub(crate) fn from_cookie_and_sequence_number(
        cookie: Cookie,
        sequence_number: SequenceNumberValue<u64>,
    ) -> Self {
        Self(concat_fixed_bytes!(cookie.0, sequence_number.0.to_le_bytes()))
    }
}
impl From<[u8; Self::LENGTH]> for Nonce {
    fn from(nonce: [u8; Self::LENGTH]) -> Self {
        Self(nonce)
    }
}
impl<'array, 'nonce: 'array> From<&'nonce Nonce> for &'array GenericArray<u8, U24> {
    fn from(nonce: &'nonce Nonce) -> Self {
        Self::from(&nonce.0)
    }
}
impl From<GenericArray<u8, U24>> for Nonce {
    fn from(nonce: GenericArray<u8, U24>) -> Self {
        Self(nonce.into())
    }
}
impl TryFrom<&[u8]> for Nonce {
    type Error = TryFromSliceError;

    fn try_from(nonce: &[u8]) -> Result<Self, Self::Error> {
        Ok(Nonce::from(<[u8; Self::LENGTH]>::try_from(nonce)?))
    }
}

/// A message ID.
///
/// May or may not be unique, depending on the context it is used for.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct MessageId(pub u64);
impl MessageId {
    /// Byte length of a message ID.
    pub const LENGTH: usize = 8;

    /// Generate a random message ID.
    #[must_use]
    pub fn random() -> Self {
        Self(rand::thread_rng().r#gen())
    }
}
impl fmt::Display for MessageId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{:016x}", self.0.swap_bytes())
    }
}
impl fmt::Debug for MessageId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.swap_bytes())
    }
}
impl From<[u8; Self::LENGTH]> for MessageId {
    fn from(message_id: [u8; Self::LENGTH]) -> Self {
        Self(u64::from_le_bytes(message_id))
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
    pub nickname: Delta<String>,
}
impl MessageMetadata {
    const NICKNAME_PADDING: usize = 16;

    /// Create for a new outgoing message.
    #[must_use]
    pub fn new_outgoing(nickname: Delta<String>, message_id: MessageId) -> Self {
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
            nickname: Delta::from_non_empty(metadata.nickname.map(|nickname| nickname.trim().to_owned())),
        }
    }
}
impl From<MessageMetadata> for protobuf::csp_e2e::MessageMetadata {
    fn from(metadata: MessageMetadata) -> Self {
        let nickname = metadata.nickname.into_non_empty();

        // Determine padding needed for the nickname
        let padding_length = nickname
            .as_ref()
            .map_or(MessageMetadata::NICKNAME_PADDING, |nickname| {
                MessageMetadata::NICKNAME_PADDING.saturating_sub(nickname.len())
            });

        // Rewrap
        Self {
            padding: vec![0; padding_length],
            message_id: metadata.message_id.0,
            created_at: metadata.created_at,
            nickname,
        }
    }
}

/// A blob ID.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct BlobId(pub [u8; Self::LENGTH]);
impl BlobId {
    /// Byte length of a blob ID.
    pub const LENGTH: usize = 16;
}

/// A delta update, where some value can be unchanged, updated or removed.
#[derive(Debug, Clone, PartialEq)]
pub enum Delta<T> {
    /// Value remains unchanged.
    Unchanged,
    /// Value updated to inner `T`.
    Update(T),
    /// Value removed.
    Remove,
}
impl<T> Delta<T> {
    /// Converts from [`&Delta<T>`] to [`Delta<&T>`].
    #[expect(dead_code, reason = "May use later")]
    #[inline]
    pub(crate) const fn as_ref(&self) -> Delta<&T> {
        match &self {
            Self::Unchanged => Delta::Unchanged,
            Self::Update(value) => Delta::Update(value),
            Self::Remove => Delta::Remove,
        }
    }

    /// Apply the delta update to the `current` value.
    #[inline]
    pub(crate) fn apply_to(self, current: &mut Option<T>) {
        match self {
            Self::Unchanged => {},
            Self::Update(value) => {
                let _ = current.insert(value);
            },
            Self::Remove => {
                let _ = current.take();
            },
        }
    }
}
impl<T: Eq> Delta<T> {
    /// Re-evaluate the delta update against a `current` value, ensuring that it reflects actual
    /// changes and otherwise reports [`Delta::Unchanged`] if there is no change.
    pub(crate) fn changes(self, current: Option<&T>) -> Delta<T> {
        match (self, current) {
            (Self::Update(update), None) => Delta::Update(update),
            (Self::Update(update), Some(current)) => {
                if current == &update {
                    Delta::Unchanged
                } else {
                    Delta::Update(update)
                }
            },
            (Self::Remove, None) | (Self::Unchanged, _) => Delta::Unchanged,
            (Self::Remove, Some(_)) => Delta::Remove,
        }
    }
}
impl Delta<String> {
    /// Creates a [`Delta<String>`] from a source where an empty string is semantically
    /// equivalent to [`Delta::Remove`].
    pub(crate) fn from_non_empty(update: Option<String>) -> Self {
        match update {
            Some(update) => {
                if update.is_empty() {
                    Self::Remove
                } else {
                    Self::Update(update)
                }
            },
            None => Self::Unchanged,
        }
    }

    /// Creates a [`Option<String>`] from the delta update where [`Delta::Remove`] is converted into
    /// an empty string.
    ///
    /// WARNING: A delta update should not contain an empty string, since the resulting
    /// [`Option<String>`] is recognized as a [`Delta::Remove`] when converted back via
    /// [`Delta<String>::from_non_empty`].
    #[tracing::instrument()]
    pub(crate) fn into_non_empty(self) -> Option<String> {
        match self {
            Self::Unchanged => None,
            Self::Update(value) => {
                if value.is_empty() {
                    warn!("Delta::Update contained an empty string");
                }
                Some(value)
            },
            Self::Remove => Some(String::new()),
        }
    }
}
