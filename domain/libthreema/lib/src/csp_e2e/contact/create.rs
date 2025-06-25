//! Task for creating contacts.
use core::mem;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, error, info};

use super::ContactInit;
use crate::{
    common::{Nonce, ThreemaId},
    csp_e2e::{
        CspE2eProtocolContext, CspE2eProtocolError, ReflectId, TaskLoop,
        provider::ContactProvider,
        reflect::{ReflectFlags, ReflectPayload},
        transaction::{
            begin::{
                BeginTransactionInstruction, BeginTransactionResponse, BeginTransactionResult,
                BeginTransactionSubtask, PreconditionVerdict,
            },
            commit::{CommitTransactionInstruction, CommitTransactionSubtask},
        },
    },
    protobuf,
    utils::time::utc_now_ms,
};

/// Instruction for creating contacts. See each variant's steps.
pub enum CreateContactsTaskInstruction {
    /// Run [`BeginTransactionInstruction`] with the following result mapping:
    ///
    /// - For [`BeginTransactionInstruction::BeginTransaction`], provide the result by calling
    ///   [`CreateContactsTask::begin_transaction_response`].
    /// - For [`BeginTransactionInstruction::AbortTransaction`], provide the result by calling
    ///   [`CreateContactsTask::abort_transaction_response`].
    BeginTransaction(BeginTransactionInstruction),

    /// Run [`CommitTransactionInstruction`] and provide the result by calling
    /// [`CreateContactsTask::reflect_and_commit_transaction_ack`].
    ReflectAndCommitTransaction(CommitTransactionInstruction),
}

/// Result of creating contacts.
pub struct CreateContactsTaskResult {
    /// A list of identities that have been created.
    pub added: Vec<ThreemaId>,
}

/// Result of polling a [`CreateContactsTask`].
pub type CreateContactsTaskLoop = TaskLoop<CreateContactsTaskInstruction, CreateContactsTaskResult>;

struct InitState {
    contacts: Vec<ContactInit>,
}

struct BeginTransactionState {
    contacts: Vec<ContactInit>,
    transaction_task: BeginTransactionSubtask,
}

struct ReflectAndCommitTransactionState {
    contacts: Vec<ContactInit>,
    transaction_task: CommitTransactionSubtask,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(CspE2eProtocolError),
    Init(InitState),
    BeginTransaction(BeginTransactionState),
    ReflectAndCommitTransaction(ReflectAndCommitTransactionState),
    Done,
}
impl State {
    fn poll_init(
        context: &mut CspE2eProtocolContext,
        state: InitState,
    ) -> Result<(Self, CreateContactsTaskLoop), CspE2eProtocolError> {
        let identities: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();

        // Add tracing span
        //
        // TODO(LIB-16): This should be applied to the whole task somehow
        let _span = tracing::info_span! { "contacts", contacts = ?identities }.entered();

        // Non-MD: Add contacts and done
        if context.d2x.is_none() {
            let added = Self::add_contacts(context.contacts.as_ref(), state.contacts);
            return Ok((
                Self::Done,
                CreateContactsTaskLoop::Done(CreateContactsTaskResult { added }),
            ));
        }

        // MD: We need to create a transaction.

        // Precondition: At least one of the contacts has not yet been added.
        let contact_provider = context.contacts.clone();
        let precondition = Box::new(move || {
            Ok(if contact_provider.has_many(&identities) < identities.len() {
                PreconditionVerdict::Continue
            } else {
                PreconditionVerdict::Abort
            })
        });

        // Begin the transaction in the next state
        Self::poll_begin_transaction(
            context,
            BeginTransactionState {
                contacts: state.contacts,
                transaction_task: BeginTransactionSubtask::new(
                    precondition,
                    protobuf::d2d::transaction_scope::Scope::ContactSync,
                    None,
                ),
            },
        )
    }

    fn poll_begin_transaction(
        context: &mut CspE2eProtocolContext,
        mut state: BeginTransactionState,
    ) -> Result<(Self, CreateContactsTaskLoop), CspE2eProtocolError> {
        // Ensure MD is enabled
        let Some(d2x_context) = context.d2x.as_mut() else {
            let message = "D2X context missing";
            error!(message);
            return Err(CspE2eProtocolError::InternalError(message.to_owned()));
        };

        // Poll until the transaction is in progress
        match state.transaction_task.poll(d2x_context)? {
            TaskLoop::Instruction(instruction) => {
                return Ok((
                    Self::BeginTransaction(state),
                    CreateContactsTaskLoop::Instruction(CreateContactsTaskInstruction::BeginTransaction(
                        instruction,
                    )),
                ));
            },
            TaskLoop::Done(result) => match result {
                BeginTransactionResult::TransactionInProgress => {},
                BeginTransactionResult::TransactionAborted => {
                    info!("Contacts already added");
                    return Ok((
                        Self::Done,
                        CreateContactsTaskLoop::Done(CreateContactsTaskResult { added: vec![] }),
                    ));
                },
            },
        }

        // Contacts may have been added in between states, so we need to remove those
        state
            .contacts
            .retain(|contact| !context.contacts.has(contact.identity));

        // Encode and encrypt reflection messages containing the contacts to be added
        let (reflect_messages, nonces) = state
            .contacts
            .iter_mut()
            .map(|contact| {
                // Bump _created-at_
                contact.created_at = utc_now_ms();

                // Encode and encrypt the reflect message
                ReflectPayload::encode_and_encrypt(
                    d2x_context,
                    ReflectFlags::default(),
                    protobuf::d2d::envelope::Content::ContactSync(protobuf::d2d::ContactSync {
                        action: Some(protobuf::d2d::contact_sync::Action::Create(
                            protobuf::d2d::contact_sync::Create {
                                contact: Some(protobuf::d2d_sync::Contact::from(&*contact)),
                            },
                        )),
                    }),
                )
            })
            .collect::<Result<(Vec<ReflectPayload>, Vec<Nonce>), CspE2eProtocolError>>()?;

        // Reflect and commit the transaction
        d2x_context.nonce_storage.add_many(nonces);
        let (transaction_task, commit_instruction) = CommitTransactionSubtask::new(reflect_messages);
        Ok((
            Self::ReflectAndCommitTransaction(ReflectAndCommitTransactionState {
                contacts: state.contacts,
                transaction_task,
            }),
            CreateContactsTaskLoop::Instruction(CreateContactsTaskInstruction::ReflectAndCommitTransaction(
                commit_instruction,
            )),
        ))
    }

    fn poll_reflect_and_commit_transaction(
        context: &CspE2eProtocolContext,
        state: ReflectAndCommitTransactionState,
    ) -> Result<(Self, CreateContactsTaskLoop), CspE2eProtocolError> {
        // Fulfill the transaction
        state.transaction_task.poll()?;

        // Add contacts
        let expected: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();
        let added = Self::add_contacts(context.contacts.as_ref(), state.contacts);
        if expected.len() != added.len() {
            let message = "One or more contact were added unexpectedly during a transaction";
            error!(?expected, ?added, message);
            return Err(CspE2eProtocolError::DesyncError(message));
        }

        // Done
        Ok((
            Self::Done,
            CreateContactsTaskLoop::Done(CreateContactsTaskResult { added }),
        ))
    }

    fn add_contacts(contact_provider: &dyn ContactProvider, contacts: Vec<ContactInit>) -> Vec<ThreemaId> {
        // Add all missing contacts
        let (identities, contacts): (Vec<ThreemaId>, Vec<ContactInit>) = contacts
            .into_iter()
            .filter_map(|contact| {
                if contact_provider.has(contact.identity) {
                    info!(identity = ?contact.identity, "Skipped adding contact, already exists");
                    None
                } else {
                    Some((contact.identity, contact))
                }
            })
            .unzip();
        debug!(contacts = ?identities, "Adding contacts");
        contact_provider.add(&contacts);
        info!(contacts = ?identities, "Added contacts");

        // TODO(SE-510): Schedule fetching gateway-defined profile picture here, if contact was
        // added and if necessary.

        // Added
        identities
    }
}

/// Task for creating contacts.
#[derive(Name)]
pub struct CreateContactsTask {
    state: State,
}
impl CreateContactsTask {
    /// Create a new task for creating contacts.
    ///
    /// Note: The contacts need to have already been looked up at this point.
    #[must_use]
    pub fn new(contacts: Vec<ContactInit>) -> Self {
        Self {
            state: State::Init(InitState { contacts }),
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
    ) -> Result<CreateContactsTaskLoop, CspE2eProtocolError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                CreateContactsTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => State::poll_init(context, state),
            State::BeginTransaction(state) => State::poll_begin_transaction(context, state),
            State::ReflectAndCommitTransaction(state) => {
                State::poll_reflect_and_commit_transaction(context, state)
            },
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

    /// Possible result after handling a [`CreateContactsTaskInstruction::BeginTransaction`]
    /// instruction.
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    pub fn begin_transaction_response(
        &mut self,
        response: BeginTransactionResponse,
    ) -> Result<(), CspE2eProtocolError> {
        let State::BeginTransaction(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::BEGIN_TRANSACTION
            )));
        };
        state.transaction_task.begin_transaction_response(response)
    }

    /// Possible result after handling a [`CreateContactsTaskInstruction::BeginTransaction`]
    /// instruction.
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    pub fn abort_transaction_response(
        &mut self,
        response: protobuf::d2m::CommitTransactionAck,
    ) -> Result<(), CspE2eProtocolError> {
        let State::BeginTransaction(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::BEGIN_TRANSACTION
            )));
        };
        state.transaction_task.abort_transaction_response(response)
    }

    /// Possible result after handling a
    /// [`CreateContactsTaskInstruction::ReflectAndCommitTransaction`] instruction.
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    pub fn reflect_and_commit_transaction_ack(
        &mut self,
        acknowledged_reflect_ids: Vec<ReflectId>,
        response: protobuf::d2m::CommitTransactionAck,
    ) -> Result<(), CspE2eProtocolError> {
        let State::ReflectAndCommitTransaction(state) = &mut self.state else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::REFLECT_AND_COMMIT_TRANSACTION
            )));
        };
        state
            .transaction_task
            .reflect_and_commit_transaction_ack(acknowledged_reflect_ids, response);
        Ok(())
    }
}
