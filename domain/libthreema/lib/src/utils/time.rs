//! Time-related utilities.
use core::fmt;
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

/// Clock drift direction (ahead/behind) against some reference.
enum DriftDirection {
    Ahead,
    Behind,
}
impl DriftDirection {
    fn apply(&self, delta: f64) -> f64 {
        match self {
            DriftDirection::Ahead => delta,
            DriftDirection::Behind =>
            {
                #[expect(clippy::float_arithmetic, reason = "Fine here")]
                -delta
            },
        }
    }
}

/// Clock delta between the system's time and a reference time.
pub struct ClockDelta {
    delta: Duration,
    direction: DriftDirection,
}
impl ClockDelta {
    /// Calculate the clock delta between the system's time and a reference UTC timestamp.
    #[must_use]
    pub fn calculate(reference_timestamp: Duration) -> Self {
        let system_timestamp = utc_now();
        Self {
            delta: system_timestamp.abs_diff(reference_timestamp),
            direction: if system_timestamp > reference_timestamp {
                DriftDirection::Ahead
            } else {
                DriftDirection::Behind
            },
        }
    }

    /// Returns the number of seconds the system's clock is ahead/behind reference.
    #[must_use]
    pub fn to_seconds(&self) -> f64 {
        self.direction.apply(self.delta.as_secs_f64())
    }
}
impl fmt::Debug for ClockDelta {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let (sign, direction) = match self.direction {
            DriftDirection::Ahead => ('+', "ahead"),
            DriftDirection::Behind => ('-', "behind"),
        };
        write!(formatter, "{sign}{delta:?} ({direction})", delta = self.delta)
    }
}
