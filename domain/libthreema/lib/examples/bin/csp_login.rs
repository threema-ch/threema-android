//! Example for usage of the Chat Server Protocol state machine, doing a real handshake with the
//! chat server, exiting immediately after successful login.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]

use std::io;

use anyhow::bail;
use clap::Parser;
use libthreema::{
    cli::{FullIdentityConfig, FullIdentityConfigOptions},
    csp::{CspProtocol, CspProtocolContext, CspStateUpdate, payload::OutgoingFrame},
    https::cli::https_client_builder,
    utils::logging::init_stderr_logging,
};
use tokio::{
    io::{AsyncReadExt as _, AsyncWriteExt as _},
    net::TcpStream,
};
use tracing::{Level, debug, error, info, trace, warn};

#[derive(Parser)]
#[command()]
struct CspPingPongCommand {
    #[command(flatten)]
    config: FullIdentityConfigOptions,
}

struct CspProtocolRunner {
    /// The TCP stream
    stream: TcpStream,

    /// An instance of the [`CspProtocol`] state machine
    protocol: CspProtocol,
}
impl CspProtocolRunner {
    /// Initiate a CSP protocol connection and hand out the initial `client_hello` message
    #[tracing::instrument(skip_all)]
    async fn new(
        server_address: Vec<(String, u16)>,
        context: CspProtocolContext,
    ) -> anyhow::Result<(Self, OutgoingFrame)> {
        // Connect via TCP
        debug!(?server_address, "Establishing TCP connection to chat server",);
        let tcp_stream = TcpStream::connect(
            server_address
                .first()
                .expect("CSP config should have at least one address"),
        )
        .await?;

        // Create the protocol
        let (csp_protocol, client_hello) = CspProtocol::new(context);
        Ok((
            Self {
                stream: tcp_stream,
                protocol: csp_protocol,
            },
            client_hello,
        ))
    }

    /// Do the handshake with the chat server by exchanging the following messages:
    ///
    /// ```txt
    /// C -- client-hello -> S
    /// C <- server-hello -- S
    /// C ---- login ---- -> S
    /// C <-- login-ack ---- S
    /// ```
    #[tracing::instrument(skip_all)]
    async fn run_handshake_flow(&mut self, client_hello: OutgoingFrame) -> anyhow::Result<()> {
        // Send the client hello
        debug!(length = client_hello.0.len(), "Sending client hello");
        self.send(&client_hello.0).await?;

        // Handshake by polling the CSP state
        for iteration in 1_usize.. {
            trace!("Iteration #{iteration}");

            // Receive required bytes and add them
            let bytes = self.receive_required().await?;
            self.protocol.add_chunks(&[&bytes])?;

            // Handle instruction
            let Some(instruction) = self.protocol.poll()? else {
                continue;
            };

            // We do not expect an incoming payload at this stage
            if let Some(incoming_payload) = instruction.incoming_payload {
                let message = "Unexpected incoming payload during handshake";
                error!(?incoming_payload, message);
                bail!(message)
            }

            // Send any outgoing frame
            if let Some(frame) = instruction.outgoing_frame {
                self.send(&frame.0).await?;
            }

            // Check if we've completed the handshake
            if let Some(CspStateUpdate::PostHandshake(login_ack_data)) = instruction.state_update {
                info!(?login_ack_data, "Handshake complete");
                break;
            }
        }

        Ok(())
    }

    /// Shut down the TCP connection
    #[tracing::instrument(skip_all)]
    async fn shutdown(&mut self) -> anyhow::Result<()> {
        info!("Shutting down TCP connection");
        Ok(self.stream.shutdown().await?)
    }

    /// Send bytes to the server over the TCP connection
    #[tracing::instrument(skip_all, fields(bytes_length = bytes.len()))]
    async fn send(&mut self, bytes: &[u8]) -> anyhow::Result<()> {
        trace!(length = bytes.len(), "Sending bytes");
        self.stream.write_all(bytes).await?;

        Ok(())
    }

    #[tracing::instrument(skip_all)]
    async fn receive_required(&mut self) -> anyhow::Result<Vec<u8>> {
        // Get the minimum amount of bytes we'll need to receive
        let length = self.protocol.next_required_length()?;
        let mut buffer = vec![0; length];
        trace!(?length, "Reading bytes");

        // If there is nothing to read, return immediately
        if length == 0 {
            return Ok(buffer);
        }

        // Read the exact number of bytes required
        let _ = self.stream.read_exact(&mut buffer).await?;

        // Read more if available
        match self.stream.try_read_buf(&mut buffer) {
            Ok(0) => {
                // Remote shut down our reading end. But we still need to process the previously
                // read bytes.
                warn!("TCP reading end closed");
            },
            Ok(length) => {
                trace!(length, "Got additional bytes");
            },
            Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                trace!("No additional bytes available");
            },
            Err(error) => {
                return Err(error.into());
            },
        }
        debug!(length = buffer.len(), "Received bytes");

        Ok(buffer)
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Create HTTP client
    let http_client = https_client_builder().build()?;

    // Parse arguments for command
    let arguments = CspPingPongCommand::parse();
    let config = FullIdentityConfig::from_options(&http_client, arguments.config).await?;

    // Create CSP protocol and establish a connection
    let (mut csp_runner, client_hello) = CspProtocolRunner::new(
        config
            .minimal
            .common
            .config
            .chat_server_address
            .addresses(config.csp_server_group),
        config
            .csp_context_init()
            .try_into()
            .expect("Configuration should be valid"),
    )
    .await?;

    // Run the handshake flow
    csp_runner.run_handshake_flow(client_hello).await?;

    // Shut down
    csp_runner.shutdown().await?;
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    CspPingPongCommand::command().debug_assert();
}
