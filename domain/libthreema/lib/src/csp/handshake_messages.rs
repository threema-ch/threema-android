use libthreema_macros::{Name, concat_fixed_bytes};

use super::{
    ClientCookie, Context, CspProtocolError, ServerCookie, TemporaryServerKey,
    frame::{FrameEncoder, OutgoingFrame},
};
use crate::{
    common::{Cookie, CspDeviceId, PublicKey, ThreemaId},
    crypto::{digest::MAC_256_LENGTH, salsa20, x25519},
    utils::{
        bytes::{ByteReader as _, ByteWriter, OwnedVecByteWriter, SliceByteReader},
        debug::debug_slice_length,
        frame::FixedLengthFrameDecoder,
    },
};

/// Initial message from the client, containing a server authentication challenge in order to
/// establish transport layer encryption.
#[derive(Debug, Name)]
pub(super) struct ClientHello {
    pub(super) temporary_client_key_public: PublicKey,
    pub(super) client_cookie: ClientCookie,
}
impl ClientHello {
    const LENGTH: usize = PublicKey::LENGTH + Cookie::LENGTH;
}
impl FrameEncoder for ClientHello {
    fn encode_to_frame(&self) -> Result<OutgoingFrame, CspProtocolError> {
        let encoded: [u8; Self::LENGTH] = concat_fixed_bytes!(
            *self.temporary_client_key_public.0.as_bytes(),
            self.client_cookie.0.0
        );
        Ok(OutgoingFrame(encoded.to_vec()))
    }
}

/// Initial message from the server.
///
/// This concludes establishing transport layer encryption based on temporary client and server key.
#[derive(Debug)]
pub(super) struct ServerHello {
    pub(super) server_cookie: ServerCookie,

    /// The encrypted [`ServerChallengeResponse`].
    pub(super) server_challenge_response_box: [u8; Self::SERVER_CHALLENGE_RESPONSE_BOX_LENGTH],
}
impl ServerHello {
    /// Total byte length
    pub(super) const LENGTH: usize = Cookie::LENGTH + Self::SERVER_CHALLENGE_RESPONSE_BOX_LENGTH;
    /// Byte length of the encrypted [`ServerChallengeResponse`]
    const SERVER_CHALLENGE_RESPONSE_BOX_LENGTH: usize =
        ServerChallengeResponse::LENGTH + { salsa20::TAG_LENGTH };
}
impl From<&[u8; Self::LENGTH]> for ServerHello {
    fn from(data: &[u8; Self::LENGTH]) -> Self {
        let mut reader = SliceByteReader::new(data);

        let server_cookie = ServerCookie(Cookie(
            reader
                .read_fixed::<{ Cookie::LENGTH }>()
                .expect("data must be >= Cookie::LENGTH"),
        ));

        let server_challenge_response_box = reader
            .read_fixed::<{ Self::SERVER_CHALLENGE_RESPONSE_BOX_LENGTH }>()
            .expect("data must be >= Cookie::LENGTH + SERVER_CHALLENGE_BOX_RESPONSE_LENGTH");

        Self {
            server_cookie,
            server_challenge_response_box,
        }
    }
}

/// [`ServerHello`] frame decoder
pub(super) type ServerHelloDecoder = FixedLengthFrameDecoder<{ ServerHello::LENGTH }>;

/// Authentication challenge response from the server.
#[derive(Debug, Name)]
pub(super) struct ServerChallengeResponse {
    pub(super) temporary_server_key: TemporaryServerKey,
    pub(super) repeated_client_cookie: ClientCookie,
}
impl ServerChallengeResponse {
    /// Byte length
    const LENGTH: usize = PublicKey::LENGTH + Cookie::LENGTH;
}
impl TryFrom<&[u8]> for ServerChallengeResponse {
    type Error = CspProtocolError;

    fn try_from(data: &[u8]) -> Result<Self, Self::Error> {
        let mut reader = SliceByteReader::new(data);
        reader
            .run(|reader| {
                let temporary_server_key = TemporaryServerKey(PublicKey(x25519::PublicKey::from(
                    reader.read_fixed::<{ PublicKey::LENGTH }>()?,
                )));
                let repeated_client_cookie = ClientCookie(Cookie(reader.read_fixed::<{ Cookie::LENGTH }>()?));
                Ok(Self {
                    temporary_server_key,
                    repeated_client_cookie,
                })
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// Supported CSP features bit mask.
#[derive(Clone, Copy)]
struct Features(pub(super) u8);
#[rustfmt::skip]
impl Features {
    /// Supports the `message-with-metadata-box`.
    const SUPPORTS_MESSAGE_WITH_METADATA_BOX: u8 = 0b_0000_0001;
    /// Supports reception of `echo-request`s.
    const SUPPORTS_RECEIVING_ECHO_REQUEST: u8 = 0b_0000_0010;
}

/// An extension field.
#[derive(Name)]
enum Extension {
    /// Client info extension payload
    ClientInfo(String),

    /// CSP device ID extension payload
    CspDeviceId(CspDeviceId),

    /// Supported CSP features
    SupportedFeatures(Features),

    /// A 16 byte random value chosen by the client
    DeviceCookie(u16),
}
impl Extension {
    /// Encode this extension into the provided `writer`.
    fn encode_into(&self, writer: &mut impl ByteWriter) -> Result<(), CspProtocolError> {
        match self {
            Extension::ClientInfo(client_info) => {
                let client_info = client_info.as_bytes();
                Self::encode_header(
                    writer,
                    0x00,
                    client_info
                        .len()
                        .try_into()
                        .map_err(|_| CspProtocolError::InternalError("Oversized client info exceeds u16"))?,
                )?;
                writer
                    .write(client_info)
                    .map_err(|error| CspProtocolError::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })
            },
            Extension::CspDeviceId(device_id) => {
                Self::encode_header(writer, 0x01, 8)?;
                writer
                    .write_u64_le(device_id.0)
                    .map_err(|error| CspProtocolError::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })
            },
            Extension::SupportedFeatures(features) => {
                Self::encode_header(writer, 0x02, 1)?;
                writer
                    .write_u8(features.0)
                    .map_err(|error| CspProtocolError::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })
            },
            Extension::DeviceCookie(device_cookie) => {
                Self::encode_header(writer, 0x03, 2)?;
                writer
                    .write_u16_le(*device_cookie)
                    .map_err(|error| CspProtocolError::EncodingFailed {
                        name: Self::NAME,
                        source: error,
                    })
            },
        }
    }

    fn encode_header(
        writer: &mut impl ByteWriter,
        extension_type: u8,
        length: u16,
    ) -> Result<(), CspProtocolError> {
        writer
            .run(|writer| {
                writer.write_u8(extension_type)?;
                writer.write_u16_le(length)
            })
            .map_err(|error| CspProtocolError::EncodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// A collection of extensions.
#[derive(Name)]
pub(super) struct Extensions(Vec<Extension>);
impl Extensions {
    const ENCRYPTION_OVERHEAD_LENGTH: usize = salsa20::TAG_LENGTH;

    /// Create a set of extensions of all necessary extensions from the provided context.
    pub(super) fn new(context: &Context) -> Self {
        // Set the supported features and client info
        let mut extensions = vec![
            Extension::SupportedFeatures(Features(
                Features::SUPPORTS_MESSAGE_WITH_METADATA_BOX | Features::SUPPORTS_RECEIVING_ECHO_REQUEST,
            )),
            Extension::ClientInfo(context.client_info.clone()),
        ];

        // Add CSP device ID, if any
        extensions.extend(context.csp_device_id.map(Extension::CspDeviceId));

        // Add device cookie, if any
        extensions.extend(context.device_cookie.map(Extension::DeviceCookie));

        Self(extensions)
    }

    /// Encode all extensions and return them alongside the extension length **with encryption
    /// overhead** needed for [`LoginData`].
    pub(super) fn encode(&self) -> Result<(Vec<u8>, u16), CspProtocolError> {
        // Encode all extensions, one after another
        let mut writer = OwnedVecByteWriter::new_empty();
        for extension in &self.0 {
            extension.encode_into(&mut writer)?;
        }
        let encoded = writer.into_inner();
        let length_with_overhead: u16 = encoded
            .len()
            .checked_add(Self::ENCRYPTION_OVERHEAD_LENGTH)
            .ok_or(CspProtocolError::InternalError(
                "Encoded extensions length exceeded a u16",
            ))?
            .try_into()
            .map_err(|_| CspProtocolError::InternalError("Encoded extensions length exceeded a u16"))?;
        Ok((encoded, length_with_overhead))
    }
}

/// Login data of the client.
#[derive(educe::Educe, Name)]
#[educe(Debug)]
pub(super) struct LoginData {
    pub(super) identity: ThreemaId,

    /// Byte length of the **encrypted** extensions (meaning with overhead, provided separately
    /// within [`Login`])
    pub(super) extensions_byte_length: u16,

    /// Repeated server connection Cookie (SCK), acting as the client's challenge response
    pub(super) repeated_server_cookie: ServerCookie,

    /// Session voucher
    #[educe(Debug(method(debug_slice_length)))]
    pub(super) vouch: [u8; MAC_256_LENGTH],
}
impl LoginData {
    const EXTENSION_INDICATOR_LENGTH: usize = 32;
    /// Magic string to indicate presence of extension indicator
    const EXTENSION_INDICATOR_MAGIC_STRING: [u8; 30] = *b"threema-clever-extension-field";
    const LENGTH: usize = ThreemaId::LENGTH
        + Self::EXTENSION_INDICATOR_LENGTH
        + Cookie::LENGTH
        + Self::RESERVED_1_LENGTH
        + MAC_256_LENGTH
        + Self::RESERVED_2_LENGTH;
    const RESERVED_1_LENGTH: usize = 24;
    const RESERVED_2_LENGTH: usize = 16;

    /// Encode the login data.
    pub(super) fn encode(&self) -> [u8; Self::LENGTH] {
        concat_fixed_bytes!(
            // Encode identity
            self.identity.to_bytes(),
            // Encode the extension indicator
            Self::EXTENSION_INDICATOR_MAGIC_STRING,
            u16::to_le_bytes(self.extensions_byte_length),
            // Encode repeated server connection cookie
            self.repeated_server_cookie.0.0,
            // Encode reserved #1
            [0_u8; Self::RESERVED_1_LENGTH],
            // Encode session voucher
            self.vouch,
            // Encode reserved #2
            [0_u8; Self::RESERVED_2_LENGTH],
        )
    }
}

const LOGIN_DATA_BOX_LENGTH: usize = LoginData::LENGTH + { salsa20::TAG_LENGTH };

/// Login request from the client.
#[derive(Name, educe::Educe)]
#[educe(Debug)]
pub(super) struct Login {
    /// The encrypted [`LoginData`].
    #[educe(Debug(method(debug_slice_length)))]
    pub(super) login_data_box: [u8; LOGIN_DATA_BOX_LENGTH],

    /// The encrypted extensions
    #[educe(Debug(method(debug_slice_length)))]
    pub(super) extensions_box: Vec<u8>,
}
impl Login {
    pub(super) const LOGIN_DATA_BOX_LENGTH: usize = LoginData::LENGTH + { salsa20::TAG_LENGTH };
}
impl FrameEncoder for Login {
    fn encode_to_frame(&self) -> Result<OutgoingFrame, CspProtocolError> {
        Ok(OutgoingFrame(
            [self.login_data_box.as_slice(), self.extensions_box.as_slice()].concat(),
        ))
    }
}

/// Login acknowledgement data from the server.
#[derive(Debug, Name)]
pub(super) struct LoginAckData {
    /// The current timestamp of the server.
    #[expect(unused, reason = "Will use later")]
    pub(super) current_time_utc: u64,

    /// Amount of queued messages on the server for the client.
    pub(super) queued_messages: u32,
}
impl LoginAckData {
    const LENGTH: usize = Self::RESERVED_LENGTH + 8 + 4;
    const RESERVED_LENGTH: usize = 4;
}
impl TryFrom<&[u8]> for LoginAckData {
    type Error = CspProtocolError;

    fn try_from(data: &[u8]) -> Result<Self, Self::Error> {
        let mut reader = SliceByteReader::new(data);
        reader
            .run(|reader| {
                reader.skip(Self::RESERVED_LENGTH)?;
                let current_time_utc = reader.read_u64_le()?;
                let queued_messages = reader.read_u32_le()?;
                Ok(Self {
                    current_time_utc,
                    queued_messages,
                })
            })
            .map_err(|error| CspProtocolError::DecodingFailed {
                name: Self::NAME,
                source: error,
            })
    }
}

/// Login acknowledgment from the server.
#[derive(Name)]
pub(super) struct LoginAck {
    /// The encrypted [`LoginAckData`].
    pub(super) login_ack_data_box: [u8; Self::LOGIN_ACK_DATA_BOX_LENGTH],
}
impl LoginAck {
    pub(super) const LENGTH: usize = Self::LOGIN_ACK_DATA_BOX_LENGTH;
    const LOGIN_ACK_DATA_BOX_LENGTH: usize = LoginAckData::LENGTH + salsa20::TAG_LENGTH;
}
impl From<&[u8; Self::LENGTH]> for LoginAck {
    fn from(data: &[u8; Self::LENGTH]) -> Self {
        let mut reader = SliceByteReader::new(data);

        let login_ack_data_box = reader
            .read_fixed::<{ Self::LOGIN_ACK_DATA_BOX_LENGTH }>()
            .expect("data must be >= LOGIN_ACK_DATA_BOX_LENGTH");

        Self { login_ack_data_box }
    }
}

/// [`LoginAck`] frame decoder
pub(super) type LoginAckDecoder = FixedLengthFrameDecoder<{ LoginAck::LENGTH }>;
