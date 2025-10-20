//! Sequence numbers.
use duplicate::duplicate_item;

/// Exhausted the available sequence numbers.
#[derive(Clone, Debug, thiserror::Error)]
#[error("Sequence number would overflow")]
pub struct SequenceNumberOverflow;

/// A sequence number value, safely yielded by calling [`SequenceNumberU32::get_and_increment`] resp.
/// [`SequenceNumberU64::get_and_increment`].
pub(crate) struct SequenceNumberValue<T>(pub(crate) T);

/// A generic 64-bit unsigned sequence number. Prevents wrapping.
#[duplicate_item(
    struct_name           value_type;
    [ SequenceNumberU32 ] [ u32 ];
    [ SequenceNumberU64 ] [ u64 ];
)]
#[derive(Debug)]
pub(crate) struct struct_name {
    value: value_type,
}

/// A generic 64-bit unsigned sequence number. Prevents wrapping.
#[duplicate_item(
    struct_name           value_type;
    [ SequenceNumberU32 ] [ u32 ];
    [ SequenceNumberU64 ] [ u64 ];
)]
#[expect(clippy::allow_attributes, reason = "duplicate shenanigans")]
impl struct_name {
    /// Create a new sequence number starting with `start`.
    #[allow(dead_code, reason = "Will use later")]
    pub(crate) fn new(start: value_type) -> Self {
        Self { value: start }
    }

    /// Return the next sequence number (i.e. the current value plus one). Increases the internal
    /// value by one.
    ///
    /// # Errors
    ///
    /// Return a [`SequenceNumberOverflow`] if the increment would result in an overflow.
    pub(crate) fn get_and_increment(
        &mut self,
    ) -> Result<SequenceNumberValue<value_type>, SequenceNumberOverflow> {
        let next = self.value;
        self.value = self.value.checked_add(1).ok_or(SequenceNumberOverflow)?;
        Ok(SequenceNumberValue(next))
    }
}
