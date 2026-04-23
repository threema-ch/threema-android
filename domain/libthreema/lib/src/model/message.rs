//! Message structs.
use core::{fmt, str};

use duplicate::duplicate_item;
use educe::Educe;
use libthreema_macros::Name;

use crate::{
    common::{GroupIdentity, MessageFlags, MessageId, ThreemaId},
    protobuf::common::CspE2eMessageType,
    utils::{
        apply::Apply,
        debug::{Name as _, debug_slice_length},
    },
};

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

/// An outgoing distribution list message.
#[derive(Debug, Clone)]
#[cfg_attr(test, derive(PartialEq))]
pub struct OutgoingDistributionListMessageBody {
    /// The specific distribution list identity.
    pub distribution_list_identity: u64,

    /// The message's body.
    pub body: DistributionListMessageBody,
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
    /// Get the outgoing message's type.
    pub(crate) const fn message_type(&self) -> CspE2eMessageType {
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
