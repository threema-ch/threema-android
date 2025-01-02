use std::sync::{Mutex, MutexGuard, PoisonError};

/// Extension for [`Mutex`] to ignore and disregard any poison introduced by a thread panicking
/// while holding the lock.
pub(crate) trait MutexIgnorePoison {
    type T;

    /// Acquires a mutex, blocking the current thread until it is able to do so, ignoring any mutex
    /// poison.
    fn lock_ignore_poison(&self) -> MutexGuard<'_, Self::T>;
}

impl<T> MutexIgnorePoison for Mutex<T> {
    type T = T;

    fn lock_ignore_poison(&self) -> MutexGuard<'_, Self::T> {
        self.lock().unwrap_or_else(PoisonError::into_inner)
    }
}
