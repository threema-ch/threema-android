//! Byte writer.
use core::ptr;

use duplicate::duplicate_item;

/// An error occurred while writing bytes.
#[derive(Clone, Debug, thiserror::Error)]
pub enum ByteWriterError {
    /// Provided relative offset would move the writer offset out of bounds.
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

    /// Insufficient space left for the desired operation
    #[error(
        "Insufficient space left: required-space={required_space}, available-space={available_space}, \
         offset={offset}"
    )]
    InsufficientSpace {
        /// Required amount of space for the write operation.
        required_space: usize,
        /// Available amount of space left for writing.
        available_space: String,
        /// Current offset in the underlying data buffer.
        offset: usize,
    },
}

pub(crate) trait ByteWriter {
    /// Writer type used for [`ByteWriter::run`].
    type RunWriter<'run>: ByteWriter;

    /// Writer type used for [`ByteWriter::run_at`].
    type RunAtWriter<'run_at>: ByteWriter;

    /// Return the current offset of the writer.
    fn offset(&self) -> usize;

    /// Run an arbitrary operation on the writer.
    ///
    /// This is useful when chaining multiple write operations that can be mapped to a single error.
    ///
    /// # Error
    ///
    /// Returns any [`ByteWriterError`] returned by the operation.
    fn run<T, F: FnOnce(&mut Self::RunWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteWriterError>;

    /// Run an arbitrary operation on the writer at a relative offset to the current offset in a
    /// child reader instance. The parent reader will maintain the current offset.
    ///
    /// If the underlying buffer is extendable and not large enough to facilitiate `offset`, newly
    /// allocated space up until `offset` will be zerofilled.
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the `offset` would move outside of the
    /// underlying buffer boundary.
    ///
    /// Returns any [`ByteWriterError`] returned by the operation.
    fn run_at<T, F: FnOnce(Self::RunAtWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteWriterError>;

    /// Write a sequence of bytes.
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the writer offset would move outside
    /// of the underlying buffer boundary.
    fn write(&mut self, bytes: &[u8]) -> Result<(), ByteWriterError>;

    /// Borrow a mutable slice for direct in-place writing.
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the `offset` would move outside of the underlying
    /// buffer boundary.
    fn write_in_place(&mut self, length: usize) -> Result<&mut [u8], ByteWriterError>;

    /// Write a u8.
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the writer offset would move outside
    /// of the underlying buffer boundary.
    #[inline]
    fn write_u8(&mut self, value: u8) -> Result<(), ByteWriterError> {
        self.write(&[value])
    }

    /// Write a u16 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the writer offset would move outside
    /// of the underlying buffer boundary.
    #[inline]
    fn write_u16_le(&mut self, value: u16) -> Result<(), ByteWriterError> {
        self.write(&value.to_le_bytes())
    }

    /// Write a u32 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the writer offset would move outside
    /// of the underlying buffer boundary.
    #[inline]
    fn write_u32_le(&mut self, value: u32) -> Result<(), ByteWriterError> {
        self.write(&value.to_le_bytes())
    }

    /// Write a u64 (little endian).
    ///
    /// # Error
    ///
    /// Returns [`ByteWriterError::InsufficientSpace`] if the writer offset would move outside
    /// of the underlying buffer boundary.
    #[inline]
    fn write_u64_le(&mut self, value: u64) -> Result<(), ByteWriterError> {
        self.write(&value.to_le_bytes())
    }
}

pub(crate) struct ByteWriterContainer<T> {
    buffer: T,
    offset: usize,
}

#[duplicate_item(
    buffer_type;
    [ &'buffer mut [u8] ];
    [ Vec<u8> ];
    [ &'buffer mut Vec<u8> ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
#[allow(clippy::extra_unused_lifetimes, reason = "Abstracted duplicate")]
impl<'buffer> ByteWriterContainer<buffer_type> {
    /// Create a new byte writer starting at the **beginning** of the `buffer`.
    #[inline]
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) fn new(buffer: buffer_type) -> Self {
        Self { buffer, offset: 0 }
    }

    /// Consume this writer, returning the underlying buffer.
    #[inline]
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) fn into_inner(self) -> buffer_type {
        self.buffer
    }
}

#[duplicate_item(
    buffer_type;
    [ &mut [u8] ];
)]
impl ByteWriterContainer<buffer_type> {
    /// Return the amount of remaining space left in the underlying buffer of the writer.
    #[inline]
    pub(crate) fn space(&self) -> usize {
        self.buffer
            .len()
            .checked_sub(self.offset)
            .expect("length must be >= offset and therefore usize")
    }

    #[inline]
    fn insufficient_space(&self, length: usize) -> ByteWriterError {
        ByteWriterError::InsufficientSpace {
            required_space: length,
            available_space: self.space().to_string(),
            offset: self.offset,
        }
    }
}

#[duplicate_item(
    buffer_type   run_writer_type    run_at_writer_type;
    [ &mut [u8] ] [ &'run mut [u8] ] [ &'run_at mut [u8] ];
)]
impl ByteWriter for ByteWriterContainer<buffer_type> {
    type RunAtWriter<'run_at> = ByteWriterContainer<run_at_writer_type>;
    type RunWriter<'run> = ByteWriterContainer<run_writer_type>;

    #[inline]
    fn offset(&self) -> usize {
        self.offset
    }

    #[inline]
    fn run<T, F: FnOnce(&mut Self::RunWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteWriterError> {
        op(self)
    }

    #[inline]
    fn run_at<T, F: FnOnce(Self::RunAtWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteWriterError> {
        let invalid_offset = || ByteWriterError::InvalidRelativeOffset {
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
        op(Self::RunAtWriter {
            buffer: self.buffer,
            offset: updated_offset,
        })
    }

    #[inline]
    fn write(&mut self, bytes: &[u8]) -> Result<(), ByteWriterError> {
        let length = bytes.len();
        let insufficient_space = || self.insufficient_space(length);

        // Calculate new offset
        let updated_offset = self.offset.checked_add(length).ok_or_else(insufficient_space)?;

        // Check if there's sufficient space
        if updated_offset > self.buffer.len() {
            return Err(insufficient_space());
        }

        // Write bytes
        self.buffer
            .get_mut(self.offset..updated_offset)
            .expect("[offset..updated_offset] must be valid after space check")
            .copy_from_slice(bytes);
        self.offset = updated_offset;
        Ok(())
    }

    #[inline]
    fn write_in_place(&mut self, length: usize) -> Result<&mut [u8], ByteWriterError> {
        let insufficient_space = || self.insufficient_space(length);

        // Calculate new offset
        let updated_offset = self.offset.checked_add(length).ok_or_else(insufficient_space)?;

        // Check if there's sufficient space
        if updated_offset > self.buffer.len() {
            return Err(insufficient_space());
        }

        // Update offset and return borrowed mutable buffer
        let buffer = self
            .buffer
            .get_mut(self.offset..updated_offset)
            .expect("[offset..updated_offset] must be valid after space check");
        self.offset = updated_offset;
        Ok(buffer)
    }
}

#[duplicate_item(
    buffer_type;
    [ Vec<u8> ];
    [ &'buffer mut Vec<u8> ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
#[allow(clippy::extra_unused_lifetimes, reason = "Abstracted duplicate")]
impl<'buffer> ByteWriterContainer<buffer_type> {
    /// Create a new byte writer starting at the **end** of the `buffer`.
    #[inline]
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn new_extending(buffer: buffer_type) -> Self {
        let offset = buffer.len();
        Self { buffer, offset }
    }

    #[inline]
    fn insufficient_space(&self, length: usize) -> ByteWriterError {
        ByteWriterError::InsufficientSpace {
            required_space: length,
            available_space: "unlimited".to_owned(),
            offset: self.offset,
        }
    }
}

impl ByteWriterContainer<Vec<u8>> {
    /// Create a new byte writer with an empty buffer without any pre-allocated capacity.
    #[inline]
    pub(crate) fn new_empty() -> Self {
        Self::new(Vec::new())
    }

    /// Create a new byte writer with an empty buffer pre-allocated with `capacity`.
    #[inline]
    pub(crate) fn new_with_capacity(capacity: usize) -> Self {
        Self::new(Vec::with_capacity(capacity))
    }
}

#[duplicate_item(
    buffer_type      run_writer_type       run_at_writer_type;
    [ &mut Vec<u8> ] [ &'run mut Vec<u8> ] [ &'run_at mut Vec<u8> ];
    [ Vec<u8> ]      [ Vec<u8> ]           [ &'run_at mut Vec<u8> ];
)]
impl ByteWriter for ByteWriterContainer<buffer_type> {
    type RunAtWriter<'run_at> = ByteWriterContainer<run_at_writer_type>;
    type RunWriter<'run> = ByteWriterContainer<run_writer_type>;

    #[inline]
    fn offset(&self) -> usize {
        self.offset
    }

    #[inline]
    fn run<T, F: FnOnce(&mut Self::RunWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        op: F,
    ) -> Result<T, ByteWriterError> {
        op(self)
    }

    #[inline]
    fn run_at<T, F: FnOnce(Self::RunAtWriter<'_>) -> Result<T, ByteWriterError>>(
        &mut self,
        relative_offset: isize,
        op: F,
    ) -> Result<T, ByteWriterError> {
        let invalid_offset = || ByteWriterError::InvalidRelativeOffset {
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

        // Extend (zerofill) first if we need to (but don't truncate).
        if updated_offset > self.buffer.len() {
            self.buffer.resize(updated_offset, 0x00);
        }

        // Run
        op(Self::RunAtWriter {
            buffer: &mut self.buffer,
            offset: updated_offset,
        })
    }

    #[inline]
    fn write(&mut self, bytes: &[u8]) -> Result<(), ByteWriterError> {
        let length = bytes.len();

        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(|| self.insufficient_space(length))?;

        // Check if we can take a shortcut (extending at the end of the underlying buffer)
        if self.offset == self.buffer.len() {
            // Extend from bytes slice
            self.buffer.extend_from_slice(bytes);
        } else {
            // Extend (zerofill) first if we need to (but don't truncate).
            //
            // Note: We need to do this, otherwise writing to the mutable slice would fail.
            if updated_offset > self.buffer.len() {
                self.buffer.resize(updated_offset, 0x00);
            }

            // Write bytes
            self.buffer
                .get_mut(self.offset..updated_offset)
                .expect("[offset..updated_offset] must be valid after resizing")
                .copy_from_slice(bytes);
        }

        // Update offset
        self.offset = updated_offset;
        Ok(())
    }

    #[inline]
    fn write_in_place(&mut self, length: usize) -> Result<&mut [u8], ByteWriterError> {
        // Calculate new offset
        let updated_offset = self
            .offset
            .checked_add(length)
            .ok_or_else(|| self.insufficient_space(length))?;

        // Extend (zerofill) first if we need to (but don't truncate).
        if updated_offset > self.buffer.len() {
            self.buffer.resize(updated_offset, 0x00);
        }

        // Update offset and return borrowed mutable buffer
        let buffer = self
            .buffer
            .get_mut(self.offset..updated_offset)
            .expect("[offset..updated_offset] must be valid after space check");
        self.offset = updated_offset;
        Ok(buffer)
    }
}

/// Wraps a [`&mut [u8]`] and allows to apply write operations safely within the constrained space.
#[expect(dead_code, reason = "Will use later")]
pub(crate) type SliceByteWriter<'buffer> = ByteWriterContainer<&'buffer mut [u8]>;

/// Wraps or creates a [`Vec<u8>`] and allows to apply write operations. The [`Vec<u8>`] will be
/// extended automatically whenever needed. It is however a good idea to initialize it with its
/// expected final length to mitigate unnecessary re-allocations.
pub(crate) type OwnedVecByteWriter = ByteWriterContainer<Vec<u8>>;

/// Wraps a [`&mut Vec<u8>`] and is otherwise identical to the [`OwnedVecByteWriter`].
#[expect(dead_code, reason = "Will use later")]
pub(crate) type BorrowedVecByteWriter<'buffer> = ByteWriterContainer<&'buffer mut Vec<u8>>;

// TODO(LIB-31): Should be unnecessary
pub(crate) trait InsertSlice {
    fn insert_at(&mut self, offset: usize, bytes: &[u8]);
}
impl InsertSlice for Vec<u8> {
    /// Inserts a [`u8`] slice at a specific offset.
    ///
    /// This is an *O*(*n*) operation and usually less efficient than concatenating items to a
    /// [`Vec<u8>`].
    ///
    /// Note: This is stolen from [`String::insert_str`].
    ///
    /// # Panics
    ///
    /// Panics if `offset` is larger than the `Vec<u8>`'s length.
    #[inline]
    fn insert_at(&mut self, offset: usize, bytes: &[u8]) {
        let current_length = self.len();
        let additional_length = bytes.len();

        // Make room for the additional bytes
        self.reserve(additional_length);

        #[expect(
            clippy::arithmetic_side_effects,
            clippy::multiple_unsafe_ops_per_block,
            reason = "TODO(LIB-16)"
        )]
        // # Safety: The underlying vector MUST contain bytes.
        unsafe {
            // Move the existing bytes at the specific offset by the amount of bytes to add
            ptr::copy(
                self.as_ptr().add(offset),
                self.as_mut_ptr().add(offset + additional_length),
                current_length - offset,
            );

            // Copy the bytes to the correct offset
            ptr::copy_nonoverlapping(bytes.as_ptr(), self.as_mut_ptr().add(offset), additional_length);

            self.set_len(current_length + additional_length);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Test byte writing for an implementation of [`ByteWriter`].
    ///
    /// `extendable` indicates whether the inner buffer of `writer` can extend itself by allocating more
    /// space.
    ///
    /// The tests fail if the passed writer is not zero-filled initially.
    fn test_byte_writer<TWriter: ByteWriter, TUnwrapFn: Fn(&TWriter) -> &[u8]>(
        mut writer: TWriter,
        extendable: bool,
        inner: TUnwrapFn,
    ) {
        {
            // Sanity check: The passed writer is zero-filled
            let buffer = inner(&writer);
            assert!(buffer.iter().all(|byte| *byte == 0));
        }

        // Write multiple groups of bytes (the written bytes are in increasing order to facilitate testing)
        writer.write_u8(1).unwrap();
        writer.write_u16_le(u16::from_le_bytes([2, 3])).unwrap();
        writer.write_u32_le(u32::from_le_bytes([4, 5, 6, 7])).unwrap();
        writer
            .write_u64_le(u64::from_le_bytes([8, 9, 10, 11, 12, 13, 14, 15]))
            .unwrap();
        writer.write(&[16, 17, 18]).unwrap();
        {
            let buffer = writer.write_in_place(3).unwrap();
            assert_eq!(buffer.len(), 3);

            buffer.copy_from_slice(&[19, 20, 21]);
        }
        assert_eq!(inner(&writer)[..21], (1..=21).collect::<Vec<_>>());

        // Catch out of bounds writing
        assert_eq!(writer.offset(), 21);
        assert!(writer.run_at(-22, |mut writer| writer.write(&[0, 0])).is_err());

        if extendable {
            let offset = writer.offset();
            assert_eq!(offset, 21);

            // Extendable: We should be able to add more bytes
            writer.write_u8(22).unwrap();
            writer.write(&[23]).unwrap();

            let buffer = inner(&writer);
            assert_eq!(buffer[offset..], [22, 23]);
        } else {
            // Not extendable: Moving beyond the offset should fail
            assert!(writer.write_u8(1).is_err());
            assert!(writer.write(&[0]).is_err());
        }
    }

    #[test]
    fn slice_byte_writer() {
        let mut buffer: [u8; 21] = [0; 21];
        let writer = SliceByteWriter::new(&mut buffer);

        test_byte_writer(writer, false, |writer| writer.buffer);
    }

    #[test]
    fn owned_vec_byte_writer() {
        let writer = OwnedVecByteWriter::new_with_capacity(21);
        test_byte_writer(writer, true, |writer| &writer.buffer);

        let writer = OwnedVecByteWriter::new_empty();
        test_byte_writer(writer, true, |writer| &writer.buffer);
    }

    #[test]
    fn borrowed_vec_byte_writer() {
        let mut buffer: Vec<u8> = vec![];
        let writer = BorrowedVecByteWriter::new(&mut buffer);
        test_byte_writer(writer, true, |writer| writer.buffer);

        let mut buffer: Vec<u8> = Vec::with_capacity(21);
        let writer = BorrowedVecByteWriter::new(&mut buffer);
        test_byte_writer(writer, true, |writer| writer.buffer);
    }
}
