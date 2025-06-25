//! Builds libthreema (d'oh!).

use std::io::Result;

#[cfg(feature = "uniffi")]
use uniffi as _;

fn main() -> Result<()> {
    // Compile protobuf
    println!("cargo:rerun-if-changed=../threema-protocols/src/");
    prost_build::Config::new().enable_type_names().compile_protos(
        &[
            "../threema-protocols/src/common.proto",
            "../threema-protocols/src/csp-e2e.proto",
            "../threema-protocols/src/md-d2d.proto",
            "../threema-protocols/src/md-d2d-sync.proto",
            "../threema-protocols/src/md-d2d-rendezvous.proto",
            "../threema-protocols/src/md-d2m.proto",
        ],
        &["../threema-protocols/src/"],
    )?;

    // Done
    Ok(())
}
