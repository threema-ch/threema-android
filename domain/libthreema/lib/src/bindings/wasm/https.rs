//! HTTPS request/response structures.
use duplicate::duplicate_item;
use serde::{Deserialize, Serialize};
use serde_bytes::ByteBuf;
use tsify::Tsify;

use crate::{
    https::{self, HttpsError},
    remote_secret::{monitor::RemoteSecretMonitorResponse, setup::RemoteSecretSetupResponse},
};

/// Binding version of [`https::HttpsRequest`].
#[derive(Tsify, Serialize)]
#[serde(rename_all = "camelCase")]
#[tsify(into_wasm_abi)]
pub struct HttpsRequest {
    /// Maximum amount of time in milliseconds the request is allowed to take before it should be aborted.
    pub timeout_ms: u32,

    /// HTTPS request URL.
    pub url: String,

    /// HTTPS request method.
    pub method: https::HttpsMethod,

    /// HTTPS headers to be used in the request.
    pub headers: Vec<https::HttpsHeader>,

    /// HTTPS body to be used in the request.
    pub body: ByteBuf,
}
impl From<https::HttpsRequest> for HttpsRequest {
    fn from(request: https::HttpsRequest) -> Self {
        Self {
            timeout_ms: request
                .timeout
                .as_millis()
                .try_into()
                .expect("timeout should not exceed a u32"),
            url: request.url,
            method: request.method,
            headers: request.headers,
            body: ByteBuf::from(request.body),
        }
    }
}

/// Binding version of [`https::HttpsResponse`].
#[derive(Tsify, Deserialize)]
#[serde(rename_all = "camelCase")]
#[tsify(from_wasm_abi)]
pub struct HttpsResponse {
    /// HTTPS response status code.
    pub status: u16,

    /// HTTPS body of the response.
    pub body: ByteBuf,
}

/// Binding version of [`https::HttpsResult`].
#[derive(Tsify, Deserialize)]
#[serde(
    tag = "type",
    content = "result",
    rename_all = "kebab-case",
    rename_all_fields = "camelCase"
)]
#[tsify(from_wasm_abi)]
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
            HttpsResult::Response(https_response) => Ok(https::HttpsResponse {
                status: https_response.status,
                body: https_response.body.to_vec(),
            }),
            HttpsResult::Error(https_error) => Err(https_error),
        };

        Self { result }
    }
}
