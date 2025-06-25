//! Debug-related utilities.

use core::fmt;

use data_encoding::HEXLOWER;

use crate::crypto::x25519;

/// Formatter to format a slice to its length
pub(crate) fn debug_slice_length<T>(slice: &[T], formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
    write!(formatter, "length={}", slice.len())
}

/// Formatter to format bytes as hex
pub(crate) fn debug_bytes_hex(bytes: &[u8], formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
    formatter.write_str(&HEXLOWER.encode(bytes))
}

/// Formatter to format a [`x25519::StaticSecret`] to its public key
pub(crate) fn debug_static_secret(
    static_secret: &x25519::StaticSecret,
    formatter: &mut fmt::Formatter<'_>,
) -> fmt::Result {
    debug_bytes_hex(x25519::PublicKey::from(static_secret).as_bytes(), formatter)
}
