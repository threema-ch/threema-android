//! Byte reader.
use core::ops::Range;

use duplicate::duplicate_item;
use educe::Educe;

use crate::utils::debug::debug_slice_length;

/// An error occurred while reading bytes.
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

    /// Return the current offset of the reader.
    #[expect(dead_code, reason = "Will use later")]
    fn offset(&self) -> usize;

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

/// Contains a range reference to an encrypted section of data.
#[derive(Clone, Educe)]
#[educe(Debug)]
pub(crate) struct EncryptedDataRange<const TAG_LENGTH: usize> {
    /// Tag for the decryption process
    #[educe(Debug(method(debug_slice_length)))]
    pub(crate) tag: [u8; TAG_LENGTH],

    /// Data range reference
    pub(crate) data: Range<usize>,
}

/// Extension to read a specific amount of encrypted bytes and its encryption tag.
pub(crate) trait EncryptedDataRangeReader<const TAG_LENGTH: usize> {
    /// Read a specific amount of encrypted bytes and its encryption tag, yielding the offset range of the
    /// slice and the tag at once.
    ///
    /// Note: `length` should NOT include the length of the tag.
    ///
    /// IMPORTANT: This may only be used for encryption implementations with a **prefix tag** (e.g.
    /// Salsa20Poly1305).
    ///
    /// # Error
    ///
    /// Returns [`ByteReaderError::InsufficientRemaining`] if the reader is does not have sufficient remaining
    /// bytes left.
    fn read_encrypted_data_range_tag_ahead(
        &mut self,
        length: usize,
    ) -> Result<EncryptedDataRange<TAG_LENGTH>, ByteReaderError>;
}
impl<TReader: ByteReader, const TAG_LENGTH: usize> EncryptedDataRangeReader<TAG_LENGTH> for TReader {
    fn read_encrypted_data_range_tag_ahead(
        &mut self,
        length: usize,
    ) -> Result<EncryptedDataRange<TAG_LENGTH>, ByteReaderError> {
        Ok(EncryptedDataRange {
            tag: self.read_fixed::<TAG_LENGTH>()?.to_owned(),
            data: self.read_range(length)?,
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

    /// Consume this reader, returning the underlying buffer.
    #[inline]
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) fn into_inner(self) -> buffer_type {
        self.buffer
    }
}

impl ByteReaderContainer<&[u8]> {
    #[inline]
    fn insufficient_remaining(&self, length: usize) -> ByteReaderError {
        ByteReaderError::InsufficientRemaining {
            requested: length,
            remaining: self.remaining(),
            offset: self.offset,
        }
    }
}

impl<'buffer> ByteReader for ByteReaderContainer<&'buffer [u8]> {
    type Buffer = &'buffer [u8];
    type RunAtReader<'run_at> = ByteReaderContainer<&'run_at [u8]>;
    type RunReader<'run> = ByteReaderContainer<&'run [u8]>;

    #[inline]
    fn offset(&self) -> usize {
        self.offset
    }

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

impl ByteReaderContainer<Vec<u8>> {
    /// Truncate the underlying buffer to the remaining bytes. The current offset will be updated accordingly.
    #[inline]
    pub(crate) fn truncate(&mut self) {
        let _ = self.buffer.drain(..self.offset);
        self.offset = 0;
    }

    /// Read all remaining bytes and consume the reader and its underlying buffer.
    #[inline]
    pub(crate) fn read_remaining_owned(mut self) -> Vec<u8> {
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
    fn offset(&self) -> usize {
        self.offset
    }

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

/// Wraps a [`&Vec<u8>`](Vec<u8>) and is otherwise identical to the [`OwnedVecByteReader`].
#[expect(dead_code, reason = "Will use later")]
pub(crate) type BorrowedVecByteReader<'buffer> = ByteReaderContainer<&'buffer Vec<u8>>;

#[cfg(test)]
mod tests {
    use super::*;

    const DATA: [u8; 25] = [
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    ];

    struct StateValidator {
        offset: usize,
        remaining: usize,
    }
    impl StateValidator {
        fn assert_processed(&mut self, reader: &mut impl ByteReader, n_processed: usize) {
            self.offset += n_processed;
            self.remaining -= n_processed;

            assert_eq!(reader.offset(), self.offset);
            assert_eq!(reader.remaining(), self.remaining);
        }
    }

    /// Test byte reading for an implementation of [`ByteReading`].
    ///
    /// The tests fail if the passed reader does not contain [`DATA`]
    fn test_byte_reader<Reader, Inner>(mut reader: Reader, inner: Inner)
    where
        Reader: ByteReader,
        Inner: Fn(&Reader::Buffer) -> &[u8],
    {
        // Initialize validator
        let mut validator = StateValidator {
            offset: 0,
            remaining: DATA.len(),
        };
        validator.assert_processed(&mut reader, 0);

        // Read byte [0]
        assert_eq!(reader.read_u8().unwrap(), 0);
        validator.assert_processed(&mut reader, 1);

        // Read bytes [1, 2] as u16 little endian
        assert_eq!(reader.read_u16_le().unwrap(), 513);
        validator.assert_processed(&mut reader, 2);

        // Read bytes [3, 4, 5, 6] as u32 little endian
        assert_eq!(reader.read_u32_le().unwrap(), 100_992_003);
        validator.assert_processed(&mut reader, 4);

        // Read bytes [7, 8, 9, 10, 11, 12, 13, 14] as u64 little endian
        assert_eq!(reader.read_u64_le().unwrap(), 1_012_478_732_780_767_239);
        validator.assert_processed(&mut reader, 8);

        // Skip byte 15
        reader.skip(1).unwrap();
        validator.assert_processed(&mut reader, 1);

        // Read bytes [16, 17, 18]
        assert_eq!(reader.read(3).unwrap(), &[16, 17, 18]);
        validator.assert_processed(&mut reader, 3);

        // Read range of length 2
        let range_19_20 = reader.read_range(2).unwrap();
        assert_eq!(range_19_20, validator.offset..(validator.offset + 2));
        validator.assert_processed(&mut reader, 2);

        // Check the last two bytes (i.e., [19, 20])
        assert_eq!(
            reader.run_at(-2, |mut reader| reader.read_u16_le()).unwrap(),
            5139,
        );
        validator.assert_processed(&mut reader, 0);

        // Skip the next byte (i.e., 21) and read u8 (i.e., 22)
        assert_eq!(reader.run_at(1, |mut reader| reader.read_u8()).unwrap(), 22);
        validator.assert_processed(&mut reader, 0);

        // Try to read out of bound
        assert!(
            reader
                .run_at(DATA.len().try_into().unwrap(), |mut reader| reader.read_u8())
                .is_err(),
        );
        validator.assert_processed(&mut reader, 0);

        // Get the remaining bytes
        let remaining_bytes = &DATA[validator.offset..];
        assert_eq!(reader.read_remaining(), remaining_bytes);
        validator.assert_processed(&mut reader, remaining_bytes.len());

        // Try to read some more out of range...
        assert!(reader.read_u8().is_err());
        // .. but with changed offset it should work
        assert_eq!(
            reader.run_at(-1, |mut reader| reader.read_u8()).unwrap(),
            DATA[DATA.len() - 1],
        );

        // Check that all data is read and compare underlying data
        let data = reader.expect_consumed().unwrap();
        assert_eq!(inner(&data), DATA);

        // Apply the range we read earlier against bytes [19, 20]
        assert_eq!(&inner(&data)[range_19_20], &[19, 20]);
    }

    #[test]
    fn slice_byte_reader() {
        let reader = SliceByteReader::new(&DATA);
        test_byte_reader(reader, |buffer| buffer);
    }

    #[test]
    fn owned_vec_byte_reader() {
        let reader = OwnedVecByteReader::new(DATA.to_vec());
        test_byte_reader(reader, |buffer| buffer);
    }

    #[test]
    fn borrowed_vec_byte_reader() {
        let data = DATA.to_vec();
        let reader = BorrowedVecByteReader::new(&data);
        test_byte_reader(reader, |buffer| buffer);
    }

    #[test]
    fn truncate() {
        let mut reader = OwnedVecByteReader::new(DATA.to_vec());
        assert_eq!(reader.buffer, DATA);
        assert_eq!(reader.buffer.len(), 25);

        // Truncate after offset 18 and ensure only the remaining data is available
        reader.skip(18).unwrap();
        reader.truncate();
        assert_eq!(reader.buffer, DATA[18..]);
    }
}
