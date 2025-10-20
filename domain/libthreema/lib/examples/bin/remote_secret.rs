//! Example for creating a new Threema ID.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
#![expect(clippy::print_stdout, reason = "Examples are allowed to print")]

use clap::{Parser, Subcommand};
use data_encoding::HEXLOWER;
use libthreema::{
    cli::{MinimalIdentityConfig, MinimalIdentityConfigOptions},
    common::keys::{RemoteSecretAuthenticationToken, RemoteSecretHash},
    https::cli::https_client_builder,
    remote_secret::{
        monitor::{
            RemoteSecretMonitorContext, RemoteSecretMonitorInstruction, RemoteSecretMonitorProtocol,
            RemoteSecretMonitorResponse,
        },
        setup::{
            RemoteSecretSetupContext, RemoteSecretSetupResponse,
            create::{RemoteSecretCreateLoop, RemoteSecretCreateResult, RemoteSecretCreateTask},
            delete::{RemoteSecretDeleteLoop, RemoteSecretDeleteTask},
        },
    },
    utils::logging::init_stderr_logging,
};
use tokio::time;
use tracing::{Level, debug, info};

#[derive(Parser)]
#[command()]
struct RemoteSecretCommand {
    #[command(flatten)]
    config: MinimalIdentityConfigOptions,

    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    Create,

    Delete {
        #[arg(long, value_parser = RemoteSecretAuthenticationToken::from_hex)]
        remote_secret_authentication_token: RemoteSecretAuthenticationToken,
    },

    Monitor {
        #[arg(long, value_parser = RemoteSecretAuthenticationToken::from_hex)]
        remote_secret_authentication_token: RemoteSecretAuthenticationToken,

        #[arg(long, value_parser = RemoteSecretHash::from_hex)]
        remote_secret_hash: RemoteSecretHash,
    },
}

async fn run_create_remote_secret(
    http_client: reqwest::Client,
    context: RemoteSecretSetupContext,
) -> anyhow::Result<RemoteSecretCreateResult> {
    let mut task = RemoteSecretCreateTask::new(context);
    loop {
        match task.poll()? {
            RemoteSecretCreateLoop::Instruction(instruction) => {
                let result = instruction.request.send(&http_client).await;
                task.response(RemoteSecretSetupResponse { result })?;
            },

            RemoteSecretCreateLoop::Done(result) => return Ok(result),
        }
    }
}

async fn run_delete_remote_secret(
    http_client: reqwest::Client,
    context: RemoteSecretSetupContext,
    remote_secret_authentication_token: RemoteSecretAuthenticationToken,
) -> anyhow::Result<()> {
    let mut task = RemoteSecretDeleteTask::new(context, remote_secret_authentication_token);
    loop {
        match task.poll()? {
            RemoteSecretDeleteLoop::Instruction(instruction) => {
                let result = instruction.request.send(&http_client).await;
                task.response(RemoteSecretSetupResponse { result })?;
            },

            RemoteSecretDeleteLoop::Done(()) => return Ok(()),
        }
    }
}

async fn run_monitor_remote_secret(
    http_client: reqwest::Client,
    context: RemoteSecretMonitorContext,
) -> anyhow::Result<()> {
    let mut protocol = RemoteSecretMonitorProtocol::new(context);
    loop {
        match protocol.poll()? {
            RemoteSecretMonitorInstruction::Request(https_request) => {
                let result = https_request.send(&http_client).await;
                protocol.response(RemoteSecretMonitorResponse { result })?;
            },

            RemoteSecretMonitorInstruction::Schedule {
                timeout,
                remote_secret,
            } => {
                if let Some(remote_secret) = remote_secret {
                    info!(
                        remote_secret = HEXLOWER.encode(&remote_secret.0),
                        remote_secret_hash = ?remote_secret.derive_hash(),
                        "Retrieved remote secret",
                    );
                }
                debug!(?timeout, "Scheduling another poll");
                time::sleep(timeout).await;
            },
        }
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Create HTTP client
    let http_client = https_client_builder().build()?;

    // Parse arguments for command
    let arguments = RemoteSecretCommand::parse();
    let config = MinimalIdentityConfig::from_options(&http_client, arguments.config).await?;
    match arguments.command {
        Commands::Create => {
            let context = config.remote_secret_setup_context()?;
            let result = run_create_remote_secret(http_client, context).await?;
            println!(
                "--remote-secret-authentication-token {}",
                HEXLOWER.encode(&result.remote_secret_authentication_token.0)
            );
            println!(
                "--remote-secret-hash {}",
                HEXLOWER.encode(&result.remote_secret.derive_hash().0)
            );
        },

        Commands::Delete {
            remote_secret_authentication_token,
        } => {
            let context = config.remote_secret_setup_context()?;
            run_delete_remote_secret(http_client, context, remote_secret_authentication_token).await?;
        },

        Commands::Monitor {
            remote_secret_authentication_token,
            remote_secret_hash,
        } => {
            let context =
                config.remote_secret_monitor_context(remote_secret_authentication_token, remote_secret_hash);
            run_monitor_remote_secret(http_client, context).await?;
        },
    }
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    RemoteSecretCommand::command().debug_assert();
}
