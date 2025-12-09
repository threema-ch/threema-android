//! Apply traits for in-place modification.

/// Modify self by applying a provided value.
pub(crate) trait Apply<T>: Sized {
    /// Apply the provided value, modifying self.
    fn apply(&mut self, value: T);

    /// Apply the provided value, modifying and returning self.
    #[inline]
    fn chain_apply(mut self, value: T) -> Self {
        self.apply(value);
        self
    }
}

/// Try modifying self by applying a provided value.
pub(crate) trait TryApply<T>: Sized {
    /// The type returned in the event of an error. If this is returned, self must not have been modified.
    type Error;

    /// Try applying the provided value, modifying self.
    ///
    /// IMPORTANT: In case of an error, self must not be modified!
    fn try_apply(&mut self, value: T) -> Result<(), Self::Error>;

    /// Try applying the provided value, modifying and returning self.
    #[expect(dead_code, reason = "May use later")]
    #[inline]
    fn try_chain_apply(mut self, value: T) -> Result<Self, Self::Error> {
        self.try_apply(value)?;
        Ok(self)
    }
}
