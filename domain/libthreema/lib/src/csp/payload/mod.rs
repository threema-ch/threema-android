//! Incoming/outgoing payloads.
use educe::Educe;
use libthreema_macros::Name;

use crate::{csp::CspProtocolError, utils::debug::debug_slice_length};

pub(super) mod handshake;
mod post_handshake;
pub use handshake::LoginAckData;
pub use post_handshake::*;

/// An encoded outgoing frame.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct OutgoingFrame(#[educe(Debug(method(debug_slice_length)))] pub Vec<u8>);

/// Encodes a message/payload into a frame that can be sent on the CSP transport stream.
pub(super) trait FrameEncoder {
    /// Encode the message/payload into a frame with the appropriate header.
    fn encode_frame(&self) -> Result<OutgoingFrame, CspProtocolError>;
}
