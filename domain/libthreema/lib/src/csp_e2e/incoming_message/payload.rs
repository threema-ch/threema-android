use core::str::{self, Utf8Error};

use crate::{
    common::{MessageId, Nonce, ThreemaId, ThreemaIdError},
    crypto::salsa20,
    csp::payload::MessageWithMetadataBox,
    utils::bytes::{ByteReader as _, ByteReaderError, EncryptedDataRangeReader as _, OwnedVecByteReader},
};

/// An error occurred while decoding an incoming message payload.
#[derive(Debug, thiserror::Error)]
pub(super) enum IncomingMessagePayloadError {
    /// Decoding the message failed.
    #[error("Decoding message failed: {0}")]
    DecodingFailed(#[from] ByteReaderError),

    /// Invalid sender identity encountered.
    #[error("Invalid sender identity: {0}")]
    InvalidSenderIdentity(#[source] ThreemaIdError),

    /// Invalid receiver identity encountered.
    #[error("Invalid receiver identity: {0}")]
    InvalidReceiverIdentity(#[source] ThreemaIdError),

    /// Invalid nickname encoding.
    #[error("Invalid nickname: {0}")]
    InvalidNickname(Utf8Error),

    /// Invalid message.
    #[error("Invalid message: Does not contain sufficient bytes")]
    InvalidMessage,
}

/// Message flags which were/are transmitted to the server.
#[expect(dead_code, reason = "Will use later")]
pub(super) struct MessageFlags(pub(super) u8);
#[rustfmt::skip]
#[expect(dead_code, reason = "Will use later")]
impl MessageFlags {
    pub(super) const SEND_PUSH_NOTIFICATION:u8 =      0b_0000_0001;
    pub(super) const NO_SERVER_QUEUING: u8 =          0b_0000_0010;
    pub(super) const NO_SERVER_ACKNOWLEDGEMENT: u8 =  0b_0000_0100;
    // Reserved:                                      0b_0000_1000
    // Reserved (formerly _group message marker_):    0b_0001_0000
    pub(super) const SHORT_LIVED_SERVER_QUEUING: u8 = 0b_0010_0000;
    // Reserved:                                      0b_0100_0000
    pub(super) const NO_DELIVERY_RECEIPTS: u8 =       0b_1000_0000;
}

/// The fully decoded `message-with-metadata-box` message.
pub(super) struct DecodedMessageWithMetadataBox {
    /// Raw bytes of the payload
    pub(super) bytes: Vec<u8>,

    /// The sender's Threema ID.
    pub(super) sender_identity: ThreemaId,

    /// The receiver's Threema ID.
    pub(super) receiver_identity: ThreemaId,

    /// The ID of the message
    pub(super) id: MessageId,

    /// (Legacy) UNIX timestamp in seconds for when the message has been created.
    ///
    /// Note: This timestamp is also present in the `metadata` field and should be preferred from
    /// there.
    pub(super) legacy_created_at: u32,

    /// Message flags.
    #[expect(dead_code, reason = "Will use later")]
    pub(super) flags: MessageFlags,
    pub(super) legacy_sender_nickname: Option<String>,
    pub(super) metadata: Option<salsa20::EncryptedDataRange>,
    pub(super) nonce: Nonce,
    pub(super) message_container: salsa20::EncryptedDataRange,
}
impl TryFrom<MessageWithMetadataBox> for DecodedMessageWithMetadataBox {
    type Error = IncomingMessagePayloadError;

    fn try_from(payload: MessageWithMetadataBox) -> Result<Self, Self::Error> {
        let mut reader = OwnedVecByteReader::new(payload.message_bytes);

        // Decode and validate sender and receiver identities
        let sender_identity = ThreemaId::try_from(reader.read_fixed::<{ ThreemaId::LENGTH }>()?.as_slice())
            .map_err(IncomingMessagePayloadError::InvalidSenderIdentity)?;
        let receiver_identity = ThreemaId::try_from(reader.read_fixed::<{ ThreemaId::LENGTH }>()?.as_slice())
            .map_err(IncomingMessagePayloadError::InvalidReceiverIdentity)?;

        // Skip the message ID (we already have it)
        reader.skip(MessageId::LENGTH)?;

        // Decode legacy _created at_ timestamp, flags and reserved bytes
        let legacy_created_at = reader.read_u32_le()?;
        let flags = MessageFlags(reader.read_u8()?);
        reader.skip(1)?;

        // Decode metadata length
        let metadata_length = reader.read_u16_le()?;

        // Decode and trim zero-padded legacy sender nickname (if any)
        let legacy_sender_nickname = {
            let nickname = reader.read_fixed::<32>()?;
            let length = nickname
                .iter()
                .position(|&character| character == b'\0')
                .unwrap_or(nickname.len());
            if length > 0 {
                let nickname = nickname
                    .get(..length)
                    .expect("calculated legacy sender nickname length must be in bounds");
                let nickname =
                    str::from_utf8(nickname).map_err(IncomingMessagePayloadError::InvalidNickname)?;
                Some(nickname.trim().to_owned())
            } else {
                None
            }
        };

        // Decode metadata (if any)
        let metadata = if metadata_length > 0 {
            Some(
                reader.read_encrypted_data_range(
                    (metadata_length as usize)
                        .checked_sub(salsa20::TAG_LENGTH)
                        .ok_or(IncomingMessagePayloadError::InvalidMessage)?,
                )?,
            )
        } else {
            None
        };

        // Decode nonce
        let nonce = Nonce::from(reader.read_fixed::<{ Nonce::LENGTH }>()?);

        // Decode message container
        let message_container = reader.read_encrypted_data_range(
            reader
                .remaining()
                .checked_sub(salsa20::TAG_LENGTH)
                .ok_or(IncomingMessagePayloadError::InvalidMessage)?,
        )?;

        // Done
        let message_bytes = reader.expect_consumed()?;
        Ok(DecodedMessageWithMetadataBox {
            bytes: message_bytes,
            sender_identity,
            receiver_identity,
            id: payload.message_id,
            legacy_created_at,
            flags,
            legacy_sender_nickname,
            metadata,
            nonce,
            message_container,
        })
    }
}
