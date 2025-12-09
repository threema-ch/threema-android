//! Payloads exchanged during the post-handshake phase.
use educe::Educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::error;

use crate::{
    common::{CspDeviceId, MessageFlags, MessageId, ThreemaId},
    csp::{
        CspProtocolError, InternalErrorCause,
        payload::{FrameEncoder, OutgoingFrame},
    },
    utils::{
        bytes::{ByteReader, ByteWriter, OwnedVecByteReader, OwnedVecByteWriter},
        debug::{Name as _, debug_slice_length},
        frame::{FrameDelimiter as _, U16LittleEndianDelimiter, VariableLengthFrameDecoder},
    },
};

/// The payload of an echo request to be answered by a corresponding echo response.
#[derive(Clone, Name)]
pub struct EchoPayload(pub Vec<u8>);
impl EchoPayload {
    fn decode(reader: OwnedVecByteReader) -> Self {
        Self(reader.read_remaining_owned())
    }

    fn length(&self) -> usize {
        self.0.len()
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .write(&self.0)
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        Ok(())
    }
}

/// An end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
///
/// Note 1: For incoming messages, only essential parts for the CSP layer have been decoded here, so that it
/// can be acknowledged at need. The E2E layer is responsible for decoding and decrypting the rest.
///
/// Note 2: For outgoing messages, the E2E layer is responsible for encoding the message bytes.
#[derive(Educe, Name)]
#[educe(Debug)]
#[cfg_attr(test, derive(Clone))]
pub struct MessageWithMetadataBox {
    /// The sender's Threema ID.
    pub sender_identity: ThreemaId,

    /// The ID of the message.
    pub id: MessageId,

    /// Message flags of the message.
    pub flags: MessageFlags,

    /// Raw bytes of the payload.
    #[educe(Debug(method(debug_slice_length)))]
    pub bytes: Vec<u8>,
}
impl MessageWithMetadataBox {
    pub(crate) fn decode(mut reader: OwnedVecByteReader) -> Result<Self, CspProtocolError> {
        // Truncate to the payload, since we need the raw bytes later
        reader.truncate();

        // Decode the sender's identity, message ID and flags
        let (sender_identity, id, flags) = reader
            .run(|reader| {
                let sender_identity = reader.read_fixed::<{ ThreemaId::LENGTH }>()?;
                reader.skip(ThreemaId::LENGTH)?;
                let id = MessageId(reader.read_u64_le()?);
                reader.skip(4)?;
                let message_flags = MessageFlags(reader.read_u8()?);
                Ok((sender_identity, id, message_flags))
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?;

        let bytes = reader.into_inner();
        Ok(Self {
            sender_identity: ThreemaId::try_from(sender_identity.as_ref()).map_err(|error| {
                CspProtocolError::InvalidMessage {
                    name: Self::NAME,
                    cause: error.to_string(),
                }
            })?,
            id,
            flags,
            bytes,
        })
    }

    fn length(&self) -> usize {
        self.bytes.len()
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        // TODO(LIB-31): This additional copying could be avoided if we are fine with layer violations. We
        // could split this payload into two, keep the incoming variant as-is but add an outgoing one where we
        // reference `OutgoingMessageWithMetadataBox` instead. Then we could call
        // `OutgoingMessageWithMetadataBox::encode_into` directly.
        writer
            .write(&self.bytes)
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        Ok(())
    }
}

/// Acknowledges that a message has been received.
#[derive(Debug, Name)]
#[cfg_attr(test, derive(PartialEq))]
pub struct MessageAck {
    /// Identity of the sender (for an incoming message) or the receiver (for an outgoing message).
    pub sender_identity: ThreemaId,

    /// Refers to the message id of the acknowledged message.
    pub id: MessageId,
}
impl MessageAck {
    const LENGTH: usize = ThreemaId::LENGTH + MessageId::LENGTH;

    fn decode(reader: impl ByteReader) -> Result<Self, CspProtocolError> {
        let (identity, id) = reader
            .run_owned(|mut reader| {
                let sender_identity = reader.read_fixed::<{ ThreemaId::LENGTH }>()?;
                let id = reader.read_u64_le()?;
                let _ = reader.expect_consumed()?;
                Ok((sender_identity, id))
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?;

        Ok(Self {
            sender_identity: ThreemaId::try_from(identity.as_ref()).map_err(|error| {
                CspProtocolError::InvalidMessage {
                    name: Self::NAME,
                    cause: error.to_string(),
                }
            })?,
            id: MessageId(id),
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .run(|writer| {
                writer.write(&self.sender_identity.to_bytes())?;
                writer.write_u64_le(self.id.0)
            })
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        Ok(())
    }
}

/// APNs server type
#[derive(Clone, Copy)]
pub enum ApnsServerType {
    /// Production server of the APNs service.
    Production,
    /// Development server of the APNs service.
    Development,
}

#[derive(Clone, Copy)]
#[repr(u8)]
enum PushNotificationTokenType {
    None = 0x00,
    // LegacyApnsProduction = 0x01,
    // LegacyApnsDevelopment = 0x02,
    // LegacyApnsProductionWithContentAvailable = 0x03,
    // LegacyApnsDevelopmentWithContentAvailable = 0x04,
    ApnsProductionWithMutableContent = 0x05,
    ApnsDevelopmentWithMutableContent = 0x06,
    Fcm = 0x11,
    Hms = 0x13,
}
impl PushNotificationTokenType {
    const LENGTH: usize = 1;
}
impl From<ApnsServerType> for PushNotificationTokenType {
    fn from(r#type: ApnsServerType) -> Self {
        match r#type {
            ApnsServerType::Production => Self::ApnsProductionWithMutableContent,
            ApnsServerType::Development => Self::ApnsDevelopmentWithMutableContent,
        }
    }
}

/// Sets (or clears) the push notification token on the server.
#[derive(Name)]
pub enum PushNotificationToken {
    /// Clear any existing push notification token of this device
    Clear,

    /// Set an APNs push notification token
    ///
    /// Note: For readers with legacy knowledge, this is the one with the `mutable-content` key and the
    /// encryption key. No other variants are in use by modern clients.
    Apns {
        /// APNs server type
        r#type: ApnsServerType,
        /// Bundle ID of the app (e.g. `ch.threema.iapp` for consumer)
        bundle_id: String,
        /// Payload encryption key (XSalsa20)
        encryption_key: [u8; 32],
        /// APNs device token
        token: Vec<u8>,
    },

    /// Set an FCM push notification token
    Fcm(String),

    /// Set an HMS push notification token
    Hms {
        /// App ID (e.g. `103713829` for consumer)
        app_id: String,
        /// HMS push token
        token: String,
    },
}
impl PushNotificationToken {
    // Decoding messages is awkward as the separated binary data may contain a pipe delimiter, making the
    // delimiter totally useless. Still, we need it for legacy reasons...
    const AWKWARD_DELIMITER: &'static [u8; 1] = b"|";

    fn length(&self) -> usize {
        PushNotificationTokenType::LENGTH.saturating_add(match self {
            PushNotificationToken::Clear => 0,
            PushNotificationToken::Apns {
                bundle_id,
                encryption_key,
                token,
                ..
            } => token
                .len()
                .saturating_add(Self::AWKWARD_DELIMITER.len())
                .saturating_add(bundle_id.len())
                .saturating_add(Self::AWKWARD_DELIMITER.len())
                .saturating_add(encryption_key.len()),
            PushNotificationToken::Fcm(token) => token.len(),
            PushNotificationToken::Hms { app_id, token } => app_id
                .len()
                .saturating_add(Self::AWKWARD_DELIMITER.len())
                .saturating_add(token.len()),
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .run(|writer| match self {
                PushNotificationToken::Clear => writer.write_u8(PushNotificationTokenType::None as u8),
                PushNotificationToken::Apns {
                    r#type,
                    bundle_id,
                    encryption_key,
                    token,
                } => {
                    writer.write_u8(PushNotificationTokenType::from(*r#type) as u8)?;

                    // Note: We're lucky here since we only need to encode it. But if you need to decode,
                    // well... because both `encryption_key` and `token` are binary, it can contain a pipe
                    // delimiter, making the delimiter totally useless. You'll have to split off the 32 byte
                    // `encryption_key` and the delimiter from the end, then seek backwards for the next
                    // delimiter to be able to distinguish the `token` from the `bundle_id`.
                    writer.write(token)?;
                    writer.write(Self::AWKWARD_DELIMITER)?;
                    writer.write(bundle_id.as_bytes())?;
                    writer.write(Self::AWKWARD_DELIMITER)?;
                    writer.write(encryption_key)
                },
                PushNotificationToken::Fcm(token) => {
                    writer.write_u8(PushNotificationTokenType::Fcm as u8)?;

                    writer.write(token.as_bytes())
                },
                PushNotificationToken::Hms { app_id, token } => {
                    writer.write_u8(PushNotificationTokenType::Hms as u8)?;

                    writer.write(app_id.as_bytes())?;
                    writer.write(Self::AWKWARD_DELIMITER)?;
                    writer.write(token.as_bytes())
                },
            })
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        Ok(())
    }
}

/// Removes some specific device's push notification tokens or all device's existing push
/// notification tokens from the server.
#[derive(Name)]
pub enum DeletePushNotificationToken {
    /// Remove all device's push notification tokens.
    All,

    /// Remove push notification tokens of the provided device IDs.
    ///
    /// WARNING: This list may not be empty or it will produce an
    /// [`CspProtocolError::InvalidMessage`] when encoding!
    Devices(Vec<CspDeviceId>),
}
impl DeletePushNotificationToken {
    fn length(&self) -> usize {
        match self {
            DeletePushNotificationToken::All => 0,
            DeletePushNotificationToken::Devices(csp_device_ids) => {
                csp_device_ids.len().saturating_mul(CspDeviceId::LENGTH)
            },
        }
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        match self {
            DeletePushNotificationToken::All => Ok(()),
            DeletePushNotificationToken::Devices(csp_device_ids) => {
                if csp_device_ids.is_empty() {
                    return Err(CspProtocolError::InvalidParameter(const_format::formatcp!(
                        "Device list may not be empty for {}",
                        DeletePushNotificationToken::NAME
                    )));
                }
                writer
                    .run(|writer| {
                        for csp_device_id in csp_device_ids {
                            writer.write_u64_le(csp_device_id.0)?;
                        }
                        Ok(())
                    })
                    .map_err(|error| InternalErrorCause::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })?;
                Ok(())
            },
        }
    }
}

/// Set the connection idle timeout (in minutes)
#[derive(Name)]
pub struct ConnectionIdleTimeout(pub u16);
impl ConnectionIdleTimeout {
    const LENGTH: usize = 2;

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .write_u16_le(self.0)
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        Ok(())
    }
}

/// Indicates that the connection has experienced an unrecoverable error and must be closed.
#[derive(Name)]
pub struct CloseError {
    /// Indicates whether the client is allowed to reconnect automatically after the connection has
    /// been severed.
    pub can_reconnect: bool,

    /// Error message
    pub message: String,
}
impl CloseError {
    fn decode(mut reader: OwnedVecByteReader) -> Result<Self, CspProtocolError> {
        // The client may automatically reconnect iff the first byte is not zero
        let can_reconnect = reader
            .read_u8()
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?
            != 0;

        // Parse and decode the message
        let message = String::from_utf8(reader.read_remaining_owned()).map_err(|error| {
            CspProtocolError::InvalidMessage {
                name: Self::NAME,
                cause: error.to_string(),
            }
        })?;
        Ok(Self {
            can_reconnect,
            message,
        })
    }
}

/// Generic alert that should be displayed in the client's user interface.
#[derive(Debug, Name)]
pub struct ServerAlert(pub String);
impl ServerAlert {
    fn decode(reader: OwnedVecByteReader) -> Result<Self, CspProtocolError> {
        // Parse and decode the message
        let message = String::from_utf8(reader.read_remaining_owned()).map_err(|error| {
            CspProtocolError::InvalidMessage {
                name: Self::NAME,
                cause: error.to_string(),
            }
        })?;
        Ok(Self(message))
    }
}

const PAYLOAD_HEADER_RESERVED: [u8; 3] = [0_u8; 3];

#[derive(Clone, Copy, strum::FromRepr, VariantNames, DebugVariantNames)]
#[repr(u8)]
enum IncomingPayloadType {
    EchoRequest = 0x00,
    EchoResponse = 0x80,
    MessageWithMetadataBox = 0x02,
    MessageAck = 0x81,
    QueueSendComplete = 0xd0,
    DeviceCookieChangeIndication = 0xd2,
    CloseError = 0xe0,
    Alert = 0xe1,
}

/// An incoming payload received from the chat server during the post-handshake phase.
#[derive(Name, VariantNames, DebugVariantNames)]
pub enum IncomingPayload {
    /// An echo request to be answered by a corresponding echo response.
    ///
    /// Can be used for connection keep-alive or RTT estimation.
    EchoRequest(EchoPayload),

    /// An echo response corresponding to an echo request.
    EchoResponse(EchoPayload),

    /// An incoming end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
    MessageWithMetadataBox(MessageWithMetadataBox),

    /// Acknowledges that the referred outgoing message has been stored on the server.
    MessageAck(MessageAck),

    /// Indicates that the incoming message queue on the server has been fully transmitted to the client. A
    /// client should not disconnect prior to having received this payload.
    QueueSendComplete,

    /// Indicates to the client that a device cookie mismatch has been detected since the last time that the
    /// device cookie change indication has been cleared.
    DeviceCookieChangeIndication,

    /// Indicates that the connection has experienced an unrecoverable error and must be closed.
    CloseError(CloseError),

    /// Generic alert that should be displayed in the client's user interface.
    ServerAlert(ServerAlert),

    /// The payload type is not known, either due to server misbehavior or as consequence of running an old
    /// version of libthreema. This information might be helpful for debugging.
    UnknownPayload {
        /// The (unsupported) type of the payload
        payload_type: u8,

        /// The length of the received container
        length: usize,
    },
}
impl TryFrom<Vec<u8>> for IncomingPayload {
    type Error = CspProtocolError;

    fn try_from(data: Vec<u8>) -> Result<Self, CspProtocolError> {
        fn expect_empty_payload(
            reader: OwnedVecByteReader,
            r#type: IncomingPayloadType,
        ) -> Result<(), CspProtocolError> {
            let _ = reader
                .expect_consumed()
                .map_err(|error| CspProtocolError::DecodingFailed {
                    name: r#type.variant_name(),
                    source: error,
                })?;
            Ok(())
        }

        let mut reader = OwnedVecByteReader::new(data);
        let r#type = reader
            .run(|reader| {
                // Get the payload type
                let r#type = reader.read_u8()?;

                // Skip reserved bytes
                reader.skip(PAYLOAD_HEADER_RESERVED.len())?;

                Ok(r#type)
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?;

        // Parse payload type
        let Some(r#type) = IncomingPayloadType::from_repr(r#type) else {
            return Ok(Self::UnknownPayload {
                payload_type: r#type,
                length: reader.remaining(),
            });
        };

        // Create the corresponding variant by decoding the payload accordingly
        Ok(match r#type {
            IncomingPayloadType::EchoRequest => Self::EchoRequest(EchoPayload::decode(reader)),
            IncomingPayloadType::MessageWithMetadataBox => {
                Self::MessageWithMetadataBox(MessageWithMetadataBox::decode(reader)?)
            },
            IncomingPayloadType::EchoResponse => Self::EchoResponse(EchoPayload::decode(reader)),
            IncomingPayloadType::MessageAck => Self::MessageAck(MessageAck::decode(reader)?),
            IncomingPayloadType::QueueSendComplete => {
                expect_empty_payload(reader, r#type)?;
                Self::QueueSendComplete
            },
            IncomingPayloadType::DeviceCookieChangeIndication => {
                expect_empty_payload(reader, r#type)?;
                Self::DeviceCookieChangeIndication
            },
            IncomingPayloadType::CloseError => Self::CloseError(CloseError::decode(reader)?),
            IncomingPayloadType::Alert => Self::ServerAlert(ServerAlert::decode(reader)?),
        })
    }
}

#[cfg(test)]
mod incoming_payload_tests {
    use data_encoding::HEXLOWER;

    use super::IncomingPayload;
    use crate::common::{MessageFlags, MessageId, ThreemaId};

    #[test]
    fn valid_message() -> anyhow::Result<()> {
        let payload = HEXLOWER.decode(
            b"\
                02000000304441354d453736304850543945574489aa9a7eaff77d96cb7327680100340000000000\
                00000000000000000000000000000000000000000000000000000000439039d79074fa4a0d0961d6\
                51af57b94b7245c4c686733aab55b7f2b049fa3a8a5fec982eaa27d37557beaadd802cfc2703d849\
                b2d718b0db179e3e3bcdbf3c2be997490a0349f2e4fbaa43712e263aab0c2c4a920182f01f810df0\
                63363191b26c2c6404c0e84e73a3e005e327589702878e259642e1cf3b29e36db1c6a258f55ea73c\
                842fddafffd76a3057c0c13b6881bccc6522a0edee793f586fcb9ec5b398eb3be0af1a8c6111fe46\
                3ed25d916e66bea54955ca3398e27cbae25bfb6c16e26f326ecf8a4ba81aef9312b59f612b9e3355\
                de6c14c0434dc195e0a03462fc95d836a7bca74bda61d59be8489a9fdd9e626e7cb7324ac0724b0a\
                42168a5bea525eaef17d3bf13bbd8551ab8c85f5892fa6ba9c32e01343c3bc8ed2ad59f54411de08\
                9b193dca452b9699dafe34d124dfe521a956cce4adf58902a4c7b8bcf3d4548848dd2f1bee",
        )?;
        let payload = IncomingPayload::try_from(payload)?;
        let IncomingPayload::MessageWithMetadataBox(message) = payload else {
            panic!("Expected IncomingPayload::MessageWithMetadataBox");
        };

        assert_eq!(message.sender_identity, ThreemaId::try_from("0DA5ME76")?);
        assert_eq!(message.id, MessageId::from_hex("89aa9a7eaff77d96")?);
        assert_eq!(message.flags, MessageFlags(MessageFlags::SEND_PUSH_NOTIFICATION));
        assert_eq!(message.bytes.len(), 393);

        Ok(())
    }
}

enum OutgoingPayloadType {
    EchoRequest = 0x00,
    EchoResponse = 0x80,
    MessageWithMetadataBox = 0x01,
    MessageAck = 0x82,
    UnblockIncomingMessages = 0x03,
    SetPushNotificationToken = 0x20,
    DeletePushNotificationToken = 0x25,
    SetConnectionIdleTimeout = 0x30,
    ClearDeviceCookieChangeIndiciation = 0xd3,
}

/// An outgoing payload.
#[derive(Name, VariantNames, DebugVariantNames)]
pub enum OutgoingPayload {
    /// An echo request to be answered by a corresponding echo response.
    ///
    /// Can be used for connection keep-alive or RTT estimation.
    EchoRequest(EchoPayload),

    /// An echo response corresponding to an echo request.
    EchoResponse(EchoPayload),

    /// An outgoing end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
    MessageWithMetadataBox(MessageWithMetadataBox),

    /// Acknowledges that the referred incoming message has been received by the client.
    MessageAck(MessageAck),

    /// Unblock incoming messages from the server.
    UnblockIncomingMessages,

    /// Sets (or clears) the push notification token on the server for this device.
    SetPushNotificationToken(PushNotificationToken),

    /// Removes some specific device's push notification tokens or all device's existing push
    /// notification tokens from the server.
    DeletePushNotificationToken(DeletePushNotificationToken),

    /// Set the connection idle timeout
    SetConnectionIdleTimeout(ConnectionIdleTimeout),

    /// Clear the flag that triggers sending of a device-cookie-change-indication.
    ClearDeviceCookieChangeIndiciation,
}
impl OutgoingPayload {
    const HEADER_LENGTH: usize = 1 + PAYLOAD_HEADER_RESERVED.len();

    /// Encode the payload.
    pub(crate) fn encode(&self) -> Result<Vec<u8>, CspProtocolError> {
        // Determine the payload type and length
        let (r#type, length) = match self {
            OutgoingPayload::EchoRequest(payload) => (OutgoingPayloadType::EchoRequest, payload.length()),
            OutgoingPayload::EchoResponse(payload) => (OutgoingPayloadType::EchoResponse, payload.length()),
            OutgoingPayload::MessageWithMetadataBox(payload) => {
                (OutgoingPayloadType::MessageWithMetadataBox, payload.length())
            },
            OutgoingPayload::MessageAck(_) => (OutgoingPayloadType::MessageAck, MessageAck::LENGTH),
            OutgoingPayload::UnblockIncomingMessages => (OutgoingPayloadType::UnblockIncomingMessages, 0),
            OutgoingPayload::SetPushNotificationToken(payload) => {
                (OutgoingPayloadType::SetPushNotificationToken, payload.length())
            },
            OutgoingPayload::DeletePushNotificationToken(payload) => {
                (OutgoingPayloadType::DeletePushNotificationToken, payload.length())
            },
            OutgoingPayload::SetConnectionIdleTimeout(_) => (
                OutgoingPayloadType::SetConnectionIdleTimeout,
                ConnectionIdleTimeout::LENGTH,
            ),
            OutgoingPayload::ClearDeviceCookieChangeIndiciation => {
                (OutgoingPayloadType::ClearDeviceCookieChangeIndiciation, 0)
            },
        };

        // Encode the header
        let mut writer = OwnedVecByteWriter::new_with_capacity(Self::HEADER_LENGTH.saturating_add(length));
        writer
            .run(|writer| {
                // Encode the payload type
                writer.write_u8(r#type as u8)?;

                // Encode reserved bytes
                writer.write(&PAYLOAD_HEADER_RESERVED)
            })
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: error,
            })?;

        // Encode the payload
        match self {
            OutgoingPayload::EchoRequest(payload) | OutgoingPayload::EchoResponse(payload) => {
                payload.encode_into(&mut writer)
            },
            OutgoingPayload::MessageWithMetadataBox(payload) => payload.encode_into(&mut writer),
            OutgoingPayload::MessageAck(payload) => payload.encode_into(&mut writer),
            OutgoingPayload::SetPushNotificationToken(payload) => payload.encode_into(&mut writer),
            OutgoingPayload::DeletePushNotificationToken(payload) => payload.encode_into(&mut writer),
            OutgoingPayload::SetConnectionIdleTimeout(payload) => payload.encode_into(&mut writer),
            OutgoingPayload::UnblockIncomingMessages
            | OutgoingPayload::ClearDeviceCookieChangeIndiciation => Ok(()),
        }?;

        // Done
        Ok(writer.into_inner())
    }
}

/// Encrypted [`IncomingPayload`] frame decoder.
pub(crate) type PayloadDecoder =
    VariableLengthFrameDecoder<{ U16LittleEndianDelimiter::LENGTH }, U16LittleEndianDelimiter>;

/// Contains an [`OutgoingPayload`] to encode it.
#[derive(Name)]
pub(crate) struct EncryptedOutgoingPayload(pub(crate) Vec<u8>);
impl FrameEncoder for EncryptedOutgoingPayload {
    fn encode_frame(&self) -> Result<OutgoingFrame, CspProtocolError> {
        let mut writer = OwnedVecByteWriter::new_with_capacity(
            U16LittleEndianDelimiter::LENGTH.saturating_add(self.0.len()),
        );

        // Prepare length
        let length = self.0.len();
        let length = u16::try_from(length).map_err(|_| {
            error!(length, "Encoded frame length exceeds a u16");
            InternalErrorCause::from("Encoded frame too large, exceeds a u16")
        })?;

        // Encode the header and the content
        writer
            .run(|writer| {
                // Encode the length of the frame
                writer.write_u16_le(length)?;

                // Encode the content
                writer.write(&self.0)
            })
            .map_err(|error| InternalErrorCause::EncodingFailed {
                name: OutgoingFrame::NAME,
                source: error,
            })?;

        // Done
        Ok(OutgoingFrame(writer.into_inner()))
    }
}
