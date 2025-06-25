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
use tracing::{debug, error, info, warn};

use super::payload::DecodedMessageWithMetadataBox;
use crate::{
    common::{Conversation, Delta, MessageId, MessageMetadata, Nonce},
    crypto::aead::AeadInPlace as _,
    csp::payload::MessageWithMetadataBox,
    csp_e2e::{
        CspE2eProtocolContext, CspE2eProtocolError, D2mRole, ReflectId, TaskLoop,
        contact::{
            ContactOrInit, ContactPermission, ContactUpdate,
            create::{CreateContactsTask, CreateContactsTaskInstruction},
            lookup::{
                CacheLookupPolicy, ContactResult, ContactsLookupSubtask, RequestIdentitiesInstruction,
                RequestIdentitiesResult,
            },
            predefined::PredefinedContact,
            update::{UpdateContactsTask, UpdateContactsTaskInstruction},
        },
        message::{IncomingMessage, MessageBody, MessageProperties},
        reflect::{ReflectFlags, ReflectInstruction, ReflectPayload, ReflectSubtask},
    },
    protobuf::{self, common::CspE2eMessageType},
};

/// Instruction for processing an incoming message.
pub enum IncomingMessageTaskInstruction {
    /// TODO(LIB-16)
    FetchSender(RequestIdentitiesInstruction),
    /// TODO(LIB-16)
    CreateContact(CreateContactsTaskInstruction),
    /// TODO(LIB-16)
    UpdateContact(UpdateContactsTaskInstruction),
    /// TODO(LIB-16)
    ReflectMessage(ReflectInstruction),
    /// TODO(LIB-16)
    AcknowledgeMessage(MessageId),
}

/// Result of processing an incoming message.
///
/// 1. If `outgoing_message_ack` is present, send a `message-ack` payload to the chat server with the enclosed
///    message ID.
pub struct IncomingMessageTaskResult {
    /// An optional message ID to acknowledge to (and therefore remove from) the server.
    pub outgoing_message_ack: Option<MessageId>,
}

/// Result of polling a [`IncomingMessageTask`].
pub type IncomingMessageTaskLoop = TaskLoop<IncomingMessageTaskInstruction, IncomingMessageTaskResult>;

struct OuterIncomingMessage {
    message_type: CspE2eMessageType,
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

struct HandleMessageState {
    outer_message: OuterIncomingMessage,
    inner_message: IncomingMessage,
}

struct ReflectMessageState {
    message_id: MessageId,
    message_properties: MessageProperties,
    reflect_message_task: ReflectSubtask,
}

struct SendDeliveryReceiptState {
    #[expect(dead_code, reason = "Will use later")]
    message_id: MessageId,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(CspE2eProtocolError),
    Init(InitState),
    FetchSender(FetchSenderState),
    CreateContact(CreateContactState),
    UpdateContact(UpdateContactState),
    ReflectMessage(ReflectMessageState),
    SendDeliveryReceipt(SendDeliveryReceiptState),
    Done,
}
impl State {
    fn poll_init(
        context: &mut CspE2eProtocolContext,
        state: InitState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        let length = state.payload.message_bytes.len();

        // Decode and validate `message-with-metadata-box` (to some degree)
        let payload = {
            let message_id = state.payload.message_id;
            match DecodedMessageWithMetadataBox::try_from(state.payload) {
                Ok(payload) => payload,
                Err(error) => {
                    warn!(
                        ?length,
                        ?error,
                        "Discarding message whose payload could not be decoded"
                    );
                    return Ok(Self::acknowledge_message(message_id));
                },
            }
        };

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
        if context.csp_e2e.nonce_storage.has(&payload.nonce) {
            warn!("Discarding message due to nonce reuse");
            return Ok(Self::acknowledge_message(payload.id));
        }

        // Check that the user is the receiver
        if payload.receiver_identity != context.csp_e2e.user_identity {
            warn!("Discarding message not destined to the user");
            return Ok(Self::acknowledge_message(payload.id));
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
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        let payload = &mut state.payload;

        // Poll until we have looked up the sender
        let sender = match state.fetch_sender_task.poll(context)? {
            TaskLoop::Instruction(instruction) => {
                return Ok((
                    Self::FetchSender(state),
                    IncomingMessageTaskLoop::Instruction(IncomingMessageTaskInstruction::FetchSender(
                        instruction,
                    )),
                ));
            },
            TaskLoop::Done(mut contacts) => contacts.remove(&payload.sender_identity),
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
                return Ok(Self::acknowledge_message(payload.id));
            },
            ContactResult::Invalid(identity) => {
                warn!(?identity, "Discarding message from an invalid/revoked identity");
                return Ok(Self::acknowledge_message(payload.id));
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
                    return Ok(Self::acknowledge_message(payload.id));
                }

                // Decode
                #[expect(clippy::useless_asref, reason = "False positive")]
                match protobuf::csp_e2e::MessageMetadata::decode(metadata_bytes.as_ref()) {
                    Ok(metadata) => Some(MessageMetadata::from(metadata)),
                    Err(error) => {
                        warn!(?error, "Discarding message whose metadata could not be decoded");
                        return Ok(Self::acknowledge_message(payload.id));
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
                return Ok(Self::acknowledge_message(payload.id));
            }

            // Remove PKCS#7 padding
            let message_bytes = {
                let Some(padding_length) = message_bytes.last() else {
                    warn!("Discarding message without any data");
                    return Ok(Self::acknowledge_message(payload.id));
                };
                let Some(unpadded_length) = message_bytes.len().checked_sub(*padding_length as usize) else {
                    warn!("Discarding message with invalid PKCS#7 padding");
                    return Ok(Self::acknowledge_message(payload.id));
                };
                message_bytes
                    .get(..unpadded_length)
                    .expect("calculated PKCS#7 padding length must be in bounds")
            };

            // Decode message type
            let Some(outer_type) = message_bytes.first() else {
                warn!("Discarding message without an outer type");
                return Ok(Self::acknowledge_message(payload.id));
            };

            // The remaining data is the message data
            let message_data = message_bytes
                .get(1..)
                .expect("message data after a successfully decoded message outer type must be in bounds");

            // Done decoding
            (*outer_type, message_data)
        };

        // Legacy: Ensure it's not of type `0xff`.
        //
        // Note: This prevents payload confusion due to the old broken vouch mechanism. Since
        // messages can be re-sent (as long as the associated nonce has been removed), we will have
        // to keep this indefinitely.
        if outer_type == 0xff {
            warn!("Discarding message with disallowed outer type 0xff");
            return Ok(Self::acknowledge_message(payload.id));
        }

        // Ensure it's a known type
        let Ok(outer_type) = CspE2eMessageType::try_from(i32::from(outer_type)) else {
            warn!(?outer_type, "Discarding message with unknown outer type");
            return Ok(Self::acknowledge_message(payload.id));
        };

        // TODO(LIB-42): Handle FS, decode inner metadata
        if outer_type == CspE2eMessageType::ForwardSecurityEnvelope {
            error!("TODO(LIB-42): Handle FS envelope");
            return Ok(Self::acknowledge_message(payload.id));
        }
        let inner_metadata = outer_metadata;
        let inner_type = outer_type;
        let inner_message_data = outer_message_data;

        // Decode the incoming message
        //
        // Note: At this point the message is only decoded and validated to some degree. The receive
        // steps have not been run, yet.
        let inner_message = match MessageBody::decode(inner_type, inner_message_data) {
            Ok(message) => message,
            Err(error) => {
                warn!(
                    ?inner_type,
                    ?error,
                    "Discarding message that could not be decoded",
                );
                return Ok(Self::acknowledge_message(payload.id));
            },
        };
        let message_properties = inner_message.properties();

        // Check the message ID for divergences or re-use
        if let Some(metadata) = inner_metadata.as_ref() {
            // Ensure the message IDs match
            if metadata.message_id != payload.id {
                warn!(
                    ?inner_type,
                    metadata_message_id = ?metadata.message_id,
                    "Discarding message with diverging message IDs",
                );
                return Ok(Self::acknowledge_message(payload.id));
            }
        }
        if context
            .messages
            .is_marked_used(sender.inner().identity, payload.id)
        {
            warn!(?inner_type, "Discarding message with reused message ID");
            return Ok(Self::acknowledge_message(payload.id));
        }

        // TODO(LIB-42): If inner-type is not defined (i.e. handling an FS control message), log a
        // notice, Acknowledge and discard the message and abort these steps.

        // Disallow usage of FS encapsulation within an FS encapsulated message
        if inner_type == CspE2eMessageType::ForwardSecurityEnvelope {
            warn!("Discarding message with FS encapsulated within FS");
            return Ok(Self::acknowledge_message(payload.id));
        }

        // Check if the message is exempted from blocking and otherwise check if the contact is
        // blocked
        if !message_properties.exempt_from_blocking {
            let contact_permission = sender.inner().contact_permission(context);
            match contact_permission {
                ContactPermission::Allow => {},
                ContactPermission::BlockExplicit | ContactPermission::BlockUnknown => {
                    info!(
                        ?inner_type,
                        ?contact_permission,
                        "Discarding message for blocked sender"
                    );
                    return Ok(Self::acknowledge_message(payload.id));
                },
            }
        }

        // Apply special contact handling (right now, this is only `*3MAPUSH`)
        if sender.inner().identity == PredefinedContact::_3MAPUSH {
            // Only use case right now is the `web-session-resume` which we just forward
            if message_properties.message_type != CspE2eMessageType::WebSessionResume {
                warn!(
                    ?inner_type,
                    "Discarding message from *3MAPUSH with unexpected inner type"
                );
                return Ok(Self::acknowledge_message(payload.id));
            }
            let MessageBody::WebSessionResume(web_session_resume) = inner_message else {
                warn!(
                    decoded_type = inner_message.variant_name(),
                    "Discarding message from *3MAPUSH with unexpected decoded type"
                );
                return Ok(Self::acknowledge_message(payload.id));
            };
            context
                .shortcut_provider
                .handle_web_session_resume(web_session_resume);

            // Nothing more to do
            return Ok((
                Self::Done,
                IncomingMessageTaskLoop::Done(IncomingMessageTaskResult {
                    outgoing_message_ack: Some(payload.id),
                }),
            ));
        }

        // Check if we need to add a contact
        if !message_properties.contact_creation && matches!(sender, ContactOrInit::NewContact(_)) {
            info!(
                ?inner_type,
                "Discarding message that does not require contact creation without an existing contact"
            );
            return Ok(Self::acknowledge_message(payload.id));
        }

        // Extract the sender's (potentially) updated nickname, if available
        //
        // Note: The nicknames were already trimmed at this point.
        let nickname = if let Some(metadata) = inner_metadata.as_ref() {
            metadata.nickname.clone()
        } else if message_properties.user_profile_distribution {
            match payload.legacy_sender_nickname.clone() {
                Some(nickname) => Delta::Update(nickname),
                None => Delta::Remove,
            }
        } else {
            Delta::Unchanged
        };

        // Update or create the contact, if necessary
        let outer_message = OuterIncomingMessage {
            nonce: payload.nonce.clone(),
            message_type: outer_type,
            unpadded_message_data: outer_message_data.to_vec(),
        };
        let inner_message = IncomingMessage {
            sender_identity: sender.inner().identity,
            id: payload.id,
            created_at: inner_metadata.map_or(
                u64::from(payload.legacy_created_at).saturating_mul(1000),
                |metadata| metadata.created_at,
            ),
            body: inner_message,
        };
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
                    Self::handle_message(
                        context,
                        HandleMessageState {
                            outer_message,
                            inner_message,
                        },
                    )
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

    fn poll_create_contact(
        context: &mut CspE2eProtocolContext,
        mut state: CreateContactState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        // Poll until we have added the contact
        match state.create_contact_task.poll(context)? {
            TaskLoop::Instruction(instruction) => {
                return Ok((
                    Self::CreateContact(state),
                    IncomingMessageTaskLoop::Instruction(IncomingMessageTaskInstruction::CreateContact(
                        instruction,
                    )),
                ));
            },
            TaskLoop::Done(_) => {},
        }

        // Note: Technically we should check if we need to update the contact's nickname here but
        // it's extremely unlikely that we lost a race to create the same contact and the next
        // message from the sender would fix it anyways.

        // Handle the message in the next state
        Self::handle_message(
            context,
            HandleMessageState {
                outer_message: state.outer_message,
                inner_message: state.inner_message,
            },
        )
    }

    fn poll_update_contact(
        context: &mut CspE2eProtocolContext,
        mut state: UpdateContactState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        // Poll until we have updated the contact
        match state.update_contact_task.poll(context)? {
            TaskLoop::Instruction(instruction) => {
                return Ok((
                    Self::UpdateContact(state),
                    IncomingMessageTaskLoop::Instruction(IncomingMessageTaskInstruction::UpdateContact(
                        instruction,
                    )),
                ));
            },
            TaskLoop::Done(()) => {},
        }

        // Handle the message in the next state
        Self::handle_message(
            context,
            HandleMessageState {
                outer_message: state.outer_message,
                inner_message: state.inner_message,
            },
        )
    }

    fn handle_message(
        context: &mut CspE2eProtocolContext,
        state: HandleMessageState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        let HandleMessageState {
            outer_message,
            inner_message,
        } = state;
        let message_properties = inner_message.properties();
        let inner_type = message_properties.message_type;

        // Run the receive steps associated to the message
        match &inner_message.body {
            MessageBody::Text(_) => {
                info!(?inner_type, "Adding message to 1:1 conversation");
                context.messages.add(
                    Conversation::Contact(inner_message.sender_identity),
                    inner_message.clone(),
                );
            },
            MessageBody::WebSessionResume(_) => {
                // Only allowed from `*3MAPUSH`
                warn!(?inner_type, "Discarding message only allowed from *3MAPUSH");
                return Ok((
                    Self::Done,
                    IncomingMessageTaskLoop::Done(IncomingMessageTaskResult {
                        outgoing_message_ack: Some(inner_message.id),
                    }),
                ));
            },
        }

        // (MD) Reflect and await acknowledgement, if necessary
        if let Some(d2x_context) = context.d2x.as_mut() {
            if message_properties.reflect_incoming {
                // 21. (MD) If the properties associated to inner-type require reflecting incoming messages,
                //     reflect a d2d.IncomingMessage from outer-type and outer-message and the associated
                //     conversation to other devices and wait for reflection acknowledgement.² If this fails,
                //     exceptionally abort these steps and the connection.³
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
                d2x_context.nonce_storage.add_many(vec![nonce]);
                let (reflect_message_task, reflect_instruction) = ReflectSubtask::new(vec![reflect_message]);
                return Ok((
                    Self::ReflectMessage(ReflectMessageState {
                        message_id: inner_message.id,
                        message_properties,
                        reflect_message_task,
                    }),
                    IncomingMessageTaskLoop::Instruction(IncomingMessageTaskInstruction::ReflectMessage(
                        reflect_instruction,
                    )),
                ));
            }
        }

        // Acknowledge the message in the next state
        Ok(Self::acknowledge_message_2(inner_message.id, &message_properties))
    }

    fn poll_reflect_message(
        state: ReflectMessageState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        // Fulfill the reflection
        state.reflect_message_task.poll()?;

        // Acknowledge the message in the next state
        Ok(Self::acknowledge_message_2(
            state.message_id,
            &state.message_properties,
        ))
    }

    fn acknowledge_message_2(
        message_id: MessageId,
        message_properties: &MessageProperties,
    ) -> (Self, IncomingMessageTaskLoop) {
        // Acknowledge the message.
        //
        // Because the message is already acknowledged towards the server here, the following steps
        // may not get executed at all if the connection drops or the execution fails. All following
        // steps must therefore be non-critical.
        //
        // Note: The two variants here are a bit awkward but they do make sense... right?!

        // TODO(LIB-16):
        // - Fix this, use actual _Acknowledge_ steps.
        // - Try re-using ::acknowledge here!
        if message_properties.delivery_receipts {
            (
                Self::SendDeliveryReceipt(SendDeliveryReceiptState { message_id }),
                IncomingMessageTaskLoop::Instruction(IncomingMessageTaskInstruction::AcknowledgeMessage(
                    message_id,
                )),
            )
        } else {
            (
                Self::Done,
                IncomingMessageTaskLoop::Done(IncomingMessageTaskResult {
                    outgoing_message_ack: Some(message_id),
                }),
            )
        }
    }

    #[expect(
        unused_variables,
        clippy::needless_pass_by_value,
        clippy::unnecessary_wraps,
        reason = "TODO(LIB-16)"
    )]
    fn poll_send_delivery_receipt(
        context: &mut CspE2eProtocolContext,
        state: SendDeliveryReceiptState,
    ) -> Result<(Self, IncomingMessageTaskLoop), CspE2eProtocolError> {
        // TODO(LIB-16): Wrap up here for now and add this later. We wanna get this to a stage where
        // it's test- and mergeable.

        // TODO(LIB-16): Implement
        //
        // 24. Run the Bundled Messages Send Steps with the following properties:
        //     - id being a random message ID,
        //     - created-at set to the current timestamp,
        //     - receivers set to contact,
        //     - to construct a delivery-receipt message with status received (0x01) and the respective
        //       message-id.

        // Done, phew!
        Ok((
            Self::Done,
            IncomingMessageTaskLoop::Done(IncomingMessageTaskResult {
                outgoing_message_ack: None,
            }),
        ))
    }

    fn acknowledge_message(message_id: MessageId) -> (Self, IncomingMessageTaskLoop) {
        // TODO(LIB-16): Actually implemented _Acknowledge_ steps here
        //
        // 1. If the steps for this message have already been invoked once, abort these steps.
        //
        // 2. If flags does not contain the no server acknowledgement (0x04) flag, send a message-ack payload
        //    to the chat server with the respective message-id.
        //
        // 3. If the properties associated to inner-type require protection against replay, mark the nonce of
        //    message-and-metadata-nonce as used.
        //
        // 4. If fs-commit-fn is defined, run it.

        (
            Self::Done,
            IncomingMessageTaskLoop::Done(IncomingMessageTaskResult {
                outgoing_message_ack: Some(message_id),
            }),
        )
    }
}

/// Task for processing an incoming message.
#[derive(Name)]
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
    pub fn poll(
        &mut self,
        context: &mut CspE2eProtocolContext,
    ) -> Result<IncomingMessageTaskLoop, CspE2eProtocolError> {
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
            State::ReflectMessage(state) => State::poll_reflect_message(state),
            State::SendDeliveryReceipt(state) => State::poll_send_delivery_receipt(context, state),
            State::Done => Err(CspE2eProtocolError::TaskAlreadyDone(Self::NAME)),
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

    /// TODO(LIB-16)
    ///
    /// # Errors
    ///
    /// TODO(LIB-16)
    pub fn fetch_sender_result(
        &mut self,
        result: RequestIdentitiesResult,
    ) -> Result<(), CspE2eProtocolError> {
        let State::FetchSender(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::FETCH_SENDER
            )));
        };
        state.fetch_sender_task.request_identities_result(result)
    }

    /// TODO(LIB-16)
    ///
    /// # Errors
    ///
    /// TODO(LIB-16)
    pub fn message_reflect_ack(
        &mut self,
        acknowledged_reflect_ids: Vec<ReflectId>,
    ) -> Result<(), CspE2eProtocolError> {
        let State::ReflectMessage(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::REFLECT_MESSAGE
            )));
        };
        state.reflect_message_task.reflect_ack(acknowledged_reflect_ids);
        Ok(())
    }
}
