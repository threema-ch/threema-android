//! UniFFI bindings.
use std::sync::Arc;

use logging::{LogDispatcher, LogLevel, init_logging};

pub mod crypto;
pub mod d2d_rendezvous;
pub mod id_backup;
pub mod logging;

/// Used for (foreign) functions that are considered infallible. In case the foreign function fails,
/// this error will be propagated back.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum InfallibleError {
    /// A foreign function considered infallible returned an error.
    #[error("Infallible function failed in foreign code: {0}")]
    Foreign(String),
}

impl From<uniffi::UnexpectedUniFFICallbackError> for InfallibleError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Foreign(error.reason)
    }
}

/// Initialise libthreema.
///
/// IMPORTANT: This must be called **once** before making any other calls to libthreema in order to
/// set up the log dispatcher.
#[uniffi::export]
pub fn init(min_log_level: LogLevel, log_dispatcher: Arc<dyn LogDispatcher>) {
    init_logging(min_log_level, log_dispatcher);
}
