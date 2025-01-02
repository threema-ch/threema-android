use logging::{init_logging, init_panic_dispatcher, LogDispatcher, LogLevel, PanicDispatcher};
use wasm_bindgen::prelude::*;

// The crypto dependencies require getrandom and we need to explicitly list it as a dependency
// when compiling for WASM since it requires the feature 'js'. If we don't declare this here,
// the linter will pick it up as an unused dependency.
mod external_crate_false_positives {
    use getrandom as _;
}

/// Logging utility to forward logging.
pub mod logging;

/// Bindings for the _Connection Rendezvous Protocol_.
pub mod d2d_rendezvous;

/// Initialise libthreema.
///
/// IMPORTANT: This must be called **once** before making any other calls to libthreema in order to
/// set up the panic and log dispatcher.
#[wasm_bindgen(js_name = init)]
pub fn init(panic_dispatcher: PanicDispatcher, logger: &LogDispatcher, min_log_level: LogLevel) {
    init_panic_dispatcher(panic_dispatcher);
    init_logging(logger, min_log_level);
}
