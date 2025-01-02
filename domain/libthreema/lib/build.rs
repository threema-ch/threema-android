//! Builds libthreema (d'oh!).

use std::io::Result;

#[cfg(feature = "uniffi")]
use uniffi as _;

fn main() -> Result<()> {
    // Compile protobuf
    prost_build::compile_protos(
        &[
            "../threema-protocols/src/common.proto",
            "../threema-protocols/src/csp-e2e.proto",
            "../threema-protocols/src/md-d2d-rendezvous.proto",
        ],
        &["../threema-protocols/src/"],
    )?;

    // Done
    Ok(())
}
