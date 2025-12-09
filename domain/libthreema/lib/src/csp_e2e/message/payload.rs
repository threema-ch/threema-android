//! Fully decoded/encoded messages, bridging the two CSP layers.
use core::str::{self, Utf8Error};

use educe::Educe;

use crate::{
    common::{MessageFlags, MessageId, Nonce, ThreemaId, ThreemaIdError},
    crypto::salsa20,
    csp::payload::MessageWithMetadataBox,
    utils::{
        bytes::{
            ByteReader as _, ByteReaderError, ByteWriter, ByteWriterError, EncryptedDataRangeReader as _,
            OwnedVecByteReader, OwnedVecByteWriter,
        },
        debug::debug_slice_length,
    },
};

const LEGACY_SENDER_NICKNAME_LENGTH: u8 = 32;

/// An error occurred while decoding an incoming message payload.
#[derive(Debug, thiserror::Error)]
pub(crate) enum IncomingMessagePayloadError {
    /// Decoding the message failed.
    #[error("Decoding message failed: {0}")]
    DecodingFailed(#[from] ByteReaderError),

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

/// An incoming end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
///
/// Note: Only the outer shell has been decoded here. The incoming message task is responsible for decrypting
/// and decoding the inner layers.
#[derive(Educe)]
#[educe(Debug)]
pub(crate) struct IncomingMessageWithMetadataBox {
    /// Raw bytes of the payload
    #[educe(Debug(method(debug_slice_length)))]
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
    pub(super) flags: MessageFlags,

    /// Legacy sender nickname (nickname from metadata should be preferred).
    pub(super) legacy_sender_nickname: Option<String>,

    /// Encrypted metadata.
    pub(super) metadata: Option<salsa20::EncryptedDataRange>,

    /// Nonce for both the contained message and the metadata box.
    pub(super) nonce: Nonce,

    /// Encrypted contained message.
    pub(super) message_container: salsa20::EncryptedDataRange,
}
impl TryFrom<MessageWithMetadataBox> for IncomingMessageWithMetadataBox {
    type Error = IncomingMessagePayloadError;

    fn try_from(payload: MessageWithMetadataBox) -> Result<Self, Self::Error> {
        let mut reader = OwnedVecByteReader::new(payload.bytes);

        // Skip sender identity
        reader.skip(ThreemaId::LENGTH)?;

        // Decode receiver identity
        let receiver_identity = ThreemaId::try_from(reader.read_fixed::<{ ThreemaId::LENGTH }>()?.as_slice())
            .map_err(IncomingMessagePayloadError::InvalidReceiverIdentity)?;

        // Skip the message ID (we already have it)
        reader.skip(MessageId::LENGTH)?;

        // Decode legacy _created at_ timestamp
        let legacy_created_at = reader.read_u32_le()?;

        // Skip flags (we already have it) and reserved bytes
        reader.skip(2)?;

        // Decode metadata length
        let metadata_length = reader.read_u16_le()?;

        // Decode and trim zero-padded legacy sender nickname (if any)
        let legacy_sender_nickname = {
            let nickname = reader.read_fixed::<{ LEGACY_SENDER_NICKNAME_LENGTH as usize }>()?;
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
                reader.read_encrypted_data_range_tag_ahead(
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
        let message_container = reader.read_encrypted_data_range_tag_ahead(
            reader
                .remaining()
                .checked_sub(salsa20::TAG_LENGTH)
                .ok_or(IncomingMessagePayloadError::InvalidMessage)?,
        )?;

        // Done
        let bytes = reader.expect_consumed()?;
        Ok(IncomingMessageWithMetadataBox {
            bytes,
            sender_identity: payload.sender_identity,
            receiver_identity,
            id: payload.id,
            legacy_created_at,
            flags: payload.flags,
            legacy_sender_nickname,
            metadata,
            nonce,
            message_container,
        })
    }
}

/// An error occurred while encoding an outgoing message payload.
#[derive(Debug, thiserror::Error)]
pub(crate) enum OutgoingMessagePayloadError {
    /// Decoding the message failed.
    #[error("Encoding message failed: {0}")]
    EncodingFailed(#[from] ByteWriterError),

    /// Metadata too large.
    #[error("Metadata too large")]
    MetadataTooLarge,
}

/// An outgoing end-to-end encrypted Threema message with additional end-to-end encrypted metadata.
#[derive(Educe)]
#[educe(Debug)]
pub(crate) struct OutgoingMessageWithMetadataBox {
    /// The sender's Threema ID.
    pub(super) sender_identity: ThreemaId,

    /// The receiver's Threema ID.
    pub(super) receiver_identity: ThreemaId,

    /// The ID of the message.
    pub(super) id: MessageId,

    /// (Legacy) UNIX timestamp in seconds for when the message has been created.
    ///
    /// Note: This timestamp is also present in the `metadata` field and should be preferred from
    /// there.
    pub(super) legacy_created_at: u32,

    /// Message flags.
    pub(super) flags: MessageFlags,

    /// Legacy sender nickname (nickname from metadata should be preferred).
    ///
    /// Note 1: The creator of the struct is responsible for omitting this field when sending a message to
    /// non-Gateway IDs.
    ///
    /// Note 2: Only 32 bytes of the UTF-8 string will be encoded, so prior truncation at UTF-8 codepoints is
    /// advised.
    pub(super) legacy_sender_nickname: Option<String>,

    /// Encrypted metadata.
    #[educe(Debug(method(debug_slice_length)))]
    pub(super) metadata: Vec<u8>,

    /// Nonce for both the contained message and the metadata box.
    pub(super) nonce: Nonce,

    /// Encrypted contained message.
    #[educe(Debug(method(debug_slice_length)))]
    pub(super) message_container: Vec<u8>,
}
impl OutgoingMessageWithMetadataBox {
    const STATIC_LENGTH: usize = 2 * ThreemaId::LENGTH
        + MessageId::LENGTH
        + 4 /* legacy_created_at */
        + MessageFlags::LENGTH
        + 1 /* reserved */
        + 2 /* metadata-length */
        + LEGACY_SENDER_NICKNAME_LENGTH as usize
        + Nonce::LENGTH;

    fn length(&self) -> usize {
        Self::STATIC_LENGTH
            .saturating_add(self.metadata.len())
            .saturating_add(self.message_container.len())
    }

    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), OutgoingMessagePayloadError> {
        // Encode sender identity, receiver identity, message ID, legacy _created at_ timestamp, flags and
        // reserved space
        writer.write(&self.sender_identity.to_bytes())?;
        writer.write(&self.receiver_identity.to_bytes())?;
        writer.write_u64_le(self.id.0)?;
        writer.write_u32_le(self.legacy_created_at)?;
        writer.write_u8(self.flags.0)?;
        writer.write_u8(0)?; // reserved

        // Encode metadata length
        writer.write_u16_le(
            self.metadata
                .len()
                .try_into()
                .map_err(|_| OutgoingMessagePayloadError::MetadataTooLarge)?,
        )?;

        // Encode zero-padded legacy sender nickname if available (truncate if needed)
        writer.write(&[0_u8; LEGACY_SENDER_NICKNAME_LENGTH as usize])?;
        if let Some(legacy_sender_nickname) = &self.legacy_sender_nickname
            && let Some(truncated_legacy_sender_nickname) = legacy_sender_nickname.as_bytes().get(
                ..legacy_sender_nickname
                    .len()
                    .min(LEGACY_SENDER_NICKNAME_LENGTH as usize),
            )
        {
            writer.run_at(
                (LEGACY_SENDER_NICKNAME_LENGTH as isize)
                    .checked_neg()
                    .expect("-LEGACY_SENDER_NICKNAME_LENGTH fits isize"),
                |mut writer| writer.write(truncated_legacy_sender_nickname),
            )?;
        }

        // Encode metadata, nonce and message container
        writer.write(&self.metadata)?;
        writer.write(&self.nonce.0)?;
        writer.write(&self.message_container)?;

        // Done
        Ok(())
    }
}
impl TryFrom<OutgoingMessageWithMetadataBox> for MessageWithMetadataBox {
    type Error = OutgoingMessagePayloadError;

    fn try_from(message: OutgoingMessageWithMetadataBox) -> Result<Self, Self::Error> {
        let mut writer = OwnedVecByteWriter::new_with_capacity(message.length());
        message.encode_into(&mut writer)?;
        Ok(Self {
            sender_identity: message.sender_identity,
            id: message.id,
            flags: message.flags,
            bytes: writer.into_inner(),
        })
    }
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use data_encoding::HEXLOWER;

    use crate::{
        common::{MessageFlags, MessageId, Nonce, ThreemaId},
        csp::payload::MessageWithMetadataBox,
        csp_e2e::message::payload::IncomingMessageWithMetadataBox,
        utils::bytes::OwnedVecByteReader,
    };

    #[test]
    fn valid_message() -> anyhow::Result<()> {
        let message = HEXLOWER.decode(
            b"\
                304441354d453736304850543945574489aa9a7eaff77d96cb7327680100340000000000\
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
        let message = MessageWithMetadataBox::decode(OwnedVecByteReader::new(message))?;
        let message = IncomingMessageWithMetadataBox::try_from(message)?;

        assert_eq!(message.bytes.len(), 393);
        assert_eq!(message.sender_identity, ThreemaId::try_from("0DA5ME76")?);
        assert_eq!(message.receiver_identity, ThreemaId::try_from("0HPT9EWD")?);
        assert_eq!(message.id, MessageId::from_hex("89aa9a7eaff77d96")?);
        assert_eq!(message.legacy_created_at, 1747416011);
        assert_eq!(message.flags, MessageFlags(MessageFlags::SEND_PUSH_NOTIFICATION));
        assert_eq!(message.legacy_sender_nickname, None);
        let metadata = assert_matches!(message.metadata, Some(metadata) => metadata);
        assert_eq!(
            metadata.tag,
            TryInto::<[u8; 16]>::try_into(HEXLOWER.decode(b"439039d79074fa4a0d0961d651af57b9")?).unwrap()
        );
        assert_eq!(metadata.data, 80..116);
        assert_eq!(
            message.nonce,
            Nonce::from_hex("b2d718b0db179e3e3bcdbf3c2be997490a0349f2e4fbaa43")?,
        );
        assert_eq!(
            message.message_container.tag,
            TryInto::<[u8; 16]>::try_into(HEXLOWER.decode(b"712e263aab0c2c4a920182f01f810df0")?).unwrap()
        );
        assert_eq!(message.message_container.data, 156..393);

        Ok(())
    }

    // TODO(LIB-16): Test negative cases
}
