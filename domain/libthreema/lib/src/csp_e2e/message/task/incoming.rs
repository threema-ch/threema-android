//! Task for processing an incoming message.
use core::mem;
use std::str;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use prost::Message as _;
use tracing::{debug, error, info, trace, warn};

use crate::{
    common::{
        Delta, MessageFlags, MessageId, MessageMetadata, Nonce, ThreemaId, keys::CspE2eKey, task::TaskLoop,
    },
    crypto::aead::AeadInPlace as _,
    csp::payload::{MessageAck, MessageWithMetadataBox},
    csp_e2e::{
        CspE2eContext, CspE2eProtocolContext, CspE2eProtocolError, D2mRole,
        contacts::{
            create::{
                CreateContactsInstruction, CreateContactsLoop, CreateContactsResponse, CreateContactsTask,
            },
            lookup::{
                CacheLookupPolicy, ContactResult, ContactsLookupInstruction, ContactsLookupLoop,
                ContactsLookupResponse, ContactsLookupSubtask,
            },
            update::{
                UpdateContactsInstruction, UpdateContactsLoop, UpdateContactsResponse, UpdateContactsTask,
            },
        },
        message::payload::IncomingMessageWithMetadataBox,
        reflect::{ReflectFlags, ReflectInstruction, ReflectPayload, ReflectResponse, ReflectSubtask},
    },
    model::{
        contact::{CommunicationPermission, ContactOrInit, ContactUpdate, PredefinedContact},
        message::{
            ContactMessageBody, IncomingMessage, IncomingMessageBody, MessageOverrides, MessageProperties,
        },
        provider::ProviderError,
    },
    protobuf::{self, common::CspE2eMessageType, d2d_sync::contact as protobuf_contact},
    utils::{apply::Apply as _, debug::Name as _},
};

/// Instruction for processing an incoming message.
pub enum IncomingMessageInstruction {
    /// TODO(LIB-16)
    FetchSender(ContactsLookupInstruction),
    /// TODO(LIB-16)
    CreateContact(CreateContactsInstruction),
    /// TODO(LIB-16)
    UpdateContact(UpdateContactsInstruction),
    /// TODO(LIB-16)
    ReflectMessage(ReflectInstruction),
}

/// Possible response for an [`IncomingMessageInstruction`].
pub enum IncomingMessageResponse {
    /// Possible response for an inner [`ContactsLookupInstruction`].
    FetchSender(ContactsLookupResponse),

    /// Possible response for an inner [`CreateContactsInstruction`].
    CreateContact(CreateContactsResponse),

    /// Possible response for an inner [`UpdateContactsInstruction`].
    UpdateContact(UpdateContactsResponse),

    /// Possible response for an inner [`ReflectInstruction`].
    ReflectMessage(ReflectResponse),
}

/// Result of processing an incoming message.
///
/// 1. If `outgoing_message_ack` is present, send a `message-ack` payload to the chat server with the enclosed
///    message ID.
/// 2. If `outgoing_message_task` is present, schedule it at the task manager.
#[derive(Debug)]
pub struct IncomingMessageResult {
    /// An optional message ID to acknowledge to (and therefore remove from) the server.
    pub outgoing_message_ack: Option<MessageAck>,
    /// TODO(LIB-16): An optional outgoing message task to be scheduled.
    pub outgoing_message_task: Option<()>,
}

/// Result of polling a [`IncomingMessageTask`].
pub type IncomingMessageLoop = TaskLoop<IncomingMessageInstruction, IncomingMessageResult>;

#[derive(Debug, PartialEq, Eq)]
enum DiscardReason {
    PayloadDecodingFailed,
    NonceReuse,
    ReceiverIsNotUser,
    SenderIsUser,
    SenderIsInvalid,
    MetadataDecryptionFailed,
    MetadataDecodingFailed,
    MessageDecryptionFailed,
    MessageMissingPkcs7PaddingForMessage,
    MessageInvalidPkcs7Padding,
    MessageMissingOuterType,
    MessageDisallowedOuterType,
    MessageUnknownOuterType,
    MessageForwardSecurityUnsupported,
    MessageDecodingFailed,
    MessageIdsDiverging,
    MessageIdReuse,
    MessageSenderIsBlocked,
    MessageBy3maPushUnexpected,
    MessageMayNotImplicitlyAddContact,
    MessageOnlyAllowedFrom3maPush,
}

struct OuterIncomingMessage {
    message_type: CspE2eMessageType,
    flags: MessageFlags,
    unpadded_message_data: Vec<u8>,
    nonce: Nonce,
}

struct IncomingMessageParts {
    outer: OuterIncomingMessage,
    inner: IncomingMessage,
    metadata: Option<MessageMetadata>,
}

#[derive(Debug)]
enum ProcessingOutcome<T> {
    Ok(T),
    Discard {
        reason: DiscardReason,
        acknowledge: AcknowledgeContext,
    },
}

#[derive(Debug)]
struct AcknowledgeContext {
    sender_identity: ThreemaId,
    id: MessageId,
    flags: MessageFlags,
    nonce: Option<Nonce>,
}
impl From<&IncomingMessageWithMetadataBox> for AcknowledgeContext {
    fn from(payload: &IncomingMessageWithMetadataBox) -> Self {
        Self {
            sender_identity: payload.sender_identity,
            id: payload.id,
            flags: payload.flags,
            nonce: Some(payload.nonce.clone()),
        }
    }
}
impl From<&IncomingMessageParts> for AcknowledgeContext {
    fn from(message: &IncomingMessageParts) -> Self {
        Self {
            sender_identity: message.inner.sender_identity,
            id: message.inner.id,
            flags: message.outer.flags,
            nonce: Some(message.outer.nonce.clone()),
        }
    }
}

fn decrypt_and_decode_metadata(
    shared_secret: &CspE2eKey,
    payload: &mut IncomingMessageWithMetadataBox,
) -> ProcessingOutcome<Option<MessageMetadata>> {
    ProcessingOutcome::Ok(match payload.metadata.as_ref() {
        Some(metadata_range) => {
            // Extract metadata bytes
            let metadata_bytes = payload
                .bytes
                .get_mut(metadata_range.data.clone())
                .expect("calculated metadata data length must be in bounds");

            // Decrypt metadata
            if shared_secret
                .message_metadata_cipher()
                .0
                .decrypt_in_place_detached(
                    &payload.nonce.0.into(),
                    b"",
                    metadata_bytes,
                    &metadata_range.tag.into(),
                )
                .is_err()
            {
                warn!("Discarding message whose metadata could not be decrypted");
                return ProcessingOutcome::Discard {
                    reason: DiscardReason::MetadataDecryptionFailed,
                    acknowledge: AcknowledgeContext::from(&*payload),
                };
            }

            // Decode metadata
            match protobuf::csp_e2e::MessageMetadata::decode(&*metadata_bytes) {
                Ok(metadata) => Some(MessageMetadata::from(metadata)),
                Err(error) => {
                    warn!(?error, "Discarding message whose metadata could not be decoded");
                    return ProcessingOutcome::Discard {
                        reason: DiscardReason::MetadataDecodingFailed,
                        acknowledge: AcknowledgeContext::from(&*payload),
                    };
                },
            }
        },
        None => None,
    })
}

fn decrypt_and_decode_message(
    shared_secret: CspE2eKey,
    payload: &mut IncomingMessageWithMetadataBox,
    sender: &ContactOrInit,
) -> ProcessingOutcome<IncomingMessageParts> {
    // Decrypt and decode metadata (if any)
    let outer_metadata = match decrypt_and_decode_metadata(&shared_secret, payload) {
        ProcessingOutcome::Ok(outer_metadata) => outer_metadata,
        ProcessingOutcome::Discard { reason, acknowledge } => {
            return ProcessingOutcome::Discard { reason, acknowledge };
        },
    };

    // Decrypt message container and decode it to message type and message data
    let (outer_type, outer_message_data) = {
        // Extract message container bytes
        let message_container_bytes = payload
            .bytes
            .get_mut(payload.message_container.data.clone())
            .expect("calculated metadata data length must be in bounds");

        // Decrypt message container
        if shared_secret
            .message_cipher()
            .0
            .decrypt_in_place_detached(
                &payload.nonce.0.into(),
                b"",
                message_container_bytes,
                &payload.message_container.tag.into(),
            )
            .is_err()
        {
            warn!("Discarding message whose data could not be decrypted");
            return ProcessingOutcome::Discard {
                reason: DiscardReason::MessageDecryptionFailed,
                acknowledge: AcknowledgeContext::from(&*payload),
            };
        }

        // Drop shared secret ASAP
        drop(shared_secret);

        // Remove PKCS#7 padding from message container
        //
        // Note: We do not need to handle the padding carefully since we follow the pad-then-encrypt scheme.
        let message_container_bytes = {
            let Some(padding_length) = message_container_bytes.last() else {
                warn!("Discarding message without any PKCS#7 padding");
                return ProcessingOutcome::Discard {
                    reason: DiscardReason::MessageMissingPkcs7PaddingForMessage,
                    acknowledge: AcknowledgeContext::from(&*payload),
                };
            };
            let unpadded_length = if *padding_length > 0
                && let Some(unpadded_length) = message_container_bytes
                    .len()
                    .checked_sub(*padding_length as usize)
            {
                unpadded_length
            } else {
                warn!("Discarding message with invalid PKCS#7 padding");
                return ProcessingOutcome::Discard {
                    reason: DiscardReason::MessageInvalidPkcs7Padding,
                    acknowledge: AcknowledgeContext::from(&*payload),
                };
            };
            message_container_bytes
                .get(..unpadded_length)
                .expect("calculated PKCS#7 padding length must be in bounds")
        };

        // Decode message type
        let Some(outer_type) = message_container_bytes.first() else {
            warn!("Discarding message without an outer type");
            return ProcessingOutcome::Discard {
                reason: DiscardReason::MessageMissingOuterType,
                acknowledge: AcknowledgeContext::from(&*payload),
            };
        };

        // The remaining data is the message data
        let outer_message_data = message_container_bytes
            .get(1..)
            .expect("message data after a successfully decoded message outer type must be in bounds");

        (*outer_type, outer_message_data)
    };

    // Legacy: Ensure it's not of type `0xff`.
    //
    // Note: This prevents payload confusion due to the old broken vouch mechanism. Since
    // messages can be re-sent (as long as the associated nonce has been removed), we will have
    // to keep this indefinitely.
    if outer_type == 0xff {
        warn!("Discarding message with disallowed outer type 0xff");
        return ProcessingOutcome::Discard {
            reason: DiscardReason::MessageDisallowedOuterType,
            acknowledge: AcknowledgeContext::from(&*payload),
        };
    }

    // Ensure it's a known type
    let Ok(outer_type) = CspE2eMessageType::try_from(i32::from(outer_type)) else {
        warn!(?outer_type, "Discarding message with unknown outer type");
        return ProcessingOutcome::Discard {
            reason: DiscardReason::MessageUnknownOuterType,
            acknowledge: AcknowledgeContext::from(&*payload),
        };
    };

    // TODO(LIB-42): Handle FS, decode inner metadata
    let (inner_metadata, inner_type, inner_message_data) =
        if outer_type == CspE2eMessageType::ForwardSecurityEnvelope {
            error!("TODO(LIB-42): Handle FS envelope");

            // TODO(LIB-42): Disallow usage of FS encapsulation within an FS encapsulated message

            return ProcessingOutcome::Discard {
                reason: DiscardReason::MessageForwardSecurityUnsupported,
                acknowledge: AcknowledgeContext::from(&*payload),
            };
        } else {
            (outer_metadata, outer_type, outer_message_data)
        };

    // Bundle the outer message parts
    let outer_message = OuterIncomingMessage {
        message_type: outer_type,
        flags: payload.flags,
        unpadded_message_data: outer_message_data.to_vec(),
        nonce: payload.nonce.clone(),
    };

    // Decode the incoming message
    let inner_message = IncomingMessage {
        sender_identity: sender.inner().identity,
        id: payload.id,
        overrides: MessageOverrides::from(payload.flags),
        created_at: inner_metadata.as_ref().map_or(
            u64::from(payload.legacy_created_at).saturating_mul(1000),
            |metadata| metadata.created_at,
        ),
        body: match IncomingMessageBody::decode(inner_type, inner_message_data) {
            Ok(message) => message,
            Err(error) => {
                warn!(
                    ?inner_type,
                    ?error,
                    "Discarding message that could not be decoded",
                );
                return ProcessingOutcome::Discard {
                    reason: DiscardReason::MessageDecodingFailed,
                    acknowledge: AcknowledgeContext::from(&*payload),
                };
            },
        },
    };

    // Done
    ProcessingOutcome::Ok(IncomingMessageParts {
        outer: outer_message,
        inner: inner_message,
        metadata: inner_metadata,
    })
}

struct InitState {
    payload: MessageWithMetadataBox,
}

struct FetchSenderState {
    payload: IncomingMessageWithMetadataBox,
    fetch_sender_task: ContactsLookupSubtask,
}

struct CreateContactState {
    message: IncomingMessageParts,
    create_contact_task: CreateContactsTask,
}

struct UpdateContactState {
    message: IncomingMessageParts,
    update_contact_task: UpdateContactsTask,
    n_retries: u8,
}
struct ReflectMessageState {
    sender_identity: ThreemaId,
    message_id: MessageId,
    message_flags: MessageFlags,
    message_nonce: Nonce,
    effective_message_properties: MessageProperties,
    reflect_message_task: ReflectSubtask,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(CspE2eProtocolError),
    Init(InitState),
    FetchSender(FetchSenderState),
    CreateContact(CreateContactState),
    UpdateContact(UpdateContactState),
    ReflectMessage(ReflectMessageState),
    Done,
}
impl State {
    fn poll_init(
        context: &mut CspE2eProtocolContext,
        state: InitState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        match Self::process_init_state(context, state)? {
            ProcessingOutcome::Ok((state, message_loop)) => Ok((state, message_loop)),
            ProcessingOutcome::Discard { acknowledge, .. } => {
                Self::acknowledge_and_discard(context, acknowledge)
            },
        }
    }

    fn process_init_state(
        context: &mut CspE2eProtocolContext,
        state: InitState,
    ) -> Result<ProcessingOutcome<(Self, IncomingMessageLoop)>, CspE2eProtocolError> {
        // (MD) Ensure the device is leading
        if context
            .d2x
            .as_ref()
            .is_some_and(|d2m_context| d2m_context.role != D2mRole::Leader)
        {
            return Err(CspE2eProtocolError::InvalidState(
                "D2M _leader_ role required to process an incoming message",
            ));
        }

        let length = state.payload.bytes.len();

        // Decode and validate `message-with-metadata-box` (to some degree)
        let payload = {
            let MessageWithMetadataBox {
                sender_identity,
                id: message_id,
                flags: message_flags,
                ..
            } = state.payload;
            match IncomingMessageWithMetadataBox::try_from(state.payload) {
                Ok(payload) => payload,
                Err(error) => {
                    warn!(
                        ?length,
                        ?error,
                        "Discarding message whose payload could not be decoded"
                    );
                    return Ok(ProcessingOutcome::Discard {
                        reason: DiscardReason::PayloadDecodingFailed,
                        acknowledge: AcknowledgeContext {
                            sender_identity,
                            id: message_id,
                            flags: message_flags,
                            nonce: None,
                        },
                    });
                },
            }
        };
        trace!(?payload, "Decoded message");

        // Add tracing span
        let _span = tracing::info_span! {
            "message",
            id = ?payload.id,
            sender = ?payload.sender_identity,
        }
        .entered();

        // Check if the nonce has been used before
        if context.csp_e2e.nonce_storage.borrow().has(&payload.nonce)? {
            warn!("Discarding message due to nonce reuse");
            return Ok(ProcessingOutcome::Discard {
                reason: DiscardReason::NonceReuse,
                acknowledge: AcknowledgeContext::from(&payload),
            });
        }

        // Check that the user is the receiver
        if payload.receiver_identity != context.csp_e2e.user_identity {
            warn!("Discarding message not destined to the user");
            return Ok(ProcessingOutcome::Discard {
                reason: DiscardReason::ReceiverIsNotUser,
                acknowledge: AcknowledgeContext::from(&payload),
            });
        }

        // Lookup the sender in the next state
        let fetch_sender_task =
            ContactsLookupSubtask::new(vec![payload.sender_identity], CacheLookupPolicy::Allow);
        Self::process_fetch_sender_state(
            context,
            FetchSenderState {
                payload,
                fetch_sender_task,
            },
        )
    }

    fn poll_fetch_sender(
        context: &mut CspE2eProtocolContext,
        state: FetchSenderState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        match Self::process_fetch_sender_state(context, state)? {
            ProcessingOutcome::Ok((state, message_loop)) => Ok((state, message_loop)),
            ProcessingOutcome::Discard { acknowledge, .. } => {
                Self::acknowledge_and_discard(context, acknowledge)
            },
        }
    }

    fn process_fetch_sender_state(
        context: &mut CspE2eProtocolContext,
        mut state: FetchSenderState,
    ) -> Result<ProcessingOutcome<(Self, IncomingMessageLoop)>, CspE2eProtocolError> {
        let payload = &mut state.payload;

        // Poll until we have looked up the sender
        let sender = match state.fetch_sender_task.poll(context)? {
            ContactsLookupLoop::Instruction(instruction) => {
                return Ok(ProcessingOutcome::Ok((
                    Self::FetchSender(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::FetchSender(instruction)),
                )));
            },
            ContactsLookupLoop::Done(mut contacts) => contacts.remove(&payload.sender_identity),
        };

        // Add tracing span
        let _span = tracing::info_span! {
            "message",
            id = ?payload.id,
            sender = ?payload.sender_identity,
        }
        .entered();

        // Validate the sender
        let sender = {
            // Extract the sender from the task result
            let Some(sender) = sender else {
                let message = "Sender identity missing in contact lookup result";
                error!(message);
                return Err(CspE2eProtocolError::InternalError(message.into()));
            };

            // Ensure that the sender is not the user or invalid/revoked
            match sender {
                ContactResult::User => {
                    warn!("Discarding message where the sender is the user");
                    return Ok(ProcessingOutcome::Discard {
                        reason: DiscardReason::SenderIsUser,
                        acknowledge: AcknowledgeContext::from(&*payload),
                    });
                },
                ContactResult::Invalid(identity) => {
                    warn!(?identity, "Discarding message from an invalid/revoked identity");
                    return Ok(ProcessingOutcome::Discard {
                        reason: DiscardReason::SenderIsInvalid,
                        acknowledge: AcknowledgeContext::from(&*payload),
                    });
                },
                ContactResult::ExistingContact(sender) => ContactOrInit::ExistingContact(sender),
                ContactResult::NewContact(sender) => ContactOrInit::NewContact(sender),
            }
        };

        // Decrypt and decode the message
        //
        // Note: At this point the message is only decoded and validated to some degree. The receive
        // steps have not been run, yet.
        let message = match decrypt_and_decode_message(
            context
                .csp_e2e
                .client_key
                .derive_csp_e2e_key(&sender.inner().public_key),
            payload,
            &sender,
        ) {
            ProcessingOutcome::Ok(message) => message,
            ProcessingOutcome::Discard { reason, acknowledge } => {
                return Ok(ProcessingOutcome::Discard { reason, acknowledge });
            },
        };
        let inner_type = message.inner.message_type();

        // Check the message ID for divergences or re-use
        if let Some(metadata) = message.metadata.as_ref()
            && metadata.message_id != payload.id
        {
            warn!(
                ?inner_type,
                metadata_message_id = ?metadata.message_id,
                "Discarding message with diverging message IDs",
            );
            return Ok(ProcessingOutcome::Discard {
                reason: DiscardReason::MessageIdsDiverging,
                acknowledge: AcknowledgeContext::from(&*payload),
            });
        }
        if context
            .conversations
            .borrow()
            .message_is_marked_used(sender.inner().identity, payload.id)?
        {
            warn!(?inner_type, "Discarding message with reused message ID");
            return Ok(ProcessingOutcome::Discard {
                reason: DiscardReason::MessageIdReuse,
                acknowledge: AcknowledgeContext::from(&*payload),
            });
        }

        // TODO(LIB-42): If inner-type is not defined (i.e. handling an FS control message), log a
        // notice, Acknowledge and discard the message and abort these steps.

        // Check if the message is exempted from blocking and otherwise check if the contact is
        // blocked
        if !message.inner.is_exempt_from_blocking() {
            let communication_permission = sender.communication_permission(
                &context.config,
                &*context.settings.borrow(),
                &*context.contacts.borrow(),
            )?;
            match communication_permission {
                CommunicationPermission::Allow => {},
                CommunicationPermission::BlockExplicit | CommunicationPermission::BlockUnknown => {
                    info!(
                        ?inner_type,
                        ?communication_permission,
                        "Discarding message for blocked sender"
                    );
                    return Ok(ProcessingOutcome::Discard {
                        reason: DiscardReason::MessageSenderIsBlocked,
                        acknowledge: AcknowledgeContext::from(&*payload),
                    });
                },
            }
        }

        // Apply special contact handling (right now, this is only `*3MAPUSH`)
        if sender.inner().identity == PredefinedContact::_3MAPUSH_IDENTITY {
            // Only use case right now is the `web-session-resume` which we just forward
            let IncomingMessageBody::Contact(ContactMessageBody::WebSessionResume(web_session_resume)) =
                message.inner.body
            else {
                warn!(?inner_type, "Unexpected message type from *3MAPUSH");
                return Ok(ProcessingOutcome::Discard {
                    reason: DiscardReason::MessageBy3maPushUnexpected,
                    acknowledge: AcknowledgeContext::from(&*payload),
                });
            };
            context.shortcut.handle_web_session_resume(web_session_resume)?;

            // Nothing more to do
            return Ok(ProcessingOutcome::Ok((
                State::Done,
                IncomingMessageLoop::Done(IncomingMessageResult {
                    outgoing_message_ack: Some(MessageAck {
                        sender_identity: message.inner.sender_identity,
                        id: message.inner.id,
                    }),
                    outgoing_message_task: None,
                }),
            )));
        }

        // Handle messages from other special contacts
        let effective_message_properties = message.inner.effective_properties();
        if let Some(predefined_contact) = context.config.predefined_contacts.get(&sender.inner().identity)
            && predefined_contact.special
        {
            // Skip any processing and only reflect (if necessary) and acknowledge
            return Self::reflect_and_acknowledge(context, message, &effective_message_properties)
                .map(ProcessingOutcome::Ok);
        }

        // Check if we should discard the message if the sender is not already a contact
        if !effective_message_properties.requires_direct_contact
            && matches!(sender, ContactOrInit::NewContact(_))
        {
            info!(
                ?inner_type,
                "Discarding message that does not require adding missing sender contact"
            );
            return Ok(ProcessingOutcome::Discard {
                reason: DiscardReason::MessageMayNotImplicitlyAddContact,
                acknowledge: AcknowledgeContext::from(&*payload),
            });
        }

        // Extract the sender's (potentially) updated nickname, if available
        //
        // Note: The nicknames were already trimmed at this point.
        let sender_nickname = if let Some(metadata) = message.metadata.as_ref() {
            metadata.nickname.clone()
        } else if effective_message_properties.user_profile_distribution {
            match payload.legacy_sender_nickname.clone() {
                Some(nickname) => Delta::Update(nickname),
                None => Delta::Remove,
            }
        } else {
            Delta::Unchanged
        };

        // Update or create the contact, if necessary
        match sender {
            // Update existing contact
            ContactOrInit::ExistingContact(sender) => {
                // Prepare the update
                let update = {
                    let mut update = ContactUpdate::default(sender.identity);
                    if effective_message_properties.requires_direct_contact
                        && sender.acquaintance_level != protobuf_contact::AcquaintanceLevel::Direct
                    {
                        update.acquaintance_level = Some(protobuf_contact::AcquaintanceLevel::Direct);
                    }
                    update.nickname = sender_nickname.changes(sender.nickname.as_ref());
                    update
                };

                // Apply the update or start handling the message
                if update.has_changes() {
                    // Update the contact in the next state
                    Self::process_update_contact_state(
                        context,
                        UpdateContactState {
                            message,
                            update_contact_task: UpdateContactsTask::new(vec![update]),
                            n_retries: 0,
                        },
                    )
                } else {
                    // Handle the message
                    Self::handle_message(context, message, &effective_message_properties)
                }
            },

            // Create new contact
            ContactOrInit::NewContact(mut sender) => {
                // Raise the acquaintance level to _direct_ and update the nickname accordingly
                //
                // Note: This only works as intended for groups because each member is added as part of the
                // `group-setup` receive steps with the acquaintance level _group_.
                sender.acquaintance_level = protobuf_contact::AcquaintanceLevel::Direct;
                sender.nickname.apply(sender_nickname);

                // Create the contact in the next state
                Self::process_create_contact_state(
                    context,
                    CreateContactState {
                        message,
                        create_contact_task: CreateContactsTask::new(vec![sender]),
                    },
                )
            },
        }
    }

    fn poll_update_contact(
        context: &mut CspE2eProtocolContext,
        state: UpdateContactState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        match Self::process_update_contact_state(context, state)? {
            ProcessingOutcome::Ok((state, message_loop)) => Ok((state, message_loop)),
            ProcessingOutcome::Discard { acknowledge, .. } => {
                Self::acknowledge_and_discard(context, acknowledge)
            },
        }
    }

    fn process_update_contact_state(
        context: &mut CspE2eProtocolContext,
        mut state: UpdateContactState,
    ) -> Result<ProcessingOutcome<(Self, IncomingMessageLoop)>, CspE2eProtocolError> {
        // Poll until we have updated the contact
        match state.update_contact_task.poll(context)? {
            UpdateContactsLoop::Instruction(instruction) => {
                return Ok(ProcessingOutcome::Ok((
                    Self::UpdateContact(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::UpdateContact(instruction)),
                )));
            },
            UpdateContactsLoop::Done(()) => {},
        }

        // There is a very slight chance that we have lost a race against another device which could have
        // updated the exact same contact but with a different acquaintance level, which we need to fix.
        let effective_message_properties = state.message.inner.effective_properties();
        if let Some(update_contact_task) = Self::ensure_correct_sender_contact_acquaintance_level(
            context,
            state.message.inner.sender_identity,
            &effective_message_properties,
            state.n_retries,
        )? {
            return Self::process_update_contact_state(
                context,
                UpdateContactState {
                    message: state.message,
                    update_contact_task,
                    n_retries: state.n_retries.wrapping_add(1),
                },
            );
        }

        // Handle the message
        Self::handle_message(context, state.message, &effective_message_properties)
    }

    fn poll_create_contact(
        context: &mut CspE2eProtocolContext,
        state: CreateContactState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        match Self::process_create_contact_state(context, state)? {
            ProcessingOutcome::Ok((state, message_loop)) => Ok((state, message_loop)),
            ProcessingOutcome::Discard { acknowledge, .. } => {
                Self::acknowledge_and_discard(context, acknowledge)
            },
        }
    }

    fn process_create_contact_state(
        context: &mut CspE2eProtocolContext,
        mut state: CreateContactState,
    ) -> Result<ProcessingOutcome<(Self, IncomingMessageLoop)>, CspE2eProtocolError> {
        // Poll until we have added the contact
        match state.create_contact_task.poll(context)? {
            CreateContactsLoop::Instruction(instruction) => {
                return Ok(ProcessingOutcome::Ok((
                    Self::CreateContact(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::CreateContact(instruction)),
                )));
            },
            CreateContactsLoop::Done(_) => {},
        }

        // There is a very slight chance that we have lost a race against another device which could have
        // created the exact same contact but with a different acquaintance level, which we need to fix.
        let effective_message_properties = state.message.inner.effective_properties();
        if let Some(update_contact_task) = Self::ensure_correct_sender_contact_acquaintance_level(
            context,
            state.message.inner.sender_identity,
            &effective_message_properties,
            0,
        )? {
            return Self::process_update_contact_state(
                context,
                UpdateContactState {
                    message: state.message,
                    update_contact_task,
                    n_retries: 1,
                },
            );
        }

        // Handle the message
        Self::handle_message(context, state.message, &effective_message_properties)
    }

    fn ensure_correct_sender_contact_acquaintance_level(
        context: &mut CspE2eProtocolContext,
        sender_identity: ThreemaId,
        effective_message_properties: &MessageProperties,
        n_retries: u8,
    ) -> Result<Option<UpdateContactsTask>, CspE2eProtocolError> {
        if !effective_message_properties.requires_direct_contact {
            return Ok(None);
        }

        // Do not try more than 3 times as this is extremely unlikely
        if n_retries == 3 {
            return Err(CspE2eProtocolError::NetworkError(
                "I think your other devices are trolling me".to_owned(),
            ));
        }

        // Check if we need to adjust the acquaintance level again
        let sender = context.contacts.borrow().get(sender_identity)?.ok_or_else(|| {
            let message = "Sender contact disappeared";
            error!(message);
            CspE2eProtocolError::DesyncError(message.to_owned())
        })?;
        if sender.acquaintance_level == protobuf_contact::AcquaintanceLevel::Direct {
            return Ok(None);
        }

        // Prepare the update
        warn!(
            sender_acquaintance_level = ?sender.acquaintance_level,
            n_retries,
            "Sender contact acquaintance level changed, adjusting to direct",
        );
        let update = {
            let mut update = ContactUpdate::default(sender.identity);
            update.acquaintance_level = Some(protobuf_contact::AcquaintanceLevel::Direct);
            update
        };
        Ok(Some(UpdateContactsTask::new(vec![update])))
    }

    fn handle_message(
        context: &mut CspE2eProtocolContext,
        message: IncomingMessageParts,
        effective_message_properties: &MessageProperties,
    ) -> Result<ProcessingOutcome<(Self, IncomingMessageLoop)>, CspE2eProtocolError> {
        let inner_type = message.inner.message_type();

        // Run the receive steps associated to the message
        match &message.inner.body {
            // All 1:1 messages...
            IncomingMessageBody::Contact(body) => match body {
                ContactMessageBody::Text(_)
                | ContactMessageBody::Location(_)
                | ContactMessageBody::DeliveryReceipt(_)
                | ContactMessageBody::LegacyReaction(_) => {
                    info!(?inner_type, "Adding message to 1:1 conversation");
                    context
                        .conversations
                        .borrow_mut()
                        .add_or_update_incoming_message(message.inner.clone())?;
                },

                ContactMessageBody::WebSessionResume(_) => {
                    // Only allowed from `*3MAPUSH`
                    warn!(?inner_type, "Discarding message only allowed from *3MAPUSH");
                    return Ok(ProcessingOutcome::Discard {
                        reason: DiscardReason::MessageOnlyAllowedFrom3maPush,
                        acknowledge: AcknowledgeContext::from(&message),
                    });
                },
            },

            // All group messages...
            //
            // TODO(LIB-53): Handle them
            IncomingMessageBody::Group(_) => {
                return Err(CspE2eProtocolError::DesyncError(
                    "Ain't nobody got time to implement handling group messages".to_owned(),
                ));
            },
        }

        // Reflect (if necessary) and acknowledge
        Self::reflect_and_acknowledge(context, message, effective_message_properties)
            .map(ProcessingOutcome::Ok)
    }

    fn reflect_and_acknowledge(
        context: &mut CspE2eProtocolContext,
        message: IncomingMessageParts,
        effective_message_properties: &MessageProperties,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        // (MD) Reflect and await acknowledgement, if necessary
        if let Some(d2x_context) = context.d2x.as_mut()
            && effective_message_properties.reflect_incoming
        {
            let (reflect_message, nonce) = ReflectPayload::encode_and_encrypt(
                d2x_context,
                ReflectFlags::from(effective_message_properties),
                protobuf::d2d::envelope::Content::IncomingMessage(protobuf::d2d::IncomingMessage {
                    sender_identity: message.inner.sender_identity.as_str().to_owned(),
                    message_id: message.inner.id.0,
                    created_at: message.inner.created_at,
                    r#type: message.outer.message_type.into(),
                    body: message.outer.unpadded_message_data,
                    nonce: message.outer.nonce.0.to_vec(),
                }),
            )?;
            d2x_context.nonce_storage.borrow_mut().add_many(vec![nonce])?;
            let (reflect_message_task, reflect_instruction) = ReflectSubtask::new(vec![reflect_message]);
            return Ok((
                Self::ReflectMessage(ReflectMessageState {
                    sender_identity: message.inner.sender_identity,
                    message_id: message.inner.id,
                    message_flags: message.outer.flags,
                    message_nonce: message.outer.nonce.clone(),
                    effective_message_properties: effective_message_properties.clone(),
                    reflect_message_task,
                }),
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::ReflectMessage(
                    reflect_instruction,
                )),
            ));
        }

        // Acknowledge the message
        Self::acknowledge_message_and_schedule_delivery_receipt(
            context,
            message.inner.sender_identity,
            message.inner.id,
            message.outer.flags,
            message.outer.nonce,
            effective_message_properties,
        )
    }

    fn poll_reflect_message(
        context: &mut CspE2eProtocolContext,
        state: ReflectMessageState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        // Fulfill the reflection
        state.reflect_message_task.poll()?;

        // Acknowledge the message
        Self::acknowledge_message_and_schedule_delivery_receipt(
            context,
            state.sender_identity,
            state.message_id,
            state.message_flags,
            state.message_nonce,
            &state.effective_message_properties,
        )
    }

    fn acknowledge_message_and_schedule_delivery_receipt(
        context: &mut CspE2eProtocolContext,
        sender_identity: ThreemaId,
        message_id: MessageId,
        message_flags: MessageFlags,
        message_nonce: Nonce,
        effective_message_properties: &MessageProperties,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        let outgoing_message_ack = Self::acknowledge(
            &mut context.csp_e2e,
            AcknowledgeContext {
                sender_identity,
                id: message_id,
                flags: message_flags,
                // Only mark the nonce as used if we need replay protection for the message
                nonce: if effective_message_properties.replay_protection {
                    Some(message_nonce)
                } else {
                    None
                },
            },
        )?;

        Ok((
            Self::Done,
            IncomingMessageLoop::Done(IncomingMessageResult {
                outgoing_message_ack,
                // Schedule sending a delivery receipt with status _received_, if needed
                outgoing_message_task: if effective_message_properties.delivery_receipts {
                    // TODO(LIB-16): Do it!
                    Some(())
                } else {
                    None
                },
            }),
        ))
    }

    fn acknowledge_and_discard(
        context: &mut CspE2eProtocolContext,
        acknowledge: AcknowledgeContext,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        Ok((
            Self::Done,
            IncomingMessageLoop::Done(IncomingMessageResult {
                outgoing_message_ack: Self::acknowledge(&mut context.csp_e2e, acknowledge)?,
                outgoing_message_task: None,
            }),
        ))
    }

    #[expect(clippy::needless_pass_by_value, reason = "Prevent re-use")]
    fn acknowledge(
        context: &mut CspE2eContext,
        acknowledge: AcknowledgeContext,
    ) -> Result<Option<MessageAck>, ProviderError> {
        // Mark the nonce as used if the message requires protection against replay attacks
        if let Some(message_nonce) = acknowledge.nonce.as_ref() {
            context
                .nonce_storage
                .borrow_mut()
                .add_many(vec![message_nonce.clone()])?;
        }

        // TODO(LIB-42): Run `fs-commit-fn` if applicable. Ensure this does not get executed if called prior
        // to decapsulation.

        // Acknowledge only if needed
        Ok(
            if acknowledge.flags.0 & MessageFlags::NO_SERVER_ACKNOWLEDGEMENT == 0 {
                Some(MessageAck {
                    sender_identity: acknowledge.sender_identity,
                    id: acknowledge.id,
                })
            } else {
                None
            },
        )
    }
}

/// Task for processing an incoming message.
#[derive(Debug, Name)]
pub struct IncomingMessageTask {
    state: State,
}
impl IncomingMessageTask {
    pub(crate) fn new(payload: MessageWithMetadataBox) -> Self {
        Self {
            state: State::Init(InitState { payload }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(
        &mut self,
        context: &mut CspE2eProtocolContext,
    ) -> Result<IncomingMessageLoop, CspE2eProtocolError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                IncomingMessageTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => State::poll_init(context, state),
            State::FetchSender(state) => State::poll_fetch_sender(context, state),
            State::CreateContact(state) => State::poll_create_contact(context, state),
            State::UpdateContact(state) => State::poll_update_contact(context, state),
            State::ReflectMessage(state) => State::poll_reflect_message(context, state),
            State::Done => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} already done",
                IncomingMessageTask::NAME
            ))),
        };
        match result {
            Ok((state, instruction)) => {
                self.state = state;
                debug!(state = ?self.state, "Changed state");
                Ok(instruction)
            },
            Err(error) => {
                self.state = State::Error(error.clone());
                debug!(state = ?self.state, "Changed state to error");
                Err(error)
            },
        }
    }

    /// Possible results after handling a [`IncomingMessageInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(&mut self, response: IncomingMessageResponse) -> Result<(), CspE2eProtocolError> {
        match response {
            IncomingMessageResponse::FetchSender(response) => {
                let State::FetchSender(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::FETCH_SENDER
                    )));
                };
                state.fetch_sender_task.response(response)
            },
            IncomingMessageResponse::CreateContact(response) => {
                let State::CreateContact(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::CREATE_CONTACT
                    )));
                };
                state.create_contact_task.response(response)
            },
            IncomingMessageResponse::UpdateContact(response) => {
                let State::UpdateContact(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::UPDATE_CONTACT
                    )));
                };
                state.update_contact_task.response(response)
            },
            IncomingMessageResponse::ReflectMessage(response) => {
                let State::ReflectMessage(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::REFLECT_MESSAGE
                    )));
                };
                state.reflect_message_task.response(response);
                Ok(())
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use core::cell::RefCell;
    use std::rc::Rc;

    use assert_matches::assert_matches;
    use data_encoding::HEXLOWER;
    use derive_builder::Builder;
    use rstest::rstest;

    use super::*;
    use crate::{
        common::{
            ClientInfo, ConversationId, D2xDeviceId, FeatureMask, MessageFlags, MessageId, Nonce, ThreemaId,
            config::{Config, Flavor, WorkContext, WorkCredentials, WorkFlavor},
            keys::{ClientKey, DeviceGroupKey, PublicKey, RawClientKey},
        },
        csp_e2e::{
            CspE2eContextInit, CspE2eProtocolContext, CspE2eProtocolContextInit, CspE2eProtocolError,
            D2xContextInit,
            contacts::lookup::{CachedContactResult, ContactsLookupSubtask},
            message::{
                payload::OutgoingMessageWithMetadataBox,
                task::outgoing::{
                    encode_and_encrypt_message, encode_metadata, encrypt_message_container_in_place,
                    encrypt_metadata_in_place,
                },
            },
        },
        model::{
            contact::Contact,
            message::{
                DeliveryReceiptMessage, DeliveryReceiptType, LocationMessage, MessageLifetime,
                OutgoingContactMessageBody, OutgoingMessage, OutgoingMessageBody, TextMessage,
                WebSessionResumeMessage,
            },
            provider::{
                ShortcutProvider,
                in_memory::{InMemoryDb, InMemoryDbInit, InMemoryDbSettings},
            },
        },
        protobuf::{self, d2d_sync::contact as protobuf_contact},
        utils::{
            bytes::{OwnedVecByteReader, OwnedVecByteWriter},
            time::utc_now_ms,
        },
    };

    trait TestSender {
        fn identity() -> ThreemaId;
        fn public_key() -> PublicKey;
        fn nickname() -> Delta<String>;

        fn contact() -> Contact {
            Contact {
                identity: Self::identity(),
                public_key: Self::public_key(),
                created_at: utc_now_ms(),
                first_name: None,
                last_name: None,
                // We're intentionally not using `Self::nickname()` here so that we can check if the nickname
                // was applied after receiving.
                nickname: None,
                verification_level: protobuf_contact::VerificationLevel::Unverified,
                work_verification_level: protobuf_contact::WorkVerificationLevel::None,
                identity_type: protobuf_contact::IdentityType::Regular,
                acquaintance_level: protobuf_contact::AcquaintanceLevel::GroupOrDeleted,
                activity_state: protobuf_contact::ActivityState::Active,
                feature_mask: FeatureMask(FeatureMask::NONE),
                sync_state: protobuf_contact::SyncState::Initial,
                read_receipt_policy_override: None,
                typing_indicator_policy_override: None,
                notification_trigger_policy_override: None,
                notification_sound_policy_override: None,
                conversation_category: protobuf::d2d_sync::ConversationCategory::Default,
                conversation_visibility: protobuf::d2d_sync::ConversationVisibility::Normal,
            }
        }

        fn cached() -> (ThreemaId, CachedContactResult) {
            (Self::identity(), CachedContactResult::NewContact(Self::contact()))
        }

        fn cached_invalid() -> (ThreemaId, CachedContactResult) {
            let identity = Self::identity();
            (identity, CachedContactResult::Invalid(identity))
        }

        fn create_message(
            context: &CspE2eContext,
            message: &OutgoingMessage,
        ) -> OutgoingMessageWithMetadataBox {
            let nickname = Self::nickname();
            encode_and_encrypt_message(
                Self::identity(),
                (nickname.as_deref().clone().into_non_empty(), nickname.as_deref()),
                context.user_identity,
                context.client_key.derive_csp_e2e_key(&Self::public_key()),
                message,
                Nonce::random(),
            )
        }

        fn create_message_from_body(
            context: &CspE2eContext,
            message_body: OutgoingMessageBody,
        ) -> OutgoingMessageWithMetadataBox {
            Self::create_message(
                context,
                &OutgoingMessage {
                    id: MessageId::random(),
                    overrides: MessageOverrides::default(),
                    created_at: utc_now_ms(),
                    body: message_body,
                },
            )
        }

        fn create_text_message(context: &CspE2eContext, text: String) -> OutgoingMessageWithMetadataBox {
            Self::create_message_from_body(
                context,
                OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                    receiver_identity: context.user_identity,
                    body: ContactMessageBody::Text(TextMessage { text }),
                }),
            )
        }
    }

    struct StaticMessage {
        message: MessageWithMetadataBox,
        expected_id: MessageId,
        expected_created_at: u64,
        expected_properties: MessageProperties,
        expected_nonce: Nonce,
        expected_body: IncomingMessageBody,
    }

    struct StaticTestSender;
    impl StaticTestSender {
        const IDENTITY: ThreemaId = ThreemaId::predefined(*b"0DA5ME76");

        fn static_text_message() -> StaticMessage {
            let message = HEXLOWER
                .decode(
                    b"\
                    304441354d453736304850543945574489aa9a7eaff77d96cb732768010034000000000000000000\
                    000000000000000000000000000000000000000000000000439039d79074fa4a0d0961d651af57b9\
                    4b7245c4c686733aab55b7f2b049fa3a8a5fec982eaa27d37557beaadd802cfc2703d849b2d718b0\
                    db179e3e3bcdbf3c2be997490a0349f2e4fbaa43712e263aab0c2c4a920182f01f810df063363191\
                    b26c2c6404c0e84e73a3e005e327589702878e259642e1cf3b29e36db1c6a258f55ea73c842fddaf\
                    ffd76a3057c0c13b6881bccc6522a0edee793f586fcb9ec5b398eb3be0af1a8c6111fe463ed25d91\
                    6e66bea54955ca3398e27cbae25bfb6c16e26f326ecf8a4ba81aef9312b59f612b9e3355de6c14c0\
                    434dc195e0a03462fc95d836a7bca74bda61d59be8489a9fdd9e626e7cb7324ac0724b0a42168a5b\
                    ea525eaef17d3bf13bbd8551ab8c85f5892fa6ba9c32e01343c3bc8ed2ad59f54411de089b193dca\
                    452b9699dafe34d124dfe521a956cce4adf58902a4c7b8bcf3d4548848dd2f1bee",
                )
                .unwrap();
            let message = MessageWithMetadataBox::decode(OwnedVecByteReader::new(message)).unwrap();
            StaticMessage {
                message,
                expected_id: MessageId::from_hex("89aa9a7eaff77d96").unwrap(),
                expected_created_at: 1747416011868,
                expected_properties: MessageProperties {
                    push: true,
                    lifetime: MessageLifetime::Indefinite,
                    user_profile_distribution: true,
                    requires_direct_contact: true,
                    replay_protection: true,
                    reflect_incoming: true,
                    reflect_outgoing: true,
                    reflect_sent_update: true,
                    delivery_receipts: true,
                },
                expected_nonce: Nonce::from_hex("b2d718b0db179e3e3bcdbf3c2be997490a0349f2e4fbaa43").unwrap(),
                expected_body: IncomingMessageBody::Contact(ContactMessageBody::Text(TextMessage {
                    text: "Hi".to_owned(),
                })),
            }
        }

        fn static_location_message() -> StaticMessage {
            let message = HEXLOWER
                .decode(
                    b"\
                    304441354d45373630485054394557446d6b2e0a01d312587458ee68010034000000000000000000\
                    0000000000000000000000000000000000000000000000002891b9460574ca923a999cfefc0560e9\
                    56f7b1d68ee817800ae6230b747a2b72a7f11de76ebbf889421e99d927c3008cfd14440c8326dff3\
                    ad0296f74ab49c81dd9cc234f75580a99d19051cab78e5cba9aaf8761333e50510e6392eff140671\
                    9f7aec01bf4bf347dedfce7e31fd7fe5874f6904723baaef574c8af07b635f900d1c6afdea7c6e36\
                    671dd4a3b52f05c0bee93d525b3333eded57aeb027238ec4e746739a65cd5ef42dac5880b6232d5f\
                    65f690e32ce61196dfa91ddfa598083439adeb4ac2bc77dfb503eef3a95938bf244122e1f4499dd9\
                    cb19b3ec729ab70529014c3e0780734719a9cbeec521c7e22b41da13daa292078224aebec4670238\
                    8f22e227c9429f2177770cd9e2a21aa3d04f6dc80cef550b54e2bd7fb79ff7cd511d6e2bd0cb2d2b\
                    c1e34e0bb3861a80676d7c6c0089b72af08d7ee06b96824f33cf4d4c1677407804f49445e3cb192b\
                    472a656ebd0790620d710593d3598d",
                )
                .unwrap();
            let message = MessageWithMetadataBox::decode(OwnedVecByteReader::new(message)).unwrap();
            StaticMessage {
                message,
                expected_id: MessageId::from_hex("6d6b2e0a01d31258").unwrap(),
                expected_created_at: 1760450676889,
                expected_properties: MessageProperties {
                    push: true,
                    lifetime: MessageLifetime::Indefinite,
                    user_profile_distribution: true,
                    requires_direct_contact: true,
                    replay_protection: true,
                    reflect_incoming: true,
                    reflect_outgoing: true,
                    reflect_sent_update: true,
                    delivery_receipts: true,
                },
                expected_nonce: Nonce::from_hex("8326dff3ad0296f74ab49c81dd9cc234f75580a99d19051c").unwrap(),
                expected_body: IncomingMessageBody::Contact(ContactMessageBody::Location(LocationMessage {
                    latitude: 47.202952,
                    longitude: 8.778079,
                    accuracy_m: Some(0.0_f64),
                    name: Some("Pfäffikon SZ".to_owned()),
                    address: Some("Unknown address".to_owned()),
                })),
            }
        }
    }
    impl TestSender for StaticTestSender {
        fn identity() -> ThreemaId {
            Self::IDENTITY
        }

        #[rustfmt::skip]
        fn public_key() -> PublicKey {
            PublicKey::from([
                0xc1, 0x13, 0xa3, 0xd8, 0x57, 0x56, 0x85, 0x26,
                0x02, 0xcc, 0x82, 0xcb, 0x69, 0x31, 0x4e, 0x87,
                0xa0, 0x17, 0x82, 0xc5, 0xff, 0xde, 0xf3, 0xa9,
                0x53, 0xcf, 0x36, 0xac, 0x7d, 0x6b, 0xc5, 0x28,
            ])
        }

        fn nickname() -> Delta<String> {
            Delta::Update("lenny-test@pixel8a".to_owned())
        }
    }

    struct ThreemaSupportTestSender;
    impl ThreemaSupportTestSender {
        const IDENTITY: ThreemaId = ThreemaId::predefined(*b"*SUPPORT");
        const NICKNAME: &'static str = "Marcel Davis";

        fn predefined() -> PredefinedContact {
            PredefinedContact {
                identity: Self::IDENTITY,
                special: false,
                public_key: Self::public_key(),
                nickname: Self::NICKNAME.to_owned(),
            }
        }
    }
    impl TestSender for ThreemaSupportTestSender {
        fn identity() -> ThreemaId {
            Self::IDENTITY
        }

        #[rustfmt::skip]
        fn public_key() -> PublicKey {
            PublicKey::from([
                0x0f, 0x94, 0x4d, 0x18, 0x32, 0x4b, 0x21, 0x32,
                0xc6, 0x1d, 0x8e, 0x40, 0xaf, 0xce, 0x60, 0xa0,
                0xeb, 0xd7, 0x01, 0xbb, 0x11, 0xe8, 0x9b, 0xe9,
                0x49, 0x72, 0xd4, 0x22, 0x9e, 0x94, 0x72, 0x2a,
            ])
        }

        fn nickname() -> Delta<String> {
            Delta::Update(Self::NICKNAME.to_owned())
        }

        fn contact() -> Contact {
            Contact::from(&Self::predefined())
        }
    }

    struct ThreemaSpecialTestSender;
    impl ThreemaSpecialTestSender {
        const IDENTITY: ThreemaId = ThreemaId::predefined(*b"*SPECIAL");
        const NICKNAME: &'static str = "Unnamed heroine";

        fn predefined() -> PredefinedContact {
            PredefinedContact {
                identity: Self::IDENTITY,
                special: true,
                public_key: Self::public_key(),
                nickname: Self::NICKNAME.to_owned(),
            }
        }
    }
    impl TestSender for ThreemaSpecialTestSender {
        fn identity() -> ThreemaId {
            Self::IDENTITY
        }

        #[rustfmt::skip]
        fn public_key() -> PublicKey {
            PublicKey::from([
                0x3a, 0x38, 0x65, 0x0c, 0x68, 0x14, 0x35, 0xbd,
                0x1f, 0xb8, 0x49, 0x8e, 0x21, 0x3a, 0x29, 0x19,
                0xb0, 0x93, 0x88, 0xf5, 0x80, 0x3a, 0xa4, 0x46,
                0x40, 0xe0, 0xf7, 0x06, 0x32, 0x6a, 0x86, 0x5c,
            ])
        }

        fn nickname() -> Delta<String> {
            Delta::Update(Self::NICKNAME.to_owned())
        }

        fn contact() -> Contact {
            Contact::from(&Self::predefined())
        }
    }

    struct ThreemaPushTestSender;
    impl ThreemaPushTestSender {
        const NICKNAME: &'static str = "Threema Pushmeister";

        fn predefined() -> PredefinedContact {
            PredefinedContact {
                identity: Self::identity(),
                special: true,
                public_key: Self::public_key(),
                nickname: Self::NICKNAME.to_owned(),
            }
        }
    }
    impl TestSender for ThreemaPushTestSender {
        fn identity() -> ThreemaId {
            PredefinedContact::_3MAPUSH_IDENTITY
        }

        #[rustfmt::skip]
        fn public_key() -> PublicKey {
            PublicKey::from([
                0xfd, 0x71, 0x1e, 0x1a, 0x0d, 0xb0, 0xe2, 0xf0,
                0x3f, 0xca, 0xab, 0x6c, 0x43, 0xda, 0x25, 0x75,
                0xb9, 0x51, 0x36, 0x64, 0xa6, 0x2a, 0x12, 0xbd,
                0x07, 0x28, 0xd8, 0x7f, 0x71, 0x25, 0xcc, 0x24,
            ])
        }

        fn nickname() -> Delta<String> {
            Delta::Update(Self::NICKNAME.to_owned())
        }

        fn contact() -> Contact {
            Contact::from(&Self::predefined())
        }
    }

    struct TestShortcutProvider {
        web_session_resume_buffer: Rc<RefCell<Vec<WebSessionResumeMessage>>>,
    }
    impl ShortcutProvider for TestShortcutProvider {
        fn handle_web_session_resume(
            &mut self,
            message: WebSessionResumeMessage,
        ) -> Result<(), ProviderError> {
            self.web_session_resume_buffer.borrow_mut().push(message);
            Ok(())
        }
    }

    #[derive(Builder)]
    #[builder(pattern = "owned")]
    struct ContextInit {
        #[builder(default = "\"0HPT9EWD\"")]
        identity: &'static str,

        #[builder(default = "\"5d31a1950a807edcd195ec16a7e980e525c8bafe315d3b9bd9b22b186b4096bd\"")]
        client_key: &'static str,

        #[builder(default = "Flavor::Consumer")]
        flavor: Flavor,

        #[builder(default = "None")]
        device_group_key: Option<DeviceGroupKey>,

        #[builder(default = "Vec::default()")]
        predefined_contacts: Vec<PredefinedContact>,

        #[builder(default = "Vec::default()")]
        blocked_identities: Vec<ThreemaId>,

        #[builder(default = "Vec::default()")]
        contacts: Vec<Contact>,

        #[builder(default = "InMemoryDbSettings::default()")]
        settings: InMemoryDbSettings,

        #[builder(default = "Vec::default()")]
        contact_lookup_cache: Vec<(ThreemaId, CachedContactResult)>,
    }
    impl ContextInitBuilder {
        fn with_multi_device(mut self) -> Self {
            self.device_group_key = Some(Some(DeviceGroupKey::from([0xab_u8; DeviceGroupKey::LENGTH])));
            self
        }

        fn with_work_flavor(mut self) -> Self {
            self.flavor = Some(Flavor::Work(WorkContext {
                credentials: WorkCredentials {
                    username: "nein".to_owned(),
                    password: "doch".to_owned(),
                },
                flavor: WorkFlavor::Work,
            }));
            self
        }

        fn with_onprem_flavor(mut self) -> Self {
            self.flavor = Some(Flavor::Work(WorkContext {
                credentials: WorkCredentials {
                    username: "nein".to_owned(),
                    password: "doch".to_owned(),
                },
                flavor: WorkFlavor::OnPrem,
            }));
            self
        }
    }

    struct Context {
        database: InMemoryDb,
        web_session_resume_buffer: Rc<RefCell<Vec<WebSessionResumeMessage>>>,
        context: CspE2eProtocolContext,
    }
    impl From<ContextInit> for Context {
        fn from(init: ContextInit) -> Self {
            // Create empty database
            let user_identity = ThreemaId::try_from(init.identity).unwrap();
            let mut database = InMemoryDb::from(InMemoryDbInit {
                user_identity,
                settings: init.settings,
                contacts: init.contacts,
                blocked_identities: init.blocked_identities,
            });

            // Add predefined contacts
            let mut config = Config::testing();
            for predefined_contact in init.predefined_contacts {
                let _ = config
                    .predefined_contacts
                    .insert(predefined_contact.identity, predefined_contact);
            }

            // Create protocol context
            let web_session_resume_buffer = Rc::new(RefCell::new(vec![]));
            let context = CspE2eProtocolContext::from(CspE2eProtocolContextInit {
                client_info: ClientInfo::Libthreema,
                config: Rc::new(config),
                csp_e2e: CspE2eContextInit {
                    user_identity,
                    client_key: ClientKey::from(&RawClientKey::from_hex(init.client_key).unwrap()),
                    flavor: init.flavor,
                    nonce_storage: Box::new(RefCell::new(database.csp_e2e_nonce_provider())),
                },
                d2x: init.device_group_key.map(|device_group_key| D2xContextInit {
                    device_id: D2xDeviceId(0x01),
                    device_group_key,
                    nonce_storage: Box::new(RefCell::new(database.d2d_nonce_provider())),
                }),
                shortcut: Box::new(TestShortcutProvider {
                    web_session_resume_buffer: Rc::clone(&web_session_resume_buffer),
                }),
                settings: Box::new(RefCell::new(database.settings_provider())),
                contacts: Box::new(RefCell::new(database.contact_provider())),
                conversations: Box::new(RefCell::new(database.message_provider())),
            });
            let mut context = Context {
                database,
                web_session_resume_buffer,
                context,
            };

            // Add cached contact results
            for (identity, contact_result) in init.contact_lookup_cache {
                let _ = context
                    .context
                    .contact_lookup_cache
                    .insert(identity, contact_result);
            }

            context
        }
    }

    #[test]
    fn init_bad_message() -> anyhow::Result<()> {
        let mut context = Context::from(ContextInitBuilder::default().build()?);

        // Set up a bogus message
        let sender_identity = ThreemaId::try_from("HAHAHAHA")?;
        let message_id = MessageId(0x00);
        let message_flags = MessageFlags(0x00);
        let state_fn = || InitState {
            payload: MessageWithMetadataBox {
                sender_identity,
                id: message_id,
                flags: message_flags,
                bytes: vec![],
            },
        };

        // We'll test `State::acknowledge_and_discard` here once implicitly. Further tests will just take a
        // shortcut to the `process_*` family of functions that yield the discard reasons.
        {
            let (state, instruction) = State::poll_init(&mut context.context, state_fn())?;
            assert_matches!(state, State::Done);
            let result = assert_matches!(instruction, IncomingMessageLoop::Done(result) => result);
            let message_ack = assert_matches!(result.outgoing_message_ack, Some(message_ack) => message_ack);
            assert_eq!(message_ack.sender_identity, sender_identity);
            assert_eq!(message_ack.id, message_id);
            assert_eq!(result.outgoing_message_task, None);
        }

        assert_matches!(
            State::process_init_state(&mut context.context, state_fn())?,
            ProcessingOutcome::Discard { reason, acknowledge } => {
                assert_eq!(reason, DiscardReason::PayloadDecodingFailed);
                assert_eq!(acknowledge.sender_identity, sender_identity);
                assert_eq!(acknowledge.id, message_id);
                assert_eq!(acknowledge.flags, message_flags);
                assert_eq!(acknowledge.nonce, None);
            }
        );

        Ok(())
    }

    #[test]
    fn init_multi_device_not_leading() -> anyhow::Result<()> {
        let mut context = Context::from(ContextInitBuilder::default().with_multi_device().build()?);
        let state = InitState {
            payload: StaticTestSender::static_text_message().message,
        };

        let result = State::process_init_state(&mut context.context, state);
        assert_matches!(result, Err(CspE2eProtocolError::InvalidState(_)));

        Ok(())
    }

    #[test]
    fn init_nonce_reuse() -> anyhow::Result<()> {
        let mut context = Context::from(ContextInitBuilder::default().build()?);
        let static_message = StaticTestSender::static_text_message();
        let state = InitState {
            payload: static_message.message.clone(),
        };
        let message = IncomingMessageWithMetadataBox::try_from(static_message.message)?;

        // Mark the nonce as used
        context
            .context
            .csp_e2e
            .nonce_storage
            .borrow_mut()
            .add_many(vec![static_message.expected_nonce])?;

        assert_matches!(
            State::process_init_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, acknowledge } => {
                assert_eq!(reason, DiscardReason::NonceReuse);
                assert_eq!(acknowledge.sender_identity, message.sender_identity);
                assert_eq!(acknowledge.id, message.id);
                assert_eq!(acknowledge.flags, message.flags);
                assert_eq!(acknowledge.nonce, Some(message.nonce));
            }
        );

        Ok(())
    }

    #[test]
    fn init_invalid_receiver() -> anyhow::Result<()> {
        // Use a different receiver (loud cat)
        let mut context = Context::from(ContextInitBuilder::default().identity("MEOWMEOW").build()?);
        let state = InitState {
            payload: StaticTestSender::static_text_message().message,
        };

        assert_matches!(
            State::process_init_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::ReceiverIsNotUser);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default())]
    #[case(ContextInitBuilder::default().with_work_flavor())]
    #[case(ContextInitBuilder::default().with_onprem_flavor())]
    fn init_valid_unknown_sender(#[case] context_builder: ContextInitBuilder) -> anyhow::Result<()> {
        let mut context = Context::from(context_builder.build()?);
        let state = InitState {
            payload: StaticTestSender::static_text_message().message,
        };

        let (state, instruction) = State::poll_init(&mut context.context, state)?;
        assert_matches!(state, State::FetchSender(_));
        let instruction = assert_matches!(
            instruction,
            IncomingMessageLoop::Instruction(
                IncomingMessageInstruction::FetchSender(instruction)) => instruction
        );
        match context.context.csp_e2e.flavor {
            Flavor::Consumer => assert_matches!(instruction.work_directory_request, None),
            Flavor::Work(_) => assert_matches!(instruction.work_directory_request, Some(_)),
        }

        Ok(())
    }

    #[test]
    fn fetch_sender_bad_accounting_missing_sender() -> anyhow::Result<()> {
        let mut context = Context::from(ContextInitBuilder::default().build()?);
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(
                StaticTestSender::static_text_message().message,
            )?,
            fetch_sender_task: ContactsLookupSubtask::new(vec![], CacheLookupPolicy::Allow),
        };

        let result = State::poll_fetch_sender(&mut context.context, state);
        assert_matches!(result, Err(CspE2eProtocolError::InternalError(cause)) => {
            assert_eq!(cause.to_string(), "Sender identity missing in contact lookup result");
        });

        Ok(())
    }

    #[test]
    fn fetch_sender_invalid_sender_is_user() -> anyhow::Result<()> {
        let mut context = Context::from(ContextInitBuilder::default().build()?);
        let mut message =
            IncomingMessageWithMetadataBox::try_from(StaticTestSender::static_text_message().message)?;
        message.sender_identity = context.database.user_identity;
        let state = FetchSenderState {
            payload: message,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![context.database.user_identity],
                CacheLookupPolicy::Allow,
            ),
        };

        // Note: We only get an immediate result from the ContactsLookupSubtask because the contact is the
        // user itself and will therefore not trigger a remote lookup.
        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::SenderIsUser);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default()
        .contact_lookup_cache(vec![StaticTestSender::cached_invalid()])
    )]
    #[case(ContextInitBuilder::default()
        .contacts(vec![{
            let mut contact = StaticTestSender::contact();
            contact.activity_state = protobuf_contact::ActivityState::Invalid;
            contact
        }])
    )]
    fn fetch_sender_invalid_sender(#[case] context_builder: ContextInitBuilder) -> anyhow::Result<()> {
        let mut context = Context::from(context_builder.build()?);
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(
                StaticTestSender::static_text_message().message,
            )?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::SenderIsInvalid);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default()
        .contact_lookup_cache(vec![StaticTestSender::cached()])
    )]
    #[case(ContextInitBuilder::default().contacts(vec![StaticTestSender::contact()]))]
    fn fetch_sender_invalid_undecryptable_outer_metdata(
        #[case] context_builder: ContextInitBuilder,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(context_builder.build()?);

        // Fill the encrypted metadata with garbage
        let mut message = StaticTestSender::create_text_message(&context.context.csp_e2e, "meow".to_owned());
        message.metadata.fill(0xff);

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MetadataDecryptionFailed);
            }
        );

        Ok(())
    }

    #[test]
    fn fetch_sender_invalid_undecryptable_message_container() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![StaticTestSender::contact()])
                .build()?,
        );

        // Fill the encrypted message container with garbage
        let mut message = StaticTestSender::create_text_message(&context.context.csp_e2e, "meow".to_owned());
        message.message_container.fill(0xff);

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageDecryptionFailed);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(Some(vec![0xff; 32]), None, DiscardReason::MetadataDecodingFailed)]
    #[case(None, Some(vec![]), DiscardReason::MessageMissingPkcs7PaddingForMessage)]
    #[case(None, Some(vec![0x00]), DiscardReason::MessageInvalidPkcs7Padding)]
    #[case(None, Some(vec![0x03, 0x03]), DiscardReason::MessageInvalidPkcs7Padding)]
    #[case(None, Some(vec![0x02, 0x02]), DiscardReason::MessageMissingOuterType)]
    #[case(None, Some(vec![0xff, 0x01]), DiscardReason::MessageDisallowedOuterType)]
    #[case(None, Some(vec![0xfd, 0x01]), DiscardReason::MessageUnknownOuterType)]
    #[case(None, Some(vec![0xa0, 0x01]), DiscardReason::MessageForwardSecurityUnsupported)]
    #[case(None, Some(vec![0x01, 0xc0, 0x01]), DiscardReason::MessageDecodingFailed)]
    fn fetch_sender_invalid_message(
        #[case] metadata: Option<Vec<u8>>,
        #[case] message_container: Option<Vec<u8>>,
        #[case] expected_discard_reason: DiscardReason,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![StaticTestSender::contact()])
                .build()?,
        );

        // Construct an invalid message
        let message = {
            let receiver_identity = context.database.user_identity;

            // Create outgoing message
            let message = OutgoingMessage {
                id: MessageId::random(),
                overrides: MessageOverrides::default(),
                created_at: utc_now_ms(),
                body: OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                    receiver_identity,
                    body: ContactMessageBody::Text(TextMessage {
                        text: "meow".to_owned(),
                    }),
                }),
            };

            // Derive shared secret
            let shared_secret = context
                .context
                .csp_e2e
                .client_key
                .derive_csp_e2e_key(&StaticTestSender::public_key());

            // Create random nonce
            let nonce = Nonce::random();

            // Encode and encrypt metadata (pre-defined or fallback to valid metadata)
            let metadata = {
                let mut metadata = metadata.unwrap_or_else(|| {
                    encode_metadata(Delta::Unchanged, &message, &message.effective_properties())
                });
                encrypt_metadata_in_place(&shared_secret, &nonce, &mut metadata);
                metadata
            };

            // Encode and encrypt a message container (pre-defined or fallback to a valid 1:1 text message)
            let message_container = {
                let mut message_container = message_container.unwrap_or_else(|| {
                    let mut writer = OwnedVecByteWriter::new_empty();
                    message.body.encode_into(&mut writer).unwrap();
                    writer.into_inner()
                });
                encrypt_message_container_in_place(&shared_secret, &nonce, &mut message_container);
                message_container
            };

            // Wrap outgoing message for transposing into an incoming message
            OutgoingMessageWithMetadataBox {
                sender_identity: StaticTestSender::IDENTITY,
                receiver_identity,
                id: message.id,
                legacy_created_at: 0,
                flags: MessageFlags::default(),
                legacy_sender_nickname: None,
                metadata,
                nonce,
                message_container,
            }
        };

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, expected_discard_reason);
            }
        );

        Ok(())
    }

    #[test]
    fn fetch_sender_diverging_message_ids() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![StaticTestSender::contact()])
                .build()?,
        );

        // Change the outer message ID, so that it diverges from the inner one (of the encrypted metadata)
        let mut message = StaticTestSender::create_text_message(&context.context.csp_e2e, "meow".to_owned());
        message.id = MessageId::random();

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageIdsDiverging);
            }
        );

        Ok(())
    }

    #[test]
    fn fetch_sender_reused_message_id() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![StaticTestSender::contact()])
                .build()?,
        );

        // Create the message and then mark the message as used
        let message = StaticTestSender::create_text_message(&context.context.csp_e2e, "meow".to_owned());
        context
            .context
            .conversations
            .borrow_mut()
            .add_or_update_incoming_message(IncomingMessage {
                sender_identity: StaticTestSender::IDENTITY,
                id: message.id,
                overrides: MessageOverrides::default(),
                created_at: utc_now_ms(),
                body: IncomingMessageBody::Contact(ContactMessageBody::Text(TextMessage {
                    text: "re-use, h3h3".to_owned(),
                })),
            })?;

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageIdReuse);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default()
        .contact_lookup_cache(vec![StaticTestSender::cached()])
    )]
    #[case(ContextInitBuilder::default().contacts(vec![StaticTestSender::contact()]))]
    fn fetch_sender_blocked_unknown_sender(
        #[case] context_builder: ContextInitBuilder,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            context_builder
                .settings(InMemoryDbSettings {
                    block_unknown_identities: true,
                })
                .build()?,
        );

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(
                StaticTestSender::static_text_message().message,
            )?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageSenderIsBlocked);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default()
        .contact_lookup_cache(vec![StaticTestSender::cached()])
    )]
    #[case(ContextInitBuilder::default().contacts(vec![StaticTestSender::contact()]))]
    #[case(ContextInitBuilder::default()
        .contacts(vec![{
            let mut contact = StaticTestSender::contact();
            contact.acquaintance_level = protobuf_contact::AcquaintanceLevel::Direct;
            contact
        }])
    )]
    fn fetch_sender_blocked_explicit_sender(
        #[case] context_builder: ContextInitBuilder,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            context_builder
                .blocked_identities(vec![StaticTestSender::IDENTITY])
                .build()?,
        );

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(
                StaticTestSender::static_text_message().message,
            )?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageSenderIsBlocked);
            }
        );

        Ok(())
    }

    #[test]
    fn fetch_sender_unexpected_message_by_3mapush() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .predefined_contacts(vec![ThreemaPushTestSender::predefined()])
                .build()?,
        );

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(
                ThreemaPushTestSender::create_text_message(&context.context.csp_e2e, "meow".to_owned()),
            )?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![ThreemaPushTestSender::identity()],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageBy3maPushUnexpected);
            }
        );

        Ok(())
    }

    #[rstest]
    #[case(ContextInitBuilder::default())]
    #[case(ContextInitBuilder::default().settings(InMemoryDbSettings {
        block_unknown_identities: true,
    }))]
    #[case(ContextInitBuilder::default().blocked_identities(vec![ThreemaPushTestSender::identity()]))]
    fn fetch_sender_valid_message_by_3mapush(
        #[case] context_builder: ContextInitBuilder,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            context_builder
                .predefined_contacts(vec![ThreemaPushTestSender::predefined()])
                .build()?,
        );

        let web_session_resume = WebSessionResumeMessage {
            push_payload: vec![1, 2, 3, 4],
        };
        let message = ThreemaPushTestSender::create_message_from_body(
            &context.context.csp_e2e,
            OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                receiver_identity: context.database.user_identity,
                body: ContactMessageBody::WebSessionResume(web_session_resume.clone()),
            }),
        );
        let message_id = message.id;
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![ThreemaPushTestSender::identity()],
                CacheLookupPolicy::Allow,
            ),
        };

        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(
            result,
            IncomingMessageLoop::Done(result) => result
        );
        assert_matches!(
            result,
            IncomingMessageResult {
                outgoing_message_ack,
                outgoing_message_task
            } => {
                assert_eq!(
                    outgoing_message_ack,
                    Some(MessageAck {
                        sender_identity: ThreemaPushTestSender::identity(),
                        id: message_id,
                    })
                );
                assert_eq!(outgoing_message_task, None);
            }
        );
        assert_eq!(context.web_session_resume_buffer.take(), vec![web_session_resume]);
        assert!(context.database.contacts.borrow().is_empty());
        assert!(context.database.conversations.borrow().is_empty());

        Ok(())
    }

    #[test]
    fn fetch_sender_unexpected_message_by_special_contact() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .predefined_contacts(vec![ThreemaSpecialTestSender::predefined()])
                .build()?,
        );

        let message = ThreemaSpecialTestSender::create_text_message(
            &context.context.csp_e2e,
            "You're SPECIAL".to_owned(),
        );
        let message_id = message.id;
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![ThreemaSpecialTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(
            result,
            IncomingMessageLoop::Done(result) => result
        );
        assert_matches!(
            result,
            IncomingMessageResult {
                outgoing_message_ack,
                outgoing_message_task
            } => {
                assert_eq!(
                    outgoing_message_ack,
                    Some(MessageAck {
                        sender_identity: ThreemaSpecialTestSender::IDENTITY,
                        id: message_id,
                    })
                );
                assert_eq!(outgoing_message_task, Some(()));
            }
        );
        assert!(context.database.contacts.borrow().is_empty());
        assert!(context.database.conversations.borrow().is_empty());

        Ok(())
    }

    #[test]
    fn fetch_sender_message_not_requring_implicit_contact_creation() -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contact_lookup_cache(vec![StaticTestSender::cached()])
                .build()?,
        );

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(
                StaticTestSender::create_message_from_body(
                    &context.context.csp_e2e,
                    OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                        receiver_identity: context.database.user_identity,
                        body: ContactMessageBody::DeliveryReceipt(DeliveryReceiptMessage {
                            receipt_type: DeliveryReceiptType::Received,
                            message_ids: vec![MessageId::random()],
                        }),
                    }),
                ),
            )?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        assert_matches!(
            State::process_fetch_sender_state(&mut context.context, state)?,
            ProcessingOutcome::Discard { reason, .. } => {
                assert_eq!(reason, DiscardReason::MessageMayNotImplicitlyAddContact);
            }
        );

        Ok(())
    }

    // TODO(LIB-16): Add static messages for all other messages
    #[rstest]
    #[case(StaticTestSender::static_text_message())]
    #[case(StaticTestSender::static_location_message())]
    fn handle_valid_static_message(#[case] message: StaticMessage) -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contact_lookup_cache(vec![StaticTestSender::cached()])
                .build()?,
        );
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(message.message)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        // Ensure the processing is done and we get a `message-ack` and a delivery receipt
        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(
            result,
            IncomingMessageLoop::Done(result) => result
        );
        assert_matches!(
            result,
            IncomingMessageResult {
                outgoing_message_ack,
                outgoing_message_task
            } => {
                assert_eq!(
                    outgoing_message_ack,
                    Some(MessageAck {
                        sender_identity: StaticTestSender::IDENTITY,
                        id: message.expected_id,
                    })
                );
                assert_eq!(outgoing_message_task, Some(()));
            }
        );

        // Ensure the contact exists and was updated to the correct acquaintance level and nickname
        let contacts = context.database.contacts.borrow();
        let (sender_contact, _) = contacts.get(&StaticTestSender::IDENTITY).unwrap();
        assert_eq!(
            sender_contact.acquaintance_level,
            protobuf_contact::AcquaintanceLevel::Direct
        );
        assert_eq!(
            sender_contact.nickname,
            StaticTestSender::nickname().into_non_empty()
        );

        // Ensure the message exists and equals the one that was sent
        let conversations = context.database.conversations.borrow();
        let incoming_message = conversations
            .get(&ConversationId::Contact(StaticTestSender::IDENTITY))
            .unwrap()
            .get(&(StaticTestSender::IDENTITY, message.expected_id))
            .unwrap();
        assert_eq!(incoming_message.sender_identity, StaticTestSender::IDENTITY);
        assert_eq!(incoming_message.id, message.expected_id);
        assert_eq!(incoming_message.overrides, MessageOverrides::default());
        assert_eq!(incoming_message.created_at, message.expected_created_at);
        assert_eq!(
            incoming_message.effective_properties(),
            message.expected_properties
        );
        assert_eq!(incoming_message.body, message.expected_body);

        Ok(())
    }

    // Naming of rstest here is way too verbose here. Wait for https://github.com/la10736/rstest/issues/320 so
    // we can rename tests.
    //
    // TODO(LIB-53): Add variants with the sender having acquaintance level _group_ and the group existing.
    #[rstest]
    fn handle_valid_message<TSender: TestSender>(
        // Contacts, all of which should accept regular messages and be added to the contact list with the
        // respective acquaintance level and nickname if necessary.
        #[values(
            // Normal sender, not existing as contact.
            (
                StaticTestSender,
                ContextInitBuilder::default()
                    .contact_lookup_cache(vec![StaticTestSender::cached()])
            ),

            // Normal sender, existing as contact but with acquaintance level _group/deleted_.
            (
                StaticTestSender,
                ContextInitBuilder::default().contacts(vec![StaticTestSender::contact()])
            ),

            // Normal sender, existing as contact with acquaintance level _direct_ (and a different nickname)
            // and block unknown active.
            (
                StaticTestSender,
                ContextInitBuilder::default()
                    .settings(InMemoryDbSettings {
                        block_unknown_identities: true,
                    })
                    .contacts(vec![{
                        let mut contact = StaticTestSender::contact();
                        contact.acquaintance_level = protobuf_contact::AcquaintanceLevel::Direct;
                        contact.nickname = Some("lappen3000".to_owned());
                        contact
                    }])
            ),

            // Predefined sender, not existing as contact, with block unknown active.
            (
                ThreemaSupportTestSender,
                ContextInitBuilder::default()
                    .predefined_contacts(vec![ThreemaSupportTestSender::predefined()])
                    .settings(InMemoryDbSettings {
                        block_unknown_identities: true,
                    })
                    .contact_lookup_cache(vec![ThreemaSupportTestSender::cached()])
            ),

            // Predefined sender, existing as contact but with acquaintance level _group/deleted_ (and a
            // different nickname).
            (
                ThreemaSupportTestSender,
                ContextInitBuilder::default()
                    .predefined_contacts(vec![ThreemaSupportTestSender::predefined()])
                    .contacts(vec![{
                        let mut contact = ThreemaSupportTestSender::contact();
                        contact.acquaintance_level = protobuf_contact::AcquaintanceLevel::GroupOrDeleted;
                        contact.nickname = Some("Peter Lustig".to_owned());
                        contact
                    }])
            ),
        )]
        (_, context_builder): (TSender, ContextInitBuilder),

        // All contact messages that implicitly create a contact / bump the acquaintance level of the sender
        // to _direct_.
        #[values(
            ContactMessageBody::Text(TextMessage {
                text: "meow".to_owned(),
            }),
            ContactMessageBody::Location(LocationMessage {
                latitude: 47.201441260878504,
                longitude: 8.78350732499435,
                accuracy_m: Some(5_f64),
                name: Some("Thermalbad".to_owned()),
                address: Some("Churerstrasse 82\n8808 Pfäffikon".to_owned()),
             }),
        )]
        outgoing_message_body: ContactMessageBody,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(context_builder.build()?);

        let outgoing_message = OutgoingMessage {
            id: MessageId::random(),
            overrides: MessageOverrides::default(),
            created_at: utc_now_ms(),
            body: OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                receiver_identity: context.database.user_identity,
                body: outgoing_message_body.clone(),
            }),
        };
        let message_id = outgoing_message.id;
        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(
                TSender::create_message(&context.context.csp_e2e, &outgoing_message),
            )?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![TSender::identity()],
                CacheLookupPolicy::Allow,
            ),
        };

        // Ensure the processing is done and we get a `message-ack` and a delivery receipt
        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(
            result,
            IncomingMessageLoop::Done(result) => result
        );
        assert_matches!(
            result,
            IncomingMessageResult {
                outgoing_message_ack,
                outgoing_message_task
            } => {
                assert_eq!(
                    outgoing_message_ack,
                    Some(MessageAck {
                        sender_identity: TSender::identity(),
                        id: message_id,
                    })
                );
                assert_eq!(outgoing_message_task, Some(()));
            }
        );

        // Ensure the contact exists and was updated to the correct acquaintance level and nickname
        let contacts = context.database.contacts.borrow();
        let (sender_contact, _) = contacts.get(&TSender::identity()).unwrap();
        assert_eq!(
            sender_contact.acquaintance_level,
            protobuf_contact::AcquaintanceLevel::Direct
        );
        assert_eq!(sender_contact.nickname, TSender::nickname().into_non_empty());

        // Ensure the message exists and equals the one that was sent
        let conversations = context.database.conversations.borrow();
        let incoming_message = conversations
            .get(&ConversationId::Contact(TSender::identity()))
            .unwrap()
            .get(&(TSender::identity(), message_id))
            .unwrap();
        assert_eq!(incoming_message.sender_identity, TSender::identity());
        assert_eq!(incoming_message.id, message_id);
        assert_eq!(incoming_message.overrides, outgoing_message.overrides);
        assert_eq!(incoming_message.created_at, outgoing_message.created_at);
        assert_matches!(&incoming_message.body, IncomingMessageBody::Contact(incoming_message_body) => {
            assert_eq!(*incoming_message_body, outgoing_message_body);
        });

        Ok(())
    }

    #[rstest]
    fn handle_valid_message_legacy_metadata_only(
        // The previous nickname should be overridden, regardless of whether it was present or not.
        #[values(None, Some("vorher"))] previous_nickname: Option<&str>,

        // The legacy nickname should be applied correctly (if the message is eligible for user profile
        // distribution).
        #[values(None, Some(""), Some("hinterher"))] new_legacy_nickname: Option<&str>,

        // Try it with messages that are both eligible and not eligible for user profile distributions.
        #[values(
            (
                ContactMessageBody::Text(TextMessage {
                    text: "greetings".to_owned(),
                }),
                true,
            ),
            (
                ContactMessageBody::DeliveryReceipt(DeliveryReceiptMessage {
                    receipt_type: DeliveryReceiptType::Read,
                    message_ids: vec![],
                }),
                false,
            ),
        )]
        (outgoing_message_body, user_profile_distribution): (ContactMessageBody, bool),
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![{
                    let mut contact = StaticTestSender::contact();
                    contact.acquaintance_level = protobuf_contact::AcquaintanceLevel::Direct;
                    contact.nickname = previous_nickname.map(ToOwned::to_owned);
                    contact
                }])
                .build()?,
        );

        // Construct a message where only the legacy metadata is present by stripping the new-style metadata
        // and enforcing the legacy metadata
        let message_id = MessageId::random();
        let mut message = encode_and_encrypt_message(
            StaticTestSender::IDENTITY,
            (None, Delta::Unchanged),
            context.database.user_identity,
            context
                .context
                .csp_e2e
                .client_key
                .derive_csp_e2e_key(&StaticTestSender::public_key()),
            &OutgoingMessage {
                id: message_id,
                overrides: MessageOverrides::default(),
                created_at: utc_now_ms(),
                body: OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                    receiver_identity: context.database.user_identity,
                    body: outgoing_message_body.clone(),
                }),
            },
            Nonce::random(),
        );
        message.metadata = vec![];
        message.legacy_created_at = 1337;
        message.legacy_sender_nickname = new_legacy_nickname.map(ToOwned::to_owned);

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        assert_matches!(result, IncomingMessageLoop::Done(_));

        // Ensure the contact exists and was updated to the correct nickname
        let contacts = context.database.contacts.borrow();
        let (sender_contact, _) = contacts.get(&StaticTestSender::IDENTITY).unwrap();
        assert_eq!(
            sender_contact.nickname.as_deref(),
            match (user_profile_distribution, new_legacy_nickname) {
                (true, Some("")) => None,
                (true, _) => new_legacy_nickname,
                (false, _) => previous_nickname,
            }
        );

        // Ensure the message exists has the correct (legacy) metadata
        let conversations = context.database.conversations.borrow();
        let incoming_message = conversations
            .get(&ConversationId::Contact(StaticTestSender::IDENTITY))
            .unwrap()
            .get(&(StaticTestSender::IDENTITY, message_id))
            .unwrap();
        assert_eq!(incoming_message.id, message_id);
        assert_eq!(incoming_message.created_at, 1337000);
        assert_matches!(&incoming_message.body, IncomingMessageBody::Contact(incoming_message_body) => {
            assert_eq!(*incoming_message_body, outgoing_message_body);
        });

        Ok(())
    }

    #[rstest]
    fn handle_valid_message_metadata(
        // The previous nickname should be overridden, regardless of whether it was present or not.
        #[values(None, Some("vorher"))] previous_nickname: Option<&str>,

        // The legacy nickname should have no impact.
        #[values(None, Some("ignorier-mich"))] new_legacy_nickname: Option<&str>,

        // The metadata nickname delta update should be applied correctly.
        #[values(Delta::Unchanged, Delta::Remove, Delta::Update("hinterher"))] new_nickname: Delta<&str>,

        // The metadata nickname should be applied regardless of whether the message is eligible for user
        // profile distribution or not.
        #[values(
            ContactMessageBody::Text(TextMessage {
                text: "greetings".to_owned(),
            }),
            ContactMessageBody::DeliveryReceipt(DeliveryReceiptMessage {
                receipt_type: DeliveryReceiptType::Read,
                message_ids: vec![],
            })
        )]
        outgoing_message_body: ContactMessageBody,
    ) -> anyhow::Result<()> {
        let mut context = Context::from(
            ContextInitBuilder::default()
                .contacts(vec![{
                    let mut contact = StaticTestSender::contact();
                    contact.acquaintance_level = protobuf_contact::AcquaintanceLevel::Direct;
                    contact.nickname = previous_nickname.map(ToOwned::to_owned);
                    contact
                }])
                .build()?,
        );

        // Construct a message where the legacy metadata diverges from the new-style metadata
        let message_id = MessageId::random();
        let message = {
            let shared_secret = context
                .context
                .csp_e2e
                .client_key
                .derive_csp_e2e_key(&StaticTestSender::public_key());
            let nonce = Nonce::random();

            // Encode and encrypt metadata
            let metadata = {
                let mut metadata: Vec<u8> = MessageMetadata {
                    message_id,
                    created_at: 1234,
                    nickname: new_nickname.clone().map(ToOwned::to_owned),
                }
                .into();
                encrypt_metadata_in_place(&shared_secret, &nonce, &mut metadata);
                metadata
            };

            // Encode and encrypt message container
            let message_container = {
                let mut writer = OwnedVecByteWriter::new_empty();
                OutgoingMessageBody::Contact(OutgoingContactMessageBody {
                    receiver_identity: context.database.user_identity,
                    body: outgoing_message_body.clone(),
                })
                .encode_into(&mut writer)
                .unwrap();
                let mut message_container = writer.into_inner();
                encrypt_message_container_in_place(&shared_secret, &nonce, &mut message_container);
                message_container
            };

            // Wrap outgoing message for transposing into an incoming message
            OutgoingMessageWithMetadataBox {
                sender_identity: StaticTestSender::IDENTITY,
                receiver_identity: context.database.user_identity,
                id: message_id,
                legacy_created_at: 1337,
                flags: MessageFlags::default(),
                legacy_sender_nickname: new_legacy_nickname.map(ToOwned::to_owned),
                metadata,
                nonce,
                message_container,
            }
        };

        let state = FetchSenderState {
            payload: IncomingMessageWithMetadataBox::try_from(MessageWithMetadataBox::try_from(message)?)?,
            fetch_sender_task: ContactsLookupSubtask::new(
                vec![StaticTestSender::IDENTITY],
                CacheLookupPolicy::Allow,
            ),
        };

        let (state, result) = State::poll_fetch_sender(&mut context.context, state)?;
        assert_matches!(state, State::Done);
        assert_matches!(result, IncomingMessageLoop::Done(_));

        // Ensure the contact exists and was updated to the correct nickname
        let contacts = context.database.contacts.borrow();
        let (sender_contact, _) = contacts.get(&StaticTestSender::IDENTITY).unwrap();
        assert_eq!(
            sender_contact.nickname.as_deref(),
            match new_nickname {
                Delta::Unchanged => previous_nickname,
                Delta::Update(updated_nickname) => Some(updated_nickname),
                Delta::Remove => None,
            }
        );

        // Ensure the message exists has the correct new-style metadata
        let conversations = context.database.conversations.borrow();
        let incoming_message = conversations
            .get(&ConversationId::Contact(StaticTestSender::IDENTITY))
            .unwrap()
            .get(&(StaticTestSender::IDENTITY, message_id))
            .unwrap();
        assert_eq!(incoming_message.id, message_id);
        assert_eq!(incoming_message.created_at, 1234);
        assert_matches!(&incoming_message.body, IncomingMessageBody::Contact(incoming_message_body) => {
            assert_eq!(*incoming_message_body, outgoing_message_body);
        });

        Ok(())
    }
}
