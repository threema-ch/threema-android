//! Payloads exchanged during the handshake phase.
use educe::Educe;
use libthreema_macros::Name;
use prost::Message as _;

use crate::{
    common::keys::PublicKey,
    d2m::{
        D2mProtocolError,
        payload::{PayloadDecoder, PayloadEncoder},
    },
    protobuf::d2m as protobuf,
    utils::{
        bytes::{ByteWriter, OwnedVecByteReader},
        debug::{Name, debug_slice_length},
        time::{ClockDelta, Duration},
    },
};

/// Decoder for a specific D2M payload.
trait SpecificPayloadDecoder: Sized + Name {
    const TYPE: u8;

    fn decode(reader: OwnedVecByteReader) -> Result<Self, D2mProtocolError>;
}
impl<TDecoder: SpecificPayloadDecoder> PayloadDecoder for TDecoder {
    fn decode(r#type: u8, reader: OwnedVecByteReader) -> Result<Self, D2mProtocolError> {
        // Ensure the type matches the one we expect
        if r#type != Self::TYPE {
            return Err(D2mProtocolError::UnexpectedPayloadType(r#type));
        }

        // Decode payload
        Self::decode(reader)
    }
}

#[repr(u8)]
enum HandshakePayloadType {
    ServerHello = 0x10,
    ClientHello = 0x11,
    ServerInfo = 0x12,
}

/// Initial message from the server, containing an authentication challenge.
#[derive(Name, Educe)]
#[educe(Debug)]
pub(crate) struct ServerHello {
    /// Highest protocol version [`protobuf::ProtocolVersion`] the server supports.
    pub(crate) version: u32,

    /// Ephemeral Server Key (ESK).
    pub(crate) ephemeral_server_key: PublicKey,

    /// 32 byte random challenge
    #[educe(Debug(method(debug_slice_length)))]
    pub(crate) challenge: [u8; 32],
}
impl SpecificPayloadDecoder for ServerHello {
    const TYPE: u8 = HandshakePayloadType::ServerHello as u8;

    fn decode(reader: OwnedVecByteReader) -> Result<Self, D2mProtocolError> {
        let protobuf::ServerHello {
            version,
            esk,
            challenge,
        } = Self::decode_protobuf(reader)?;
        Ok(Self {
            version,
            ephemeral_server_key: PublicKey::try_from(esk.as_slice()).map_err(|_| {
                D2mProtocolError::InvalidMessage {
                    name: Self::NAME,
                    cause: "Invalid ephemeral server key".to_owned(),
                }
            })?,
            challenge: challenge
                .try_into()
                .map_err(|_| D2mProtocolError::InvalidMessage {
                    name: Self::NAME,
                    cause: "Invalid challenge length".to_owned(),
                })?,
        })
    }
}

/// Initial message from the client, containing the authentication challenge response and additional login
/// information.
impl PayloadEncoder for protobuf::ClientHello {
    #[inline]
    fn type_and_length(&self) -> (u8, Option<usize>) {
        (HandshakePayloadType::ClientHello as u8, Some(self.encoded_len()))
    }

    #[inline]
    fn encode_into<TWriter: ByteWriter>(self, writer: &mut TWriter) -> Result<(), D2mProtocolError> {
        Self::encode_protobuf(self, writer)
    }
}

/// Parts of the server's configuration and the device slot state.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct ServerInfo {
    /// Clock delta between the server's time and the client's time.
    ///
    /// If the client's current timestamp deviates by more than 20 minutes, the client should disconnect and
    /// prompt the user to synchronise its clock. The user should also have an option to _connect anyway_
    /// which should be cached for a reasonable amount of time.
    pub clock_delta: ClockDelta,

    /// Maximum number of available device slots.
    pub max_device_slots: u32,

    /// Device slot state of the client on the server.
    pub device_slot_state: Option<protobuf::DeviceSlotState>,

    /// Device data shared among devices (`SharedDeviceData`), encrypted by `DGSDDK.secret` and prefixed with
    /// a random nonce.
    #[educe(Debug(method(debug_slice_length)))]
    pub encrypted_shared_device_data: Vec<u8>,

    /// Amount of messages in the reflection queue that will now be sent to the device. If the client is
    /// up-to-date, the value will be 0.
    ///
    /// Note: The amount of messages in the reflection queue may increase at any time, so there is no
    /// guarantee that `ReflectionQueueDry` will be received after having received `reflection_queue_length`
    /// reflected messages.
    pub reflection_queue_length: u32,
}
impl SpecificPayloadDecoder for ServerInfo {
    const TYPE: u8 = HandshakePayloadType::ServerInfo as u8;

    fn decode(reader: OwnedVecByteReader) -> Result<Self, D2mProtocolError> {
        let protobuf::ServerInfo {
            current_time,
            max_device_slots,
            device_slot_state,
            encrypted_shared_device_data,
            reflection_queue_length,
        } = Self::decode_protobuf(reader)?;
        Ok(Self {
            clock_delta: ClockDelta::calculate(Duration::from_millis(current_time)),
            max_device_slots,
            device_slot_state: protobuf::DeviceSlotState::try_from(device_slot_state).ok(),
            encrypted_shared_device_data,
            reflection_queue_length,
        })
    }
}
