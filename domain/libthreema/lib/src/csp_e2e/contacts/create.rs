//! Task for creating contacts.
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
        contact::{Contact, ContactInit},
        provider::{ContactProvider, ProviderError},
    },
    protobuf,
    utils::{debug::Name as _, time::utc_now_ms},
};

/// Instruction for creating contacts. See each variant's steps.
pub enum CreateContactsInstruction {
    /// Run [`BeginTransactionInstruction`], wrapping any response into a
    /// [`CreateContactsResponse::BeginTransactionResponse`].
    BeginTransaction(BeginTransactionInstruction),

    /// Run [`CommitTransactionInstruction`], wrapping any response into a
    /// [`CreateContactsResponse::CommitTransactionResponse`].
    ReflectAndCommitTransaction(CommitTransactionInstruction),
}

/// Possible response for an [`CreateContactsInstruction`].
pub enum CreateContactsResponse {
    /// Possible response for an inner [`BeginTransactionInstruction`].
    BeginTransactionResponse(BeginTransactionResponse),

    /// Possible response for an inner [`CommitTransactionInstruction`].
    CommitTransactionResponse(CommitTransactionResponse),
}

/// Result of creating contacts.
pub struct CreateContactsResult {
    /// A list of identities that have been created.
    pub added: Vec<ThreemaId>,
}

/// Result of polling a [`CreateContactsTask`].
pub type CreateContactsLoop = TaskLoop<CreateContactsInstruction, CreateContactsResult>;

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
    ) -> Result<(Self, CreateContactsLoop), CspE2eProtocolError> {
        let identities: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();

        // Add tracing span
        //
        // TODO(LIB-16): This should be applied to the whole task somehow
        let _span = tracing::info_span! { "contacts", contacts = ?identities }.entered();

        // Non-MD: Add contacts and done
        if context.d2x.is_none() {
            let added = Self::add_contacts(&mut *context.contacts.borrow_mut(), state.contacts)?;
            return Ok((
                Self::Done,
                CreateContactsLoop::Done(CreateContactsResult { added }),
            ));
        }

        // MD: We need to create a transaction.

        // Precondition: At least one of the contacts has not yet been added.
        let contact_provider = Rc::clone(&context.contacts);
        let precondition = Box::new(move || {
            Ok(
                if contact_provider.borrow().has_many(&identities)? < identities.len() {
                    PreconditionVerdict::Continue
                } else {
                    PreconditionVerdict::Abort
                },
            )
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
    ) -> Result<(Self, CreateContactsLoop), CspE2eProtocolError> {
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
                    CreateContactsLoop::Instruction(CreateContactsInstruction::BeginTransaction(instruction)),
                ));
            },
            BeginTransactionLoop::Done(result) => match result {
                BeginTransactionResult::TransactionInProgress => {},
                BeginTransactionResult::TransactionAborted => {
                    info!("Contacts already added");
                    return Ok((
                        Self::Done,
                        CreateContactsLoop::Done(CreateContactsResult { added: vec![] }),
                    ));
                },
            },
        }

        // Contacts may have been added in between states, so we need to filter affected
        let mut contacts = state
            .contacts
            .into_iter()
            .filter_map(|contact| match context.contacts.borrow().has(contact.identity) {
                Ok(true) => Some(Ok(contact)),
                Ok(false) => None,
                Err(error) => Some(Err(error)),
            })
            .collect::<Result<Vec<Contact>, ProviderError>>()?;

        // Encode and encrypt reflection messages containing the contacts to be added
        let (reflect_messages, nonces) = contacts
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
        d2x_context.nonce_storage.borrow_mut().add_many(nonces)?;
        let (transaction_task, commit_instruction) = CommitTransactionSubtask::new(reflect_messages);
        Ok((
            Self::ReflectAndCommitTransaction(ReflectAndCommitTransactionState {
                contacts,
                transaction_task,
            }),
            CreateContactsLoop::Instruction(CreateContactsInstruction::ReflectAndCommitTransaction(
                commit_instruction,
            )),
        ))
    }

    fn poll_reflect_and_commit_transaction(
        context: &CspE2eProtocolContext,
        state: ReflectAndCommitTransactionState,
    ) -> Result<(Self, CreateContactsLoop), CspE2eProtocolError> {
        // Fulfill the transaction
        state.transaction_task.poll()?;

        // Add contacts
        let expected: Vec<ThreemaId> = state.contacts.iter().map(|contact| contact.identity).collect();
        let added = Self::add_contacts(&mut *context.contacts.borrow_mut(), state.contacts)?;
        if expected.len() != added.len() {
            let message = "One or more contact were added unexpectedly during a transaction";
            error!(?expected, ?added, message);
            return Err(CspE2eProtocolError::DesyncError(message.to_owned()));
        }

        // Done
        Ok((
            Self::Done,
            CreateContactsLoop::Done(CreateContactsResult { added }),
        ))
    }

    fn add_contacts(
        contact_provider: &mut dyn ContactProvider,
        mut contacts: Vec<ContactInit>,
    ) -> Result<Vec<ThreemaId>, ProviderError> {
        // Add all missing contacts
        let mut identities: Vec<ThreemaId> = Vec::with_capacity(contacts.len());
        for contact in &contacts {
            if contact_provider.has(contact.identity)? {
                error!(identity = ?contact.identity, "Not adding contact, already exists");
            } else {
                identities.push(contact.identity);
            }
        }
        contacts.retain(|contact| identities.contains(&contact.identity));
        debug!(contacts = ?identities, "Adding contacts");
        contact_provider.add(contacts)?;
        info!(contacts = ?identities, "Added contacts");

        // TODO(SE-510): Schedule fetching gateway-defined profile picture here, if contact was
        // added and if necessary.

        // Added
        Ok(identities)
    }
}

/// Task for creating contacts.
#[derive(Debug, Name)]
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
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(
        &mut self,
        context: &mut CspE2eProtocolContext,
    ) -> Result<CreateContactsLoop, CspE2eProtocolError> {
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
            State::Done => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} already done",
                CreateContactsTask::NAME
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

    /// Possible results after handling a [`CreateContactsInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(&mut self, response: CreateContactsResponse) -> Result<(), CspE2eProtocolError> {
        match response {
            CreateContactsResponse::BeginTransactionResponse(response) => {
                let State::BeginTransaction(state) = &mut self.state else {
                    return Err(CspE2eProtocolError::InvalidState(formatcp!(
                        "Must be in '{}' state",
                        State::BEGIN_TRANSACTION
                    )));
                };
                state.transaction_task.response(response)
            },
            CreateContactsResponse::CommitTransactionResponse(response) => {
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
