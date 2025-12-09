//! Protobuf utilities and extensions.
use duplicate::duplicate_item;
use rand::Rng as _;

use crate::{protobuf, utils::debug::Name};

impl<ProtobufMessage: prost::Name> Name for ProtobufMessage {
    const NAME: &'static str = ProtobufMessage::NAME;
}

struct PaddingConstraint {
    /// The minimum amount of total bytes that must always be met by adding padding.
    ///
    /// Should be chosen so that it sufficiently prevents the enclosed content from being guessed, e.g. the
    /// average length of the largest enclosed variant.
    minimum_total_length: u16,

    /// The maximum amount of additional padding bytes that may be added.
    ///
    /// Should be chosen so that it doesn't blow up any length limitations.
    maximum_padding_length: u16,
}
impl PaddingConstraint {
    // The largest possible tag has 29 bits which is 5 bytes in varint encoding (29 bits divided into five 7
    // bit payloads). The largest amount of padding has 16 bits which is 3 bytes in varint encoding (16 bits
    // divided into three 7 bit payloads). Makes 8 bytes.
    const MAX_PADDING_OVERHEAD_LENGTH: usize = 5 + 3;
}

/// Encode the message with padding to a newly allocated buffer. The padding is ensured to correctly take
/// the total encoded length into account but the varint encoding of the padding tag and length adds at
/// least two and at most eight bytes of variable overhead.
///
/// IMPORTANT: If another field with `padding_tag` exists that was encoded into the buffer, the resulting
/// message may either be deserialized into one or the other depending on the implementation.
fn encode_to_vec_padded<TMessage: prost::Message>(
    message: &TMessage,
    padding_tag: u32,
    constraint: &PaddingConstraint,
) -> Vec<u8> {
    // Generate random padding length
    let mut padding_length: u16 = rand::thread_rng().gen_range(0..constraint.maximum_padding_length);

    // Ensure the resulting data will be clamped to at least `minimum_total_length` bytes
    let message_length = message.encoded_len();
    if message_length
        .checked_add(padding_length as usize)
        .expect("message_length + padding_length should not blow up a usize")
        < constraint.minimum_total_length as usize
    {
        padding_length = constraint
            .minimum_total_length
            .checked_sub(
                message_length
                    .try_into()
                    .expect("message_length must be < minimum_total_length and therefore u32"),
            )
            .expect("minimum_total_length must be > message_length");
    }

    // Encode message
    let mut buffer = Vec::with_capacity(
        message_length
            .checked_add(padding_length as usize)
            .expect("message_length + padding_length should not blow up a usize")
            .checked_add(PaddingConstraint::MAX_PADDING_OVERHEAD_LENGTH)
            .expect(
                "message_length + MAX_PADDING_OVERHEAD_LENGTH + padding_length should not blow up a usize",
            ),
    );
    message.encode_raw(&mut buffer);

    // Encode padding header
    prost::encoding::encode_key(
        padding_tag,
        prost::encoding::WireType::LengthDelimited,
        &mut buffer,
    );
    prost::encoding::encode_varint(padding_length.into(), &mut buffer);

    // Encode padding bytes (33emafill)
    buffer.resize(
        buffer
            .len()
            .checked_add(padding_length as usize)
            .expect("message_length + padding overhead + padding_length should not blow up a usize"),
        0x33,
    );

    buffer
}

/// Post-encoding padding support to a message, so that the padding can be calculated based on the length of
/// the encoded message and appended afterwards.
///
/// TODO(LIB-47): This does not prevent the usage of `.encode_to_vec()`, so it can be easily missed.
/// TODO(LIB-72): Add tests
pub(crate) trait PaddedMessage: prost::Message {
    /// Encode the message with padding to a newly allocated buffer. The padding is ensured to correctly take
    /// the total encoded length into account but the varint encoding of the padding tag and length adds at
    /// least two and at most eight bytes of variable overhead.
    fn encode_to_vec_padded(&self) -> Vec<u8>
    where
        Self: Sized;
}

#[duplicate_item(
    [
        struct_name [ protobuf::csp_e2e::MessageMetadata ]
        padding_constraint [ PaddingConstraint { minimum_total_length: 32, maximum_padding_length: 64 } ]
    ]
    [
        struct_name [ protobuf::d2d::DeviceInfo ]
        padding_constraint [ PaddingConstraint { minimum_total_length: 64, maximum_padding_length: 128 } ]
    ]
    [
        struct_name [ protobuf::d2d::Envelope ]
        padding_constraint [ PaddingConstraint { minimum_total_length: 64, maximum_padding_length: 512 } ]
    ]
)]
impl PaddedMessage for struct_name {
    fn encode_to_vec_padded(&self) -> Vec<u8> {
        encode_to_vec_padded(self, Self::PADDING_TAG, &padding_constraint)
    }
}
