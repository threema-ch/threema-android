//! Time-related utilities.
#[cfg(not(feature = "wasm"))]
pub(crate) use std::time::*;

#[cfg(feature = "wasm")]
pub(crate) use web_time::*;

/// Get the current UTC timestamp.
///
/// # Panics
///
/// When time travel occurs.
#[must_use]
pub(crate) fn utc_now() -> Duration {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("Time travel occurred")
}

/// Get the current UTC timestamp in milliseconds.
///
/// # Panics
///
/// When it's the year 584556019.
#[must_use]
pub(crate) fn utc_now_ms() -> u64 {
    utc_now()
        .as_millis()
        .try_into()
        .expect("Party like it's 584556019")
}
