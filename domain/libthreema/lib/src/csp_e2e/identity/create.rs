//! Task structures to create a new identity.
use core::mem;
use std::rc::Rc;

use const_format::formatcp;
use libthreema_macros::{DebugVariantNames, Name, VariantNames};
use tracing::{debug, error, info};

use crate::{
    common::{ChatServerGroup, ClientInfo, ThreemaId, config::Config, keys::ClientKey, task::TaskLoop},
    csp_e2e::{CspE2eProtocolError, Flavor},
    https::{HttpsRequest, HttpsResult, directory},
};

/// 1. Run the HTTPS request as defined by [`HttpsRequest`] and let `response` be the result.
/// 2. Provide `response` to the associated task as a [`CreateIdentityResponse`] and poll again.
pub struct CreateIdentityInstruction {
    /// The HTTPs request to be made.
    pub request: HttpsRequest,
}

/// Possible response to an [`CreateIdentityInstruction`].
#[derive(Name)]
pub struct CreateIdentityResponse {
    /// Result for the HTTPS request.
    pub result: HttpsResult,
}

/// Result of creating an identity.
pub struct CreateIdentityResult {
    /// Assigned identity.
    pub identity: ThreemaId,

    /// Client key associated to the identity.
    pub client_key: ClientKey,

    /// Assigned server group.
    pub server_group: ChatServerGroup,
}

/// Result of polling a [`CreateIdentityTask`].
pub type CreateIdentityLoop = TaskLoop<CreateIdentityInstruction, CreateIdentityResult>;

/// Context for creating an identity.
pub struct CreateIdentityContext {
    /// Client info.
    pub client_info: ClientInfo,

    /// Configuration used by the protocol.
    pub config: Rc<Config>,

    /// Application flavour.
    pub flavor: Flavor,
}

struct InitState {
    client_key: ClientKey,
}

struct DirectoryChallengeState {
    client_key: ClientKey,
    response: Option<CreateIdentityResponse>,
}

struct DirectoryCreateState {
    client_key: ClientKey,
    response: Option<CreateIdentityResponse>,
}

struct RegisterWorkChallengeState {
    client_key: ClientKey,
    identity_info: directory::CreateIdentityResponse,
    response: Option<CreateIdentityResponse>,
}

struct RegisterWorkState {
    client_key: ClientKey,
    identity_info: directory::CreateIdentityResponse,
    response: Option<CreateIdentityResponse>,
}

#[derive(DebugVariantNames, VariantNames)]
enum State {
    Error(CspE2eProtocolError),
    Init(InitState),
    DirectoryChallenge(DirectoryChallengeState),
    DirectoryCreate(DirectoryCreateState),
    RegisterWorkChallenge(RegisterWorkChallengeState),
    RegisterWork(RegisterWorkState),
    Done,
}
impl State {
    fn poll_init(context: &CreateIdentityContext, state: InitState) -> (Self, CreateIdentityLoop) {
        // Request an authentication challenge to create an identity
        let public_key = state.client_key.public_key();
        debug!("Requesting challenge to create identity");
        (
            Self::DirectoryChallenge(DirectoryChallengeState {
                client_key: state.client_key,
                response: None,
            }),
            CreateIdentityLoop::Instruction(CreateIdentityInstruction {
                request: directory::create_identity_authentication_request(
                    &context.client_info,
                    &context.config.directory_server_url,
                    &context.flavor,
                    public_key,
                ),
            }),
        )
    }

    fn poll_directory_challenge(
        context: &CreateIdentityContext,
        state: DirectoryChallengeState,
    ) -> Result<(Self, CreateIdentityLoop), CspE2eProtocolError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                CreateIdentityResponse::NAME,
                State::DIRECTORY_CHALLENGE,
            )));
        };

        // Handle the authentication challenge and provide the final request to create an identity
        let authentication = directory::handle_authentication_challenge(&state.client_key, response.result)?;
        let public_key = state.client_key.public_key();
        info!("Creating identity");
        Ok((
            Self::DirectoryCreate(DirectoryCreateState {
                client_key: state.client_key,
                response: None,
            }),
            CreateIdentityLoop::Instruction(CreateIdentityInstruction {
                request: directory::create_identity_request(
                    &context.client_info,
                    &context.config.directory_server_url,
                    &context.flavor,
                    authentication,
                    public_key,
                ),
            }),
        ))
    }

    fn poll_directory_create(
        context: &CreateIdentityContext,
        state: DirectoryCreateState,
    ) -> Result<(Self, CreateIdentityLoop), CspE2eProtocolError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                CreateIdentityResponse::NAME,
                State::DIRECTORY_CREATE,
            )));
        };

        // Handle the result
        let identity_info = directory::handle_create_identity_result(response.result)?;
        info!(identity = ?identity_info.identity, "Identity created");
        match &context.flavor {
            Flavor::Consumer => Ok((
                Self::Done,
                CreateIdentityLoop::Done(CreateIdentityResult {
                    identity: identity_info.identity,
                    client_key: state.client_key,
                    server_group: identity_info.server_group,
                }),
            )),

            // Request an authentication challenge to update the work properties.
            //
            // Note: This is necessary for work identities to appear in the work cockpit.
            Flavor::Work(work_context) => {
                debug!("Requesting challenge to update work properties");
                let instruction = CreateIdentityLoop::Instruction(CreateIdentityInstruction {
                    request: directory::update_work_properties_authentication_request(
                        &context.client_info,
                        &context.config.directory_server_url,
                        work_context,
                        identity_info.identity,
                    ),
                });
                Ok((
                    Self::RegisterWorkChallenge(RegisterWorkChallengeState {
                        client_key: state.client_key,
                        identity_info,
                        response: None,
                    }),
                    instruction,
                ))
            },
        }
    }

    fn poll_register_work_challenge(
        context: &CreateIdentityContext,
        state: RegisterWorkChallengeState,
    ) -> Result<(Self, CreateIdentityLoop), CspE2eProtocolError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                CreateIdentityResponse::NAME,
                State::REGISTER_WORK_CHALLENGE,
            )));
        };

        // Ensure we're in a work context
        let Flavor::Work(work_context) = &context.flavor else {
            let message = "Work context missing";
            error!(message);
            return Err(CspE2eProtocolError::InternalError(message.to_owned()));
        };

        // Handle the result
        let authentication = directory::handle_authentication_challenge(&state.client_key, response.result)?;
        info!("Registering as work identity");
        let instruction = CreateIdentityLoop::Instruction(CreateIdentityInstruction {
            request: directory::update_work_properties_request(
                &context.client_info,
                &context.config.directory_server_url,
                work_context,
                state.identity_info.identity,
                authentication,
            ),
        });
        Ok((
            State::RegisterWork(RegisterWorkState {
                client_key: state.client_key,
                identity_info: state.identity_info,
                response: None,
            }),
            instruction,
        ))
    }

    fn poll_register_work(
        state: RegisterWorkState,
    ) -> Result<(Self, CreateIdentityLoop), CspE2eProtocolError> {
        // Ensure the caller provided the response
        let Some(response) = state.response else {
            return Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} result was not provided for '{}' state",
                CreateIdentityResponse::NAME,
                State::REGISTER_WORK,
            )));
        };

        // Handle the result
        directory::handle_update_work_properties_result(response.result)?;
        info!("Registered as work identity");
        Ok((
            Self::Done,
            CreateIdentityLoop::Done(CreateIdentityResult {
                identity: state.identity_info.identity,
                client_key: state.client_key,
                server_group: state.identity_info.server_group,
            }),
        ))
    }
}

/// Task for creating a new identity.
#[derive(Debug, Name)]
pub struct CreateIdentityTask {
    state: State,
}
impl CreateIdentityTask {
    /// Create a new task for creating a new identity.
    #[must_use]
    #[expect(clippy::new_without_default, reason = "Task pattern")]
    pub fn new() -> Self {
        Self {
            state: State::Init(InitState {
                client_key: ClientKey::random(),
            }),
        }
    }

    /// Poll to advance the state.
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn poll(
        &mut self,
        context: &CreateIdentityContext,
    ) -> Result<CreateIdentityLoop, CspE2eProtocolError> {
        let result = match mem::replace(
            &mut self.state,
            State::Error(CspE2eProtocolError::InvalidState(formatcp!(
                "{} in a transitional state",
                CreateIdentityTask::NAME
            ))),
        ) {
            State::Error(error) => Err(error),
            State::Init(state) => Ok(State::poll_init(context, state)),
            State::DirectoryChallenge(state) => State::poll_directory_challenge(context, state),
            State::DirectoryCreate(state) => State::poll_directory_create(context, state),
            State::RegisterWorkChallenge(state) => State::poll_register_work_challenge(context, state),
            State::RegisterWork(state) => State::poll_register_work(state),
            State::Done => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "{} already done",
                CreateIdentityTask::NAME
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

    /// Possible results after handling a [`CreateIdentityInstruction`].
    ///
    /// # Errors
    ///
    /// Returns [`CspE2eProtocolError`] for all possible reasons.
    #[tracing::instrument(skip_all, fields(?self))]
    pub fn response(&mut self, response: CreateIdentityResponse) -> Result<(), CspE2eProtocolError> {
        match &mut self.state {
            State::DirectoryChallenge(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            State::DirectoryCreate(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            State::RegisterWorkChallenge(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            State::RegisterWork(state) => {
                let _ = state.response.insert(response);
                Ok(())
            },
            _ => Err(CspE2eProtocolError::InvalidState(formatcp!(
                "Must be in any of the following states: {}, {}, {}, {}",
                State::DIRECTORY_CHALLENGE,
                State::DIRECTORY_CREATE,
                State::REGISTER_WORK,
                State::REGISTER_WORK_CHALLENGE,
            ))),
        }
    }
}
