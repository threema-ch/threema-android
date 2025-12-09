//! Incoming/outgoing payloads.
use core::fmt;
use std::collections::VecDeque;

use educe::Educe;
use libthreema_macros::Name;

pub(super) mod handshake;
mod post_handshake;
pub use handshake::ServerInfo;
pub use post_handshake::*;

use crate::{
    d2m::{D2mProtocolError, InternalEncodingErrorCause, InternalErrorCause},
    utils::{
        bytes::{ByteReader as _, ByteWriter, OwnedVecByteReader, OwnedVecByteWriter},
        debug::{Name, debug_slice_length},
    },
};

/// Flags attached to reflected message payloads.
#[derive(Clone, Default, PartialEq, Name)]
pub struct ReflectFlags(pub u16);
impl ReflectFlags {
    /// Marks the message as _ephemeral_. The server will forward the message only to devices that are
    /// currently connected while still maintaining the order of the reflection queue. No acknowledgement will
    /// be sent.
    pub const EPHEMERAL_MARKER: u16 = 0x00_01;
}
impl fmt::Debug for ReflectFlags {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let check_flag = |flag: u16, name: &'static str| -> Option<&'static str> {
            if self.0 & flag != 0 { Some(name) } else { None }
        };

        write!(
            formatter,
            "{}({})",
            Self::NAME,
            itertools::join(
                [check_flag(Self::EPHEMERAL_MARKER, "ephemeral"),]
                    .into_iter()
                    .flatten(),
                ", ",
            ),
        )
    }
}

/// An outgoing D2M payload encoded as a datagram to be sent to the mediator server.
#[derive(Educe)]
#[educe(Debug)]
pub struct OutgoingDatagram(#[educe(Debug(method(debug_slice_length)))] pub Vec<u8>);

const PAYLOAD_HEADER_RESERVED: [u8; 3] = [0_u8; 3];

/// Decoder for a specific subset of D2M payloads.
pub(super) trait PayloadDecoder: Sized + Name {
    /// Try decoding a D2M payload from its type and a reader containing all of the payload's data.
    fn decode(r#type: u8, reader: OwnedVecByteReader) -> Result<Self, D2mProtocolError>;

    /// Decode a D2M payload protobuf message.
    fn decode_protobuf<TMessage: Default + Name + prost::Message>(
        mut reader: OwnedVecByteReader,
    ) -> Result<TMessage, D2mProtocolError> {
        TMessage::decode(reader.read_remaining()).map_err(|error| D2mProtocolError::DecodingFailed {
            name: TMessage::NAME,
            source: error.into(),
        })
    }

    /// Decode a datagram with the associated container header to a D2M payload.
    fn decode_from_datagram(datagram: Vec<u8>) -> Result<Self, D2mProtocolError> {
        let mut reader = OwnedVecByteReader::new(datagram);

        // Read the type
        let r#type = reader
            .read_u8()
            .map_err(|error| D2mProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error.into(),
            })?;

        // Skip reserved bytes
        reader
            .skip(PAYLOAD_HEADER_RESERVED.len())
            .map_err(|error| D2mProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error.into(),
            })?;

        // Try decoding to payload
        Self::decode(r#type, reader)
    }
}

/// Encoder for a D2M payload.
pub(super) trait PayloadEncoder: Sized + Name {
    const HEADER_LENGTH: usize = 1 + PAYLOAD_HEADER_RESERVED.len();

    /// D2M payload type and length when encoded, if known.
    fn type_and_length(&self) -> (u8, Option<usize>);

    /// Encode the D2M payload into a writer.
    fn encode_into<TWriter: ByteWriter>(self, writer: &mut TWriter) -> Result<(), D2mProtocolError>;

    /// Encode a D2M payload protobuf message.
    fn encode_protobuf<TMessage: Default + Name + prost::Message, TWriter: ByteWriter>(
        message: TMessage,
        writer: &mut TWriter,
    ) -> Result<(), D2mProtocolError> {
        let mut buffer = writer.write_in_place(message.encoded_len()).map_err(|error| {
            D2mProtocolError::InternalError(InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: InternalEncodingErrorCause::ByteWriterError(error),
            })
        })?;
        message.encode(&mut buffer).map_err(|error| {
            D2mProtocolError::InternalError(InternalErrorCause::EncodingFailed {
                name: Self::NAME,
                source: InternalEncodingErrorCause::ProtobufEncodeError(error),
            })
        })?;
        Ok(())
    }

    /// Encode a D2M payload into a datagram with the associated container header.
    fn encode_to_datagram(self) -> Result<Vec<u8>, D2mProtocolError> {
        // Determine the payload type and length
        let (r#type, length) = self.type_and_length();

        // Create writer for encoding
        let mut writer = OwnedVecByteWriter::new_with_capacity(
            Self::HEADER_LENGTH.saturating_add(length.unwrap_or_default()),
        );

        // Encode type and reserved bytes
        writer
            .run(|writer| {
                writer.write_u8(r#type)?;
                writer.write(&PAYLOAD_HEADER_RESERVED)
            })
            .map_err(|error| {
                D2mProtocolError::InternalError(InternalErrorCause::EncodingFailed {
                    name: Self::NAME,
                    source: InternalEncodingErrorCause::ByteWriterError(error),
                })
            })?;

        // Encode the payload
        self.encode_into(&mut writer)?;
        Ok(writer.into_inner())
    }
}

/// Contains an arbitrary amount of incoming datagrams.
#[derive(Default, Name)]
pub(super) struct DatagramBuffer(VecDeque<Vec<u8>>);
impl DatagramBuffer {
    /// Add datagrams to the decoder.
    #[inline]
    pub(super) fn add_datagrams(&mut self, datagrams: Vec<Vec<u8>>) {
        self.0.extend(datagrams);
    }

    /// Get the next datagram from the decoder.
    #[inline]
    pub(super) fn next(&mut self) -> Option<Vec<u8>> {
        self.0.pop_front()
    }
}
impl fmt::Debug for DatagramBuffer {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&format_args!(
                "#datagrams={length}, byte-length={byte_length}",
                length = self.0.len(),
                byte_length = self.0.iter().map(Vec::len).sum::<usize>(),
            ))
            .finish()
    }
}
