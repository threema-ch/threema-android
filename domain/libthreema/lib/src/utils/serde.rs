//! Serde utilities.
pub(crate) mod base64 {
    pub(crate) mod fixed_length {
        use core::fmt;

        use data_encoding::BASE64;
        use serde::{
            Deserializer, Serialize as _, Serializer,
            de::{Error, Visitor},
        };

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
                    if length < N || length > N.saturating_add(2) {
                        return Err(Error::custom(format_args!(
                            "invalid length {length}, expected at most {}",
                            N.saturating_add(2)
                        )));
                    }
                    let decoded = BASE64.decode(encoded).map_err(|error| Error::custom(error))?;
                    let length = decoded.len();
                    decoded.try_into().map_err(|_| {
                        Error::custom(format_args!("invalid decoded length {length}, expected {N}",))
                    })
                }
            }
            deserializer.deserialize_str(Base64Visitor)
        }
    }

    pub(crate) mod variable_length {
        use core::fmt;

        use data_encoding::BASE64;
        use serde::{
            Deserializer, Serialize as _, Serializer,
            de::{Error, Visitor},
        };

        pub(crate) fn serialize<S: Serializer>(value: &[u8], serializer: S) -> Result<S::Ok, S::Error> {
            BASE64.encode(value).serialize(serializer)
        }

        pub(crate) fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Vec<u8>, D::Error> {
            struct Base64Visitor;
            impl Visitor<'_> for Base64Visitor {
                type Value = Vec<u8>;

                fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                    write!(
                        formatter,
                        "a base64 string containing an arbitrary amount of bytes"
                    )
                }

                fn visit_str<E: Error>(self, value: &str) -> Result<Self::Value, E> {
                    let encoded = value.as_bytes();
                    BASE64.decode(encoded).map_err(|error| Error::custom(error))
                }
            }
            deserializer.deserialize_str(Base64Visitor)
        }
    }
}

pub(crate) mod from_str {
    use core::{
        fmt::{self},
        marker::PhantomData,
        str::FromStr,
    };

    use serde::{
        Deserializer,
        de::{Error, Visitor},
    };

    pub(crate) fn deserialize<'de, D: Deserializer<'de>, T>(deserializer: D) -> Result<T, D::Error>
    where
        T: FromStr,
        T::Err: fmt::Display,
    {
        struct TVisitor<TI>(PhantomData<TI>);
        impl<TI: FromStr> Visitor<'_> for TVisitor<TI>
        where
            TI::Err: fmt::Display,
        {
            type Value = TI;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                write!(formatter, "valid matching string")
            }

            fn visit_str<E: Error>(self, value: &str) -> Result<Self::Value, E> {
                value.parse::<Self::Value>().map_err(Error::custom)
            }
        }
        deserializer.deserialize_string(TVisitor(PhantomData))
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
