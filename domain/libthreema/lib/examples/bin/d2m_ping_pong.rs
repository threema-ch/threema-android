//! Example for usage of the Device to Mediator Protocol state machine, doing a real handshake with the
//! mediator server and an exemplary payload flow loop.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
#![expect(
    clippy::integer_division_remainder_used,
    reason = "Some internal of tokio::select triggers this"
)]

use core::time::Duration;

use anyhow::{Result, anyhow, bail};
use clap::Parser;
use futures_util::{SinkExt as _, TryStreamExt as _};
use libthreema::{
    cli::{FullIdentityConfig, FullIdentityConfigOptions},
    d2m::{
        D2mContext, D2mProtocol, D2mStateUpdate,
        payload::{BeginTransaction, IncomingPayload, OutgoingPayload, Reflect, ReflectFlags},
    },
    https::cli::https_client_builder,
    utils::logging::init_stderr_logging,
};
use rand::random;
use reqwest::StatusCode;
use tokio::{
    net::TcpStream,
    signal,
    sync::mpsc,
    time::{self, Instant},
};
use tokio_tungstenite::{
    MaybeTlsStream, WebSocketStream, connect_async,
    tungstenite::protocol::{CloseFrame, Message, frame::coding::CloseCode},
};
use tracing::{Level, debug, error, info, trace, warn};

#[derive(Parser)]
#[command()]
struct D2mPingPongCommand {
    #[command(flatten)]
    config: FullIdentityConfigOptions,
}

/// Payload queues for the main process
struct PayloadQueuesForD2mPingPong {
    incoming: mpsc::Receiver<IncomingPayload>,
    outgoing: mpsc::Sender<OutgoingPayload>,
}

/// Payload queues for the protocol flow runner
struct PayloadQueuesForProtocol {
    incoming: mpsc::Sender<IncomingPayload>,
    outgoing: mpsc::Receiver<OutgoingPayload>,
}

struct D2mProtocolRunner {
    /// The WebSocket stream
    stream: WebSocketStream<MaybeTlsStream<TcpStream>>,

    /// An instance of the [`D2mProtocol`] state machine
    protocol: D2mProtocol,
}
impl D2mProtocolRunner {
    /// Initiate a D2M protocol connection
    #[tracing::instrument(skip_all)]
    async fn new(context: D2mContext) -> Result<Self> {
        // Create the protocol
        let (d2m_protocol, url) = D2mProtocol::new(context);

        // Connect via WebSocket
        debug!(?url, "Establishing WebSocket connection to mediator server");
        let (stream, response) = connect_async(url).await?;
        if response.status() != StatusCode::SWITCHING_PROTOCOLS {
            bail!(
                "Expected response to switch protocols ({expected}), got {actual}",
                expected = StatusCode::SWITCHING_PROTOCOLS,
                actual = response.status(),
            );
        }
        Ok(Self {
            stream,
            protocol: d2m_protocol,
        })
    }

    /// Do the handshake with the mediator server by exchanging the following messages:
    ///
    /// ```txt
    /// C -- client-info --> S (was already sent as part of the URL's path)
    /// C <- server-hello -- S
    /// C -- client-hello -> S
    /// C <- server-info --- S
    /// ```
    async fn run_handshake_flow(&mut self) -> Result<()> {
        for iteration in 1_usize.. {
            trace!("Iteration #{iteration}");

            // Receive datagram and add it
            let datagram = self.receive().await?;
            self.protocol.add_datagrams(vec![datagram])?;

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

            // Send any outgoing datagram
            if let Some(datagram) = instruction.outgoing_datagram {
                self.send(datagram.0).await?;
            }

            // Check if we've completed the handshake
            if let Some(D2mStateUpdate::PostHandshake(server_info)) = instruction.state_update {
                info!(?server_info, "Handshake completed");
                break;
            }
        }

        Ok(())
    }

    /// Run the payload exchange flow until stopped.
    #[tracing::instrument(skip_all)]
    async fn run_payload_flow(&mut self, mut queues: PayloadQueuesForProtocol) -> Result<()> {
        for iteration in 1_usize.. {
            trace!("Payload flow iteration #{iteration}");

            // Poll for any pending instruction
            let mut instruction = self.protocol.poll()?;
            if instruction.is_none() {
                // No pending instruction left, wait for more input
                instruction = tokio::select! {
                    // Forward any incoming datagrams from the WebSocket transport
                    datagram = self.receive() => {
                        // Add datagram (poll in the next iteration)
                        self.protocol.add_datagrams(vec![datagram?])?;
                        None
                    },

                    // Forward any outgoing payloads
                    Some(outgoing_payload) = queues.outgoing.recv() => {
                        debug!(?outgoing_payload, "Sending payload");
                        let instruction = self.protocol.create_payload(outgoing_payload)?;
                        Some(instruction)
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

            // Send any outgoing datagram
            if let Some(datagram) = instruction.outgoing_datagram {
                self.send(datagram.0).await?;
            }
        }

        Ok(())
    }

    #[tracing::instrument(skip_all)]
    async fn shutdown(mut self) -> Result<()> {
        info!("Shutting down WebSocket connection");

        // Normal closure, e.g. when the user is explicitly disconnecting
        Ok(self
            .stream
            .close(Some(CloseFrame {
                code: CloseCode::Normal,
                reason: "Bye".into(),
            }))
            .await?)
    }

    #[tracing::instrument(skip_all)]
    async fn send(&mut self, datagram: Vec<u8>) -> Result<()> {
        trace!(length = datagram.len(), "Sending datagram");
        self.stream.send(Message::Binary(datagram.into())).await?;
        Ok(())
    }

    #[tracing::instrument(skip_all)]
    async fn receive(&mut self) -> Result<Vec<u8>> {
        let datagram = loop {
            let message = self
                .stream
                .try_next()
                .await?
                .ok_or(anyhow!("WebSocket reading end closed"))?;
            match message {
                Message::Binary(bytes) => break bytes.to_vec(),

                Message::Text(text) => {
                    bail!("Received unexpected text message: {}", text.as_str())
                },

                Message::Ping(bytes) => {
                    // WARNING: There's a slight chance that the pong is lost when this is cancelled!
                    debug!(ping_length = bytes.len(), "Received ping, responding with a pong");
                    self.stream.feed(Message::Pong(bytes)).await?;
                    debug!("Pong sent");
                },

                Message::Pong(bytes) => {
                    debug!(pong_length = bytes.len(), "Received pong");
                },

                Message::Close(close_frame) => {
                    info!(?close_frame, "Received close");
                },

                Message::Frame(_) => {
                    bail!("Received unexpected raw frame");
                },
            }
        };
        debug!(datagram_length = datagram.len(), "Received datagram");
        Ok(datagram)
    }
}

#[derive(Debug, PartialEq)]
enum TransactionState {
    None,
    Blocked,
    AwaitingBeginAck,
    Running,
    AwaitingCommitAck,
}

struct D2mPingPongFlowRunner {
    queues: PayloadQueuesForD2mPingPong,
    transaction_state: TransactionState,
    reflect_id_counter: u32,
}
impl D2mPingPongFlowRunner {
    fn new(queues: PayloadQueuesForD2mPingPong) -> Self {
        Self {
            queues,
            transaction_state: TransactionState::None,
            reflect_id_counter: 0,
        }
    }

    async fn run(mut self) -> Result<()> {
        // Create a timer that will periodically trigger an outgoing payload
        let mut payload_timer = time::interval_at(
            Instant::now()
                .checked_add(Duration::from_secs(10))
                .expect("Oops, apocalypse is near"),
            Duration::from_secs(10),
        );

        // Enter ping-pong flow loop
        loop {
            let outgoing_payload = tokio::select! {
                // Create an outgoing payload when the timer fires
                _ = payload_timer.tick() => {
                    self.create_outgoing_payload()
                },

                // Process incoming payload
                incoming_payload = self.queues.incoming.recv() => {
                    if let Some(incoming_payload) = incoming_payload {
                        info!(?incoming_payload, "Received payload");
                        self.handle_incoming_payload(&incoming_payload)
                    } else {
                        break
                    }
                }
            };

            // Send any outgoing payload
            if let Some(outgoing_payload) = outgoing_payload {
                info!(?outgoing_payload, "Sending payload");
                self.queues.outgoing.send(outgoing_payload).await?;
            }
        }

        Ok(())
    }

    #[tracing::instrument(skip_all)]
    fn handle_incoming_payload(&mut self, incoming_payload: &IncomingPayload) -> Option<OutgoingPayload> {
        match incoming_payload {
            // Transaction acknowledged: Now running
            IncomingPayload::BeginTransactionAck => {
                self.transaction_state = TransactionState::Running;
                None
            },

            // Transaction committed: Now none ongoing
            IncomingPayload::CommitTransactionAck => {
                self.transaction_state = TransactionState::None;
                None
            },

            // Transaction rejected: Retry beginning once we're unblocked
            IncomingPayload::TransactionRejected(_) => {
                self.transaction_state = TransactionState::Blocked;
                None
            },

            // Another transaction ended: Retry if we were blocked
            IncomingPayload::TransactionEnded(_) => {
                if self.transaction_state == TransactionState::Blocked {
                    self.transaction_state = TransactionState::None;
                    Some(OutgoingPayload::BeginTransaction(BeginTransaction {
                        encrypted_scope: b"encrypted_scope".to_vec(),
                        ttl: None,
                    }))
                } else {
                    None
                }
            },

            _ => None,
        }
    }

    #[tracing::instrument(skip_all)]
    fn create_outgoing_payload(&mut self) -> Option<OutgoingPayload> {
        trace!(state = ?self.transaction_state);
        match self.transaction_state {
            // No transaction: Occasionally begin a transaction
            TransactionState::None if random::<bool>() => {
                self.transaction_state = TransactionState::AwaitingBeginAck;
                Some(OutgoingPayload::BeginTransaction(BeginTransaction {
                    encrypted_scope: b"encrypted_scope".to_vec(),
                    ttl: None,
                }))
            },

            // Transaction running: Commit
            TransactionState::Running => {
                self.transaction_state = TransactionState::AwaitingCommitAck;
                Some(OutgoingPayload::CommitTransaction)
            },

            // No trannsaction running: Reflect
            TransactionState::None
            | TransactionState::Blocked
            | TransactionState::AwaitingBeginAck
            | TransactionState::AwaitingCommitAck => {
                self.reflect_id_counter = self.reflect_id_counter.checked_add(1)?;
                Some(OutgoingPayload::Reflect(Reflect {
                    flags: ReflectFlags(ReflectFlags::EPHEMERAL_MARKER),
                    reflect_id: self.reflect_id_counter,
                    envelope: b"envelope".to_vec(),
                }))
            },
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Create HTTP client
    let http_client = https_client_builder().build()?;

    // Parse command
    let arguments = D2mPingPongCommand::parse();
    let config = FullIdentityConfig::from_options(&http_client, arguments.config).await?;

    // Create payload queues
    let (app_queues, protocol_queues) = {
        let incoming_payload = mpsc::channel(4);
        let outgoing_payload = mpsc::channel(4);
        (
            PayloadQueuesForD2mPingPong {
                incoming: incoming_payload.1,
                outgoing: outgoing_payload.0,
            },
            PayloadQueuesForProtocol {
                incoming: incoming_payload.0,
                outgoing: outgoing_payload.1,
            },
        )
    };

    // Create D2M protocol and establish a connection
    let mut d2m_connection = D2mProtocolRunner::new(
        config
            .d2m_context()
            .expect("Configuration must include D2X configuration"),
    )
    .await?;

    // Create protocol flow runner
    let ping_pong_flow_runner = D2mPingPongFlowRunner::new(app_queues);

    // Run the handshake flow
    d2m_connection.run_handshake_flow().await?;

    // Run the protocols
    tokio::select! {
        _ = d2m_connection.run_payload_flow(protocol_queues) => {}
        _ = ping_pong_flow_runner.run() => {}
        _ = signal::ctrl_c() => {},
    };

    // Shut down
    d2m_connection.shutdown().await?;
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    D2mPingPongCommand::command().debug_assert();
}
