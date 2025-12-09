//! Task structures to monitor an existing remote secret by initially fetching it and then monitoring its
//! availability according to the protocol.
use core::{mem, ops::RangeInclusive};

use const_format::formatcp;
use educe::Educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, info, warn};

use crate::{
    common::{
        ClientInfo, ThreemaId,
        config::WorkServerBaseUrl,
        keys::{
            RemoteSecret, RemoteSecretAuthenticationToken, RemoteSecretHash, RemoteSecretHashForIdentity,
        },
    },
    https::{
        HttpsRequest, HttpsResult,
        endpoint::HttpsEndpointError,
        work_directory::{self, WorkFetchRemoteSecretResponse},
    },
    utils::{
        debug::Name as _,
        time::{Duration, Instant},
    },
};

// Grace period for timeouts
const TIMEOUT_GRACE_PERIOD: Duration = Duration::from_secs(5);

// Timeout between failed attempts until explicit failure is triggered while storage is locked
const RETRY_INTERVAL_WHILE_LOCKED: Duration = Duration::from_secs(10);

// Number of failed attempts until explicit failure is triggered while storage is locked
const N_FAILED_ATTEMPTS_MAX_WHILE_LOCKED: u16 = 5;

// Valid refresh interval range: 10s up to 24h
const VALID_CHECK_INTERVAL_RANGE_S: RangeInclusive<u32> = 10..=86400;

/// Most recent cause for a remote secret monitoring timeout.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Serialize),
    serde(
        tag = "type",
        content = "details",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    ),
    tsify(into_wasm_abi)
)]
#[cfg_attr(test, derive(PartialEq))]
pub enum TimeoutCause {
    /// An unrecoverable network error occurred while communicating with a server.
    #[error("Network error: {0}")]
    NetworkError(String),

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(String),

    /// A rate limit of a server has been exceeded.
    #[error("Rate limit exceeded")]
    RateLimitExceeded,
}
impl From<HttpsEndpointError> for TimeoutCause {
    fn from(error: HttpsEndpointError) -> Self {
        match error {
            HttpsEndpointError::NetworkError(_) => Self::NetworkError(error.to_string()),
            HttpsEndpointError::RateLimitExceeded => Self::RateLimitExceeded,
            HttpsEndpointError::Forbidden
            | HttpsEndpointError::NotFound
            | HttpsEndpointError::InvalidCredentials
            | HttpsEndpointError::ChallengeExpired
            | HttpsEndpointError::InvalidChallengeResponse
            | HttpsEndpointError::UnexpectedStatus(_)
            | HttpsEndpointError::DecodingFailed(_)
            | HttpsEndpointError::CustomPossiblyLocalizedError(_) => Self::ServerError(error.to_string()),
        }
    }
}

/// An error occurred while monitoring the remote secret.
///
/// When encountering an error:
///
/// 1. Immediately lock access to the storage and purge any keys from memory.
/// 2. Notify the user accordingly with the option to manually retry and abort the protocol.
///
/// Note: Locking access is very platform dependent. A simple but sufficient solution could just restart the
/// app process with an intent to show the lock result after successful restart. The user should always be
/// allowed to retry.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Serialize),
    serde(
        tag = "type",
        content = "details",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    ),
    tsify(into_wasm_abi)
)]
#[cfg_attr(test, derive(PartialEq))]
pub enum RemoteSecretMonitorError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Invalid state for the requested operation.
    #[error("Invalid state: {0}")]
    InvalidState(&'static str),

    /// A server misbehaved in an operation considered infallible.
    #[error("Server error: {0}")]
    ServerError(String),

    /// A timeout occurred while initially fetching or refreshing the remote secret.
    #[error("Fetching the remote secret timed out, most recent cause: {0}")]
    Timeout(TimeoutCause),

    /// No remote secret found. It may have been removed by the user or the Work administrator.
    #[error("Remote secret could not be found")]
    NotFound,

    /// A remote secret was found but access is currently prohibited by the Work administrator.
    #[error("Access to remote secret prohibited by administrator")]
    Blocked,

    /// A remote secret was found but it does not match the one we were expecting.
    #[error("Remote secret differs from the one we expect")]
    Mismatch,
}

/// Verifier for a Remote Secret.
pub enum RemoteSecretVerifier {
    /// Verifier that ensures that the remote secret returned by the endpoint matches the expected one.
    RemoteSecretHash(RemoteSecretHash),

    /// Verifier that ensures that:
    ///
    /// - the remote secret returned by the endpoint matches the expected one, and
    /// - the user's identity is tied to that remote secret.
    ///
    /// Note: This verifier should only be used by the iOS client. It is needed when the database is protected
    /// by a Remote Secret and data is backed up by an iCloud backup. While the associated Remote Secret
    /// Authentication Token is stored as part of the iCloud backup, making it possible to retrieve the Remote
    /// Secret and decrypt the database, the associated identity is not. This verifier ensures that the
    /// correct identity is being used to prevent a user from restoring a database to a different identity
    /// (assuming the user doesn't patch its client).
    RemoteSecretHashForIdentity {
        /// The user's identity.
        user_identity: ThreemaId,

        /// Remote Secret Hash tied to an identity (RSHID).
        remote_secret_hash_for_identity: RemoteSecretHashForIdentity,
    },
}

/// Context for monitoring a remote secret.
pub struct RemoteSecretMonitorContext {
    /// Client info.
    pub client_info: ClientInfo,

    /// Work server URL from the configuration.
    ///
    /// Note: This may be a cached URL of the Work server since not all information required to construct the
    /// commonly used [`crate::common::config::Config`] structure is available when fetching the remote
    /// secret.
    pub work_server_url: WorkServerBaseUrl,

    /// Remote secret authentication token to request the remote secret.
    pub remote_secret_authentication_token: RemoteSecretAuthenticationToken,

    /// Remote secret verifier to ensure the remote secret is correct.
    pub remote_secret_verifier: RemoteSecretVerifier,
}

/// Instruction for monitoring a remote secret. See each variant's steps.
#[derive(DebugVariantNames, VariantNames)]
pub enum RemoteSecretMonitorInstruction {
    /// When processing this variant:
    ///
    /// 1. Run the HTTPS request as defined by [`HttpsRequest`] and let `response` be the result.
    /// 2. Provide `response` to the associated task as a [`HttpsResult`] and poll again.
    Request(HttpsRequest),

    /// Schedule a timer to poll again, soon.
    ///
    /// When processing this variant:
    ///
    /// 1. Schedule a monotonic timer to call [`RemoteSecretMonitorProtocol::poll`] again in `timeout`.
    /// 2. If `remote_secret` has been provided, unlock access to the storage.
    ///
    /// Note: The `remote_secret` will only be provided once during the lifetime of the protocol.
    Schedule {
        /// Exact duration to wait before polling again.
        timeout: Duration,

        /// Remote secret to unlock the storage with.
        remote_secret: Option<RemoteSecret>,
    },
}

/// Possible response to an [`RemoteSecretMonitorInstruction`].
#[derive(Name)]
pub struct RemoteSecretMonitorResponse {
    /// Result for the HTTPS request.
    pub result: HttpsResult,
}

#[derive(DebugVariantNames, VariantNames)]
enum StorageState {
    Locked,
    Unlocked {
        check_interval: Duration,
        n_failed_attempts_max: u16,
    },
}
impl StorageState {
    fn n_failed_attempts_max(&self) -> u16 {
        match self {
            StorageState::Locked => N_FAILED_ATTEMPTS_MAX_WHILE_LOCKED,
            StorageState::Unlocked {
                n_failed_attempts_max,
                ..
            } => *n_failed_attempts_max,
        }
    }
}

struct FetchState {
    storage_state: StorageState,
    n_failed_attempts: u16,
    scheduled_at: Instant,
    timeout: Duration,
}

struct VerifyState {
    storage_state: StorageState,
    n_failed_attempts: u16,
    scheduled_at: Instant,
    timeout: Duration,
    response: Option<RemoteSecretMonitorResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(RemoteSecretMonitorError),
    Fetch(FetchState),
    Verify(VerifyState),
}
impl State {
    fn poll_fetch(
        context: &RemoteSecretMonitorContext,
        state: FetchState,
    ) -> (Self, RemoteSecretMonitorInstruction) {
        // Check if the schedule was violated
        if state.scheduled_at.elapsed() > state.timeout {
            let time_delta = state.scheduled_at.elapsed().saturating_sub(state.timeout);
            warn!(?time_delta, "Remote secret fetch delayed");
        }

        // Request remote secret
        info!("Requesting remote secret");
        let request = work_directory::request_remote_secret(
            &context.client_info,
            &context.work_server_url,
            &context.remote_secret_authentication_token,
        );
        (
            Self::Verify(VerifyState {
                storage_state: state.storage_state,
                n_failed_attempts: state.n_failed_attempts,
                scheduled_at: Instant::now(),
                timeout: request.timeout.saturating_add(TIMEOUT_GRACE_PERIOD),
                response: None,
            }),
            RemoteSecretMonitorInstruction::Request(request),
        )
    }

    fn poll_verify(
        context: &RemoteSecretMonitorContext,
        state: VerifyState,
    ) -> Result<(Self, RemoteSecretMonitorInstruction), RemoteSecretMonitorError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(RemoteSecretMonitorError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                RemoteSecretMonitorResponse::NAME,
                State::VERIFY,
            )));
        };

        // Check if the result timeout was violated
        if state.scheduled_at.elapsed() > state.timeout {
            let time_delta = state.scheduled_at.elapsed().saturating_sub(state.timeout);
            warn!(?time_delta, "Remote secret result delayed");
        }

        // Handle the remote secret result
        match work_directory::handle_remote_secret_result(response.result) {
            Ok(WorkFetchRemoteSecretResponse {
                remote_secret,
                check_interval_s,
                n_missed_checks_max,
            }) => {
                let remote_secret = RemoteSecret(remote_secret);

                // Clamp the check interval to the valid range
                let check_interval = Duration::from_secs(
                    check_interval_s
                        .clamp(
                            *VALID_CHECK_INTERVAL_RANGE_S.start(),
                            *VALID_CHECK_INTERVAL_RANGE_S.end(),
                        )
                        .into(),
                );

                // Verify the remote secret is the one we expect
                let actual_remote_secret_hash = remote_secret.derive_hash();
                match &context.remote_secret_verifier {
                    RemoteSecretVerifier::RemoteSecretHash(expected_remote_secret_hash) => {
                        if actual_remote_secret_hash != *expected_remote_secret_hash {
                            warn!(
                                expected = ?expected_remote_secret_hash,
                                actual = ?actual_remote_secret_hash,
                                "Remote secret mismatch"
                            );
                            return Err(RemoteSecretMonitorError::Mismatch);
                        }
                    },
                    RemoteSecretVerifier::RemoteSecretHashForIdentity {
                        user_identity,
                        remote_secret_hash_for_identity: expected_remote_secret_hash_for_identity,
                    } => {
                        let actual_remote_secret_hash_for_identity =
                            actual_remote_secret_hash.derive_for_identity(*user_identity);
                        if actual_remote_secret_hash_for_identity != *expected_remote_secret_hash_for_identity
                        {
                            warn!(
                                expected = ?expected_remote_secret_hash_for_identity,
                                actual = ?actual_remote_secret_hash_for_identity,
                                "Remote secret mismatch"
                            );
                            return Err(RemoteSecretMonitorError::Mismatch);
                        }
                    },
                }

                // Schedule another check and hand out the remote secret, if needed
                info!(refresh_in = ?check_interval, "Fetching remote secret successful");
                Ok((
                    Self::Fetch(FetchState {
                        storage_state: StorageState::Unlocked {
                            check_interval,
                            n_failed_attempts_max: n_missed_checks_max,
                        },
                        n_failed_attempts: 0,
                        scheduled_at: Instant::now(),
                        timeout: check_interval.saturating_add(TIMEOUT_GRACE_PERIOD),
                    }),
                    RemoteSecretMonitorInstruction::Schedule {
                        timeout: check_interval,
                        remote_secret: if matches!(state.storage_state, StorageState::Locked) {
                            Some(remote_secret)
                        } else {
                            None
                        },
                    },
                ))
            },

            Err(HttpsEndpointError::Forbidden) => {
                info!("Access to remote secret blocked");
                Err(RemoteSecretMonitorError::Blocked)
            },

            Err(HttpsEndpointError::NotFound) => {
                info!("Remote secret does not exist");
                Err(RemoteSecretMonitorError::NotFound)
            },

            Err(error) => {
                // Check if we can still make another check based on maximum amount of tries
                if state.n_failed_attempts >= state.storage_state.n_failed_attempts_max() {
                    info!("Maximum number of failed remote secret fetch attempts exceeded");
                    return Err(RemoteSecretMonitorError::Timeout(TimeoutCause::from(error)));
                }

                // Schedule another attempt
                Ok(match state.storage_state {
                    storage_state @ StorageState::Locked => {
                        // The storage was never unlocked, so we have to work with constant timers and failure
                        // counters
                        info!(
                            cause = ?error,
                            retry_in = ?RETRY_INTERVAL_WHILE_LOCKED,
                            "Fetching remote secret failed",
                        );
                        (
                            Self::Fetch(FetchState {
                                storage_state,
                                n_failed_attempts: state.n_failed_attempts.saturating_add(1),
                                scheduled_at: Instant::now(),
                                timeout: RETRY_INTERVAL_WHILE_LOCKED.saturating_add(TIMEOUT_GRACE_PERIOD),
                            }),
                            RemoteSecretMonitorInstruction::Schedule {
                                timeout: RETRY_INTERVAL_WHILE_LOCKED,
                                remote_secret: None,
                            },
                        )
                    },

                    storage_state @ StorageState::Unlocked { check_interval, .. } => {
                        // The storage was unlocked before, so we maintain the settings from the previous
                        // fetch
                        info!(cause = ?error, retry_in = ?check_interval, "Fetching remote secret failed");
                        (
                            Self::Fetch(FetchState {
                                storage_state,
                                n_failed_attempts: state.n_failed_attempts.saturating_add(1),
                                scheduled_at: Instant::now(),
                                timeout: check_interval.saturating_add(TIMEOUT_GRACE_PERIOD),
                            }),
                            RemoteSecretMonitorInstruction::Schedule {
                                timeout: check_interval,
                                remote_secret: None,
                            },
                        )
                    },
                })
            },
        }
    }
}

/// The Remote Secret Monitoring Protocol state machine.
///
/// The following flow must be initiated when starting the app and looped while the remote secret feature is
/// active and a remote secret has been created:
///
/// 1. Run [`RemoteSecretMonitorProtocol::new`].
/// 2. Run the following steps in a loop:
///    1. Run [`RemoteSecretMonitorProtocol::poll`] and handle the result according to the description of
///       [`RemoteSecretMonitorInstruction`] and [`RemoteSecretMonitorError`].
///
/// The [`RemoteSecret`] will be provided exactly once for the lifetime of the protocol instance at which
/// point the storage can be unlocked. Most [`RemoteSecretMonitorError`]s require that the storage is being
/// locked and keys purged from memory immediately.
///
/// The concrete usage of the provided [`RemoteSecret`] is platform dependent. Its intention is to provide an
/// additional protection layer for scenarios when a device is lost. The [`RemoteSecret`] can then be blocked
/// or removed by the administrator, so that access to local data protected by it is no longer possible. This
/// assumes that an attacker was unable to extract keys from memory or launch the app and fetch the
/// [`RemoteSecret`] before it has been blocked or removed.
///
/// Note for choosing parameters: The maximum timeout after an initial fetch can be calculated as follows:
/// `check_interval_s` * `n_missed_checks_max` + `endpoint::TIMEOUT`
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct RemoteSecretMonitorProtocol {
    #[educe(Debug(ignore))]
    context: RemoteSecretMonitorContext,
    state: State,
}
impl RemoteSecretMonitorProtocol {
    /// Create a new protocol for unlocking the storage from the remote secret and continuously monitoring it.
    #[must_use]
    #[tracing::instrument(skip_all)]
    pub fn new(context: RemoteSecretMonitorContext) -> Self {
        debug!("Creating remote secret monitor protocol");

        // Create initial state
        Self {
            context,
            state: State::Fetch(FetchState {
                storage_state: StorageState::Locked,
                n_failed_attempts: 0,
                scheduled_at: Instant::now(),
                timeout: TIMEOUT_GRACE_PERIOD,
            }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`RemoteSecretMonitorError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<RemoteSecretMonitorInstruction, RemoteSecretMonitorError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(RemoteSecretMonitorError::InvalidState(formatcp!(
                "{} in a transitional state",
                RemoteSecretMonitorProtocol::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Fetch(state) => Ok(State::poll_fetch(&self.context, state)),
            State::Verify(state) => State::poll_verify(&self.context, state),
        };
        match result {
            Ok((state, instruction)) => {
                self.state = state;
                debug!(state = ?self.state, "Changed state");
                Ok(instruction)
            },
            Err(error) => {
                self.state = State::Error(error.clone());
                debug!(state = ?self.state, "Changed state to error");
                Err(error)
            },
        }
    }

    /// Possible results after handling a [`RemoteSecretMonitorInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`RemoteSecretMonitorError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(
        &mut self,
        response: RemoteSecretMonitorResponse,
    ) -> Result<(), RemoteSecretMonitorError> {
        match &mut self.state {
            State::Verify(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            _ => Err(RemoteSecretMonitorError::InvalidState(formatcp!(
                "Must be in '{}' state",
                State::VERIFY,
            ))),
        }
    }
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use rstest::rstest;
    use rstest_reuse::{apply, template};
    use serde_json::json;

    use super::*;
    use crate::{common::config::Config, https::HttpsResponse};

    #[derive(Clone, Copy)]
    enum Verifier {
        Hash,
        HashForId,
    }

    fn monitor_context(verifier: Verifier) -> RemoteSecretMonitorContext {
        let remote_secret = RemoteSecret([2_u8; 32]);
        RemoteSecretMonitorContext {
            client_info: ClientInfo::Libthreema,
            remote_secret_authentication_token: RemoteSecretAuthenticationToken([1_u8; 32]),
            remote_secret_verifier: match verifier {
                Verifier::Hash => RemoteSecretVerifier::RemoteSecretHash(remote_secret.derive_hash()),
                Verifier::HashForId => {
                    let user_identity = ThreemaId::predefined(*b"TESTTEST");
                    RemoteSecretVerifier::RemoteSecretHashForIdentity {
                        user_identity,
                        remote_secret_hash_for_identity: remote_secret
                            .derive_hash()
                            .derive_for_identity(user_identity),
                    }
                },
            },
            work_server_url: Config::testing().work_server_url,
        }
    }

    #[template]
    #[rstest]
    fn monitor_context_template(
        #[values(monitor_context(Verifier::Hash), monitor_context(Verifier::HashForId))]
        context: RemoteSecretMonitorContext,
    ) {
    }

    #[apply(monitor_context_template)]
    fn init_valid(context: RemoteSecretMonitorContext) {
        let state = FetchState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 0,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
        };

        let (state, instruction) = State::poll_fetch(&context, state);
        let state = assert_matches!(state, State::Verify(state) => state);
        let request =
            assert_matches!(instruction, RemoteSecretMonitorInstruction::Request(request) => request);
        assert_matches!(state.storage_state, StorageState::Locked);
        assert_eq!(state.n_failed_attempts, 0);
        assert!(state.scheduled_at <= Instant::now());
        assert_eq!(state.timeout, request.timeout + TIMEOUT_GRACE_PERIOD);
        assert!(state.response.is_none());
    }

    #[apply(monitor_context_template)]
    fn verify_without_response(context: RemoteSecretMonitorContext) {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 0,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: None,
        };

        let result = State::poll_verify(&context, state);
        assert_matches!(result, Err(RemoteSecretMonitorError::InvalidState(_)));
    }

    #[apply(monitor_context_template)]
    fn verify_mismatch(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 5,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secret": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "checkIntervalS": 0_u32,
                        "nMissedChecksMax": 0_u16,
                    }))?,
                }),
            }),
        };

        let result = State::poll_verify(&context, state);
        assert_matches!(result, Err(RemoteSecretMonitorError::Mismatch));
        Ok(())
    }

    #[test]
    fn verify_correct_remote_secret_but_different_identity() -> anyhow::Result<()> {
        let context = {
            let remote_secret = RemoteSecret([2_u8; 32]);
            RemoteSecretMonitorContext {
                client_info: ClientInfo::Libthreema,
                remote_secret_authentication_token: RemoteSecretAuthenticationToken([1_u8; 32]),
                remote_secret_verifier: RemoteSecretVerifier::RemoteSecretHashForIdentity {
                    user_identity: ThreemaId::predefined(*b"TESTTEST"),
                    remote_secret_hash_for_identity: remote_secret
                        .derive_hash()
                        .derive_for_identity(ThreemaId::predefined(*b"NOPENOPE")),
                },
                work_server_url: Config::testing().work_server_url,
            }
        };
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 5,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                        "checkIntervalS": 0_u32,
                        "nMissedChecksMax": 0_u16,
                    }))?,
                }),
            }),
        };

        let result = State::poll_verify(&context, state);
        assert_matches!(result, Err(RemoteSecretMonitorError::Mismatch));
        Ok(())
    }

    #[apply(monitor_context_template)]
    #[case(403, RemoteSecretMonitorError::Blocked)]
    #[case(404, RemoteSecretMonitorError::NotFound)]
    fn verify_immediate_lockout(
        context: RemoteSecretMonitorContext,
        #[case] status: u16,
        #[case] error: RemoteSecretMonitorError,
    ) {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 0,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse { status, body: vec![] }),
            }),
        };

        let result = State::poll_verify(&context, state);
        assert_eq!(result.unwrap_err(), error);
    }

    #[apply(monitor_context_template)]
    fn verify_storage_locked_valid(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 5,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                        "checkIntervalS": 0_u32,
                        "nMissedChecksMax": 0_u16,
                    }))?,
                }),
            }),
        };

        let (state, instruction) = State::poll_verify(&context, state)?;
        let state = assert_matches!(state, State::Fetch(state) => state);
        assert_matches!(
            state.storage_state,
            StorageState::Unlocked {check_interval, n_failed_attempts_max} => {
                assert_eq!(
                    check_interval,
                    // Note: `checkIntervalS: 0` will be clamped to this value
                    Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.start()).into()),
                );
                assert_eq!(n_failed_attempts_max, 0);
            }
        );
        assert_eq!(state.n_failed_attempts, 0);
        assert!(state.scheduled_at <= Instant::now());
        assert_eq!(
            state.timeout,
            Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.start()).into()) + TIMEOUT_GRACE_PERIOD
        );
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule {timeout, remote_secret} => {
            assert_eq!(
                timeout,
                Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.start()).into()),
            );
            assert_eq!(remote_secret.unwrap().0, [2_u8; 32]);
        });

        Ok(())
    }

    #[apply(monitor_context_template)]
    fn verify_storage_unlocked_valid(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        let state = VerifyState {
            storage_state: StorageState::Unlocked {
                check_interval: Duration::from_secs(42),
                n_failed_attempts_max: 0,
            },
            n_failed_attempts: 65535,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                        "checkIntervalS": 86401_u32,
                        "nMissedChecksMax": 65535_u16,
                    }))?,
                }),
            }),
        };

        let (state, instruction) = State::poll_verify(&context, state)?;
        let state = assert_matches!(state, State::Fetch(state) => state);
        assert_matches!(
            state.storage_state,
            StorageState::Unlocked {check_interval, n_failed_attempts_max} => {
                assert_eq!(
                    check_interval,
                    // Note: `checkIntervalS: 86401` will be clamped to this value
                    Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.end()).into()),
                );
                assert_eq!(n_failed_attempts_max, 65535);
            }
        );
        assert_eq!(state.n_failed_attempts, 0);
        assert!(state.scheduled_at <= Instant::now());
        assert_eq!(
            state.timeout,
            Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.end()).into()) + TIMEOUT_GRACE_PERIOD
        );
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule {timeout, remote_secret} => {
            assert_eq!(
                timeout,
                Duration::from_secs((*VALID_CHECK_INTERVAL_RANGE_S.end()).into()),
            );
            assert!(remote_secret.is_none());
        });

        Ok(())
    }

    #[apply(monitor_context_template)]
    fn verify_storage_locked_fail_timeout(context: RemoteSecretMonitorContext) {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 5,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "invalid-challenge-response"}"#.to_vec(),
                }),
            }),
        };

        let result = State::poll_verify(&context, state);
        assert_matches!(
            result,
            Err(RemoteSecretMonitorError::Timeout(TimeoutCause::ServerError(_)))
        );
    }

    #[apply(monitor_context_template)]
    fn verify_storage_unlocked_fail_timeout(context: RemoteSecretMonitorContext) {
        let state = VerifyState {
            storage_state: StorageState::Unlocked {
                check_interval: Duration::from_secs(42),
                n_failed_attempts_max: 0,
            },
            n_failed_attempts: 0,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "invalid-challenge-response"}"#.to_vec(),
                }),
            }),
        };

        let result = State::poll_verify(&context, state);
        assert_matches!(
            result,
            Err(RemoteSecretMonitorError::Timeout(TimeoutCause::ServerError(_)))
        );
    }

    #[apply(monitor_context_template)]
    fn verify_storage_locked_fail_retry(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        let state = VerifyState {
            storage_state: StorageState::Locked,
            n_failed_attempts: 4,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "invalid-challenge-response"}"#.to_vec(),
                }),
            }),
        };

        let (state, instruction) = State::poll_verify(&context, state)?;
        let state = assert_matches!(state, State::Fetch(state) => state);
        assert_matches!(state.storage_state, StorageState::Locked);
        assert_eq!(state.n_failed_attempts, 5);
        assert!(state.scheduled_at <= Instant::now());
        assert_eq!(state.timeout, RETRY_INTERVAL_WHILE_LOCKED + TIMEOUT_GRACE_PERIOD);
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule {timeout, remote_secret} => {
            assert_eq!(timeout, RETRY_INTERVAL_WHILE_LOCKED);
            assert!(remote_secret.is_none());
        });

        Ok(())
    }

    #[apply(monitor_context_template)]
    fn verify_storage_unlocked_fail_retry(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        let state = VerifyState {
            storage_state: StorageState::Unlocked {
                check_interval: Duration::from_secs(42),
                n_failed_attempts_max: 1,
            },
            n_failed_attempts: 0,
            scheduled_at: Instant::now(),
            timeout: Duration::from_secs(1),
            response: Some(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 401,
                    body: br#"{"code": "invalid-challenge-response"}"#.to_vec(),
                }),
            }),
        };

        let (state, instruction) = State::poll_verify(&context, state)?;
        let state = assert_matches!(state, State::Fetch(state) => state);
        assert_matches!(
            state.storage_state,
            StorageState::Unlocked {check_interval, n_failed_attempts_max} => {
                assert_eq!(check_interval, Duration::from_secs(42));
                assert_eq!(n_failed_attempts_max, 1);
            }
        );
        assert_eq!(state.n_failed_attempts, 1);
        assert!(state.scheduled_at <= Instant::now());
        assert_eq!(state.timeout, Duration::from_secs(42) + TIMEOUT_GRACE_PERIOD);
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule {timeout, remote_secret} => {
            assert_eq!(timeout, Duration::from_secs(42));
            assert!(remote_secret.is_none());
        });

        Ok(())
    }

    #[apply(monitor_context_template)]
    fn unexpected_response(context: RemoteSecretMonitorContext) {
        let mut protocol = RemoteSecretMonitorProtocol::new(context);
        let result = protocol.response(RemoteSecretMonitorResponse {
            result: Err(crate::https::HttpsError::Timeout("isso".to_owned())),
        });
        assert_matches!(result, Err(RemoteSecretMonitorError::InvalidState(_)));
    }

    #[apply(monitor_context_template)]
    fn unexpected_poll_after_error(context: RemoteSecretMonitorContext) {
        let mut protocol = RemoteSecretMonitorProtocol {
            context,
            state: State::Error(RemoteSecretMonitorError::Mismatch),
        };
        let result = protocol.poll();
        assert_matches!(result, Err(RemoteSecretMonitorError::Mismatch));
    }

    #[apply(monitor_context_template)]
    fn two_cycles_until_failure(context: RemoteSecretMonitorContext) -> anyhow::Result<()> {
        // Initial state
        let mut protocol = RemoteSecretMonitorProtocol::new(context);
        assert_matches!(&protocol.state, State::Fetch(_));

        // First fetch
        let instruction = protocol.poll()?;
        assert_matches!(&protocol.state, State::Verify(_));
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Request(_));

        // First response with expected content
        protocol.response(RemoteSecretMonitorResponse {
            result: Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                    "checkIntervalS": 11_u32,
                    "nMissedChecksMax": 1_u16,
                }))?,
            }),
        })?;
        let instruction = protocol.poll()?;
        assert_matches!(&protocol.state, State::Fetch(state) => {
            assert_eq!(state.n_failed_attempts, 0);
        });
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule { timeout, remote_secret } => {
            assert_eq!(timeout, Duration::from_secs(11));
            assert_eq!(remote_secret.unwrap().0, [2_u8; 32]);
        });

        // Second fetch
        let instruction = protocol.poll()?;
        assert_matches!(&protocol.state, State::Verify(_));
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Request(_));

        // Second response with expected content
        protocol.response(RemoteSecretMonitorResponse {
            result: Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "secret": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                    "checkIntervalS": 22_u32,
                    "nMissedChecksMax": 3_u16,
                }))?,
            }),
        })?;
        let instruction = protocol.poll()?;
        assert_matches!(&protocol.state, State::Fetch(state) => {
            assert_eq!(state.n_failed_attempts, 0);
        });
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Schedule { timeout, remote_secret } => {
            assert_eq!(timeout, Duration::from_secs(22));
            assert!(remote_secret.is_none());
        });

        // Subsequent fetches and responses triggering a retry 3 times
        for n_failed_attempts in 0..3_u16 {
            // Subsequent fetches
            let instruction = protocol.poll()?;
            assert_matches!(&protocol.state, State::Verify(_));
            assert_matches!(instruction, RemoteSecretMonitorInstruction::Request(_));

            // Subsequent responses triggering a retry
            protocol.response(RemoteSecretMonitorResponse {
                result: Ok(HttpsResponse {
                    status: 429,
                    body: vec![],
                }),
            })?;
            let instruction = protocol.poll()?;
            assert_matches!(&protocol.state, State::Fetch(state) => {
                assert_eq!(state.n_failed_attempts, n_failed_attempts + 1);
            });
            assert_matches!(
                instruction,
                RemoteSecretMonitorInstruction::Schedule { timeout, remote_secret } => {
                    assert_eq!(timeout, Duration::from_secs(22));
                    assert!(remote_secret.is_none());
                }
            );
        }

        // Final fetch
        let instruction = protocol.poll()?;
        assert_matches!(&protocol.state, State::Verify(_));
        assert_matches!(instruction, RemoteSecretMonitorInstruction::Request(_));

        // Final response triggering a timeout
        protocol.response(RemoteSecretMonitorResponse {
            result: Ok(HttpsResponse {
                status: 429,
                body: vec![],
            }),
        })?;
        let result = protocol.poll();
        assert_matches!(
            protocol.state,
            State::Error(RemoteSecretMonitorError::Timeout(TimeoutCause::RateLimitExceeded))
        );
        assert_matches!(
            result,
            Err(RemoteSecretMonitorError::Timeout(TimeoutCause::RateLimitExceeded))
        );

        Ok(())
    }
}
