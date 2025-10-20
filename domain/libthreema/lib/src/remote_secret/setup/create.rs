//! Task structures to create a new remote secret.
use core::mem;

use const_format::formatcp;
use educe::Educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, info};

use crate::{
    common::{
        keys::{RemoteSecret, RemoteSecretAuthenticationToken},
        task::TaskLoop,
    },
    https::work_directory,
    remote_secret::setup::{
        RemoteSecretSetupContext, RemoteSecretSetupError, RemoteSecretSetupInstruction,
        RemoteSecretSetupResponse,
    },
};

/// 1. Update key storage according to the application data protection scheme to make use of the
///    `remote_secret`, fetchable via `remote_secret_authentication_token`.
/// 2. Purge the `remote_secret` from memory.
/// 3. Initiate the [`crate::remote_secret::monitor::RemoteSecretMonitorProtocol`] from the
///    `remote_secret_authentication_token` and the [`crate::common::keys::RemoteSecretHash`] derived from the
///    `remote_secret`.
pub struct RemoteSecretCreateResult {
    /// Established remote secret.
    pub remote_secret: RemoteSecret,

    /// Assigned remote secret authentication token.
    pub remote_secret_authentication_token: RemoteSecretAuthenticationToken,
}

/// Result of polling a [`RemoteSecretCreateTask`].
pub type RemoteSecretCreateLoop = TaskLoop<RemoteSecretSetupInstruction, RemoteSecretCreateResult>;

struct InitState {
    remote_secret: RemoteSecret,
}

struct ChallengeState {
    remote_secret: RemoteSecret,
    response: Option<RemoteSecretSetupResponse>,
}

struct CreateState {
    remote_secret: RemoteSecret,
    response: Option<RemoteSecretSetupResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(RemoteSecretSetupError),
    Init(InitState),
    Challenge(ChallengeState),
    Create(CreateState),
    Done,
}
impl State {
    fn poll_init(context: &RemoteSecretSetupContext, state: InitState) -> (Self, RemoteSecretCreateLoop) {
        debug!("Requesting challenge to create remote secret");
        let request = work_directory::create_remote_secret_authentication_request(
            &context.client_info,
            &context.work_server_url,
            context.user_identity,
            &context.work_context,
            &state.remote_secret,
        );
        (
            Self::Challenge(ChallengeState {
                remote_secret: state.remote_secret,
                response: None,
            }),
            RemoteSecretCreateLoop::Instruction(RemoteSecretSetupInstruction { request }),
        )
    }

    fn poll_challenge(
        context: &RemoteSecretSetupContext,
        state: ChallengeState,
    ) -> Result<(Self, RemoteSecretCreateLoop), RemoteSecretSetupError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                RemoteSecretSetupResponse::NAME,
                State::CHALLENGE,
            )));
        };

        // Handle the authentication challenge and provide the final request to create a remote secret
        let authentication =
            work_directory::handle_authentication_challenge(&context.client_key, response.result)?;
        info!("Creating remote secret");
        let instruction = RemoteSecretSetupInstruction {
            request: work_directory::create_remote_secret_request(
                &context.client_info,
                &context.work_server_url,
                context.user_identity,
                &context.work_context,
                authentication,
                &state.remote_secret,
            ),
        };
        Ok((
            Self::Create(CreateState {
                remote_secret: state.remote_secret,
                response: None,
            }),
            RemoteSecretCreateLoop::Instruction(instruction),
        ))
    }

    fn poll_create(state: CreateState) -> Result<(Self, RemoteSecretCreateLoop), RemoteSecretSetupError> {
        // Ensure the caller provided the resonse
        let Some(response) = state.response else {
            return Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                RemoteSecretSetupResponse::NAME,
                State::CREATE,
            )));
        };

        // Handle the result
        let remote_secret_authentication_token =
            work_directory::handle_create_remote_secret_result(response.result)?;
        info!("Remote secret created");
        Ok((
            Self::Done,
            RemoteSecretCreateLoop::Done(RemoteSecretCreateResult {
                remote_secret: state.remote_secret,
                remote_secret_authentication_token,
            }),
        ))
    }
}

/// Task for creating a new remote secret.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct RemoteSecretCreateTask {
    #[educe(Debug(ignore))]
    context: RemoteSecretSetupContext,
    state: State,
}
impl RemoteSecretCreateTask {
    /// Create a new task for creating a new remote secret.
    #[must_use]
    pub fn new(context: RemoteSecretSetupContext) -> Self {
        Self {
            context,
            state: State::Init(InitState {
                remote_secret: RemoteSecret::random(),
            }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`RemoteSecretSetupError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<RemoteSecretCreateLoop, RemoteSecretSetupError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} in a transitional state",
                RemoteSecretCreateTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => Ok(State::poll_init(&self.context, state)),
            State::Challenge(state) => State::poll_challenge(&self.context, state),
            State::Create(state) => State::poll_create(state),
            State::Done => Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} already done",
                RemoteSecretCreateTask::NAME
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

    /// Possible results after handling a [`RemoteSecretSetupInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`RemoteSecretSetupError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(&mut self, response: RemoteSecretSetupResponse) -> Result<(), RemoteSecretSetupError> {
        match &mut self.state {
            State::Challenge(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            State::Create(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            _ => Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "Must be in '{}' or '{}' state",
                State::CHALLENGE,
                State::CREATE,
            ))),
        }
    }
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use serde_json::json;

    use super::*;
    use crate::{https::HttpsResponse, remote_secret::setup::tests::setup_context};

    #[test]
    fn init() {
        let context = setup_context();
        let state = InitState {
            remote_secret: RemoteSecret([1_u8; 32]),
        };

        let (state, instruction) = State::poll_init(&context, state);
        let state = assert_matches!(state, State::Challenge(state) => state);
        assert_eq!(state.remote_secret.0, [1_u8; 32]);
        assert!(state.response.is_none());
        assert_matches!(instruction, RemoteSecretCreateLoop::Instruction(_));
    }

    #[test]
    fn challenge_without_response() {
        let context = setup_context();
        let state = ChallengeState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: None,
        };

        let result = State::poll_challenge(&context, state);
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn challenge_invalid() -> anyhow::Result<()> {
        let context = setup_context();
        let state = ChallengeState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: Some(RemoteSecretSetupResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "challenge": "bWVvdw==",
                    }))?,
                }),
            }),
        };

        let result = State::poll_challenge(&context, state);
        assert_matches!(result, Err(RemoteSecretSetupError::ServerError(_)));

        Ok(())
    }

    #[test]
    fn challenge_valid() -> anyhow::Result<()> {
        let context = setup_context();
        let state = ChallengeState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: Some(RemoteSecretSetupResponse {
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
        let state = assert_matches!(state, State::Create(state) => state);
        assert_eq!(state.remote_secret.0, [1_u8; 32]);
        assert!(state.response.is_none());
        assert_matches!(instruction, RemoteSecretCreateLoop::Instruction(_));

        Ok(())
    }

    #[test]
    fn create_without_response() {
        let state = CreateState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: None,
        };

        let result = State::poll_create(state);
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn create_invalid() -> anyhow::Result<()> {
        let state = CreateState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: Some(RemoteSecretSetupResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secretAuthenticationToken": "bWVvdw==",
                    }))?,
                }),
            }),
        };

        let result = State::poll_create(state);
        assert_matches!(result, Err(RemoteSecretSetupError::ServerError(_)));

        Ok(())
    }

    #[test]
    fn create_valid() -> anyhow::Result<()> {
        let state = CreateState {
            remote_secret: RemoteSecret([1_u8; 32]),
            response: Some(RemoteSecretSetupResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secretAuthenticationToken": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                    }))?,
                }),
            }),
        };

        let (state, instruction) = State::poll_create(state)?;
        assert_matches!(state, State::Done);
        let result = assert_matches!(instruction, RemoteSecretCreateLoop::Done(result) => result);
        assert_eq!(result.remote_secret.0, [1_u8; 32]);
        assert_eq!(result.remote_secret_authentication_token.0, [2_u8; 32]);

        Ok(())
    }

    #[test]
    fn unexpected_response() {
        let mut task = RemoteSecretCreateTask::new(setup_context());
        let result = task.response(RemoteSecretSetupResponse {
            result: Err(crate::https::HttpsError::Timeout("isso".to_owned())),
        });
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn unexpected_poll_after_error() {
        let mut task = RemoteSecretCreateTask {
            context: setup_context(),
            state: State::Error(RemoteSecretSetupError::NetworkError(
                "meh, Server hatte keine Lust".to_owned(),
            )),
        };
        let result = task.poll();
        assert_matches!(result, Err(RemoteSecretSetupError::NetworkError(_)));
    }

    #[test]
    fn unexpected_poll_when_done() {
        let mut task = RemoteSecretCreateTask {
            context: setup_context(),
            state: State::Done,
        };
        let result = task.poll();
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn complete_task() -> anyhow::Result<()> {
        // Init state
        let mut task = RemoteSecretCreateTask::new(setup_context());
        let remote_secret = assert_matches!(&task.state, State::Init(state) => state.remote_secret.0);

        // Challenge state
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Challenge(_));
        assert_matches!(instruction, RemoteSecretCreateLoop::Instruction(_));

        // Create state
        task.response(RemoteSecretSetupResponse {
            result: Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "challengePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "challenge": "bWVvdw==",
                }))?,
            }),
        })?;
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Create(_));
        assert_matches!(instruction, RemoteSecretCreateLoop::Instruction(_));

        // Done state
        task.response(RemoteSecretSetupResponse {
            result: Ok(HttpsResponse {
                status: 200,
                body: serde_json::to_vec(&json!({
                    "secretAuthenticationToken": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                }))?,
            }),
        })?;
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Done);
        let result = assert_matches!(instruction, RemoteSecretCreateLoop::Done(result) => result);
        assert_eq!(result.remote_secret.0, remote_secret);
        assert_eq!(result.remote_secret_authentication_token.0, [2_u8; 32]);

        Ok(())
    }
}
