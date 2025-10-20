//! Decoded incoming message.
use core::str::{self, Utf8Error};

use libthreema_macros::{DebugVariantNames, VariantNames};

use crate::{
    common::{MessageId, ThreemaId},
    protobuf::common::CspE2eMessageType,
    utils::bytes::{ByteReader, SliceByteReader},
};

/// An error occurred while processing an incoming message.
#[derive(Clone, Debug, thiserror::Error)]
pub enum IncomingMessageError {
    /// Invalid UTF-8.
    #[error("Invalid UTF-8: {0}")]
    InvalidString(#[from] Utf8Error),
}

/// Lifetime of the message on the server.
#[derive(Clone, Copy)]
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
#[derive(Clone)]
pub(crate) struct MessageProperties {
    /// The message's protocol type.
    pub(crate) message_type: CspE2eMessageType,

    /// Whether the message should be/has been pushed.
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) push: bool,

    /// Lifetime of the message on the server.
    pub(crate) lifetime: MessageLifetime,

    /// Whether the message requires user profile distribution.
    pub(crate) user_profile_distribution: bool,

    /// Whether the message requires to create a _direct_ contact upon reception.
    pub(crate) contact_creation: bool,

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

    /// Whether delivery receipts should be sent (if the message is incoming).
    pub(crate) delivery_receipts: bool,
}

/// Message behaviour overrides that need to be stored.
#[derive(Clone, Debug)]
pub struct MessageOverrides {
    /// Whether delivery receipts should be omitted. If `true`, this overrides the default behaviour.
    pub disable_delivery_receipts: bool,
}

/// A text message.
#[derive(Clone)]
pub struct TextMessage {
    /// Text of the message.
    pub text: String,
}
impl TextMessage {
    fn decode(reader: &mut impl ByteReader) -> Result<Self, IncomingMessageError> {
        let text = str::from_utf8(reader.read_remaining())?;
        Ok(Self {
            text: text.to_owned(),
        })
    }
}

/// A control message from Threema Web, requesting a session to be resumed.
#[derive(Clone)]
pub struct WebSessionResume(pub Vec<u8>);
impl WebSessionResume {
    fn decode(reader: &mut impl ByteReader) -> Self {
        Self(reader.read_remaining().to_vec())
    }
}

/// The message's body.
#[derive(Clone, VariantNames, DebugVariantNames)]
pub enum MessageBody {
    /// See [`TextMessage`].
    Text(TextMessage),
    /// See [`WebSessionResume`].
    WebSessionResume(WebSessionResume),
}
impl MessageBody {
    /// Try to decode the message's body from the protocol type and the raw data.
    pub(crate) fn decode(
        message_type: CspE2eMessageType,
        message_data: &[u8],
    ) -> Result<Self, IncomingMessageError> {
        let mut reader = SliceByteReader::new(message_data);
        Ok(match message_type {
            CspE2eMessageType::Text => MessageBody::Text(TextMessage::decode(&mut reader)?),
            CspE2eMessageType::WebSessionResume => {
                MessageBody::WebSessionResume(WebSessionResume::decode(&mut reader))
            },
            // TODO(LIB-16): Decode the rest
            #[expect(clippy::todo, reason = "WIP PoC")]
            _ => todo!(),
        })
    }
}

/// An incoming message.
#[derive(Clone, Debug)]
pub struct IncomingMessage {
    /// Identity of the sender.
    pub sender_identity: ThreemaId,

    /// The ID of the message.
    pub id: MessageId,

    /// Behaviour overrides.
    pub overrides: MessageOverrides,

    /// Unix-ish timestamp in milliseconds for when the message has been created.
    pub created_at: u64,

    /// The message's body.
    pub body: MessageBody,
}
impl IncomingMessage {
    /// Get the message's properties.
    pub(crate) fn properties(&self) -> MessageProperties {
        match &self.body {
            MessageBody::Text(_) => MessageProperties {
                message_type: CspE2eMessageType::Text,
                push: true,
                lifetime: MessageLifetime::Indefinite,
                user_profile_distribution: true,
                contact_creation: true,
                replay_protection: true,
                reflect_incoming: true,
                reflect_outgoing: true,
                reflect_sent_update: true,
                delivery_receipts: !self.overrides.disable_delivery_receipts,
            },
            MessageBody::WebSessionResume(_) => MessageProperties {
                message_type: CspE2eMessageType::WebSessionResume,
                push: false,
                lifetime: MessageLifetime::Ephemeral,
                user_profile_distribution: false,
                contact_creation: false,
                replay_protection: true,
                reflect_incoming: false,
                reflect_outgoing: false,
                reflect_sent_update: false,
                delivery_receipts: false,
            },
        }
    }

    /// Determine whether the message is exempt from blocking.
    pub(crate) fn is_exempt_from_blocking(&self) -> bool {
        match &self.body {
            MessageBody::WebSessionResume(_) | MessageBody::Text(_) => false,
        }
    }
}
