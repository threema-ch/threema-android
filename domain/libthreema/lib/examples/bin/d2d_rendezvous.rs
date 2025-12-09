//! Example for usage of the Connection Rendezvous Protocol state machine, using MPSC channels to
//! simulate paths.
#![expect(unused_crate_dependencies, reason = "Example triggered false positive")]

use core::time::Duration;
use std::{
    sync::mpsc::{self, RecvTimeoutError},
    thread,
    time::Instant,
};

use anyhow::Context as _;
use data_encoding::HEXLOWER;
use libthreema::{
    d2d_rendezvous::{
        AuthenticationKey, OutgoingFrame, PathProcessResult, PathStateUpdate, RendezvousProtocol,
    },
    utils::logging::init_stderr_logging,
};
use tracing::{Level, info, trace, trace_span, warn};

struct Keys;
impl Keys {
    const AK: [u8; 32] = [
        0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1,
        0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1,
    ];
}

fn process_incoming_frame(
    protocol: &mut RendezvousProtocol,
    pid: u32,
    incoming_frame: &OutgoingFrame,
) -> anyhow::Result<Option<PathProcessResult>> {
    let (header, payload) = incoming_frame.encode();
    if let Some(nominated_pid) = protocol.nominated_path()
        && pid != nominated_pid
    {
        warn!(
            pid,
            ?incoming_frame,
            "Discarding chunk for unknown or dropped path"
        );
        return Ok(None);
    }

    // Process incoming frame
    protocol
        .add_chunks(pid, &[header.as_slice(), payload])
        .context("Failed to add chunk")?;
    let result = protocol.process_frame(pid).context("Failed to process frame")?;
    Ok(result)
}

#[expect(clippy::needless_pass_by_value, reason = "Prevent re-use")]
fn run_protocol(
    mut protocol: RendezvousProtocol,
    initial_outgoing_frames: Vec<(u32, OutgoingFrame)>,
    tx: mpsc::Sender<(u32, OutgoingFrame)>,
    rx: mpsc::Receiver<(u32, OutgoingFrame)>,
) -> anyhow::Result<()> {
    // Send initial frames
    for outgoing_frame in initial_outgoing_frames {
        tx.send(outgoing_frame)?;
    }

    // Nomination loop where we run the handshakes simultaneously over all available paths until we
    // have nominated one path.
    info!("Entering nomination loop");
    let (nominated_pid, rph) = 'nomination: loop {
        // Receive and process incoming frame
        let (pid, incoming_frame) = rx.recv().context("Failed to receive incoming frame")?;
        let mut maybe_result = process_incoming_frame(&mut protocol, pid, &incoming_frame)?;

        // Handle results
        while let Some(result) = maybe_result {
            // We're not expecting to receive any incoming ULP data.
            assert!(result.incoming_ulp_data.is_none(), "Unexpected incoming ULP data");

            // Send any outgoing frame
            if let Some(outgoing_frame) = result.outgoing_frame {
                tx.send((pid, outgoing_frame))
                    .context("Failed to send outgoing frame")?;
            }

            // Handle any state update
            maybe_result = match result.state_update {
                Some(PathStateUpdate::AwaitingNominate { measured_rtt }) => {
                    // Check if we should nominate the path
                    //
                    // Note: A real implementation should wait a bit and then choose the _best_ path
                    // based on the measured RTT.
                    trace!(?measured_rtt, "Path ready to nominate");
                    if protocol.is_nominator() {
                        Some(protocol.nominate_path(pid).context("Failed to nominate")?)
                    } else {
                        None
                    }
                },
                Some(PathStateUpdate::Nominated { rph }) => {
                    // The path was nominated
                    break 'nomination (pid, rph);
                },
                None => None,
            }
        }
    };

    // ULP loop where we can use the nominated path to exchange arbitrary data. For this example, we
    // will send a string every 3s and print out whatever remote sent us.
    info!(rph = HEXLOWER.encode(&rph.0), "Path nominated, entering ULP loop");
    let (initial_timeout, outgoing_ulp_data) = if protocol.is_nominator() {
        (1000, "Tick")
    } else {
        (2000, "Tock")
    };
    let mut timeout = Duration::from_millis(initial_timeout);
    loop {
        let started_at = Instant::now();
        match rx.recv_timeout(timeout) {
            Ok((pid, incoming_frame)) => {
                // Calculate remaining time for the next iteration
                timeout = timeout.saturating_sub(Instant::elapsed(&started_at));

                // Receive and process incoming frame
                let maybe_result = process_incoming_frame(&mut protocol, pid, &incoming_frame)?;

                // Handle result
                if let Some(result) = maybe_result {
                    // We're not expecting any state updates.
                    assert!(result.state_update.is_none(), "Unexpected state update");

                    // We're not expecting to send any outgoing frames since the handshake state
                    // machine has completed.
                    assert!(
                        result.outgoing_frame.is_none(),
                        "Unexpected outgoing frame in nominated state"
                    );

                    // We do expect incoming ULP data.
                    let incoming_ulp_data =
                        String::from_utf8(result.incoming_ulp_data.expect("Expecting incoming ULP data"))
                            .context("Failed to decode ULP data string")?;
                    info!(data = incoming_ulp_data, ?incoming_frame, "Received ULP data");
                }
            },

            Err(RecvTimeoutError::Timeout) => {
                // Create outgoing frame
                let result = protocol
                    .create_ulp_frame(outgoing_ulp_data.as_bytes().to_vec())
                    .context("Failed to create ULP frame")?;
                info!(
                    data = outgoing_ulp_data,
                    outgoing_frame = ?result.outgoing_frame,
                    "Sending ULP data"
                );

                // We're not expecting any state updates.
                assert!(result.state_update.is_none(), "Unexpected state update");

                // Send any outgoing frame
                if let Some(outgoing_frame) = result.outgoing_frame {
                    tx.send((nominated_pid, outgoing_frame))
                        .context("Failed to send outgoing frame")?;
                }

                // We're not expecting to receive any incoming ULP data.
                assert!(result.incoming_ulp_data.is_none(), "Unexpected incoming ULP data");

                // Reset timeout
                timeout = Duration::from_secs(2);
            },

            Err(RecvTimeoutError::Disconnected) => {
                return Err(RecvTimeoutError::Disconnected).context("Failed to receive incoming frame")?;
            },
        }
    }
}

fn main() {
    // Configure logging
    init_stderr_logging(Level::TRACE);

    // Communication channels for RID and RRD
    let (to_rrd, from_rid) = mpsc::channel::<(u32, OutgoingFrame)>();
    let (to_rid, from_rrd) = mpsc::channel::<(u32, OutgoingFrame)>();

    // Start RID
    let rid_thread = thread::spawn(move || {
        trace_span!("initiator").in_scope(|| {
            // Create and run protocol for RID
            let protocol = RendezvousProtocol::new_as_rid(true, AuthenticationKey(Keys::AK), &[0x1, 0x2]);
            let result = run_protocol(protocol, vec![], to_rrd, from_rrd);
            info!("Initiator stopped: {result:?}");
        });
    });

    // Start RRD
    let rrd_thread = thread::spawn(move || {
        trace_span!("responder").in_scope(|| {
            // Create and run protocol for RRD
            let (protocol, initial_outgoing_frames) =
                RendezvousProtocol::new_as_rrd(false, AuthenticationKey(Keys::AK), &[0x1, 0x2]);
            let result = run_protocol(protocol, initial_outgoing_frames, to_rid, from_rid);
            info!("Responder stopped: {result:?}");
        });
    });

    // Join threads
    let _ = [rid_thread, rrd_thread].map(|handle| handle.join().expect("Joining threads failed"));
}
