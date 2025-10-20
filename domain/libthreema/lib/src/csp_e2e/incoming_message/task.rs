//! Task for processing an incoming message.
//!
//! # Task Terminology
//!
//! A **task** is a state machine (usually an `enum`) that abstracts an asynchronous process,
//! usually I/O between client and server. It contains all possibles states until the task has been
//! completed.
//!
//! A task has an internal **state+* which is driven to completion by calling `poll` repeatedly and
//! handling the resulting **instruction**s by making the required asynchronous actions and awaiting
//! a specific result.
//!
//! A **subtask** is a re-usable internal task that should not be reachable via a consuming API.
use core::mem;
use std::str;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use prost::Message as _;
use tracing::{debug, error, info, trace, warn};

use super::payload::DecodedMessageWithMetadataBox;
use crate::{
    common::{
        ConversationId, Delta, MessageFlags, MessageId, MessageMetadata, Nonce, ThreemaId, task::TaskLoop,
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
        reflect::{ReflectFlags, ReflectInstruction, ReflectPayload, ReflectResponse, ReflectSubtask},
    },
    model::{
        contact::{CommunicationPermission, ContactOrInit, ContactUpdate, PredefinedContact},
        message::{IncomingMessage, MessageBody, MessageOverrides, MessageProperties},
        provider::ProviderError,
    },
    protobuf::{self, common::CspE2eMessageType},
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
pub struct IncomingMessageResult {
    /// An optional message ID to acknowledge to (and therefore remove from) the server.
    pub outgoing_message_ack: Option<MessageAck>,
    /// TODO(LIB-16): An optional outgoing message task to be scheduled.
    pub outgoing_message_task: Option<()>,
}

/// Result of polling a [`IncomingMessageTask`].
pub type IncomingMessageLoop = TaskLoop<IncomingMessageInstruction, IncomingMessageResult>;

struct AcknowledgeMessage {
    sender_identity: ThreemaId,
    id: MessageId,
    flags: MessageFlags,
    nonce: Option<Nonce>,
}
impl From<&DecodedMessageWithMetadataBox> for AcknowledgeMessage {
    fn from(payload: &DecodedMessageWithMetadataBox) -> Self {
        Self {
            sender_identity: payload.sender_identity,
            id: payload.id,
            flags: payload.flags,
            nonce: Some(payload.nonce.clone()),
        }
    }
}

struct OuterIncomingMessage {
    message_type: CspE2eMessageType,
    flags: MessageFlags,
    unpadded_message_data: Vec<u8>,
    nonce: Nonce,
}

struct InitState {
    payload: MessageWithMetadataBox,
}

struct FetchSenderState {
    payload: DecodedMessageWithMetadataBox,
    fetch_sender_task: ContactsLookupSubtask,
}

struct CreateContactState {
    outer_message: OuterIncomingMessage,
    inner_message: IncomingMessage,
    create_contact_task: CreateContactsTask,
}

struct UpdateContactState {
    outer_message: OuterIncomingMessage,
    inner_message: IncomingMessage,
    update_contact_task: UpdateContactsTask,
}
struct ReflectMessageState {
    sender_identity: ThreemaId,
    message_id: MessageId,
    message_flags: MessageFlags,
    message_nonce: Nonce,
    message_properties: MessageProperties,
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
        let length = state.payload.bytes.len();

        // Decode and validate `message-with-metadata-box` (to some degree)
        let payload = {
            let MessageWithMetadataBox {
                sender_identity,
                id: message_id,
                flags: message_flags,
                ..
            } = state.payload;
            match DecodedMessageWithMetadataBox::try_from(state.payload) {
                Ok(payload) => payload,
                Err(error) => {
                    warn!(
                        ?length,
                        ?error,
                        "Discarding message whose payload could not be decoded"
                    );
                    return Self::acknowledge_and_discard(
                        context,
                        AcknowledgeMessage {
                            sender_identity,
                            id: message_id,
                            flags: message_flags,
                            nonce: None,
                        },
                    );
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

        // Check if the nonce has been used before
        if context.csp_e2e.nonce_storage.borrow().has(&payload.nonce)? {
            warn!("Discarding message due to nonce reuse");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&payload));
        }

        // Check that the user is the receiver
        if payload.receiver_identity != context.csp_e2e.user_identity {
            warn!("Discarding message not destined to the user");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&payload));
        }

        // Lookup the sender in the next state
        let fetch_sender_task =
            ContactsLookupSubtask::new(vec![payload.sender_identity], CacheLookupPolicy::Allow);
        Self::poll_fetch_sender(
            context,
            FetchSenderState {
                payload,
                fetch_sender_task,
            },
        )
    }

    fn poll_fetch_sender(
        context: &mut CspE2eProtocolContext,
        mut state: FetchSenderState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        let payload = &mut state.payload;

        // Poll until we have looked up the sender
        let sender = match state.fetch_sender_task.poll(context)? {
            ContactsLookupLoop::Instruction(instruction) => {
                return Ok((
                    Self::FetchSender(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::FetchSender(instruction)),
                ));
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

        // Extract the sender from the task result
        let Some(sender) = sender else {
            let message = "Sender identity missing in contact lookup result";
            error!(message);
            return Err(CspE2eProtocolError::InternalError(message.to_owned()));
        };

        // Ensure that the sender is not the user or invalid/revoked
        let sender = match sender {
            ContactResult::User => {
                warn!("Discarding message where the sender is the user");
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            },
            ContactResult::Invalid(identity) => {
                warn!(?identity, "Discarding message from an invalid/revoked identity");
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            },
            ContactResult::ExistingContact(sender) => ContactOrInit::ExistingContact(sender),
            ContactResult::NewContact(sender) => ContactOrInit::NewContact(sender),
        };

        // Derive the shared secret
        let shared_secret = context
            .csp_e2e
            .client_key
            .derive_csp_e2e_key(&sender.inner().public_key);

        // Decrypt and decode metadata (if any)
        let outer_metadata = match payload.metadata.as_ref() {
            Some(metadata) => {
                let metadata_bytes = &mut payload
                    .bytes
                    .get_mut(metadata.data.clone())
                    .expect("calculated metadata data length must be in bounds");

                // Decrypt
                let mmk = shared_secret.message_metadata_cipher();
                if mmk
                    .0
                    .decrypt_in_place_detached(
                        &payload.nonce.0.into(),
                        b"",
                        metadata_bytes,
                        &metadata.tag.into(),
                    )
                    .is_err()
                {
                    warn!("Discarding message whose metadata could not be decrypted");
                    return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                }

                // Decode
                #[expect(clippy::useless_asref, reason = "False positive")]
                match protobuf::csp_e2e::MessageMetadata::decode(metadata_bytes.as_ref()) {
                    Ok(metadata) => Some(MessageMetadata::from(metadata)),
                    Err(error) => {
                        warn!(?error, "Discarding message whose metadata could not be decoded");
                        return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                    },
                }
            },
            None => None,
        };

        // Decrypt message container and decode it to message type and message data
        let (outer_type, outer_message_data) = {
            let message_bytes = payload
                .bytes
                .get_mut(payload.message_container.data.clone())
                .expect("calculated metadata data length must be in bounds");

            // Decrypt
            if shared_secret
                .message_cipher()
                .0
                .decrypt_in_place_detached(
                    &payload.nonce.0.into(),
                    b"",
                    message_bytes,
                    &payload.message_container.tag.into(),
                )
                .is_err()
            {
                warn!("Discarding message whose data could not be decrypted");
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            }

            // Remove PKCS#7 padding
            let message_bytes = {
                let Some(padding_length) = message_bytes.last() else {
                    warn!("Discarding message without any data");
                    return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                };
                let Some(unpadded_length) = message_bytes.len().checked_sub(*padding_length as usize) else {
                    warn!("Discarding message with invalid PKCS#7 padding");
                    return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                };
                message_bytes
                    .get(..unpadded_length)
                    .expect("calculated PKCS#7 padding length must be in bounds")
            };

            // Decode message type
            let Some(outer_type) = message_bytes.first() else {
                warn!("Discarding message without an outer type");
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            };

            // The remaining data is the message data
            let message_data = message_bytes
                .get(1..)
                .expect("message data after a successfully decoded message outer type must be in bounds");

            // Done decoding
            (*outer_type, message_data)
        };

        // Drop shared secret ASAP
        drop(shared_secret);

        // Legacy: Ensure it's not of type `0xff`.
        //
        // Note: This prevents payload confusion due to the old broken vouch mechanism. Since
        // messages can be re-sent (as long as the associated nonce has been removed), we will have
        // to keep this indefinitely.
        if outer_type == 0xff {
            warn!("Discarding message with disallowed outer type 0xff");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        }

        // Ensure it's a known type
        let Ok(outer_type) = CspE2eMessageType::try_from(i32::from(outer_type)) else {
            warn!(?outer_type, "Discarding message with unknown outer type");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        };

        // TODO(LIB-42): Handle FS, decode inner metadata
        if outer_type == CspE2eMessageType::ForwardSecurityEnvelope {
            error!("TODO(LIB-42): Handle FS envelope");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        }
        let inner_metadata = outer_metadata;
        let inner_type = outer_type;
        let inner_message_data = outer_message_data;

        // Decode the incoming message
        //
        // Note: At this point the message is only decoded and validated to some degree. The receive
        // steps have not been run, yet.
        let outer_message = OuterIncomingMessage {
            message_type: outer_type,
            flags: payload.flags,
            unpadded_message_data: outer_message_data.to_vec(),
            nonce: payload.nonce.clone(),
        };
        let inner_message = IncomingMessage {
            sender_identity: sender.inner().identity,
            id: payload.id,
            overrides: MessageOverrides {
                disable_delivery_receipts: payload.flags.0 & MessageFlags::NO_DELIVERY_RECEIPTS != 0,
            },
            created_at: inner_metadata.as_ref().map_or(
                u64::from(payload.legacy_created_at).saturating_mul(1000),
                |metadata| metadata.created_at,
            ),
            body: match MessageBody::decode(inner_type, inner_message_data) {
                Ok(message) => message,
                Err(error) => {
                    warn!(
                        ?inner_type,
                        ?error,
                        "Discarding message that could not be decoded",
                    );
                    return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                },
            },
        };
        let inner_message_properties = inner_message.properties();

        // Check the message ID for divergences or re-use
        if let Some(metadata) = inner_metadata.as_ref() {
            // Ensure the message IDs match
            if metadata.message_id != payload.id {
                warn!(
                    ?inner_type,
                    metadata_message_id = ?metadata.message_id,
                    "Discarding message with diverging message IDs",
                );
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            }
        }
        if context
            .messages
            .borrow()
            .is_marked_used(sender.inner().identity, payload.id)?
        {
            warn!(?inner_type, "Discarding message with reused message ID");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        }

        // TODO(LIB-42): If inner-type is not defined (i.e. handling an FS control message), log a
        // notice, Acknowledge and discard the message and abort these steps.

        // Disallow usage of FS encapsulation within an FS encapsulated message
        if inner_type == CspE2eMessageType::ForwardSecurityEnvelope {
            warn!("Discarding message with FS encapsulated within FS");
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        }

        // Check if the message is exempted from blocking and otherwise check if the contact is
        // blocked
        if !inner_message.is_exempt_from_blocking() {
            let communication_permission = sender.inner().communication_permission(
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
                    return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
                },
            }
        }

        // Apply special contact handling (right now, this is only `*3MAPUSH`)
        if sender.inner().identity == PredefinedContact::_3MAPUSH {
            // Only use case right now is the `web-session-resume` which we just forward
            if inner_message_properties.message_type != CspE2eMessageType::WebSessionResume {
                warn!(
                    ?inner_type,
                    "Discarding message from *3MAPUSH with unexpected inner type"
                );
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            }
            let MessageBody::WebSessionResume(web_session_resume) = inner_message.body else {
                warn!(
                    decoded_type = inner_message.body.variant_name(),
                    "Discarding message from *3MAPUSH with unexpected decoded type"
                );
                return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
            };
            context.shortcut.handle_web_session_resume(web_session_resume)?;

            // Nothing more to do
            return Ok((
                Self::Done,
                IncomingMessageLoop::Done(IncomingMessageResult {
                    outgoing_message_ack: Some(MessageAck {
                        identity: inner_message.sender_identity,
                        message_id: inner_message.id,
                    }),
                    outgoing_message_task: None,
                }),
            ));
        }

        // Check if we need to add a contact
        if !inner_message_properties.contact_creation && matches!(sender, ContactOrInit::NewContact(_)) {
            info!(
                ?inner_type,
                "Discarding message that does not require contact creation without an existing contact"
            );
            return Self::acknowledge_and_discard(context, AcknowledgeMessage::from(&*payload));
        }

        // Extract the sender's (potentially) updated nickname, if available
        //
        // Note: The nicknames were already trimmed at this point.
        let nickname = if let Some(metadata) = inner_metadata.as_ref() {
            metadata.nickname.clone()
        } else if inner_message_properties.user_profile_distribution {
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
                    update.nickname = nickname.changes(sender.nickname.as_ref());
                    update
                };

                // Apply the update or start handling the message
                if update.has_changes() {
                    // Update the contact in the next state
                    Self::poll_update_contact(
                        context,
                        UpdateContactState {
                            outer_message,
                            inner_message,
                            update_contact_task: UpdateContactsTask::new(vec![update]),
                        },
                    )
                } else {
                    // Handle the message in the next state
                    Self::handle_message(context, outer_message, &inner_message)
                }
            },

            // Create new contact
            ContactOrInit::NewContact(mut sender) => {
                // Raise the acquaintance level to _direct_ and update the nickname accordingly
                sender.acquaintance_level = protobuf::d2d_sync::contact::AcquaintanceLevel::Direct;
                nickname.apply_to(&mut sender.nickname);

                // Create the contact in the next state
                Self::poll_create_contact(
                    context,
                    CreateContactState {
                        outer_message,
                        inner_message,
                        create_contact_task: CreateContactsTask::new(vec![sender]),
                    },
                )
            },
        }
    }

    fn poll_update_contact(
        context: &mut CspE2eProtocolContext,
        mut state: UpdateContactState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        // Poll until we have updated the contact
        match state.update_contact_task.poll(context)? {
            UpdateContactsLoop::Instruction(instruction) => {
                return Ok((
                    Self::UpdateContact(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::UpdateContact(instruction)),
                ));
            },
            UpdateContactsLoop::Done(()) => {},
        }

        // Handle the message in the next state
        Self::handle_message(context, state.outer_message, &state.inner_message)
    }

    fn poll_create_contact(
        context: &mut CspE2eProtocolContext,
        mut state: CreateContactState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        // Poll until we have added the contact
        match state.create_contact_task.poll(context)? {
            CreateContactsLoop::Instruction(instruction) => {
                return Ok((
                    Self::CreateContact(state),
                    IncomingMessageLoop::Instruction(IncomingMessageInstruction::CreateContact(instruction)),
                ));
            },
            CreateContactsLoop::Done(_) => {},
        }

        // Note: Technically we should check if we need to update the contact's nickname here but
        // it's extremely unlikely that we lost a race to create the same contact and the next
        // message from the sender would fix it anyways.

        // Handle the message in the next state
        Self::handle_message(context, state.outer_message, &state.inner_message)
    }

    fn handle_message(
        context: &mut CspE2eProtocolContext,
        outer_message: OuterIncomingMessage,
        inner_message: &IncomingMessage,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        let message_properties = inner_message.properties();
        let inner_type = message_properties.message_type;

        // Run the receive steps associated to the message
        match &inner_message.body {
            MessageBody::Text(_) => {
                info!(?inner_type, "Adding message to 1:1 conversation");
                context.messages.borrow_mut().add(
                    ConversationId::Contact(inner_message.sender_identity),
                    inner_message.clone(),
                )?;
            },
            MessageBody::WebSessionResume(_) => {
                // Only allowed from `*3MAPUSH`
                warn!(?inner_type, "Discarding message only allowed from *3MAPUSH");
                return Ok((
                    Self::Done,
                    IncomingMessageLoop::Done(IncomingMessageResult {
                        outgoing_message_ack: Some(MessageAck {
                            identity: inner_message.sender_identity,
                            message_id: inner_message.id,
                        }),
                        outgoing_message_task: None,
                    }),
                ));
            },
        }

        // (MD) Reflect and await acknowledgement, if necessary
        if let Some(d2x_context) = context.d2x.as_mut()
            && message_properties.reflect_incoming
        {
            let (reflect_message, nonce) = ReflectPayload::encode_and_encrypt(
                d2x_context,
                ReflectFlags::from(&message_properties),
                protobuf::d2d::envelope::Content::IncomingMessage(protobuf::d2d::IncomingMessage {
                    sender_identity: inner_message.sender_identity.as_str().to_owned(),
                    message_id: inner_message.id.0,
                    created_at: inner_message.created_at,
                    r#type: outer_message.message_type.into(),
                    body: outer_message.unpadded_message_data,
                    nonce: outer_message.nonce.0.to_vec(),
                }),
            )?;
            d2x_context.nonce_storage.borrow_mut().add_many(vec![nonce])?;
            let (reflect_message_task, reflect_instruction) = ReflectSubtask::new(vec![reflect_message]);
            return Ok((
                Self::ReflectMessage(ReflectMessageState {
                    sender_identity: inner_message.sender_identity,
                    message_id: inner_message.id,
                    message_flags: outer_message.flags,
                    message_nonce: outer_message.nonce.clone(),
                    message_properties,
                    reflect_message_task,
                }),
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::ReflectMessage(
                    reflect_instruction,
                )),
            ));
        }

        // Acknowledge the message in the next state
        Self::acknowledge_message_and_schedule_delivery_receipt(
            context,
            inner_message.sender_identity,
            inner_message.id,
            outer_message.flags,
            outer_message.nonce,
            &message_properties,
        )
    }

    fn poll_reflect_message(
        context: &mut CspE2eProtocolContext,
        state: ReflectMessageState,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        // Fulfill the reflection
        state.reflect_message_task.poll()?;

        // Acknowledge the message in the next state
        Self::acknowledge_message_and_schedule_delivery_receipt(
            context,
            state.sender_identity,
            state.message_id,
            state.message_flags,
            state.message_nonce,
            &state.message_properties,
        )
    }

    fn acknowledge_message_and_schedule_delivery_receipt(
        context: &mut CspE2eProtocolContext,
        sender_identity: ThreemaId,
        message_id: MessageId,
        message_flags: MessageFlags,
        message_nonce: Nonce,
        message_properties: &MessageProperties,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        let outgoing_message_ack = Self::acknowledge(
            &mut context.csp_e2e,
            AcknowledgeMessage {
                sender_identity,
                id: message_id,
                flags: message_flags,
                // Only mark the nonce as used if we need replay protection for the message
                nonce: if message_properties.replay_protection {
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
                outgoing_message_task: if message_properties.delivery_receipts {
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
        acknowledge_message: AcknowledgeMessage,
    ) -> Result<(Self, IncomingMessageLoop), CspE2eProtocolError> {
        Ok((
            Self::Done,
            IncomingMessageLoop::Done(IncomingMessageResult {
                outgoing_message_ack: Self::acknowledge(&mut context.csp_e2e, acknowledge_message)?,
                outgoing_message_task: None,
            }),
        ))
    }

    #[expect(clippy::needless_pass_by_value, reason = "Prevent re-use")]
    fn acknowledge(
        context: &mut CspE2eContext,
        acknowledge_message: AcknowledgeMessage,
    ) -> Result<Option<MessageAck>, ProviderError> {
        // Mark the nonce as used if the message requires protection against replay attacks
        if let Some(message_nonce) = acknowledge_message.nonce.as_ref() {
            context
                .nonce_storage
                .borrow_mut()
                .add_many(vec![message_nonce.clone()])?;
        }

        // TODO(LIB-42): Run `fs-commit-fn` if applicable. Ensure this does not get executed if called prior
        // to decapsulation.

        // Acknowledge only if needed
        Ok(
            if acknowledge_message.flags.0 & MessageFlags::NO_SERVER_ACKNOWLEDGEMENT == 0 {
                Some(MessageAck {
                    identity: acknowledge_message.sender_identity,
                    message_id: acknowledge_message.id,
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

    use crate::{
        common::{
            ClientInfo, D2xDeviceId, MessageFlags, MessageId, Nonce, ThreemaId,
            config::{Config, Flavor, WorkContext, WorkCredentials, WorkFlavor},
            keys::{ClientKey, DeviceGroupKey, RawClientKey},
        },
        csp::payload::{MessageAck, MessageWithMetadataBox},
        csp_e2e::{
            CspE2eContextInit, CspE2eProtocolContext, CspE2eProtocolContextInit, CspE2eProtocolError,
            D2xContextInit,
            incoming_message::task::{IncomingMessageInstruction, IncomingMessageLoop, InitState, State},
        },
        model::provider::in_memory::{
            DefaultShortcutProvider, InMemoryDb, InMemoryDbInit, InMemoryDbSettings,
        },
        utils::bytes::OwnedVecByteReader,
    };

    #[derive(Builder)]
    #[builder(pattern = "owned")]
    struct Context {
        #[builder(default = "\"0HPT9EWD\"")]
        identity: &'static str,

        #[builder(default = "\"5d31a1950a807edcd195ec16a7e980e525c8bafe315d3b9bd9b22b186b4096bd\"")]
        client_key: &'static str,

        #[builder(default = "Flavor::Consumer")]
        flavor: Flavor,

        #[builder(default = "None")]
        device_group_key: Option<DeviceGroupKey>,
    }
    impl ContextBuilder {
        fn with_multi_device(mut self) -> Self {
            self.device_group_key = Some(Some(DeviceGroupKey::from([0xab_u8; 32])));
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
    impl From<Context> for CspE2eProtocolContext {
        fn from(context: Context) -> Self {
            let user_identity = ThreemaId::try_from(context.identity).expect("Invalid Threema ID");
            let mut database = InMemoryDb::from(InMemoryDbInit {
                user_identity,
                settings: InMemoryDbSettings {
                    block_unknown_identities: false,
                },
                contacts: vec![],
                blocked_identities: vec![],
            });
            CspE2eProtocolContext::from(CspE2eProtocolContextInit {
                client_info: ClientInfo::Libthreema,
                config: Rc::new(Config::testing()),
                csp_e2e: CspE2eContextInit {
                    user_identity,
                    client_key: ClientKey::from(
                        &RawClientKey::from_hex(context.client_key).expect("Invalid client key"),
                    ),
                    flavor: context.flavor,
                    nonce_storage: Box::new(RefCell::new(database.csp_e2e_nonce_provider())),
                },
                d2x: context.device_group_key.map(|device_group_key| D2xContextInit {
                    device_id: D2xDeviceId(0x01),
                    device_group_key,
                    nonce_storage: Box::new(RefCell::new(database.d2d_nonce_provider())),
                }),
                shortcut: Box::new(DefaultShortcutProvider),
                settings: Box::new(RefCell::new(database.settings_provider())),
                contacts: Box::new(RefCell::new(database.contact_provider())),
                messages: Box::new(RefCell::new(database.message_provider())),
            })
        }
    }

    fn message() -> anyhow::Result<MessageWithMetadataBox> {
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
        Ok(MessageWithMetadataBox::decode(OwnedVecByteReader::new(message))?)
    }

    #[test]
    fn bad_message() -> anyhow::Result<()> {
        let mut context = CspE2eProtocolContext::from(ContextBuilder::default().build()?);

        // Set up a bogus message
        let sender_identity = ThreemaId::try_from("HAHAHAHA")?;
        let message_id = MessageId(0x00);
        let state = InitState {
            payload: MessageWithMetadataBox {
                sender_identity,
                id: message_id,
                flags: MessageFlags(0x00),
                bytes: vec![],
            },
        };

        let (state, instruction) = State::poll_init(&mut context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(instruction, IncomingMessageLoop::Done(result) => result);
        let message_ack = assert_matches!(result.outgoing_message_ack, Some(message_ack) => message_ack);
        assert_eq!(message_ack.identity, sender_identity);
        assert_eq!(message_ack.message_id, message_id);
        assert_eq!(result.outgoing_message_task, None);

        Ok(())
    }

    #[test]
    fn multi_device_not_leading() -> anyhow::Result<()> {
        let mut context = CspE2eProtocolContext::from(ContextBuilder::default().with_multi_device().build()?);
        let state = InitState { payload: message()? };

        let result = State::poll_init(&mut context, state);
        assert_matches!(result, Err(CspE2eProtocolError::InvalidState(_)));

        Ok(())
    }

    #[test]
    fn nonce_reuse() -> anyhow::Result<()> {
        let mut context = CspE2eProtocolContext::from(ContextBuilder::default().build()?);
        let state = InitState { payload: message()? };

        // Mark the nonce as used
        context
            .csp_e2e
            .nonce_storage
            .borrow_mut()
            .add_many(vec![Nonce::from_hex(
                "b2d718b0db179e3e3bcdbf3c2be997490a0349f2e4fbaa43",
            )?])?;

        let (state, instruction) = State::poll_init(&mut context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(instruction, IncomingMessageLoop::Done(result) => result);
        assert_matches!(result.outgoing_message_ack, Some(MessageAck { .. }));
        assert_eq!(result.outgoing_message_task, None);

        Ok(())
    }

    #[test]
    fn invalid_receiver() -> anyhow::Result<()> {
        // Use a different receiver (loud cat)
        let mut context =
            CspE2eProtocolContext::from(ContextBuilder::default().identity("MEOWMEOW").build()?);
        let state = InitState { payload: message()? };

        let (state, instruction) = State::poll_init(&mut context, state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(instruction, IncomingMessageLoop::Done(result) => result);
        assert_matches!(result.outgoing_message_ack, Some(MessageAck { .. }));
        assert_eq!(result.outgoing_message_task, None);

        Ok(())
    }

    #[test]
    fn fetch_sender_consumer() -> anyhow::Result<()> {
        let mut context = CspE2eProtocolContext::from(ContextBuilder::default().build()?);
        let state = InitState { payload: message()? };

        let (state, instruction) = State::poll_init(&mut context, state)?;
        assert_matches!(state, State::FetchSender(_));
        let instruction = assert_matches!(
            instruction,
            IncomingMessageLoop::Instruction(
                IncomingMessageInstruction::FetchSender(instruction)) => instruction
        );
        assert_matches!(instruction.work_directory_request, None);

        Ok(())
    }

    #[test]
    fn fetch_sender_work_onprem() -> anyhow::Result<()> {
        let contexts = [
            CspE2eProtocolContext::from(ContextBuilder::default().with_work_flavor().build()?),
            CspE2eProtocolContext::from(ContextBuilder::default().with_onprem_flavor().build()?),
        ];
        for mut context in contexts {
            let state = InitState { payload: message()? };

            let (state, instruction) = State::poll_init(&mut context, state)?;
            assert_matches!(state, State::FetchSender(_));
            let instruction = assert_matches!(
                instruction,
                IncomingMessageLoop::Instruction(
                    IncomingMessageInstruction::FetchSender(instruction)) => instruction
            );
            assert_matches!(instruction.work_directory_request, Some(_));
        }

        Ok(())
    }

    // TODO(LIB-16): Continue here
    // #[test]
    // fn fetch_sender_result_missing() -> anyhow::Result<()> {
    //     let mut context = CspE2eProtocolContext::from(ContextBuilder::default().build()?);
    //     let state = InitState { payload: message()? };

    //     Ok(())
    // }
}
