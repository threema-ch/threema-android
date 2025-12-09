//! Builds libthreema (d'oh!).
use std::{env, io::Result};

#[cfg(feature = "uniffi")]
use uniffi as _;

fn main() -> Result<()> {
    // Configure and compile protobuf
    println!("cargo:rerun-if-changed=../threema-protocols/src/");
    let mut builder = prost_build::Config::new();
    let builder = builder
        .message_attribute(".", "#[libthreema_macros::protobuf_annotations]")
        .enable_type_names();
    let builder = if env::var("CARGO_FEATURE_CLI").is_ok() {
        // For the CLI, we want to be able to parse some enums via clap.
        builder.enum_attribute(".d2m.DeviceSlotState", "#[derive(clap::ValueEnum)]")
    } else {
        builder
    };
    builder.compile_protos(
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
