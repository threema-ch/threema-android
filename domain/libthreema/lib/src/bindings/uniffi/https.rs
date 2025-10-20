//! HTTPS request/response structures.
use duplicate::duplicate_item;

use crate::{
    https::{HttpsError, HttpsResponse},
    remote_secret::{monitor::RemoteSecretMonitorResponse, setup::RemoteSecretSetupResponse},
};

/// Binding version of [`crate::https::HttpsResult`].
#[derive(uniffi::Enum)]
pub enum HttpsResult {
    /// Contains the HTTPS response.
    Response(HttpsResponse),

    /// Contains the HTTPS error.
    Error(HttpsError),
}

#[duplicate_item(
    response_type;
    [ RemoteSecretSetupResponse ];
    [ RemoteSecretMonitorResponse ];
)]
impl From<HttpsResult> for response_type {
    fn from(response: HttpsResult) -> Self {
        let result = match response {
            HttpsResult::Response(https_response) => Ok(https_response),
            HttpsResult::Error(https_error) => Err(https_error),
        };

        Self { result }
    }
}
