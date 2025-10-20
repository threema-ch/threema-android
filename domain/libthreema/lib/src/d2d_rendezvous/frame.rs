//! Incoming/Outgoing frame utilities.
use educe::Educe;
use libthreema_macros::Name;

use crate::utils::{
    debug::debug_slice_length,
    frame::{FrameDelimiter as _, U32LittleEndianDelimiter, VariableLengthFrameDecoder},
};

const HEADER_LENGTH: usize = 4;

/// An incoming frame
#[derive(Educe)]
#[educe(Debug)]
pub struct IncomingFrame(#[educe(Debug(method(debug_slice_length)))] pub Vec<u8>);

/// Incoming frame decoder.
pub(super) type FrameDecoder =
    VariableLengthFrameDecoder<{ U32LittleEndianDelimiter::LENGTH }, U32LittleEndianDelimiter>;
impl FrameDecoder {
    // During the handshake and before nomination, we don't expect frames larger than 16 KiB. After
    // the handshake, we don't expect frames larger than 100 MiB. (If we ever need larger frames
    // than this, we need to add Blob chunking and stream the content.)
    pub(super) const MAX_LENGTH_AFTER_NOMINATION: usize = 100 * 1_048_576;
    pub(super) const MAX_LENGTH_BEFORE_NOMINATION: usize = 16_384;
}

/// An outgoing frame.
///
/// TODO(LIB-30): Simplify
#[derive(Educe, Name)]
#[educe(Debug)]
pub struct OutgoingFrame(#[educe(Debug(method(debug_slice_length)))] pub(super) Vec<u8>);
impl OutgoingFrame {
    /// Encode the frame to bytes (header bytes, followed by the payload).
    ///
    /// # Panics
    ///
    /// Will panic if the enclosed frame's length exceeds a u32.
    #[must_use]
    pub fn encode(&self) -> ([u8; HEADER_LENGTH], &[u8]) {
        // Encode the length
        let length = u32::try_from(self.0.len())
            .expect("Frame length must be <= 2^32-1")
            .to_le_bytes();

        // Yield slice of the payload
        let payload = &self.0;

        (length, payload)
    }
}

impl From<OutgoingFrame> for Vec<u8> {
    fn from(frame: OutgoingFrame) -> Self {
        let (header, payload) = frame.encode();
        [header.as_slice(), payload].concat()
    }
}
