//! Directory endpoint.
use serde::{Deserialize, Serialize};
use serde_repr::Deserialize_repr;

use super::endpoint::{HttpsEndpointError, TIMEOUT, https_headers_with_authentication};
use crate::{
    common::{
        ChatServerGroup, ClientInfo, FeatureMask, ThreemaId,
        config::{DirectoryServerBaseUrl, Flavor, WorkContext},
        keys::{ClientKey, PublicKey},
    },
    crypto::digest::Mac as _,
    https::{HttpsHeadersBuilder, HttpsMethod, HttpsRequest, HttpsResponse, HttpsResult},
    model::contact::ContactInit,
    protobuf::{self, d2d_sync::contact as protobuf_contact},
    utils::{serde::base64, time::utc_now_ms},
};

#[derive(Debug, Serialize)]
struct WorkCredentials<'creds> {
    #[serde(rename = "licenseUsername")]
    username: &'creds str,

    #[serde(rename = "licensePassword")]
    password: &'creds str,
}
impl<'creds> From<&'creds crate::common::config::WorkCredentials> for WorkCredentials<'creds> {
    fn from(credentials: &'creds crate::common::config::WorkCredentials) -> Self {
        Self {
            username: &credentials.username,
            password: &credentials.password,
        }
    }
}

#[inline]
fn https_headers(mode: &Flavor) -> HttpsHeadersBuilder {
    match mode {
        Flavor::Consumer => HttpsHeadersBuilder::default(),
        Flavor::Work(context) => https_headers_with_authentication(context),
    }
}

fn handle_status<TStatusFn: FnOnce(u16) -> Option<HttpsEndpointError>>(
    result: HttpsResult,
    unexpected_status_map_fn: TStatusFn,
) -> Result<HttpsResponse, HttpsEndpointError> {
    let response = result?;
    match response.status {
        200 | 204 => Ok(response),
        401 => Err(HttpsEndpointError::InvalidCredentials),
        429 => Err(HttpsEndpointError::RateLimitExceeded),
        status => {
            Err(unexpected_status_map_fn(status).unwrap_or(HttpsEndpointError::UnexpectedStatus(status)))
        },
    }
}

#[derive(Deserialize)]
#[serde(untagged)]
enum AwkwardResponse {
    Error {
        #[serde(rename = "success")]
        success: bool,
        #[serde(rename = "error")]
        error: String,
    },
    Success {
        #[serde(rename = "success")]
        success: bool,
    },
}

fn handle_status_and_awkward_response<TStatusFn: FnOnce(u16) -> Option<HttpsEndpointError>>(
    result: HttpsResult,
    unexpected_status_map_fn: TStatusFn,
) -> Result<HttpsResponse, HttpsEndpointError> {
    let response = handle_status(result, unexpected_status_map_fn)?;

    // Handle the super-awkward response with the `success` field
    if response.status != 200 {
        return Err(HttpsEndpointError::UnexpectedStatus(response.status));
    }
    match serde_json::from_slice::<AwkwardResponse>(&response.body)? {
        AwkwardResponse::Error {
            success: _success,
            error,
        } => Err(HttpsEndpointError::CustomPossiblyLocalizedError(error)),
        AwkwardResponse::Success { success } => {
            if success {
                Ok(response)
            } else {
                Err(HttpsEndpointError::CustomPossiblyLocalizedError(
                    "Server is totally stoned".to_owned(),
                ))
            }
        },
    }
}

#[derive(Deserialize)]
struct AuthenticationChallenge {
    #[serde(rename = "tokenRespKeyPub", with = "base64::fixed_length")]
    public_key: [u8; PublicKey::LENGTH],

    #[serde(rename = "token", with = "base64::variable_length")]
    challenge: Vec<u8>,
}

#[derive(Serialize)]
pub(crate) struct AuthenticationChallengeResponse {
    #[serde(rename = "token", with = "base64::variable_length")]
    challenge: Vec<u8>,

    #[serde(rename = "response", with = "base64::fixed_length")]
    response: [u8; PublicKey::LENGTH],
}

/// Process the result and solve the authentication challenge.
pub(crate) fn handle_authentication_challenge(
    client_key: &ClientKey,
    result: HttpsResult,
) -> Result<AuthenticationChallengeResponse, HttpsEndpointError> {
    let response = handle_status(result, |_| None)?;
    let challenge: AuthenticationChallenge = serde_json::from_slice(&response.body)?;
    let response = AuthenticationChallengeResponse {
        challenge: challenge.challenge.clone(),
        response: client_key
            .derive_directory_authentication_key(&PublicKey::from(challenge.public_key))
            .0
            .chain_update(&challenge.challenge)
            .finalize()
            .into_bytes()
            .into(),
    };
    Ok(response)
}

#[derive(Serialize)]
struct IdentitiesRequest<'request> {
    identities: &'request [ThreemaId],
}

/// Request identity properties tied to a set of identities.
pub(crate) fn request_identities(
    client_info: &ClientInfo,
    directory_server_url: &DirectoryServerBaseUrl,
    mode: &Flavor,
    identities: &[ThreemaId],
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: directory_server_url.request_identities_path(),
        method: HttpsMethod::Post,
        headers: https_headers(mode).accept("application/json").build(client_info),
        body: serde_json::to_vec(&IdentitiesRequest { identities })
            .expect("Failed to create directory identities request body"),
    }
}

#[derive(Deserialize_repr)]
#[repr(u8)]
enum IdentityType {
    Regular = 0,
    Work = 1,
}
impl From<IdentityType> for protobuf_contact::IdentityType {
    fn from(r#type: IdentityType) -> Self {
        match r#type {
            IdentityType::Regular => protobuf_contact::IdentityType::Regular,
            IdentityType::Work => protobuf_contact::IdentityType::Work,
        }
    }
}

#[derive(Deserialize_repr)]
#[repr(u8)]
enum ActivityState {
    Active = 0,
    Inactive = 1,
}
impl Default for ActivityState {
    fn default() -> Self {
        Self::Active
    }
}
impl From<ActivityState> for protobuf_contact::ActivityState {
    fn from(state: ActivityState) -> Self {
        match state {
            ActivityState::Active => protobuf_contact::ActivityState::Active,
            ActivityState::Inactive => protobuf_contact::ActivityState::Inactive,
        }
    }
}

#[derive(Deserialize)]
struct ValidIdentity {
    #[serde(rename = "identity")]
    identity: ThreemaId,

    #[serde(rename = "type")]
    identity_type: IdentityType,

    #[serde(rename = "publicKey", with = "base64::fixed_length")]
    public_key: [u8; PublicKey::LENGTH],

    // Note: OnPrem directory omits this parameter for some reason, so we need a default
    #[serde(default, rename = "state")]
    activity_state: ActivityState,

    #[serde(rename = "featureMask")]
    feature_mask: u64,
}
impl From<ValidIdentity> for ContactInit {
    fn from(entry: ValidIdentity) -> Self {
        ContactInit {
            identity: entry.identity,
            public_key: PublicKey::from(entry.public_key),
            created_at: utc_now_ms(),
            first_name: None,
            last_name: None,
            nickname: None,
            verification_level: protobuf_contact::VerificationLevel::Unverified,
            work_verification_level: protobuf_contact::WorkVerificationLevel::None,
            identity_type: entry.identity_type.into(),
            acquaintance_level: protobuf_contact::AcquaintanceLevel::GroupOrDeleted,
            activity_state: entry.activity_state.into(),
            feature_mask: FeatureMask(entry.feature_mask),
            sync_state: protobuf_contact::SyncState::Initial,
            read_receipt_policy_override: None,
            typing_indicator_policy_override: None,
            notification_trigger_policy_override: None,
            notification_sound_policy_override: None,
            conversation_category: protobuf::d2d_sync::ConversationCategory::Default,
            conversation_visibility: protobuf::d2d_sync::ConversationVisibility::Normal,
        }
    }
}

#[derive(Deserialize)]
struct IdentitiesResponse {
    #[serde(rename = "identities")]
    identities: Vec<ValidIdentity>,
}

/// Process the identities result and map it to all valid identities.
///
/// IMPORTANT: Identities that do not exist or have already been revoked will not be included!
pub(crate) fn handle_identities_result(result: HttpsResult) -> Result<Vec<ContactInit>, HttpsEndpointError> {
    let response = handle_status(result, |_| None)?;
    let IdentitiesResponse { identities } = serde_json::from_slice(&response.body)?;
    Ok(identities.into_iter().map(ContactInit::from).collect())
}

#[derive(Serialize)]
struct CreateIdentityRequest {
    #[serde(rename = "publicKey", with = "base64::fixed_length")]
    public_key: [u8; PublicKey::LENGTH],
}

/// Request an authentication challenge to create an identity.
pub(crate) fn create_identity_authentication_request(
    client_info: &ClientInfo,
    directory_server_url: &DirectoryServerBaseUrl,
    mode: &Flavor,
    public_key: PublicKey,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: directory_server_url.create_identity_path(),
        method: HttpsMethod::Post,
        headers: https_headers(mode).accept("application/json").build(client_info),
        body: serde_json::to_vec(&CreateIdentityRequest {
            public_key: public_key.0.to_bytes(),
        })
        .expect("Failed to create directory identity creation challenge request body"),
    }
}

#[derive(Serialize)]
struct CreateIdentityAuthenticatedRequest {
    #[serde(flatten)]
    request: CreateIdentityRequest,

    #[serde(flatten)]
    authentication: AuthenticationChallengeResponse,
}

/// Create an identity.
pub(crate) fn create_identity_request(
    client_info: &ClientInfo,
    directory_server_url: &DirectoryServerBaseUrl,
    mode: &Flavor,
    authentication: AuthenticationChallengeResponse,
    public_key: PublicKey,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: directory_server_url.create_identity_path(),
        method: HttpsMethod::Post,
        headers: https_headers(mode).accept("application/json").build(client_info),
        body: serde_json::to_vec(&CreateIdentityAuthenticatedRequest {
            request: CreateIdentityRequest {
                public_key: public_key.0.to_bytes(),
            },
            authentication,
        })
        .expect("Failed to create directory identity creation request body"),
    }
}

/// Response for a newly created identity.
#[derive(Deserialize)]
pub(crate) struct CreateIdentityResponse {
    #[serde(rename = "identity")]
    pub(crate) identity: ThreemaId,

    #[serde(rename = "serverGroup")]
    pub(crate) server_group: ChatServerGroup,
}

/// Process the result after attempting to create an identity.
pub(crate) fn handle_create_identity_result(
    result: HttpsResult,
) -> Result<CreateIdentityResponse, HttpsEndpointError> {
    let response = handle_status_and_awkward_response(result, |_| None)?;
    Ok(serde_json::from_slice(&response.body)?)
}

#[derive(Serialize)]
struct UpdateWorkPropertiesRequest<'body> {
    #[serde(flatten)]
    credentials: WorkCredentials<'body>,

    #[serde(rename = "identity")]
    identity: ThreemaId,

    #[serde(rename = "version")]
    version: &'body str,
}

/// Request an authentication challenge to update work properties.
///
/// Note: Technically, this is a part that the work directory should do but historically it is part of the
/// directory. So, we're reflecting that... for now.
pub(crate) fn update_work_properties_authentication_request(
    client_info: &ClientInfo,
    directory_server_url: &DirectoryServerBaseUrl,
    work_context: &WorkContext,
    identity: ThreemaId,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: directory_server_url.update_work_properties_path(),
        method: HttpsMethod::Post,
        headers: https_headers_with_authentication(work_context)
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&UpdateWorkPropertiesRequest {
            credentials: (&work_context.credentials).into(),
            identity,
            version: &client_info.to_semicolon_separated(),
        })
        .expect("Failed to create update work properties challenge request body"),
    }
}

#[derive(Serialize)]
struct UpdateWorkPropertiesAuthenticatedRequest<'body> {
    #[serde(flatten)]
    request: UpdateWorkPropertiesRequest<'body>,

    #[serde(flatten)]
    authentication: AuthenticationChallengeResponse,
}

/// Update work properties.
///
/// Note: Technically, this is a part that the work directory should do but historically it is part of the
/// directory. So, we're reflecting that... for now.
pub(crate) fn update_work_properties_request(
    client_info: &ClientInfo,
    directory_server_url: &DirectoryServerBaseUrl,
    work_context: &WorkContext,
    identity: ThreemaId,
    authentication: AuthenticationChallengeResponse,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: directory_server_url.update_work_properties_path(),
        method: HttpsMethod::Post,
        headers: https_headers_with_authentication(work_context)
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&UpdateWorkPropertiesAuthenticatedRequest {
            request: UpdateWorkPropertiesRequest {
                credentials: (&work_context.credentials).into(),
                identity,
                version: &client_info.to_semicolon_separated(),
            },
            authentication,
        })
        .expect("Failed to create update work properties request body"),
    }
}

/// Process the result after attempting to update work properties.
pub(crate) fn handle_update_work_properties_result(result: HttpsResult) -> Result<(), HttpsEndpointError> {
    let _ = handle_status_and_awkward_response(result, |_| None)?;
    Ok(())
}
