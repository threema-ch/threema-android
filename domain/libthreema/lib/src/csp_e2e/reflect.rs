//! Task for reflecting messages.
use const_format::formatcp;
use libthreema_macros::Name;
use prost::Name as _;
use tracing::{error, warn};

use crate::{
    common::Nonce,
    crypto::aead::AeadRandomNonceAhead as _,
    csp_e2e::{CspE2eProtocolError, D2xContext, ReflectId},
    model::message::{MessageLifetime, MessageProperties},
    protobuf::{self},
    utils::bytes::ProtobufPaddedMessage as _,
};

/// 1. Let `reflect-ids` be the list of all reflect IDs of `reflect_messages` that do not have the _ephemeral_
///    flag.
/// 2. Reflect each message of `reflect_messages` with the provided flags and ID as a `reflect` message.
/// 3. Wait until all `reflect-ids` have been acknowledged by a corresponding `reflect-ack` message, provide
///    the associated task with the `reflect-ids` as a [`ReflectResponse`] and poll again.
pub struct ReflectInstruction {
    /// Messages that need to be reflected.
    pub reflect_messages: Vec<ReflectPayload>,
}

/// Possible response for an [`ReflectInstruction`].
pub struct ReflectResponse {
    /// Acknowledgements for reflected messages.
    pub acknowledged_reflect_ids: Vec<ReflectId>,
}

/// Flags applied when reflecting/reflected
#[derive(Default)]
pub struct ReflectFlags {
    /// If `true`, the reflected message is only transmitted to other devices currently online.
    pub ephemeral: bool,
}
impl From<&MessageProperties> for ReflectFlags {
    fn from(properties: &MessageProperties) -> Self {
        Self {
            // Here's the rationale on why `MessageLifetime::Brief` does not also map to
            // _ephemeral_: A _short-lived_ (aka _brief_) message should either be received by no
            // device (if dropped) or all devices. This is because _short-lived_ is applied to
            // messages that affect the conversation history, e.g. a `call-offer` that would display
            // an attempted call. However, _ephemeral_ messages are not allowed to alter the
            // conversation history, at least not permanently (e.g. the _typing_ indicator).
            ephemeral: matches!(properties.lifetime, MessageLifetime::Ephemeral),
        }
    }
}

/// A message that should be reflected or has been reflected.
///
/// TODO(LIB-16): Add a `From` for the D2M layer reflect payload / align with those structs
pub struct ReflectPayload {
    /// Flags used for the reflected message.
    pub flags: ReflectFlags,

    /// ID used for the reflected message (only needed for acknowledgement).
    pub id: ReflectId,

    /// Enclosed encrypted D2D `Envelope`.
    pub envelope: Vec<u8>,
}
impl ReflectPayload {
    pub(super) fn encode_and_encrypt(
        d2x_context: &mut D2xContext,
        flags: ReflectFlags,
        content: protobuf::d2d::envelope::Content,
    ) -> Result<(Self, Nonce), CspE2eProtocolError> {
        let mut envelope = protobuf::d2d::Envelope {
            #[expect(deprecated, reason = "Will be filled by encode_to_vec_padded")]
            padding: vec![],
            device_id: d2x_context.device_id.0,
            protocol_version: protobuf::d2d::ProtocolVersion::V03 as u32,
            content: Some(content),
        }
        .encode_to_vec_padded();
        let nonce = d2x_context
            .device_group_key
            .reflect_key()
            .0
            .encrypt_in_place_random_nonce_ahead(b"", &mut envelope)
            .map_err(|_| CspE2eProtocolError::EncryptionFailed {
                name: protobuf::d2d::Envelope::NAME,
            })?;
        Ok((
            Self {
                flags,
                id: d2x_context.reflect_id.0.get_and_increment()?.into(),
                envelope,
            },
            nonce.into(),
        ))
    }
}

/// Subtask for reflecting a bundled list of messages and awaiting acknowledgement.
#[derive(Debug, Name)]
pub(crate) struct ReflectSubtask {
    reflect_ids: Vec<ReflectId>,
    acknowledged_reflect_ids: Option<Vec<ReflectId>>,
}
impl ReflectSubtask {
    pub(crate) fn new(reflect_messages: Vec<ReflectPayload>) -> (Self, ReflectInstruction) {
        (
            Self {
                // Only expect acknowledgements from reflection messages that are not ephemeral
                reflect_ids: reflect_messages
                    .iter()
                    .filter_map(|reflect_message| {
                        if reflect_message.flags.ephemeral {
                            None
                        } else {
                            Some(reflect_message.id)
                        }
                    })
                    .collect(),
                acknowledged_reflect_ids: None,
            },
            ReflectInstruction { reflect_messages },
        )
    }

    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn poll(mut self) -> Result<(), CspE2eProtocolError> {
        // Ensure the caller provided the acknowledged reflect IDs
        let Some(mut acknowledged_reflect_ids) = self.acknowledged_reflect_ids else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Acknowledged reflect IDs were not provided for '{}' state",
                ReflectSubtask::NAME,
            )));
        };

        // Ensure all reflect IDs have been acknowledged.
        //
        // Note: Input is expected to be small, so a linear search is fine.
        self.reflect_ids.retain(|reflect_id| {
            match acknowledged_reflect_ids
                .iter()
                .position(|other_reflect_id| other_reflect_id == reflect_id)
            {
                Some(index) => {
                    // Note: `swap_remove` is faster but we actually care about the order of the
                    // remaining acknowledged reflect IDs for debugging purposes
                    let _ = acknowledged_reflect_ids.remove(index);
                    false
                },
                None => true,
            }
        });

        // Check if there are reflect IDs that have not been acknowledged
        if !self.reflect_ids.is_empty() {
            let message = "Desync likely! Some provided reflect IDs have no associated acknowledgement";
            error!(
                unacknowledged_reflect_ids = ?self.reflect_ids,
                ?acknowledged_reflect_ids,
                message,
            );
            return Err(CspE2eProtocolError::DesyncError(message));
        }
        if !acknowledged_reflect_ids.is_empty() {
            warn!(
                ?acknowledged_reflect_ids,
                "Some acknowledged reflect IDs have no associated reflect ID",
            );
        }

        // Done
        Ok(())
    }

    // TODO(LIB-16): Add the `reflect-ack` timestamps here and forward them to the result!
    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn response(&mut self, response: ReflectResponse) {
        let _ = self
            .acknowledged_reflect_ids
            .insert(response.acknowledged_reflect_ids);
    }
}
