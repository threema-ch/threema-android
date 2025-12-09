//! Task for updating contacts.
//!
//! TODO(LIB-16): This omits profile pictures. Is this a problem?
use core::mem;
use std::rc::Rc;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, error, info};

use crate::{
    common::{Nonce, ThreemaId, task::TaskLoop},
    csp_e2e::{
        CspE2eProtocolContext, CspE2eProtocolError,
        reflect::{ReflectFlags, ReflectPayload},
        transaction::{
            begin::{
                BeginTransactionInstruction, BeginTransactionLoop, BeginTransactionResponse,
                BeginTransactionResult, BeginTransactionSubtask, PreconditionVerdict,
            },
            commit::{CommitTransactionInstruction, CommitTransactionResponse, CommitTransactionSubtask},
        },
    },
    model::{
        contact::ContactUpdate,
        provider::{ContactProvider, ProviderError},
    },
    protobuf,
    utils::debug::Name as _,
};

/// Instruction for updating contacts. See each variant's steps.
pub enum UpdateContactsInstruction {
    /// Run [`BeginTransactionInstruction`], wrapping any response into a
    /// [`UpdateContactsResponse::BeginTransactionResponse`].
    BeginTransaction(BeginTransactionInstruction),

    /// Run [`CommitTransactionInstruction`], wrapping any response into a
    /// [`UpdateContactsResponse::CommitTransactionResponse`].
    ReflectAndCommitTransaction(CommitTransactionInstruction),
}

/// Possible response for an [`UpdateContactsInstruction`].
pub enum UpdateContactsResponse {
    /// Possible response for an inner [`BeginTransactionInstruction`].
    BeginTransactionResponse(BeginTransactionResponse),

    /// Possible response for an inner [`CommitTransactionInstruction`].
    CommitTransactionResponse(CommitTransactionResponse),
}

/// Result of polling a [`UpdateContactsTask`].
pub type UpdateContactsLoop = TaskLoop<UpdateContactsInstruction, ()>;

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
    ) -> Result<(Self, UpdateContactsLoop), CspE2eProtocolError> {
        let identities: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();

        // Add tracing span
        //
        // TODO(LIB-16): This should be applied to the whole task somehow
        let _span = tracing::info_span! { "contacts", contacts = ?identities }.entered();

        // Non-MD: Update contacts and done
        if context.d2x.is_none() {
            Self::update_contacts(&mut *context.contacts.borrow_mut(), state.contacts)?;
            return Ok((Self::Done, UpdateContactsLoop::Done(())));
        }

        // MD: We need to create a transaction.

        // Precondition: All of the contacts must exist (hard error)
        let contact_provider = Rc::clone(&context.contacts);
        let precondition = Box::new(move || {
            let n_existing = contact_provider.borrow().has_many(&identities)?;
            if n_existing == identities.len() {
                Ok(PreconditionVerdict::Continue)
            } else {
                let message = "An existing contact disappeared";
                error!(n_existing, n_expected = identities.len(), message);
                Err(CspE2eProtocolError::DesyncError(message.to_owned()))
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
    ) -> Result<(Self, UpdateContactsLoop), CspE2eProtocolError> {
        // Ensure MD is enabled
        let Some(d2x_context) = context.d2x.as_mut() else {
            let message = "D2X context missing";
            error!(message);
            return Err(CspE2eProtocolError::InternalError(message.into()));
        };

        // Poll until the transaction is in progress
        match state.transaction_task.poll(d2x_context)? {
            BeginTransactionLoop::Instruction(instruction) => {
                return Ok((
                    Self::BeginTransaction(state),
                    UpdateContactsLoop::Instruction(UpdateContactsInstruction::BeginTransaction(instruction)),
                ));
            },
            BeginTransactionLoop::Done(result) => match result {
                BeginTransactionResult::TransactionInProgress => {},
                BeginTransactionResult::TransactionAborted => {
                    // The precondition should never abort, so this should never happen.
                    let message = "Transaction aborted unexpectedly";
                    error!(message);
                    return Err(CspE2eProtocolError::InternalError(message.into()));
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
        d2x_context.nonce_storage.borrow_mut().add_many(nonces)?;
        let (transaction_state, commit_instruction) = CommitTransactionSubtask::new(reflect_messages);
        Ok((
            Self::ReflectAndCommitTransaction(ReflectAndCommitTransactionState {
                contacts: state.contacts,
                transaction_task: transaction_state,
            }),
            UpdateContactsLoop::Instruction(UpdateContactsInstruction::ReflectAndCommitTransaction(
                commit_instruction,
            )),
        ))
    }

    fn poll_reflect_and_commit_transaction(
        context: &CspE2eProtocolContext,
        state: ReflectAndCommitTransactionState,
    ) -> Result<(Self, UpdateContactsLoop), CspE2eProtocolError> {
        // Fulfill the transaction
        state.transaction_task.poll()?;

        // Update contacts
        Self::update_contacts(&mut *context.contacts.borrow_mut(), state.contacts)?;
        Ok((Self::Done, UpdateContactsLoop::Done(())))
    }

    fn update_contacts(
        contact_provider: &mut dyn ContactProvider,
        contacts: Vec<ContactUpdate>,
    ) -> Result<(), ProviderError> {
        // Update all contacts
        debug!("Updating contacts");
        contact_provider.update(contacts)?;
        info!("Updated contacts");
        Ok(())
    }
}

/// Task for updating contacts.
#[derive(Debug, Name)]
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
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(
        &mut self,
        context: &mut CspE2eProtocolContext,
    ) -> Result<UpdateContactsLoop, CspE2eProtocolError> {
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
            State::Done => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} already done",
                UpdateContactsTask::NAME
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

    /// Possible results after handling a [`UpdateContactsInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(&mut self, response: UpdateContactsResponse) -> Result<(), CspE2eProtocolError> {
        match response {
            UpdateContactsResponse::BeginTransactionResponse(response) => {
                let State::BeginTransaction(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::BEGIN_TRANSACTION
                    )));
                };
                state.transaction_task.response(response)
            },
            UpdateContactsResponse::CommitTransactionResponse(response) => {
                let State::ReflectAndCommitTransaction(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::REFLECT_AND_COMMIT_TRANSACTION
                    )));
                };
                state.transaction_task.response(response);
                Ok(())
            },
        }
    }
}
