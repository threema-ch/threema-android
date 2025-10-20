//! Example for usage of the Chat Server Protocol state machine, doing a real handshake with the
//! chat server and an exemplary payload flow loop.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
#![expect(
    clippy::integer_division_remainder_used,
    reason = "Some internal of tokio::select triggers this"
)]

use core::time::Duration;
use std::io;

use anyhow::bail;
use clap::Parser;
use libthreema::{
    cli::{FullIdentityConfig, FullIdentityConfigOptions},
    csp::{
        CspProtocol, CspProtocolContext, CspStateUpdate,
        frame::OutgoingFrame,
        payload::{EchoPayload, IncomingPayload, OutgoingPayload},
    },
    https::cli::https_client_builder,
    utils::logging::init_stderr_logging,
};
use tokio::{
    io::{AsyncReadExt as _, AsyncWriteExt as _},
    net::TcpStream,
    signal,
    sync::mpsc,
    time::{self, Instant},
};
use tracing::{Level, debug, error, info, trace, warn};

#[derive(Parser)]
#[command()]
struct CspPingPongCommand {
    #[command(flatten)]
    config: FullIdentityConfigOptions,
}

/// Payload queues for the main process
struct PayloadQueuesForCspPingPong {
    incoming: mpsc::Receiver<IncomingPayload>,
    outgoing: mpsc::Sender<OutgoingPayload>,
}

/// Payload queues for the protocol flow runner
struct PayloadQueuesForCsp {
    incoming: mpsc::Sender<IncomingPayload>,
    outgoing: mpsc::Receiver<OutgoingPayload>,
}

/// The Client Server Protocol connection handler
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
            if let Some(CspStateUpdate::PostHandshake { queued_messages }) = instruction.state_update {
                info!(queued_messages, "Handshake complete");
                break;
            }
        }

        Ok(())
    }

    /// Run the payload exchange flow until stopped.
    #[tracing::instrument(skip_all)]
    async fn run_payload_flow(&mut self, mut queues: PayloadQueuesForCsp) -> anyhow::Result<()> {
        let mut read_buffer = [0_u8; 8192];

        for iteration in 1_usize.. {
            trace!("Payload flow iteration #{iteration}");

            // Poll for any pending instruction
            let mut instruction = self.protocol.poll()?;
            if instruction.is_none() {
                // No pending instruction left, wait for more input
                instruction = tokio::select! {
                    // Forward any incoming chunks from the TCP stream
                    _ = self.stream.readable() => {
                        let length = self.try_receive(&mut read_buffer)?;

                        // Add chunks (poll in the next iteration)
                        self.protocol
                            .add_chunks(&[read_buffer.get(..length)
                            .expect("Amount of read bytes should be available")])?;
                        None
                    }

                    // Forward any outgoing payloads
                    Some(outgoing_payload) = queues.outgoing.recv() => {
                        debug!(?outgoing_payload, "Sending payload");
                        Some(self.protocol.create_payload(&outgoing_payload)?)
                    }
                }
            }
            let Some(instruction) = instruction else {
                continue;
            };

            // We do not expect any state updates at this stage
            if let Some(state_update) = instruction.state_update {
                let message = "Unexpected state update after handshake";
                error!(?state_update, message);
                bail!(message)
            }

            // Forward any incoming payload
            if let Some(incoming_payload) = instruction.incoming_payload {
                debug!(?incoming_payload, "Received payload");
                queues.incoming.send(incoming_payload).await?;
            }

            // Send any outgoing frame
            if let Some(frame) = instruction.outgoing_frame {
                self.send(&frame.0).await?;
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

    #[tracing::instrument(skip_all)]
    fn try_receive(&mut self, buffer: &mut [u8]) -> anyhow::Result<usize> {
        match self.stream.try_read(buffer) {
            Ok(0) => {
                // Remote shut down our reading end gracefully.
                //
                // IMPORTANT: An implementation needs to ensure that it stops gracefully by processing any
                // remaining payloads prior to stopping the protocol. This example implementation ensures this
                // by handling all pending instructions prior to polling for more data. The only case we bail
                // is therefore when our instruction queue is already dry.
                bail!("TCP reading end closed")
            },
            Ok(length) => {
                debug!(length, "Received bytes");
                Ok(length)
            },
            Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                trace!("No bytes to receive");
                Ok(0)
            },
            Err(error) => Err(error.into()),
        }
    }
}

#[tracing::instrument(skip_all)]
async fn run_ping_pong_flow(mut queues: PayloadQueuesForCspPingPong) -> anyhow::Result<()> {
    // Create the echo timer that will trigger an outgoing payload every 10s
    let mut echo_timer = time::interval_at(
        Instant::now()
            .checked_add(Duration::from_secs(10))
            .expect("Oops, apocalypse in 10s"),
        Duration::from_secs(10),
    );

    // Enter application loop
    loop {
        tokio::select! {
            // Send echo-request when the timer fires
            _ = echo_timer.tick() => {
                let echo_request = OutgoingPayload::EchoRequest(
                    EchoPayload("ping".as_bytes().to_owned()));
                info!(?echo_request, "Sending echo request");
                queues.outgoing.send(echo_request).await?;
            }

            // Process incoming payload (or stop signal)
            incoming_payload = queues.incoming.recv() => {
                if let Some(incoming_payload) = incoming_payload {
                    info!(?incoming_payload, "Received payload");
                } else {
                    break
                }
            }
        };
    }

    Ok(())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Create HTTP client
    let http_client = https_client_builder().build()?;

    // Parse command
    let arguments = CspPingPongCommand::parse();
    let config = FullIdentityConfig::from_options(&http_client, arguments.config).await?;

    // Create payload queues
    let (csp_ping_pong_queues, csp_queues) = {
        let incoming_payload = mpsc::channel(4);
        let outgoing_payload = mpsc::channel(4);
        (
            PayloadQueuesForCspPingPong {
                incoming: incoming_payload.1,
                outgoing: outgoing_payload.0,
            },
            PayloadQueuesForCsp {
                incoming: incoming_payload.0,
                outgoing: outgoing_payload.1,
            },
        )
    };

    // Create CSP protocol and establish a connection
    let (mut csp_runner, client_hello) = CspProtocolRunner::new(
        config
            .minimal
            .common
            .config
            .chat_server_address
            .addresses(config.csp_server_group),
        config.csp_context().expect("Configuration should be valid"),
    )
    .await?;

    // Run the handshake flow
    csp_runner.run_handshake_flow(client_hello).await?;

    // Run the protocols
    tokio::select! {
        _ = csp_runner.run_payload_flow(csp_queues) => {},
        _ = run_ping_pong_flow(csp_ping_pong_queues) => {},
        _ = signal::ctrl_c() => {},
    };

    // Shut down
    csp_runner.shutdown().await?;
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    CspPingPongCommand::command().debug_assert();
}
