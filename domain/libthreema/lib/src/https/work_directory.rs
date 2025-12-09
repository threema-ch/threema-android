//! Work directory endpoint.
use serde::{Deserialize, Serialize};

use super::endpoint::{HttpsEndpointError, TIMEOUT, https_headers_with_authentication};
use crate::{
    common::{
        ClientInfo, ThreemaId,
        config::{WorkContext, WorkServerBaseUrl},
        keys::{ClientKey, PublicKey, RemoteSecret, RemoteSecretAuthenticationToken},
    },
    crypto::{blake2b, digest::Mac as _},
    https::{HttpsHeadersBuilder, HttpsMethod, HttpsRequest, HttpsResponse, HttpsResult},
    model::contact::{Contact, ContactUpdateError},
    protobuf::d2d_sync::contact as protobuf_contact,
    utils::serde::{base64, string},
};

#[derive(Debug, Serialize)]
struct WorkCredentials<'creds> {
    #[serde(rename = "username")]
    username: &'creds str,

    #[serde(rename = "password")]
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
fn https_headers(context: Option<&WorkContext>) -> HttpsHeadersBuilder {
    match context {
        None => HttpsHeadersBuilder::default(),
        Some(context) => https_headers_with_authentication(context),
    }
}

#[derive(Deserialize)]
#[serde(tag = "code")]
enum UnauthorizedDetails {
    #[serde(rename = "invalid-credentials")]
    InvalidCredentials,
    #[serde(rename = "challenge-expired")]
    ChallengeExpired,
    #[serde(rename = "invalid-challenge-response")]
    InvalidChallengeResponse,
    #[serde(other)]
    Unknown,
}

fn handle_status<TStatusFn: FnOnce(u16) -> Option<HttpsEndpointError>>(
    result: HttpsResult,
    unexpected_status_map_fn: TStatusFn,
) -> Result<HttpsResponse, HttpsEndpointError> {
    let response = result?;
    match response.status {
        200 | 204 => Ok(response),
        401 => match serde_json::from_slice::<UnauthorizedDetails>(&response.body) {
            Ok(UnauthorizedDetails::ChallengeExpired) => Err(HttpsEndpointError::ChallengeExpired),
            Ok(UnauthorizedDetails::InvalidChallengeResponse) => {
                Err(HttpsEndpointError::InvalidChallengeResponse)
            },
            Ok(UnauthorizedDetails::InvalidCredentials | UnauthorizedDetails::Unknown) | Err(_) => {
                Err(HttpsEndpointError::InvalidCredentials)
            },
        },
        429 => Err(HttpsEndpointError::RateLimitExceeded),
        status => {
            Err(unexpected_status_map_fn(status).unwrap_or(HttpsEndpointError::UnexpectedStatus(status)))
        },
    }
}

#[derive(Debug, Deserialize)]
struct AuthenticationChallenge {
    #[serde(rename = "challengePublicKey", with = "base64::fixed_length")]
    public_key: [u8; PublicKey::LENGTH],

    #[serde(rename = "challenge", with = "base64::variable_length")]
    challenge: Vec<u8>,
}

#[derive(Debug, Serialize)]
pub(crate) struct AuthenticationChallengeResponse {
    #[serde(rename = "challenge", with = "base64::variable_length")]
    challenge: Vec<u8>,

    #[serde(rename = "response", with = "base64::fixed_length")]
    response: [u8; blake2b::MAC_256_LENGTH],
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
            .derive_work_directory_authentication_key(&PublicKey::from(challenge.public_key))
            .0
            .chain_update(&challenge.challenge)
            .finalize()
            .into_bytes()
            .into(),
    };
    Ok(response)
}

#[derive(Debug, Serialize)]
struct WorkContactsRequest<'body> {
    #[serde(flatten)]
    credentials: WorkCredentials<'body>,

    #[serde(rename = "contacts")]
    identities: &'body [ThreemaId],
}

/// Request additonal properties associated to a set of contacts of the same Work
/// subscription.
pub(crate) fn request_contacts(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    context: &WorkContext,
    identities: &[ThreemaId],
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.request_contacts_path(),
        method: HttpsMethod::Post,
        headers: https_headers(Some(context))
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&WorkContactsRequest {
            credentials: (&context.credentials).into(),
            identities,
        })
        .expect("Failed to create work directory contacts request body"),
    }
}

/// Additional Work properties associated to a specific contact.
#[derive(Debug, Deserialize)]
pub(crate) struct WorkContact {
    #[serde(rename = "id")]
    pub(crate) identity: ThreemaId,

    #[serde(rename = "pk", with = "base64::fixed_length")]
    pub(crate) public_key: [u8; PublicKey::LENGTH],

    #[serde(rename = "first", deserialize_with = "string::empty_to_optional::deserialize")]
    pub(crate) first_name: Option<String>,

    #[serde(rename = "last", deserialize_with = "string::empty_to_optional::deserialize")]
    pub(crate) last_name: Option<String>,
}
impl WorkContact {
    pub(crate) fn update(self, contact: &mut Contact) -> Result<(), ContactUpdateError> {
        let Self {
            identity,
            public_key,
            first_name,
            last_name,
        } = self;

        // Ensure the identity and public key equal before updating
        if identity != contact.identity {
            return Err(ContactUpdateError::IdentityMismatch {
                expected: contact.identity,
                actual: identity,
            });
        }
        if &public_key != contact.public_key.0.as_bytes() {
            return Err(ContactUpdateError::PublicKeyMismatch);
        }

        // Bump verification levels (if needed)
        if contact.verification_level == protobuf_contact::VerificationLevel::Unverified {
            contact.verification_level = protobuf_contact::VerificationLevel::ServerVerified;
        }
        if contact.work_verification_level == protobuf_contact::WorkVerificationLevel::None {
            contact.work_verification_level =
                protobuf_contact::WorkVerificationLevel::WorkSubscriptionVerified;
        }

        // Update first and last name if provided
        if let Some(first_name) = first_name {
            contact.first_name = Some(first_name);
        }
        if let Some(last_name) = last_name {
            contact.last_name = Some(last_name);
        }

        // Done
        Ok(())
    }
}

/// Process the result and map it to a subset of the provided contacts that are part of the same
/// Work subscription with the associated additional Work properties.
pub(crate) fn handle_contacts_result(result: HttpsResult) -> Result<Vec<WorkContact>, HttpsEndpointError> {
    let response = handle_status(result, |_| None)?;
    let amendments: Vec<WorkContact> = serde_json::from_slice(&response.body)?;
    Ok(amendments)
}

#[derive(Debug, Serialize)]
struct WorkCreateRemoteSecretRequest<'body> {
    #[serde(rename = "identity")]
    identity: ThreemaId,

    #[serde(flatten)]
    credentials: WorkCredentials<'body>,

    #[serde(rename = "secret", with = "base64::fixed_length")]
    remote_secret: &'body [u8; RemoteSecret::LENGTH],
}

/// Request an authentication challenge to create a remote secret.
pub(crate) fn create_remote_secret_authentication_request(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    identity: ThreemaId,
    context: &WorkContext,
    remote_secret: &RemoteSecret,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.remote_secret_path(),
        method: HttpsMethod::Put,
        headers: https_headers(Some(context))
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&WorkCreateRemoteSecretRequest {
            identity,
            credentials: (&context.credentials).into(),
            remote_secret: &remote_secret.0,
        })
        .expect("Failed to create remote secret creation challenge request body"),
    }
}

#[derive(Debug, Serialize)]
struct WorkCreateRemoteSecretAuthenticatedRequest<'body> {
    #[serde(flatten)]
    request: WorkCreateRemoteSecretRequest<'body>,

    #[serde(flatten)]
    authentication: AuthenticationChallengeResponse,
}

/// Create a remote secret.
pub(crate) fn create_remote_secret_request(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    identity: ThreemaId,
    context: &WorkContext,
    authentication: AuthenticationChallengeResponse,
    remote_secret: &RemoteSecret,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.remote_secret_path(),
        method: HttpsMethod::Put,
        headers: https_headers(Some(context))
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&WorkCreateRemoteSecretAuthenticatedRequest {
            request: WorkCreateRemoteSecretRequest {
                identity,
                credentials: (&context.credentials).into(),
                remote_secret: &remote_secret.0,
            },
            authentication,
        })
        .expect("Failed to create remote secret creation request body"),
    }
}

#[derive(Debug, Deserialize)]
struct WorkCreateRemoteSecretResponse {
    #[serde(rename = "secretAuthenticationToken", with = "base64::fixed_length")]
    remote_secret_authentication_token: [u8; RemoteSecretAuthenticationToken::LENGTH],
}

/// Process the result after attempting to create a remote secret.
pub(crate) fn handle_create_remote_secret_result(
    result: HttpsResult,
) -> Result<RemoteSecretAuthenticationToken, HttpsEndpointError> {
    let response = handle_status(result, |_| None)?;
    let response: WorkCreateRemoteSecretResponse = serde_json::from_slice(&response.body)?;
    Ok(RemoteSecretAuthenticationToken(
        response.remote_secret_authentication_token,
    ))
}

#[derive(Debug, Serialize)]
struct WorkDeleteRemoteSecretRequest<'body> {
    #[serde(rename = "identity")]
    identity: ThreemaId,

    #[serde(flatten)]
    credentials: WorkCredentials<'body>,

    #[serde(rename = "secretAuthenticationToken", with = "base64::fixed_length")]
    remote_secret_authentication_token: &'body [u8; RemoteSecretAuthenticationToken::LENGTH],
}

/// Request an authentication challenge to remove a remote secret.
pub(crate) fn delete_remote_secret_authentication_request(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    identity: ThreemaId,
    context: &WorkContext,
    remote_secret_authentication_token: &RemoteSecretAuthenticationToken,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.remote_secret_path(),
        method: HttpsMethod::Delete,
        headers: https_headers(Some(context))
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&WorkDeleteRemoteSecretRequest {
            identity,
            credentials: (&context.credentials).into(),
            remote_secret_authentication_token: &remote_secret_authentication_token.0,
        })
        .expect("Failed to create remote secret deletion challenge request body"),
    }
}

#[derive(Debug, Serialize)]
struct WorkDeleteRemoteSecretAuthenticatedRequest<'body> {
    #[serde(flatten)]
    request: WorkDeleteRemoteSecretRequest<'body>,

    #[serde(flatten)]
    authentication: AuthenticationChallengeResponse,
}

/// Remove a remote secret.
pub(crate) fn delete_remote_secret_request(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    identity: ThreemaId,
    context: &WorkContext,
    authentication: AuthenticationChallengeResponse,
    remote_secret_authentication_token: &RemoteSecretAuthenticationToken,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.remote_secret_path(),
        method: HttpsMethod::Delete,
        headers: https_headers(Some(context))
            .accept("application/json")
            .build(client_info),
        body: serde_json::to_vec(&WorkDeleteRemoteSecretAuthenticatedRequest {
            request: WorkDeleteRemoteSecretRequest {
                identity,
                credentials: (&context.credentials).into(),
                remote_secret_authentication_token: &remote_secret_authentication_token.0,
            },
            authentication,
        })
        .expect("Failed to create remote secret deletion request body"),
    }
}

/// Process the result after attempting to remove a remote secret.
pub(crate) fn handle_delete_remote_secret_result(result: HttpsResult) -> Result<(), HttpsEndpointError> {
    let _ = handle_status(result, |_| None)?;
    Ok(())
}

#[derive(Debug, Serialize)]
struct WorkFetchRemoteSecretRequest<'body> {
    #[serde(rename = "secretAuthenticationToken", with = "base64::fixed_length")]
    remote_secret_authentication_token: &'body [u8; RemoteSecretAuthenticationToken::LENGTH],
}

/// Retrieve a remote secret.
pub(crate) fn request_remote_secret(
    client_info: &ClientInfo,
    work_server_url: &WorkServerBaseUrl,
    remote_secret_authentication_token: &RemoteSecretAuthenticationToken,
) -> HttpsRequest {
    HttpsRequest {
        timeout: TIMEOUT,
        url: work_server_url.remote_secret_path(),
        method: HttpsMethod::Post,
        headers: https_headers(None).accept("application/json").build(client_info),
        body: serde_json::to_vec(&WorkFetchRemoteSecretRequest {
            remote_secret_authentication_token: &remote_secret_authentication_token.0,
        })
        .expect("Failed to create remote secret request body"),
    }
}

#[derive(Debug, Deserialize)]
pub(crate) struct WorkFetchRemoteSecretResponse {
    #[serde(rename = "secret", with = "base64::fixed_length")]
    pub(crate) remote_secret: [u8; RemoteSecret::LENGTH],

    #[serde(rename = "checkIntervalS")]
    pub(crate) check_interval_s: u32,

    #[serde(rename = "nMissedChecksMax")]
    pub(crate) n_missed_checks_max: u16,
}

/// Process the result after attempting to retrieve a remote secret.
pub(crate) fn handle_remote_secret_result(
    result: HttpsResult,
) -> Result<WorkFetchRemoteSecretResponse, HttpsEndpointError> {
    let response = handle_status(result, |status| match status {
        403 => Some(HttpsEndpointError::Forbidden),
        404 => Some(HttpsEndpointError::NotFound),
        _ => None,
    })?;
    let remote_secret_response: WorkFetchRemoteSecretResponse = serde_json::from_slice(&response.body)?;
    Ok(remote_secret_response)
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use data_encoding::HEXLOWER;
    use rstest::rstest;
    use serde_json::json;

    use super::*;
    use crate::{
        common::config::{Config, WorkCredentials, WorkFlavor},
        https::HttpsResponse,
    };

    fn work_server_url() -> WorkServerBaseUrl {
        Config::testing().work_server_url
    }

    fn work_context() -> WorkContext {
        WorkContext {
            credentials: WorkCredentials {
                username: "klo".to_owned(),
                password: "bürste".to_owned(),
            },
            flavor: WorkFlavor::Work,
        }
    }

    #[test]
    fn common_status_challenge_expired() {
        assert_matches!(
            handle_status(
                Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "challenge-expired"}"#.to_vec(),
                }),
                |_| None
            ),
            Err(HttpsEndpointError::ChallengeExpired)
        );
    }

    #[test]
    fn common_status_invalid_challenge_response() {
        assert_matches!(
            handle_status(
                Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "invalid-challenge-response"}"#.to_vec(),
                }),
                |_| None
            ),
            Err(HttpsEndpointError::InvalidChallengeResponse)
        );
    }

    #[rstest]
    #[case(br#"{"code": "invalid-credentials"}"#)]
    #[case(br#"{"code": "don't-like-you"}"#)]
    #[case(b"roflcopter")]
    fn common_status_invalid_credentials(#[case] body: &'static [u8]) {
        assert_matches!(
            handle_status(
                Ok(HttpsResponse {
                    status: 401,
                    body: body.to_vec(),
                }),
                |_| None
            ),
            Err(HttpsEndpointError::InvalidCredentials)
        );
    }

    #[test]
    fn common_status_rate_limit_exceeded() {
        assert_matches!(
            handle_status(
                Ok(HttpsResponse {
                    status: 429,
                    body: vec![],
                }),
                |_| None
            ),
            Err(HttpsEndpointError::RateLimitExceeded)
        );
    }

    #[test]
    fn common_status_unexpected_custom_map() {
        assert_matches!(
            handle_status(
                Ok(HttpsResponse {
                    status: 429,
                    body: vec![],
                }),
                |_| None
            ),
            Err(HttpsEndpointError::RateLimitExceeded)
        );
    }

    #[rstest]
    fn common_status_unexpected(#[values(0, 403, 404, 1234)] expected_status: u16) {
        assert_matches!(
            handle_status(Ok(HttpsResponse { status: expected_status, body: vec![] }), |_| None),
            Err(HttpsEndpointError::UnexpectedStatus(actual_status)) => {
                assert_eq!(actual_status, expected_status);
            }
        );
    }

    #[rstest]
    #[case(0, || HttpsEndpointError::CustomPossiblyLocalizedError("why not".to_owned()))]
    #[case(403, || HttpsEndpointError::Forbidden)]
    #[case(404, || HttpsEndpointError::NotFound)]
    fn common_status_unexpected_mapped<TErrorFn: Fn() -> HttpsEndpointError>(
        #[case] status: u16,
        #[case] error_fn: TErrorFn,
    ) {
        assert_eq!(
            handle_status(Ok(HttpsResponse { status, body: vec![] }), |unexpected_status| {
                if unexpected_status == status {
                    Some(error_fn())
                } else {
                    None
                }
            })
            .unwrap_err()
            .to_string(),
            error_fn().to_string(),
        );
    }

    #[rstest]
    #[case(200, b"Sure, dude")]
    #[case(200, br#"{"code": "invalid-credentials"}"#)]
    #[case(204, b"")]
    fn common_status_valid(#[case] status: u16, #[case] body: &'static [u8]) -> anyhow::Result<()> {
        let _ = handle_status(
            Ok(HttpsResponse {
                status,
                body: body.to_vec(),
            }),
            |_| None,
        )?;
        Ok(())
    }

    #[test]
    fn authentication_challenge_valid() -> anyhow::Result<()> {
        let response = handle_authentication_challenge(
            &ClientKey::from([0_u8; ClientKey::LENGTH]),
            Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "challenge": "bWVvdw==",
                }))?,
            }),
        )?;
        assert_eq!(response.challenge, b"meow".to_vec());
        assert_eq!(
            response.response.to_vec(),
            HEXLOWER
                .decode(b"56266bebac77186b46982853067a9a405f28163e5d436e9353388f4f6f59e394")
                .unwrap()
        );
        Ok(())
    }

    #[test]
    fn create_remote_secret_challenge_request() -> anyhow::Result<()> {
        let request = create_remote_secret_authentication_request(
            &ClientInfo::Libthreema,
            &work_server_url(),
            ThreemaId::predefined(*b"TESTTEST"),
            &work_context(),
            &RemoteSecret([1_u8; 32]),
        );
        assert_eq!(
            request.url,
            "https://ds-apip-work.example.threema.ch/api-client/v1/remote-secret"
        );
        assert_eq!(request.method, HttpsMethod::Put);
        let body: serde_json::Value = serde_json::from_slice(&request.body)?;
        assert_eq!(body["identity"], "TESTTEST");
        assert_eq!(body["username"], "klo");
        assert_eq!(body["password"], "bürste");
        assert_eq!(body["secret"], "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        Ok(())
    }

    #[test]
    fn create_remote_secret_request_valid() -> anyhow::Result<()> {
        let request = create_remote_secret_request(
            &ClientInfo::Libthreema,
            &work_server_url(),
            ThreemaId::predefined(*b"TESTTEST"),
            &work_context(),
            AuthenticationChallengeResponse {
                challenge: b"kekse".to_vec(),
                response: [0_u8; 32],
            },
            &RemoteSecret([1_u8; 32]),
        );
        assert_eq!(
            request.url,
            "https://ds-apip-work.example.threema.ch/api-client/v1/remote-secret"
        );
        assert_eq!(request.method, HttpsMethod::Put);
        let body: serde_json::Value = serde_json::from_slice(&request.body)?;
        assert_eq!(body["identity"], "TESTTEST");
        assert_eq!(body["username"], "klo");
        assert_eq!(body["password"], "bürste");
        assert_eq!(body["challenge"], "a2Vrc2U=");
        assert_eq!(body["response"], "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assert_eq!(body["secret"], "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        Ok(())
    }

    #[test]
    fn create_remote_secret_response_valid() -> anyhow::Result<()> {
        let response = handle_create_remote_secret_result(Ok(HttpsResponse {
            status: 200,
            body: serde_json::to_vec(&json!({
                "secretAuthenticationToken": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
            }))?,
        }))?;
        assert_eq!(response.0, [2_u8; 32]);
        Ok(())
    }

    #[test]
    fn delete_remote_secret_authentication_request_valid() -> anyhow::Result<()> {
        let request = delete_remote_secret_authentication_request(
            &ClientInfo::Libthreema,
            &work_server_url(),
            ThreemaId::predefined(*b"TESTTEST"),
            &work_context(),
            &RemoteSecretAuthenticationToken([2_u8; 32]),
        );
        assert_eq!(
            request.url,
            "https://ds-apip-work.example.threema.ch/api-client/v1/remote-secret"
        );
        assert_eq!(request.method, HttpsMethod::Delete);
        let body: serde_json::Value = serde_json::from_slice(&request.body)?;
        assert_eq!(body["identity"], "TESTTEST");
        assert_eq!(body["username"], "klo");
        assert_eq!(body["password"], "bürste");
        assert_eq!(
            body["secretAuthenticationToken"],
            "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        );
        Ok(())
    }

    #[test]
    fn delete_remote_secret_request_valid() -> anyhow::Result<()> {
        let request = delete_remote_secret_request(
            &ClientInfo::Libthreema,
            &work_server_url(),
            ThreemaId::predefined(*b"TESTTEST"),
            &work_context(),
            AuthenticationChallengeResponse {
                challenge: b"kekse".to_vec(),
                response: [0_u8; 32],
            },
            &RemoteSecretAuthenticationToken([2_u8; 32]),
        );
        assert_eq!(
            request.url,
            "https://ds-apip-work.example.threema.ch/api-client/v1/remote-secret"
        );
        assert_eq!(request.method, HttpsMethod::Delete);
        let body: serde_json::Value = serde_json::from_slice(&request.body)?;
        assert_eq!(body["identity"], "TESTTEST");
        assert_eq!(body["username"], "klo");
        assert_eq!(body["password"], "bürste");
        assert_eq!(body["challenge"], "a2Vrc2U=");
        assert_eq!(body["response"], "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        assert_eq!(
            body["secretAuthenticationToken"],
            "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
        );
        Ok(())
    }

    #[test]
    fn remote_secret_request() -> anyhow::Result<()> {
        let request = request_remote_secret(
            &ClientInfo::Libthreema,
            &work_server_url(),
            &RemoteSecretAuthenticationToken([2_u8; 32]),
        );
        assert_eq!(
            request.url,
            "https://ds-apip-work.example.threema.ch/api-client/v1/remote-secret"
        );
        assert_eq!(request.method, HttpsMethod::Post);
        let body: serde_json::Value = serde_json::from_slice(&request.body)?;
        assert_eq!(
            body["secretAuthenticationToken"],
            "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
        );
        Ok(())
    }

    #[test]
    fn remote_secret_response_valid() -> anyhow::Result<()> {
        let response = handle_remote_secret_result(Ok(HttpsResponse {
            status: 200,
            body: serde_json::to_vec(&json!({
                "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
                "checkIntervalS": 1_u32,
                "nMissedChecksMax": 0_u16,
            }))?,
        }))?;
        assert_eq!(response.remote_secret, [1_u8; 32]);
        assert_eq!(response.check_interval_s, 1);
        assert_eq!(response.n_missed_checks_max, 0);
        Ok(())
    }

    // Note: These tests cover some common excess JSON decoding cases as well. Other endpoint tests do not
    // need to be this elaborate.
    #[rstest]
    #[case(b"Not ok")]
    #[case(
        // Invalid `secret` base64
        br#"{
            "secret": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "checkIntervalS": 1,
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `secret` length
        br#"{
            "secret": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "checkIntervalS": 1,
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Missing `checkIntervalS`
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `checkIntervalS` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": "nein",
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `checkIntervalS` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": [0, 1, 2, 3],
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `checkIntervalS` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": 0.0,
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `checkIntervalS` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": -1,
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Invalid `checkIntervalS` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": 4294967296,
            "nMissedChecksMax": 1
        }"#
    )]
    #[case(
        // Missing `nMissedChecksMax`
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": 1
        }"#
    )]
    #[case(
        // Invalid `nMissedChecksMax` type
        br#"{
            "secret": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
            "checkIntervalS": 1,
            "nMissedChecksMax": 65536
        }"#
    )]
    fn remote_secret_response_invalid_content(#[case] body: &'static [u8]) {
        let response = handle_remote_secret_result(Ok(HttpsResponse {
            status: 200,
            body: body.to_vec(),
        }));
        assert_matches!(response, Err(HttpsEndpointError::DecodingFailed(_)));
    }
}
