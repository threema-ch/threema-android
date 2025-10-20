//! Common HTTPS endpoint items.
use crate::{
    common::config::{WorkContext, WorkFlavor},
    https::{HttpsError, HttpsHeadersBuilder},
    utils::time::Duration,
};

/// An error occurred while communicationg with a common HTTPS endpoint.
///
/// Note: Not all errors are expected from all endpoints. For example, an endpoint whose protocol
/// specification has no semantic meaning addressed to status code `403` will not map it to
/// [`HttpsEndpointError::Forbidden`] but instead map it to [`HttpsEndpointError::UnexpectedStatus`].
#[derive(Debug, thiserror::Error)]
pub(crate) enum HttpsEndpointError {
    /// A network error occurred.
    #[error("Network error: {0}")]
    NetworkError(#[from] HttpsError),

    /// Access forbidden
    #[error("Forbidden")]
    Forbidden,

    /// Not found
    #[error("Not found")]
    NotFound,

    /// Invalid authentication credentials
    #[error("Invalid credentials")]
    InvalidCredentials,

    /// The rate limit has been exceeded.
    #[error("Rate limit exceeded")]
    RateLimitExceeded,

    /// An authentication challenge expired
    #[error("Challenge expired")]
    ChallengeExpired,

    /// Authentication challenge response was invalid
    #[error("Invalid challenge response")]
    InvalidChallengeResponse,

    /// Unexpected status code.
    #[error("Unexpected status code '{0}'")]
    UnexpectedStatus(u16),

    /// Decoding the response failed.
    #[error("Decoding response failed: {0}")]
    DecodingFailed(#[from] serde_json::Error),

    /// The awkward response did not indicate success and contained a custom (possibly localized) error
    /// instead.
    #[error("Custom (possibly localized) error from server: {0}")]
    CustomPossiblyLocalizedError(String),
}

/// Default timeout for HTTPS endpoints.
pub(crate) const TIMEOUT: Duration = Duration::from_secs(15);

/// Returns a default set of headers for use with endpoints with basic auth added only if operating
/// in OnPrem mode.
pub(crate) fn https_headers_with_authentication(context: &WorkContext) -> HttpsHeadersBuilder {
    let credentials = match context.flavor {
        WorkFlavor::Work => None,
        WorkFlavor::OnPrem => Some(&context.credentials),
    };
    let https_headers = HttpsHeadersBuilder::default();
    match credentials {
        None => https_headers,
        Some(credentials) => https_headers.basic_auth(&credentials.username, &credentials.password),
    }
}
