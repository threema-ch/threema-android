//! Byte reader.
use core::ops::Range;

use duplicate::duplicate_item;

/// An error occurred while reading while using a [`ByteReader`].
#[derive(Clone, Debug, thiserror::Error)]
pub enum ByteReaderError {
    /// Provided relative offset would move the reader offset out of bounds.
    #[error(
        "Relative offset out of bounds: relative-offset={relative_offset}, offset={offset}, length={length}"
    )]
    InvalidRelativeOffset {
        /// Requested relative offset to move within the data buffer.
        relative_offset: isize,
        /// Current offset in the underlying data buffer.
        offset: usize,
        /// Total length of the underlying data buffer.
        length: usize,
    },

    /// Insufficient remaining bytes left for the desired operation
    #[error("Insufficient bytes left: requested={requested}, remaining={remaining}, offset={offset}")]
    InsufficientRemaining {
        /// Requested amount of bytes to be read.
        requested: usize,
        /// Remaining amount of bytes available to read.
        remaining: usize,
        /// Current offset in the underlying data buffer.
        offset: usize,
    },

    /// The reader was expected to be consumed but has remaining bytes left
    #[error("Unexpected remaining bytes left: remaining={remaining}, length={length}")]
    UnexpectedRemaining {
        /// Remaining amount of bytes left to read.
        remaining: usize,
        /// Total length of the underlying data buffer.
        length: usize,
    },
}

pub(crate) struct ByteReaderContainer<T> {
    buffer: T,
    offset: usize,
}

pub(crate) trait ByteReader {
    type Buffer;

    /// Writer type used for [`ByteReader::run`].
    type RunReader<'run>: ByteReader;

    /// Writer type used for [`ByteReader::run_at`].
    type RunAtReader<'run_at>: ByteReader;

    /// Return the amount of remaining bytes in the reader.
    fn remaining(&self) -> usize;

    /// Skip over a specific amount of bytes.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    fn skip(&mut self, length: usize) -> Result<(), ByteReaderError>;

    /// Run an arbitrary operation on the reader.
    ///
    /// This is useful when chaining multiple read operations that can be mapped to a single error.
    ///
    /// # Error
    ///
    /// Returns any [`ByteReaderError`] returned by the operation.
    fn run<T, F: FnOnce(&mut Self::RunReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteReaderError>;

    /// Run an arbitrary operation on the reader and take it.
    ///
    /// Note: This does not call [`ByteReader::expect_consumed`] at the end of the function!
    ///
    /// This is useful when chaining multiple read operations that can be mapped to a single error.
    ///
    /// # Error
    ///
    /// Returns any [`ByteReaderError`] returned by the operation.
    fn run_owned<T, F: FnOnce(Self::RunReader<'_>) -> Result<T, ByteReaderError>>(
        self,
        op: F,
    ) -> Result<T, ByteReaderError>;

    /// Run an arbitrary operation on the reader at a relative offset to the current offset in a
    /// child reader instance. The parent reader will maintain the current offset.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader offset would move of the
    /// underlying data boundary.
    ///
    /// Returns any [`ByteReaderError`] returned by the operation.
    fn run_at<T, F: FnOnce(Self::RunAtReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteReaderError>;

    /// Expect the reader to have consumed all data (i.e. [`Self::remaining`] is `0`), returning the
    /// underlying buffer.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::UnexpectedRemaining`] if the reader is not yet consumed.
    fn expect_consumed(self) -> Result<Self::Buffer, ByteReaderError>;

    /// Read a specific amount of bytes.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    fn read(&mut self, length: usize) -> Result<&[u8], ByteReaderError>;

    /// Read a specific amount of bytes, yielding the offset range of the slice.
    ///
    /// This can be used when wrapping a [`ByteReader`] around a [`Vec<u8>`] to get a mutable slice
    /// over the returned range at after consuming the [`ByteReader`].
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    fn read_range(&mut self, length: usize) -> Result<Range<usize>, ByteReaderError>;

    /// Read all remaining bytes in the reader.
    fn read_remaining(&mut self) -> &[u8];

    /// Read a specific amount of bytes, yielding a fixed-size slice.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    #[inline]
    fn read_fixed<const LENGTH: usize>(&mut self) -> Result<[u8; LENGTH], ByteReaderError> {
        let bytes = self.read(LENGTH)?;
        // Note: The `.read` call above ensures that the `expect` call does not fail
        Ok(bytes
            .try_into()
            .expect("[offset..offset + LENGTH] must be LENGTH bytes"))
    }

    /// Read a u8.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    #[inline]
    fn read_u8(&mut self) -> Result<u8, ByteReaderError> {
        self.read_fixed::<1>().map(|bytes| bytes[0])
    }

    /// Read a u16 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    #[inline]
    fn read_u16_le(&mut self) -> Result<u16, ByteReaderError> {
        let bytes = self.read_fixed::<2>()?;
        Ok(u16::from_le_bytes(bytes))
    }

    /// Read a u32 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    #[inline]
    fn read_u32_le(&mut self) -> Result<u32, ByteReaderError> {
        let bytes = self.read_fixed::<4>()?;
        Ok(u32::from_le_bytes(bytes))
    }

    /// Read a u64 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    #[inline]
    fn read_u64_le(&mut self) -> Result<u64, ByteReaderError> {
        let bytes = self.read_fixed::<8>()?;
        Ok(u64::from_le_bytes(bytes))
    }
}

/// Contains a range reference to an encrypted section of data, explicitly including its tag.
pub(crate) struct EncryptedDataRange<const TAG_LENGTH: usize> {
    /// Data range reference
    pub(crate) data: Range<usize>,
    /// Tag for the decryption process
    pub(crate) tag: [u8; TAG_LENGTH],
}

/// Extension to read a specific amount of encrypted bytes and its encryption tag.
pub(crate) trait EncryptedDataRangeReader<const TAG_LENGTH: usize> {
    /// Read a specific amount of encrypted bytes and its encryption tag, yielding the offset range
    /// of the slice and the tag at once.
    ///
    /// Note: `length` should NOT include the length of the tag.
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient
    /// remaining bytes left.
    fn read_encrypted_data_range(
        &mut self,
        length: usize,
    ) -> Result<EncryptedDataRange<TAG_LENGTH>, ByteReaderError>;
}
impl<TReader: ByteReader, const TAG_LENGTH: usize> EncryptedDataRangeReader<TAG_LENGTH> for TReader {
    fn read_encrypted_data_range(
        &mut self,
        length: usize,
    ) -> Result<EncryptedDataRange<TAG_LENGTH>, ByteReaderError> {
        Ok(EncryptedDataRange {
            data: self.read_range(length)?,
            tag: self.read_fixed::<TAG_LENGTH>()?.to_owned(),
        })
    }
}

#[duplicate_item(
    buffer_type;
    [ &'buffer [u8] ];
    [ Vec<u8> ];
    [ &'buffer Vec<u8> ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
#[allow(clippy::extra_unused_lifetimes, reason = "Abstracted duplicate")]
impl<'buffer> ByteReaderContainer<buffer_type> {
    /// Create a new [`ByteReader`] starting at the **beginning** of the `buffer`.
    #[inline]
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) const fn new(buffer: buffer_type) -> Self {
        Self { buffer, offset: 0 }
    }
}

#[duplicate_item(
    buffer_type;
    [ &[u8] ];
)]
impl ByteReaderContainer<buffer_type> {
    #[inline]
    fn insufficient_remaining(&self, length: usize) -> ByteReaderError {
        ByteReaderError::InsufficientRemaining {
            requested: length,
            remaining: self.remaining(),
            offset: self.offset,
        }
    }
}

#[duplicate_item(
    buffer_type       run_writer_type   run_at_writer_type;
    [ &'buffer [u8] ] [ &'run [u8] ]    [ &'run_at [u8] ];
)]
impl<'buffer> ByteReader for ByteReaderContainer<buffer_type> {
    type Buffer = buffer_type;
    type RunAtReader<'run_at> = ByteReaderContainer<run_at_writer_type>;
    type RunReader<'run> = ByteReaderContainer<run_writer_type>;

    #[inline]
    fn remaining(&self) -> usize {
        #[expect(clippy::panic, reason = "Impossible")]
        let Some(remaining) = self.buffer.len().checked_sub(self.offset) else {
            panic!("length must be >= offset and therefore usize")
        };
        remaining
    }

    #[inline]
    fn skip(&mut self, length: usize) -> Result<(), ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Check if there are sufficient remaining bytes left
        if updated_offset > self.buffer.len() {
            return Err(insufficient_remaining());
        }

        // Update offset
        self.offset = updated_offset;
        Ok(())
    }

    #[inline]
    fn run<T, F: FnOnce(&mut Self::RunReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteReaderError> {
        op(self)
    }

    #[inline]
    fn run_owned<T, F: FnOnce(Self) -> Result<T, ByteReaderError>>(
        self,
        op: F,
    ) -> Result<T, ByteReaderError> {
        op(self)
    }

    #[inline]
    fn run_at<T, F: FnOnce(Self::RunAtReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteReaderError> {
        let invalid_offset = || ByteReaderError::InvalidRelativeOffset {
            relative_offset,
            offset: self.offset,
            length: self.buffer.len(),
        };

        // Calculate new offset
        let updated_offset: usize = relative_offset
            .checked_add(self.offset.try_into().map_err(|_| invalid_offset())?)
            .ok_or_else(invalid_offset)?
            .try_into()
            .map_err(|_| invalid_offset())?;
        if updated_offset > self.buffer.len() {
            return Err(invalid_offset());
        }

        // Run
        op(Self::RunAtReader {
            buffer: self.buffer,
            offset: updated_offset,
        })
    }

    #[inline]
    fn expect_consumed(self) -> Result<Self::Buffer, ByteReaderError> {
        if self.offset != self.buffer.len() {
            return Err(ByteReaderError::UnexpectedRemaining {
                remaining: self.remaining(),
                length: self.buffer.len(),
            });
        }
        Ok(self.buffer)
    }

    #[inline]
    fn read(&mut self, length: usize) -> Result<&'buffer [u8], ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Attempt to read
        let bytes = self
            .buffer
            .get(self.offset..updated_offset)
            .ok_or_else(insufficient_remaining)?;

        // Update offset and return read bytes
        self.offset = updated_offset;
        Ok(bytes)
    }

    #[inline]
    fn read_range(&mut self, length: usize) -> Result<Range<usize>, ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Check if there are sufficient remaining bytes left
        if updated_offset > self.buffer.len() {
            return Err(insufficient_remaining());
        }

        // Update offset and return range
        let range = self.offset..updated_offset;
        self.offset = updated_offset;
        Ok(range)
    }

    #[inline]
    fn read_remaining(&mut self) -> &'buffer [u8] {
        let bytes = self
            .buffer
            .get(self.offset..)
            .expect("offset should be <= buffer length");
        self.offset = self.buffer.len();
        bytes
    }
}

#[duplicate_item(
    buffer_type;
    [ Vec<u8> ];
    [ &Vec<u8> ];
)]
impl ByteReaderContainer<buffer_type> {
    #[inline]
    fn insufficient_remaining(&self, length: usize) -> ByteReaderError {
        ByteReaderError::InsufficientRemaining {
            requested: length,
            remaining: self.remaining(),
            offset: self.offset,
        }
    }
}

#[duplicate_item(
    buffer_type;
    [ Vec<u8> ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
#[allow(clippy::multiple_inherent_impl, reason = "Abstracted duplicate")]
impl ByteReaderContainer<Vec<u8>> {
    /// Read all remaining bytes and consume the reader and its underlying buffer.
    #[inline]
    pub(crate) fn read_remaining_owned(mut self) -> buffer_type {
        self.buffer.drain(self.offset..).collect()
    }
}

#[duplicate_item(
    buffer_type          run_writer_type   run_at_writer_type;
    [ &'buffer Vec<u8> ] [ &'run Vec<u8> ] [ &'run_at Vec<u8> ];
    [ Vec<u8> ]          [ Vec<u8> ]       [ &'run_at Vec<u8> ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
#[allow(clippy::extra_unused_lifetimes, reason = "Abstracted duplicate")]
impl<'buffer> ByteReader for ByteReaderContainer<buffer_type> {
    type Buffer = buffer_type;
    type RunAtReader<'run_at> = ByteReaderContainer<run_at_writer_type>;
    type RunReader<'run> = ByteReaderContainer<run_writer_type>;

    #[inline]
    fn remaining(&self) -> usize {
        #[expect(clippy::panic, reason = "Impossible")]
        let Some(remaining) = self.buffer.len().checked_sub(self.offset) else {
            panic!("length must be >= offset and therefore usize")
        };
        remaining
    }

    #[inline]
    fn skip(&mut self, length: usize) -> Result<(), ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Check if there are sufficient remaining bytes left
        if updated_offset > self.buffer.len() {
            return Err(insufficient_remaining());
        }

        // Update offset
        self.offset = updated_offset;
        Ok(())
    }

    #[inline]
    fn run<T, F: FnOnce(&mut Self::RunReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteReaderError> {
        op(self)
    }

    #[inline]
    fn run_owned<T, F: FnOnce(Self) -> Result<T, ByteReaderError>>(
        self,
        op: F,
    ) -> Result<T, ByteReaderError> {
        op(self)
    }

    #[inline]
    fn run_at<T, F: FnOnce(Self::RunAtReader<'_>) -> Result<T, ByteReaderError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteReaderError> {
        let invalid_offset = || ByteReaderError::InvalidRelativeOffset {
            relative_offset,
            offset: self.offset,
            length: self.buffer.len(),
        };

        // Calculate new offset
        let updated_offset: usize = relative_offset
            .checked_add(self.offset.try_into().map_err(|_| invalid_offset())?)
            .ok_or_else(invalid_offset)?
            .try_into()
            .map_err(|_| invalid_offset())?;
        if updated_offset > self.buffer.len() {
            return Err(invalid_offset());
        }

        // Run
        #[allow(clippy::needless_borrow, reason = "(Poorly) abstracted duplicate")]
        op(Self::RunAtReader {
            buffer: &self.buffer,
            offset: updated_offset,
        })
    }

    #[inline]
    fn expect_consumed(self) -> Result<Self::Buffer, ByteReaderError> {
        if self.offset != self.buffer.len() {
            return Err(ByteReaderError::UnexpectedRemaining {
                remaining: self.remaining(),
                length: self.buffer.len(),
            });
        }
        Ok(self.buffer)
    }

    #[inline]
    fn read(&mut self, length: usize) -> Result<&[u8], ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Attempt to read
        let bytes = self
            .buffer
            .get(self.offset..updated_offset)
            .ok_or_else(insufficient_remaining)?;

        // Update offset and return read bytes
        self.offset = updated_offset;
        Ok(bytes)
    }

    #[inline]
    fn read_range(&mut self, length: usize) -> Result<Range<usize>, ByteReaderError> {
        let insufficient_remaining = || self.insufficient_remaining(length);

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(insufficient_remaining)?;

        // Check if there are sufficient remaining bytes left
        if updated_offset > self.buffer.len() {
            return Err(insufficient_remaining());
        }

        // Update offset and return range
        let range = self.offset..updated_offset;
        self.offset = updated_offset;
        Ok(range)
    }

    #[inline]
    fn read_remaining(&mut self) -> &[u8] {
        let bytes = self
            .buffer
            .get(self.offset..)
            .expect("offset should be <= buffer length");
        self.offset = self.buffer.len();
        bytes
    }
}

/// Wraps a [`&[u8]`] and allows to apply read operations safely within the constrained space.
pub(crate) type SliceByteReader<'buffer> = ByteReaderContainer<&'buffer [u8]>;

/// Wraps or creates a [`Vec<u8>`] and allows to apply read operations safely within the constrained
/// space.
pub(crate) type OwnedVecByteReader = ByteReaderContainer<Vec<u8>>;

/// Wraps a [`&Vec<u8>`] and is otherwise identical to the [`OwnedVecByteReader`].
#[expect(dead_code, reason = "Will use later")]
pub(crate) type BorrowedVecByteReader<'buffer> = ByteReaderContainer<&'buffer Vec<u8>>;
