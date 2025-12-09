//! Task for sending an outgoing message.

use aead::AeadMutInPlace as _;

use crate::{
    common::{Delta, MessageMetadata, Nonce, ThreemaId, keys::CspE2eKey},
    csp_e2e::message::payload::OutgoingMessageWithMetadataBox,
    model::message::{MessageProperties, OutgoingMessage},
    utils::bytes::OwnedVecByteWriter,
};

pub(super) fn encode_metadata(
    sender_nickname: Delta<&str>,
    message: &OutgoingMessage,
    effective_message_properties: &MessageProperties,
) -> Vec<u8> {
    MessageMetadata {
        message_id: message.id,
        created_at: message.created_at,
        nickname: if effective_message_properties.user_profile_distribution {
            sender_nickname.map(ToOwned::to_owned)
        } else {
            Delta::Unchanged
        },
    }
    .into()
}

// TODO(LIB-51): Make encryption fallible
pub(super) fn encrypt_metadata_in_place(shared_secret: &CspE2eKey, nonce: &Nonce, metadata: &mut Vec<u8>) {
    shared_secret
        .message_metadata_cipher()
        .0
        .encrypt_in_place(&nonce.0.into(), b"", metadata)
        .expect("metadata encryption should not fail");
}

// TODO(LIB-51): Make encryption fallible
pub(super) fn encrypt_message_container_in_place(
    shared_secret: &CspE2eKey,
    nonce: &Nonce,
    message_container: &mut Vec<u8>,
) {
    shared_secret
        .message_cipher()
        .0
        .encrypt_in_place(&nonce.0.into(), b"", message_container)
        .expect("message container encryption should not fail");
}

#[expect(unused, reason = "TODO(LIB-51)")]
pub(super) fn encode_and_encrypt_message(
    sender_identity: ThreemaId,
    (legacy_sender_nickname, sender_nickname): (Option<&str>, Delta<&str>),
    receiver_identity: ThreemaId,
    shared_secret: CspE2eKey,
    message: &OutgoingMessage,
    nonce: Nonce,
) -> OutgoingMessageWithMetadataBox {
    let effective_message_properties = message.effective_properties();

    // Encode and encrypt metadata
    let metadata = {
        let mut metadata = encode_metadata(sender_nickname, message, &effective_message_properties);
        encrypt_metadata_in_place(&shared_secret, &nonce, &mut metadata);
        metadata
    };

    // Encode and encrypt message container
    let message_container = {
        // Note: The effort to calculate the required capacity for all message types would be unreasonable, so
        // we don't.
        let mut writer = OwnedVecByteWriter::new_empty();

        // Encode message container (i.e. message type, data and PKCS#7 padding)
        message
            .body
            .encode_into(&mut writer)
            .expect("encoding message container with OwnedVecByteWriter should not fail");
        let mut message_container = writer.into_inner();

        // Encrypt message container
        encrypt_message_container_in_place(&shared_secret, &nonce, &mut message_container);
        message_container
    };

    // Drop shared secret ASAP
    drop(shared_secret);

    // Encode legacy created-at timestamp
    let legacy_created_at: u32 = message
        .created_at
        .checked_div_euclid(1000)
        .expect("ms to s conversion should work")
        .try_into()
        .unwrap_or(u32::MAX);

    // Encode legacy sender nickname (if applicable and towards a Gateway ID)
    let legacy_sender_nickname =
        if effective_message_properties.user_profile_distribution && receiver_identity.is_gateway_id() {
            legacy_sender_nickname.map(ToOwned::to_owned)
        } else {
            None
        };

    // Done
    OutgoingMessageWithMetadataBox {
        sender_identity,
        receiver_identity,
        id: message.id,
        legacy_created_at,
        flags: message.flags(),
        legacy_sender_nickname,
        metadata,
        nonce,
        message_container,
    }
}
