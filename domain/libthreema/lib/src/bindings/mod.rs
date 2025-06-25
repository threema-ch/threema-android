//! FFI bindings of this library.
#[cfg(feature = "uniffi")]
pub mod uniffi;
#[cfg(feature = "wasm")]
pub mod wasm;
