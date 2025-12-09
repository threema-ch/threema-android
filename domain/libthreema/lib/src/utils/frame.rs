//! Frame (i.e. a fixed size of bytes) utilities.
use core::{fmt, marker::PhantomData};

use duplicate::duplicate_item;
use libthreema_macros::Name;

use crate::utils::debug::Name as _;

/// Contains a stream of data and hands out individual fixed length frames, one by one.
#[derive(Name)]
pub(crate) struct FixedLengthFrameDecoder<const LENGTH: usize> {
    data: Vec<u8>,
}
impl<const LENGTH: usize> FixedLengthFrameDecoder<LENGTH> {
    pub(crate) fn new() -> Self {
        Self { data: vec![] }
    }

    pub(crate) fn new_with_data(data: Vec<u8>) -> Self {
        Self { data }
    }

    /// Dissolve the frame decoder, returning any excess data.
    pub(crate) fn dissolve(self) -> Vec<u8> {
        self.data
    }

    /// The required number of bytes for the decoder to decode a full frame.
    ///
    /// Note: An efficient implementation may always provide more than the required amount of bytes,
    /// if already available.
    pub(crate) fn required_length(&self) -> usize {
        LENGTH.saturating_sub(self.data.len())
    }

    /// Add chunks to the decoder's stream of data.
    pub(crate) fn add_chunks(&mut self, chunks: &[&[u8]]) {
        for chunk in chunks {
            self.data.extend_from_slice(chunk);
        }
    }

    /// Get the next frame from the decoder, passing through a transformation step (e.g. to avoid
    /// copying) if available.
    pub(crate) fn next_and_then<TFrame, F: FnOnce(&[u8; LENGTH]) -> TFrame>(
        &mut self,
        map_fn: F,
    ) -> Option<TFrame> {
        if self.data.len() < LENGTH {
            return None;
        }
        Some(map_fn(
            self.data
                .drain(..LENGTH)
                .as_slice()
                .try_into()
                .expect("self.data must contain LENGTH bytes"),
        ))
    }
}
impl<const LENGTH: usize> fmt::Debug for FixedLengthFrameDecoder<LENGTH> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct(Self::NAME)
            .field("data", &format_args!("length={}", self.data.len()))
            .field("required_length()", &self.required_length())
            .finish()
    }
}

/// An arbitrary fixed-size frame delimiter, decoding itself to yield the length of a frame.
pub(crate) trait FrameDelimiter<const LENGTH: usize> {
    const LENGTH: usize;

    /// Decode the frame delimiter and return the length of the frame
    fn decode(data: [u8; LENGTH]) -> usize;
}

/// [`u16`] little endian length delimiter.
pub(crate) struct U16LittleEndianDelimiter;

/// [`u32`] little endian length delimiter.
pub(crate) struct U32LittleEndianDelimiter;

#[duplicate_item(
    delimiter_type               int_type int_length;
    [ U16LittleEndianDelimiter ] [ u16 ]  [ 2 ];
    [ U32LittleEndianDelimiter ] [ u32 ]  [ 4 ];
)]
impl FrameDelimiter<int_length> for delimiter_type {
    const LENGTH: usize = int_length;

    #[inline]
    fn decode(data: [u8; int_length]) -> usize {
        int_type::from_le_bytes(data) as usize
    }
}

#[derive(Debug)]
enum VariableLengthFrameDecoderState {
    PartialDelimiter,
    PartialFrame { length: usize },
}

/// Contains a stream of data and hands out individual variable length frames, one by one.
#[derive(Name)]
pub(crate) struct VariableLengthFrameDecoder<
    const DELIMETER_LENGTH: usize,
    TDelimiter: FrameDelimiter<DELIMETER_LENGTH>,
> {
    delimiter: PhantomData<TDelimiter>,
    state: VariableLengthFrameDecoderState,
    data: Vec<u8>,
}
impl<const DELIMETER_LENGTH: usize, TDelimiter: FrameDelimiter<DELIMETER_LENGTH>>
    VariableLengthFrameDecoder<DELIMETER_LENGTH, TDelimiter>
{
    const LENGTH: usize = DELIMETER_LENGTH;

    /// Create a frame decoder using a [`FrameDelimiter`] to decode the length of frames with `data`
    /// being the initial data.
    pub(crate) fn new(data: Vec<u8>) -> Self {
        Self {
            delimiter: PhantomData,
            state: VariableLengthFrameDecoderState::PartialDelimiter,
            data,
        }
    }

    /// The required number of bytes for the decoder to advance its internal state.
    ///
    /// Note: An efficient implementation may always provide more than the required amount of bytes,
    /// if already available.
    pub(crate) fn required_length(&self) -> usize {
        match &self.state {
            VariableLengthFrameDecoderState::PartialDelimiter => Self::LENGTH.saturating_sub(self.data.len()),
            VariableLengthFrameDecoderState::PartialFrame { length } => {
                length.saturating_sub(self.data.len())
            },
        }
    }

    /// Add chunks to the decoder's stream of data and return the amount of data that is currently
    /// buffered.
    pub(crate) fn add_chunks(&mut self, chunks: &[&[u8]]) -> usize {
        for chunk in chunks {
            self.data.extend_from_slice(chunk);
        }
        self.data.len()
    }

    /// Dissolve the frame decoder, returning any excess data.
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn dissolve(self) -> Vec<u8> {
        self.data
    }

    /// Get the next frame from the decoder, passing through a transformation step (e.g. to avoid
    /// copying) if available.
    pub(crate) fn next_frame_and_then<TResult, F: FnOnce(&[u8]) -> TResult>(
        &mut self,
        map_fn: F,
    ) -> Option<TResult>
    where
        [(); DELIMETER_LENGTH]:,
    {
        if let VariableLengthFrameDecoderState::PartialDelimiter = &self.state {
            // Check if we have sufficient bytes to decode the limiter or wait for more
            let delimiter = self.data.get(..DELIMETER_LENGTH)?;

            // Decode the delimiter to retrieve the length
            let delimiter: [u8; DELIMETER_LENGTH] = delimiter
                .try_into()
                .expect("[0..DELIMETER_LENGTH] must be DELIMETER_LENGTH bytes");
            let length = TDelimiter::decode(delimiter);

            // Drain the delimiter and move into the next state
            let _ = self.data.drain(..DELIMETER_LENGTH);
            self.state = VariableLengthFrameDecoderState::PartialFrame { length };
        }

        if let VariableLengthFrameDecoderState::PartialFrame { length } = &self.state {
            if self.data.len() < *length {
                // We have less data than what our frame needs. Wait for more.
                return None;
            }

            // The chunk contains more than or exactly what our frame needs. Drain the frame data
            // and move into the next state with the remaining data.
            let frame = map_fn(self.data.drain(..*length).as_slice());
            self.state = VariableLengthFrameDecoderState::PartialDelimiter;
            return Some(frame);
        }

        unreachable!("All decoder states should have been handled at this point");
    }
}
impl<const DELIMETER_LENGTH: usize, TDelimiter: FrameDelimiter<DELIMETER_LENGTH>> fmt::Debug
    for VariableLengthFrameDecoder<DELIMETER_LENGTH, TDelimiter>
{
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct(Self::NAME)
            .field("state", &self.state)
            .field("data", &format_args!("length={}", self.data.len()))
            .finish()
    }
}
