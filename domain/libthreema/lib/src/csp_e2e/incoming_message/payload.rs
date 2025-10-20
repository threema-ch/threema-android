use core::str::{self, Utf8Error};

use educe::Educe;

use crate::{
    common::{MessageFlags, MessageId, Nonce, ThreemaId, ThreemaIdError},
    crypto::salsa20,
    csp::payload::MessageWithMetadataBox,
    utils::{
        bytes::{ByteReader as _, ByteReaderError, EncryptedDataRangeReader as _, OwnedVecByteReader},
        debug::debug_slice_length,
    },
};

/// An error occurred while decoding an incoming message payload.
#[derive(Debug, thiserror::Error)]
pub(super) enum IncomingMessagePayloadError {
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

/// The fully decoded `message-with-metadata-box` message.
#[derive(Educe)]
#[educe(Debug)]
pub(super) struct DecodedMessageWithMetadataBox {
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

    /// Encrypted metadata box.
    pub(super) metadata: Option<salsa20::EncryptedDataRange>,

    /// Nonce for both the contained message and the metadata box.
    pub(super) nonce: Nonce,

    /// Encrypted contained message.
    pub(super) message_container: salsa20::EncryptedDataRange,
}
impl TryFrom<MessageWithMetadataBox> for DecodedMessageWithMetadataBox {
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
        let bytes = reader.expect_consumed()?;
        Ok(DecodedMessageWithMetadataBox {
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

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use data_encoding::HEXLOWER;

    use crate::{
        common::{MessageFlags, MessageId, Nonce, ThreemaId},
        csp::payload::MessageWithMetadataBox,
        csp_e2e::incoming_message::payload::DecodedMessageWithMetadataBox,
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
        let message = DecodedMessageWithMetadataBox::try_from(message)?;

        assert_eq!(message.bytes.len(), 393);
        assert_eq!(message.sender_identity, ThreemaId::try_from("0DA5ME76")?);
        assert_eq!(message.receiver_identity, ThreemaId::try_from("0HPT9EWD")?);
        assert_eq!(message.id, MessageId::from_hex("89aa9a7eaff77d96")?);
        assert_eq!(message.legacy_created_at, 1747416011);
        assert_eq!(message.flags, MessageFlags(MessageFlags::SEND_PUSH_NOTIFICATION));
        assert_eq!(message.legacy_sender_nickname, None);
        let metadata = assert_matches!(message.metadata, Some(metadata) => metadata);
        assert_eq!(metadata.data, 64..100);
        assert_eq!(
            message.nonce,
            Nonce::from_hex("b2d718b0db179e3e3bcdbf3c2be997490a0349f2e4fbaa43")?,
        );
        assert_eq!(message.message_container.data, 140..377);

        Ok(())
    }
}
