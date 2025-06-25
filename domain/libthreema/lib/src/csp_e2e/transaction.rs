//! Tasks for beginning and committing (or aborting) a transaction.

/// Task for beginning a transaction.
pub mod begin {
    use core::mem;

    use const_format::formatcp;
    use libthreema_macros::{DebugVariantNames, Name, VariantNames};
    use prost::{Message as _, Name as _};
    use tracing::{debug, info, warn};

    use super::commit::CommitTransactionSubtask;
    use crate::{
        common::{DeviceGroupTransactionScopeCipher, DeviceId},
        crypto::aead::AeadRandomNonceAhead as _,
        csp_e2e::{CspE2eProtocolError, D2xContext, TaskLoop},
        protobuf::{self},
        utils::time::Duration,
    };

    /// Instruction for beginning a transaction. See each variant's steps.
    pub enum BeginTransactionInstruction {
        /// 1. Await a `TransactionEnded` message.
        /// 2. Poll again.
        TransactionRejected,

        /// 1. Send the `message` (`BeginTransaction`).
        /// 2. Let `response` be the result of selectively awaiting a `BeginTransactionAck` or
        ///    `TransactionRejected` message.
        /// 3. Provide the `response` to the associated task (subtask:
        ///    [`BeginTransactionSubtask::begin_transaction_response`]) and poll again.
        BeginTransaction {
            /// The D2M `BeginTransaction` message.
            message: protobuf::d2m::BeginTransaction,
        },

        /// 1. Send the `message` (`CommitTransaction`).
        /// 2. Let `response` be the result of selectively awaiting a `CommitTransactionAck`.
        /// 3. Provide `response` to the associated task (subtask:
        ///    [`BeginTransactionSubtask::abort_transaction_response`]) and poll again.
        AbortTransaction {
            /// The D2M `CommitTransaction` message.
            message: protobuf::d2m::CommitTransaction,
        },
    }

    /// Possible responses to a `BeginTransaction` message.
    pub enum BeginTransactionResponse {
        /// The transaction has been acknowledged and is now in progress.
        BeginTransactionAck(protobuf::d2m::BeginTransactionAck),

        /// The transaction has been rejected and needs to be retried.
        TransactionRejected(protobuf::d2m::TransactionRejected),
    }

    pub(crate) enum BeginTransactionResult {
        /// The transaction is now in progress (and consequently needs to be committed after the
        /// desired task has been fulfilled, or alternatively abandoned).
        TransactionInProgress,

        /// The transaction has been aborted by the precondition. If the transaction was in
        /// progress, it has since been committed.
        TransactionAborted,
    }

    pub(crate) type BeginTransactionTaskLoop = TaskLoop<BeginTransactionInstruction, BeginTransactionResult>;

    /// Decide whether the transaction is still required or whether it can be aborted.
    pub(crate) enum PreconditionVerdict {
        /// Continue the transaction.
        Continue,

        /// Abort the transaction.
        ///
        /// Use this when the transaction has become obsolete. Do not use this for errors!
        Abort,
    }

    /// Precondition function to check if a transaction is still necessary.
    ///
    /// Errors are propagated back to the task.
    pub(crate) type PreconditionFn = Box<dyn Fn() -> Result<PreconditionVerdict, CspE2eProtocolError>>;

    /// The transaction is either being initiated for the first time or it was rejected, so we need
    /// to re-initiate it.
    struct InitOrTransactionRejectedState {
        precondition: PreconditionFn,
        scope: protobuf::d2d::transaction_scope::Scope,
        time_to_live: Option<Duration>,
    }

    /// A `BeginTransaction` is being sent and we're waiting for the response.
    struct BeginTransactionState {
        precondition: PreconditionFn,
        scope: protobuf::d2d::transaction_scope::Scope,
        time_to_live: Option<Duration>,
        response: Option<BeginTransactionResponse>,
    }

    /// The transaction is in progress but the precondition indicated that the transaction should be
    /// aborted (i.e. committed without any further action).
    struct AbortTransactionState {
        commit_transaction_task: CommitTransactionSubtask,
    }

    #[derive(DebugVariantNames, VariantNames)]
    enum State {
        Error(CspE2eProtocolError),
        InitOrTransactionRejected(InitOrTransactionRejectedState),
        BeginTransaction(BeginTransactionState),
        AbortTransaction(AbortTransactionState),
        Done,
    }
    impl State {
        fn poll_init_or_transaction_rejected(
            context: &D2xContext,
            state: InitOrTransactionRejectedState,
        ) -> Result<(Self, BeginTransactionTaskLoop), CspE2eProtocolError> {
            // The transaction is either being started for the first time or another transaction
            // ended, so we can retry. Before we do so, run the precondition.
            match (state.precondition)()? {
                PreconditionVerdict::Continue => {},
                PreconditionVerdict::Abort => {
                    info!("Skipping transaction due to precondition abort");
                    return Ok((
                        Self::Done,
                        BeginTransactionTaskLoop::Done(BeginTransactionResult::TransactionAborted),
                    ));
                },
            }

            // Encrypt the scope
            //
            // Note: We don't care if the scope is replayed, so not storing the nonce here.
            let encrypted_scope = {
                let mut scope = protobuf::d2d::TransactionScope {
                    scope: state.scope.into(),
                }
                .encode_to_vec();
                let _ = context
                    .device_group_key
                    .transaction_scope_key()
                    .0
                    .encrypt_in_place_random_nonce_ahead(b"", &mut scope)
                    .map_err(|_| CspE2eProtocolError::EncryptionFailed {
                        name: protobuf::d2d::TransactionScope::NAME,
                    })?;
                scope
            };

            // Begin the transaction
            info!("Beginning transaction");
            Ok((
                Self::BeginTransaction(BeginTransactionState {
                    precondition: state.precondition,
                    scope: state.scope,
                    time_to_live: state.time_to_live,
                    response: None,
                }),
                BeginTransactionTaskLoop::Instruction(BeginTransactionInstruction::BeginTransaction {
                    message: protobuf::d2m::BeginTransaction {
                        encrypted_scope,
                        ttl: state.time_to_live.map_or(0, |duration| {
                            u32::try_from(duration.as_secs()).unwrap_or(u32::MAX)
                        }),
                    },
                }),
            ))
        }

        fn poll_begin_transaction(
            context: &D2xContext,
            state: BeginTransactionState,
        ) -> Result<(Self, BeginTransactionTaskLoop), CspE2eProtocolError> {
            // Ensure the caller provided the response
            let Some(response) = state.response else {
                return Err(CspE2eProtocolError::InvalidState(formatcp!(
                    "BeginTransactionAck/TransactionRejected response was not provided for '{}' state",
                    State::BEGIN_TRANSACTION,
                )));
            };

            // Process the response
            Ok(match response {
                // The transaction is now in progress
                BeginTransactionResponse::BeginTransactionAck(..) => {
                    // Run the precondition again...
                    match (state.precondition)()? {
                        // Transaction still necessary, continue
                        PreconditionVerdict::Continue => {
                            info!("Transaction acknowledged, now in progress");
                            (
                                Self::Done,
                                BeginTransactionTaskLoop::Done(BeginTransactionResult::TransactionInProgress),
                            )
                        },

                        // Transaction is obsolete now, so abort it by committing immediately
                        PreconditionVerdict::Abort => {
                            info!("Commiting obsolete transaction due to precondition abort");
                            let (commit_transaction_task, commit_transaction_instruction) =
                                CommitTransactionSubtask::new(vec![]);
                            (
                                Self::AbortTransaction(AbortTransactionState {
                                    commit_transaction_task,
                                }),
                                BeginTransactionTaskLoop::Instruction(
                                    BeginTransactionInstruction::AbortTransaction {
                                        message: commit_transaction_instruction.commit_transaction_message,
                                    },
                                ),
                            )
                        },
                    }
                },

                // The transaction was rejected due to another transaction being in progress
                BeginTransactionResponse::TransactionRejected(transaction_rejected) => {
                    fn decrypt_and_decode_transaction_scope(
                        transaction_scope_key: &DeviceGroupTransactionScopeCipher,
                        mut scope: Vec<u8>,
                    ) -> Option<protobuf::d2d::transaction_scope::Scope> {
                        // Decrypt scope
                        if transaction_scope_key
                            .0
                            .decrypt_in_place_random_nonce_ahead(b"", &mut scope)
                            .is_err()
                        {
                            warn!(
                                "Ignoring scope that could not be decrypted in TransactionRejected message"
                            );
                            return None;
                        }

                        // Decode scope
                        let scope = match protobuf::d2d::TransactionScope::decode(scope.as_ref()) {
                            Ok(scope) => scope,
                            Err(error) => {
                                warn!(decode_error = ?error,
                                    "Ignoring scope message that could not be decoded in \
                                     TransactionRejected message");
                                return None;
                            },
                        };
                        match protobuf::d2d::transaction_scope::Scope::try_from(scope.scope) {
                            Ok(scope) => Some(scope),
                            Err(error) => {
                                warn!(decode_error = ?error,
                                    "Ignoring scope enum value that could not be decoded in \
                                     TransactionRejected message");
                                None
                            },
                        }
                    }

                    // Try to decrypt and decode the scope and go back to the initial state
                    let other_device_id = DeviceId(transaction_rejected.device_id);
                    let other_scope = decrypt_and_decode_transaction_scope(
                        &context.device_group_key.transaction_scope_key(),
                        transaction_rejected.encrypted_scope,
                    );
                    info!(
                        ?other_device_id,
                        ?other_scope,
                        "Transaction rejected due to another transaction being in progress"
                    );
                    (
                        Self::InitOrTransactionRejected(InitOrTransactionRejectedState {
                            precondition: state.precondition,
                            scope: state.scope,
                            time_to_live: state.time_to_live,
                        }),
                        BeginTransactionTaskLoop::Instruction(
                            BeginTransactionInstruction::TransactionRejected,
                        ),
                    )
                },
            })
        }

        fn poll_abort_transaction(
            state: AbortTransactionState,
        ) -> Result<(Self, BeginTransactionTaskLoop), CspE2eProtocolError> {
            state.commit_transaction_task.poll()?;
            Ok((
                Self::Done,
                BeginTransactionTaskLoop::Done(BeginTransactionResult::TransactionAborted),
            ))
        }
    }

    /// Subtask for beginning a transaction and looping until it has been established or aborted.
    #[derive(Name)]
    pub(crate) struct BeginTransactionSubtask {
        state: State,
    }
    impl BeginTransactionSubtask {
        pub(crate) fn new(
            precondition: PreconditionFn,
            scope: protobuf::d2d::transaction_scope::Scope,
            time_to_live: Option<Duration>,
        ) -> Self {
            Self {
                state: State::InitOrTransactionRejected(InitOrTransactionRejectedState {
                    precondition,
                    scope,
                    time_to_live,
                }),
            }
        }

        pub(crate) fn poll(
            &mut self,
            context: &D2xContext,
        ) -> Result<BeginTransactionTaskLoop, CspE2eProtocolError> {
            let result = match mem::replace(
                &mut self.state,
                State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                    "{} in a transitional state",
                    BeginTransactionSubtask::NAME
                ))),
            ) {
                State::Error(error) => Err(error),
                State::InitOrTransactionRejected(state) => {
                    State::poll_init_or_transaction_rejected(context, state)
                },
                State::BeginTransaction(state) => State::poll_begin_transaction(context, state),
                State::AbortTransaction(state) => State::poll_abort_transaction(state),
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

        pub(crate) fn begin_transaction_response(
            &mut self,
            response: BeginTransactionResponse,
        ) -> Result<(), CspE2eProtocolError> {
            let State::BeginTransaction(state) = &mut self.state else {
                return Err(CspE2eProtocolError::InvalidState(formatcp!(
                    "Must be in '{}' state",
                    State::BEGIN_TRANSACTION
                )));
            };
            let _ = state.response.insert(response);
            Ok(())
        }

        pub(crate) fn abort_transaction_response(
            &mut self,
            response: protobuf::d2m::CommitTransactionAck,
        ) -> Result<(), CspE2eProtocolError> {
            let State::AbortTransaction(state) = &mut self.state else {
                return Err(CspE2eProtocolError::InvalidState(formatcp!(
                    "Must be in '{}' state",
                    State::ABORT_TRANSACTION
                )));
            };
            state
                .commit_transaction_task
                .reflect_and_commit_transaction_ack(vec![], response);
            Ok(())
        }
    }
}

/// Task for committing an established transaction.
///
/// Note: The only way to abort an established transaction is to disconnect and reconnect.
pub mod commit {
    use libthreema_macros::Name;

    use crate::{
        csp_e2e::{
            CspE2eProtocolError, ReflectId,
            reflect::{ReflectInstruction, ReflectPayload, ReflectSubtask},
        },
        protobuf::{self},
    };

    /// 1. Let `reflect-ids` be the list of all reflect IDs of `reflect_messages` that do not have the
    ///    _ephemeral_ flag.
    /// 2. Reflect each message of `reflect_messages` with the provided flags and ID as a `reflect` message.
    /// 3. Send the `commit_transaction_message` (`CommitTransaction`).
    /// 4. Let `acknowledged_reflect_ids` the collection of all `reflect-ack` messages while selectively
    ///    awaiting a `CommitTransactionAck` and storing it as `response`.
    /// 5. Provide `acknowledged_reflect_ids` and `response` to the associated task (subtask:
    ///    [`CommitTransactionSubtask::reflect_and_commit_transaction_ack`]) and poll again.
    pub struct CommitTransactionInstruction {
        /// Messages to be reflected.
        pub reflect_messages: Vec<ReflectPayload>,

        /// The D2M `CommitTransaction` message.
        pub commit_transaction_message: protobuf::d2m::CommitTransaction,
    }

    /// Subtask for committing a transaction and waiting until it has been comitted.
    #[derive(Name)]
    pub(crate) struct CommitTransactionSubtask(ReflectSubtask);
    impl CommitTransactionSubtask {
        pub(crate) fn new(reflect_messages: Vec<ReflectPayload>) -> (Self, CommitTransactionInstruction) {
            let (reflect_task, ReflectInstruction { reflect_messages }) =
                ReflectSubtask::new(reflect_messages);
            (
                Self(reflect_task),
                CommitTransactionInstruction {
                    reflect_messages,
                    commit_transaction_message: protobuf::d2m::CommitTransaction {},
                },
            )
        }

        pub(crate) fn poll(self) -> Result<(), CspE2eProtocolError> {
            self.0.poll()
        }

        pub(crate) fn reflect_and_commit_transaction_ack(
            &mut self,
            acknowledged_reflect_ids: Vec<ReflectId>,
            _response: protobuf::d2m::CommitTransactionAck,
        ) {
            self.0.reflect_ack(acknowledged_reflect_ids);
        }
    }
}
