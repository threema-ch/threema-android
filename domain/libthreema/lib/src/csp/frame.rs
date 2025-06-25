//! Incoming/Outgoing frame utilities.
use libthreema_macros::Name;

use super::CspProtocolError;
use crate::utils::debug::debug_slice_length;

/// An encoded outgoing frame.
#[derive(Name, educe::Educe)]
#[educe(Debug)]
pub struct OutgoingFrame(#[educe(Debug(method(debug_slice_length)))] pub Vec<u8>);

/// Encodes a message/payload into a frame that can be sent on the CSP transport stream.
pub(super) trait FrameEncoder {
    /// Encode the message into a frame with the appropriate header.
    fn encode_to_frame(&self) -> Result<OutgoingFrame, CspProtocolError>;
}
