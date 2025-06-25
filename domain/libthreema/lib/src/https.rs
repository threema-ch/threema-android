//! HTTPS request/response structures.
use data_encoding::BASE64;

use crate::utils::time::Duration;

/// HTTPS request method
pub enum HttpsMethod {
    /// Make a GET request
    GET,
    /// Make a POST request
    POST,
}

pub(crate) struct HttpsHeadersBuilder {
    user_agent: &'static str,
    authentication: Option<String>,
    accept: Option<&'static str>,
}
impl HttpsHeadersBuilder {
    pub(crate) fn basic_auth(mut self, username: &str, password: &str) -> Self {
        let encoded_credentials = BASE64.encode(format!("{username}:{password}").as_bytes());
        let _ = self
            .authentication
            .replace(format!("Basic {encoded_credentials}"));
        self
    }

    pub(crate) fn accept(mut self, accept: &'static str) -> Self {
        let _ = self.accept.replace(accept);
        self
    }
}
impl Default for HttpsHeadersBuilder {
    fn default() -> Self {
        Self {
            user_agent: const_format::formatcp!("libthreema,/{version}", version = env!("CARGO_PKG_VERSION")),
            accept: None,
            authentication: None,
        }
    }
}
impl From<HttpsHeadersBuilder> for HttpsHeaders {
    fn from(headers: HttpsHeadersBuilder) -> Self {
        Self(
            [
                ("user-agent", Some(headers.user_agent)),
                ("authentication", headers.authentication.as_deref()),
                ("accept", headers.accept),
            ]
            .iter()
            .filter_map(|(name, value)| value.map(|value| ((*name).to_owned(), value.to_owned())))
            .collect(),
        )
    }
}

/// HTTPS headers, containing a sequence of header name and value pairs.
pub struct HttpsHeaders(pub Vec<(String, String)>);
impl HttpsHeaders {
    pub(crate) fn builder() -> HttpsHeadersBuilder {
        HttpsHeadersBuilder { ..Default::default() }
    }
}

/// An HTTPS request.
///
/// TODO(LIB-16):
/// - Add Steps
/// - Note that the implementation must set the necessary SPKIs themselves
pub struct HttpsRequest {
    /// Maximum amount of time the request is allowed to take before it should be aborted.
    pub timeout: Duration,
    /// HTTPS request URL.
    pub url: String,
    /// HTTPS request method.
    pub method: HttpsMethod,
    /// HTTPS headers to be used in the request.
    pub headers: HttpsHeaders,
    /// HTTPS body to be used in the request.
    pub body: Vec<u8>,
}

/// HTTPS error for when a request failed or timed out.
#[derive(Debug, thiserror::Error)]
pub enum HttpsError {
    /// Server unreachable
    #[error("HTTPs server unreachable")]
    Unreachable,

    /// Server did not respond in time
    #[error("HTTPs server did not respond in time")]
    Timeout,
}

/// An HTTPS response.
pub struct HttpsResponse {
    /// HTTPS response status code.
    pub status: u16,
    /// HTTPS body of the response.
    pub body: Vec<u8>,
}

/// HTTPS response result
pub type HttpsResult = Result<HttpsResponse, HttpsError>;
