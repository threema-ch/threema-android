//! Directory endpoint.
use serde::{Deserialize, Serialize};
use serde_repr::Deserialize_repr;

use super::{CspE2eEndpointError, TIMEOUT, https_headers_with_authentication};
use crate::{
    common::{FeatureMask, PublicKey, ThreemaId},
    csp_e2e::{Flavor, config::Config, contact::ContactInit},
    https::{HttpsHeaders, HttpsHeadersBuilder, HttpsMethod, HttpsRequest, HttpsResult},
    protobuf::{self, d2d_sync::contact as protobuf_contact},
    utils::{serde::base64, time::utc_now_ms},
};

#[inline]
fn https_headers(mode: &Flavor) -> HttpsHeadersBuilder {
    match mode {
        Flavor::Consumer => HttpsHeaders::builder(),
        Flavor::Work(context) => https_headers_with_authentication(context),
    }
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
struct IdentitiesRequest<'request> {
    identities: &'request [ThreemaId],
}

/// Request identity properties tied to a set of identities.
pub(crate) fn request_identities(config: &Config, mode: &Flavor, identities: &[ThreemaId]) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: config
            .directory_server_url
            .path(format_args!("identity/fetch_bulk")),
        method: HttpsMethod::POST,
        headers: https_headers(mode).accept("application/json").into(),
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
    #[serde(rename = "publicKey", with = "base64")]
    public_key: [u8; 32],
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

/// Process the result and map it to all valid identities.
///
/// IMPORTANT: Identities that do not exist or have already been revoked will not be included!
pub(crate) fn handle_identities_result(result: HttpsResult) -> Result<Vec<ContactInit>, CspE2eEndpointError> {
    let body = preprocess_result(result)?;
    let IdentitiesResponse { identities } = serde_json::from_slice(&body)?;
    Ok(identities.into_iter().map(ContactInit::from).collect())
}
