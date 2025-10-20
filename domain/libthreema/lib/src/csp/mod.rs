//! Implementation of the transport layer of the _Chat Server Protocol_.
use core::mem;

use cipher::{LoginAckCipher, LoginBoxes, LoginCipher, decrypt_server_challenge_response};
use const_format::formatcp;
use data_encoding::HEXLOWER;
use educe::Educe;
use frame::OutgoingFrame;
use handshake_messages::{
    ClientHello, Login, LoginAck, LoginAckData, LoginAckDecoder, LoginData, ServerHelloDecoder,
};
use libthreema_macros::{ConstantTimeEq, DebugVariantNames, Name, VariantNames};
use payload::{EncryptedOutgoingPayload, PayloadDecoder};
use tracing::{debug, error, trace, warn};

use self::{
    cipher::PayloadCipher,
    frame::FrameEncoder as _,
    handshake_messages::{Extensions, ServerChallengeResponse, ServerHello},
    payload::{IncomingPayload, OutgoingPayload},
};
use crate::{
    common::{
        ClientInfo, Cookie, CspDeviceId, ThreemaId,
        keys::{ClientKey, PublicKey},
    },
    crypto::x25519,
    utils::{
        bytes::{ByteReaderError, ByteWriterError},
        debug::debug_static_secret,
        sequence_numbers::{SequenceNumberOverflow, SequenceNumberU64},
    },
};

mod cipher;
pub mod frame;
mod handshake_messages;
pub mod payload;

/// An error occurred while running the Chat Server Protocol.
///
/// Note: Errors can occur when using the API incorrectly or when the remote server behaves incorrectly. None
/// of these errors are considered recoverable.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`CspProtocolError`].
/// 2. Abort the protocol due to `error`.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum CspProtocolError {
    /// Invalid parameter provided by foreign code.
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Invalid state for the requested operation.
    #[error("Invalid state: {0}")]
    InvalidState(&'static str),

    /// An internal error happened.
    #[error("Internal error: {0}")]
    InternalError(&'static str),

    /// Exhausted the available sequence numbers to use for sending/receiving payloads.
    ///
    /// Note: Only the post-handshake state should ever be able to produce this.
    #[error("Sequence number overflow happened")]
    SequenceNumberOverflow(#[from] SequenceNumberOverflow),

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

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(&'static str),
}

/// The context containing all parameters needed to initiate the protocol.
#[derive(Debug)]
pub struct CspProtocolContext {
    /// The server's permanent public keys.
    ///
    /// The first element is used as the server's primary public key and the remaining keys as
    /// fallback secondary keys. Using a secondary key will trigger a warning.
    permanent_server_keys: Vec<PublicKey>,

    /// The client's Threema ID
    identity: ThreemaId,

    /// The client's permanent private key
    client_key: ClientKey,

    /// Client info to be sent to the server during the handshake.
    ///
    /// See the protocol specification for the exact format.
    client_info: ClientInfo,

    /// The (optional) device cookie of the client's device
    device_cookie: Option<u16>,

    /// CSP device ID, randomly generated once for the associated multi-device group.
    csp_device_id: Option<CspDeviceId>,
}

impl CspProtocolContext {
    /// Initialize and validate a new context.
    ///
    /// The first element in `permanent_server_keys` is used as the server's primary public key and
    /// the remaining keys as fallback secondary keys.
    ///
    /// # Errors
    ///
    /// Returns an error if `permanent_server_keys` is empty.
    pub fn new(
        permanent_server_keys: Vec<PublicKey>,
        identity: ThreemaId,
        client_key: ClientKey,
        client_info: ClientInfo,
        device_cookie: Option<u16>,
        csp_device_id: Option<CspDeviceId>,
    ) -> Result<Self, CspProtocolError> {
        if permanent_server_keys.is_empty() {
            return Err(CspProtocolError::InvalidParameter(
                "permanent_server_keys must contain at least one public key.",
            ));
        }

        Ok(Self {
            permanent_server_keys,
            identity,
            client_key,
            client_info,
            device_cookie,
            csp_device_id,
        })
    }
}

/// Temporary client key (TCK)
#[derive(Educe)]
#[educe(Debug)]
struct TemporaryClientKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);

/// Temporary server key (TSK)
#[derive(Debug)]
struct TemporaryServerKey(PublicKey);

/// Client sequence number (CSN)
#[derive(Debug)]
struct ClientSequenceNumber(SequenceNumberU64);

/// Server sequence number (SSN)
#[derive(Debug)]
struct ServerSequenceNumber(SequenceNumberU64);

/// Client connection cookie (CCK)
#[derive(Debug, Clone, Copy, ConstantTimeEq)]
struct ClientCookie(Cookie);

/// Server connection cookie (SCK)
#[derive(Debug, Clone, Copy, ConstantTimeEq)]
struct ServerCookie(Cookie);

/// The state to await the server hello.
struct AwaitingServerHelloState {
    temporary_client_key: TemporaryClientKey,
    client_cookie: ClientCookie,
    decoder: ServerHelloDecoder,
}

struct AwaitingLoginAckState {
    decoder: LoginAckDecoder,
    cipher: LoginAckCipher,
}

/// The state after the handshake has completed
struct PostHandshakeState {
    decoder: PayloadDecoder,
    cipher: PayloadCipher,
}

/// A state in the chat server protocol.
///
/// The flow is as follows:
///
/// ```txt
/// [Start] --> AwaitingServerHello +-> AwaitingLoginAck +-> Post-Handshake <--+
///                                 |                    |                 \___/
///                                 +--------------------+-> Error <-------/
/// ```
///
/// To advance the state, call the corresponding poll function, e.g.,
/// [`State::poll_awaiting_server_hello`] to advance _out of_ the [`State::AwaitingServerHello`].
#[derive(DebugVariantNames, VariantNames)]
enum State {
    /// Sent the client hello, next incoming message should be the server hello
    AwaitingServerHello(AwaitingServerHelloState),

    /// Sent the login, next incoming message should be the login ack
    AwaitingLoginAck(AwaitingLoginAckState),

    /// Already completed, following messages should be incoming payload messages
    PostHandshake(PostHandshakeState),

    /// An unrecoverable error has happened, this state can never be left again. Close the
    /// connection and restart the protocol.
    Error(CspProtocolError),
}

impl State {
    /// Try to advance the state out of the [`AwaitingServerHelloState`] to
    /// [`State::AwaitingLoginAck`] and return the next instruction to be executed by the client.
    ///
    /// The server hello message should be contained in the `decoder`. Otherwise, return the
    /// [`State::AwaitingServerHello`] again and set the [`CspProtocolInstruction`] to all `None`
    /// values (reflecting that the caller must just wait).
    ///
    /// # Errors
    ///
    /// Returns [`CspProtocolError`] if the `server-hello` or any of its containing parts could not
    /// be decrypted or decoded.
    fn poll_awaiting_server_hello(
        context: &CspProtocolContext,
        mut state: AwaitingServerHelloState,
    ) -> Result<(Self, Option<CspProtocolInstruction>), CspProtocolError> {
        // Poll the `server-hello`
        trace!(decoder = ?state.decoder, "Poll");
        let Some(server_hello) = state
            .decoder
            .next_and_then(|server_hello| ServerHello::from(server_hello))
        else {
            return Ok((Self::AwaitingServerHello(state), None));
        };

        // Decrypt and decode the contained `server-challenge-response`
        let mut server_sequence_number = ServerSequenceNumber(SequenceNumberU64::new(1));
        let (selected_permanent_server_key, server_challenge_response) = {
            let (permanent_server_key, server_challenge_response) = decrypt_server_challenge_response(
                context,
                &state.temporary_client_key,
                &server_hello.server_cookie,
                &mut server_sequence_number,
                server_hello.server_challenge_response_box.to_vec(),
            )?;
            (
                permanent_server_key,
                ServerChallengeResponse::try_from(server_challenge_response.as_slice())?,
            )
        };
        debug!("Received server-hello message");
        trace!(?server_challenge_response);

        // Sanity checks on server connection cookie and the repeated client connection cookie
        if server_hello.server_cookie.0 == state.client_cookie.0 {
            let message = "SCK must not be equal to CCK";
            warn!(
                identical_cookies = %state.client_cookie.0,
                message
            );
            return Err(CspProtocolError::ServerError(message));
        }
        if server_challenge_response.repeated_client_cookie != state.client_cookie {
            let message = "Provided CCK does not match";
            warn!(
                expected_client_cookie = %state.client_cookie.0,
                received_client_cookie = %server_challenge_response.repeated_client_cookie.0,
                message
            );
            return Err(CspProtocolError::ServerError(message));
        }

        // Encode and encrypt the `login` message
        //
        // Note: This looks a bit wonky because we need to encode the extensions first since the
        // extensions length must be included in the `login-data`. But `login-data` must be
        // encrypted before the extensions because it takes the first sequence number.
        debug!("Creating login message");
        let (session_cipher, login) = {
            let mut login_cipher = LoginCipher::new(
                &state.temporary_client_key,
                state.client_cookie,
                ClientSequenceNumber(SequenceNumberU64::new(1)),
                server_hello.server_cookie,
                server_sequence_number,
                server_challenge_response.temporary_server_key,
            );

            // Encode the extensions
            let (extensions, extensions_byte_length) = Extensions::new(context).encode()?;

            // Encode the `login-data`
            let login_data = LoginData {
                identity: context.identity,
                extensions_byte_length,
                repeated_server_cookie: server_hello.server_cookie,
                vouch: login_cipher.vouch_session(&context.client_key, &selected_permanent_server_key),
            };
            trace!(?login_data);
            let login_data = login_data.encode().to_vec();

            // Encrypt `login-data` and the extensions
            let LoginBoxes {
                login_data_box,
                extensions_box,
            } = login_cipher.encrypt_login(login_data, extensions)?;

            // Encode `login`
            let login = Login {
                login_data_box: login_data_box.try_into().map_err(|login_data_box: Vec<u8>| {
                    error!(
                        expected_length = Login::LOGIN_DATA_BOX_LENGTH,
                        actual_length = login_data_box.len(),
                        "Unexpected login-data length"
                    );
                    CspProtocolError::InternalError(formatcp!(
                        "Encoded login-data was expected to be {} bytes",
                        Login::LOGIN_DATA_BOX_LENGTH,
                    ))
                })?,
                extensions_box,
            };
            trace!(?login);

            (login_cipher.dissolve(), login)
        };

        // Set and return the next state and instruction
        let state = State::AwaitingLoginAck(AwaitingLoginAckState {
            decoder: LoginAckDecoder::new_with_data(state.decoder.dissolve()),
            cipher: LoginAckCipher::new(session_cipher),
        });
        let instruction = CspProtocolInstruction {
            state_update: Some(CspStateUpdate::AwaitingLoginAck),
            outgoing_frame: Some(login.encode_to_frame()?),
            incoming_payload: None,
        };
        debug!(?state, "Changing state");
        Ok((state, Some(instruction)))
    }

    /// Try to advance the state out of the [`AwaitingLoginAckState`] to [`State::PostHandshake`] and return
    /// the next instruction to be executed by the client.
    ///
    /// The login ack message should be contained in the `decoder`. Otherwise, return the
    /// [`State::AwaitingLoginAck`] again and set the [`CspProtocolInstruction`] to all `None` values
    /// (reflecting that the caller must just wait).
    ///
    /// # Errors
    ///
    /// [`CspProtocolError`] if the `login-ack` or any of its containing parts could not be decrypted or
    /// decoded.
    fn poll_awaiting_login_ack(
        mut state: AwaitingLoginAckState,
    ) -> Result<(Self, Option<CspProtocolInstruction>), CspProtocolError> {
        // Poll the `login-ack`
        trace!(decoder = ?state.decoder, "Poll");
        let Some(login_ack) = state.decoder.next_and_then(|login_ack| LoginAck::from(login_ack)) else {
            return Ok((Self::AwaitingLoginAck(state), None));
        };

        // Decrypt and decode the contained `login-ack-data`
        let login_ack_data = state.cipher.decrypt(login_ack.login_ack_data_box.to_vec())?;
        let login_ack_data = LoginAckData::try_from(login_ack_data.as_ref())?;
        debug!("Received login-ack message");
        trace!(?login_ack_data);

        // Set and return the next state and instruction
        let state = State::PostHandshake(PostHandshakeState {
            decoder: PayloadDecoder::new(state.decoder.dissolve()),
            cipher: PayloadCipher::new(state.cipher.dissolve()),
        });
        let instruction = CspProtocolInstruction {
            state_update: Some(CspStateUpdate::PostHandshake {
                queued_messages: login_ack_data.queued_messages as usize,
            }),
            outgoing_frame: None,
            incoming_payload: None,
        };
        debug!(?state, "Changing state");
        Ok((state, Some(instruction)))
    }

    /// Try to loop over the [`PostHandshakeState`] and return [`State::PostHandshake`] as well as
    /// the the next instruction to be executed by the client.
    ///
    /// The `decoder` should contain the next frame.
    /// Otherwise, return the [`State::PostHandshake`] again and set the
    /// [`CspProtocolInstruction`] to all `None` values (reflecting that the caller must just wait).
    ///
    /// # Errors
    ///
    /// - [`CspProtocolError`] if the incoming payload could not be decrypted or decoded.
    fn poll_post_handshake(
        mut state: PostHandshakeState,
    ) -> Result<(Self, Option<CspProtocolInstruction>), CspProtocolError> {
        // Poll for a payload
        trace!(decoder = ?state.decoder, "Poll");
        let Some(payload) = state.decoder.next_frame_and_then(<[u8]>::to_vec) else {
            return Ok((Self::PostHandshake(state), None));
        };

        // Decrypt and decode the payload according to its type
        let payload = state.cipher.decrypt_payload(payload)?;
        trace!(payload = HEXLOWER.encode(&payload), "Raw payload");
        let payload = IncomingPayload::try_from(payload)?;
        debug!(?payload, "Received payload");

        // Set and return the next state and instruction
        let instruction = CspProtocolInstruction {
            state_update: None,
            outgoing_frame: None,
            incoming_payload: Some(payload),
        };
        Ok((State::PostHandshake(state), Some(instruction)))
    }
}

/// A CSP state update to indicate advancing
#[derive(Debug)]
pub enum CspStateUpdate {
    /// The client hello was sent successfully,
    AwaitingLoginAck,

    /// The handshake was successful
    PostHandshake {
        /// Amount of queued messages on the server for the client.
        queued_messages: usize,
    },
}

/// An instruction of what to do next.
///
/// When handling this instruction, run the following steps:
///
/// 1. If the current phase is the _handshake phase_:
///    1. If `incoming_payload` is present, abort the protocol due to an error and abort these steps.
///    2. If `outgoing_frame` is present, enqueue it to be sent to the chat server.
/// 2. If the current phase is the _payload phase_:
///    1. If `state_update` is present, abort the protocol due to an error and abort these steps.
///    2. If `outgoing_frame` is present, enqueue it to be sent to the chat server.
///    3. If `incoming_payload` is present, hand it off to the application.
/// 3. (Unreachable)
pub struct CspProtocolInstruction {
    /// The state to which the CSP protocol was advanced to
    pub state_update: Option<CspStateUpdate>,

    /// The outgoing frame that should be sent to the server
    pub outgoing_frame: Option<OutgoingFrame>,

    /// The incoming payload that should be processed by the client
    pub incoming_payload: Option<IncomingPayload>,
}

/// The Chat Server Protocol state machine.
///
/// The protocol state machine can be constructed once a connection to the chat server has been
/// established via [`CspProtocol::new`].
///
/// Any interaction with the protocol state machine that changes the internal state will yield a
/// [`CspProtocolInstruction`] that must be handled according to its documentation.
///
/// The protocol goes through exactly two phases:
///
/// - The _handshake phase_.
/// - The _payload phase_ where payloads are being exchanged.
///
/// When the connection to the chat server has been closed:
///
/// 1. Let `cause` be an error or any information associated to the close event.
/// 2. Log the protocol abort due to `cause` as a notice or an error respectively.
/// 3. Tear down the protocol state machine.
///
/// The flow of the state machine is as follows:
///
/// 1. Run [`CspProtocol::new`] to initiate a new state machine and send the resulting [`OutgoingFrame`] to
///    the chat server.
/// 2. Run the following steps in a loop:
///    1. Run the following steps in a loop:
///       1. Let `n_bytes` be the result of [`CspProtocol::next_required_length`].
///       2. If `n_bytes` is `0`, break this loop.
///       3. Forward at least `n_bytes` to the protocol via [`CspProtocol::add_chunks`] in a single or
///          multiple consecutive calls.
///    2. Run [`CspProtocol::poll`] and let `instruction` be the resulting [`CspProtocolInstruction`].
///    3. Handle `instruction`. If `instruction` yielded a [`CspStateUpdate::PostHandshake`] state, break this
///       loop.
/// 3. Run the following steps in a loop:
///    1. Run [`CspProtocol::poll`] and handle any instruction until it no longer produces one.
///    2. Wait for further input from either the chat server or a payload being created by the application.
///       1. If data has been received from the chat server, add it via [`CspProtocol::add_chunks`].
///       2. If a payload is being created, run [`CspProtocol::create_payload`] and handle the resulting
///          instruction.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct CspProtocol {
    #[educe(Debug(ignore))]
    context: CspProtocolContext,
    state: State,
}

impl CspProtocol {
    /// Initiate a new CSP protocol and also return [`OutgoingFrame`] set to the client hello.
    ///
    /// # Panics
    ///
    /// If the `client-hello` could not be encoded to a frame.
    #[tracing::instrument(skip_all)]
    pub fn new(context: CspProtocolContext) -> (Self, OutgoingFrame) {
        debug!("Creating CSP protocol");

        // Generate the temporary client key (TCK) and the client cookie (CCK)
        let temporary_client_key =
            TemporaryClientKey(x25519::StaticSecret::random_from_rng(rand::thread_rng()));
        let temporary_client_key_public = PublicKey::from(&temporary_client_key.0);
        let client_cookie = ClientCookie(Cookie::random());

        // Generate the outgoing `client-hello` message
        let client_hello = ClientHello {
            temporary_client_key_public,
            client_cookie,
        };
        trace!(?client_hello);
        let outgoing_frame = client_hello
            .encode_to_frame()
            .expect("ClientHello must be encodable");

        // Create initial state
        let state = State::AwaitingServerHello(AwaitingServerHelloState {
            temporary_client_key,
            client_cookie,
            decoder: ServerHelloDecoder::new(),
        });
        debug!(?state, "Starting with initial state");
        (Self { context, state }, outgoing_frame)
    }

    /// Poll to advance the state.
    ///
    /// If this returns [`None`], then the state machine will not produce any more
    /// [`CspProtocolInstruction`]s until further input is being provided.
    ///
    /// # Errors
    ///
    /// Returns [`CspProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<Option<CspProtocolInstruction>, CspProtocolError> {
        let poll_result = match mem::replace(
            &mut self.state,
            State::Error(CspProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                CspProtocol::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::AwaitingServerHello(state) => State::poll_awaiting_server_hello(&self.context, state),
            State::AwaitingLoginAck(state) => State::poll_awaiting_login_ack(state),
            State::PostHandshake(state) => State::poll_post_handshake(state),
        };
        match poll_result {
            Ok((state, instruction)) => {
                self.state = state;
                Ok(instruction)
            },
            Err(error) => {
                self.state = State::Error(error.clone());
                Err(error)
            },
        }
    }

    /// Add chunks received from the chat server. The chunks may or may not contain complete frames
    /// or even contain multiple complete frames.
    ///
    /// # Errors
    ///
    /// Returns [`CspProtocolError::InvalidState`] if the protocol is in the error state.
    #[tracing::instrument(skip_all, fields(
        ?self,
        chunks_byte_length = chunks.iter().map(|chunk| chunk.len()).sum::<usize>(),
    ))]
    pub fn add_chunks(&mut self, chunks: &[&[u8]]) -> Result<(), CspProtocolError> {
        trace!("Adding chunks");
        match &mut self.state {
            State::AwaitingServerHello(state) => {
                state.decoder.add_chunks(chunks);
                Ok(())
            },
            State::AwaitingLoginAck(state) => {
                state.decoder.add_chunks(chunks);
                Ok(())
            },
            State::PostHandshake(state) => {
                let _ = state.decoder.add_chunks(chunks);
                Ok(())
            },
            State::Error(error) => {
                let message = "Cannot add chunks in error state";
                error!(?error, message);
                Err(CspProtocolError::InvalidState(message))
            },
        }
    }

    /// Get the required number of bytes for the current state's decoder to advance the decoder's
    /// internal state.
    ///
    /// Note: An efficient implementation may always provide more than the required amount of bytes,
    /// if already available.
    ///
    /// # Errors
    ///
    /// Returns [`CspProtocolError::InvalidState`] if the protocol is in the error state.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn next_required_length(&self) -> Result<usize, CspProtocolError> {
        let required_length = match &self.state {
            State::AwaitingServerHello(state) => state.decoder.required_length(),
            State::AwaitingLoginAck(state) => state.decoder.required_length(),
            State::PostHandshake(state) => state.decoder.required_length(),
            State::Error(error) => {
                let message = "Cannot query next required length in error state";
                error!(?error, message);
                return Err(CspProtocolError::InvalidState(message));
            },
        };
        trace!(required_length);
        Ok(required_length)
    }

    /// Create an outgoing payload.
    ///
    /// # Errors
    ///
    /// Returns [`CspProtocolError::InvalidState`] if the protocol is not in the post-handshake
    /// state.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn create_payload(
        &mut self,
        payload: &OutgoingPayload,
    ) -> Result<CspProtocolInstruction, CspProtocolError> {
        // Ensure we're in the post-handshake state
        let State::PostHandshake(state) = &mut self.state else {
            let message = "Creating an outgoing payload requires the post-handshake state";
            error!(message);
            return Err(CspProtocolError::InvalidState(message));
        };

        // Encode and encrypt the payload into an outgoing frame
        debug!("Creating payload");
        let payload = payload.encode()?;
        let payload = state.cipher.encrypt_payload(payload)?;
        let outgoing_frame = EncryptedOutgoingPayload(payload).encode_to_frame()?;

        // Done
        Ok(CspProtocolInstruction {
            state_update: None,
            outgoing_frame: Some(outgoing_frame),
            incoming_payload: None,
        })
    }
}
