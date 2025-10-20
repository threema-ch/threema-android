//! Example for usage of the Chat Server E2EE Protocol, connecting to the chat server and receiving incoming
//! messages.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]
#![expect(
    clippy::integer_division_remainder_used,
    reason = "Some internal of tokio::select triggers this"
)]
#![expect(
    unreachable_code,
    unused_variables,
    clippy::todo,
    reason = "TODO(LIB-16): Finalise this, then remove me"
)]

use core::cell::RefCell;
use std::io;

use anyhow::bail;
use clap::Parser;
use libthreema::{
    cli::{FullIdentityConfig, FullIdentityConfigOptions},
    common::ClientInfo,
    csp::{
        CspProtocol, CspProtocolContext, CspProtocolInstruction, CspStateUpdate,
        frame::OutgoingFrame,
        payload::{IncomingPayload, MessageAck, MessageWithMetadataBox, OutgoingPayload},
    },
    csp_e2e::{
        CspE2eProtocol, CspE2eProtocolContextInit,
        contacts::{
            create::{CreateContactsInstruction, CreateContactsResponse},
            lookup::ContactsLookupResponse,
            update::{UpdateContactsInstruction, UpdateContactsResponse},
        },
        incoming_message::task::{IncomingMessageInstruction, IncomingMessageLoop, IncomingMessageResponse},
        reflect::{ReflectInstruction, ReflectResponse},
        transaction::{
            begin::{BeginTransactionInstruction, BeginTransactionResponse},
            commit::{CommitTransactionInstruction, CommitTransactionResponse},
        },
    },
    https::cli::https_client_builder,
    model::provider::in_memory::{DefaultShortcutProvider, InMemoryDb, InMemoryDbInit, InMemoryDbSettings},
    utils::logging::init_stderr_logging,
};
use tokio::{
    io::{AsyncReadExt as _, AsyncWriteExt as _},
    net::TcpStream,
    signal,
    sync::mpsc,
};
use tracing::{Level, debug, error, info, trace, warn};

#[derive(Parser)]
#[command()]
struct CspE2eReceiveCommand {
    #[command(flatten)]
    config: FullIdentityConfigOptions,
}

enum PayloadForCspE2e {
    Message(MessageWithMetadataBox),
    MessageAck(MessageAck),
}
impl From<PayloadForCspE2e> for OutgoingPayload {
    fn from(payload: PayloadForCspE2e) -> Self {
        match payload {
            PayloadForCspE2e::Message(message) => OutgoingPayload::MessageWithMetadataBox(message),
            PayloadForCspE2e::MessageAck(message_ack) => OutgoingPayload::MessageAck(message_ack),
        }
    }
}

/// Payload queues for the main process
struct PayloadQueuesForCspE2e {
    incoming: mpsc::Receiver<PayloadForCspE2e>,
    outgoing: mpsc::Sender<PayloadForCspE2e>,
}

/// Payload queues for the protocol flow runner
struct PayloadQueuesForCsp {
    incoming: mpsc::Sender<PayloadForCspE2e>,
    outgoing: mpsc::Receiver<PayloadForCspE2e>,
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
        let mut next_instruction: Option<CspProtocolInstruction> = None;

        for iteration in 1_usize.. {
            trace!("Iteration #{iteration}");

            // Poll for an instruction, if necessary
            if next_instruction.is_none() {
                next_instruction = self.protocol.poll()?;
            }

            // Wait for more input, if necessary
            if next_instruction.is_none() {
                next_instruction = tokio::select! {
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
                    outgoing_payload = queues.outgoing.recv() => {
                        if let Some(outgoing_payload) = outgoing_payload {
                            let outgoing_payload = OutgoingPayload::from(outgoing_payload);
                            debug!(?outgoing_payload, "Sending payload");
                            Some(self.protocol.create_payload(&outgoing_payload)?)
                        } else {
                            break
                        }
                    }
                };
            }

            // Handle instruction
            let Some(current_instruction) = next_instruction.take() else {
                continue;
            };

            // We do not expect any state updates at this stage
            if let Some(state_update) = current_instruction.state_update {
                let message = "Unexpected state update after handshake";
                error!(?state_update, message);
                bail!(message)
            }

            // Handle any incoming payload
            if let Some(incoming_payload) = current_instruction.incoming_payload {
                debug!(?incoming_payload, "Received payload");
                match incoming_payload {
                    IncomingPayload::EchoRequest(echo_payload) => {
                        // Respond to echo request
                        next_instruction = Some(
                            self.protocol
                                .create_payload(&OutgoingPayload::EchoResponse(echo_payload))?,
                        );
                    },
                    IncomingPayload::MessageWithMetadataBox(payload) => {
                        // Forward message
                        queues.incoming.send(PayloadForCspE2e::Message(payload)).await?;
                    },
                    IncomingPayload::MessageAck(payload) => {
                        // Forward message ack
                        queues
                            .incoming
                            .send(PayloadForCspE2e::MessageAck(payload))
                            .await?;
                    },

                    IncomingPayload::EchoResponse(_)
                    | IncomingPayload::QueueSendComplete
                    | IncomingPayload::DeviceCookieChangeIndication
                    | IncomingPayload::CloseError(_)
                    | IncomingPayload::ServerAlert(_)
                    | IncomingPayload::UnknownPayload { .. } => {},
                }
            }

            // Send any outgoing frame
            if let Some(frame) = current_instruction.outgoing_frame {
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
                // Remote shut down our reading end gracefully.
                //
                // IMPORTANT: An implementation needs to ensure that it stops gracefully by processing any
                // remaining payloads prior to stopping the protocol. This example implementation ensures this
                // by handling all pending instructions prior to polling for more data. The only case we bail
                // is therefore when our instruction queue is already dry.
                bail!("TCP reading end closed")
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

struct CspE2eProtocolRunner {
    /// An instance of the [`CspE2eProtocol`] state machine
    protocol: CspE2eProtocol,

    /// HTTP client
    http_client: reqwest::Client,
}
impl CspE2eProtocolRunner {
    #[tracing::instrument(skip_all)]
    fn new(http_client: reqwest::Client, context: CspE2eProtocolContextInit) -> anyhow::Result<Self> {
        Ok(Self {
            protocol: CspE2eProtocol::new(context),
            http_client,
        })
    }

    /// Run the receive flow until stopped.
    #[tracing::instrument(skip_all)]
    async fn run_receive_flow(&mut self, mut queues: PayloadQueuesForCspE2e) -> anyhow::Result<()> {
        for iteration in 1_usize.. {
            trace!("Receive flow iteration #{iteration}");

            // Handle any incoming payloads until we have a task
            let mut task = match queues.incoming.recv().await {
                Some(PayloadForCspE2e::Message(message)) => self.protocol.handle_incoming_message(message),
                Some(PayloadForCspE2e::MessageAck(message_ack)) => {
                    warn!(?message_ack, "Unexpected message-ack");
                    continue;
                },
                None => break,
            };

            // Handle task
            match task.poll(self.protocol.context())? {
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::FetchSender(instruction)) => {
                    // Run both requests simultaneously
                    let work_directory_request_future = async {
                        match instruction.work_directory_request {
                            Some(work_directory_request) => {
                                work_directory_request.send(&self.http_client).await.map(Some)
                            },
                            None => Ok(None),
                        }
                    };
                    let (directory_result, work_directory_result) = tokio::join!(
                        instruction.directory_request.send(&self.http_client),
                        work_directory_request_future,
                    );

                    // Forward response
                    task.response(IncomingMessageResponse::FetchSender(ContactsLookupResponse {
                        directory_result,
                        work_directory_result: work_directory_result.transpose(),
                    }))?;
                },
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::CreateContact(instruction)) => {
                    match instruction {
                        CreateContactsInstruction::BeginTransaction(instruction) => {
                            // Begin transaction and forward response, if any
                            let response = self.begin_transaction(instruction).await?;
                            if let Some(response) = response {
                                task.response(IncomingMessageResponse::CreateContact(
                                    CreateContactsResponse::BeginTransactionResponse(response),
                                ))?;
                            }
                        },
                        CreateContactsInstruction::ReflectAndCommitTransaction(instruction) => {
                            // Reflect and commit transaction and forward response
                            task.response(IncomingMessageResponse::CreateContact(
                                CreateContactsResponse::CommitTransactionResponse(
                                    self.reflect_and_commit_transaction(instruction).await?,
                                ),
                            ))?;
                        },
                    }
                },
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::UpdateContact(instruction)) => {
                    match instruction {
                        UpdateContactsInstruction::BeginTransaction(instruction) => {
                            // Begin transaction and forward response, if any
                            let response = self.begin_transaction(instruction).await?;
                            if let Some(response) = response {
                                task.response(IncomingMessageResponse::UpdateContact(
                                    UpdateContactsResponse::BeginTransactionResponse(response),
                                ))?;
                            }
                        },
                        UpdateContactsInstruction::ReflectAndCommitTransaction(instruction) => {
                            // Reflect and commit transaction and forward response
                            task.response(IncomingMessageResponse::UpdateContact(
                                UpdateContactsResponse::CommitTransactionResponse(
                                    self.reflect_and_commit_transaction(instruction).await?,
                                ),
                            ))?;
                        },
                    }
                },
                IncomingMessageLoop::Instruction(IncomingMessageInstruction::ReflectMessage(instruction)) => {
                    task.response(IncomingMessageResponse::ReflectMessage(
                        self.reflect(instruction).await?,
                    ))?;
                },

                IncomingMessageLoop::Done(result) => {
                    // Send message acknowledgement, if any
                    if let Some(outgoing_message_ack) = result.outgoing_message_ack {
                        queues
                            .outgoing
                            .send(PayloadForCspE2e::MessageAck(outgoing_message_ack))
                            .await?;
                    }

                    // TODO(LIB-16). Enqueue outgoing message task, if any
                },
            }
        }

        Ok(())
    }

    #[tracing::instrument(skip_all)]
    async fn begin_transaction(
        &self,
        instruction: BeginTransactionInstruction,
    ) -> anyhow::Result<Option<BeginTransactionResponse>> {
        match instruction {
            BeginTransactionInstruction::TransactionRejected => {
                // TODO(LIB-16). Await TransactionEnded
                Ok(None)
            },
            BeginTransactionInstruction::BeginTransaction { message } => {
                // TODO(LIB-16). Send `BeginTransaction, await BeginTransactionAck or TransactionRejected,
                // then return BeginTransactionResponse(message)
                Ok(Some(BeginTransactionResponse::BeginTransactionReply(todo!())))
            },
            BeginTransactionInstruction::AbortTransaction { message } => {
                // TODO(LIB-16). Send `CommitTransaction`, await CommitTransactionAck, then return
                // AbortTransaction(CommitTransactionAck)
                Ok(Some(BeginTransactionResponse::AbortTransactionResponse(todo!())))
            },
        }
    }

    #[tracing::instrument(skip_all)]
    async fn reflect_and_commit_transaction(
        &self,
        instruction: CommitTransactionInstruction,
    ) -> anyhow::Result<CommitTransactionResponse> {
        // TODO(LIB-16). Reflect messages, then immediately commit. Await CommitAck and gather any
        // reflect-acks
        Ok(CommitTransactionResponse {
            acknowledged_reflect_ids: todo!(),
            commit_transaction_ack: todo!(),
        })
    }

    #[tracing::instrument(skip_all)]
    async fn reflect(&self, instruction: ReflectInstruction) -> anyhow::Result<ReflectResponse> {
        // TODO(LIB-16). Reflect messages, then wait for corresponding reflect-acks
        Ok(ReflectResponse {
            acknowledged_reflect_ids: todo!(),
        })
    }
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Create HTTP client
    let http_client = https_client_builder().build()?;

    // Parse arguments for command
    let arguments = CspE2eReceiveCommand::parse();
    let config = FullIdentityConfig::from_options(&http_client, arguments.config).await?;

    // Create CSP E2EE protocol context
    let mut database = InMemoryDb::from(InMemoryDbInit {
        user_identity: config.minimal.user_identity,
        settings: InMemoryDbSettings {
            block_unknown_identities: false,
        },
        contacts: vec![],
        blocked_identities: vec![],
    });
    let csp_e2e_context = CspE2eProtocolContextInit {
        client_info: ClientInfo::Libthreema,
        config: config.minimal.common.config.clone(),
        csp_e2e: config.csp_e2e_context_init(Box::new(RefCell::new(database.csp_e2e_nonce_provider()))),
        d2x: config.d2x_context_init(Box::new(RefCell::new(database.d2d_nonce_provider()))),
        shortcut: Box::new(DefaultShortcutProvider),
        settings: Box::new(RefCell::new(database.settings_provider())),
        contacts: Box::new(RefCell::new(database.contact_provider())),
        messages: Box::new(RefCell::new(database.message_provider())),
    };

    // Create payload queues
    let (csp_e2e_queues, csp_queues) = {
        let incoming_payload = mpsc::channel(4);
        let outgoing_payload = mpsc::channel(4);
        (
            PayloadQueuesForCspE2e {
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

    // Create CSP E2E protocol
    let mut csp_e2e_protocol = CspE2eProtocolRunner::new(http_client, csp_e2e_context)?;

    // Run the protocols
    tokio::select! {
        _ = csp_runner.run_payload_flow(csp_queues) => {},
        _ = csp_e2e_protocol.run_receive_flow(csp_e2e_queues) => {},
        _ = signal::ctrl_c() => {},
    };

    // Shut down
    csp_runner.shutdown().await?;
    Ok(())
}

#[test]
fn verify_cli() {
    use clap::CommandFactory;
    CspE2eReceiveCommand::command().debug_assert();
}
