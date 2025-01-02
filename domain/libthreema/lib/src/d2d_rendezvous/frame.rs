use tracing::trace;

const HEADER_LENGTH: usize = 4;

/// An error occurred while decoding an incoming frame.
#[derive(Debug, thiserror::Error)]
pub enum FrameDecoderError {
    /// Frame exceeds the maximum acceptable length.
    #[error("Frame is too large, max-length={max_length}, frame-length={announced_length}")]
    FrameTooLarge {
        /// Maximum acceptable frame length (at this point in the protocol flow).
        max_length: usize,
        /// Frame length announced by the frame header.
        announced_length: usize,
    },
}

/// An incoming frame
pub struct IncomingFrame(pub Vec<u8>);

impl core::fmt::Debug for IncomingFrame {
    fn fmt(&self, formatter: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        formatter
            .debug_struct("IncomingFrame")
            .field("length", &self.0.len())
            .finish()
    }
}

enum IncomingFrameDecoderState {
    PartialHeader,
    PartialFrame { length: usize },
}

/// Tracks boundaries to hand out complete incoming frames from chunks of frames (or even multiple
/// frames in a single chunk).
///
/// Chunks of data can be added through [`IncomingFrameDecoder::add_chunk`]. At any time, calling
/// [`IncomingFrameDecoder::decode_next`] will yield a complete frame decoded from the internally
/// contained data, if one is available.
pub(super) struct IncomingFrameDecoder {
    data: Vec<u8>,
    state: IncomingFrameDecoderState,
}
impl IncomingFrameDecoder {
    pub(super) fn new() -> Self {
        Self {
            data: vec![],
            state: IncomingFrameDecoderState::PartialHeader,
        }
    }

    /// Add a single chunk to the decoder which may or may not contain a full frame (or even
    /// multiple frames).
    ///
    /// Note: To retrieve the next available frame, call [`IncomingFrameDecoder::decode_next`] after
    /// adding a chunk.
    pub(super) fn add_chunk(&mut self, chunk: &[u8]) {
        trace! {
            chunk_length = chunk.len(),
            "Adding chunk",
        };

        // Add chunk to excess data that we still need to handle
        self.data.extend_from_slice(chunk);
    }

    /// Poll and retrieve a frame from the decoder.
    ///
    /// Note: This should be called in a loop after adding a chunk through
    /// [`IncomingFrameDecoder::add_chunk`] until [`None`] is returned.
    pub(super) fn decode_next(
        &mut self,
        max_length: usize,
    ) -> Result<Option<IncomingFrame>, FrameDecoderError> {
        if let IncomingFrameDecoderState::PartialHeader = &self.state {
            // We need at least four bytes to determine the size of the frame before we can move
            // into the next state.
            if self.data.len() < HEADER_LENGTH {
                return Ok(None);
            }

            // Decode the length and move into the next state without adding any data yet.
            let length: usize = u32::from_le_bytes(
                self.data
                    .get(..HEADER_LENGTH)
                    .expect("data must be >= HEADER_LENGTH")
                    .try_into()
                    .expect("HEADER_LENGTH must be 4 bytes"),
            ) as usize;
            if length > max_length {
                return Err(FrameDecoderError::FrameTooLarge {
                    max_length,
                    announced_length: length,
                });
            }
            self.state = IncomingFrameDecoderState::PartialFrame { length };
        }

        if let IncomingFrameDecoderState::PartialFrame { length } = &self.state {
            if self
                .data
                .len()
                .checked_sub(HEADER_LENGTH)
                .expect("data must be >= HEADER_LENGTH")
                < *length
            {
                // We have less data than what our frame needs. Wait for more.
                return Ok(None);
            }

            // The chunk contains more than or exactly what our frame needs. Drain the frame data
            // and move into the next state with the remaining data.
            let _ = self.data.drain(..HEADER_LENGTH);
            let frame = IncomingFrame(self.data.drain(..*length).collect());
            self.state = IncomingFrameDecoderState::PartialHeader;
            return Ok(Some(frame));
        }

        unreachable!("All states should have been handled at this point");
    }
}

/// An outgoing frame.
pub struct OutgoingFrame(pub Vec<u8>);

impl core::fmt::Debug for OutgoingFrame {
    fn fmt(&self, formatter: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        formatter
            .debug_struct("OutgoingFrame")
            .field("length", &(self.0.len().saturating_add(HEADER_LENGTH)))
            .finish()
    }
}

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
