//! Bindings for the _Work Properties_.
use std::sync::Mutex;

use crate::{
    bindings::uniffi::https::HttpsResult,
    common::{
        ClientInfo, ThreemaId,
        config::{WorkContext, WorkServerBaseUrl},
        keys::ClientKey,
    },
    https::HttpsRequest,
    utils::sync::MutexIgnorePoison as _,
    work::properties::{self, WorkPropertiesUpdateError},
};

/// Binding version of [`properties::WorkPropertiesUpdateContext`].
#[derive(uniffi::Record)]
pub struct WorkPropertiesUpdateContext {
    /// Client info.
    pub client_info: ClientInfo,

    /// Work directory server base URL. Must end with a trailing slash.
    pub work_server_base_url: String,

    /// Work (or OnPrem) application configuration.
    pub work_context: WorkContext,

    /// The user's identity.
    pub user_identity: String,

    /// Client key (32 bytes).
    pub client_key: Vec<u8>,
}
impl TryFrom<WorkPropertiesUpdateContext> for properties::WorkPropertiesUpdateContext {
    type Error = WorkPropertiesUpdateError;

    fn try_from(context: WorkPropertiesUpdateContext) -> Result<Self, Self::Error> {
        let work_server_url = WorkServerBaseUrl::try_from(context.work_server_base_url).map_err(|_| {
            WorkPropertiesUpdateError::InvalidParameter(String::from("'work_server_url' invalid"))
        })?;

        let client_key: [u8; ClientKey::LENGTH] = context.client_key.try_into().map_err(|_| {
            WorkPropertiesUpdateError::InvalidParameter(String::from("'client_key' must be 32 bytes"))
        })?;

        let user_identity = ThreemaId::try_from(context.user_identity.as_str()).map_err(|_| {
            WorkPropertiesUpdateError::InvalidParameter(String::from("'user_identity' invalid"))
        })?;

        Ok(Self {
            client_info: context.client_info,
            work_server_url,
            work_context: context.work_context,
            user_identity,
            client_key: client_key.into(),
        })
    }
}

/// Binding version of [`properties::WorkPropertiesUpdateLoop`].
#[derive(uniffi::Enum)]
pub enum WorkPropertiesUpdateLoop {
    #[expect(missing_docs, reason = "Binding version")]
    Instruction(HttpsRequest),

    #[expect(missing_docs, reason = "Binding version")]
    Done,
}
impl From<properties::WorkPropertiesUpdateLoop> for WorkPropertiesUpdateLoop {
    fn from(update_loop: properties::WorkPropertiesUpdateLoop) -> Self {
        match update_loop {
            properties::WorkPropertiesUpdateLoop::Instruction(
                properties::WorkPropertiesUpdateInstruction { request },
            ) => Self::Instruction(request),

            properties::WorkPropertiesUpdateLoop::Done(()) => Self::Done,
        }
    }
}

/// Binding version of [`properties::WorkPropertiesUpdateTask`].
#[derive(uniffi::Object)]
pub struct WorkPropertiesUpdateTask(Mutex<properties::WorkPropertiesUpdateTask>);

#[uniffi::export]
impl WorkPropertiesUpdateTask {
    /// Binding version of [`properties::WorkPropertiesUpdateTask::new`].
    ///
    /// # Errors
    ///
    /// Returns a [`WorkPropertiesUpdateError::InvalidParameter`] if `context` contains invalid
    /// parameters.
    #[uniffi::constructor]
    pub fn new(
        context: WorkPropertiesUpdateContext,
        work_properties: properties::WorkProperties,
    ) -> Result<Self, WorkPropertiesUpdateError> {
        Ok(Self(Mutex::new(properties::WorkPropertiesUpdateTask::new(
            context.try_into()?,
            work_properties,
        ))))
    }

    /// Binding version of [`properties::WorkPropertiesUpdateTask::poll`].
    #[expect(clippy::missing_errors_doc, reason = "Binding version")]
    pub fn poll(&self) -> Result<WorkPropertiesUpdateLoop, WorkPropertiesUpdateError> {
        self.0
            .lock_ignore_poison()
            .poll()
            .map(WorkPropertiesUpdateLoop::from)
    }

    /// Binding version of [`properties::WorkPropertiesUpdateTask::response`].
    #[expect(clippy::missing_errors_doc, reason = "Binding version")]
    pub fn response(&self, response: HttpsResult) -> Result<(), WorkPropertiesUpdateError> {
        self.0.lock_ignore_poison().response(response.into())
    }
}
