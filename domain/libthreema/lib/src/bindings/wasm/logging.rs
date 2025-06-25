//! Logging utility to forward logging.
use std::{io, panic};

use tracing::{Level, debug, metadata::LevelFilter};
use tracing_subscriber::fmt::MakeWriter;
use wasm_bindgen::prelude::*;

#[wasm_bindgen(typescript_custom_section)]
const PANIC_DISPATCHER: &'static str = r#"
/**
 * Dispatches panics raised in libthreema.
 *
 * IMPORTANT: The panic handler should still crash the application, just more gracefully and with
 * the provided information.
 */
interface PanicDispatcher {
    readonly handle: (info: string) => void;
}
"#;

#[wasm_bindgen]
extern "C" {
    /// Dispatches panics raised in libthreema.
    #[wasm_bindgen(
        extends = ::js_sys::Object,
        js_name = PanicDispatcher,
        typescript_type = "PanicDispatcher"
    )]
    #[derive(Debug, Clone)]
    pub type PanicDispatcher;

    /// Pass the panic info to the panic dispatcher
    #[wasm_bindgen(method, js_class = "handle", js_name = handle)]
    pub fn handle(this: &PanicDispatcher, panic_info: &str);
}

// SAFETY: Safe as long as WASM is single-threaded
unsafe impl Send for PanicDispatcher {}
// SAFETY: Safe as long as WASM is single-threaded
unsafe impl Sync for PanicDispatcher {}

#[wasm_bindgen(typescript_custom_section)]
const LOG_DISPATCHER: &'static str = r#"
/** Handle a log record. */
type LogRecordFn = (record: string) => void;

/** Dispatches log records from libthreema. */
interface LogDispatcher {
    readonly debug: LogRecordFn;
    readonly info: LogRecordFn;
    readonly warn: LogRecordFn;
    readonly error: LogRecordFn;
}
"#;

// Note: Unfortunately we cannot use string enums:
// https://github.com/rustwasm/wasm-bindgen/issues/2153
#[wasm_bindgen(typescript_custom_section)]
const LOG_LEVEL: &'static str = r#"
/**
 * Log levels used by libthreema.
 *
 * - `'trace'`: Extremely verbose, often leaking sensitive data. Only use this for specific
 *   debugging sessions.
 * - `'debug'`: Verbose. Should not leak sensitive data and is therefore safe to use in debug
 *   builds.
 * - `'info'`: Useful information. Should be used by release builds.
 * - `'warn'`: Warns about unexpected situations.
 * - `'error'`: Indicates an internal error has been encountered.
 */
type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error';
"#;

#[wasm_bindgen]
extern "C" {
    /// Handle a log record
    #[wasm_bindgen(
        extends = ::js_sys::Object,
        js_name = LogDispatcher,
        typescript_type = "LogDispatcher"
    )]
    #[derive(Debug, Clone)]
    pub type LogDispatcher;

    /// Log a record at the debug level
    #[wasm_bindgen(method, js_class = "LogDispatcher", js_name = debug)]
    pub fn debug(this: &LogDispatcher, record: &str);

    /// Log a record at the info level
    #[wasm_bindgen(method, js_class = "LogDispatcher", js_name = info)]
    pub fn info(this: &LogDispatcher, record: &str);

    /// Log a record at the warn level
    #[wasm_bindgen(method, js_class = "LogDispatcher", js_name = warn)]
    pub fn warn(this: &LogDispatcher, record: &str);

    /// Log a record at the error level
    #[wasm_bindgen(method, js_class = "LogDispatcher", js_name = error)]
    pub fn error(this: &LogDispatcher, record: &str);

    /// Describes the level of verbosity of the logging
    #[wasm_bindgen(extends = ::js_sys::Object, js_name = LogLevel, typescript_type = "LogLevel")]
    pub type LogLevel;
}

// SAFETY: Safe as long as WASM is single-threaded
unsafe impl Send for LogDispatcher {}
// SAFETY: Safe as long as WASM is single-threaded
unsafe impl Sync for LogDispatcher {}

impl From<LogLevel> for LevelFilter {
    fn from(level: LogLevel) -> LevelFilter {
        match level.as_string().as_deref() {
            Some("trace") => LevelFilter::TRACE,
            Some("debug") => LevelFilter::DEBUG,
            Some("info") => LevelFilter::INFO,
            Some("warn") => LevelFilter::WARN,
            Some("error") => LevelFilter::ERROR,
            Some(_) | None => LevelFilter::OFF,
        }
    }
}

struct DispatchWriter {
    buffer: Vec<u8>,
    level: Option<Level>,
    dispatcher: LogDispatcher,
}

impl io::Write for DispatchWriter {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        self.buffer.write(buffer)
    }

    fn flush(&mut self) -> io::Result<()> {
        // Nothing to-do here, we instead flush on drop
        Ok(())
    }
}

impl Drop for DispatchWriter {
    fn drop(&mut self) {
        let record = String::from_utf8_lossy(&self.buffer);
        match self.level {
            Some(Level::DEBUG | Level::TRACE) | None => self.dispatcher.debug(&record),
            Some(Level::INFO) => self.dispatcher.info(&record),
            Some(Level::WARN) => self.dispatcher.warn(&record),
            Some(Level::ERROR) => self.dispatcher.error(&record),
        }
    }
}

struct MakeDispatchWriter {
    dispatcher: LogDispatcher,
}

impl MakeDispatchWriter {
    fn new(dispatcher: LogDispatcher) -> Self {
        Self { dispatcher }
    }
}

impl<'writer> MakeWriter<'writer> for MakeDispatchWriter {
    type Writer = DispatchWriter;

    fn make_writer(&'writer self) -> Self::Writer {
        Self::Writer {
            buffer: vec![],
            level: None,
            dispatcher: self.dispatcher.clone(),
        }
    }

    fn make_writer_for(&'writer self, meta: &tracing::Metadata<'_>) -> Self::Writer {
        Self::Writer {
            buffer: vec![],
            level: Some(*meta.level()),
            dispatcher: self.dispatcher.clone(),
        }
    }
}

/// Initialise the panic dispatcher (set it as the panic hook).
pub(super) fn init_panic_dispatcher(panic_dispatcher: PanicDispatcher) {
    // Set panic hook
    panic::set_hook(Box::new(move |info| {
        #[wasm_bindgen]
        extern "C" {
            type Error;

            #[wasm_bindgen(constructor, final)]
            fn new() -> Error;

            #[wasm_bindgen(method, getter)]
            fn stack(error: &Error) -> String;
        }

        // Stolen from https://github.com/rustwasm/console_error_panic_hook/blob/master/src/lib.rs
        // which also explains why the message is formatted as such (gist: browsers doing browser
        // things).
        let mut message = info.to_string();
        message.push_str("\n\nStack:\n\n");
        let error = Error::new();
        message.push_str(&error.stack());
        message.push_str("\n\n");

        // Call panic dispatcher
        panic_dispatcher.handle(&message);
    }));
}

/// Initialise logging with the provided log dispatcher and minimum log level.
///
/// IMPORTANT: This may only be called **once**!
pub(super) fn init_logging(log_dispatcher: &LogDispatcher, min_log_level: LogLevel) {
    // Configure tracing
    let level_filter: LevelFilter = min_log_level.into();
    let subscriber = tracing_subscriber::fmt()
        .with_max_level(level_filter)
        .with_ansi(false)
        .with_target(false)
        .with_level(false)
        .without_time()
        .with_writer(MakeDispatchWriter::new(log_dispatcher.clone()))
        .finish();
    tracing::subscriber::set_global_default(subscriber)
        .expect("Cannot initialize logging multiple times. Did you call this more than once?");
    debug!(?level_filter, "Configured log level filter");
}
