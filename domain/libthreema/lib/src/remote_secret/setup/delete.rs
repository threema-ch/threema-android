//! Task structures to remove an existing remote secret.
use core::mem;

use const_format::formatcp;
use educe::Educe;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, info};

use crate::{
    common::{keys::RemoteSecretAuthenticationToken, task::TaskLoop},
    https::work_directory,
    remote_secret::setup::{
        RemoteSecretSetupContext, RemoteSecretSetupError, RemoteSecretSetupInstruction,
        RemoteSecretSetupResponse,
    },
    utils::debug::Name as _,
};

/// Result of polling a [`RemoteSecretDeleteTask`].
pub type RemoteSecretDeleteLoop = TaskLoop<RemoteSecretSetupInstruction, ()>;

struct InitState {
    remote_secret_authentication_token: RemoteSecretAuthenticationToken,
}

struct ChallengeState {
    remote_secret_authentication_token: RemoteSecretAuthenticationToken,
    response: Option<RemoteSecretSetupResponse>,
}

struct DeleteState {
    response: Option<RemoteSecretSetupResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(RemoteSecretSetupError),
    Init(InitState),
    Challenge(ChallengeState),
    Delete(DeleteState),
    Done,
}
impl State {
    fn poll_init(context: &RemoteSecretSetupContext, state: InitState) -> (Self, RemoteSecretDeleteLoop) {
        debug!("Requesting challenge to remove remote secret");
        let request = work_directory::delete_remote_secret_authentication_request(
            &context.client_info,
            &context.work_server_url,
            context.user_identity,
            &context.work_context,
            &state.remote_secret_authentication_token,
        );
        (
            Self::Challenge(ChallengeState {
                remote_secret_authentication_token: state.remote_secret_authentication_token,
                response: None,
            }),
            RemoteSecretDeleteLoop::Instruction(RemoteSecretSetupInstruction { request }),
        )
    }

    fn poll_challenge(
        context: &RemoteSecretSetupContext,
        state: ChallengeState,
    ) -> Result<(Self, RemoteSecretDeleteLoop), RemoteSecretSetupError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                RemoteSecretSetupResponse::NAME,
                State::CHALLENGE,
            )));
        };

        // Handle the authentication challenge and provide the final request to remove a remote secret
        let authentication =
            work_directory::handle_authentication_challenge(&context.client_key, response.result)?;
        info!("Removing remote secret");
        Ok((
            Self::Delete(DeleteState { response: None }),
            RemoteSecretDeleteLoop::Instruction(RemoteSecretSetupInstruction {
                request: work_directory::delete_remote_secret_request(
                    &context.client_info,
                    &context.work_server_url,
                    context.user_identity,
                    &context.work_context,
                    authentication,
                    &state.remote_secret_authentication_token,
                ),
            }),
        ))
    }

    fn poll_delete(state: DeleteState) -> Result<(Self, RemoteSecretDeleteLoop), RemoteSecretSetupError> {
        // Ensure the caller provided the resonse
        let Some(response) = state.response else {
            return Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                RemoteSecretSetupResponse::NAME,
                State::DELETE,
            )));
        };

        // Handle the result
        work_directory::handle_delete_remote_secret_result(response.result)?;
        info!("Remote secret removed");
        Ok((Self::Done, RemoteSecretDeleteLoop::Done(())))
    }
}

/// Task for removing a remote secret.
#[derive(Name, Educe)]
#[educe(Debug)]
pub struct RemoteSecretDeleteTask {
    #[educe(Debug(ignore))]
    context: RemoteSecretSetupContext,
    state: State,
}
impl RemoteSecretDeleteTask {
    /// Create a new task for removing a remote secret.
    #[must_use]
    pub fn new(
        context: RemoteSecretSetupContext,
        remote_secret_authentication_token: RemoteSecretAuthenticationToken,
    ) -> Self {
        Self {
            context,
            state: State::Init(InitState {
                remote_secret_authentication_token,
            }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`RemoteSecretSetupError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(&mut self) -> Result<RemoteSecretDeleteLoop, RemoteSecretSetupError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} in a transitional state",
                RemoteSecretDeleteTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => Ok(State::poll_init(&self.context, state)),
            State::Challenge(state) => State::poll_challenge(&self.context, state),
            State::Delete(state) => State::poll_delete(state),
            State::Done => Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "{} already done",
                RemoteSecretDeleteTask::NAME
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
            State::Delete(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            _ => Err(RemoteSecretSetupError::InvalidState(formatcp!(
                "Must be in '{}' or '{}' state",
                State::CHALLENGE,
                State::DELETE,
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
            remote_secret_authentication_token: RemoteSecretAuthenticationToken([2_u8; 32]),
        };

        let (state, instruction) = State::poll_init(&context, state);
        let state = assert_matches!(state, State::Challenge(state) => state);
        assert_eq!(state.remote_secret_authentication_token.0, [2_u8; 32]);
        assert!(state.response.is_none());
        assert_matches!(instruction, RemoteSecretDeleteLoop::Instruction(_));
    }

    #[test]
    fn challenge_without_response() {
        let context = setup_context();
        let state = ChallengeState {
            remote_secret_authentication_token: RemoteSecretAuthenticationToken([2_u8; 32]),
            response: None,
        };

        let result = State::poll_challenge(&context, state);
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn challenge_invalid() -> anyhow::Result<()> {
        let context = setup_context();
        let state = ChallengeState {
            remote_secret_authentication_token: RemoteSecretAuthenticationToken([2_u8; 32]),
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
            remote_secret_authentication_token: RemoteSecretAuthenticationToken([2_u8; 32]),
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
        let state = assert_matches!(state, State::Delete(state) => state);
        assert!(state.response.is_none());
        assert_matches!(instruction, RemoteSecretDeleteLoop::Instruction(_));

        Ok(())
    }

    #[test]
    fn delete_without_response() {
        let state = DeleteState { response: None };

        let result = State::poll_delete(state);
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn delete_invalid() {
        let state = DeleteState {
            response: Some(RemoteSecretSetupResponse {
                result: Ok(HttpsResponse {
                    status: 413,
                    body: vec![],
                }),
            }),
        };

        let result = State::poll_delete(state);
        assert_matches!(result, Err(RemoteSecretSetupError::ServerError(_)));
    }

    #[test]
    fn delete_valid() -> anyhow::Result<()> {
        let state = DeleteState {
            response: Some(RemoteSecretSetupResponse {
                result: Ok(HttpsResponse {
                    status: 200,
                    body: serde_json::to_vec(&json!({
                        "secretAuthenticationToken": "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
                    }))?,
                }),
            }),
        };

        let (state, instruction) = State::poll_delete(state)?;
        assert_matches!(state, State::Done);
        assert_matches!(instruction, RemoteSecretDeleteLoop::Done(()));

        Ok(())
    }

    #[test]
    fn unexpected_response() {
        let mut task =
            RemoteSecretDeleteTask::new(setup_context(), RemoteSecretAuthenticationToken([2_u8; 32]));
        let result = task.response(RemoteSecretSetupResponse {
            result: Err(crate::https::HttpsError::Timeout("isso".to_owned())),
        });
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn unexpected_poll_after_error() {
        let mut task = RemoteSecretDeleteTask {
            context: setup_context(),
            state: State::Error(RemoteSecretSetupError::RateLimitExceeded),
        };
        let result = task.poll();
        assert_matches!(result, Err(RemoteSecretSetupError::RateLimitExceeded));
    }

    #[test]
    fn unexpected_poll_when_done() {
        let mut task = RemoteSecretDeleteTask {
            context: setup_context(),
            state: State::Done,
        };
        let result = task.poll();
        assert_matches!(result, Err(RemoteSecretSetupError::InvalidState(_)));
    }

    #[test]
    fn complete_task() -> anyhow::Result<()> {
        // Init state
        let mut task =
            RemoteSecretDeleteTask::new(setup_context(), RemoteSecretAuthenticationToken([2_u8; 32]));
        assert_matches!(&task.state, State::Init(_));

        // Challenge state
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Challenge(_));
        assert_matches!(instruction, RemoteSecretDeleteLoop::Instruction(_));

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
        assert_matches!(task.state, State::Delete(_));
        assert_matches!(instruction, RemoteSecretDeleteLoop::Instruction(_));

        // Done state
        task.response(RemoteSecretSetupResponse {
            result: Ok(HttpsResponse {
                status: 204,
                body: vec![],
            }),
        })?;
        let instruction = task.poll()?;
        assert_matches!(task.state, State::Done);
        assert_matches!(instruction, RemoteSecretDeleteLoop::Done(()));

        Ok(())
    }
}
