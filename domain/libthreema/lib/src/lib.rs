//! # libthreema
//!
//! The one stop shop for all your Threema needs!

// We have pretty strict linting rules and it would be a massive hassle having to conditionally
// compile all `pub(crate)` exposed items, depending on the selected features.
//
// The goal is to get it right with both features `uniffi` and `wasm` enabled (and of course, all
// feature variants still need to compile).
//
// However, it does make sense to disable this from time to time to check for actual issues.
#![cfg_attr(
    not(all(feature = "uniffi", feature = "wasm")),
    allow(dead_code, unused_crate_dependencies, unused_imports)
)]

// Avoids dev dependencies used only in examples to be picked up by the linter. Should no longer be
// necessary once https://github.com/rust-lang/cargo/issues/1982 has been resolved.
#[cfg(test)]
mod external_crate_false_positives {
    use anyhow as _;
}

// Set up UniFFI scaffolding for UniFFI bindings
#[cfg(feature = "uniffi")]
uniffi::setup_scaffolding!();

/// Compiled low-level protobuf messages.
#[allow(
    unnameable_types,
    unreachable_pub,
    clippy::pedantic,
    clippy::restriction
)]
mod protobuf {
    pub mod common {
        include!(concat!(env!("OUT_DIR"), "/common.rs"));
    }
    pub mod csp_e2e {
        include!(concat!(env!("OUT_DIR"), "/csp_e2e.rs"));
    }
    pub mod d2d_rendezvous {
        include!(concat!(env!("OUT_DIR"), "/rendezvous.rs"));
    }
}

/// Higher-level wrappers around crypto libraries used and some commonly used abstractions.
///
/// Note: Always use this module instead of using the crypto dependencies directly!
pub(crate) mod crypto;

/// Time-related utilities.
pub(crate) mod time;

/// Sync-related utilities.
pub(crate) mod sync;

/// Common items needed in many places.
pub mod common;

/// Implementation of the _Connection Rendezvous Protocol_.
pub mod d2d_rendezvous;

/// Implementation of the Threema ID Backup
pub mod id_backup;

/// FFI bindings of this library.
pub mod bindings;
