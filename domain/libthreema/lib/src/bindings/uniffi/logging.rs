//! Logging utility to forward logging.
use std::{io, sync::Arc};

use tracing::{Level, debug};
use tracing_subscriber::{filter::LevelFilter, fmt::MakeWriter};

/// Log levels used by libthreema.
#[derive(Clone, Copy, Debug, uniffi::Enum)]
pub enum LogLevel {
    /// Extremely verbose, often leaking sensitive data. Only use this for specific debugging
    /// sessions.
    Trace,
    /// Verbose. Should not leak sensitive data and is therefore safe to use in debug builds.
    Debug,
    /// Useful information. Should be used by release builds.
    Info,
    /// Warns about unexpected situations.
    Warn,
    /// Indicates an internal error has been encountered.
    Error,
}

impl From<LogLevel> for LevelFilter {
    fn from(level: LogLevel) -> LevelFilter {
        match level {
            LogLevel::Trace => LevelFilter::TRACE,
            LogLevel::Debug => LevelFilter::DEBUG,
            LogLevel::Info => LevelFilter::INFO,
            LogLevel::Warn => LevelFilter::WARN,
            LogLevel::Error => LevelFilter::ERROR,
        }
    }
}

impl From<Level> for LogLevel {
    fn from(level: Level) -> Self {
        match level {
            Level::TRACE => LogLevel::Trace,
            Level::DEBUG => LogLevel::Debug,
            Level::INFO => LogLevel::Info,
            Level::WARN => LogLevel::Warn,
            Level::ERROR => LogLevel::Error,
        }
    }
}
/// Dispatches log records from libthreema.
#[uniffi::export(with_foreign)]
pub trait LogDispatcher: Send + Sync {
    /// Handle a log record.
    ///
    /// # Errors
    ///
    /// This function is considered infallible and should not return an error. If it does however,
    /// libthreema will discard the error.
    fn log(&self, level: LogLevel, record: String) -> Result<(), super::InfallibleError>;
}

struct DispatchWriter {
    buffer: Vec<u8>,
    level: LogLevel,
    dispatcher: Arc<dyn LogDispatcher>,
}

impl io::Write for DispatchWriter {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        self.buffer.write(buffer.trim_ascii_end())
    }

    fn flush(&mut self) -> io::Result<()> {
        // Nothing to-do here, we instead flush on drop
        Ok(())
    }
}

impl Drop for DispatchWriter {
    fn drop(&mut self) {
        let record = String::from_utf8_lossy(&self.buffer).to_string();
        // Ignoring result as there's not much we can do here other than to log... which will likely
        // fail again
        let _ = self.dispatcher.log(self.level, record);
    }
}

struct MakeDispatchWriter {
    dispatcher: Arc<dyn LogDispatcher>,
}

impl MakeDispatchWriter {
    fn new(dispatcher: Arc<dyn LogDispatcher>) -> Self {
        Self { dispatcher }
    }
}

impl<'writer> MakeWriter<'writer> for MakeDispatchWriter {
    type Writer = DispatchWriter;

    fn make_writer(&'writer self) -> Self::Writer {
        Self::Writer {
            buffer: vec![],
            level: LogLevel::Debug,
            dispatcher: self.dispatcher.clone(),
        }
    }

    fn make_writer_for(&'writer self, meta: &tracing::Metadata<'_>) -> Self::Writer {
        Self::Writer {
            buffer: vec![],
            level: (*meta.level()).into(),
            dispatcher: self.dispatcher.clone(),
        }
    }
}

/// Initialise logging with the provided log dispatcher and minimum log level.
///
/// IMPORTANT: This may only be called **once**!
pub(super) fn init_logging(min_log_level: LogLevel, log_dispatcher: Arc<dyn LogDispatcher>) {
    // Configure tracing
    let level_filter: LevelFilter = min_log_level.into();
    let subscriber = tracing_subscriber::fmt()
        .with_max_level(level_filter)
        .with_ansi(false)
        .with_target(false)
        .with_level(false)
        .without_time()
        .with_writer(MakeDispatchWriter::new(log_dispatcher))
        .finish();
    tracing::subscriber::set_global_default(subscriber)
        .expect("Cannot initialize logging multiple times. Did you call this more than once?");
    debug!(?level_filter, "Configured log level filter");
}
