//! Implementation of the transport layer of the _Device to Mediator (d2m) Protocol_.
use core::{cmp::min, mem};

use const_format::formatcp;
use data_encoding::HEXLOWER;
use educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use prost::Message as _;
use tracing::{debug, error, trace, warn};

use crate::{
    common::{ChatServerGroup, ClientInfo, D2xDeviceId, config::MediatorServerBaseUrl, keys::DeviceGroupKey},
    crypto::aead::AeadRandomNonceAhead as _,
    d2m::payload::{
        DatagramBuffer, IncomingPayload, OutgoingDatagram, OutgoingPayload, PayloadDecoder as _,
        PayloadEncoder as _,
        handshake::{ServerHello, ServerInfo},
    },
    protobuf::d2m::{self as protobuf},
    utils::{
        bytes::{ByteReaderError, ByteWriterError},
        debug::Name as _,
        protobuf::PaddedMessage as _,
    },
};

/// The minimum supported protocol version (inclusive)
const MINIMUM_PROTOCOL_VERSION: u32 = protobuf::ProtocolVersion::V0 as u32;
/// The maximum supported protocol version (inclusive)
const MAXIMUM_PROTOCOL_VERSION: u32 = protobuf::ProtocolVersion::V0 as u32;

pub mod payload;

/// Cause of an internal encoding error.
#[derive(Clone, Debug, thiserror::Error)]
pub enum InternalEncodingErrorCause {
    /// Unable to encode via a `ByteWriter`.
    #[error(transparent)]
    ByteWriterError(ByteWriterError),

    /// Unable to encode a protobuf [`prost::Message`].
    #[error(transparent)]
    ProtobufEncodeError(prost::EncodeError),
}

/// Cause of an internal error.
#[derive(Clone, Debug, thiserror::Error)]
#[expect(clippy::enum_variant_names, reason = "All ending with 'failed' intentionally")]
pub enum InternalErrorCause {
    /// Unable to encrypt a struct or message.
    #[error("Encrypting '{name}' failed")]
    EncryptionFailed {
        /// Name of the struct or message
        name: &'static str,
    },

    /// Unable to encode a struct or message.
    #[error("Encoding '{name}' failed: {source}")]
    EncodingFailed {
        /// Name of the struct or message
        name: &'static str,
        /// Error source
        source: InternalEncodingErrorCause,
    },

    /// Unable to encode a protobuf message.
    #[error("Encoding protobuf '{name} failed: {source}")]
    EncodingProtobufFailed {
        /// Name of the protobuf message
        name: &'static str,
        /// Error source
        source: prost::EncodeError,
    },
}

/// Source of a decoding error.
#[derive(Clone, Debug, thiserror::Error)]
pub enum DecodingErrorSource {
    /// Error during decoding of a protobuf message.
    #[error(transparent)]
    ProstDecodeError(#[from] prost::DecodeError),

    /// Error while reading (usually while decoding a struct).
    #[error(transparent)]
    ByteReaderError(#[from] ByteReaderError),
}

/// An error occurred while running the Device to Mediator Protocol.
///
/// Note: Errors can occur when using the API incorrectly or when the remote party behaves incorrectly. None
/// of these errors are considered recoverable.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`D2mProtocolError`].
/// 2. Abort the protocol due to `error`.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum D2mProtocolError {
    /// Invalid parameter provided by the caller.
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Invalid state for the requested operation.
    #[error("Invalid state: {0}")]
    InvalidState(&'static str),

    /// An internal error happened.
    #[error("Internal error: {0}")]
    InternalError(#[from] InternalErrorCause),

    /// Unable to decrypt a handshake message or a payload.
    #[error("Decrypting '{name}' failed")]
    DecryptionFailed {
        /// Name of the handshake message or payload.
        name: &'static str,
    },

    /// Unable to decode a message.
    #[error("Decoding '{name}' failed: {source}")]
    DecodingFailed {
        /// Name of the message.
        name: &'static str,
        /// Error source.
        source: DecodingErrorSource,
    },

    /// Unexpected payload type.
    #[error("Unexpected payload type: 0x{0:02x}")]
    UnexpectedPayloadType(u8),

    /// Invalid data in message or payload.
    #[error("Invalid '{name}': {cause}")]
    InvalidMessage {
        /// Name of the handshake message or payload.
        name: &'static str,
        /// Error cause.
        cause: String,
    },

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(&'static str),

    /// The version offered by the mediator server is unsupported.
    #[error("Server protocol version {0} is unsupported")]
    UnsupportedVersion(u32),
}

/// A D2M state update to indicate advancing.
#[derive(Debug)]
pub enum D2mStateUpdate {
    /// The client hello was sent successfully.
    AwaitingServerInfo,

    /// The handshake was successful.
    PostHandshake(ServerInfo),
}

/// An instruction of what to do next.
///
/// When handling this instruction, run the following steps:
///
/// 1. If the current phase is the _handshake phase_:
///    1. If `incoming_payload` is present, abort the protocol due to an error and abort these steps.
///    2. If `outgoing_datagram` is present, enqueue it to be sent to the mediator server.
/// 2. If the current phase is the _payload phase_:
///    1. If `state_update` is present, abort the protocol due to an error and abort these steps.
///    2. If `incoming_payload` is present, hand it off to the application.
///    3. If `outgoing_datagram` is present, enqueue it to be sent to the mediator server.
/// 3. (Unreachable)
pub struct D2mProtocolInstruction {
    /// The state to which the D2M protocol was advanced to.
    pub state_update: Option<D2mStateUpdate>,

    /// The outgoing datagram that should be sent to the server.
    pub outgoing_datagram: Option<OutgoingDatagram>,

    /// The incoming message that should be processed by the client.
    pub incoming_payload: Option<IncomingPayload>,
}

/// D2M-specific context information.
pub struct D2mContext {
    /// Mediator server URL from the configuration.
    pub mediator_server_url: MediatorServerBaseUrl,

    /// The device group key of the D2X protocol.
    pub device_group_key: DeviceGroupKey,

    /// CSP server group.
    pub csp_server_group: ChatServerGroup,

    /// The D2X device ID.
    pub device_id: D2xDeviceId,

    /// Policy to be applied in case the device id is not registered on the server and all device slots have
    /// been exhausted.
    pub device_slots_exhausted_policy: protobuf::client_hello::DeviceSlotsExhaustedPolicy,

    /// Policy determining the device slot's lifetime.
    pub device_slot_expiration_policy: protobuf::DeviceSlotExpirationPolicy,

    /// Expected device registration state on the mediator server.
    pub expected_device_slot_state: protobuf::DeviceSlotState,

    /// Client info to derive the `DeviceInfo` from.
    pub client_info: ClientInfo,

    /// The device's label (e.g. "PC at Work"), recommended to not exceed 64 grapheme clusters.
    pub device_label: Option<String>,
}

struct AwaitingServerHelloState {
    datagrams: DatagramBuffer,
}
struct AwaitingServerInfoState {
    datagrams: DatagramBuffer,
}
struct PostHandshakeState {
    datagrams: DatagramBuffer,
}

/// A state in the device to mediator protocol.
///
/// The flow is as follows:
///
/// ```txt
/// [Start] --> AwaitingServerHello +-> AwaitingServerInfo +-> PostHandshake <---\
///                                 |                      |                 \___/
///                                 +----------------------+-> Error <-------/
/// ```
///
/// To advance the state, call the corresponding poll function, e.g.,
/// [`State::poll_awaiting_server_hello`] to advance _out of_ the [`State::AwaitingServerHello`].
#[derive(DebugVariantNames, VariantNames)]
enum State {
    AwaitingServerHello(AwaitingServerHelloState),
    AwaitingServerInfo(AwaitingServerInfoState),
    PostHandshake(PostHandshakeState),
    Error(D2mProtocolError),
}

impl State {
    /// Consume a [`AwaitingServerHelloState`] and return a [`State::AwaitingServerInfo`] together with the
    /// next instruction to be executed by the client.
    ///
    /// The `datagrams` of the `state` should contain the next datagram. Otherwise, return the
    /// [`State::AwaitingServerHello`] again and set the [`D2mProtocolInstruction`] to `None` (reflecting that
    /// the caller must just wait).
    #[tracing::instrument(skip_all)]
    fn poll_awaiting_server_hello(
        context: &D2mContext,
        mut state: AwaitingServerHelloState,
    ) -> Result<(Self, Option<D2mProtocolInstruction>), D2mProtocolError> {
        // Poll the `ServerHello`
        trace!(datagrams = ?state.datagrams, "Poll");
        let server_hello = {
            let Some(datagram) = state.datagrams.next() else {
                return Ok((Self::AwaitingServerHello(state), None));
            };
            ServerHello::decode_from_datagram(datagram)?
        };
        debug!(version = server_hello.version, "Received ServerHello message");
        trace!(?server_hello);

        // Check the offered version and choose the maximum compatible version
        #[expect(
            clippy::absurd_extreme_comparisons,
            reason = "MINIMUM_PROTOCOL_VERSION is currently zero"
        )]
        if server_hello.version < MINIMUM_PROTOCOL_VERSION {
            let error = D2mProtocolError::UnsupportedVersion(server_hello.version);
            warn!(?error, "Unsupported version");
            return Err(error);
        }
        let chosen_version = min(server_hello.version, MAXIMUM_PROTOCOL_VERSION);

        // Encode the ClientHello message
        debug!(chosen_version, "Creating ClientHello message");
        let client_hello = {
            // Solve the challenge and prepare the response
            let (_, challenge_response) = context
                .device_group_key
                .path_key()
                .authentication_cipher(&server_hello.ephemeral_server_key)
                .0
                .encrypt_random_nonce_ahead(server_hello.challenge.as_slice())
                .map_err(|_| {
                    D2mProtocolError::InternalError(InternalErrorCause::EncryptionFailed {
                        name: "ChallengeResponse",
                    })
                })?;

            // Encrypt the device info
            let encrypted_device_info = {
                let mut device_info = context
                    .client_info
                    .to_device_info(context.device_label.clone())
                    .encode_to_vec_padded();
                let _ = context
                    .device_group_key
                    .device_info_key()
                    .0
                    .encrypt_in_place_random_nonce_ahead(b"", &mut device_info)
                    .map_err(|_| {
                        D2mProtocolError::InternalError(InternalErrorCause::EncryptionFailed {
                            name: "EncryptedDeviceInfo",
                        })
                    })?;
                device_info
            };

            // Wrap it in a ClientHello
            protobuf::ClientHello {
                version: chosen_version,
                response: challenge_response,
                device_id: context.device_id.0,
                device_slots_exhausted_policy: context.device_slots_exhausted_policy as i32,
                device_slot_expiration_policy: context.device_slot_expiration_policy as i32,
                expected_device_slot_state: context.expected_device_slot_state as i32,
                encrypted_device_info,
            }
        };

        // Set and return the next state and instruction
        let state = Self::AwaitingServerInfo(AwaitingServerInfoState {
            datagrams: state.datagrams,
        });
        let instruction = D2mProtocolInstruction {
            state_update: Some(D2mStateUpdate::AwaitingServerInfo),
            outgoing_datagram: Some(OutgoingDatagram(client_hello.encode_to_datagram()?)),
            incoming_payload: None,
        };
        debug!(?state, "Changing state");
        Ok((state, Some(instruction)))
    }

    /// Consume a [`AwaitingServerInfoState`] and return a [`State::PostHandshake`] together with the next
    /// instruction to be executed by the client.
    ///
    /// The `datagrams` of the `state` should contain the next datagram. Otherwise, return the
    /// [`State::AwaitingServerInfoState`] again and set the [`D2mProtocolInstruction`] to `None` (reflecting
    /// that the caller must just wait).
    #[tracing::instrument(skip_all)]
    fn poll_awaiting_server_info(
        mut state: AwaitingServerInfoState,
    ) -> Result<(Self, Option<D2mProtocolInstruction>), D2mProtocolError> {
        // Poll the ServerInfo
        trace!(datagrams = ?state.datagrams, "Poll");
        let server_info = {
            let Some(datagram) = state.datagrams.next() else {
                return Ok((Self::AwaitingServerInfo(state), None));
            };
            ServerInfo::decode_from_datagram(datagram)?
        };
        debug!(
            reflection_queue_length = server_info.reflection_queue_length,
            "Received ServerInfo message"
        );
        trace!(?server_info);

        // Set and return the next state and instruction
        let state = Self::PostHandshake(PostHandshakeState {
            datagrams: state.datagrams,
        });
        let instruction = D2mProtocolInstruction {
            state_update: Some(D2mStateUpdate::PostHandshake(server_info)),
            outgoing_datagram: None,
            incoming_payload: None,
        };
        debug!(?state, "Changing state");
        Ok((state, Some(instruction)))
    }

    /// Try to loop over the [`PostHandshakeState`] and return [`State::PostHandshake`] as well as the next
    /// instruction to be executed by the client.
    ///
    /// The `datagrams` of the `state` should contain the next datagram. Otherwise, return the
    /// [`State::PostHandshake`] again and set the [`D2mProtocolInstruction`] to `None` (reflecting that the
    /// caller must just wait).
    #[tracing::instrument(skip_all)]
    fn poll_post_handshake(
        mut state: PostHandshakeState,
    ) -> Result<(Self, Option<D2mProtocolInstruction>), D2mProtocolError> {
        // Poll for a payload
        trace!(datagrams = ?state.datagrams, "Poll");
        let payload = {
            let Some(datagram) = state.datagrams.next() else {
                return Ok((Self::PostHandshake(state), None));
            };
            IncomingPayload::decode_from_datagram(datagram)?
        };
        debug!(?payload, "Received payload");

        // Set and return the next state and instruction
        let instruction = D2mProtocolInstruction {
            state_update: None,
            outgoing_datagram: None,
            incoming_payload: Some(payload),
        };
        Ok((State::PostHandshake(state), Some(instruction)))
    }
}

/// The Device to Mediator Protocol state machine.
///
/// The protocol state machine can be constructed to establish a connection to the mediator server via
/// [`D2mProtocol::new`].
///
/// Any interaction with the protocol state machine that changes the internal state will yield a
/// [`D2mProtocolInstruction`] that must be handled according to its documentation.
///
/// The protocol goes through exactly two phases:
///
/// - The _handshake phase_.
/// - The _payload phase_ where payloads are being exchanged.
///
/// When the connection to the mediator server has been closed:
///
/// 1. Let `cause` be an error or any information associated to the close event.
/// 2. Log the protocol abort due to `cause` as a notice or an error respectively.
/// 3. Tear down the protocol state machine.
///
/// The flow of the state machine is as follows:
///
/// 1. Run [`D2mProtocol::new`] to initiate a new state machine and connect to the mediator server via the
///    provided WebSocket URL.
/// 2. Run the following steps in a loop:
///    1. Run the following steps for each received WebSocket message:
///       1. If the received WebSocket message is not of type binary, log a warning and discard it.
///       2. If the received WebSocket message is of type binary, forward it to the protocol via
///          [`D2mProtocol::add_datagrams`].
///    2. Run [`D2mProtocol::poll`] and let `instruction` be the resulting [`D2mProtocolInstruction`].
///    3. Handle `instruction`. If `instruction` yielded a [`D2mStateUpdate::PostHandshake`] state, break this
///       loop.
/// 3. Run the following steps in a loop:
///    1. Run [`D2mProtocol::poll`] and handle any instruction until it no longer produces one.
///    2. Run the following steps for each received WebSocket message:
///       1. If the received WebSocket message is not of type binary, log a warning and discard it.
///       2. If the received WebSocket message is of type binary, forward it to the protocol via
///          [`D2mProtocol::add_datagrams`].
///    3. If a payload is being created, run [`D2mProtocol::create_payload`] and handle the resulting
///       instruction.
#[derive(Name, educe::Educe)]
#[educe(Debug)]
pub struct D2mProtocol {
    #[educe(Debug(ignore))]
    context: D2mContext,
    state: State,
}

impl D2mProtocol {
    /// Initiate a new D2M protocol and also return the WebSocket URL to be used to connect to the server.
    #[tracing::instrument(skip_all)]
    pub fn new(context: D2mContext) -> (Self, String) {
        debug!("Creating D2M protocol");

        // Create the `client-url-info` and hex-encode it into the WebSocket path
        let url = {
            let device_group_id = context.device_group_key.path_key().public_key();
            trace!(
                ?device_group_id,
                server_group = &context.csp_server_group.0,
                "Create client-url-info message"
            );
            let client_url_info = HEXLOWER.encode(
                &protobuf::ClientUrlInfo {
                    device_group_id: device_group_id.0.as_bytes().to_vec(),
                    server_group: context.csp_server_group.0.clone().to_string(),
                }
                .encode_to_vec(),
            );
            context.mediator_server_url.path(
                &context.device_group_key.path_key(),
                format_args!("{client_url_info}"),
            )
        };

        // Create and return initial state
        let state = State::AwaitingServerHello(AwaitingServerHelloState {
            datagrams: DatagramBuffer::default(),
        });
        debug!(?state, "Starting with initial state");
        (Self { context, state }, url)
    }

    /// Poll to advance the state.
    ///
    /// If this returns [`None`], then the state machine will not produce any more [`D2mProtocolInstruction`]s
    /// until further input is being provided.
    ///
    /// # Errors
    ///
    /// Returns [`D2mProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<Option<D2mProtocolInstruction>, D2mProtocolError> {
        let poll_result = match mem::replace(
            &mut self.state,
            State::Error(D2mProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                D2mProtocol::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::AwaitingServerHello(state) => State::poll_awaiting_server_hello(&self.context, state),
            State::AwaitingServerInfo(state) => State::poll_awaiting_server_info(state),
            State::PostHandshake(state) => State::poll_post_handshake(state),
        };

        match poll_result {
            Ok((state, instruction)) => {
                // Extract the next instruction and change the internal state
                self.state = state;
                Ok(instruction)
            },
            Err(error) => {
                // There is nothing we can do in case of an error...
                self.state = State::Error(error.clone());
                Err(error)
            },
        }
    }

    /// Add datagrams received from the mediator server. A datagram must contain exactly one contiguous
    /// WebSocket message.
    ///
    /// # Errors
    ///
    /// Returns [`D2mProtocolError::InvalidState`] if the protocol is in the error state.
    #[tracing::instrument(skip_all, fields(
        datagrams_byte_length = datagrams.iter().map(Vec::len).sum::<usize>(),
    ))]
    pub fn add_datagrams(&mut self, datagrams: Vec<Vec<u8>>) -> Result<(), D2mProtocolError> {
        trace!("Adding datagrams");
        match &mut self.state {
            State::AwaitingServerHello(state) => {
                state.datagrams.add_datagrams(datagrams);
            },
            State::AwaitingServerInfo(state) => {
                state.datagrams.add_datagrams(datagrams);
            },
            State::PostHandshake(state) => {
                state.datagrams.add_datagrams(datagrams);
            },
            State::Error(error) => {
                let message = "Cannot add datagram in error state";
                error!(?error, message);
                return Err(D2mProtocolError::InvalidState(message));
            },
        }
        Ok(())
    }

    /// Create an outgoing message.
    ///
    /// # Errors
    ///
    /// Returns [`D2mProtocolError::InvalidState`] if the protocol is not in the post-handshake state.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn create_payload(
        &mut self,
        payload: OutgoingPayload,
    ) -> Result<D2mProtocolInstruction, D2mProtocolError> {
        match (&self.state, &payload) {
            // Creating payloads is allowed in...
            //
            // - the post-handshake state for any type of payload, and
            // - all other valid states for the `proxy` payload.
            (State::PostHandshake(_), _)
            | (State::AwaitingServerHello(_) | State::AwaitingServerInfo(_), OutgoingPayload::Proxy(_)) => {},

            // Creating payloads in any other state is disallowed.
            (State::Error(error), _) => {
                let message = "Cannot create an outgoing payload in the error state";
                error!(?error, message);
                return Err(D2mProtocolError::InvalidState(message));
            },
            (_, _) => {
                let message = "Creating an outgoing (non-proxy) payload requires the post-handshake state";
                error!(message);
                return Err(D2mProtocolError::InvalidState(message));
            },
        }

        // Encode the payload into an outgoing datagram
        let outgoing_datagram = OutgoingDatagram(payload.encode_to_datagram()?);

        // Done
        debug!("Creating payload");
        Ok(D2mProtocolInstruction {
            state_update: None,
            outgoing_datagram: Some(outgoing_datagram),
            incoming_payload: None,
        })
    }
}
