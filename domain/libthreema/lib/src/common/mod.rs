//! Common items needed in many places.
use core::{
    array::TryFromSliceError,
    fmt,
    num::ParseIntError,
    ops::Deref,
    str::{self, FromStr},
};
use std::env;

#[cfg(test)]
use anyhow;
use data_encoding::HEXLOWER;
use libthreema_macros::Name;
use rand::{self, Rng as _};
use serde::{Deserialize, Serialize};
use tracing::warn;

use crate::{
    crypto::{consts::U24, generic_array::GenericArray},
    protobuf,
    utils::{apply::Apply, debug::Name as _, protobuf::PaddedMessage as _, time::utc_now_ms},
};

pub mod config;
pub mod keys;
pub mod task;

/// Client info
#[derive(Debug, Clone)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(tag = "platform", rename_all = "kebab-case", rename_all_fields = "camelCase"),
    tsify(from_wasm_abi)
)]
pub enum ClientInfo {
    /// Android
    Android {
        /// Version string.
        version: String,

        /// Locale, i.e. `<language>/<country-code>` where:
        ///
        /// - `<language>` is an ISO 639-1:2002-ish language code
        /// - `<country-code>` is an ISO 3166-1-ish country code
        locale: String,

        /// Device model.
        device_model: String,

        /// OS version.
        os_version: String,
    },

    /// iOS
    Ios {
        /// Version string.
        version: String,

        /// Locale, i.e. `<language>/<country-code>` where:
        ///
        /// - `<language>` is an ISO 639-1:2002-ish language code
        /// - `<country-code>` is an ISO 3166-1-ish country code
        locale: String,

        /// Device model.
        device_model: String,

        /// OS version.
        os_version: String,
    },

    /// Desktop 2.x
    Desktop {
        /// Version string.
        version: String,

        /// Locale, i.e. `<language>/<country-code>` where:
        ///
        /// - `<language>` is an ISO 639-1:2002-ish language code
        /// - `<country-code>` is an ISO 3166-1-ish country code
        locale: String,

        /// Renderer name (e.g. `electron`).
        renderer_name: String,

        /// Renderer version.
        renderer_version: String,

        /// OS name (e.g. `linux`).
        os_name: String,

        /// OS architecture (e.g. `x64`).
        os_architecture: String,
    },

    /// libthreema standalone (CLI, testing, ...)
    Libthreema,
}
impl ClientInfo {
    /// Encode to HTTPS user agent name.
    #[must_use]
    pub fn to_user_agent(&self) -> String {
        match self {
            ClientInfo::Android { version, .. } => format!("Threema Android/{version}"),
            ClientInfo::Ios { version, .. } => format!("Threema iOS/{version}"),
            ClientInfo::Desktop { version, .. } => format!("Threema Desktop/{version}"),
            ClientInfo::Libthreema => format!("libthreema/{version}", version = env!("CARGO_PKG_VERSION")),
        }
    }

    /// Encode to colon-separated as used by the CSP `client-info` (hopefully not for long).
    #[must_use]
    pub fn to_semicolon_separated(&self) -> String {
        match self {
            ClientInfo::Android {
                version,
                locale,
                device_model,
                os_version,
            } => {
                format!(
                    "{version};A;{locale};{device_model};{os_version}",
                    version = version.replace(';', "_"),
                    locale = locale.replace(';', "_"),
                    device_model = device_model.replace(';', "_"),
                    os_version = os_version.replace(';', "_")
                )
            },

            ClientInfo::Ios {
                version,
                locale,
                device_model,
                os_version,
            } => {
                format!(
                    "{version};I;{locale};{device_model};{os_version}",
                    version = version.replace(';', "_"),
                    locale = locale.replace(';', "_"),
                    device_model = device_model.replace(';', "_"),
                    os_version = os_version.replace(';', "_")
                )
            },

            ClientInfo::Desktop {
                version,
                locale,
                renderer_name,
                renderer_version,
                os_name,
                os_architecture,
            } => {
                format!(
                    "{version};Q;{locale};{renderer_name};{renderer_version};{os_name};{os_architecture}",
                    version = version.replace(';', "_"),
                    locale = locale.replace(';', "_"),
                    renderer_name = renderer_name.replace(';', "_"),
                    renderer_version = renderer_version.replace(';', "_"),
                    os_name = os_name.replace(';', "_"),
                    os_architecture = os_architecture.replace(';', "_")
                )
            },

            ClientInfo::Libthreema => {
                format!(
                    "{version};L;en/CH;{os_name};{os_architecture}",
                    version = env!("CARGO_PKG_VERSION"),
                    os_name = env::consts::OS,
                    os_architecture = env::consts::ARCH
                )
            },
        }
    }

    /// Construct a [`protobuf::d2d::DeviceInfo`] from a device label and the [`ClientInfo`].
    ///
    /// The device label (e.g. "PC at Work") is recommended to not exceed 64 grapheme clusters.
    pub(crate) fn to_device_info(&self, label: Option<String>) -> protobuf::d2d::DeviceInfo {
        let (platform, platform_details, app_version) = match self {
            ClientInfo::Android {
                version,
                device_model,
                ..
            } => (
                protobuf::d2d::device_info::Platform::Android,
                device_model.clone(),
                version.clone(),
            ),

            ClientInfo::Ios {
                version,
                device_model,
                ..
            } => (
                protobuf::d2d::device_info::Platform::Ios,
                device_model.clone(),
                version.clone(),
            ),

            ClientInfo::Desktop {
                version,
                renderer_name,
                renderer_version,
                os_name,
                ..
            } => (
                protobuf::d2d::device_info::Platform::Desktop,
                format!("{renderer_name} {renderer_version} ({os_name})"),
                version.clone(),
            ),

            ClientInfo::Libthreema => (
                protobuf::d2d::device_info::Platform::Unspecified,
                format!("libthreema ({os_name})", os_name = env::consts::OS),
                env!("CARGO_PKG_VERSION").to_owned(),
            ),
        };
        protobuf::d2d::DeviceInfo {
            #[expect(deprecated, reason = "Will be filled by encode_to_vec_padded")]
            padding: vec![],
            platform: platform as i32,
            platform_details,
            app_version,
            label: label.unwrap_or_default(),
        }
    }
}

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
#[serde(try_from = "&str", into = "String")]
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

    /// Byte representation of the Threema ID.
    #[inline]
    #[must_use]
    pub fn to_bytes(self) -> [u8; Self::LENGTH] {
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

    /// Return whether this is a Gateway ID
    #[inline]
    #[must_use]
    pub fn is_gateway_id(self) -> bool {
        self.0[0] == b'*'
    }
}
impl fmt::Display for ThreemaId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(self.as_str())
    }
}
impl fmt::Debug for ThreemaId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
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
        Self::try_from(id.as_bytes())
    }
}
impl FromStr for ThreemaId {
    type Err = ThreemaIdError;

    fn from_str(id: &str) -> Result<Self, Self::Err> {
        Self::try_from(id)
    }
}

#[cfg(test)]
mod threema_id_tests {
    use assert_matches::assert_matches;

    use super::{ThreemaId, ThreemaIdError};

    #[test]
    fn valid() {
        assert!(ThreemaId::try_from("ECHOECHO").is_ok());
        assert!(ThreemaId::try_from([0x45, 0x43, 0x48, 0x4f, 0x45, 0x43, 0x48, 0x4f].as_slice()).is_ok());
        assert!(ThreemaId::try_from("*RICHTIG").is_ok());
        assert!(ThreemaId::try_from([0x2a, 0x52, 0x49, 0x43, 0x48, 0x54, 0x49, 0x47].as_slice()).is_ok());
    }

    #[test]
    fn invalid() {
        assert_matches!(ThreemaId::try_from(""), Err(ThreemaIdError::InvalidLength));
        assert_matches!(ThreemaId::try_from("ZUWENIG"), Err(ThreemaIdError::InvalidLength));
        assert_matches!(
            ThreemaId::try_from("*NEINNEIN"),
            Err(ThreemaIdError::InvalidLength)
        );
        assert_matches!(
            ThreemaId::try_from("ECHÜECHÜ"),
            Err(ThreemaIdError::InvalidLength)
        );
        assert_matches!(
            ThreemaId::try_from([0x00, 0x9f, 0x92, 0x96, 0x00, 0x00, 0x00, 0x00].as_slice()),
            Err(ThreemaIdError::InvalidSymbols)
        );
        assert_matches!(
            ThreemaId::try_from([0_u8; 8].as_slice()),
            Err(ThreemaIdError::InvalidSymbols)
        );
        assert_matches!(
            ThreemaId::try_from("ECH_ECH_"),
            Err(ThreemaIdError::InvalidSymbols)
        );
        assert_matches!(
            ThreemaId::try_from("********"),
            Err(ThreemaIdError::InvalidSymbols)
        );
    }
}

/// A CSP server group.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Deserialize, Name)]
#[serde(try_from = "&str")]
pub struct ChatServerGroup(pub u8);
impl TryFrom<&str> for ChatServerGroup {
    type Error = ParseIntError;

    fn try_from(string: &str) -> Result<Self, Self::Error> {
        Ok(Self(u8::from_str_radix(string, 16)?))
    }
}
impl FromStr for ChatServerGroup {
    type Err = ParseIntError;

    fn from_str(id: &str) -> Result<Self, Self::Err> {
        Self::try_from(id)
    }
}
impl fmt::Display for ChatServerGroup {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{:02x}", self.0)
    }
}
impl fmt::Debug for ChatServerGroup {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}

/// A CSP device cookie.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct DeviceCookie(pub [u8; Self::LENGTH]);
impl DeviceCookie {
    /// Byte length of a CSP device cookie.
    pub(crate) const LENGTH: usize = 16;

    #[cfg(any(test, feature = "cli"))]
    pub(crate) fn from_hex(string: &str) -> anyhow::Result<Self> {
        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; Self::LENGTH] = bytes.as_slice().try_into()?;
        Ok(Self(bytes))
    }
}
#[cfg(any(test, feature = "cli"))]
impl FromStr for DeviceCookie {
    type Err = anyhow::Error;

    fn from_str(device_cookie: &str) -> Result<Self, Self::Err> {
        Self::from_hex(device_cookie)
    }
}
impl fmt::Display for DeviceCookie {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(&self.0))
    }
}
impl fmt::Debug for DeviceCookie {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}

/// A D2X device ID.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct D2xDeviceId(pub u64);
#[cfg(any(test, feature = "cli"))]
impl TryFrom<&str> for D2xDeviceId {
    type Error = ParseIntError;

    fn try_from(id: &str) -> Result<Self, Self::Error> {
        Ok(Self(u64::from_str_radix(id, 16)?.to_be()))
    }
}
#[cfg(any(test, feature = "cli"))]
impl FromStr for D2xDeviceId {
    type Err = ParseIntError;

    fn from_str(id: &str) -> Result<Self, Self::Err> {
        Self::try_from(id)
    }
}
impl fmt::Display for D2xDeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{:02x}", self.0)
    }
}
impl fmt::Debug for D2xDeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.to_be())
    }
}

/// A CSP device ID.
///
/// WARNING: This should never be equal to the [`D2xDeviceId`].
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct CspDeviceId(pub u64);
impl CspDeviceId {
    /// Byte length of a CSP device ID.
    pub(crate) const LENGTH: usize = 8;
}
#[cfg(any(test, feature = "cli"))]
impl TryFrom<&str> for CspDeviceId {
    type Error = ParseIntError;

    fn try_from(id: &str) -> Result<Self, Self::Error> {
        Ok(Self(u64::from_str_radix(id, 16)?.to_be()))
    }
}
#[cfg(any(test, feature = "cli"))]
impl FromStr for CspDeviceId {
    type Err = ParseIntError;

    fn from_str(id: &str) -> Result<Self, Self::Err> {
        Self::try_from(id)
    }
}
impl fmt::Display for CspDeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{:02x}", self.0)
    }
}
impl fmt::Debug for CspDeviceId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.to_be())
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
            creator_identity: group_identity.creator_identity.into(),
        }
    }
}

/// A specific conversation (aka _receiver_).
#[derive(Clone, Copy, Debug, Eq, PartialEq, Hash)]
pub enum ConversationId {
    /// A specific 1:1 contact.
    Contact(ThreemaId),
    /// A specific distribution list.
    DistributionList(u64),
    /// A specific group.
    Group(GroupIdentity),
}
impl TryFrom<&protobuf::d2d::conversation_id::Id> for ConversationId {
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
impl From<&ConversationId> for protobuf::d2d::ConversationId {
    fn from(conversation: &ConversationId) -> Self {
        let id = match conversation {
            ConversationId::Contact(contact_identity) => {
                protobuf::d2d::conversation_id::Id::Contact(contact_identity.as_str().to_owned())
            },
            ConversationId::DistributionList(distribution_list_id) => {
                protobuf::d2d::conversation_id::Id::DistributionList(*distribution_list_id)
            },
            ConversationId::Group(group_identity) => {
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

/// A 24-byte nonce for use with XSalsa20Poly1305 or XChaCha20Poly1305.
#[derive(Clone, Eq, PartialEq, Hash, Name)]
pub struct Nonce(pub [u8; Self::LENGTH]);
impl Nonce {
    /// Byte length of a nonce.
    pub const LENGTH: usize = 24;

    /// Generate a random nonce
    #[must_use]
    pub fn random() -> Self {
        let mut nonce = Self([0_u8; Self::LENGTH]);
        rand::thread_rng().fill(&mut nonce.0);
        nonce
    }

    #[cfg(test)]
    pub(crate) fn from_hex(string: &str) -> anyhow::Result<Self> {
        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; Self::LENGTH] = bytes.as_slice().try_into()?;
        Ok(Self(bytes))
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
impl fmt::Display for Nonce {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(&self.0))
    }
}
impl fmt::Debug for Nonce {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
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

    #[cfg(test)]
    pub(crate) fn from_hex(string: &str) -> Result<Self, ParseIntError> {
        Ok(Self(u64::from_str_radix(string, 16)?.to_be()))
    }

    /// Byte representation of the Message ID.
    #[inline]
    #[must_use]
    pub fn to_bytes(self) -> [u8; Self::LENGTH] {
        u64::to_le_bytes(self.0)
    }
}
impl fmt::Display for MessageId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{:016x}", self.0.to_be())
    }
}
impl fmt::Debug for MessageId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}
impl From<[u8; Self::LENGTH]> for MessageId {
    fn from(message_id: [u8; Self::LENGTH]) -> Self {
        Self(u64::from_le_bytes(message_id))
    }
}

/// Message flags which were/are transmitted to the server.
#[derive(Clone, Copy, Default, Name, PartialEq, Eq)]
pub struct MessageFlags(pub u8);
#[rustfmt::skip]
impl MessageFlags {
    pub(crate) const LENGTH: usize = 1;

    /// Whether a push should be sent by the chat server. Only meaningful for an outgoing message.
    pub const SEND_PUSH_NOTIFICATION:u8 =          0b_0000_0001;

    /// Whether the chat server should discard the message in case the receiver is not currently connected to
    /// the chat server. Only meaningful for an outgoing message.
    pub const NO_SERVER_QUEUING: u8 =              0b_0000_0010;

    /// Whether the message should not be acknowledged by the chat server (outgoing) or by the client
    /// (incoming).
    pub const NO_SERVER_ACKNOWLEDGEMENT: u8 =      0b_0000_0100;

    // Reserved:                                   0b_0000_1000
    // Reserved (formerly _group message marker_): 0b_0001_0000

    /// Instructs the chat server to only queue the message for a short period of time (currently 60 seconds).
    /// Only meaningful for an outgoing message.
    pub const SHORT_LIVED_SERVER_QUEUING: u8 =     0b_0010_0000;

    // Reserved:                                   0b_0100_0000

    /// If present, overrides behaviour of messages that would normally trigger a delivery receipt of type
    /// _received_ or _read_.
    pub const NO_DELIVERY_RECEIPTS: u8 =           0b_1000_0000;
}
impl fmt::Debug for MessageFlags {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let check_flag = |flag: u8, name: &'static str| -> Option<&'static str> {
            if self.0 & flag != 0 { Some(name) } else { None }
        };

        // Keep this format in sync with [`MessageOverrides`]!
        write!(
            formatter,
            "{}({})",
            Self::NAME,
            itertools::join(
                [
                    check_flag(Self::SEND_PUSH_NOTIFICATION, "push"),
                    check_flag(Self::NO_SERVER_QUEUING, "no-queue"),
                    check_flag(Self::NO_SERVER_ACKNOWLEDGEMENT, "no-ack"),
                    check_flag(Self::SHORT_LIVED_SERVER_QUEUING, "short-lived"),
                    check_flag(Self::NO_DELIVERY_RECEIPTS, "no-receipts"),
                ]
                .into_iter()
                .flatten(),
                ", ",
            ),
        )
    }
}

/// Metadata associated to a message.
#[derive(Debug, Clone)]
pub struct MessageMetadata {
    /// Unique message ID. Must match the message ID of the outer struct.
    pub message_id: MessageId,
    /// Unix-ish timestamp in milliseconds for when the message has been created.
    pub created_at: u64,
    /// Nickname of the sender at the time the message had been created.
    pub nickname: Delta<String>,
}
impl MessageMetadata {
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
impl From<MessageMetadata> for Vec<u8> {
    fn from(metadata: MessageMetadata) -> Self {
        let metadata = protobuf::csp_e2e::MessageMetadata {
            #[expect(deprecated, reason = "Will be filled by encode_to_vec_padded")]
            padding: vec![],
            message_id: metadata.message_id.0,
            created_at: metadata.created_at,
            nickname: metadata.nickname.into_non_empty(),
        };
        metadata.encode_to_vec_padded()
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
    /// Maps a `Delta<T>` to `Delta<U>` by applying a function to a contained value (if `Update`) or returns
    /// `Unchanged` or `Remove` respectively.
    pub(crate) fn map<U, F: FnOnce(T) -> U>(self, transform_fn: F) -> Delta<U> {
        match self {
            Delta::Unchanged => Delta::Unchanged,
            Delta::Update(value) => Delta::Update(transform_fn(value)),
            Delta::Remove => Delta::Remove,
        }
    }

    /// Converts from [`&Delta<T>`](`Delta<T>`) to [`Delta<&T>`].
    #[inline]
    pub(crate) const fn as_ref(&self) -> Delta<&T> {
        match &self {
            Self::Unchanged => Delta::Unchanged,
            Self::Update(value) => Delta::Update(value),
            Self::Remove => Delta::Remove,
        }
    }

    /// Converts from [`Delta<T>`] (or [`&Delta<T>`](`Delta<T>`)) to `Delta<&T::Target>`.
    #[expect(dead_code, reason = "May use later")]
    #[inline]
    pub(crate) fn as_deref(&self) -> Delta<&T::Target>
    where
        T: Deref,
    {
        self.as_ref().map(Deref::deref)
    }
}
impl<T> Apply<Delta<T>> for Option<T> {
    /// Apply the delta update to self.
    ///
    /// - [`Delta::Unchanged`] does nothing,
    /// - [`Delta::Update`] replaces any value in self with `Some(T)`,
    /// - [`Delta::Remove`] replaces any value in self with `None`.
    #[inline]
    fn apply(&mut self, value: Delta<T>) {
        match value {
            Delta::Unchanged => {},
            Delta::Update(value) => {
                let _ = self.insert(value);
            },
            Delta::Remove => {
                let _ = self.take();
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
impl<T: Default + Eq> Delta<T> {
    /// Creates a [`Delta<T>`] from a source where the `T::Default` is semantically equivalent to
    /// [`Delta::Remove`].
    pub(crate) fn from_non_empty(update: Option<T>) -> Self {
        match update {
            Some(update) => {
                if update == T::default() {
                    Self::Remove
                } else {
                    Self::Update(update)
                }
            },
            None => Self::Unchanged,
        }
    }

    /// Creates a [`Option<T>`] from the delta update where [`Delta::Remove`] is converted into `T::Default`.
    ///
    /// WARNING: A [`Delta::Update`] should never contain a `T::Default`, since the resulting [`Option<T>`] is
    /// recognized as a [`Delta::Remove`] when converted back via [`Delta<T>::from_non_empty`].
    pub(crate) fn into_non_empty(self) -> Option<T> {
        match self {
            Self::Unchanged => None,
            Self::Update(value) => {
                if value == T::default() {
                    warn!("Delta::Update contained T::Default");
                }
                Some(value)
            },
            Self::Remove => Some(T::default()),
        }
    }
}
