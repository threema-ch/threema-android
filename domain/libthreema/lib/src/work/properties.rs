//! Task structures to request work properties.
use core::mem;

use const_format::formatcp;
use educe::Educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use prost::Message as _;
use serde::Serialize;
use tracing::{debug, info};

use crate::{
    common::{
        ClientInfo, ThreemaId,
        config::{WorkContext, WorkServerBaseUrl},
        keys::ClientKey,
        task::TaskLoop,
    },
    https::{HttpsRequest, HttpsResult, endpoint::HttpsEndpointError, work_directory},
    protobuf,
    utils::debug::Name as _,
};

/// An error occurred while updating the work properties.
///
/// Note: Errors can occur when using the API incorrectly or when the remote server behaves incorrectly. None
/// of these errors are considered recoverable.
///
/// When encountering an error:
///
/// 1. Let `error` be the provided [`WorkPropertiesUpdateError`].
/// 2. If `error` is [`WorkPropertiesUpdateError::InvalidCredentials`], notify the user that the Work
///    credentials are invalid and request new ones before making a new attempt.
/// 3. Notify the user according to `error` with the option to manually retry and abort the task.
#[derive(Clone, Debug, thiserror::Error)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Error), uniffi(flat_error))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, Serialize),
    serde(
        tag = "type",
        content = "details",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    ),
    tsify(into_wasm_abi)
)]
pub enum WorkPropertiesUpdateError {
    /// Invalid parameter provided.
    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),

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
impl From<HttpsEndpointError> for WorkPropertiesUpdateError {
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

/// Context for updating the work properties.
pub struct WorkPropertiesUpdateContext {
    /// Client info.
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

/// The work availability status.
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(rename_all = "camelCase")
)]
pub struct WorkAvailabilityStatus {
    /// Availability status of the user.
    pub category: protobuf::d2d_sync::WorkAvailabilityStatusCategory,

    /// Optional custom status description as chosen by the contact. Must be ≤
    /// 256 bytes.
    ///
    /// Note: The description will be silently dropped if the category is _none_.
    pub description: Option<String>,
}
impl WorkAvailabilityStatus {
    /// Maximum byte length of the availability status description.
    const DESCRIPTION_LENGTH_MAX: usize = 256;
}
impl TryFrom<WorkAvailabilityStatus> for protobuf::d2d_sync::WorkAvailabilityStatus {
    type Error = WorkPropertiesUpdateError;

    fn try_from(mut availability_status: WorkAvailabilityStatus) -> Result<Self, Self::Error> {
        // Silently drop the description if category is _none_.
        if matches!(
            &availability_status.category,
            protobuf::d2d_sync::WorkAvailabilityStatusCategory::None
        ) {
            availability_status.description = None;
        }

        // Only accept descriptions within the declared length limit.
        if let Some(description) = &availability_status.description {
            let byte_length = description.clone().into_bytes().len();
            if byte_length > WorkAvailabilityStatus::DESCRIPTION_LENGTH_MAX {
                return Err(WorkPropertiesUpdateError::InvalidParameter(format!(
                    "Availability status description length exceeded. Used {byte_length} of {}",
                    WorkAvailabilityStatus::DESCRIPTION_LENGTH_MAX,
                )));
            }
        }

        // Map to protobuf.
        Ok(protobuf::d2d_sync::WorkAvailabilityStatus {
            category: availability_status.category.into(),
            description: availability_status.description.unwrap_or_default(),
        })
    }
}

/// The user's work properties to update.
///
/// IMPORTANT: All fields are optional and presence indicates a change to a
/// property. Lack of presence means that the property remains unchanged.
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(rename_all = "camelCase"),
    tsify(from_wasm_abi)
)]
pub struct WorkProperties {
    /// Availability status of the user.
    pub availability_status: Option<WorkAvailabilityStatus>,
}
impl TryFrom<WorkProperties> for protobuf::directory::WorkProperties {
    type Error = WorkPropertiesUpdateError;

    fn try_from(properties: WorkProperties) -> Result<Self, Self::Error> {
        Ok(protobuf::directory::WorkProperties {
            availability_status: match properties.availability_status {
                None => None,
                Some(availability_status) => Some(availability_status.try_into()?),
            },
        })
    }
}

/// 1. Run the HTTPS request as defined by [`HttpsRequest`] and let `response` be the result.
/// 2. Provide `response` to the associated task as a [`HttpsResult`] and poll again.
pub struct WorkPropertiesUpdateInstruction {
    /// The HTTPs request to be made.
    pub request: HttpsRequest,
}

/// Possible response to a [`WorkPropertiesUpdateInstruction`].
#[derive(Debug, Name)]
pub struct WorkPropertiesUpdateResponse {
    /// The HTTPs request to be made.
    pub result: HttpsResult,
}

/// Result of polling a [`WorkPropertiesUpdateTask`].
pub type WorkPropertiesUpdateLoop = TaskLoop<WorkPropertiesUpdateInstruction, ()>;

struct InitState {
    work_properties: WorkProperties,
}

struct ChallengeState {
    encoded_work_properties: Vec<u8>,
    response: Option<WorkPropertiesUpdateResponse>,
}

struct UpdateState {
    response: Option<WorkPropertiesUpdateResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(WorkPropertiesUpdateError),
    Init(InitState),
    Challenge(ChallengeState),
    Update(UpdateState),
    Done,
}
impl State {
    fn poll_init(
        context: &WorkPropertiesUpdateContext,
        state: InitState,
    ) -> Result<(Self, WorkPropertiesUpdateLoop), WorkPropertiesUpdateError> {
        debug!("Requesting challenge to update the work properties");
        let encoded_work_properties =
            protobuf::directory::WorkProperties::try_from(state.work_properties)?.encode_to_vec();
        let request = work_directory::update_work_properties_authentication_request(
            &context.client_info,
            &context.work_server_url,
            &context.work_context,
            context.user_identity,
            encoded_work_properties.clone(),
        );
        Ok((
            Self::Challenge(ChallengeState {
                encoded_work_properties,
                response: None,
            }),
            WorkPropertiesUpdateLoop::Instruction(WorkPropertiesUpdateInstruction { request }),
        ))
    }

    fn poll_challenge(
        context: &WorkPropertiesUpdateContext,
        state: ChallengeState,
    ) -> Result<(Self, WorkPropertiesUpdateLoop), WorkPropertiesUpdateError> {
        // Ensure the caller provided the response.
        let Some(response) = state.response else {
            return Err(WorkPropertiesUpdateError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                WorkPropertiesUpdateResponse::NAME,
                State::CHALLENGE,
            )));
        };

        // Handle the authentication challenge and provide the final request to update the work properties.
        let authentication =
            work_directory::handle_authentication_challenge(&context.client_key, response.result)?;
        info!("Updating the work properties");
        let request = work_directory::update_work_properties_request(
            &context.client_info,
            &context.work_server_url,
            &context.work_context,
            context.user_identity,
            authentication,
            state.encoded_work_properties,
        );
        Ok((
            Self::Update(UpdateState { response: None }),
            WorkPropertiesUpdateLoop::Instruction(WorkPropertiesUpdateInstruction { request }),
        ))
    }

    fn poll_update(
        state: UpdateState,
    ) -> Result<(Self, WorkPropertiesUpdateLoop), WorkPropertiesUpdateError> {
        // Ensure the caller provided the response.
        let Some(response) = state.response else {
            return Err(WorkPropertiesUpdateError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                WorkPropertiesUpdateResponse::NAME,
                State::UPDATE,
            )));
        };

        // Handle the result.
        work_directory::handle_update_work_properties_result(response.result)?;
        info!("Updated work properties");
        Ok((Self::Done, WorkPropertiesUpdateLoop::Done(())))
    }
}

/// Task for updating the work properties.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct WorkPropertiesUpdateTask {
    #[educe(Debug(ignore))]
    context: WorkPropertiesUpdateContext,
    state: State,
}
impl WorkPropertiesUpdateTask {
    /// Create a new task for updating the work properties.
    #[must_use]
    pub fn new(context: WorkPropertiesUpdateContext, properties: WorkProperties) -> Self {
        Self {
            context,
            state: State::Init(InitState {
                work_properties: properties,
            }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`WorkPropertiesUpdateError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<WorkPropertiesUpdateLoop, WorkPropertiesUpdateError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(WorkPropertiesUpdateError::InvalidState(formatcp!(
                "{} in a transitional state",
                WorkPropertiesUpdateTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => State::poll_init(&self.context, state),
            State::Challenge(state) => State::poll_challenge(&self.context, state),
            State::Update(state) => State::poll_update(state),
            State::Done => Err(WorkPropertiesUpdateError::InvalidState(formatcp!(
                "{} already done",
                WorkPropertiesUpdateTask::NAME
            ))),
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

    /// Possible results after handling a [`WorkPropertiesUpdateInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`WorkPropertiesUpdateError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(
        &mut self,
        response: WorkPropertiesUpdateResponse,
    ) -> Result<(), WorkPropertiesUpdateError> {
        match &mut self.state {
            State::Challenge(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            State::Update(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            _ => Err(WorkPropertiesUpdateError::InvalidState(formatcp!(
                "Must be in '{}' or '{}' state",
                State::CHALLENGE,
                State::UPDATE,
            ))),
        }
    }
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use derive_builder::Builder;
    use rstest::rstest;
    use serde_json::json;

    use super::*;
    use crate::{
        common::{
            ThreemaId,
            config::{Config, WorkContext, WorkCredentials, WorkFlavor},
            keys::ClientKey,
        },
        https::HttpsResponse,
    };

    fn context() -> WorkPropertiesUpdateContext {
        let config = Config::testing();
        WorkPropertiesUpdateContext {
            client_info: ClientInfo::Libthreema,
            work_server_url: config.work_server_url,
            work_context: WorkContext {
                credentials: WorkCredentials {
                    username: "nigel".to_owned(),
                    password: "nagel".to_owned(),
                },
                flavor: WorkFlavor::Work,
            },
            user_identity: ThreemaId::predefined(*b"TITATEST"),
            client_key: ClientKey::from([0_u8; ClientKey::LENGTH]),
        }
    }

    #[derive(Builder)]
    #[builder(pattern = "owned")]
    struct WorkPropertiesInit {
        #[builder(default = None)]
        availability_status: Option<WorkAvailabilityStatus>,
    }
    impl WorkPropertiesInitBuilder {
        fn with_default_availability_status(self) -> Self {
            self.with_availability_status(protobuf::d2d_sync::WorkAvailabilityStatusCategory::None, None)
        }

        fn with_availability_status(
            mut self,
            status: protobuf::d2d_sync::WorkAvailabilityStatusCategory,
            description: Option<String>,
        ) -> Self {
            self.availability_status = Some(Some(WorkAvailabilityStatus {
                category: status,
                description,
            }));
            self
        }
    }
    impl From<WorkPropertiesInit> for WorkProperties {
        fn from(value: WorkPropertiesInit) -> Self {
            WorkProperties {
                availability_status: value.availability_status,
            }
        }
    }

    #[test]
    fn init() -> anyhow::Result<()> {
        let context = context();
        let work_properties = WorkPropertiesInitBuilder::default()
            .with_default_availability_status()
            .build()?;
        let state = InitState {
            work_properties: work_properties.into(),
        };

        let (state, instruction) = State::poll_init(&context, state)?;
        let state = assert_matches!(state, State::Challenge(state) => state);
        assert!(state.response.is_none());
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Instruction(_));

        Ok(())
    }

    #[rstest]
    fn init_with_invalid_properties(
        #[values(
            protobuf::d2d_sync::WorkAvailabilityStatusCategory::Busy,
            protobuf::d2d_sync::WorkAvailabilityStatusCategory::Unavailable
        )]
        category: protobuf::d2d_sync::WorkAvailabilityStatusCategory,
    ) -> anyhow::Result<()> {
        let context = context();
        let work_properties = WorkPropertiesInitBuilder::default()
            .with_availability_status(category, Some("dreamer du".repeat(250)))
            .build()?;
        let state = InitState {
            work_properties: work_properties.into(),
        };

        let result = State::poll_init(&context, state);
        let message =
            assert_matches!(result, Err(WorkPropertiesUpdateError::InvalidParameter(message)) => message);
        assert_eq!(
            message,
            "Availability status description length exceeded. Used 2500 of 256".to_owned()
        );

        Ok(())
    }

    #[test]
    fn challenge_without_response() {
        let context = context();
        let state = ChallengeState {
            encoded_work_properties: vec![1_u8; 32],
            response: None,
        };

        let result = State::poll_challenge(&context, state);
        assert_matches!(result, Err(WorkPropertiesUpdateError::InvalidState(_)));
    }

    #[test]
    fn challenge_invalid() -> anyhow::Result<()> {
        let context = context();
        let state = ChallengeState {
            encoded_work_properties: vec![1_u8; 32],
            response: Some(WorkPropertiesUpdateResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "challenge": "bWVvdw==",
                    }))?,
                }),
            }),
        };

        let result = State::poll_challenge(&context, state);
        assert_matches!(result, Err(WorkPropertiesUpdateError::ServerError(_)));

        Ok(())
    }

    #[test]
    fn challenge_valid() -> anyhow::Result<()> {
        let context = context();
        let state = ChallengeState {
            encoded_work_properties: vec![1_u8; 32],
            response: Some(WorkPropertiesUpdateResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "challenge": "bWVvdw==",
                    }))?,
                }),
            }),
        };

        let (state, instruction) = State::poll_challenge(&context, state)?;
        let state = assert_matches!(state, State::Update(state) => state);
        assert!(state.response.is_none());
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Instruction(_));

        Ok(())
    }

    #[test]
    fn update_without_response() {
        let state = UpdateState { response: None };

        let result = State::poll_update(state);
        assert_matches!(result, Err(WorkPropertiesUpdateError::InvalidState(_)));
    }

    #[rstest]
    fn update_valid(#[values(200, 204)] status_code: u16) -> anyhow::Result<()> {
        let state = UpdateState {
            response: Some(WorkPropertiesUpdateResponse {
                result: Ok(HttpsResponse {
                    status: status_code,
                    body: vec![],
                }),
            }),
        };

        let (state, instruction) = State::poll_update(state)?;
        assert_matches!(state, State::Done);
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Done(()));

        Ok(())
    }

    #[test]
    fn unexpected_response() -> anyhow::Result<()> {
        let work_properties = WorkPropertiesInitBuilder::default()
            .with_default_availability_status()
            .build()?;

        let mut task = WorkPropertiesUpdateTask::new(context(), work_properties.into());
        let result = task.response(WorkPropertiesUpdateResponse {
            result: Err(crate::https::HttpsError::Timeout(
                "row row row your boat".to_owned(),
            )),
        });
        assert_matches!(result, Err(WorkPropertiesUpdateError::InvalidState(_)));

        Ok(())
    }

    #[test]
    fn unexpected_poll_after_error() {
        let mut task = WorkPropertiesUpdateTask {
            context: context(),
            state: State::Error(WorkPropertiesUpdateError::NetworkError("Heute nicht".to_owned())),
        };
        let result = task.poll();
        assert_matches!(result, Err(WorkPropertiesUpdateError::NetworkError(_)));
    }

    #[test]
    fn unexpected_poll_when_done() {
        let mut task = WorkPropertiesUpdateTask {
            context: context(),
            state: State::Done,
        };
        let result = task.poll();
        assert_matches!(result, Err(WorkPropertiesUpdateError::InvalidState(_)));
    }

    #[test]
    fn complete_task() -> anyhow::Result<()> {
        let work_properties = WorkPropertiesInitBuilder::default()
            .with_default_availability_status()
            .build()?;

        // Init state
        let mut task = WorkPropertiesUpdateTask::new(context(), work_properties.into());
        assert_matches!(&task.state, State::Init(_));

        // Challenge state
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Challenge(_));
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Instruction(_));

        // Update state
        task.response(WorkPropertiesUpdateResponse {
            result: Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "challenge": "bWVvdw==",
                }))?,
            }),
        })?;
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Update(_));
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Instruction(_));

        // Done state
        task.response(WorkPropertiesUpdateResponse {
            result: Ok(HttpsResponse {
                status: 204,
                body: vec![],
            }),
        })?;
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Done);
        assert_matches!(instruction, WorkPropertiesUpdateLoop::Done(()));

        Ok(())
    }

    #[test]
    fn strip_description_when_category_is_none() -> anyhow::Result<()> {
        let work_properties = WorkProperties {
            availability_status: Some(WorkAvailabilityStatus {
                category: protobuf::d2d_sync::WorkAvailabilityStatusCategory::None,
                description: Some("Macht oder ergibt Sinn".to_owned()),
            }),
        };
        let encoded: protobuf::directory::WorkProperties = work_properties.try_into()?;
        let availability_status =
            assert_matches!(encoded.availability_status, Some(availability_status) => availability_status);
        assert_eq!(availability_status.category, 0_i32);
        assert_eq!(availability_status.description, String::new());
        Ok(())
    }

    #[test]
    fn description_length_exceeded() {
        let work_properties = WorkProperties {
            availability_status: Some(WorkAvailabilityStatus {
                category: protobuf::d2d_sync::WorkAvailabilityStatusCategory::Busy,
                description: Some("Ick mack mal Urlaub".repeat(50)),
            }),
        };
        let encoded: Result<protobuf::directory::WorkProperties, WorkPropertiesUpdateError> =
            work_properties.try_into();
        let message =
            assert_matches!(encoded, Err(WorkPropertiesUpdateError::InvalidParameter(message)) => message);
        assert_eq!(
            message,
            "Availability status description length exceeded. Used 950 of 256".to_owned()
        );
    }
}
