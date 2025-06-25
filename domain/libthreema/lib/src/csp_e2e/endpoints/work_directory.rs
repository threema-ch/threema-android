//! Work directory endpoint.
use serde::{Deserialize, Serialize};

use super::{CspE2eEndpointError, TIMEOUT, https_headers_with_authentication};
use crate::{
    common::{self, ThreemaId},
    csp_e2e::{WorkContext, config::Config},
    https::{HttpsHeadersBuilder, HttpsMethod, HttpsRequest, HttpsResult},
    utils::serde::{base64, string},
};

#[derive(Serialize)]
struct WorkCredentials<'creds> {
    username: &'creds str,
    password: &'creds str,
}
impl<'creds> From<&'creds common::WorkCredentials> for WorkCredentials<'creds> {
    fn from(credentials: &'creds common::WorkCredentials) -> Self {
        Self {
            username: &credentials.username,
            password: &credentials.password,
        }
    }
}

#[inline]
fn https_headers(context: &WorkContext) -> HttpsHeadersBuilder {
    https_headers_with_authentication(context)
}

fn preprocess_result(result: HttpsResult) -> Result<Vec<u8>, CspE2eEndpointError> {
    let response = result?;
    match response.status {
        200 => Ok(response.body),
        401 => Err(CspE2eEndpointError::InvalidCredentials),
        429 => Err(CspE2eEndpointError::RateLimitExceeded),
        status => Err(CspE2eEndpointError::UnexpectedStatus(status)),
    }
}

#[derive(Serialize)]
struct WorkContactsRequest<'body> {
    #[serde(flatten)]
    credentials: WorkCredentials<'body>,
    #[serde(rename = "contacts")]
    identities: &'body [ThreemaId],
}

/// Request additonal properties associated to a set of contacts of the same Work
/// subscription.
pub(crate) fn request_contacts(
    config: &Config,
    context: &WorkContext,
    identities: &[ThreemaId],
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: config.work_server_url.path(format_args!("identities")),
        method: HttpsMethod::POST,
        headers: https_headers(context).accept("application/json").into(),
        body: serde_json::to_vec(&WorkContactsRequest {
            credentials: (&context.credentials).into(),
            identities,
        })
        .expect("Failed to create directory identities request body"),
    }
}

/// Additional Work properties associated to a specific contact.
#[derive(Deserialize)]
pub(crate) struct WorkContact {
    #[serde(rename = "id")]
    pub(crate) identity: ThreemaId,
    #[serde(rename = "pk", with = "base64")]
    pub(crate) public_key: [u8; 32],
    #[serde(rename = "first", deserialize_with = "string::empty_to_optional")]
    pub(crate) first_name: Option<String>,
    #[serde(rename = "last", deserialize_with = "string::empty_to_optional")]
    pub(crate) last_name: Option<String>,
}

/// Process the result and map it to a subset of the provided contacts that are part of the same
/// Work subscription with the associated additional Work properties.
pub(crate) fn handle_contacts_result(result: HttpsResult) -> Result<Vec<WorkContact>, CspE2eEndpointError> {
    let body = preprocess_result(result)?;
    let amendments: Vec<WorkContact> = serde_json::from_slice(&body)?;
    Ok(amendments)
}
