//! Remote Secret Setup protocol.
use libthreema_macros::Name;

use crate::{
    common::{
        ClientInfo, ThreemaId,
        config::{WorkContext, WorkServerBaseUrl},
        keys::ClientKey,
    },
    https::{HttpsRequest, HttpsResult, endpoint::HttpsEndpointError},
};

pub mod create;
pub mod delete;

/// An error occurred while setting up the remote secret.
///
/// Note: Errors can occur when using the API incorrectly or when the remote server behaves incorrectly. None
/// of these errors are considered recoverable.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`RemoteSecretSetupError`].
/// 2. Run the steps defined for `error`. If none are specified, abort the protocol due to `error` and retry
///    in 10s.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
pub enum RemoteSecretSetupError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Invalid state for the requested operation.
    #[error("Invalid state: {0}")]
    InvalidState(&'static str),

    /// An unrecoverable network error occurred while communicating with a server.
    #[error("Network error: {0}")]
    NetworkError(String),

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(String),

    /// Invalid credentials (should only be relevant for Work and OnPrem) reported by a server caused an
    /// operation to fail.
    ///
    /// When processing this variant, abort the task and notify the user that the Work credentials are invalid
    /// and request new ones before making a new attempt.
    #[error("Invalid credentials")]
    InvalidCredentials,

    /// A rate limit of a server has been exceeded.
    #[error("Rate limit exceeded")]
    RateLimitExceeded,
}
impl From<HttpsEndpointError> for RemoteSecretSetupError {
    fn from(error: HttpsEndpointError) -> Self {
        match error {
            HttpsEndpointError::NetworkError(_) | HttpsEndpointError::ChallengeExpired => {
                Self::NetworkError(error.to_string())
            },
            HttpsEndpointError::InvalidCredentials => Self::InvalidCredentials,
            HttpsEndpointError::RateLimitExceeded => Self::RateLimitExceeded,
            HttpsEndpointError::Forbidden
            | HttpsEndpointError::NotFound
            | HttpsEndpointError::InvalidChallengeResponse
            | HttpsEndpointError::UnexpectedStatus(_)
            | HttpsEndpointError::DecodingFailed(_)
            | HttpsEndpointError::CustomPossiblyLocalizedError(_) => Self::ServerError(error.to_string()),
        }
    }
}

/// 1. Run the HTTPS request as defined by [`HttpsRequest`] and let `response` be the result.
/// 2. Provide `response` to the associated task as a [`HttpsResult`] and poll again.
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct RemoteSecretSetupInstruction {
    /// The HTTPs request to be made.
    pub request: HttpsRequest,
}

/// Possible response to a [`RemoteSecretSetupInstruction`].
#[derive(Name)]
pub struct RemoteSecretSetupResponse {
    /// Result for the HTTPS request.
    pub result: HttpsResult,
}

/// Context for creating or removing a remote secret.
pub struct RemoteSecretSetupContext {
    /// Client info
    pub client_info: ClientInfo,

    /// Work server URL from the configuration.
    pub work_server_url: WorkServerBaseUrl,

    /// Work (or OnPrem) application configuration.
    pub work_context: WorkContext,

    /// The user's identity.
    pub user_identity: ThreemaId,

    /// Client key.
    pub client_key: ClientKey,
}

#[cfg(test)]
mod tests {

    use super::*;
    use crate::common::{
        ThreemaId,
        config::{Config, WorkContext, WorkCredentials, WorkFlavor},
        keys::ClientKey,
    };

    pub(crate) fn setup_context() -> RemoteSecretSetupContext {
        let config = Config::testing();
        RemoteSecretSetupContext {
            client_info: ClientInfo::Libthreema,
            work_server_url: config.work_server_url,
            work_context: WorkContext {
                credentials: WorkCredentials {
                    username: "klo".to_owned(),
                    password: "bürste".to_owned(),
                },
                flavor: WorkFlavor::Work,
            },
            user_identity: ThreemaId::predefined(*b"TESTTEST"),
            client_key: ClientKey::from([0_u8; ClientKey::LENGTH]),
        }
    }
}
