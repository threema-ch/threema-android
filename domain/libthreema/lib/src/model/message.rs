//! Message structs.
use core::{
    fmt,
    str::{self, FromStr as _, Split, Utf8Error},
};

use duplicate::duplicate_item;
use educe::Educe;
use libthreema_macros::Name;
use rand::Rng as _;
use tracing::warn;

use crate::{
    common::{GroupIdentity, MessageFlags, MessageId, ThreemaId},
    protobuf::common::CspE2eMessageType,
    utils::{
        apply::Apply,
        bytes::{ByteReader, ByteReaderError, ByteWriter, ByteWriterError, SliceByteReader},
        debug::{Name as _, debug_slice_length},
        number::CheckedExactDiv as _,
    },
};

/// The minimum amount of padding to add to messages.
const MESSAGE_DATA_PADDING_LENGTH_MIN: u8 = 32;

/// An error occurred while processing an incoming message.
#[derive(Clone, Debug, thiserror::Error)]
pub enum IncomingMessageError {
    /// Unable to decode a message.
    #[error("Decoding failed: {0}")]
    DecodingFailed(#[from] ByteReaderError),

    /// Invalid UTF-8 contained within the message.
    #[error("Invalid UTF-8: {0}")]
    InvalidString(#[from] Utf8Error),

    /// Invalid message.
    #[error("Invalid message: {0}")]
    InvalidMessage(String),
}

/// Lifetime of the message on the server.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum MessageLifetime {
    /// The message is kept indefinitely until received.
    Indefinite,

    /// The message is kept for a short amount of time until it is silently dropped (usually 30s).
    #[expect(dead_code, reason = "Will use later")]
    Brief,

    /// The message is only transmitted if the receiver is currently online.
    ///
    /// This is a combination of the _no server queuing_ and _no server acknowledgement_ flags
    /// which are only used in conjunction.
    Ephemeral,
}

/// Message properties associated to a message type.
#[expect(
    clippy::struct_excessive_bools,
    reason = "The one place where it's reasonable"
)]
#[derive(Debug, Clone, Name)]
#[cfg_attr(test, derive(PartialEq))]
pub(crate) struct MessageProperties {
    /// Whether the message should be/has been pushed.
    pub(crate) push: bool,

    /// Lifetime of the message on the server.
    pub(crate) lifetime: MessageLifetime,

    /// Whether the message requires user profile distribution.
    pub(crate) user_profile_distribution: bool,

    /// Whether the message requires to create a _direct_ contact upon reception.
    pub(crate) requires_direct_contact: bool,

    /// Whether the message requires replay protection (by storing the nonce).
    pub(crate) replay_protection: bool,

    /// Whether the message should be reflected in case it is incoming.
    pub(crate) reflect_incoming: bool,

    /// Whether the message should be reflected in case it is outgoing.
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) reflect_outgoing: bool,

    /// Whether an update that the message has been _sent_ should be reflected (in case it is
    /// outgoing).
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) reflect_sent_update: bool,

    /// Whether delivery receipts may be sent in response to this message.
    pub(crate) delivery_receipts: bool,
}
impl Apply<MessageProperties> for MessageFlags {
    fn apply(&mut self, value: MessageProperties) {
        if value.push {
            self.0 |= MessageFlags::SEND_PUSH_NOTIFICATION;
        }
        match value.lifetime {
            MessageLifetime::Indefinite => {},
            MessageLifetime::Brief => {
                self.0 |= MessageFlags::SHORT_LIVED_SERVER_QUEUING;
            },
            MessageLifetime::Ephemeral => {
                self.0 |= MessageFlags::NO_SERVER_QUEUING | MessageFlags::NO_SERVER_ACKNOWLEDGEMENT;
            },
        }
    }
}

/// Message properties associated to a group message type.
///
/// This is a reduced version of [`MessageProperties`].
#[expect(
    clippy::struct_excessive_bools,
    reason = "The one place where it's reasonable"
)]
struct GroupMessageProperties {
    push: bool,
    lifetime: MessageLifetime,
    user_profile_distribution: bool,
    replay_protection: bool,
    reflect_incoming: bool,
    reflect_outgoing: bool,
    reflect_sent_update: bool,
}
impl GroupMessageProperties {
    const fn into_message_properties(self) -> MessageProperties {
        MessageProperties {
            push: self.push,
            lifetime: self.lifetime,
            user_profile_distribution: self.user_profile_distribution,
            requires_direct_contact: false,
            replay_protection: self.replay_protection,
            reflect_incoming: self.reflect_incoming,
            reflect_outgoing: self.reflect_outgoing,
            reflect_sent_update: self.reflect_sent_update,
            delivery_receipts: false,
        }
    }
}

/// Message behaviour overrides that need to be stored.
#[derive(Clone, Copy, Default, Name)]
#[cfg_attr(test, derive(PartialEq))]
pub struct MessageOverrides {
    /// Whether delivery receipts should be omitted. If `true`, this overrides the default behaviour.
    pub disable_delivery_receipts: bool,
}
impl Apply<MessageOverrides> for MessageProperties {
    fn apply(&mut self, value: MessageOverrides) {
        if value.disable_delivery_receipts {
            self.delivery_receipts = false;
        }
    }
}
impl Apply<MessageOverrides> for MessageFlags {
    fn apply(&mut self, value: MessageOverrides) {
        if value.disable_delivery_receipts {
            self.0 |= MessageFlags::NO_DELIVERY_RECEIPTS;
        }
    }
}
impl fmt::Debug for MessageOverrides {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        // Keep this format in sync with [`MessageFlags`]!
        write!(
            formatter,
            "{}({})",
            Self::NAME,
            itertools::join(
                [self.disable_delivery_receipts.then_some("no-receipts")]
                    .into_iter()
                    .flatten(),
                ", ",
            ),
        )
    }
}
impl From<MessageFlags> for MessageOverrides {
    fn from(flags: MessageFlags) -> Self {
        Self {
            disable_delivery_receipts: flags.0 & MessageFlags::NO_DELIVERY_RECEIPTS != 0,
        }
    }
}

/// A text message.
#[derive(Clone, Educe)]
#[educe(Debug)]
#[cfg_attr(test, derive(PartialEq))]
pub struct TextMessage {
    /// Text of the message.
    #[educe(Debug(method(debug_slice_length)))]
    pub text: String,
}
impl TextMessage {
    const CONTACT_EXEMPT_FROM_BLOCKING: bool = false;
    const CONTACT_PROPERTIES: MessageProperties = MessageProperties {
        push: true,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        requires_direct_contact: true,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: true,
        delivery_receipts: true,
    };
    const CONTACT_TYPE: CspE2eMessageType = CspE2eMessageType::Text;
    const GROUP_EXEMPT_FROM_BLOCKING: bool = false;
    const GROUP_PROPERTIES: GroupMessageProperties = GroupMessageProperties {
        push: true,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: true,
    };
    const GROUP_TYPE: CspE2eMessageType = CspE2eMessageType::GroupText;

    fn decode(reader: &mut impl ByteReader) -> Result<Self, IncomingMessageError> {
        let text = str::from_utf8(reader.read_remaining())?;
        Ok(Self {
            text: text.to_owned(),
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        writer.write(self.text.as_bytes())
    }
}

/// A location message.
#[derive(Clone, Name)]
#[cfg_attr(test, derive(PartialEq))]
pub struct LocationMessage {
    /// Latitude.
    pub latitude: f64,

    /// Longitude.
    pub longitude: f64,

    /// Accuracy of the original sender's position in meters.
    pub accuracy_m: Option<f64>,

    /// Closest name of the location or a point of interest.
    pub name: Option<String>,

    /// An arbitrary address, not following any standardised pattern. May contain line breaks.
    pub address: Option<String>,
}
impl LocationMessage {
    const CONTACT_EXEMPT_FROM_BLOCKING: bool = false;
    const CONTACT_PROPERTIES: MessageProperties = MessageProperties {
        push: true,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        requires_direct_contact: true,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: true,
        delivery_receipts: true,
    };
    const CONTACT_TYPE: CspE2eMessageType = CspE2eMessageType::Location;
    const GROUP_EXEMPT_FROM_BLOCKING: bool = false;
    const GROUP_PROPERTIES: GroupMessageProperties = GroupMessageProperties {
        push: true,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: true,
    };
    const GROUP_TYPE: CspE2eMessageType = CspE2eMessageType::Location;

    fn decode(reader: &mut impl ByteReader) -> Result<Self, IncomingMessageError> {
        let raw = str::from_utf8(reader.read_remaining())?;
        let mut lines = raw.split('\n');

        // Extract latitude, longitude and optional accuracy from this beautifully formatted soup of strings
        let (latitude, longitude, accuracy_m) = {
            let decode_next_f64 = |name: &'static str, values: &mut Split<'_, char>| {
                f64::from_str(values.next().ok_or_else(|| {
                    IncomingMessageError::InvalidMessage(format!("Missing {name} in location message"))
                })?)
                .map_err(|error| {
                    IncomingMessageError::InvalidMessage(format!(
                        "Invalid {name} in location message: {error}"
                    ))
                })
            };

            let mut values = lines
                .next()
                .expect("lines must containt at least one line")
                .split(',');
            let latitude = decode_next_f64("latitude", &mut values)?;
            let longitude = decode_next_f64("longitude", &mut values)?;
            let accuracy_m = decode_next_f64("accuracy", &mut values).ok();

            (latitude, longitude, accuracy_m)
        };

        // Extract name and address, which could have been stirred either way /chef-kiss
        let (name, address) = {
            let line1 = lines.next();
            let line2 = lines.next();
            match (line1, line2) {
                (None, _) => (None, None),
                (Some(address), None) => (None, Some(address)),
                (Some(name), Some(address)) => (Some(name.to_owned()), Some(address)),
            }
        };

        // Unescape all the escaped new-lines from the address to remove some of the spicyness
        let address = address.map(|address| address.replace("\\n", "\n"));

        // Done serving this exquisite menu
        Ok(LocationMessage {
            latitude,
            longitude,
            accuracy_m,
            name,
            address,
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        // Write langitude, longitude and optional accuracy
        writer.write(self.latitude.to_string().as_bytes())?;
        writer.write(",".as_bytes())?;
        writer.write(self.longitude.to_string().as_bytes())?;
        if let Some(accuracy_m) = self.accuracy_m {
            writer.write(",".as_bytes())?;
            writer.write(accuracy_m.to_string().as_bytes())?;
        }

        // Write name and address (if any)
        match (
            &self.name,
            self.address.as_ref().map(|address| address.replace('\n', "\\n")),
        ) {
            (None, None) => {},
            (None, Some(address)) => {
                writer.write("\n".as_bytes())?;
                writer.write(address.as_bytes())?;
            },
            (Some(name), None) => {
                warn!(
                    name,
                    "Discarding name in location message without an associated address"
                );
            },
            (Some(name), Some(address)) => {
                writer.write("\n".as_bytes())?;
                writer.write(name.as_bytes())?;
                writer.write("\n".as_bytes())?;
                writer.write(address.as_bytes())?;
            },
        }
        Ok(())
    }
}
impl fmt::Debug for LocationMessage {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(Self::NAME)
    }
}

/// The type of delivery receipt.
#[derive(Debug, Clone, Copy, strum::FromRepr)]
#[repr(u8)]
#[cfg_attr(test, derive(PartialEq))]
pub enum DeliveryReceiptType {
    /// The referred messages have been received (i.e. it was delivered to the recipient).
    Received = 0x01,
    /// The referred messages have been read.
    Read = 0x02,
}

/// The type of legacy reaction.
#[derive(Debug, Clone, Copy, strum::FromRepr)]
#[repr(u8)]
#[cfg_attr(test, derive(PartialEq))]
pub enum LegacyReactionType {
    /// The user reacted with _acknowledge_ (i.e. _thumbs up_) to the messages.
    Acknowledge = 0x03,
    /// The user reacted with _decline_ (i.e. _thumbs down_) to the messages.
    Decline = 0x04,
}

/// Mapper for deciding between decoding into/encoding from [`DeliveryReceiptMessage`] or
/// [`LegacyReactionMessage`].
enum DeliveryReceiptMapper {
    DeliveryReceipt(DeliveryReceiptType),
    LegacyReaction(LegacyReactionType),
}
impl DeliveryReceiptMapper {
    fn decode_type(reader: &mut impl ByteReader) -> Result<DeliveryReceiptMapper, IncomingMessageError> {
        let r#type = reader.read_u8()?;
        let r#type = match DeliveryReceiptType::from_repr(r#type) {
            Some(r#type) => DeliveryReceiptMapper::DeliveryReceipt(r#type),
            None => DeliveryReceiptMapper::LegacyReaction(LegacyReactionType::from_repr(r#type).ok_or(
                IncomingMessageError::InvalidMessage(format!("Unknown delivery receipt type: 0x{type:02x}")),
            )?),
        };
        Ok(r#type)
    }

    #[inline]
    fn decode_rest(reader: &mut impl ByteReader) -> Result<Vec<MessageId>, IncomingMessageError> {
        // Decode message ids
        let n_messages_ids = reader.remaining().checked_exact_div_(MessageId::LENGTH).ok_or(
            IncomingMessageError::InvalidMessage(
                "Misaligned message ids in delivery receipt / legacy reaction".to_owned(),
            ),
        )?;
        let mut messages_ids: Vec<MessageId> = Vec::with_capacity(n_messages_ids);
        for _ in 0..n_messages_ids {
            messages_ids.push(MessageId::from(reader.read_fixed::<{ MessageId::LENGTH }>()?));
        }
        Ok(messages_ids)
    }

    #[inline]
    fn encode_into(
        r#type: u8,
        message_ids: &[MessageId],
        writer: &mut impl ByteWriter,
    ) -> Result<(), ByteWriterError> {
        writer.write_u8(r#type)?;
        for message_id in message_ids {
            writer.write(&message_id.to_bytes())?;
        }
        Ok(())
    }
}

/// A delivery receipt.
///
/// Note: This only covers real delivery receipts, not legacy reactions. These are always mapped to and from
/// [`LegacyReactionMessage`].
#[derive(Clone, Educe)]
#[educe(Debug)]
#[cfg_attr(test, derive(PartialEq))]
pub struct DeliveryReceiptMessage {
    /// Type of delivery receipt.
    pub receipt_type: DeliveryReceiptType,

    /// Message ids of referred messages the receipt type should be applied to.
    pub message_ids: Vec<MessageId>,
}
impl DeliveryReceiptMessage {
    const CONTACT_EXEMPT_FROM_BLOCKING: bool = false;
    const CONTACT_PROPERTIES: MessageProperties = MessageProperties {
        push: false,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: false,
        requires_direct_contact: false,
        replay_protection: false,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: false,
        delivery_receipts: false,
    };
    const CONTACT_TYPE: CspE2eMessageType = CspE2eMessageType::DeliveryReceipt;

    fn decode(
        receipt_type: DeliveryReceiptType,
        reader: &mut impl ByteReader,
    ) -> Result<Self, IncomingMessageError> {
        Ok(Self {
            receipt_type,
            message_ids: DeliveryReceiptMapper::decode_rest(reader)?,
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        DeliveryReceiptMapper::encode_into(self.receipt_type as u8, &self.message_ids, writer)
    }
}

/// A legacy reaction message.
///
/// Note: Delivery receipts containing legacy reactions are always mapped to and from this message internally.
#[derive(Clone, Educe)]
#[educe(Debug)]
#[cfg_attr(test, derive(PartialEq))]
pub struct LegacyReactionMessage {
    /// Type of legacy reaction.
    pub reaction_type: LegacyReactionType,

    /// Message ids of referred messages the reaction should be applied to.
    pub message_ids: Vec<MessageId>,
}
impl LegacyReactionMessage {
    const CONTACT_EXEMPT_FROM_BLOCKING: bool = false;
    const CONTACT_PROPERTIES: MessageProperties = MessageProperties {
        push: false,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        requires_direct_contact: false,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: false,
        delivery_receipts: false,
    };
    const CONTACT_TYPE: CspE2eMessageType = CspE2eMessageType::DeliveryReceipt;
    const GROUP_EXEMPT_FROM_BLOCKING: bool = false;
    const GROUP_PROPERTIES: GroupMessageProperties = GroupMessageProperties {
        push: false,
        lifetime: MessageLifetime::Indefinite,
        user_profile_distribution: true,
        replay_protection: true,
        reflect_incoming: true,
        reflect_outgoing: true,
        reflect_sent_update: false,
    };
    const GROUP_TYPE: CspE2eMessageType = CspE2eMessageType::GroupDeliveryReceipt;

    fn decode(
        reaction_type: LegacyReactionType,
        reader: &mut impl ByteReader,
    ) -> Result<Self, IncomingMessageError> {
        Ok(Self {
            reaction_type,
            message_ids: DeliveryReceiptMapper::decode_rest(reader)?,
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        DeliveryReceiptMapper::encode_into(self.reaction_type as u8, &self.message_ids, writer)
    }
}

/// A control message from Threema Web, requesting a session to be resumed.
#[derive(Clone, Educe)]
#[educe(Debug)]
#[cfg_attr(test, derive(PartialEq))]
pub struct WebSessionResumeMessage {
    /// Raw `push-payload` of a `web-session-resume` message.
    #[educe(Debug(method(debug_slice_length)))]
    pub push_payload: Vec<u8>,
}
impl WebSessionResumeMessage {
    const CONTACT_EXEMPT_FROM_BLOCKING: bool = true;
    const CONTACT_PROPERTIES: MessageProperties = MessageProperties {
        push: false,
        lifetime: MessageLifetime::Ephemeral,
        user_profile_distribution: false,
        requires_direct_contact: false,
        replay_protection: true,
        reflect_incoming: false,
        reflect_outgoing: false,
        reflect_sent_update: false,
        delivery_receipts: false,
    };
    const CONTACT_TYPE: CspE2eMessageType = CspE2eMessageType::WebSessionResume;

    fn decode(reader: &mut impl ByteReader) -> Self {
        Self {
            push_payload: reader.read_remaining().to_vec(),
        }
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        writer.write(&self.push_payload)
    }
}

/// Message body of a 1:1 message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub enum ContactMessageBody {
    /// See [`TextMessage`].
    Text(TextMessage),

    /// See [`LocationMessage`].
    Location(LocationMessage),

    /// See [`DeliveryReceiptMessage`].
    DeliveryReceipt(DeliveryReceiptMessage),

    /// See [`LegacyReactionMessage`].
    LegacyReaction(LegacyReactionMessage),

    /// See [`WebSessionResumeMessage`].
    WebSessionResume(WebSessionResumeMessage),
    // TODO(LIB-16): Complete
}
impl ContactMessageBody {
    /// Get the 1:1 message's type.
    const fn message_type(&self) -> CspE2eMessageType {
        match &self {
            ContactMessageBody::Text(_) => TextMessage::CONTACT_TYPE,
            ContactMessageBody::Location(_) => LocationMessage::CONTACT_TYPE,
            ContactMessageBody::DeliveryReceipt(_) => DeliveryReceiptMessage::CONTACT_TYPE,
            ContactMessageBody::LegacyReaction(_) => LegacyReactionMessage::CONTACT_TYPE,
            ContactMessageBody::WebSessionResume(_) => WebSessionResumeMessage::CONTACT_TYPE,
        }
    }

    /// Get the 1:1 message's properties.
    const fn properties(&self) -> MessageProperties {
        match &self {
            ContactMessageBody::Text(_) => TextMessage::CONTACT_PROPERTIES,
            ContactMessageBody::Location(_) => LocationMessage::CONTACT_PROPERTIES,
            ContactMessageBody::DeliveryReceipt(_) => DeliveryReceiptMessage::CONTACT_PROPERTIES,
            ContactMessageBody::LegacyReaction(_) => LegacyReactionMessage::CONTACT_PROPERTIES,
            ContactMessageBody::WebSessionResume(_) => WebSessionResumeMessage::CONTACT_PROPERTIES,
        }
    }

    /// Determine whether the 1:1 message is exempt from blocking.
    const fn is_exempt_from_blocking(&self) -> bool {
        match &self {
            ContactMessageBody::Text(_) => TextMessage::CONTACT_EXEMPT_FROM_BLOCKING,
            ContactMessageBody::Location(_) => LocationMessage::CONTACT_EXEMPT_FROM_BLOCKING,
            ContactMessageBody::DeliveryReceipt(_) => DeliveryReceiptMessage::CONTACT_EXEMPT_FROM_BLOCKING,
            ContactMessageBody::LegacyReaction(_) => LegacyReactionMessage::CONTACT_EXEMPT_FROM_BLOCKING,
            ContactMessageBody::WebSessionResume(_) => WebSessionResumeMessage::CONTACT_EXEMPT_FROM_BLOCKING,
        }
    }
}

/// Message body of a distribution list message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub enum DistributionListMessageBody {
    /// See [`TextMessage`].
    Text(TextMessage),

    /// See [`LocationMessage`].
    Location(LocationMessage),
    // TODO(LIB-57): Complete
}
impl DistributionListMessageBody {
    /// Get the distribution list message's type.
    ///
    /// Note: These are always just 1:1 message types.
    const fn message_type(&self) -> CspE2eMessageType {
        match &self {
            DistributionListMessageBody::Text(_) => TextMessage::CONTACT_TYPE,
            DistributionListMessageBody::Location(_) => LocationMessage::CONTACT_TYPE,
        }
    }

    /// Get the distribution list message's properties.
    ///
    /// Note: The properties are identical to the 1:1 message's properties.
    const fn properties(&self) -> MessageProperties {
        match &self {
            DistributionListMessageBody::Text(_) => TextMessage::CONTACT_PROPERTIES,
            DistributionListMessageBody::Location(_) => LocationMessage::CONTACT_PROPERTIES,
        }
    }

    /// Determine whether the distribution list message is exempt from blocking.
    ///
    /// Note: This behaves identical to the respective 1:1 message.
    const fn is_exempt_from_blocking(&self) -> bool {
        match &self {
            DistributionListMessageBody::Text(_) => TextMessage::CONTACT_EXEMPT_FROM_BLOCKING,
            DistributionListMessageBody::Location(_) => LocationMessage::CONTACT_EXEMPT_FROM_BLOCKING,
        }
    }
}

/// Message body of a group message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub enum GroupMessageBody {
    /// See [`TextMessage`].
    Text(TextMessage),

    /// See [`LocationMessage`].
    Location(LocationMessage),

    /// See [`LegacyReactionMessage`].
    LegacyReaction(LegacyReactionMessage),
    // TODO(LIB-53): Complete
}
impl GroupMessageBody {
    /// Get the group message's type.
    const fn message_type(&self) -> CspE2eMessageType {
        match &self {
            GroupMessageBody::Text(_) => TextMessage::GROUP_TYPE,
            GroupMessageBody::Location(_) => LocationMessage::GROUP_TYPE,
            GroupMessageBody::LegacyReaction(_) => LegacyReactionMessage::GROUP_TYPE,
        }
    }

    /// Get the group message's properties.
    const fn properties(&self) -> GroupMessageProperties {
        match &self {
            GroupMessageBody::Text(_) => TextMessage::GROUP_PROPERTIES,
            GroupMessageBody::Location(_) => LocationMessage::GROUP_PROPERTIES,
            GroupMessageBody::LegacyReaction(_) => LegacyReactionMessage::GROUP_PROPERTIES,
        }
    }

    /// Determine whether the group message is exempt from blocking.
    #[inline]
    fn is_exempt_from_blocking(&self) -> bool {
        match &self {
            GroupMessageBody::Text(_) => TextMessage::GROUP_EXEMPT_FROM_BLOCKING,
            GroupMessageBody::Location(_) => LocationMessage::GROUP_EXEMPT_FROM_BLOCKING,
            GroupMessageBody::LegacyReaction(_) => LegacyReactionMessage::GROUP_EXEMPT_FROM_BLOCKING,
        }
    }
}

/// An incoming group message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct IncomingGroupMessageBody {
    /// The specific group identity.
    pub group_identity: GroupIdentity,

    /// The message's body.
    pub body: GroupMessageBody,
}
impl IncomingGroupMessageBody {
    #[inline]
    #[expect(unused, reason = "TODO(LIB-53)")]
    fn decode_group_creator_header(
        sender_identity: ThreemaId,
        reader: &mut impl ByteReader,
    ) -> Result<GroupIdentity, IncomingMessageError> {
        let group_id = reader.read_u64_le()?;
        Ok(GroupIdentity {
            group_id,
            creator_identity: sender_identity,
        })
    }

    #[inline]
    fn decode_group_member_header(
        reader: &mut impl ByteReader,
    ) -> Result<GroupIdentity, IncomingMessageError> {
        let creator_identity = ThreemaId::try_from(reader.read_fixed::<{ ThreemaId::LENGTH }>()?.as_slice())
            .map_err(|error| {
                IncomingMessageError::InvalidMessage(format!(
                    "Invalid group creator identity in group message header: {error}"
                ))
            })?;
        let group_id = reader.read_u64_le()?;
        Ok(GroupIdentity {
            group_id,
            creator_identity,
        })
    }
}

/// Incoming message bodies for their respective conversations.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub enum IncomingMessageBody {
    /// An incoming 1:1 message from a contact.
    Contact(ContactMessageBody),

    /// An incoming group message.
    Group(IncomingGroupMessageBody),
}
impl IncomingMessageBody {
    /// Try to decode the incoming message's body from the protocol type and the raw data.
    pub(crate) fn decode(
        message_type: CspE2eMessageType,
        message_data: &[u8],
    ) -> Result<Self, IncomingMessageError> {
        let mut reader = SliceByteReader::new(message_data);

        // Decode message body
        let message = match message_type {
            CspE2eMessageType::Text => {
                Self::Contact(ContactMessageBody::Text(TextMessage::decode(&mut reader)?))
            },

            CspE2eMessageType::Location => Self::Contact(ContactMessageBody::Location(
                LocationMessage::decode(&mut reader)?,
            )),

            CspE2eMessageType::DeliveryReceipt => {
                Self::Contact(match DeliveryReceiptMapper::decode_type(&mut reader)? {
                    DeliveryReceiptMapper::DeliveryReceipt(receipt_type) => {
                        ContactMessageBody::DeliveryReceipt(DeliveryReceiptMessage::decode(
                            receipt_type,
                            &mut reader,
                        )?)
                    },
                    DeliveryReceiptMapper::LegacyReaction(reaction_type) => {
                        ContactMessageBody::LegacyReaction(LegacyReactionMessage::decode(
                            reaction_type,
                            &mut reader,
                        )?)
                    },
                })
            },

            CspE2eMessageType::WebSessionResume => Self::Contact(ContactMessageBody::WebSessionResume(
                WebSessionResumeMessage::decode(&mut reader),
            )),

            CspE2eMessageType::GroupText => {
                let group_identity = IncomingGroupMessageBody::decode_group_member_header(&mut reader)?;
                let body = GroupMessageBody::Text(TextMessage::decode(&mut reader)?);
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            CspE2eMessageType::GroupLocation => {
                let group_identity = IncomingGroupMessageBody::decode_group_member_header(&mut reader)?;
                let body = GroupMessageBody::Location(LocationMessage::decode(&mut reader)?);
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            CspE2eMessageType::GroupDeliveryReceipt => {
                let group_identity = IncomingGroupMessageBody::decode_group_member_header(&mut reader)?;
                let body = match DeliveryReceiptMapper::decode_type(&mut reader)? {
                    DeliveryReceiptMapper::DeliveryReceipt(_) => {
                        return Err(IncomingMessageError::InvalidMessage(
                            "Unexpected group delivery receipt".to_owned(),
                        ));
                    },
                    DeliveryReceiptMapper::LegacyReaction(reaction_type) => GroupMessageBody::LegacyReaction(
                        LegacyReactionMessage::decode(reaction_type, &mut reader)?,
                    ),
                };
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            // TODO(LIB-16): Decode the rest
            _ => {
                return Err(IncomingMessageError::InvalidMessage(
                    "Ain't nobody got time to implement those incoming messages".to_owned(),
                ));
            },
        };

        // Done
        let _ = reader.expect_consumed()?;
        Ok(message)
    }

    /// Get the incoming message's type.
    const fn message_type(&self) -> CspE2eMessageType {
        match &self {
            IncomingMessageBody::Contact(body) => body.message_type(),
            IncomingMessageBody::Group(body) => body.body.message_type(),
        }
    }

    /// Get the incoming message's properties respective to the associated conversation.
    const fn properties(&self) -> MessageProperties {
        match &self {
            IncomingMessageBody::Contact(body) => body.properties(),
            IncomingMessageBody::Group(body) => body.body.properties().into_message_properties(),
        }
    }

    /// Determine whether the incoming message is exempt from blocking.
    fn is_exempt_from_blocking(&self) -> bool {
        match &self {
            IncomingMessageBody::Contact(body) => body.is_exempt_from_blocking(),
            IncomingMessageBody::Group(body) => body.body.is_exempt_from_blocking(),
        }
    }
}

/// An outgoing 1:1 message to a contact.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct OutgoingContactMessageBody {
    /// Identity of the receiver.
    pub receiver_identity: ThreemaId,

    /// The message's body.
    pub body: ContactMessageBody,
}
impl OutgoingContactMessageBody {
    /// Encode the 1:1 message.
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        match &self.body {
            ContactMessageBody::Text(message) => message.encode_into(writer),
            ContactMessageBody::Location(message) => message.encode_into(writer),
            ContactMessageBody::DeliveryReceipt(message) => message.encode_into(writer),
            ContactMessageBody::LegacyReaction(message) => message.encode_into(writer),
            ContactMessageBody::WebSessionResume(message) => message.encode_into(writer),
        }
    }
}

/// An outgoing distribution list message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct OutgoingDistributionListMessageBody {
    /// The specific distribution list identity.
    pub distribution_list_identity: u64,

    /// The message's body.
    pub body: DistributionListMessageBody,
}
impl OutgoingDistributionListMessageBody {
    /// Encode the distribution list message.
    ///
    /// Note: This behaves identical to the respective 1:1 message.
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        match &self.body {
            DistributionListMessageBody::Text(message) => message.encode_into(writer),
            DistributionListMessageBody::Location(message) => message.encode_into(writer),
        }
    }
}

/// An outgoing group message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct OutgoingGroupMessageBody {
    /// The specific group identity.
    pub group_identity: GroupIdentity,

    /// The message's body.
    pub body: GroupMessageBody,
}
impl OutgoingGroupMessageBody {
    /// Encode the group message with its respective header (either `group-creator-container` or
    /// `group-member-container`).
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        match &self.body {
            GroupMessageBody::Text(message) => {
                Self::encode_group_member_header_into(&self.group_identity, writer)?;
                message.encode_into(writer)?;
            },
            GroupMessageBody::Location(message) => {
                Self::encode_group_member_header_into(&self.group_identity, writer)?;
                message.encode_into(writer)?;
            },
            GroupMessageBody::LegacyReaction(message) => {
                Self::encode_group_member_header_into(&self.group_identity, writer)?;
                message.encode_into(writer)?;
            },
        }
        Ok(())
    }

    #[inline]
    #[expect(unused, reason = "TODO(LIB-53)")]
    fn encode_group_creator_header_into(
        group_identity: &GroupIdentity,
        writer: &mut impl ByteWriter,
    ) -> Result<(), ByteWriterError> {
        writer.write_u64_le(group_identity.group_id)
    }

    #[inline]
    fn encode_group_member_header_into(
        group_identity: &GroupIdentity,
        writer: &mut impl ByteWriter,
    ) -> Result<(), ByteWriterError> {
        writer.write(&group_identity.creator_identity.to_bytes())?;
        writer.write_u64_le(group_identity.group_id)
    }
}

/// Outgoing message bodies for their respective conversations.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub enum OutgoingMessageBody {
    /// An outgoing 1:1 message to a contact.
    Contact(OutgoingContactMessageBody),

    /// An outgoing distribution list message.
    DistributionList(OutgoingDistributionListMessageBody),

    /// An outgoing group message.
    Group(OutgoingGroupMessageBody),
}
impl OutgoingMessageBody {
    pub(crate) fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        // Encode type
        writer.write_u8(self.message_type() as u8)?;

        // Encode data
        let data_length = {
            let offset = writer.offset();
            match self {
                OutgoingMessageBody::Contact(body) => body.encode_into(writer),
                OutgoingMessageBody::DistributionList(body) => body.encode_into(writer),
                OutgoingMessageBody::Group(body) => body.encode_into(writer),
            }?;
            writer
                .offset()
                .checked_sub(offset)
                .expect("Encoded message data must be >= 0 bytes")
        };

        // Add PKCS#7 padding
        let padding_length: u8 = rand::thread_rng().gen_range(1..=255);
        let padding_length =
            if data_length.saturating_add(padding_length as usize) < MESSAGE_DATA_PADDING_LENGTH_MIN as usize
            {
                MESSAGE_DATA_PADDING_LENGTH_MIN.saturating_sub(data_length.try_into().expect(
                    "data_length + 1..=255 must be < MESSAGE_DATA_PADDING_LENGTH_MIN and therefore u8",
                ))
            } else {
                padding_length
            };
        writer
            .write_in_place(padding_length as usize)?
            .fill(padding_length);

        // Done
        Ok(())
    }

    /// Get the outgoing message's type.
    const fn message_type(&self) -> CspE2eMessageType {
        match &self {
            OutgoingMessageBody::Contact(body) => body.body.message_type(),
            OutgoingMessageBody::DistributionList(body) => body.body.message_type(),
            OutgoingMessageBody::Group(body) => body.body.message_type(),
        }
    }

    /// Get the outgoing message's properties respective to the associated conversation.
    const fn properties(&self) -> MessageProperties {
        match &self {
            OutgoingMessageBody::Contact(body) => body.body.properties(),
            OutgoingMessageBody::DistributionList(body) => body.body.properties(),
            OutgoingMessageBody::Group(body) => body.body.properties().into_message_properties(),
        }
    }

    /// Determine whether the outgoing message is exempt from blocking.
    fn is_exempt_from_blocking(&self) -> bool {
        match &self {
            OutgoingMessageBody::Contact(body) => body.body.is_exempt_from_blocking(),
            OutgoingMessageBody::DistributionList(body) => body.body.is_exempt_from_blocking(),
            OutgoingMessageBody::Group(body) => body.body.is_exempt_from_blocking(),
        }
    }
}

/// An incoming message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct IncomingMessage {
    /// Identity of the sender.
    pub sender_identity: ThreemaId,

    /// The ID of the message.
    pub id: MessageId,

    /// Behaviour overrides.
    pub overrides: MessageOverrides,

    /// Unix-ish timestamp in milliseconds for when the message has been created.
    pub created_at: u64,

    /// The message's body for the respective conversation.
    pub body: IncomingMessageBody,
}

/// An outgoing message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct OutgoingMessage {
    /// The ID of the message.
    pub id: MessageId,

    /// Behaviour overrides.
    pub overrides: MessageOverrides,

    /// Unix-ish timestamp in milliseconds for when the message has been created.
    pub created_at: u64,

    /// The message's body for the respective conversation.
    pub body: OutgoingMessageBody,
}
impl OutgoingMessage {
    /// Computes [`MessageFlags`] from the [`MessageProperties`] and then the [`MessageOverrides`].
    pub(crate) fn flags(&self) -> MessageFlags {
        MessageFlags::default()
            .chain_apply(self.properties())
            .chain_apply(self.overrides)
    }
}

#[duplicate_item(
    message_struct;
    [ IncomingMessage ];
    [ OutgoingMessage ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
impl message_struct {
    /// Get the message's type.
    #[inline]
    #[must_use]
    pub const fn message_type(&self) -> CspE2eMessageType {
        self.body.message_type()
    }

    /// Get the message's properties.
    ///
    /// IMPORTANT: This does **NOT** include the overrides from [`MessageOverrides`]!
    #[inline]
    const fn properties(&self) -> MessageProperties {
        self.body.properties()
    }

    /// Get the effective message's properties, i.e. those with the [`MessageOverrides`] applied.
    #[inline]
    pub(crate) fn effective_properties(&self) -> MessageProperties {
        self.properties().chain_apply(self.overrides)
    }

    /// Determine whether the message is exempt from blocking.
    #[inline]
    #[allow(unused, reason = "TODO(LIB-51)")]
    pub(crate) fn is_exempt_from_blocking(&self) -> bool {
        self.body.is_exempt_from_blocking()
    }
}
