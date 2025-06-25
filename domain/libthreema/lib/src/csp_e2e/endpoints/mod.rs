//! CSP E2EE specific HTTPS endpoints.
use super::{CspE2eProtocolError, WorkContext, WorkFlavor};
use crate::{
    common::WorkCredentials,
    https::{HttpsError, HttpsHeaders, HttpsHeadersBuilder},
    utils::time::Duration,
};

pub mod directory;
pub mod work_directory;

/// An error occurred while communicationg with a CSP E2EE specific HTTPS endpoint.
#[derive(Debug, thiserror::Error)]
pub(crate) enum CspE2eEndpointError {
    /// A network error occurred.
    #[error("Network error: {0}")]
    NetworkError(#[from] HttpsError),

    /// Invalid credentials
    #[error("Invalid credentials")]
    InvalidCredentials,

    /// The rate limit has been exceeded.
    #[error("Rate limit exceeded")]
    RateLimitExceeded,

    /// Unexpected status code.
    #[error("Unexpected status code '{0}'")]
    UnexpectedStatus(u16),

    /// Decoding the response failed.
    #[error("Decoding response failed: {0}")]
    DecodingFailed(#[from] serde_json::Error),
}
impl From<CspE2eEndpointError> for CspE2eProtocolError {
    fn from(error: CspE2eEndpointError) -> Self {
        match error {
            CspE2eEndpointError::NetworkError(_) => CspE2eProtocolError::NetworkError(error.to_string()),
            CspE2eEndpointError::InvalidCredentials => CspE2eProtocolError::InvalidCredentials,
            CspE2eEndpointError::RateLimitExceeded => CspE2eProtocolError::RateLimitExceeded,
            CspE2eEndpointError::UnexpectedStatus(_) | CspE2eEndpointError::DecodingFailed(_) => {
                CspE2eProtocolError::ServerError(error.to_string())
            },
        }
    }
}

/// Default timeout for CSP E2EE HTTPS endpoints.
const TIMEOUT: Duration = Duration::from_secs(15);

/// Returns a default set of headers for use with endpoints with basic auth added only if operating
/// in OnPrem mode.
fn https_headers_with_authentication(context: &WorkContext) -> HttpsHeadersBuilder {
    let credentials = match context.flavor {
        WorkFlavor::Work => None,
        WorkFlavor::OnPrem => Some(&context.credentials),
    };
    credentials.map_or_else(HttpsHeaders::builder, |WorkCredentials { username, password }| {
        HttpsHeaders::builder().basic_auth(username, password)
    })
}
