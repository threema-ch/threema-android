use std::collections::HashMap;

use libthreema_macros::{DebugVariantNames, VariantNames};
use prost::Message as _;
use rand::{self, Rng as _};
use tracing::{debug, trace, warn};
use zeroize::{Zeroize, ZeroizeOnDrop};

use self::frame::IncomingFrameDecoder;
pub use self::frame::{FrameDecoderError, IncomingFrame, OutgoingFrame};
use crate::{
    crypto::x25519,
    protobuf::d2d_rendezvous as protobuf,
    time::{Duration, Instant},
};

/// Incoming/Outgoing frame utilities.
mod frame;

/// Shared encryption/decryption utilities for RIDAK/RRDAK and RIDTK/RRDTK.
mod rxdxk;

/// Rendezvous Initiator/Responder Device Authentication Key (RIDAK/RRDAK) utilities.
mod rxdak;

/// Rendezvous Initiator/Responder Device Transport Key (RIDTK/RRDTK) utilities.
mod rxdtk;

/// An error occurred while running the Connection Rendezvous Protocol.
///
/// Note: Errors can occur when using the API incorrectly or when the remote party behaves
/// incorrectly. Since the Connection Rendezvous Protocol is short-lived and all involved parties
/// are required to behave on all paths, none of these errors are considered recoverable.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`RendezvousProtocolError`].
/// 2. Abort the protocol due to `error`.
#[derive(Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum RendezvousProtocolError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Exhausted the available sequence numbers to use for sending/receiving frames.
    #[error("Sequence number would overflow")]
    SequenceNumberOverflow,

    /// An error occurred while decoding an incoming frame.
    #[error("Frame decoding failed: {0}")]
    FrameDecodeFailed(#[from] FrameDecoderError),

    /// Unable to decrypt a frame's payload.
    #[error("Decryption failed")]
    DecryptionFailed,

    /// Unable to encrypt a frame's payload.
    #[error("Encryption failed")]
    EncryptionFailed,

    /// Unable to decode a protobuf message.
    #[error("Decoding failed")]
    ProtobufDecodeFailed(#[from] prost::DecodeError),

    /// Incoming RRD's `Hello` message is invalid.
    #[error("Invalid RRD Hello message: {0}")]
    InvalidRrdHelloMessage(String),

    /// Incoming RID's `AuthHello` message is invalid.
    #[error("Invalid RID AuthHello message: {0}")]
    InvalidRidAuthHelloMessage(String),

    /// Incoming RRD's `Auth` message is invalid.
    #[error("Invalid RRD Auth message: {0}")]
    InvalidRrdAuthMessage(String),

    /// Unexpected frame received (e.g. during the nomination phase where only one role is allowed
    /// to send frames).
    #[error("Frame received unexpectedly")]
    UnexpectedFrame,

    /// Unable to find the referenced path. It was either never created or already dropped due to
    /// nomination of another path.
    #[error("Unknown or dropped path with PID {0}")]
    UnknownOrDroppedPath(u32),

    /// The referenced path has been closed (most likely due to a previous error encountered on the
    /// path).
    #[error("Path with PID {0} is closed")]
    PathClosed(u32),

    /// The local role does not allow for nomination.
    #[error("Nomination is not allowed for the local role")]
    NominateNotAllowed,

    /// Nomination already occurred for a path.
    #[error("Nomination already occurred for PID {0}")]
    NominationAlreadyDone(u32),

    /// Nomination is not allowed in the current state.
    #[error("Nomination is not allowed in state '{0}'")]
    InvalidStateForNomination(&'static str),

    /// Nomination is required before sending ULP data.
    #[error("Nomination is required before sending ULP data")]
    NominationRequired,
}

/// Authentication Key (AK).
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct AuthenticationKey(pub [u8; 32]);

/// Rendezvous Path Hash (RPH), derived from the Shared Transport Key (STK).
pub struct RendezvousPathHash(pub [u8; 32]);

/// A path state update.
#[derive(DebugVariantNames, VariantNames)]
pub enum PathStateUpdate {
    /// The handshake on this path was successful and is await nomination (or being dropped).
    AwaitingNominate {
        /// RTT measured during the handshake, to be used by the nominator to select a path.
        measured_rtt: Duration,
    },

    /// The path has been nominated, allowing for ULP frames to be exchanged.
    Nominated {
        /// The Rendezvous Path Hash (RPH) of the nominated path.
        rph: RendezvousPathHash,
    },
}

/// Result returned when interacting with the protocol state machine. The result is associated to
/// the path whose PID was used when calling a function that yielded this result.
///
/// When handling this result, run the following steps:
///
/// 1. Let `path` be the context of the path whose PID was used in the function call that yielded
///    this result.
/// 2. If the current phase is the _handshake and nominaton phase_:
///    1. If `incoming_ulp_data` is present, abort the protocol due to an error and abort these
///       steps.
///    2. If `outgoing_frame` is present, enqueue it to be sent on `path`.
///    3. If `state_update` is [`PathStateUpdate::AwaitingNominate`] and the protocol took the role
///       of the nominator, run the _Path Awaiting Nomination Steps_ with `path` and abort these
///       steps.
///    4. If `state_update` is [`PathStateUpdate::Nominated`]:
///       1. Mark `path` as _nominated_.
///       2. If the protocol took the role of the nominee, mark all other paths except `path` as
///          _disregarded_ and close them (for WebSocket, use close code `1000`).
///       3. If the protocol took the rule of the nominator, mark all other paths except `path` as
///          _disregarded_ but keep them open with a timeout of 15s after which they should be
///          closed (for WebSocket, use close code `1000`).[^close-race]
///       4. Enter the _ULP phase_. At this point, the ULP may start creating frames through
///          [`RendezvousProtocol::create_ulp_frame`].
///    5. (Unreachable)
/// 3. If the current phase is the _ULP phase_:
///    1. If `path` is not marked as _nominated_, abort the protocol due to an error and abort these
///       steps.
///    2. If `state_update` is present, abort the protocol due to an error and abort these steps.
///    3. If `outgoing_frame` is present, enqueue it to be sent on `path`.
///    4. If `incoming_ulp_data` is present, hand it off to the ULP.
/// 4. (Unreachable)
///
/// [^close-race]: This prevents a race condition between RID nominating a path and path close
/// detection on RRD's side.
#[derive(Debug)]
pub struct PathProcessResult {
    /// The path's state updated.
    pub state_update: Option<PathStateUpdate>,
    /// An outgoing frame is ready to be sent on the path.
    pub outgoing_frame: Option<OutgoingFrame>,
    /// An incoming frame has been reassembled and is ready to be handed off to the ULP.
    pub incoming_ulp_data: Option<Vec<u8>>,
}

/// 16 byte random authentication challenge
struct Challenge([u8; 16]);
impl Challenge {
    fn random() -> Self {
        let mut challenge = Self([0_u8; 16]);
        rand::thread_rng().fill(&mut challenge.0);
        challenge
    }
}

/// Ephemeral transport key (ETK).
struct EphemeralTransportKey(x25519::SharedSecretHSalsa20);

/// Protocol context passed around to the various roles and states.
struct Context {
    is_nominator: bool,
    ak: AuthenticationKey,
}
impl Context {
    fn new(is_nominator: bool, ak: AuthenticationKey) -> Self {
        Self { is_nominator, ak }
    }
}

/// Shared path attributes.
struct BasePath {
    pid: u32,
    decoder: IncomingFrameDecoder,
}
impl BasePath {
    fn new(pid: u32) -> Self {
        Self {
            pid,
            decoder: IncomingFrameDecoder::new(),
        }
    }

    fn decode_next(&mut self, nominated: bool) -> Result<Option<IncomingFrame>, FrameDecoderError> {
        // During the handshake and before nomination, we don't expect frames larger than 16 KiB.
        // After the handshake, we don't expect frames larger than 100 MiB. (If we ever need larger
        // frames than this, we need to add Blob chunking and stream the content.)
        let max_frame_length = if nominated { 100 * 1_048_576 } else { 16_384 };

        // Decode and process the next frame, if any can be decoded
        self.decoder.decode_next(max_frame_length)
    }
}

/// Path states of RID.
#[derive(VariantNames, DebugVariantNames)]
enum RidPathState {
    /// Briefly used internally when moving from one state to another.
    Invalid,

    /// Awaiting an `RrdToRid.Hello` to start the handshake.
    AwaitingHello { authentication_keys: rxdak::ForRid },

    /// Sent an `RidToRrd.AuthHello`, awaiting an `RrdToRid.Auth`.
    AwaitingAuth {
        authentication_keys: rxdak::ForRid,
        sent_at: Instant,
        local_challenge: Challenge,
        shared_etk: EphemeralTransportKey,
    },

    /// Expecting a `Nominate` to be sent or received (depending on the configuration).
    AwaitingNominate {
        transport_keys: rxdtk::ForRid,
        rph: RendezvousPathHash,
    },

    /// The connection path was `Nominate`d and can now be used by the ULP.
    Nominated { transport_keys: rxdtk::ForRid },

    /// The connection path closed.
    Closed,
}

/// RID path protocol.
struct RidPath {
    path: BasePath,
    state: RidPathState,
}
impl RidPath {
    fn new(ak: &AuthenticationKey, pid: u32) -> Self {
        Self {
            path: BasePath::new(pid),
            state: RidPathState::AwaitingHello {
                authentication_keys: rxdak::ForRid::new(ak, pid),
            },
        }
    }

    fn process_frame(
        &mut self,
        ctx: &Context,
        mut incoming_frame: IncomingFrame,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        trace! { state = ?self.state, ?incoming_frame, "Processing frame" }
        if let RidPathState::Nominated { transport_keys } = &mut self.state {
            // Handle `Nominated` state where the transport can be used by the ULP.
            Self::handle_ulp_data(transport_keys, incoming_frame).map(|incoming_ulp_data| {
                PathProcessResult {
                    state_update: None,
                    outgoing_frame: None,
                    incoming_ulp_data: Some(incoming_ulp_data),
                }
            })
        } else {
            // Handle `Closed` state
            if let RidPathState::Closed = &self.state {
                return Err(RendezvousProtocolError::PathClosed(self.path.pid));
            }

            // Handle states that should immediately transition into another state
            //
            // IMPORTANT: All match arms must be infallible!
            match core::mem::replace(&mut self.state, RidPathState::Invalid) {
                RidPathState::AwaitingHello {
                    mut authentication_keys,
                } => {
                    // Handle `RrdToRid.Hello`, create `RidToRrd.AuthHello` and update state.
                    Self::handle_hello(&mut authentication_keys, &mut incoming_frame).map(
                        |(local_challenge, shared_etk, outgoing_frame)| {
                            (
                                RidPathState::AwaitingAuth {
                                    authentication_keys,
                                    sent_at: Instant::now(),
                                    local_challenge,
                                    shared_etk,
                                },
                                PathProcessResult {
                                    state_update: None,
                                    outgoing_frame: Some(outgoing_frame),
                                    incoming_ulp_data: None,
                                },
                            )
                        },
                    )
                },

                RidPathState::AwaitingAuth {
                    mut authentication_keys,
                    sent_at,
                    local_challenge,
                    shared_etk,
                } => {
                    // Calculate RTT
                    let measured_rtt = Instant::now().duration_since(sent_at);

                    // Handle `RrdToRid.Auth` and update state.
                    Self::handle_auth(
                        &mut authentication_keys,
                        &local_challenge,
                        &mut incoming_frame,
                    )
                    .map(|()| {
                        let (transport_keys, rph) =
                            rxdtk::ForRid::new(&ctx.ak, authentication_keys, shared_etk);
                        (
                            RidPathState::AwaitingNominate {
                                transport_keys,
                                rph,
                            },
                            PathProcessResult {
                                state_update: Some(PathStateUpdate::AwaitingNominate {
                                    measured_rtt,
                                }),
                                outgoing_frame: None,
                                incoming_ulp_data: None,
                            },
                        )
                    })
                },

                RidPathState::AwaitingNominate {
                    mut transport_keys,
                    rph,
                } => {
                    // Check if the remote side is allowed to `Nominate`.
                    if ctx.is_nominator {
                        return Err(RendezvousProtocolError::UnexpectedFrame);
                    }

                    // Handle `Nominate` and update state.
                    Self::handle_nominate(&mut transport_keys, &mut incoming_frame).map(|()| {
                        (
                            RidPathState::Nominated { transport_keys },
                            PathProcessResult {
                                state_update: Some(PathStateUpdate::Nominated { rph }),
                                outgoing_frame: None,
                                incoming_ulp_data: None,
                            },
                        )
                    })
                },

                // States that must have been covered by code above
                RidPathState::Invalid | RidPathState::Nominated { .. } | RidPathState::Closed => {
                    unreachable!("State should have been handled")
                },
            }
            .map(|(state, result)| {
                self.state = state;
                debug! { state = ?self.state, "Changed state" }
                result
            })
        }
        .map_err(|error| {
            self.state = RidPathState::Closed;
            warn! { ?error, state = ?self.state, "Closed due to error" }
            error
        })
    }

    fn nominate(&mut self) -> Result<PathProcessResult, RendezvousProtocolError> {
        // Ensure we are in the correct state to nominate
        if !matches!(&self.state, RidPathState::AwaitingNominate { .. }) {
            return Err(RendezvousProtocolError::InvalidStateForNomination(
                self.state.variant_name(),
            ));
        }

        // Nominate
        if let RidPathState::AwaitingNominate {
            mut transport_keys,
            rph,
        } = core::mem::replace(&mut self.state, RidPathState::Invalid)
        {
            Self::create_nominate(&mut transport_keys).map(|outgoing_frame| {
                (
                    RidPathState::Nominated { transport_keys },
                    PathProcessResult {
                        state_update: Some(PathStateUpdate::Nominated { rph }),
                        outgoing_frame: Some(outgoing_frame),
                        incoming_ulp_data: None,
                    },
                )
            })
        } else {
            unreachable!("Expected AwaitingNominate state")
        }
        .map(|(state, result)| {
            self.state = state;
            debug! { state = ?self.state, "Changed state" }
            result
        })
        .map_err(|error| {
            self.state = RidPathState::Closed;
            warn! { ?error, state = ?self.state, "Closed due to error" }
            error
        })
    }

    fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        match &mut self.state {
            RidPathState::Nominated { transport_keys } => {
                Self::create_ulp_data(transport_keys, outgoing_data).map(|outgoing_frame| {
                    PathProcessResult {
                        state_update: None,
                        outgoing_frame: Some(outgoing_frame),
                        incoming_ulp_data: None,
                    }
                })
            },
            _ => Err(RendezvousProtocolError::NominationRequired),
        }
    }

    fn handle_hello(
        keys: &mut rxdak::ForRid,
        incoming_frame: &mut IncomingFrame,
    ) -> Result<(Challenge, EphemeralTransportKey, OutgoingFrame), RendezvousProtocolError> {
        // Decrypt and decode into a `RrdToRid.Hello`
        let (remote_challenge, remote_etk) = {
            keys.rrdak.decrypt(&mut incoming_frame.0)?;
            let hello = protobuf::handshake::rrd_to_rid::Hello::decode(incoming_frame.0.as_ref())?;

            // Validate `RrdToRid.Hello`
            let remote_challenge =
                Challenge(hello.challenge.as_slice().try_into().map_err(|_| {
                    RendezvousProtocolError::InvalidRrdHelloMessage(format!(
                        "Expected 16 challenge bytes, got {}",
                        hello.challenge.len()
                    ))
                })?);
            let remote_etk = x25519::PublicKey::from(
                <[u8; 32]>::try_from(hello.etk.as_ref()).map_err(|_| {
                    RendezvousProtocolError::InvalidRrdHelloMessage(format!(
                        "Invalid remote ETK, got {} bytes",
                        hello.etk.len()
                    ))
                })?,
            );
            (remote_challenge, remote_etk)
        };

        // Encode and encrypt `RidToRrd.AuthHello`
        let (local_challenge, shared_etk, outgoing_frame) = {
            // Generate a challenge
            let local_challenge = Challenge::random();

            // Generate local part of ETK
            let local_etk = x25519::EphemeralSecret::random_from_rng(rand::thread_rng());

            // Encode and encrypt `RidToRrd.AuthHello`
            let local_auth_hello = protobuf::handshake::rid_to_rrd::AuthHello {
                response: remote_challenge.0.to_vec(),
                challenge: local_challenge.0.to_vec(),
                etk: x25519::PublicKey::from(&local_etk).as_bytes().to_vec(),
            };
            let mut outgoing_data = local_auth_hello.encode_to_vec();
            keys.ridak.encrypt(&mut outgoing_data)?;

            // Derive ETK
            let shared_etk = EphemeralTransportKey(local_etk.diffie_hellman(&remote_etk).into());

            (local_challenge, shared_etk, OutgoingFrame(outgoing_data))
        };

        // Done
        Ok((local_challenge, shared_etk, outgoing_frame))
    }

    fn handle_auth(
        keys: &mut rxdak::ForRid,
        local_challenge: &Challenge,
        incoming_frame: &mut IncomingFrame,
    ) -> Result<(), RendezvousProtocolError> {
        // Decrypt and decode into a `RrdToRid.Auth`
        keys.rrdak.decrypt(&mut incoming_frame.0)?;
        let remote_auth = protobuf::handshake::rrd_to_rid::Auth::decode(incoming_frame.0.as_ref())?;

        // Validate `RrdToRid.Auth`
        if remote_auth.response.as_ref() != local_challenge.0 {
            return Err(RendezvousProtocolError::InvalidRrdAuthMessage(format!(
                "Challenge response of {} bytes does not match",
                remote_auth.response.len()
            )));
        }

        // Done
        Ok(())
    }

    fn create_nominate(keys: &mut rxdtk::ForRid) -> Result<OutgoingFrame, RendezvousProtocolError> {
        // Encode and encrypt a `Nominate`
        let local_nominate = protobuf::Nominate {};
        let mut outgoing_data = local_nominate.encode_to_vec();
        keys.ridtk.encrypt(&mut outgoing_data)?;
        Ok(OutgoingFrame(outgoing_data))
    }

    fn handle_nominate(
        keys: &mut rxdtk::ForRid,
        incoming_frame: &mut IncomingFrame,
    ) -> Result<(), RendezvousProtocolError> {
        // Decrypt and decode into a `Nominate`
        keys.rrdtk.decrypt(&mut incoming_frame.0)?;
        let _ = protobuf::Nominate::decode(incoming_frame.0.as_ref())?;
        Ok(())
    }

    fn create_ulp_data(
        keys: &mut rxdtk::ForRid,
        mut outgoing_data: Vec<u8>,
    ) -> Result<OutgoingFrame, RendezvousProtocolError> {
        // Encode and encrypt ULP data
        keys.ridtk.encrypt(&mut outgoing_data)?;
        Ok(OutgoingFrame(outgoing_data))
    }

    fn handle_ulp_data(
        keys: &mut rxdtk::ForRid,
        mut incoming_frame: IncomingFrame,
    ) -> Result<Vec<u8>, RendezvousProtocolError> {
        // Decrypt and decode ULP data
        keys.rrdtk.decrypt(&mut incoming_frame.0)?;
        Ok(incoming_frame.0)
    }
}

/// Path states of RID.
#[derive(VariantNames, DebugVariantNames)]
enum RrdPathState {
    /// Briefly used internally when moving from one state to another.
    Invalid,

    /// Sent an `RrdtoRid.Hello`, awaiting an `RidToRrd.AuthHello`.
    AwaitingAuthHello {
        authentication_keys: rxdak::ForRrd,
        sent_at: Instant,
        local_challenge: Challenge,
        local_etk: x25519::EphemeralSecret,
    },

    /// Expecting a `Nominate` to be sent or received (depending on the configuration).
    AwaitingNominate {
        transport_keys: rxdtk::ForRrd,
        rph: RendezvousPathHash,
    },

    /// The connection path was `Nominate`d and can now be used by the ULP.
    Nominated { transport_keys: rxdtk::ForRrd },

    /// The connection path closed.
    Closed,
}

/// RRD path protocol.
struct RrdPath {
    path: BasePath,
    state: RrdPathState,
}
impl RrdPath {
    fn new(ak: &AuthenticationKey, pid: u32) -> (Self, OutgoingFrame) {
        // Create initial state
        let mut authentication_keys = rxdak::ForRrd::new(ak, pid);
        let (local_challenge, local_etk, outgoing_frame) =
            Self::create_hello(&mut authentication_keys);

        // Create path
        let path = Self {
            path: BasePath::new(pid),
            state: RrdPathState::AwaitingAuthHello {
                authentication_keys,
                sent_at: Instant::now(),
                local_challenge,
                local_etk,
            },
        };
        (path, outgoing_frame)
    }

    fn process_frame(
        &mut self,
        ctx: &Context,
        mut incoming_frame: IncomingFrame,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        trace! { state = ?self.state, ?incoming_frame, "Processing frame" }
        if let RrdPathState::Nominated { transport_keys } = &mut self.state {
            // Handle `Nominated` state where the transport can be used by the ULP.
            Self::handle_ulp_data(transport_keys, incoming_frame).map(|incoming_ulp_data| {
                PathProcessResult {
                    state_update: None,
                    outgoing_frame: None,
                    incoming_ulp_data: Some(incoming_ulp_data),
                }
            })
        } else {
            // Handle `Closed` state
            if let RrdPathState::Closed = &self.state {
                return Err(RendezvousProtocolError::PathClosed(self.path.pid));
            }

            // Handle states that should immediately transition into another state
            //
            // IMPORTANT: All match arms must be infallible!
            match core::mem::replace(&mut self.state, RrdPathState::Invalid) {
                RrdPathState::AwaitingAuthHello {
                    mut authentication_keys,
                    sent_at,
                    local_challenge,
                    local_etk,
                } => {
                    // Calculate RTT
                    let measured_rtt = Instant::now().duration_since(sent_at);

                    // Handle `RidToRrd.AuthHello` and update state.
                    Self::handle_auth_hello(
                        &mut authentication_keys,
                        &local_challenge,
                        local_etk,
                        &mut incoming_frame,
                    )
                    .map(|(shared_etk, outgoing_frame)| {
                        let (transport_keys, rph) =
                            rxdtk::ForRrd::new(&ctx.ak, authentication_keys, shared_etk);
                        (
                            RrdPathState::AwaitingNominate {
                                transport_keys,
                                rph,
                            },
                            PathProcessResult {
                                state_update: Some(PathStateUpdate::AwaitingNominate {
                                    measured_rtt,
                                }),
                                outgoing_frame: Some(outgoing_frame),
                                incoming_ulp_data: None,
                            },
                        )
                    })
                },

                RrdPathState::AwaitingNominate {
                    mut transport_keys,
                    rph,
                } => {
                    // Check if the remote side is allowed to `Nominate`.
                    if ctx.is_nominator {
                        return Err(RendezvousProtocolError::UnexpectedFrame);
                    }

                    // Handle `Nominate` and update state.
                    Self::handle_nominate(&mut transport_keys, &mut incoming_frame).map(|()| {
                        (
                            RrdPathState::Nominated { transport_keys },
                            PathProcessResult {
                                state_update: Some(PathStateUpdate::Nominated { rph }),
                                outgoing_frame: None,
                                incoming_ulp_data: None,
                            },
                        )
                    })
                },

                // States that must have been covered by code above
                RrdPathState::Invalid | RrdPathState::Nominated { .. } | RrdPathState::Closed => {
                    unreachable!("State should have been handled")
                },
            }
            .map(|(state, result)| {
                self.state = state;
                debug! { state = ?self.state, "Changed state" }
                result
            })
        }
        .map_err(|error| {
            self.state = RrdPathState::Closed;
            warn! { ?error, state = ?self.state, "Closed due to error" }
            error
        })
    }

    fn nominate(&mut self) -> Result<PathProcessResult, RendezvousProtocolError> {
        // Ensure we are in the correct state to nominate
        if !matches!(&self.state, RrdPathState::AwaitingNominate { .. }) {
            return Err(RendezvousProtocolError::InvalidStateForNomination(
                self.state.variant_name(),
            ));
        }

        // Nominate
        if let RrdPathState::AwaitingNominate {
            mut transport_keys,
            rph,
        } = core::mem::replace(&mut self.state, RrdPathState::Invalid)
        {
            Self::create_nominate(&mut transport_keys).map(|outgoing_frame| {
                (
                    RrdPathState::Nominated { transport_keys },
                    PathProcessResult {
                        state_update: Some(PathStateUpdate::Nominated { rph }),
                        outgoing_frame: Some(outgoing_frame),
                        incoming_ulp_data: None,
                    },
                )
            })
        } else {
            unreachable!("Expected AwaitingNominate state")
        }
        .map(|(state, result)| {
            self.state = state;
            debug! { state = ?self.state, "Changed state" }
            result
        })
        .map_err(|error| {
            self.state = RrdPathState::Closed;
            warn! { ?error, state = ?self.state, "Closed due to error" }
            error
        })
    }

    fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        match &mut self.state {
            RrdPathState::Nominated { transport_keys } => {
                Self::create_ulp_data(transport_keys, outgoing_data).map(|outgoing_frame| {
                    PathProcessResult {
                        state_update: None,
                        outgoing_frame: Some(outgoing_frame),
                        incoming_ulp_data: None,
                    }
                })
            },
            _ => Err(RendezvousProtocolError::NominationRequired),
        }
    }

    fn create_hello(
        keys: &mut rxdak::ForRrd,
    ) -> (Challenge, x25519::EphemeralSecret, OutgoingFrame) {
        // Generate a challenge
        let local_challenge = Challenge::random();

        // Generate local part of ETK
        let local_etk = x25519::EphemeralSecret::random_from_rng(rand::thread_rng());

        // Encode and encrypt `RrdToRid.Hello`
        let local_hello = protobuf::handshake::rrd_to_rid::Hello {
            challenge: local_challenge.0.to_vec(),
            etk: x25519::PublicKey::from(&local_etk).as_bytes().to_vec(),
        };
        let mut outgoing_data = local_hello.encode_to_vec();
        keys.rrdak
            .encrypt(&mut outgoing_data)
            .expect("Encrypting initial RrdToRid.Hello should work");

        (local_challenge, local_etk, OutgoingFrame(outgoing_data))
    }

    fn handle_auth_hello(
        keys: &mut rxdak::ForRrd,
        local_challenge: &Challenge,
        local_etk: x25519::EphemeralSecret,
        incoming_frame: &mut IncomingFrame,
    ) -> Result<(EphemeralTransportKey, OutgoingFrame), RendezvousProtocolError> {
        // Decrypt and decode into a `RidToRrd.AuthHello`
        let (remote_challenge, remote_etk) = {
            keys.ridak.decrypt(&mut incoming_frame.0)?;
            let remote_auth_hello =
                protobuf::handshake::rid_to_rrd::AuthHello::decode(incoming_frame.0.as_ref())?;

            // Validate `RidToRrd.AuthHello`
            if remote_auth_hello.response != local_challenge.0 {
                return Err(RendezvousProtocolError::InvalidRidAuthHelloMessage(
                    format!(
                        "Challenge response of {} bytes does not match",
                        remote_auth_hello.response.len()
                    ),
                ));
            }
            let remote_challenge = Challenge(
                remote_auth_hello
                    .challenge
                    .as_slice()
                    .try_into()
                    .map_err(|_| {
                        RendezvousProtocolError::InvalidRrdHelloMessage(format!(
                            "Expected 16 challenge bytes, got {}",
                            remote_auth_hello.challenge.len()
                        ))
                    })?,
            );
            let remote_etk = x25519::PublicKey::from(
                <[u8; 32]>::try_from(remote_auth_hello.etk.as_ref()).map_err(|_| {
                    RendezvousProtocolError::InvalidRidAuthHelloMessage(format!(
                        "Invalid remote ETK, got {} bytes",
                        remote_auth_hello.etk.len()
                    ))
                })?,
            );
            (remote_challenge, remote_etk)
        };

        // Encode and encrypt `RrdToRid.Auth`
        let (shared_etk, outgoing_frame) = {
            // Encode and encrypt `RrdToRid.Auth`
            let local_auth = protobuf::handshake::rrd_to_rid::Auth {
                response: remote_challenge.0.to_vec(),
            };
            let mut outgoing_data = local_auth.encode_to_vec();
            keys.rrdak.encrypt(&mut outgoing_data)?;

            // Derive ETK
            let shared_etk = EphemeralTransportKey(local_etk.diffie_hellman(&remote_etk).into());

            (shared_etk, OutgoingFrame(outgoing_data))
        };

        // Done
        Ok((shared_etk, outgoing_frame))
    }

    fn create_nominate(keys: &mut rxdtk::ForRrd) -> Result<OutgoingFrame, RendezvousProtocolError> {
        // Encode and encrypt a `Nominate`
        let local_nominate = protobuf::Nominate {};
        let mut outgoing_data = local_nominate.encode_to_vec();
        keys.rrdtk.encrypt(&mut outgoing_data)?;
        Ok(OutgoingFrame(outgoing_data))
    }

    fn handle_nominate(
        keys: &mut rxdtk::ForRrd,
        incoming_frame: &mut IncomingFrame,
    ) -> Result<(), RendezvousProtocolError> {
        // Decrypt and decode into a `Nominate`
        keys.ridtk.decrypt(&mut incoming_frame.0)?;
        let _ = protobuf::Nominate::decode(incoming_frame.0.as_ref())?;
        Ok(())
    }

    fn create_ulp_data(
        keys: &mut rxdtk::ForRrd,
        mut outgoing_data: Vec<u8>,
    ) -> Result<OutgoingFrame, RendezvousProtocolError> {
        // Encode and encrypt ULP data
        keys.rrdtk.encrypt(&mut outgoing_data)?;
        Ok(OutgoingFrame(outgoing_data))
    }

    fn handle_ulp_data(
        keys: &mut rxdtk::ForRrd,
        mut incoming_frame: IncomingFrame,
    ) -> Result<Vec<u8>, RendezvousProtocolError> {
        // Decrypt and decode ULP data
        keys.ridtk.decrypt(&mut incoming_frame.0)?;
        Ok(incoming_frame.0)
    }
}

trait Path: Send {
    fn add_chunk(&mut self, chunk: &[u8]);

    fn process_frame(
        &mut self,
        ctx: &Context,
    ) -> Result<Option<PathProcessResult>, RendezvousProtocolError>;

    fn nominate(&mut self) -> Result<PathProcessResult, RendezvousProtocolError>;

    fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError>;
}

impl Path for RidPath {
    fn add_chunk(&mut self, chunk: &[u8]) {
        self.path.decoder.add_chunk(chunk);
    }

    fn process_frame(
        &mut self,
        ctx: &Context,
    ) -> Result<Option<PathProcessResult>, RendezvousProtocolError> {
        let nominated = matches!(&self.state, RidPathState::Nominated { .. });
        if let Some(incoming_frame) = self.path.decode_next(nominated)? {
            self.process_frame(ctx, incoming_frame).map(Some)
        } else {
            Ok(None)
        }
    }

    fn nominate(&mut self) -> Result<PathProcessResult, RendezvousProtocolError> {
        self.nominate()
    }

    fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        self.create_ulp_frame(outgoing_data)
    }
}

impl Path for RrdPath {
    fn add_chunk(&mut self, chunk: &[u8]) {
        self.path.decoder.add_chunk(chunk);
    }

    fn process_frame(
        &mut self,
        ctx: &Context,
    ) -> Result<Option<PathProcessResult>, RendezvousProtocolError> {
        let nominated = matches!(&self.state, RrdPathState::Nominated { .. });
        if let Some(incoming_frame) = self.path.decode_next(nominated)? {
            self.process_frame(ctx, incoming_frame).map(Some)
        } else {
            Ok(None)
        }
    }

    fn nominate(&mut self) -> Result<PathProcessResult, RendezvousProtocolError> {
        self.nominate()
    }

    fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        self.create_ulp_frame(outgoing_data)
    }
}

/// Internal protocol state.
enum ProtocolState {
    /// The paths are currently racing, meaning we are in the _handshake and nomination phase_.
    RacingPaths(HashMap<u32, Box<dyn Path>>),

    /// One path has been nominated, all other paths have been discarded, meaning we are in the
    /// _ULP phase_.
    Nominated { pid: u32, path: Box<dyn Path> },
}

/// Connection Rendezvous Protocol state machine.
///
/// The protocol state machine can be constructed from a formerly exchanged a `RendezvousInit` and
/// the associated roles by using [`RendezvousProtocol::new_as_rid`] and
/// [`RendezvousProtocol::new_as_rrd`].
///
/// Any interaction with the protocol state machine that changes the internal state will yield a
/// [`PathProcessResult`] that must be handled according to its documentation.
///
/// The protocol goes through exactly two phases:
///
/// - The _handshake and nomination phase_ where all paths are racing the handshake simultaneously
///   until one has been nominated by the nominator.
/// - The _ULP phase_ where ULP frames can be exchanged on the nominated path.
///
/// The following steps are defined as the _Path Awaiting Nomination Steps_:
///
/// 1. Let `path` be the associated path.
/// 2. If the protocol did not take the role of the nominator, abort the protocol due to an error
///    and abort these steps.
/// 3. If `path` is the only path, run [`RendezvousProtocol::nominate_path`] for `path` and abort
///    these steps.
/// 4. (Unreachable / TODO(LIB-10): As of today, only one path is expected to be used.)
///
/// When a path closed, run the following steps:
///
/// 1. Let `path` be the path that closed (initiated locally or remotely).
/// 2. If `path` is marked as _nominated_, abort the protocol normally with any available close
///    information and abort these steps.
/// 3. If `path` is marked as _disregarded_, log a notice and abort these steps.
/// 4. If `path` is the last path that closed (i.e. all other paths already closed or there are no
///    other paths), log a warning that all paths closed before nomination, abort the protocol
///    normally with any available close information and abort these steps.
/// 5. Log a warning that `path` closed before nomination.
///
/// When receiving data on a path:
///
/// 1. Run [`RendezvousProtocol::add_chunks`] with the respective path's PID.
/// 2. In a loop, run [`RendezvousProtocol::process_frame`] with the respective path's PID and
///    handle the result until it no longer produces a [`PathProcessResult`].
///
/// When the protocol is being aborted:
///
/// 1. Let `cause` be an error or any information associated to normal closure.
/// 2. Log the protocol abort due to `cause` as a notice or an error respectively.
/// 3. Tear down the protocol state machine.
/// 4. Close all remaining paths exceptionally.
/// 5. Hand off `cause` to the ULP.
pub struct RendezvousProtocol {
    ctx: Context,
    state: ProtocolState,
}

// TODO(LIB-11): Add construction of the `RendezvousInit` from the paths here.
impl RendezvousProtocol {
    #[tracing::instrument(skip(ak))]
    /// Create a new Connection Rendezvous Protocol as the Rendezvous Initiator Device (RID) from a
    /// formerly exchanged `RendezvousInit`.
    ///
    /// `pids` must contain the set of available pre-initiated paths with their associated Path IDs
    /// (PID).
    ///
    /// Returns the protocol state machine instance.
    pub fn new_as_rid(is_nominator: bool, ak: AuthenticationKey, pids: &[u32]) -> Self {
        debug! { "Creating protocol" };
        let ctx = Context::new(is_nominator, ak);

        // Create paths
        let racing_paths = pids
            .iter()
            .map(|pid| {
                let path = RidPath::new(&ctx.ak, *pid);
                (*pid, Box::new(path) as Box<dyn Path>)
            })
            .collect();

        // Create protocol
        Self {
            ctx,
            state: ProtocolState::RacingPaths(racing_paths),
        }
    }

    /// Create a new Connection Rendezvous Protocol as the Rendezvous Responder Device (RRD) from a
    /// formerly exchanged `RendezvousInit`.
    ///
    /// `pids` must contain the set of available pre-initiated paths with their associated Path IDs
    /// (PID).
    ///
    /// Returns a tuple of the protocol state machine instance and a list of PIDs and outgoing
    /// frames to be enqueued on the respective paths immediately.
    #[tracing::instrument(skip(ak))]
    pub fn new_as_rrd(
        is_nominator: bool,
        ak: AuthenticationKey,
        pids: &[u32],
    ) -> (Self, Vec<(u32, OutgoingFrame)>) {
        debug! { "Creating protocol" };
        let ctx = Context::new(is_nominator, ak);
        let mut outgoing_frames = vec![];

        // Create paths
        let racing_paths = pids
            .iter()
            .map(|pid| {
                let (path, outgoing_frame) = RrdPath::new(&ctx.ak, *pid);
                outgoing_frames.push((*pid, outgoing_frame));
                (*pid, Box::new(path) as Box<dyn Path>)
            })
            .collect();

        // Create protocol
        let protocol = Self {
            ctx,
            state: ProtocolState::RacingPaths(racing_paths),
        };
        (protocol, outgoing_frames)
    }

    /// Return whether the protocol took the role of the nominator.
    #[must_use]
    pub const fn is_nominator(&self) -> bool {
        self.ctx.is_nominator
    }

    /// Return the nominated path's PID, if available.
    #[must_use]
    pub const fn nominated_path(&self) -> Option<u32> {
        if let ProtocolState::Nominated { pid, .. } = &self.state {
            Some(*pid)
        } else {
            None
        }
    }

    /// Add chunks received on the specified path. The chunks may or may not contain complete frames
    /// or even contain multiple complete frames.
    ///
    /// # Errors
    ///
    /// Returns [`RendezvousProtocolError::UnknownOrDroppedPath`] if the path associated to `pid`
    /// could not be found.
    #[tracing::instrument(skip(self, chunks))]
    pub fn add_chunks<TChunk: AsRef<[u8]>>(
        &mut self,
        pid: u32,
        chunks: &[TChunk],
    ) -> Result<(), RendezvousProtocolError> {
        let path = Self::lookup_path(&mut self.state, pid)?;
        for chunk in chunks {
            path.add_chunk(chunk.as_ref());
        }
        Ok(())
    }

    /// Process any available buffered complete frame for the specified path.
    ///
    /// # Errors
    ///
    /// Returns [`RendezvousProtocolError`] for a plethora of reasons, e.g. if the path associated
    /// to `pid` could not be found, an incoming frame could not be decoded or decrypted, or an
    /// unexpected message was received, or, as a response to it, another outgoing frame could not
    /// be encrypted.
    #[tracing::instrument(skip(self))]
    pub fn process_frame(
        &mut self,
        pid: u32,
    ) -> Result<Option<PathProcessResult>, RendezvousProtocolError> {
        let path = Self::lookup_path(&mut self.state, pid)?;

        // Decode and process the next frame, if any can be decoded
        let result = path.process_frame(&self.ctx)?;
        trace! {
            ?result,
            "Processed frame"
        }
        let Some(result) = result else {
            return Ok(result);
        };

        // Update state if the path was nominated and we are still racing paths
        if let (ProtocolState::RacingPaths(racing_paths), Some(PathStateUpdate::Nominated { .. })) =
            (&mut self.state, &result.state_update)
        {
            // Nominate the path
            let path = racing_paths
                .remove(&pid)
                .ok_or(RendezvousProtocolError::UnknownOrDroppedPath(pid))?;
            debug! {
                dropped_pids = ?racing_paths.keys(),
                "Remote nominated, dropping all other paths"
            }
            self.state = ProtocolState::Nominated { pid, path };
        }

        // Return the result
        Ok(Some(result))
    }

    /// Nominate a path.
    ///
    /// # Errors
    ///
    /// Returns [`RendezvousProtocolError`] if the protocol did not take the role of the nominator,
    /// the path associated to `pid` could not be found, the path is not ready to be nominated or
    /// nomination already happened.
    #[tracing::instrument(skip(self))]
    pub fn nominate_path(
        &mut self,
        pid: u32,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        // Ensure we're allowed to nominate
        if !self.ctx.is_nominator {
            return Err(RendezvousProtocolError::NominateNotAllowed);
        }

        // Nominate if the paths are still racing
        match &mut self.state {
            ProtocolState::RacingPaths(racing_paths) => {
                // Attempt to nominate the path
                let mut path = racing_paths
                    .remove(&pid)
                    .ok_or(RendezvousProtocolError::UnknownOrDroppedPath(pid))?;
                let result = path.nominate()?;
                debug! {
                    dropped_pids = ?racing_paths.keys(),
                    "Local nominated, dropping all other paths"
                }
                self.state = ProtocolState::Nominated { pid, path };
                Ok(result)
            },

            ProtocolState::Nominated { pid, .. } => {
                // Nomination already happened
                Err(RendezvousProtocolError::NominationAlreadyDone(*pid))
            },
        }
    }

    /// Create a ULP frame to be encrypted and sent as an outgoing frame on the nominated path.
    ///
    /// # Errors
    ///
    /// Returns [`RendezvousProtocolError`] if nomination of a path is still pending or the ULP
    /// frame could not be encrypted or encoded.
    pub fn create_ulp_frame(
        &mut self,
        outgoing_data: Vec<u8>,
    ) -> Result<PathProcessResult, RendezvousProtocolError> {
        match &mut self.state {
            ProtocolState::RacingPaths(..) => Err(RendezvousProtocolError::NominationRequired),
            ProtocolState::Nominated { path, .. } => path.create_ulp_frame(outgoing_data),
        }
    }

    fn lookup_path(
        state: &mut ProtocolState,
        pid: u32,
    ) -> Result<&mut Box<dyn Path>, RendezvousProtocolError> {
        // Lookup path based on the current state.
        let path = match state {
            // The nomination race is still ongoing. Lookup the path by its PID.
            ProtocolState::RacingPaths(racing_paths) => racing_paths
                .get_mut(&pid)
                .ok_or(RendezvousProtocolError::UnknownOrDroppedPath(pid))?,

            // There's only one nominated path. Ensure it's the correct one.
            ProtocolState::Nominated {
                pid: nominated_pid,
                path,
            } => {
                if pid != *nominated_pid {
                    return Err(RendezvousProtocolError::UnknownOrDroppedPath(pid));
                }
                path
            },
        };
        Ok(path)
    }
}
