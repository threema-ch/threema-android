//! Example for usage of the Chat Server Protocol state machine, doing a real handshake with the
//! chat server and an exemplary payload flow loop.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
#![expect(
    clippy::integer_division_remainder_used,
    reason = "Some internal of tokio::select triggers this"
)]

use core::{net::SocketAddr, time::Duration};
use std::io;

use anyhow::{Result, bail};
use clap::Parser;
use libthreema::{
    common::{PublicKey, RawClientKey, ThreemaId},
    csp::{
        Context, CspProtocol, CspStateUpdate,
        frame::OutgoingFrame,
        payload::{EchoPayload, IncomingPayload, OutgoingPayload},
    },
    utils::logging::init_stderr_logging,
};
use tokio::{
    io::{AsyncReadExt as _, AsyncWriteExt as _},
    net::TcpStream,
    signal,
    sync::mpsc,
    task,
    time::{self, Instant},
};
use tracing::{Level, debug, error, info, trace, warn};

/// Fulfill a handshake with the chat server
#[derive(Parser)]
#[command()]
struct Main {
    /// Address of the server, e.g., 1.2.3.4:80
    #[arg(long)]
    server_address: SocketAddr,

    /// The server's public key
    #[arg(
        long,
        required = true,
        num_args = 1..,
        value_delimiter = ',',
        value_parser = PublicKey::from_hex_cli
    )]
    permanent_server_key: Vec<PublicKey>,

    /// Threema ID
    #[arg(short, long, value_parser = ThreemaId::from_str_cli)]
    threema_id: ThreemaId,

    /// Client key (32 bytes base64 encoded)
    #[arg(short, long, value_parser = RawClientKey::from_hex_cli)]
    client_key: RawClientKey,
}

impl Main {
    /// Parse arguments to context and server address
    fn parse_context_server_address() -> (Context, SocketAddr) {
        let main = Main::parse();
        let context = Context::new(
            main.permanent_server_key,
            main.threema_id,
            main.client_key.into(),
            "libthreema;example;de/ch;testing".to_owned(),
            None,
            None,
        )
        .expect("permanent_server_key should not be empty");
        debug!(?context, "Starting protocol");
        (context, main.server_address)
    }
}

/// Payload queues for the main process
struct PayloadQueuesForMain {
    incoming: mpsc::Receiver<IncomingPayload>,
    outgoing: mpsc::Sender<OutgoingPayload>,
}

/// Payload queues for the protocol flow runner
struct PayloadQueuesForProtocol {
    incoming: mpsc::Sender<IncomingPayload>,
    outgoing: mpsc::Receiver<OutgoingPayload>,
}

/// The Client Server Protocol connection handler
struct CspConnection {
    /// The TCP stream
    tcp_stream: TcpStream,

    /// An instance of the [`CspProtocol`] state machine
    protocol: CspProtocol,
}

impl CspConnection {
    /// Initiate a CSP protocol connection and hand out the initial `client_hello` message
    pub(crate) async fn new(server_address: SocketAddr, context: Context) -> Result<(Self, OutgoingFrame)> {
        // Connect via TCP
        debug!(?server_address, "Establishing TCP connection to chat server",);
        let tcp_stream = TcpStream::connect(server_address).await?;

        // Create the protocol
        let (csp_protocol, client_hello) = CspProtocol::new(context);
        Ok((
            Self {
                tcp_stream,
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
    pub(crate) async fn run_handshake_flow(&mut self, client_hello: OutgoingFrame) -> Result<()> {
        // Send the client hello
        debug!(length = client_hello.0.len(), "Sending client hello");
        self.send(&client_hello.0).await?;

        // Handshake by polling the CSP state
        for iteration in 1_usize.. {
            trace!("Handshake flow iteration #{iteration}");

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
    pub(crate) async fn run_payload_flow(&mut self, mut queues: PayloadQueuesForProtocol) -> Result<()> {
        let mut read_buffer = [0_u8; 8192];

        for iteration in 1_usize.. {
            trace!("Payload flow iteration #{iteration}");

            // Poll for any pending instruction
            let mut instruction = self.protocol.poll()?;
            if instruction.is_none() {
                // No pending instruction left, wait for more input
                instruction = tokio::select! {
                    // Forward any incoming chunks from the TCP stream
                    _ = self.tcp_stream.readable() => {
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

            // Log any incoming payload
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
    pub(crate) async fn shutdown(&mut self) -> Result<()> {
        info!("Shutting down TCP connection");
        Ok(self.tcp_stream.shutdown().await?)
    }

    /// Send bytes to the server over the TCP connection
    async fn send(&mut self, bytes: &[u8]) -> Result<()> {
        trace!(length = bytes.len(), "Sending bytes");
        self.tcp_stream.write_all(bytes).await?;

        Ok(())
    }

    async fn receive_required(&mut self) -> Result<Vec<u8>> {
        // Get the minimum amount of bytes we'll need to receive
        let length = self.protocol.next_required_length()?;
        let mut buffer = vec![0; length];
        trace!(?length, "Reading bytes");

        // If there is nothing to read, return immediately
        if length == 0 {
            return Ok(buffer);
        }

        // Read the exact number of bytes required
        let _ = self.tcp_stream.read_exact(&mut buffer).await?;

        // Read more if available
        match self.tcp_stream.try_read_buf(&mut buffer) {
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

    fn try_receive(&mut self, buffer: &mut [u8]) -> Result<usize> {
        match self.tcp_stream.try_read(buffer) {
            Ok(0) => {
                // Remote shut down our reading end. But we still need to process the previously
                // read bytes.
                warn!("TCP reading end closed");
                Ok(0)
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

async fn run_app_flow(mut queues: PayloadQueuesForMain) -> Result<()> {
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
                if queues.outgoing.send(echo_request).await.is_err() {
                    info!("Stopping app");
                    return Ok(())
                }
            }

            // Process incoming payload (or stop signal)
            incoming_payload = queues.incoming.recv() => {
                if let Some(incoming_payload) = incoming_payload {
                    info!(?incoming_payload, "Received payload");
                } else {
                    info!("Stopping app");
                    return Ok(())
                }
            }
        };
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Parse arguments for command
    let (context, server_address) = Main::parse_context_server_address();

    // Create payload queues
    let (app_queues, protocol_queues) = {
        let incoming_payload = mpsc::channel(4);
        let outgoing_payload = mpsc::channel(4);
        (
            PayloadQueuesForMain {
                incoming: incoming_payload.1,
                outgoing: outgoing_payload.0,
            },
            PayloadQueuesForProtocol {
                incoming: incoming_payload.0,
                outgoing: outgoing_payload.1,
            },
        )
    };

    // Create protocol connection
    let (mut csp_connection, client_hello) = CspConnection::new(server_address, context).await?;

    // Run the handshake flow
    csp_connection.run_handshake_flow(client_hello).await?;

    // Spawn a task that simulates a payload sender/receiver flow typical for an application
    let app_handle = task::spawn(run_app_flow(app_queues));

    // Run the payload flow
    tokio::select! {
        _ = csp_connection.run_payload_flow(protocol_queues) => {}
        _ = signal::ctrl_c() => {}
    };

    // Shut down
    app_handle.await??;
    csp_connection.shutdown().await?;
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    Main::command().debug_assert();
}
