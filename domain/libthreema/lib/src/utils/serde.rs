//! Serde utilities.
pub(crate) mod base64 {
    use core::fmt;

    use data_encoding::BASE64;
    use serde::{
        Deserializer, Serialize as _, Serializer,
        de::{Error, Visitor},
    };

    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn serialize<S: Serializer, const N: usize>(
        value: &[u8; N],
        serializer: S,
    ) -> Result<S::Ok, S::Error> {
        BASE64.encode(value).serialize(serializer)
    }

    pub(crate) fn deserialize<'de, D: Deserializer<'de>, const N: usize>(
        deserializer: D,
    ) -> Result<[u8; N], D::Error> {
        struct Base64Visitor<const N: usize>;
        impl<const N: usize> Visitor<'_> for Base64Visitor<N> {
            type Value = [u8; N];

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                write!(formatter, "a base64 string containing {N} bytes")
            }

            fn visit_str<E: Error>(self, value: &str) -> Result<Self::Value, E> {
                let encoded = value.as_bytes();
                let length = BASE64.decode_len(encoded.len()).map_err(Error::custom)?;
                if length != N {
                    return Err(Error::custom(format_args!(
                        "invalid length {length}, expected {N}"
                    )));
                }
                let mut decoded = [0_u8; N];
                let length = BASE64
                    .decode_mut(encoded, &mut decoded)
                    .map_err(|partial| Error::custom(partial.error))?;
                if length != N {
                    return Err(Error::custom(format_args!(
                        "invalid decoded length {length}, expected {N}"
                    )));
                }
                Ok(decoded)
            }
        }
        deserializer.deserialize_str(Base64Visitor)
    }
}

pub(crate) mod string {
    use core::fmt;

    use serde::{
        Deserializer,
        de::{Error, Visitor},
    };

    pub(crate) fn empty_to_optional<'de, D: Deserializer<'de>>(
        deserializer: D,
    ) -> Result<Option<String>, D::Error> {
        struct StringVisitor;
        impl Visitor<'_> for StringVisitor {
            type Value = Option<String>;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                write!(formatter, "a string")
            }

            fn visit_string<E: Error>(self, value: String) -> Result<Self::Value, E> {
                Ok(if value.is_empty() { None } else { Some(value) })
            }

            fn visit_str<E: Error>(self, value: &str) -> Result<Self::Value, E> {
                Ok(if value.is_empty() {
                    None
                } else {
                    Some(value.to_owned())
                })
            }
        }
        deserializer.deserialize_string(StringVisitor)
    }
}
