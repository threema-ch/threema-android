//! Bindings for the _Remote Secret_.
pub mod setup {
    //! Bindings for the remote secret setup.
    use js_sys::Error;
    use serde::Deserialize;
    use serde_bytes::ByteBuf;
    use tsify::Tsify;
    use wasm_bindgen::prelude::wasm_bindgen;

    use crate::{
        common::{
            ClientInfo, ThreemaId,
            config::{WorkContext, WorkServerBaseUrl},
            keys::ClientKey,
        },
        remote_secret::setup,
    };

    /// Binding version of [`setup::RemoteSecretSetupContext`].
    #[derive(Tsify, Deserialize)]
    #[serde(rename_all = "camelCase")]
    #[tsify(from_wasm_abi)]
    pub struct RemoteSecretSetupContext {
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

    impl TryFrom<RemoteSecretSetupContext> for setup::RemoteSecretSetupContext {
        type Error = Error;

        fn try_from(context: RemoteSecretSetupContext) -> Result<Self, Self::Error> {
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

    pub mod create {
        //! Bindings for the remote secret creation.
        use js_sys::Error;
        use serde::Serialize;
        use serde_bytes::ByteBuf;
        use tsify::Tsify;
        use wasm_bindgen::prelude::wasm_bindgen;

        use crate::{
            bindings::wasm::https::{HttpsRequest, HttpsResult},
            remote_secret::setup::{self, create},
        };

        /// Binding version of [`create::RemoteSecretCreateResult`].
        #[derive(Tsify, Serialize)]
        #[serde(rename_all = "camelCase")]
        #[tsify(into_wasm_abi)]
        #[expect(clippy::struct_field_names, reason = "Names as defined in protocol")]
        pub struct RemoteSecretCreateResult {
            /// Established remote secret (32 bytes).
            pub remote_secret: ByteBuf,

            /// Assigned remote secret authentication token (32 bytes).
            pub remote_secret_authentication_token: ByteBuf,

            /// The hash derived from the `remote_secret` (32 bytes).
            pub remote_secret_hash: ByteBuf,
        }

        /// Binding version of [`create::RemoteSecretCreateLoop`].
        #[derive(Tsify, Serialize)]
        #[serde(
            tag = "type",
            content = "value",
            rename_all = "kebab-case",
            rename_all_fields = "camelCase"
        )]
        #[tsify(into_wasm_abi)]
        pub enum RemoteSecretCreateLoop {
            #[expect(missing_docs, reason = "Binding version")]
            Instruction(HttpsRequest),

            #[expect(missing_docs, reason = "Binding version")]
            Done(RemoteSecretCreateResult),
        }

        impl From<create::RemoteSecretCreateLoop> for RemoteSecretCreateLoop {
            fn from(create_loop: create::RemoteSecretCreateLoop) -> Self {
                match create_loop {
                    create::RemoteSecretCreateLoop::Instruction(setup::RemoteSecretSetupInstruction {
                        request,
                    }) => Self::Instruction(request.into()),

                    create::RemoteSecretCreateLoop::Done(result) => Self::Done(RemoteSecretCreateResult {
                        remote_secret: ByteBuf::from(result.remote_secret.0.to_vec()),
                        remote_secret_authentication_token: ByteBuf::from(
                            result.remote_secret_authentication_token.0.to_vec(),
                        ),
                        remote_secret_hash: ByteBuf::from(result.remote_secret.derive_hash().0.to_vec()),
                    }),
                }
            }
        }

        /// Binding version of [`create::RemoteSecretCreateTask`].
        #[wasm_bindgen]
        pub struct RemoteSecretCreateTask(create::RemoteSecretCreateTask);

        #[wasm_bindgen]
        impl RemoteSecretCreateTask {
            /// Binding version of [`create::RemoteSecretCreateTask::new`].
            ///
            /// # Errors
            ///
            /// Returns an error if `context` contains invalid parameters.
            pub fn new(context: super::RemoteSecretSetupContext) -> Result<Self, Error> {
                Ok(Self(create::RemoteSecretCreateTask::new(context.try_into()?)))
            }

            /// Binding version of [`create::RemoteSecretCreateTask::poll`].
            #[allow(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn poll(&mut self) -> Result<RemoteSecretCreateLoop, Error> {
                self.0
                    .poll()
                    .map(RemoteSecretCreateLoop::from)
                    .map_err(|error| Error::new(&error.to_string()))
            }

            /// Binding version of [`create::RemoteSecretCreateTask::response`].
            #[allow(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn response(&mut self, response: HttpsResult) -> Result<(), Error> {
                self.0
                    .response(response.into())
                    .map_err(|error| Error::new(&error.to_string()))
            }
        }
    }

    pub mod delete {
        //! Bindings for the remote secret deletion.
        use js_sys::Error;
        use serde::Serialize;
        use tsify::Tsify;
        use wasm_bindgen::prelude::wasm_bindgen;

        use crate::{
            bindings::wasm::https::{HttpsRequest, HttpsResult},
            common::keys::RemoteSecretAuthenticationToken,
            remote_secret::setup::{self, delete},
        };

        /// Binding version of [`delete::RemoteSecretDeleteLoop`].
        #[derive(Tsify, Serialize)]
        #[serde(
            tag = "type",
            content = "value",
            rename_all = "kebab-case",
            rename_all_fields = "camelCase"
        )]
        #[tsify(into_wasm_abi)]
        pub enum RemoteSecretDeleteLoop {
            #[expect(missing_docs, reason = "Binding version")]
            Instruction(HttpsRequest),

            #[expect(missing_docs, reason = "Binding version")]
            Done,
        }
        impl From<delete::RemoteSecretDeleteLoop> for RemoteSecretDeleteLoop {
            fn from(delete_loop: delete::RemoteSecretDeleteLoop) -> Self {
                match delete_loop {
                    delete::RemoteSecretDeleteLoop::Instruction(setup::RemoteSecretSetupInstruction {
                        request,
                    }) => Self::Instruction(request.into()),

                    delete::RemoteSecretDeleteLoop::Done(()) => Self::Done,
                }
            }
        }

        /// Binding version of [`delete::RemoteSecretDeleteTask`].
        #[wasm_bindgen]
        pub struct RemoteSecretDeleteTask(delete::RemoteSecretDeleteTask);

        #[wasm_bindgen]
        impl RemoteSecretDeleteTask {
            /// Binding version of [`delete::RemoteSecretDeleteTask::new`].
            ///
            /// # Errors
            ///
            /// Returns an error if `context` contains invalid parameters or if the
            /// `remote_secret_authentication_token` is not exactly 32 bytes.
            pub fn new(
                context: super::RemoteSecretSetupContext,
                remote_secret_authentication_token: Vec<u8>,
            ) -> Result<Self, Error> {
                let remote_secret_authentication_token: [u8; RemoteSecretAuthenticationToken::LENGTH] =
                    remote_secret_authentication_token
                        .try_into()
                        .map_err(|_| Error::new("'remote_secret_authentication_token' must be 32 bytes"))?;

                Ok(Self(delete::RemoteSecretDeleteTask::new(
                    context.try_into()?,
                    remote_secret_authentication_token.into(),
                )))
            }

            /// Binding version of [`delete::RemoteSecretDeleteTask::poll`].
            #[allow(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn poll(&mut self) -> Result<RemoteSecretDeleteLoop, Error> {
                self.0
                    .poll()
                    .map(RemoteSecretDeleteLoop::from)
                    .map_err(|error| Error::new(&error.to_string()))
            }

            /// Binding version of [`delete::RemoteSecretDeleteTask::response`].
            #[allow(clippy::missing_errors_doc, reason = "Binding version")]
            pub fn response(&mut self, response: HttpsResult) -> Result<(), Error> {
                self.0
                    .response(response.into())
                    .map_err(|error| Error::new(&error.to_string()))
            }
        }
    }
}

pub mod monitor {
    //! Bindings for the remote secret monitoring.
    use js_sys::Error;
    use serde::Serialize;
    use serde_bytes::ByteBuf;
    use tsify::Tsify;
    use wasm_bindgen::prelude::wasm_bindgen;

    use crate::{
        bindings::wasm::https::{HttpsRequest, HttpsResult},
        common::{
            ClientInfo,
            config::WorkServerBaseUrl,
            keys::{RemoteSecretAuthenticationToken, RemoteSecretHash},
        },
        remote_secret::monitor,
    };

    /// Binding version of [`monitor::RemoteSecretMonitorInstruction`].
    #[derive(Tsify, Serialize)]
    #[serde(
        tag = "type",
        content = "value",
        rename_all = "kebab-case",
        rename_all_fields = "camelCase"
    )]
    #[tsify(into_wasm_abi)]
    pub enum RemoteSecretMonitorInstruction {
        #[expect(missing_docs, reason = "Binding version")]
        Request(HttpsRequest),

        #[expect(missing_docs, reason = "Binding version")]
        Schedule {
            timeout_ms: u32,
            remote_secret: Option<ByteBuf>,
        },
    }
    impl From<monitor::RemoteSecretMonitorInstruction> for RemoteSecretMonitorInstruction {
        fn from(instruction: monitor::RemoteSecretMonitorInstruction) -> Self {
            match instruction {
                monitor::RemoteSecretMonitorInstruction::Request(https_request) => {
                    Self::Request(https_request.into())
                },

                monitor::RemoteSecretMonitorInstruction::Schedule {
                    timeout,
                    remote_secret,
                } => Self::Schedule {
                    timeout_ms: timeout
                        .as_millis()
                        .try_into()
                        .expect("timeout should not exceed a u32"),
                    remote_secret: remote_secret.map(|remote_secret| ByteBuf::from(remote_secret.0)),
                },
            }
        }
    }

    /// Binding version of [`monitor::RemoteSecretMonitorProtocol`].
    #[wasm_bindgen]
    pub struct RemoteSecretMonitorProtocol(monitor::RemoteSecretMonitorProtocol);

    #[wasm_bindgen]
    impl RemoteSecretMonitorProtocol {
        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::new`].
        ///
        /// # Errors
        ///
        /// Returns an error if
        ///
        /// - `work_server_base_url` is not a valid base URL (must end with a trailing slash)
        /// - `remote_secret_authentication_token` is not exactly 32 bytes
        /// - `remote_secret_hash` is not exactly 32 bytes
        pub fn new(
            client_info: ClientInfo,
            work_server_base_url: String,
            remote_secret_authentication_token: Vec<u8>,
            remote_secret_hash: Vec<u8>,
        ) -> Result<Self, Error> {
            let work_server_url = WorkServerBaseUrl::try_from(work_server_base_url)
                .map_err(|_| Error::new("'work_server_base_url' invalid"))?;

            let remote_secret_authentication_token: [u8; RemoteSecretAuthenticationToken::LENGTH] =
                remote_secret_authentication_token
                    .try_into()
                    .map_err(|_| Error::new("'remote_secret_authentication_token' must be 32 bytes"))?;

            let remote_secret_hash: [u8; RemoteSecretHash::LENGTH] = remote_secret_hash
                .try_into()
                .map_err(|_| Error::new("'remote_secret_hash' must be 32 bytes"))?;

            let context = monitor::RemoteSecretMonitorContext {
                client_info,
                work_server_url,
                remote_secret_authentication_token: remote_secret_authentication_token.into(),
                remote_secret_verifier: monitor::RemoteSecretVerifier::RemoteSecretHash(
                    remote_secret_hash.into(),
                ),
            };

            Ok(Self(monitor::RemoteSecretMonitorProtocol::new(context)))
        }

        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::poll`].
        #[allow(clippy::missing_errors_doc, reason = "Binding version")]
        pub fn poll(&mut self) -> Result<RemoteSecretMonitorInstruction, Error> {
            self.0
                .poll()
                .map(RemoteSecretMonitorInstruction::from)
                .map_err(|error| Error::new(&error.to_string()))
        }

        /// Binding version of [`monitor::RemoteSecretMonitorProtocol::response`].
        #[allow(clippy::missing_errors_doc, reason = "Binding version")]
        pub fn response(&mut self, response: HttpsResult) -> Result<(), Error> {
            self.0
                .response(response.into())
                .map_err(|error| Error::new(&error.to_string()))
        }
    }
}
