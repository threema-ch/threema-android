//! Task for committing an established transaction.
//!
//! Note: The only way to abort an established transaction is to disconnect and reconnect.
use libthreema_macros::Name;

use crate::{
    csp_e2e::{
        CspE2eProtocolError, ReflectId,
        reflect::{ReflectInstruction, ReflectPayload, ReflectResponse, ReflectSubtask},
    },
    protobuf::{self},
};

/// 1. Let `reflect-ids` be the list of all reflect IDs of `reflect_messages` that do not have the _ephemeral_
///    flag.
/// 2. Reflect each message of `reflect_messages` with the provided flags and ID as a `reflect` message.
/// 3. Send the `commit_transaction_message` (`CommitTransaction`).
/// 4. Let `acknowledged_reflect_ids` the collection of all `reflect-ack` messages while selectively awaiting
///    a `CommitTransactionAck`.
/// 5. Provide `acknowledged_reflect_ids` and the `CommitTransactionAck` to the associated task as a
///    [`CommitTransactionResponse`] and poll again.
pub struct CommitTransactionInstruction {
    /// Messages to be reflected.
    pub reflect_messages: Vec<ReflectPayload>,

    /// The D2M `CommitTransaction` message.
    pub commit_transaction_message: protobuf::d2m::CommitTransaction,
}

/// Possible response for an [`CommitTransactionInstruction`].
pub struct CommitTransactionResponse {
    /// Acknowledgements for reflected messages.
    pub acknowledged_reflect_ids: Vec<ReflectId>,

    /// The transaction has been comitted successfully.
    pub commit_transaction_ack: protobuf::d2m::CommitTransactionAck,
}

/// Subtask for committing a transaction and waiting until it has been comitted.
#[derive(Debug, Name)]
pub(crate) struct CommitTransactionSubtask(ReflectSubtask);
impl CommitTransactionSubtask {
    pub(crate) fn new(reflect_messages: Vec<ReflectPayload>) -> (Self, CommitTransactionInstruction) {
        let (reflect_task, ReflectInstruction { reflect_messages }) = ReflectSubtask::new(reflect_messages);
        (
            Self(reflect_task),
            CommitTransactionInstruction {
                reflect_messages,
                commit_transaction_message: protobuf::d2m::CommitTransaction {},
            },
        )
    }

    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn poll(self) -> Result<(), CspE2eProtocolError> {
        self.0.poll()
    }

    #[tracing::instrument(skip_all, fields(?self))]
    pub(crate) fn response(&mut self, response: CommitTransactionResponse) {
        self.0.response(ReflectResponse {
            acknowledged_reflect_ids: response.acknowledged_reflect_ids,
        });
    }
}
