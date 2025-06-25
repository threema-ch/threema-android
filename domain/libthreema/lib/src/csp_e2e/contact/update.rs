//! Task for updating contacts.
//!
//! TODO(LIB-16): This omits profile pictures. Is this a problem?
use core::mem;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, error, info};

use super::ContactUpdate;
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
};

/// Instruction for creating contacts. See each variant's steps.
pub enum UpdateContactsTaskInstruction {
    /// Run [`BeginTransactionInstruction`] with the following result mapping:
    ///
    /// - For [`BeginTransactionInstruction::BeginTransaction`], provide the result by calling
    ///   [`UpdateContactsTask::begin_transaction_response`].
    /// - For [`BeginTransactionInstruction::AbortTransaction`], provide the result by calling
    ///   [`UpdateContactsTask::abort_transaction_response`].
    BeginTransaction(BeginTransactionInstruction),

    /// Run [`CommitTransactionInstruction`] and provide the result by calling
    /// [`UpdateContactsTask::reflect_and_commit_transaction_ack`].
    ReflectAndCommitTransaction(CommitTransactionInstruction),
}

/// Result of polling a [`UpdateContactsTask`].
pub type UpdateContactsTaskLoop = TaskLoop<UpdateContactsTaskInstruction, ()>;

struct InitState {
    contacts: Vec<ContactUpdate>,
}

struct BeginTransactionState {
    contacts: Vec<ContactUpdate>,
    transaction_task: BeginTransactionSubtask,
}

struct ReflectAndCommitTransactionState {
    contacts: Vec<ContactUpdate>,
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
    ) -> Result<(Self, UpdateContactsTaskLoop), CspE2eProtocolError> {
        let identities: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();

        // Add tracing span
        //
        // TODO(LIB-16): This should be applied to the whole task somehow
        let _span = tracing::info_span! { "contacts", contacts = ?identities }.entered();

        // Non-MD: Update contacts and done
        if context.d2x.is_none() {
            Self::update_contacts(context.contacts.as_ref(), &state.contacts);
            return Ok((Self::Done, UpdateContactsTaskLoop::Done(())));
        }

        // MD: We need to create a transaction.

        // Precondition: All of the contacts must exist (hard error)
        let contact_provider = context.contacts.clone();
        let precondition = Box::new(move || {
            let n_existing = contact_provider.has_many(&identities);
            if n_existing == identities.len() {
                Ok(PreconditionVerdict::Continue)
            } else {
                let message = "An existing contact disappeared";
                error!(n_existing, n_expected = identities.len(), message);
                Err(CspE2eProtocolError::DesyncError(message))
            }
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
    ) -> Result<(Self, UpdateContactsTaskLoop), CspE2eProtocolError> {
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
                    UpdateContactsTaskLoop::Instruction(UpdateContactsTaskInstruction::BeginTransaction(
                        instruction,
                    )),
                ));
            },
            TaskLoop::Done(result) => match result {
                BeginTransactionResult::TransactionInProgress => {},
                BeginTransactionResult::TransactionAborted => {
                    // The precondition should never abort, so this should never happen.
                    let message = "Transaction aborted unexpectedly";
                    error!(message);
                    return Err(CspE2eProtocolError::InternalError(message.to_owned()));
                },
            },
        }

        // Encode and encrypt reflection messages containing the contacts to be updated
        let (reflect_messages, nonces) = state
            .contacts
            .iter_mut()
            .map(|contact| {
                ReflectPayload::encode_and_encrypt(
                    d2x_context,
                    ReflectFlags::default(),
                    protobuf::d2d::envelope::Content::ContactSync(protobuf::d2d::ContactSync {
                        action: Some(protobuf::d2d::contact_sync::Action::Update(
                            protobuf::d2d::contact_sync::Update {
                                contact: Some(protobuf::d2d_sync::Contact::from(&*contact)),
                            },
                        )),
                    }),
                )
            })
            .collect::<Result<(Vec<ReflectPayload>, Vec<Nonce>), CspE2eProtocolError>>()?;

        // Reflect and commit the transaction
        d2x_context.nonce_storage.add_many(nonces);
        let (transaction_state, commit_instruction) = CommitTransactionSubtask::new(reflect_messages);
        Ok((
            Self::ReflectAndCommitTransaction(ReflectAndCommitTransactionState {
                contacts: state.contacts,
                transaction_task: transaction_state,
            }),
            UpdateContactsTaskLoop::Instruction(UpdateContactsTaskInstruction::ReflectAndCommitTransaction(
                commit_instruction,
            )),
        ))
    }

    fn poll_reflect_and_commit_transaction(
        context: &CspE2eProtocolContext,
        state: ReflectAndCommitTransactionState,
    ) -> Result<(Self, UpdateContactsTaskLoop), CspE2eProtocolError> {
        // Fulfill the transaction
        state.transaction_task.poll()?;

        // Update contacts
        Self::update_contacts(context.contacts.as_ref(), &state.contacts);
        Ok((Self::Done, UpdateContactsTaskLoop::Done(())))
    }

    fn update_contacts(contact_provider: &dyn ContactProvider, contacts: &[ContactUpdate]) {
        // Update all contacts
        debug!("Updating contacts");
        contact_provider.update(contacts);
        info!("Updated contacts");
    }
}

/// Task for updating contacts.
#[derive(Name)]
pub struct UpdateContactsTask {
    state: State,
}
impl UpdateContactsTask {
    /// Create a new task for updating existing contacts.
    #[must_use]
    pub fn new(contacts: Vec<ContactUpdate>) -> Self {
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
    ) -> Result<UpdateContactsTaskLoop, CspE2eProtocolError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                UpdateContactsTask::NAME
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

    /// Possible result after handling a [`UpdateContactsTaskInstruction::BeginTransaction`]
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

    /// Possible result after handling a [`UpdateContactsTaskInstruction::BeginTransaction`]
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
    /// [`UpdateContactsTaskInstruction::ReflectAndCommitTransaction`] instruction.
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
