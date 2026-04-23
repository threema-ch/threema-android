//! Trait and implementations to encode/decode CSP E2E messages.
use core::str::{FromStr as _, Split, Utf8Error};

use rand::Rng as _;
use tracing::warn;

use crate::{
    common::{GroupIdentity, MessageId, ThreemaId},
    model::message::{
        ContactMessageBody, DeliveryReceiptMessage, DeliveryReceiptType, DistributionListMessageBody,
        GroupMessageBody, IncomingGroupMessageBody, IncomingMessageBody, LegacyReactionMessage,
        LegacyReactionType, LocationMessage, OutgoingContactMessageBody, OutgoingDistributionListMessageBody,
        OutgoingGroupMessageBody, OutgoingMessageBody, TextMessage, WebSessionResumeMessage,
    },
    protobuf::common::CspE2eMessageType,
    utils::{
        bytes::{ByteReader, ByteReaderError, ByteWriter, ByteWriterError, SliceByteReader},
        number::CheckedExactDiv as _,
    },
};

/// The minimum amount of padding to add to messages.
const MESSAGE_DATA_PADDING_LENGTH_MIN: u8 = 32;

/// An error occurred while decoding a message.
#[derive(Clone, Debug, thiserror::Error)]
pub enum CspMessageDecodeError {
    /// Decoding the message failed.
    #[error("Decoding message failed: {0}")]
    DecodingFailed(#[from] ByteReaderError),

    /// Invalid UTF-8 contained within the message.
    #[error("Invalid UTF-8: {0}")]
    InvalidString(#[from] Utf8Error),

    /// Invalid message.
    #[error("Invalid message: {0}")]
    InvalidMessage(String),
}

/// Decoder for CSP messages.
pub(crate) trait CspMessageDecoder: Sized {
    /// Decode a CSP E2E message from the protocol type and the raw data.
    fn decode(message_type: CspE2eMessageType, message_data: &[u8]) -> Result<Self, CspMessageDecodeError>;
}

/// Encoder for CSP messages.
pub(crate) trait CspMessageEncoder {
    /// Encode a message as a CSP E2E message into a `writer`.
    fn encode_into<TWriter: ByteWriter>(&self, writer: &mut TWriter) -> Result<(), ByteWriterError>;
}

trait InternalCspMessageDecoder: Sized {
    fn decode(reader: &mut impl ByteReader) -> Result<Self, CspMessageDecodeError>;
}

trait InternalCspMessageEncoder: Sized {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError>;
}

/// A protocol compatible `delivery-receipt` message, mapping either to [`DeliveryReceiptMessage`] or
/// [`LegacyReactionMessage`].
enum ProtocolDeliveryReceiptMessage {
    DeliveryReceipt(DeliveryReceiptMessage),
    LegacyReaction(LegacyReactionMessage),
}
impl ProtocolDeliveryReceiptMessage {
    #[inline]
    fn decode_message_ids(reader: &mut impl ByteReader) -> Result<Vec<MessageId>, CspMessageDecodeError> {
        let n_messages_ids = reader.remaining().checked_exact_div_(MessageId::LENGTH).ok_or(
            CspMessageDecodeError::InvalidMessage(
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
impl InternalCspMessageDecoder for ProtocolDeliveryReceiptMessage {
    #[inline]
    fn decode(reader: &mut impl ByteReader) -> Result<Self, CspMessageDecodeError> {
        let receipt_type = reader.read_u8()?;

        // Attempt to decode as a delivery receipt, fall back to a legacy reaction.
        if let Some(receipt_type) = DeliveryReceiptType::from_repr(receipt_type) {
            return Ok(ProtocolDeliveryReceiptMessage::DeliveryReceipt(
                DeliveryReceiptMessage {
                    receipt_type,
                    message_ids: Self::decode_message_ids(reader)?,
                },
            ));
        }
        if let Some(reaction_type) = LegacyReactionType::from_repr(receipt_type) {
            return Ok(ProtocolDeliveryReceiptMessage::LegacyReaction(
                LegacyReactionMessage {
                    reaction_type,
                    message_ids: Self::decode_message_ids(reader)?,
                },
            ));
        }

        Err(CspMessageDecodeError::InvalidMessage(format!(
            "Unknown delivery receipt type: {receipt_type:02x}"
        )))
    }
}

impl InternalCspMessageEncoder for DeliveryReceiptMessage {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        ProtocolDeliveryReceiptMessage::encode_into(self.receipt_type as u8, &self.message_ids, writer)
    }
}

impl InternalCspMessageEncoder for LegacyReactionMessage {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        ProtocolDeliveryReceiptMessage::encode_into(self.reaction_type as u8, &self.message_ids, writer)
    }
}

impl InternalCspMessageDecoder for TextMessage {
    fn decode(reader: &mut impl ByteReader) -> Result<Self, CspMessageDecodeError> {
        let text = str::from_utf8(reader.read_remaining())?;
        Ok(Self {
            text: text.to_owned(),
        })
    }
}
impl InternalCspMessageEncoder for TextMessage {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        writer.write(self.text.as_bytes())
    }
}

impl InternalCspMessageDecoder for LocationMessage {
    fn decode(reader: &mut impl ByteReader) -> Result<Self, CspMessageDecodeError> {
        let raw = str::from_utf8(reader.read_remaining())?;
        let mut lines = raw.split('\n');

        // Extract latitude, longitude and optional accuracy from this beautifully formatted soup of strings
        let (latitude, longitude, accuracy_m) = {
            let decode_next_f64 = |name: &'static str, values: &mut Split<'_, char>| {
                f64::from_str(values.next().ok_or_else(|| {
                    CspMessageDecodeError::InvalidMessage(format!("Missing {name} in location message"))
                })?)
                .map_err(|error| {
                    CspMessageDecodeError::InvalidMessage(format!(
                        "Invalid {name} in location message: {error}"
                    ))
                })
            };

            let mut values = lines
                .next()
                .expect("lines must contain at least one line")
                .split(',');
            let latitude = decode_next_f64("latitude", &mut values)?;
            let longitude = decode_next_f64("longitude", &mut values)?;
            let accuracy_m = decode_next_f64("accuracy", &mut values).ok();

            (latitude, longitude, accuracy_m)
        };

        // Extract name and address, which could have been stirred either way /chef-kiss.
        let (name, address) = {
            let line1 = lines.next();
            let line2 = lines.next();
            match (line1, line2) {
                (None, _) => (None, None),
                (Some(address), None) => (None, Some(address)),
                (Some(name), Some(address)) => (Some(name.to_owned()), Some(address)),
            }
        };

        // Unescape all the escaped new-lines from the address to remove some of the spiciness.
        let address = address.map(|address| address.replace("\\n", "\n"));

        // Done serving this exquisite menu.
        Ok(LocationMessage {
            latitude,
            longitude,
            accuracy_m,
            name,
            address,
        })
    }
}
impl InternalCspMessageEncoder for LocationMessage {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        // Write latitude, longitude and optional accuracy.
        writer.write(self.latitude.to_string().as_bytes())?;
        writer.write(",".as_bytes())?;
        writer.write(self.longitude.to_string().as_bytes())?;
        if let Some(accuracy_m) = self.accuracy_m {
            writer.write(",".as_bytes())?;
            writer.write(accuracy_m.to_string().as_bytes())?;
        }

        // Write name and address (if any).
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

impl InternalCspMessageDecoder for WebSessionResumeMessage {
    fn decode(reader: &mut impl ByteReader) -> Result<Self, CspMessageDecodeError> {
        Ok(Self {
            push_payload: reader.read_remaining().to_vec(),
        })
    }
}
impl InternalCspMessageEncoder for WebSessionResumeMessage {
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        writer.write(&self.push_payload)
    }
}

struct IncomingMessageBodyExtension;
impl IncomingMessageBodyExtension {
    #[inline]
    #[expect(unused, reason = "TODO(LIB-53)")]
    fn decode_group_creator_header(
        sender_identity: ThreemaId,
        reader: &mut impl ByteReader,
    ) -> Result<GroupIdentity, CspMessageDecodeError> {
        let group_id = reader.read_u64_le()?;
        Ok(GroupIdentity {
            group_id,
            creator_identity: sender_identity,
        })
    }

    #[inline]
    fn decode_group_member_header(
        reader: &mut impl ByteReader,
    ) -> Result<GroupIdentity, CspMessageDecodeError> {
        let creator_identity = ThreemaId::try_from(reader.read_fixed::<{ ThreemaId::LENGTH }>()?.as_slice())
            .map_err(|error| {
                CspMessageDecodeError::InvalidMessage(format!(
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

impl CspMessageDecoder for IncomingMessageBody {
    /// Try to decode the incoming message's body from the protocol type and the raw data.
    fn decode(message_type: CspE2eMessageType, message_data: &[u8]) -> Result<Self, CspMessageDecodeError> {
        let mut reader = SliceByteReader::new(message_data);

        // Decode message body.
        let message = match message_type {
            CspE2eMessageType::Text => {
                Self::Contact(ContactMessageBody::Text(TextMessage::decode(&mut reader)?))
            },

            CspE2eMessageType::Location => Self::Contact(ContactMessageBody::Location(
                LocationMessage::decode(&mut reader)?,
            )),

            CspE2eMessageType::DeliveryReceipt => {
                Self::Contact(match ProtocolDeliveryReceiptMessage::decode(&mut reader)? {
                    ProtocolDeliveryReceiptMessage::DeliveryReceipt(delivery_receipt) => {
                        ContactMessageBody::DeliveryReceipt(delivery_receipt)
                    },
                    ProtocolDeliveryReceiptMessage::LegacyReaction(legacy_reaction) => {
                        ContactMessageBody::LegacyReaction(legacy_reaction)
                    },
                })
            },

            CspE2eMessageType::WebSessionResume => Self::Contact(ContactMessageBody::WebSessionResume(
                WebSessionResumeMessage::decode(&mut reader)?,
            )),

            CspE2eMessageType::GroupText => {
                let group_identity = IncomingMessageBodyExtension::decode_group_member_header(&mut reader)?;
                let body = GroupMessageBody::Text(TextMessage::decode(&mut reader)?);
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            CspE2eMessageType::GroupLocation => {
                let group_identity = IncomingMessageBodyExtension::decode_group_member_header(&mut reader)?;
                let body = GroupMessageBody::Location(LocationMessage::decode(&mut reader)?);
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            CspE2eMessageType::GroupDeliveryReceipt => {
                let group_identity = IncomingMessageBodyExtension::decode_group_member_header(&mut reader)?;
                let body = match ProtocolDeliveryReceiptMessage::decode(&mut reader)? {
                    ProtocolDeliveryReceiptMessage::DeliveryReceipt(_) => {
                        return Err(CspMessageDecodeError::InvalidMessage(
                            "Unexpected group delivery receipt".to_owned(),
                        ));
                    },
                    ProtocolDeliveryReceiptMessage::LegacyReaction(legacy_reaction) => {
                        GroupMessageBody::LegacyReaction(legacy_reaction)
                    },
                };
                Self::Group(IncomingGroupMessageBody { group_identity, body })
            },

            // TODO(LIB-16): Decode the rest.
            _ => {
                return Err(CspMessageDecodeError::InvalidMessage(
                    "Ain't nobody got time to implement those incoming messages".to_owned(),
                ));
            },
        };

        // Done.
        let _ = reader.expect_consumed()?;
        Ok(message)
    }
}

struct OutgoingGroupMessageBodyExtension;
impl OutgoingGroupMessageBodyExtension {
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

impl InternalCspMessageEncoder for OutgoingGroupMessageBody {
    /// Encode the group message with its respective header (either `group-creator-container` or
    /// `group-member-container`).
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), ByteWriterError> {
        match &self.body {
            GroupMessageBody::Text(message) => {
                OutgoingGroupMessageBodyExtension::encode_group_member_header_into(
                    &self.group_identity,
                    writer,
                )?;
                message.encode_into(writer)?;
            },
            GroupMessageBody::Location(message) => {
                OutgoingGroupMessageBodyExtension::encode_group_member_header_into(
                    &self.group_identity,
                    writer,
                )?;
                message.encode_into(writer)?;
            },
            GroupMessageBody::LegacyReaction(message) => {
                OutgoingGroupMessageBodyExtension::encode_group_member_header_into(
                    &self.group_identity,
                    writer,
                )?;
                message.encode_into(writer)?;
            },
        }
        Ok(())
    }
}

impl InternalCspMessageEncoder for OutgoingContactMessageBody {
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

impl InternalCspMessageEncoder for OutgoingDistributionListMessageBody {
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

impl CspMessageEncoder for OutgoingMessageBody {
    fn encode_into<TWriter: ByteWriter>(&self, writer: &mut TWriter) -> Result<(), ByteWriterError> {
        // Encode type.
        writer.write_u8(self.message_type() as u8)?;

        // Encode data.
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

        // Add PKCS#7 padding.
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

        // Done.
        Ok(())
    }
}
