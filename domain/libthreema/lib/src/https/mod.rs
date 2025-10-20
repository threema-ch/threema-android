//! HTTPS request/response structures and endpoints.
use data_encoding::BASE64;
use educe::Educe;

use crate::{
    common::ClientInfo,
    utils::{debug::debug_slice_length, time::Duration},
};

pub(crate) mod endpoint;

pub mod directory;
pub mod work_directory;

/// HTTPS error for when a request failed or timed out.
#[derive(Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(
        tag = "type",
        content = "details",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    ),
    tsify(from_wasm_abi)
)]
pub enum HttpsError {
    /// The request was invalid, e.g. an invalid header name has been used. This usually indicates an error in
    /// construction of the request from libthreema.
    #[error("Invalid HTTPs request, details: {0}")]
    InvalidRequest(String),

    /// Server unreachable
    #[error("HTTPs server unreachable, details: {0}")]
    Unreachable(String),

    /// Server did not respond in time
    #[error("HTTPs server did not respond in time, details: {0}")]
    Timeout(String),

    /// The response was invalid, e.g. it was incomplete or otherwise faulty in any way.
    #[error("Invalid HTTPs response, details: {0}")]
    InvalidResponse(String),

    /// A different, unclassified error occurred.
    #[error("Unclassified error, details: {0}")]
    Unclassified(String),
}

/// HTTPS request method
#[derive(Debug, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Serialize),
    serde(rename_all = "kebab-case"),
    tsify(into_wasm_abi)
)]
pub enum HttpsMethod {
    /// Make a DELETE request
    Delete,

    /// Make a GET request
    Get,

    /// Make a POST request
    Post,

    /// Make a PUT request
    Put,
}

#[derive(Default)]
pub(crate) struct HttpsHeadersBuilder {
    authorization: Option<String>,
    accept: Option<&'static str>,
}
impl HttpsHeadersBuilder {
    pub(crate) fn basic_auth(mut self, username: &str, password: &str) -> Self {
        let encoded_credentials = BASE64.encode(format!("{username}:{password}").as_bytes());
        let _ = self.authorization.replace(format!("Basic {encoded_credentials}"));
        self
    }

    pub(crate) fn accept(mut self, accept: &'static str) -> Self {
        let _ = self.accept.replace(accept);
        self
    }

    pub(crate) fn build(self, client_info: &ClientInfo) -> Vec<HttpsHeader> {
        [
            ("user-agent", Some(client_info.to_user_agent())),
            ("authorization", self.authorization.clone()),
            ("accept", self.accept.map(ToOwned::to_owned)),
        ]
        .into_iter()
        .filter_map(|(name, value)| {
            value.map(|value| HttpsHeader {
                name: name.to_owned(),
                value,
            })
        })
        .collect()
    }
}

/// HTTPS header
#[cfg_attr(test, derive(Debug, PartialEq))]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Serialize),
    serde(rename_all = "camelCase"),
    tsify(into_wasm_abi)
)]
pub struct HttpsHeader {
    /// HTTPS header name
    pub name: String,

    /// HTTPS header value
    pub value: String,
}

/// An HTTPS request.
///
/// When handling this struct, run the following steps:
///
/// 1. Make an asynchronous HTTPS request from the provided parameters and make sure it gets cancelled after
///    `timeout`.¹
/// 2. Let `result` be the result of this HTTPS request, which can be either an HTTPS response or an error
///    (due to a timeout or another kind of error).
/// 3. Convert `result` into a [`HttpsResult`] and report it back to the appropriate task or protocol.
///
/// # IMPORTANT implementation notes
///
/// Any 3xx status codes (i.e. redirects) must be handled by the underlying client without reporting them back
/// to libthreema as a result!
///
/// The underlying client is responsible for applying SPKIs without any further notice!
#[derive(Educe)]
#[educe(Debug)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct HttpsRequest {
    /// Maximum amount of time the request is allowed to take before it should be aborted.
    pub timeout: Duration,

    /// HTTPS request URL.
    pub url: String,

    /// HTTPS request method.
    pub method: HttpsMethod,

    /// HTTPS headers to be used in the request.
    #[educe(Debug(ignore))]
    pub headers: Vec<HttpsHeader>,

    /// HTTPS body to be used in the request.
    #[educe(Debug(method = debug_slice_length))]
    pub body: Vec<u8>,
}

/// An HTTPS response.
#[derive(Educe)]
#[educe(Debug)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct HttpsResponse {
    /// HTTPS response status code.
    pub status: u16,

    /// HTTPS body of the response.
    #[educe(Debug(method = debug_slice_length))]
    pub body: Vec<u8>,
}

/// HTTPS response result
pub type HttpsResult = Result<HttpsResponse, HttpsError>;

/// Implementations for the CLI
#[cfg(feature = "cli")]
pub mod cli {
    use tracing::trace;

    use super::{HttpsError, HttpsHeader, HttpsMethod, HttpsRequest, HttpsResponse, HttpsResult};

    /// Default HTTPS client (builder) for the CLI.
    pub fn https_client_builder() -> reqwest::ClientBuilder {
        reqwest::ClientBuilder::new().user_agent("libthreema")
    }

    impl From<HttpsMethod> for reqwest::Method {
        fn from(method: HttpsMethod) -> Self {
            match method {
                HttpsMethod::Delete => reqwest::Method::DELETE,
                HttpsMethod::Get => reqwest::Method::GET,
                HttpsMethod::Post => reqwest::Method::POST,
                HttpsMethod::Put => reqwest::Method::PUT,
            }
        }
    }

    struct HttpsHeaderMap(reqwest::header::HeaderMap);
    impl TryFrom<Vec<HttpsHeader>> for HttpsHeaderMap {
        type Error = HttpsError;

        fn try_from(headers: Vec<HttpsHeader>) -> Result<Self, Self::Error> {
            let mut map = reqwest::header::HeaderMap::new();
            for header in headers {
                let name = reqwest::header::HeaderName::try_from(header.name)
                    .map_err(|_| HttpsError::InvalidRequest("Invalid header name".into()))?;
                let value = reqwest::header::HeaderValue::try_from(header.value)
                    .map_err(|_| HttpsError::InvalidRequest("Invalid header value".into()))?;
                let _ = map.insert(name, value);
            }
            Ok(HttpsHeaderMap(map))
        }
    }

    impl HttpsRequest {
        /// Send the HTTPS request and await the response.
        ///
        /// # Errors
        ///
        /// Returns [`HttpsError`] for all possible reasons.
        #[tracing::instrument(skip_all, fields(?self))]
        pub async fn send(self, http_client: &reqwest::Client) -> HttpsResult {
            // Send request and await response
            let request = http_client
                .request(reqwest::Method::from(self.method), self.url)
                .timeout(self.timeout)
                .headers(HttpsHeaderMap::try_from(self.headers)?.0)
                .body(self.body);
            trace!(?request, "Sending request");
            let response = request.send().await?;
            trace!(?response, "Got response");
            let status = response.status();
            let body = response.bytes().await?.to_vec();
            trace!(body_length = body.len(), "Got body");

            // Await the body, convert to response
            Ok(HttpsResponse {
                status: status.as_u16(),
                body,
            })
        }
    }

    impl From<reqwest::Error> for HttpsError {
        fn from(error: reqwest::Error) -> Self {
            if error.is_builder() || error.is_request() {
                return Self::InvalidRequest(error.to_string());
            }
            if error.is_timeout() {
                return Self::Timeout(error.to_string());
            }
            if error.is_body() || error.is_decode() {
                return Self::InvalidResponse(error.to_string());
            }
            Self::Unreachable(error.to_string())
        }
    }
}

#[cfg(test)]
mod tests {
    use rstest::rstest;

    use super::*;

    #[test]
    fn https_headers_default() {
        let headers = HttpsHeadersBuilder::default().build(&ClientInfo::Libthreema);
        let expected = vec![HttpsHeader {
            name: "user-agent".to_owned(),
            value: format!("libthreema/{version}", version = env!("CARGO_PKG_VERSION")),
        }];
        assert_eq!(headers, expected);
    }

    #[rstest]
    #[case(ClientInfo::Android {
        version: "1.2.3".to_owned(),
        locale: String::default(),
        device_model: String::default(),
        os_version: String::default(),
    }, "Threema Android/1.2.3")]
    #[rstest]
    #[case(ClientInfo::Ios {
        version: "2.3.4".to_owned(),
        locale: String::default(),
        device_model: String::default(),
        os_version: String::default(),
    }, "Threema iOS/2.3.4")]
    #[rstest]
    #[case(ClientInfo::Desktop {
        version: "3.4.5".to_owned(),
        locale: String::default(),
        renderer_name: String::default(),
        renderer_version: String::default(),
        os_name: String::default(),
        os_architecture: String::default(),
    }, "Threema Desktop/3.4.5")]
    #[rstest]
    fn https_headers_user_agent(#[case] client_info: ClientInfo, #[case] user_agent: String) {
        let headers = HttpsHeadersBuilder::default().build(&client_info);
        let expected = vec![HttpsHeader {
            name: "user-agent".to_owned(),
            value: user_agent,
        }];
        assert_eq!(headers, expected);
    }

    #[test]
    fn https_headers_basic_auth() {
        let headers = HttpsHeadersBuilder::default()
            .basic_auth("klo", "bürste")
            .build(&ClientInfo::Libthreema);
        let expected = vec![
            HttpsHeader {
                name: "user-agent".to_owned(),
                value: format!("libthreema/{version}", version = env!("CARGO_PKG_VERSION")),
            },
            HttpsHeader {
                name: "authorization".to_owned(),
                value: "Basic a2xvOmLDvHJzdGU=".to_owned(),
            },
        ];
        assert_eq!(headers, expected);
    }

    #[test]
    fn https_headers_accept() {
        let headers = HttpsHeadersBuilder::default()
            .accept("Nüscht")
            .build(&ClientInfo::Libthreema);
        let expected = vec![
            HttpsHeader {
                name: "user-agent".to_owned(),
                value: format!("libthreema/{version}", version = env!("CARGO_PKG_VERSION")),
            },
            HttpsHeader {
                name: "accept".to_owned(),
                value: "Nüscht".to_owned(),
            },
        ];
        assert_eq!(headers, expected);
    }

    #[test]
    fn https_headers_combination() {
        let headers = HttpsHeadersBuilder::default()
            .basic_auth("klo", "bürste")
            .accept("Nüscht")
            .build(&ClientInfo::Libthreema);
        let expected = vec![
            HttpsHeader {
                name: "user-agent".to_owned(),
                value: format!("libthreema/{version}", version = env!("CARGO_PKG_VERSION")),
            },
            HttpsHeader {
                name: "authorization".to_owned(),
                value: "Basic a2xvOmLDvHJzdGU=".to_owned(),
            },
            HttpsHeader {
                name: "accept".to_owned(),
                value: "Nüscht".to_owned(),
            },
        ];
        assert_eq!(headers, expected);
    }
}
