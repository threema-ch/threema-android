//! Payloads exchanged during the post-handshake state.
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::error;

use super::{
    CspProtocolError,
    frame::{FrameEncoder, OutgoingFrame},
};
use crate::{
    common::{CspDeviceId, MessageId, ThreemaId},
    utils::{
        bytes::{ByteReader, ByteWriter, OwnedVecByteReader, OwnedVecByteWriter},
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
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// An end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
///
/// Note: Only the message ID will be decoded, so that it can be acknowledged at need. The E2E layer
/// is responsible for decoding the rest of the message bytes.
#[derive(Name)]
pub struct MessageWithMetadataBox {
    /// The ID of the message
    pub message_id: MessageId,

    /// Raw bytes of the payload
    pub message_bytes: Vec<u8>,
}
impl MessageWithMetadataBox {
    fn decode(mut reader: OwnedVecByteReader) -> Result<Self, CspProtocolError> {
        let message_id = reader
            .run_at(
                (ThreemaId::LENGTH * 2)
                    .try_into()
                    .expect("ThreemaId::LENGTH * 2 must be isize"),
                |mut payload| Ok(MessageId(payload.read_u64_le()?)),
            )
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?;
        let message_bytes = reader.read_remaining_owned();
        Ok(Self {
            message_id,
            message_bytes,
        })
    }

    fn length(&self) -> usize {
        self.message_bytes.len()
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .run(|writer| {
                writer.skip(ThreemaId::LENGTH * 2)?;
                writer.write_u64_le(self.message_id.0)
            })
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// Acknowledges that a message has been received.
#[derive(Name)]
pub struct MessageAck {
    /// Identity of the sender for an incoming message
    pub identity: ThreemaId,

    /// Refers to the message id of the acknowledged message.
    pub message_id: MessageId,
}
impl MessageAck {
    const LENGTH: usize = ThreemaId::LENGTH + MessageId::LENGTH;

    fn decode(reader: impl ByteReader) -> Result<Self, CspProtocolError> {
        let (identity, message_id) = reader
            .run_owned(|mut payload| {
                let identity = payload.read_fixed::<{ ThreemaId::LENGTH }>()?;
                let message_id = payload.read_u64_le()?;
                let _ = payload.expect_consumed()?;
                Ok((identity, message_id))
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })?;

        Ok(Self {
            identity: ThreemaId::try_from(identity.as_ref()).map_err(|error| {
                CspProtocolError::InvalidMessage {
                    name: Self::NAME,
                    cause: error.to_string(),
                }
            })?,
            message_id: MessageId(message_id),
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        writer
            .run(|writer| {
                writer.write(&self.identity.to_bytes())?;
                writer.write_u64_le(self.message_id.0)
            })
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// APNs server type
pub enum APNsServerType {
    /// Production server of the APNs service.
    Production,
    /// Development server of the APNs service.
    Development,
}

/// Sets (or clears) the push notification token on the server.
#[derive(Name)]
pub enum PushNotificationToken {
    /// Clear any existing push notification token of this device
    Clear,

    /// Set an APNs push notification token
    ///
    /// Note: For readers with legacy knowledge, this is the one with the `mutable-content` key. No
    /// other variants are in use by modern clients.
    APNs {
        /// APNs server type
        r#type: APNsServerType,
        /// APNs token
        token: Vec<u8>,
    },

    /// Set an FCM push notification token
    FCM(Vec<u8>),

    /// Set an HMS push notification token
    HMS(Vec<u8>),
}
impl PushNotificationToken {
    fn length(&self) -> usize {
        1_usize.saturating_add(match self {
            PushNotificationToken::Clear => 0,
            PushNotificationToken::APNs { token, .. }
            | PushNotificationToken::FCM(token)
            | PushNotificationToken::HMS(token) => token.len(),
        })
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        let (token_type, token_data) = match self {
            PushNotificationToken::Clear => (0x00, None),
            PushNotificationToken::APNs { r#type: scope, token } => (
                match scope {
                    APNsServerType::Production => 0x05,
                    APNsServerType::Development => 0x06,
                },
                Some(token),
            ),
            PushNotificationToken::FCM(token) => (0x11, Some(token)),
            PushNotificationToken::HMS(token) => (0x13, Some(token)),
        };
        writer
            .run(|writer| {
                writer.write_u8(token_type)?;
                if let Some(token_data) = token_data {
                    writer.write(token_data)
                } else {
                    Ok(())
                }
            })
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
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
                    .map_err(|error| CspProtocolError::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })
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
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
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
    MessageAck = 0x82,
    QueueSendComplete = 0xd0,
    DeviceCookieChangeIndication = 0xd2,
    CloseError = 0xe0,
    Alert = 0xe1,
}

/// An incoming payload.
#[derive(Name, VariantNames, DebugVariantNames)]
pub enum IncomingPayload {
    /// An echo request to be answered by a corresponding echo response.
    ///
    /// Can be used for connection keep-alive or RTT estimation.
    EchoRequest(EchoPayload),

    /// An echo response corresponding to an echo request.
    EchoResponse(EchoPayload),

    /// An end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
    MessageWithMetadataBox(MessageWithMetadataBox),

    /// Acknowledges that a message has been received.
    MessageAck(MessageAck),

    /// Indicates that the incoming message queue on the server has been fully transmitted to the
    /// client. A client should not disconnect prior to having received this payload.
    QueueSendComplete,

    /// Indicates to the client that a device cookie mismatch has been detected since the last time
    /// that the device cookie change indication has been cleared.
    DeviceCookieChangeIndication,

    /// Indicates that the connection has experienced an unrecoverable error and must be closed.
    CloseError(CloseError),

    /// Generic alert that should be displayed in the client's user interface.
    ServerAlert(ServerAlert),

    /// The payload type is not known, either due to server misbehavior or as consequence of
    /// running an old version of libthreema. This information might be helpful for debugging.
    UnknownPayload {
        /// The message type
        unknown_type: u8,

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
                unknown_type: r#type,
                length: reader.remaining(),
            });
        };

        // Create the corresponding variant by handling the payload accordingly
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

enum OutgoingPayloadType {
    EchoRequest = 0x00,
    EchoResponse = 0x80,
    MessageWithMetadataBox = 0x01,
    MessageAck = 0x81,
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

    /// An end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
    MessageWithMetadataBox(MessageWithMetadataBox),

    /// Acknowledges that a message has been received.
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
    pub(super) fn encode(&self) -> Result<Vec<u8>, CspProtocolError> {
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
            .map_err(|error| CspProtocolError::EncodingFailed {
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
pub(super) type PayloadDecoder =
    VariableLengthFrameDecoder<{ U16LittleEndianDelimiter::LENGTH }, U16LittleEndianDelimiter>;

/// Contains an [`OutgoingPayload`] to encode it.
#[derive(Name)]
pub(super) struct EncryptedOutgoingPayload(pub(super) Vec<u8>);
impl FrameEncoder for EncryptedOutgoingPayload {
    fn encode_to_frame(&self) -> Result<OutgoingFrame, CspProtocolError> {
        let mut writer = OwnedVecByteWriter::new_with_capacity(
            U16LittleEndianDelimiter::LENGTH.saturating_add(self.0.len()),
        );

        // Prepare length
        let length = self.0.len();
        let length = u16::try_from(length).map_err(|_| {
            error!(length, "Encoded frame length exceeds a u16");
            CspProtocolError::InternalError("Encoded frame too large, exceeds a u16")
        })?;

        // Encode the header and the content
        writer
            .run(|writer| {
                // Encode the length of the frame
                writer.write_u16_le(length)?;

                // Encode the content
                writer.write(&self.0)
            })
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: OutgoingFrame::NAME,
                source: error,
            })?;

        // Done
        Ok(OutgoingFrame(writer.into_inner()))
    }
}
