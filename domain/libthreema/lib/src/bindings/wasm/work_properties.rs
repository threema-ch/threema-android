//! Bindings for the _Work Properties_.
use js_sys::Error;
use serde::{Deserialize, Serialize};
use serde_bytes::ByteBuf;
use tsify::Tsify;
use wasm_bindgen::prelude::wasm_bindgen;

use crate::{
    bindings::wasm::https::{HttpsRequest, HttpsResult},
    common::{
        ClientInfo, ThreemaId,
        config::{WorkContext, WorkServerBaseUrl},
        keys::ClientKey,
    },
    work::properties::{self},
};

/// Binding version of [`properties::WorkPropertiesUpdateContext`].
#[derive(Tsify, Deserialize)]
#[serde(rename_all = "camelCase")]
#[tsify(from_wasm_abi)]
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
    pub client_key: ByteBuf,
}
impl TryFrom<WorkPropertiesUpdateContext> for properties::WorkPropertiesUpdateContext {
    type Error = Error;

    fn try_from(context: WorkPropertiesUpdateContext) -> Result<Self, Self::Error> {
        let work_server_url = WorkServerBaseUrl::try_from(context.work_server_base_url)
            .map_err(|_| Error::new("'work_server_base_url' invalid"))?;

        let client_key: [u8; ClientKey::LENGTH] = context
            .client_key
            .to_vec()
            .try_into()
            .map_err(|_| Error::new("'client_key' must be 32 bytes"))?;

        let user_identity = ThreemaId::try_from(context.user_identity.as_str())
            .map_err(|_| Error::new("'user_identity' invalid"))?;

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
#[derive(Tsify, Serialize)]
#[serde(
    tag = "type",
    content = "value",
    rename_all = "kebab-case",
    rename_all_fields = "camelCase"
)]
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
            ) => Self::Instruction(request.into()),
            properties::WorkPropertiesUpdateLoop::Done(()) => Self::Done,
        }
    }
}

/// Binding version of a [`WorkPropertiesUpdateTask::poll`] result.
#[derive(Tsify, Serialize)]
#[serde(
    tag = "type",
    content = "value",
    rename_all = "kebab-case",
    rename_all_fields = "camelCase"
)]
#[tsify(into_wasm_abi)]
pub enum WorkPropertiesUpdatePollResult {
    #[expect(missing_docs, reason = "Binding version")]
    UpdateLoop(WorkPropertiesUpdateLoop),

    #[expect(missing_docs, reason = "Binding version")]
    Error(properties::WorkPropertiesUpdateError),
}
impl From<Result<properties::WorkPropertiesUpdateLoop, properties::WorkPropertiesUpdateError>>
    for WorkPropertiesUpdatePollResult
{
    fn from(
        result: Result<properties::WorkPropertiesUpdateLoop, properties::WorkPropertiesUpdateError>,
    ) -> Self {
        match result {
            Ok(update_loop) => Self::UpdateLoop(update_loop.into()),
            Err(error) => Self::Error(error),
        }
    }
}

/// Binding version of [`properties::WorkPropertiesUpdateTask`].
#[wasm_bindgen]
pub struct WorkPropertiesUpdateTask(properties::WorkPropertiesUpdateTask);

#[wasm_bindgen]
impl WorkPropertiesUpdateTask {
    /// Binding version of [`properties::WorkPropertiesUpdateTask::new`].
    ///
    /// # Errors
    ///
    /// Returns an error if `context` contains invalid parameters.
    pub fn new(
        context: WorkPropertiesUpdateContext,
        work_properties: properties::WorkProperties,
    ) -> Result<Self, Error> {
        Ok(Self(properties::WorkPropertiesUpdateTask::new(
            context.try_into()?,
            work_properties,
        )))
    }

    /// Binding version of [`properties::WorkPropertiesUpdateTask::poll`].
    pub fn poll(&mut self) -> WorkPropertiesUpdatePollResult {
        self.0.poll().into()
    }

    /// Binding version of [`properties::WorkPropertiesUpdateTask::response`].
    pub fn response(&mut self, response: HttpsResult) -> Option<properties::WorkPropertiesUpdateError> {
        self.0.response(response.into()).err()
    }
}
