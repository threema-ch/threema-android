//! Implementation of the end-to-end encryption layer of the _Chat Server Protocol_.
use core::fmt;
use std::rc::Rc;

use config::Config;
use contact::lookup::ContactLookupCache;
use incoming_message::task::IncomingMessageTask;
use libthreema_macros::Name;
use provider::{ContactProvider, MessageProvider, NonceStorage, SettingsProvider, ShortcutProvider};

use crate::{
    common::{ClientKey, DeviceGroupKey, DeviceId, ThreemaId, WorkCredentials},
    csp::payload::MessageWithMetadataBox,
    utils::{
        bytes::{ByteReaderError, ByteWriterError},
        sequence_numbers::{SequenceNumberOverflow, SequenceNumberU32, SequenceNumberValue},
    },
};

pub mod config;
pub mod contact;
pub mod endpoints;
pub mod incoming_message;
pub mod message;
pub mod provider;
pub mod reflect;
pub mod transaction;

/// An error occurred while running the end-to-end encryption layer of the _Chat Server Protocol_.
///
/// TODO(LIB-16): Clarify recoverability and what to do when encountering an error. so far, the idea
/// is that an error means unrecoverable and that the CSP or CSP/D2M connection needs to be
/// restarted. a new instance of the protocol can then be created with linear/exponential backoff.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum CspE2eProtocolError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Invalid state for the requested operation.
    #[error("Invalid state: {0}")]
    InvalidState(&'static str),

    /// An internal error happened.
    #[error("Internal error: {0}")]
    InternalError(String),

    /// Exhausted the available sequence numbers (e.g. for `ReflectId`s). Should never happen.
    #[error("Sequence number overflow happened")]
    SequenceNumberOverflow(#[from] SequenceNumberOverflow),

    /// The task is already done.
    #[error("Task '{0}' already done")]
    TaskAlreadyDone(&'static str),

    /// Unable to decrypt a handshake message or a payload.
    #[error("Decrypting '{name}' failed")]
    DecryptionFailed {
        /// Name of the handshake message or payload
        name: &'static str,
    },

    /// Unable to encrypt a handshake message or a payload.
    #[error("Encrypting '{name}' failed")]
    EncryptionFailed {
        /// Name of the handshake message or payload
        name: &'static str,
    },

    /// Unable to encode a struct.
    #[error("Encoding '{name}' failed: {source}")]
    EncodingFailed {
        /// Name of the struct
        name: &'static str,
        /// Error source
        source: ByteWriterError,
    },

    /// Unable to decode a struct.
    #[error("Decoding '{name}' failed: {source}")]
    DecodingFailed {
        /// Name of the struct
        name: &'static str,
        /// Error source
        source: ByteReaderError,
    },

    /// Invalid data in message or payload.
    #[error("Invalid '{name}': {cause}")]
    InvalidMessage {
        /// Name of the handshake message or payload
        name: &'static str,
        /// Error cause
        cause: String,
    },

    /// A network error occurred while communicating with a server.
    #[error("Network error: {0}")]
    NetworkError(String),

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(String),

    /// A desync occurred.
    ///
    /// This may be the result of...
    ///
    /// - a protocol violation from the client using this state machine (e.g. adding a contact to storage
    ///   without using the appropriate task), or
    /// - misbehaviour of another device in the device group (e.g. updating a contact without an appropriate
    ///   transaction), or
    /// - the mediator server misbehaving (e.g. forwarding reflected messages from another device while a
    ///   transaction is ongoing), or
    /// - an implementation error of this protocol state machine.
    ///
    /// If multi-device is active, device group integrity may have been compromised and relinking
    /// against another device is advisable.
    #[error("Desync error: {0}")]
    DesyncError(&'static str),

    /// Invalid credentials (should only be relevant for Work and OnPrem) reported by a server
    /// caused an operation to fail.
    ///
    /// When processing this variant, notify the user that the Work credentials are invalid and
    /// request new ones. A new CSP connection may be retried after the credentials have been
    /// validated.
    #[error("Invalid credentials")]
    InvalidCredentials,

    /// A rate limit of a server has been exceeded.
    ///
    /// When processing this variant, ensure that a new CSP connection attempt is delayed by at
    /// least 10s.
    #[error("Rate limit exceeded")]
    RateLimitExceeded,
}

/// A D2M reflect ID.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct ReflectId(pub u32);
impl fmt::Debug for ReflectId {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}({:016x})", Self::NAME, self.0.swap_bytes())
    }
}
impl From<SequenceNumberValue<u32>> for ReflectId {
    fn from(value: SequenceNumberValue<u32>) -> Self {
        ReflectId(value.0)
    }
}

/// Result of polling a task or advancing a task's state in any other fashion.
///
/// Note: This allows to easily expose instructions of a sub-task from a task without exposing the
/// sub-task's results.
pub enum TaskLoop<TInstruction, TResult> {
    /// An enclosed instructions needs to be handled in order to advance the task's state.
    Instruction(TInstruction),

    /// Result of the completed task.
    Done(TResult),
}

/// Work flavour of the application.
pub enum WorkFlavor {
    /// (Normal) Work application.
    Work,
    /// OnPrem application.
    OnPrem,
}

/// Work-related context information.
pub struct WorkContext {
    /// Work variant credentials.
    credentials: WorkCredentials,
    /// Work flavour of the application.
    flavor: WorkFlavor,
}

/// General flavour of the application.
pub enum Flavor {
    /// Consumer application.
    Consumer,
    /// Work (or OnPrem) application.
    Work(WorkContext),
}

/// CSP-specific context information.
struct CspE2eContext {
    /// The user's identity.
    user_identity: ThreemaId,
    /// Client key (`CK`).
    client_key: ClientKey,
    /// Application flavour.
    flavor: Flavor,
    /// CSP nonce storage.
    nonce_storage: Rc<dyn NonceStorage>,
}

/// Sequence number used for outgoing reflections.
struct ReflectSequenceNumber(SequenceNumberU32);

/// The current D2M role.
#[derive(Clone, Copy, PartialEq)]
pub enum D2mRole {
    /// Follower role (i.e. not yet _leader_).
    Follower,
    /// Leader role.
    Leader,
}

/// D2M/D2D-specific context information
pub struct D2xContext {
    /// The device's (D2X) ID.
    device_id: DeviceId,
    /// The device group key.
    device_group_key: DeviceGroupKey,
    /// D2D nonce storage.
    nonce_storage: Box<dyn NonceStorage>,
    /// Sequence number used for outgoing reflections.
    reflect_id: ReflectSequenceNumber,
    /// The current D2M role.
    role: D2mRole,
}

/// CSP E2EE protocol context
pub struct CspE2eProtocolContext {
    /// See [`ShortcutProvider`].
    shortcut_provider: Box<dyn ShortcutProvider>,
    /// Configuration used by the protocol.
    config: Config,
    /// See [`CspE2eContext`].
    csp_e2e: CspE2eContext,
    /// Optional D2X context, only available if multi-device is enabled, see [`D2xContext`].
    d2x: Option<D2xContext>,
    /// See [`SettingsProvider`].
    settings: Rc<dyn SettingsProvider>,
    /// See [`ContactProvider`].
    contacts: Rc<dyn ContactProvider>,
    /// See [`MessageProvider`].
    messages: Rc<dyn MessageProvider>,
    /// See [`ContactLookupCache`].
    contact_lookup_cache: ContactLookupCache,
}

/// The Chat Server E2EE Protocol state machine.
///
/// TODO(LIB-16):
/// - How to use.
/// - Protocol must be recreated whenever a reconnection happens.
/// - Add linear/exponential backoff when retrying a connection.
/// - ...
pub struct CspE2eProtocol {
    context: CspE2eProtocolContext,
}
impl CspE2eProtocol {
    /// TODO(LIB-16): How to use
    ///
    /// # Errors
    ///
    /// TODO(LIB-16): Describe errors
    pub fn update_d2m_state(&mut self, d2m_state: D2mRole) -> Result<(), CspE2eProtocolError> {
        let Some(d2m_context) = &mut self.context.d2x else {
            return Err(CspE2eProtocolError::InvalidState("MD context not initialized"));
        };
        if matches!(d2m_context.role, D2mRole::Leader) && matches!(d2m_state, D2mRole::Follower) {
            return Err(CspE2eProtocolError::InvalidState(
                "Downgrading D2M state is not allowed",
            ));
        }
        d2m_context.role = d2m_state;
        Ok(())
    }

    /// TODO(LIB-16): How to use
    ///
    /// # Errors
    ///
    /// TODO(LIB-16): Describe errors
    #[must_use]
    pub fn handle_incoming_message(payload: MessageWithMetadataBox) -> IncomingMessageTask {
        IncomingMessageTask::new(payload)
    }
}
