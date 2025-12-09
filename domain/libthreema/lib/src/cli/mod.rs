//! Common structures and functionality for the CLI.
//!
//! Note: Prefer placing CLI implementations next to the associated structures, if possible.
use core::cell::RefCell;
use std::rc::Rc;

use anyhow::bail;
use clap::{Args, ValueEnum};
use tracing::trace;

use crate::{
    common::{
        ChatServerGroup, ClientInfo, CspDeviceId, D2xDeviceId, DeviceCookie, ThreemaId,
        config::{
            Config, ConfigEnvironment, Flavor, OnPremConfig, OnPremLicense, WorkContext, WorkCredentials,
            WorkFlavor,
        },
        keys::{
            ClientKey, DeviceGroupKey, RawClientKey, RawDeviceGroupKey, RemoteSecretAuthenticationToken,
            RemoteSecretHash,
        },
    },
    csp::CspProtocolContextInit,
    csp_e2e::{CspE2eContextInit, D2xContextInit},
    d2m::D2mContext,
    model::provider::NonceStorage,
    protobuf,
    remote_secret::{
        monitor::{RemoteSecretMonitorContext, RemoteSecretVerifier},
        setup::RemoteSecretSetupContext,
    },
};

/// Config environment option for CLI, reduced to variants for Consumer and Work. Irrelevant for OnPrem.
#[derive(Debug, Clone, Copy, ValueEnum)]
pub enum ConfigEnvironmentOption {
    /// Sandbox environment.
    Sandbox,

    /// Production environment.
    Production,
}
impl From<ConfigEnvironmentOption> for ConfigEnvironment {
    fn from(config: ConfigEnvironmentOption) -> Self {
        match config {
            ConfigEnvironmentOption::Sandbox => ConfigEnvironment::Sandbox,
            ConfigEnvironmentOption::Production => ConfigEnvironment::Production,
        }
    }
}

/// Common configuration options for CLI.
#[derive(Debug, Args)]
#[group(required = true)]
pub struct CommonConfigOptions {
    /// Consumer environment.
    #[arg(long)]
    pub consumer: Option<ConfigEnvironmentOption>,

    /// Work environment.
    #[arg(long, requires = "credentials")]
    pub work: Option<ConfigEnvironmentOption>,

    /// OnPrem environment license URL, i.e.
    /// `threemaonprem://license?username=<username>&password=<password>&server=<oppf-file-url>`.
    #[arg(long)]
    pub onprem: Option<String>,

    /// Work / OnPrem credentials, i.e. `<username:password>`.
    #[arg(
        long,
        requires = "work",
        value_parser = WorkCredentials::from_colon_separated_str,
    )]
    pub credentials: Option<WorkCredentials>,
}

/// Common configuration for the CLI.
pub struct CommonConfig {
    /// Configuration.
    pub config: Rc<Config>,

    /// General flavour of the application.
    pub flavor: Flavor,
}
impl CommonConfig {
    /// Create a [`CommonConfig`] from [`CommonConfigOptions`].
    ///
    /// Note: This is only an asynchronous process for an OnPrem configuration in which case the OPPF file
    /// will be downloaded and verified.
    ///
    /// # Errors
    ///
    /// Returns an error if the options are invalid, or in case of an OnPrem configuration in case the
    /// OPPF file could not be downloaded or verified.
    pub async fn from_options(
        http_client: &reqwest::Client,
        options: CommonConfigOptions,
    ) -> anyhow::Result<Self> {
        let config = match (
            options.consumer,
            options.work,
            options.onprem,
            options.credentials,
        ) {
            (Some(consumer_config), None, None, None) => Self {
                config: Rc::new(Config::from(ConfigEnvironment::from(consumer_config))),
                flavor: Flavor::Consumer,
            },

            (None, Some(work_config), None, Some(credentials)) => Self {
                config: Rc::new(Config::from(ConfigEnvironment::from(work_config))),
                flavor: Flavor::Work(WorkContext {
                    credentials: credentials.clone(),
                    flavor: WorkFlavor::Work,
                }),
            },

            (None, None, Some(onprem_license_url), None) => {
                // Parse license URL
                trace!(onprem_license_url, "Decoding license URL");
                let onprem_license = OnPremLicense::from_url(&onprem_license_url)?;

                // Download and decode configuration
                trace!(?onprem_license, "Retrieving OnPrem configuration");
                let onprem_config = OnPremConfig::decode_and_verify(
                    &onprem_license.download_configuration(http_client).await?,
                )?;

                // Apply configuration
                trace!(?onprem_config, "Applying OnPrem configuration");
                Self {
                    config: Rc::new(Config::from(ConfigEnvironment::OnPrem(Box::new(onprem_config)))),
                    flavor: Flavor::Work(onprem_license.work_context),
                }
            },

            _ => bail!("Configuration constraints should have been handled by clap"),
        };
        Ok(config)
    }
}

/// Minimal configuration options for an identity.
#[derive(Debug, Args)]
pub struct MinimalIdentityConfigOptions {
    /// Common config.
    #[command(flatten)]
    pub common: CommonConfigOptions,

    /// Threema ID.
    #[arg(long)]
    pub threema_id: ThreemaId,

    /// Client key (32 bytes, hex encoded).
    #[arg(long, value_parser = RawClientKey::from_hex)]
    pub client_key: RawClientKey,
}

/// D2X configuration options.
#[derive(Debug, Args)]
#[group(
    required = false,
    requires_all = ["d2x_device_id", "csp_device_id", "device_group_key", "expected_device_slot_state"],
)]
pub struct D2xConfigOptions {
    /// D2X device ID.
    #[arg(long)]
    pub d2x_device_id: Option<D2xDeviceId>,

    /// CSP device ID.
    #[arg(long)]
    pub csp_device_id: Option<CspDeviceId>,

    /// D2X device group key.
    #[arg(long, value_parser = RawDeviceGroupKey::from_hex)]
    pub device_group_key: Option<RawDeviceGroupKey>,

    /// Expected device slot state.
    #[arg(long)]
    pub expected_device_slot_state: Option<protobuf::d2m::DeviceSlotState>,
}

/// Combination of common configuration options, CSP and D2X identity configuration for the CLI.
#[derive(Debug, Args)]
pub struct FullIdentityConfigOptions {
    /// Identity config.
    #[command(flatten)]
    pub minimal: MinimalIdentityConfigOptions,

    /// CSP server group.
    #[arg(long)]
    pub csp_server_group: ChatServerGroup,

    /// Optional CSP device cookie.
    #[arg(long)]
    pub csp_device_cookie: Option<DeviceCookie>,

    /// Optional D2X configuration.
    #[command(flatten)]
    pub d2x_config: D2xConfigOptions,
}

/// Minimal identity configuration for the CLI.
pub struct MinimalIdentityConfig {
    /// Common configuration.
    pub common: CommonConfig,

    /// The user's identity.
    pub user_identity: ThreemaId,

    /// Client key.
    pub client_key: RawClientKey,
}
impl MinimalIdentityConfig {
    /// Create an [`MinimalIdentityConfig`] from [`MinimalIdentityConfigOptions`].
    ///
    /// Note: This is only an asynchronous process for an OnPrem configuration in which case the OPPF file
    /// will be downloaded and verified.
    ///
    /// # Errors
    ///
    /// Returns an error if the options are invalid, or in case of an OnPrem configuration in case the
    /// OPPF file could not be downloaded or verified.
    pub async fn from_options(
        http_client: &reqwest::Client,
        options: MinimalIdentityConfigOptions,
    ) -> anyhow::Result<Self> {
        Ok(Self {
            common: CommonConfig::from_options(http_client, options.common).await?,
            user_identity: options.threema_id,
            client_key: options.client_key,
        })
    }

    /// Generate a [`RemoteSecretSetupContext`] from the config.
    ///
    /// # Errors
    ///
    /// Returns an error if the configuration flavor is not Work (or OnPrem).
    pub fn remote_secret_setup_context(&self) -> anyhow::Result<RemoteSecretSetupContext> {
        let Flavor::Work(work_context) = &self.common.flavor else {
            bail!("Remote secret context only available for 'Work' flavor");
        };
        Ok(RemoteSecretSetupContext {
            client_info: ClientInfo::Libthreema,
            work_server_url: self.common.config.work_server_url.clone(),
            work_context: work_context.clone(),
            user_identity: self.user_identity,
            client_key: ClientKey::from(&self.client_key),
        })
    }

    /// Generate a [`RemoteSecretMonitorContext`] from the config.
    ///
    /// # Errors
    ///
    /// Returns an error if the configuration flavor is not Work (or OnPrem).
    #[must_use]
    pub fn remote_secret_monitor_context(
        &self,
        remote_secret_authentication_token: RemoteSecretAuthenticationToken,
        remote_secret_hash: RemoteSecretHash,
    ) -> RemoteSecretMonitorContext {
        RemoteSecretMonitorContext {
            client_info: ClientInfo::Libthreema,
            work_server_url: self.common.config.work_server_url.clone(),
            remote_secret_authentication_token,
            remote_secret_verifier: RemoteSecretVerifier::RemoteSecretHash(remote_secret_hash),
        }
    }
}

/// D2X configuration for the CLI.
pub struct D2xConfig {
    /// The device's D2X ID.
    pub d2x_device_id: D2xDeviceId,

    /// The device's CSP ID.
    pub csp_device_id: CspDeviceId,

    /// The device group key.
    pub device_group_key: RawDeviceGroupKey,

    /// The expected device slot state.
    pub expected_device_slot_state: protobuf::d2m::DeviceSlotState,
}
impl From<D2xConfigOptions> for Option<D2xConfig> {
    fn from(options: D2xConfigOptions) -> Self {
        if let (
            Some(d2x_device_id),
            Some(csp_device_id),
            Some(device_group_key),
            Some(expected_device_slot_state),
        ) = (
            options.d2x_device_id,
            options.csp_device_id,
            options.device_group_key,
            options.expected_device_slot_state,
        ) {
            Some(D2xConfig {
                d2x_device_id,
                csp_device_id,
                device_group_key,
                expected_device_slot_state,
            })
        } else {
            None
        }
    }
}

/// Common, CSP and D2X identity configuration for the CLI.
pub struct FullIdentityConfig {
    /// Minimal identity configuration.
    pub minimal: MinimalIdentityConfig,

    /// CSP server group.
    pub csp_server_group: ChatServerGroup,

    /// Optional CSP device cookie.
    pub csp_device_cookie: Option<DeviceCookie>,

    /// Optional D2X configuration.
    pub d2x_config: Option<D2xConfig>,
}
impl FullIdentityConfig {
    /// Create an [`FullIdentityConfig`] from [`FullIdentityConfigOptions`].
    ///
    /// Note: This is only an asynchronous process for an OnPrem configuration in which case the OPPF file
    /// will be downloaded and verified.
    ///
    /// # Errors
    ///
    /// Returns an error if the options are invalid, or in case of an OnPrem configuration in case the
    /// OPPF file could not be downloaded or verified.
    pub async fn from_options(
        http_client: &reqwest::Client,
        options: FullIdentityConfigOptions,
    ) -> anyhow::Result<Self> {
        let minimal = MinimalIdentityConfig::from_options(http_client, options.minimal).await?;
        let d2x_config: Option<D2xConfig> = options.d2x_config.into();
        Ok(Self {
            minimal,
            csp_server_group: options.csp_server_group,
            csp_device_cookie: options.csp_device_cookie,
            d2x_config,
        })
    }

    /// Generate a [`CspProtocolContextInit`] from the config.
    ///
    /// # Errors
    ///
    /// Returns an error if the configuration is invalid.
    #[must_use]
    pub fn csp_context_init(&self) -> CspProtocolContextInit {
        CspProtocolContextInit {
            permanent_server_keys: self.minimal.common.config.chat_server_public_keys.clone(),
            identity: self.minimal.user_identity,
            client_key: ClientKey::from(&self.minimal.client_key),
            client_info: ClientInfo::Libthreema,
            device_cookie: self.csp_device_cookie,
            csp_device_id: self
                .d2x_config
                .as_ref()
                .map(|d2x_config| d2x_config.csp_device_id),
        }
    }

    /// Generate a [`CspE2eContextInit`] from the config.
    #[must_use]
    pub fn csp_e2e_context_init(&self, nonce_storage: Box<RefCell<dyn NonceStorage>>) -> CspE2eContextInit {
        CspE2eContextInit {
            user_identity: self.minimal.user_identity,
            client_key: ClientKey::from(&self.minimal.client_key),
            flavor: self.minimal.common.flavor.clone(),
            nonce_storage,
        }
    }

    /// Generate a [`D2mContext`] from the config, if multi-device has been enabled by the config.
    #[must_use]
    pub fn d2m_context(&self) -> Option<D2mContext> {
        let mediator_server_url = self
            .minimal
            .common
            .config
            .multi_device
            .as_ref()
            .map(|config| config.mediator_server_url.clone())?;

        self.d2x_config.as_ref().map(|d2x_config| D2mContext {
            mediator_server_url,
            device_group_key: DeviceGroupKey::from(&d2x_config.device_group_key),
            csp_server_group: self.csp_server_group,
            device_id: d2x_config.d2x_device_id,
            device_slots_exhausted_policy:
                protobuf::d2m::client_hello::DeviceSlotsExhaustedPolicy::DropLeastRecent,
            device_slot_expiration_policy: protobuf::d2m::DeviceSlotExpirationPolicy::Persistent,
            expected_device_slot_state: d2x_config.expected_device_slot_state,
            client_info: ClientInfo::Libthreema,
            device_label: None,
        })
    }

    /// Generate a [`D2xContextInit`] from the config, if multi-device has been enabled by the config.
    #[must_use]
    pub fn d2x_context_init(&self, nonce_storage: Box<RefCell<dyn NonceStorage>>) -> Option<D2xContextInit> {
        self.d2x_config.as_ref().map(|d2x_config| D2xContextInit {
            device_id: d2x_config.d2x_device_id,
            device_group_key: DeviceGroupKey::from(&d2x_config.device_group_key),
            nonce_storage,
        })
    }
}
