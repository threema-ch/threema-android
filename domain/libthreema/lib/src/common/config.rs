//! Configuration for the various protocols and endpoints.
use core::fmt;
use std::{collections::HashMap, sync::LazyLock};

#[cfg(any(test, feature = "cli"))]
use anyhow;
use data_encoding::BASE64;
use duplicate::duplicate_item;
use educe::Educe;
use regex::{Captures, Regex};
use serde::Deserialize;
use strum::EnumString;
use tracing::warn;

use super::{
    BlobId, ChatServerGroup,
    keys::{DeviceGroupPathKey, PublicKey},
};
use crate::{
    common::ThreemaId,
    crypto::ed25519,
    model::contact::PredefinedContact,
    utils::{
        debug::debug_str_redacted,
        serde::{base64, from_str},
        time::Duration,
    },
};

/// Work variant credentials.
#[derive(Clone, Educe, PartialEq, Eq)]
#[educe(Debug)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
#[cfg_attr(not(feature = "uniffi"), derive(zeroize::ZeroizeOnDrop))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(rename_all = "camelCase"),
    tsify(from_wasm_abi)
)]
pub struct WorkCredentials {
    /// Work username
    #[educe(Debug(method(debug_str_redacted)))]
    pub username: String,

    /// Work password
    #[educe(Debug(method(debug_str_redacted)))]
    pub password: String,
}
impl WorkCredentials {
    /// Convert a string to [`WorkCredentials`]. Must be in the following format: `<username>:<password>`.
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(any(test, feature = "cli"))]
    pub fn from_colon_separated_str(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let (username, password) = string.split_once(':').context("Invalid work credentials")?;
        Ok(Self {
            username: username.to_owned(),
            password: password.to_owned(),
        })
    }
}

/// Work flavour of the application.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(rename_all = "kebab-case"),
    tsify(from_wasm_abi)
)]
pub enum WorkFlavor {
    /// (Normal) Work application.
    Work,

    /// OnPrem application.
    OnPrem,
}

/// Work-related context information.
#[derive(Debug, Clone, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
#[cfg_attr(
    feature = "wasm",
    derive(tsify::Tsify, serde::Deserialize),
    serde(rename_all = "camelCase"),
    tsify(from_wasm_abi)
)]
pub struct WorkContext {
    /// Work variant credentials.
    pub credentials: WorkCredentials,

    /// Work flavour of the application.
    pub flavor: WorkFlavor,
}

/// General flavour of the application.
#[derive(Clone, PartialEq, Eq)]
pub enum Flavor {
    /// Consumer application.
    Consumer,

    /// Work (or OnPrem) application.
    Work(WorkContext),
}

/// URL error.
#[derive(Debug, thiserror::Error)]
#[cfg_attr(test, derive(PartialEq))]
pub enum UrlError {
    /// URL is invalid.
    #[error("Invalid URL")]
    InvalidUrl(&'static str),

    /// URL is not a valid base URL (must end with a trailing slash)
    #[error("Invalid base URL")]
    InvalidBaseUrl(&'static str),

    /// URL uses an unexpected scheme.
    #[error("Unexpected scheme: Expected {expected} but got {actual}")]
    UnexpectedScheme {
        /// Expected scheme.
        expected: &'static str,
        /// Actual scheme.
        actual: String,
    },
}

static URL_REGEX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^(?<scheme>[a-zA-Z]+)://(?<host>[^\s?#&/]+)/(?<path>[^\s?#&]+)?$")
        .expect("URL regex compilation failed")
});

static TEMPLATE_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\{(.*?)\}").expect("Template regex compilation failed"));

fn map_placeholders<F: Fn(&'_ str) -> Option<String>>(string: &str, map_fn: F) -> String {
    TEMPLATE_REGEX
        .replace_all(string, |captures: &Captures<'_>| {
            let key = captures
                .get(1)
                .expect("Template regex should have one unnamed capture group")
                .as_str();
            map_fn(key).unwrap_or_else(|| {
                warn!(string, placeholder = key, "Ignoring unknown placeholder");
                format!("{{{key}}}")
            })
        })
        .into_owned()
}

/// A URL has the following syntax: `<scheme>://<host>/<path>`
#[derive(Debug, Clone, PartialEq, Eq)]
struct Url(String);
impl Url {
    pub(crate) fn new(url: String, expected_scheme: &'static str) -> Result<Self, UrlError> {
        let captures = URL_REGEX
            .captures(&url)
            .ok_or(UrlError::InvalidUrl("Invalid format"))?;

        // Validate scheme
        let scheme = captures
            .name("scheme")
            .expect("Capture group 'scheme' should exist");
        if scheme.as_str() != expected_scheme {
            return Err(UrlError::UnexpectedScheme {
                expected: expected_scheme,
                actual: scheme.as_str().to_owned(),
            });
        }

        Ok(Self(url))
    }

    #[inline]
    fn as_str(&self) -> &str {
        &self.0
    }

    #[inline]
    fn path(&self, path: fmt::Arguments) -> String {
        format!("{}{}", self.0, path)
    }

    #[inline]
    fn map_placeholders<F: Fn(&'_ str) -> Option<String>>(&self, map_fn: F) -> String {
        map_placeholders(&self.0, map_fn)
    }
}

/// A base URL has the following syntax: `<scheme>://<host>/<path>/`
///
/// The trailing slash is required!
#[derive(Debug, Clone, PartialEq, Eq)]
struct BaseUrl(Url);
impl BaseUrl {
    pub(crate) fn new(url: String, expected_scheme: &'static str) -> Result<Self, UrlError> {
        let url = Url::new(url, expected_scheme)?;
        if !url.0.ends_with('/') {
            return Err(UrlError::InvalidBaseUrl("Missing trailing slash"));
        }
        Ok(BaseUrl(url))
    }

    #[inline]
    fn as_str(&self) -> &str {
        &self.0.0
    }

    #[inline]
    fn path(&self, path: fmt::Arguments) -> String {
        self.0.path(path)
    }

    #[inline]
    fn map_placeholders<F: Fn(&'_ str) -> Option<String>>(&self, map_fn: F) -> String {
        self.0.map_placeholders(map_fn)
    }
}

/// An HTTPS URL has the following syntax: `https://<host>/<path>`
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(try_from = "String")]
pub struct HttpsUrl(Url);
impl TryFrom<String> for HttpsUrl {
    type Error = UrlError;

    fn try_from(url: String) -> Result<Self, Self::Error> {
        let url = Url::new(url, "https")?;
        Ok(Self(url))
    }
}

/// An HTTPS base URL has the following syntax: `https://<host>/<path>/`
///
/// The trailing slash is required!
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HttpsBaseUrl(BaseUrl);
impl TryFrom<String> for HttpsBaseUrl {
    type Error = UrlError;

    fn try_from(url: String) -> Result<Self, Self::Error> {
        let url = BaseUrl::new(url, "https")?;
        Ok(Self(url))
    }
}

/// A (secure) WebSocket base URL has the following syntax: `wss://<host>/<path>/`
///
/// The trailing slash is required!
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WssBaseUrl(BaseUrl);
impl TryFrom<String> for WssBaseUrl {
    type Error = UrlError;

    fn try_from(url: String) -> Result<Self, Self::Error> {
        let url = BaseUrl::new(url, "wss")?;
        Ok(Self(url))
    }
}

#[duplicate_item(
    url_type;
    [ HttpsBaseUrl ];
    [ WssBaseUrl ];
)]
impl<'de> Deserialize<'de> for url_type {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        struct StringVisitor;
        impl serde::de::Visitor<'_> for StringVisitor {
            type Value = url_type;

            fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
                write!(formatter, "a base URL string")
            }

            fn visit_string<E: serde::de::Error>(self, url: String) -> Result<Self::Value, E> {
                Self::Value::try_from(if url.ends_with('/') {
                    url
                } else {
                    format!("{url}/")
                })
                .map_err(serde::de::Error::custom)
            }

            fn visit_str<E: serde::de::Error>(self, url: &str) -> Result<Self::Value, E> {
                Self::visit_string(self, url.to_owned())
            }
        }
        deserializer.deserialize_string(StringVisitor)
    }
}

static ON_PREM_LICENSE_URL_REGEX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^(?<scheme>[a-zA-Z]+)://license\?(?<query>.+)$").expect("URL regex compilation failed")
});

/// OnPrem configuration URL (aka URL to the OPPF file).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OnPremConfigUrl(HttpsUrl);

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct OnPremLicense {
    pub(crate) config_url: OnPremConfigUrl,
    pub(crate) work_context: WorkContext,
}
impl OnPremLicense {
    const SCHEME: &'static str = "threemaonprem";

    pub(crate) fn from_url(url: &str) -> Result<Self, UrlError> {
        let captures = ON_PREM_LICENSE_URL_REGEX
            .captures(url)
            .ok_or(UrlError::InvalidUrl("Invalid format"))?;

        // Validate scheme
        let scheme = captures
            .name("scheme")
            .expect("Capture group 'scheme' should exist");
        if scheme.as_str() != Self::SCHEME {
            return Err(UrlError::UnexpectedScheme {
                expected: Self::SCHEME,
                actual: scheme.as_str().to_owned(),
            });
        }

        // Extract server URL and credentials from query string
        let mut config_url: Option<OnPremConfigUrl> = None;
        let mut username: Option<String> = None;
        let mut password: Option<String> = None;
        let query = form_urlencoded::parse(
            captures
                .name("query")
                .ok_or(UrlError::InvalidUrl("Capture group 'query' should exist"))?
                .as_str()
                .as_bytes(),
        );
        for (name, value) in query {
            match name.as_ref() {
                "server" => {
                    let _ = config_url.replace(OnPremConfigUrl::try_from(if value.ends_with(".oppf") {
                        value.to_string()
                    } else {
                        format!("{}/prov/config.oppf", value.trim_end_matches('/'))
                    })?);
                },

                "username" => {
                    let _ = username.replace(value.into_owned());
                },

                "password" => {
                    let _ = password.replace(value.into_owned());
                },

                _ => {},
            }
        }
        Ok(Self {
            config_url: config_url.ok_or(UrlError::InvalidUrl("Missing configuration URL"))?,
            work_context: WorkContext {
                credentials: WorkCredentials {
                    username: username.ok_or(UrlError::InvalidUrl("Missing username"))?,
                    password: password.ok_or(UrlError::InvalidUrl("Missing password"))?,
                },
                flavor: WorkFlavor::OnPrem,
            },
        })
    }
}

mod device_group_id_template_url {
    use core::fmt;

    use crate::common::{config::Url, keys::DeviceGroupPathKey};

    // IMPORTANT: These template strings must be stable because they're used in the OPPF file!
    pub(super) const PREFIX_4: &str = "deviceGroupIdPrefix4";
    pub(super) const PREFIX_8: &str = "deviceGroupIdPrefix8";

    pub(super) fn path(
        url: &Url,
        device_group_path_key: &DeviceGroupPathKey,
        path: Option<fmt::Arguments>,
    ) -> String {
        let prefix = *device_group_path_key
            .public_key()
            .0
            .as_bytes()
            .first()
            .expect("Device Group Path Key should contain at least one byte");
        let base_url = url.map_placeholders(|key| match key {
            PREFIX_4 => Some(format!("{:x}", prefix >> 4_u8)),
            PREFIX_8 => Some(format!("{prefix:x}")),
            _ => None,
        });
        match path {
            None => base_url,
            Some(path) => format!("{base_url}{path}"),
        }
    }
}

mod blob_id_template_url {
    use data_encoding::HEXLOWER;

    use crate::common::{BlobId, config::Url};

    // IMPORTANT: These template strings must be stable because they're used in the OPPF file!
    pub(super) const FULL: &str = "blobId";
    pub(super) const PREFIX_8: &str = "blobIdPrefix";

    pub(crate) fn path(url: &Url, blob_id: BlobId) -> String {
        let prefix = blob_id
            .0
            .first()
            .expect("Blob ID should contain at least one byte");
        url.map_placeholders(|key| match key {
            PREFIX_8 => Some(format!("{prefix:x}")),
            FULL => Some(HEXLOWER.encode(&blob_id.0)),
            _ => None,
        })
    }
}

mod device_group_id_and_blob_id_template_url {
    use data_encoding::HEXLOWER;

    use crate::common::{
        BlobId,
        config::{Url, blob_id_template_url, device_group_id_template_url},
        keys::DeviceGroupPathKey,
    };

    pub(crate) fn path(url: &Url, device_group_path_key: &DeviceGroupPathKey, blob_id: BlobId) -> String {
        let device_group_path_key_prefix = *device_group_path_key
            .public_key()
            .0
            .as_bytes()
            .first()
            .expect("Device Group Path Key should contain at least one byte");
        let blob_id_prefix = blob_id
            .0
            .first()
            .expect("Blob ID should contain at least one byte");
        url.map_placeholders(|key| match key {
            device_group_id_template_url::PREFIX_4 => {
                Some(format!("{:x}", device_group_path_key_prefix >> 4_u8))
            },
            device_group_id_template_url::PREFIX_8 => Some(format!("{device_group_path_key_prefix:x}")),
            blob_id_template_url::PREFIX_8 => Some(format!("{blob_id_prefix:x}")),
            blob_id_template_url::FULL => Some(HEXLOWER.encode(&blob_id.0)),
            _ => None,
        })
    }
}

/// Chat server address (for non multi-device/legacy connections) with placeholders.
#[derive(Debug, Clone, PartialEq, Eq)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Record))]
pub struct ChatServerAddress {
    /// Hostname of the chat server
    pub hostname: String,

    /// Available ports the chat server is listening on
    pub ports: Vec<u16>,
}
impl ChatServerAddress {
    // IMPORTANT: This template string must be stable because it is used in the OPPF file!
    const PREFIX_8: &'static str = "serverGroupPrefix8";

    /// Retrieve all valid CSP addresses (hostname + port combinations).
    ///
    /// # Panics
    ///
    /// If the internal placeholder could not be replaced.
    #[must_use]
    pub fn addresses(&self, server_group: ChatServerGroup) -> Vec<(String, u16)> {
        let hostname = map_placeholders(&self.hostname, |key| match key {
            Self::PREFIX_8 => Some(format!("{:x}", server_group.0)),
            _ => None,
        });
        self.ports.iter().map(|port| (hostname.clone(), *port)).collect()
    }
}

/// Directory server base URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DirectoryServerBaseUrl(HttpsBaseUrl);
impl DirectoryServerBaseUrl {
    pub(crate) fn create_identity_path(&self) -> String {
        self.0.0.path(format_args!("identity/create"))
    }

    pub(crate) fn update_work_properties_path(&self) -> String {
        self.0.0.path(format_args!("identity/update_work_info"))
    }

    pub(crate) fn request_identities_path(&self) -> String {
        self.0.0.path(format_args!("identity/fetch_bulk"))
    }
}

/// Blob server upload URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobServerUploadUrl(HttpsUrl);
impl BlobServerUploadUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self) -> &str {
        &self.0.0.0
    }
}

/// Blob server download URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobServerDownloadUrl(HttpsUrl);
impl BlobServerDownloadUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, blob_id: BlobId) -> String {
        blob_id_template_url::path(&self.0.0, blob_id)
    }
}

/// Blob server URL to mark a blob as _downloaded_.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobServerDoneUrl(HttpsUrl);
impl BlobServerDoneUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, blob_id: BlobId) -> String {
        blob_id_template_url::path(&self.0.0, blob_id)
    }
}

/// Blob server configuration
#[derive(Debug, Clone, PartialEq, Eq)]
#[expect(clippy::struct_field_names, reason = "All fields intentionally end with URL")]
pub struct BlobServerConfig {
    /// Blob server upload URL.
    pub upload_url: BlobServerUploadUrl,

    /// Blob server download URL.
    pub download_url: BlobServerDownloadUrl,

    /// Blob server URL to mark a blob as _downloaded_.
    pub done_url: BlobServerDoneUrl,
}

/// Work directory server base URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WorkServerBaseUrl(HttpsBaseUrl);
impl WorkServerBaseUrl {
    pub(crate) fn remote_secret_path(&self) -> String {
        self.0.0.path(format_args!("api-client/v1/remote-secret"))
    }

    pub(crate) fn request_contacts_path(&self) -> String {
        self.0.0.path(format_args!("identities"))
    }
}

/// Avatar base server URL for Threema Gateway IDs.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GatewayAvatarBaseServerUrl(HttpsBaseUrl);
impl GatewayAvatarBaseServerUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, threema_id: ThreemaId) -> String {
        self.0.0.path(format_args!("{threema_id}"))
    }
}

/// Threema Safe server URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SafeServerBaseUrl(HttpsBaseUrl);
impl SafeServerBaseUrl {
    // IMPORTANT: This template string must be stable because it is used in the OPPF file!
    const PREFIX_8: &'static str = "backupIdPrefix8";

    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, backup_id: &[u8], path: fmt::Arguments) -> String {
        let prefix = *backup_id
            .first()
            .expect("Backup ID should contain at least one byte");
        let base_url = self.0.0.map_placeholders(|key| match key {
            Self::PREFIX_8 => Some(format!("{prefix:x}")),
            _ => None,
        });
        format!("{base_url}{path}")
    }
}

/// Rendezvous server URL with placeholders.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RendezvousServerBaseUrl(WssBaseUrl);
impl RendezvousServerBaseUrl {
    // IMPORTANT: These template strings must be stable because they're used in the OPPF file!
    const PREFIX_4: &'static str = "rendezvousPathPrefix4";
    const PREFIX_8: &'static str = "rendezvousPathPrefix8";

    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, rendezvous_path: &[u8], path: fmt::Arguments) -> String {
        let prefix = *rendezvous_path
            .first()
            .expect("Rendezvous Path should contain at least one byte");
        let base_url = self.0.0.map_placeholders(|key| match key {
            Self::PREFIX_4 => Some(format!("{:x}", prefix >> 4_u8)),
            Self::PREFIX_8 => Some(format!("{prefix:x}")),
            _ => None,
        });
        format!("{base_url}{path}")
    }
}

/// Mediator server base URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MediatorServerBaseUrl(WssBaseUrl);
impl MediatorServerBaseUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, device_group_path_key: &DeviceGroupPathKey, path: fmt::Arguments) -> String {
        device_group_id_template_url::path(&self.0.0.0, device_group_path_key, Some(path))
    }
}

/// Blob mirror server upload URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobMirrorServerUploadUrl(HttpsUrl);
impl BlobMirrorServerUploadUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, device_group_path_key: &DeviceGroupPathKey) -> String {
        device_group_id_template_url::path(&self.0.0, device_group_path_key, None)
    }
}

/// Blob mirror server download URL.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobMirrorServerDownloadUrl(HttpsUrl);
impl BlobMirrorServerDownloadUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, device_group_path_key: &DeviceGroupPathKey, blob_id: BlobId) -> String {
        device_group_id_and_blob_id_template_url::path(&self.0.0, device_group_path_key, blob_id)
    }
}

/// Blob mirror server URL to mark a blob as _downloaded_.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BlobMirrorServerDoneUrl(HttpsUrl);
impl BlobMirrorServerDoneUrl {
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path(&self, device_group_path_key: &DeviceGroupPathKey, blob_id: BlobId) -> String {
        device_group_id_and_blob_id_template_url::path(&self.0.0, device_group_path_key, blob_id)
    }
}

/// Blob mirror server configuration
#[derive(Debug, Clone, PartialEq, Eq)]
#[expect(clippy::struct_field_names, reason = "All fields intentionally end with URL")]
pub struct BlobMirrorServerConfig {
    /// Blob mirror server upload URL.
    pub upload_url: BlobMirrorServerUploadUrl,

    /// Blob mirror server download URL.
    pub download_url: BlobMirrorServerDownloadUrl,

    /// Blob mirror server URL to mark a blob as _downloaded_.
    pub done_url: BlobMirrorServerDoneUrl,
}

/// Multi-device configuration
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MultiDeviceConfig {
    /// Rendezvous server base URL.
    pub rendezvous_server_url: RendezvousServerBaseUrl,

    /// Mediator server base URL.
    pub mediator_server_url: MediatorServerBaseUrl,

    /// Blob mirror server configuration
    pub blob_mirror_server: BlobMirrorServerConfig,
}

#[duplicate_item(
    url_type;
    [ OnPremConfigUrl ];
    [ DirectoryServerBaseUrl ];
    [ BlobServerUploadUrl ];
    [ BlobServerDownloadUrl ];
    [ BlobServerDoneUrl ];
    [ WorkServerBaseUrl ];
    [ GatewayAvatarBaseServerUrl ];
    [ SafeServerBaseUrl ];
    [ RendezvousServerBaseUrl ];
    [ MediatorServerBaseUrl ];
    [ BlobMirrorServerUploadUrl ];
    [ BlobMirrorServerDownloadUrl ];
    [ BlobMirrorServerDoneUrl ];
)]
impl url_type {
    /// String representation of the URL.
    #[must_use]
    pub fn as_str(&self) -> &str {
        self.0.0.as_str()
    }
}

#[duplicate_item(
    url_type                        inner_url_type;
    [ OnPremConfigUrl ]      [ HttpsUrl ];
    [ DirectoryServerBaseUrl ]      [ HttpsBaseUrl ];
    [ BlobServerUploadUrl ]         [ HttpsUrl ];
    [ BlobServerDownloadUrl ]       [ HttpsUrl ];
    [ BlobServerDoneUrl ]           [ HttpsUrl ];
    [ WorkServerBaseUrl ]           [ HttpsBaseUrl ];
    [ GatewayAvatarBaseServerUrl ]  [ HttpsBaseUrl ];
    [ SafeServerBaseUrl ]           [ HttpsBaseUrl ];
    [ RendezvousServerBaseUrl ]     [ WssBaseUrl ];
    [ MediatorServerBaseUrl ]       [ WssBaseUrl ];
    [ BlobMirrorServerUploadUrl ]   [ HttpsUrl ];
    [ BlobMirrorServerDownloadUrl ] [ HttpsUrl ];
    [ BlobMirrorServerDoneUrl ]     [ HttpsUrl ];
)]
impl TryFrom<String> for url_type {
    type Error = UrlError;

    fn try_from(url: String) -> Result<Self, Self::Error> {
        let url = inner_url_type::try_from(url)?;
        Ok(Self(url))
    }
}

/// Version of an OnPrem configuration.
#[derive(Debug, Clone, Copy, PartialEq, Eq, EnumString)]
#[cfg_attr(feature = "uniffi", derive(uniffi::Enum))]
pub enum OnPremConfigVersion {
    /// Initial version... kinda. Historically, backwards compatible changes have not triggered a minor
    /// version bump.
    #[strum(serialize = "1.0")]
    V1_0,
}

#[derive(Deserialize)]
struct RawOnPremChatServerConfig {
    #[serde(rename = "publicKey", with = "base64::fixed_length")]
    public_key: [u8; PublicKey::LENGTH],

    #[serde(rename = "hostname")]
    hostname: String,

    #[serde(rename = "ports")]
    ports: Vec<u16>,
}

#[derive(Deserialize)]
struct RawOnPremDirectoryServerConfig {
    #[serde(rename = "url")]
    base_url: HttpsBaseUrl,
}

#[derive(Deserialize)]
#[expect(clippy::struct_field_names, reason = "All fields intentionally end with URL")]
struct RawOnPremBlobServerConfig {
    #[serde(rename = "uploadUrl")]
    upload_url: HttpsUrl,

    #[serde(rename = "downloadUrl")]
    download_url: HttpsUrl,

    #[serde(rename = "doneUrl")]
    done_url: HttpsUrl,
}

#[derive(Deserialize)]
struct RawOnPremWorkServerConfig {
    #[serde(rename = "url")]
    base_url: HttpsBaseUrl,
}

#[derive(Deserialize)]
struct RawOnPremGatewayAvatarServerConfig {
    #[serde(rename = "url")]
    base_url: HttpsBaseUrl,
}

#[derive(Deserialize)]
struct RawOnPremSafeServerConfig {
    #[serde(rename = "url")]
    base_url: HttpsBaseUrl,
}

#[derive(Deserialize)]
struct RawOnPremRendezvousServerConfig {
    #[serde(rename = "url")]
    base_url: WssBaseUrl,
}

#[derive(Deserialize)]
struct RawOnPremMediatorAndBlobMirrorServerConfig {
    #[serde(rename = "url")]
    mediator_server_base_url: WssBaseUrl,

    #[serde(rename = "blob")]
    blob_mirror_server: RawOnPremBlobServerConfig,
}

#[derive(Deserialize)]
struct RawOnPremConfig {
    #[serde(rename = "version", deserialize_with = "from_str::deserialize")]
    version: OnPremConfigVersion,

    #[serde(rename = "signatureKey", with = "base64::fixed_length")]
    signature_key: [u8; ed25519::PUBLIC_KEY_LENGTH],

    #[serde(rename = "refresh")]
    refresh_interval_s: u32,

    #[serde(rename = "chat")]
    chat_server: RawOnPremChatServerConfig,

    #[serde(rename = "directory")]
    directory_server: RawOnPremDirectoryServerConfig,

    #[serde(rename = "blob")]
    blob_server: RawOnPremBlobServerConfig,

    #[serde(rename = "work")]
    work_server: RawOnPremWorkServerConfig,

    #[serde(rename = "avatar")]
    gateway_avatar_server: RawOnPremGatewayAvatarServerConfig,

    #[serde(rename = "safe")]
    safe_server: RawOnPremSafeServerConfig,

    #[serde(rename = "rendezvous")]
    rendezvous_server: Option<RawOnPremRendezvousServerConfig>,

    #[serde(rename = "mediator")]
    mediator_and_blob_mirror_server: Option<RawOnPremMediatorAndBlobMirrorServerConfig>,
}

/// Possible errors when parsing an OnPrem configuration.
#[derive(Debug, thiserror::Error)]
pub enum OnPremConfigError {
    /// Decoding failed.
    #[error("Decoding failed: {0}")]
    DecodingFailed(String),

    /// Signature key does not match one of the accepted ones.
    #[error("Signature key mismatch")]
    SignatureKeyMismatch,

    /// Signature is invalid.
    #[error("Invalid signature")]
    InvalidSignature,
}

/// OnPrem configuration, fetched from the OPPF endpoint.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OnPremConfig {
    /// Configuration version.
    pub version: OnPremConfigVersion,

    /// Configuration refresh interval
    pub refresh_interval: Duration,

    /// Chat server address (for non multi-device/legacy connections) with placeholders.
    pub chat_server_address: ChatServerAddress,

    /// Chat server public keys.
    ///
    /// Note: The first public key is considered primary. All following public keys are fallbacks.
    pub chat_server_public_keys: Vec<PublicKey>,

    /// Directory server URL.
    pub directory_server_url: DirectoryServerBaseUrl,

    /// Blob server configuration
    pub blob_server: BlobServerConfig,

    /// Work server URL.
    pub work_server_url: WorkServerBaseUrl,

    /// Avatar server URL for Threema Gateway IDs.
    pub gateway_avatar_server_url: GatewayAvatarBaseServerUrl,

    /// Threema Safe server URL.
    pub safe_server_url: SafeServerBaseUrl,

    /// Multi-device configuration
    pub multi_device: Option<MultiDeviceConfig>,
}
impl OnPremConfig {
    #[rustfmt::skip]
    const SIGNATURE_VERIFICATION_KEYS: [[u8; ed25519::PUBLIC_KEY_LENGTH]; 3] = [
        [
            0x7a, 0x4d, 0x6a, 0x06, 0x9e, 0x03, 0xc9, 0x19,
            0x8b, 0x2f, 0xd2, 0x79, 0xb0, 0x29, 0xac, 0x29,
            0x27, 0xf0, 0x6e, 0xc8, 0x86, 0x34, 0x1e, 0x2f,
            0x78, 0x30, 0x0e, 0x0e, 0x39, 0x30, 0x7b, 0xf9,
        ], [
            0x1e, 0xb9, 0x3c, 0x68, 0x28, 0xf0, 0x2a, 0x45,
            0xf2, 0x4a, 0xe6, 0xc8, 0xec, 0x26, 0x77, 0xcb,
            0xd4, 0xb1, 0xfa, 0x84, 0xe8, 0x10, 0x78, 0xcd,
            0x90, 0x6c, 0x3d, 0xf1, 0x64, 0x91, 0x9d, 0xe5,
        ], [
            0xe6, 0x91, 0x27, 0xd5, 0x3f, 0xf9, 0x6e, 0x17,
            0x9c, 0x35, 0x6a, 0xe9, 0xf4, 0xd8, 0x14, 0x43,
            0x07, 0x91, 0x7e, 0x05, 0x6d, 0xbb, 0xf2, 0x3c,
            0x81, 0x16, 0xf7, 0x57, 0x11, 0x8f, 0xee, 0x4e,
        ],
    ];

    /// Decode the OnPrem configuration from bytes and validate its signature.
    ///
    /// # Errors
    ///
    /// Returns [`OnPremConfigError`] when the configuration could not be decoded or the signature is
    /// invalid.
    pub fn decode_and_verify(config_and_signature: &[u8]) -> Result<Self, OnPremConfigError> {
        Self::verify_with_keys(
            &Self::SIGNATURE_VERIFICATION_KEYS,
            Self::decode(config_and_signature)?,
        )
    }

    fn decode(
        config_and_signature: &[u8],
    ) -> Result<(ed25519::Signature, &str, RawOnPremConfig), OnPremConfigError> {
        // Split configuration from signature
        let (raw_config, signature) = str::from_utf8(config_and_signature)
            .map_err(|error| OnPremConfigError::DecodingFailed(error.to_string()))?
            .trim_end_matches('\n')
            .rsplit_once('\n')
            .ok_or_else(|| {
                OnPremConfigError::DecodingFailed("Unable to split configuration from signature".to_owned())
            })?;

        // Decode signature
        let signature: [u8; ed25519::SIGNATURE_LENGTH] = BASE64
            .decode(signature.as_bytes())
            .map_err(|_| OnPremConfigError::DecodingFailed("Invalid base64 signature".to_owned()))?
            .try_into()
            .map_err(|_| OnPremConfigError::DecodingFailed("Invalid signature length".to_owned()))?;
        let signature = ed25519::Signature::from_bytes(&signature);

        // Decode JSON
        let config: RawOnPremConfig = serde_json::from_slice(raw_config.as_bytes())
            .map_err(|error| OnPremConfigError::DecodingFailed(error.to_string()))?;

        // Done
        Ok((signature, raw_config, config))
    }

    fn verify_with_keys(
        verification_keys: &[[u8; ed25519::PUBLIC_KEY_LENGTH]],
        (signature, raw_config, config): (ed25519::Signature, &str, RawOnPremConfig),
    ) -> Result<Self, OnPremConfigError> {
        // Ensure chosen signature key is valid
        if !verification_keys.contains(&config.signature_key) {
            return Err(OnPremConfigError::SignatureKeyMismatch);
        }
        let signature_key = ed25519::VerifyingKey::from_bytes(&config.signature_key)
            .map_err(|_| OnPremConfigError::DecodingFailed("Invalid signature key".to_owned()))?;

        // Convert
        let config = {
            let multi_device_config = match (config.rendezvous_server, config.mediator_and_blob_mirror_server)
            {
                (Some(rendezvous_server), Some(mediator_and_blob_mirror_server)) => Some(MultiDeviceConfig {
                    rendezvous_server_url: RendezvousServerBaseUrl(rendezvous_server.base_url),
                    mediator_server_url: MediatorServerBaseUrl(
                        mediator_and_blob_mirror_server.mediator_server_base_url,
                    ),
                    blob_mirror_server: BlobMirrorServerConfig {
                        upload_url: BlobMirrorServerUploadUrl(
                            mediator_and_blob_mirror_server.blob_mirror_server.upload_url,
                        ),
                        download_url: BlobMirrorServerDownloadUrl(
                            mediator_and_blob_mirror_server.blob_mirror_server.download_url,
                        ),
                        done_url: BlobMirrorServerDoneUrl(
                            mediator_and_blob_mirror_server.blob_mirror_server.done_url,
                        ),
                    },
                }),
                (None, None) => None,
                _ => {
                    return Err(OnPremConfigError::DecodingFailed(
                        "Incomplete multi-device configuration".to_owned(),
                    ));
                },
            };
            Self {
                version: config.version,
                refresh_interval: Duration::from_secs(config.refresh_interval_s.into()),
                chat_server_address: ChatServerAddress {
                    hostname: config.chat_server.hostname,
                    ports: config.chat_server.ports,
                },
                chat_server_public_keys: vec![PublicKey::from(config.chat_server.public_key)],
                directory_server_url: DirectoryServerBaseUrl(config.directory_server.base_url),
                blob_server: BlobServerConfig {
                    upload_url: BlobServerUploadUrl(config.blob_server.upload_url),
                    download_url: BlobServerDownloadUrl(config.blob_server.download_url),
                    done_url: BlobServerDoneUrl(config.blob_server.done_url),
                },
                work_server_url: WorkServerBaseUrl(config.work_server.base_url),
                gateway_avatar_server_url: GatewayAvatarBaseServerUrl(config.gateway_avatar_server.base_url),
                safe_server_url: SafeServerBaseUrl(config.safe_server.base_url),
                multi_device: multi_device_config,
            }
        };

        // Verify signature
        signature_key
            .verify_strict(raw_config.as_bytes(), &signature)
            .map_err(|_| OnPremConfigError::InvalidSignature)?;

        // Done
        Ok(config)
    }
}

/// Configuration environment.
#[derive(Clone)]
pub enum ConfigEnvironment {
    /// Sandbox environment
    Sandbox,

    /// Production environment
    Production,

    /// OnPrem environment
    OnPrem(Box<OnPremConfig>),
}

/// Configuration.
#[derive(Clone)]
pub struct Config {
    /// Chat server address (for non multi-device/legacy connections) with placeholders.
    pub chat_server_address: ChatServerAddress,

    /// Chat server public keys.
    ///
    /// Note: The first public key is considered primary. All following public keys are fallbacks.
    pub chat_server_public_keys: Vec<PublicKey>,

    /// Directory server URL.
    pub directory_server_url: DirectoryServerBaseUrl,

    /// Blob server configuration
    pub blob_server: BlobServerConfig,

    /// Work server URL.
    pub work_server_url: WorkServerBaseUrl,

    /// Avatar server URL for Threema Gateway IDs.
    pub gateway_avatar_server_url: GatewayAvatarBaseServerUrl,

    /// Threema Safe server URL.
    pub safe_server_url: SafeServerBaseUrl,

    /// Multi-device configuration
    pub multi_device: Option<MultiDeviceConfig>,

    /// Predefined contacts.
    pub predefined_contacts: HashMap<ThreemaId, PredefinedContact>,
}

impl Config {
    pub(crate) fn production() -> Self {
        Self {
            chat_server_address: ChatServerAddress {
                hostname: format!("ds.g-{{{}}}.0.threema.ch", ChatServerAddress::PREFIX_8),
                ports: vec![5222, 443],
            },

            #[rustfmt::skip]
            chat_server_public_keys: vec![
                PublicKey::from([
                    0x45, 0x0b, 0x97, 0x57, 0x35, 0x27, 0x9f, 0xde,
                    0xcb, 0x33, 0x13, 0x64, 0x8f, 0x5f, 0xc6, 0xee,
                    0x9f, 0xf4, 0x36, 0x0e, 0xa9, 0x2a, 0x8c, 0x17,
                    0x51, 0xc6, 0x61, 0xe4, 0xc0, 0xd8, 0xc9, 0x09,
                ]),
                PublicKey::from([
                    0xda, 0x7c, 0x73, 0x79, 0x8f, 0x97, 0xd5, 0x87,
                    0xc3, 0xa2, 0x5e, 0xbe, 0x0a, 0x91, 0x41, 0x7f,
                    0x76, 0xdb, 0xcc, 0xcd, 0xda, 0x29, 0x30, 0xe6,
                    0xa9, 0x09, 0x0a, 0xf6, 0x2e, 0xba, 0x6f, 0x15,
                ]),
            ],

            directory_server_url: DirectoryServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip.threema.ch/".to_owned())
                    .expect("Production Directory Server URL invalid"),
            ),

            blob_server: BlobServerConfig {
                upload_url: BlobServerUploadUrl(
                    HttpsUrl::try_from("https://ds-blobp-upload.threema.ch/upload".to_owned())
                        .expect("Production Blob Server upload URL invalid"),
                ),

                download_url: BlobServerDownloadUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.threema.ch/{{{}}}",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Production Blob Server download URL invalid"),
                ),

                done_url: BlobServerDoneUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.threema.ch/{{{}}}/done",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Production Blob Server download URL invalid"),
                ),
            },

            work_server_url: WorkServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip-work.threema.ch/".to_owned())
                    .expect("Production Work Server URL invalid"),
            ),

            gateway_avatar_server_url: GatewayAvatarBaseServerUrl(
                HttpsBaseUrl::try_from("https://avatar.threema.ch/".to_owned())
                    .expect("Production Gateway Avatar Server URL invalid"),
            ),

            safe_server_url: SafeServerBaseUrl(
                HttpsBaseUrl::try_from(format!(
                    "https://safe-{{{}}}.threema.ch/",
                    SafeServerBaseUrl::PREFIX_8
                ))
                .expect("Production Safe Server URL invalid"),
            ),

            multi_device: Some(MultiDeviceConfig {
                rendezvous_server_url: RendezvousServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://rendezvous-{{{}}}.threema.ch/{{{}}}/",
                        RendezvousServerBaseUrl::PREFIX_4,
                        RendezvousServerBaseUrl::PREFIX_8,
                    ))
                    .expect("Production Rendezvous Server URL invalid"),
                ),

                mediator_server_url: MediatorServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://mediator-{{{}}}.threema.ch/{{{}}}/",
                        device_group_id_template_url::PREFIX_4,
                        device_group_id_template_url::PREFIX_8,
                    ))
                    .expect("Production Mediator Server URL invalid"),
                ),

                blob_mirror_server: BlobMirrorServerConfig {
                    upload_url: BlobMirrorServerUploadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.threema.ch/{{{}}}/upload",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                        ))
                        .expect("Production Blob Mirror Server upload URL invalid"),
                    ),

                    download_url: BlobMirrorServerDownloadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.threema.ch/{{{}}}/{{{}}}",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Production Blob Server download URL invalid"),
                    ),

                    done_url: BlobMirrorServerDoneUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.threema.ch/{{{}}}/{{{}}}/done",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Production Blob Server download URL invalid"),
                    ),
                },
            }),

            predefined_contacts: PredefinedContact::production(),
        }
    }

    pub(crate) fn sandbox() -> Self {
        Self {
            chat_server_address: ChatServerAddress {
                hostname: format!("ds.g-{{{}}}.0.test.threema.ch", ChatServerAddress::PREFIX_8),
                ports: vec![5222, 443],
            },

            #[rustfmt::skip]
            chat_server_public_keys: vec![PublicKey::from([
                0x5a, 0x98, 0xf2, 0x3d, 0xe6, 0x56, 0x05, 0xd0,
                0x50, 0xdc, 0x00, 0x64, 0xbe, 0x07, 0xdd, 0xdd,
                0x81, 0x1d, 0xa1, 0x16, 0xa5, 0x43, 0xce, 0x43,
                0xaa, 0x26, 0x87, 0xd1, 0x9f, 0x20, 0xaf, 0x3c,
            ])],

            directory_server_url: DirectoryServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip.test.threema.ch/".to_owned())
                    .expect("Sandbox Directory Server URL invalid"),
            ),

            blob_server: BlobServerConfig {
                upload_url: BlobServerUploadUrl(
                    HttpsUrl::try_from("https://ds-blobp-upload.test.threema.ch/upload".to_owned())
                        .expect("Sandbox Blob Server upload URL invalid"),
                ),

                download_url: BlobServerDownloadUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.test.threema.ch/{{{}}}",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Sandbox Blob Server download URL invalid"),
                ),

                done_url: BlobServerDoneUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.test.threema.ch/{{{}}}/done",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Sandbox Blob Server download URL invalid"),
                ),
            },

            work_server_url: WorkServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip-work.test.threema.ch/".to_owned())
                    .expect("Sandbox Work Server URL invalid"),
            ),

            gateway_avatar_server_url: GatewayAvatarBaseServerUrl(
                HttpsBaseUrl::try_from("https://avatar.test.threema.ch/".to_owned())
                    .expect("Sandbox Gateway Avatar Server URL invalid"),
            ),

            safe_server_url: SafeServerBaseUrl(
                HttpsBaseUrl::try_from(format!(
                    "https://safe-{{{}}}.test.threema.ch/",
                    SafeServerBaseUrl::PREFIX_8
                ))
                .expect("Sandbox Safe Server URL invalid"),
            ),

            multi_device: Some(MultiDeviceConfig {
                rendezvous_server_url: RendezvousServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://rendezvous-{{{}}}.test.threema.ch/{{{}}}/",
                        RendezvousServerBaseUrl::PREFIX_4,
                        RendezvousServerBaseUrl::PREFIX_8,
                    ))
                    .expect("Sandbox Rendezvous Server URL invalid"),
                ),

                mediator_server_url: MediatorServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://mediator-{{{}}}.test.threema.ch/{{{}}}/",
                        device_group_id_template_url::PREFIX_4,
                        device_group_id_template_url::PREFIX_8,
                    ))
                    .expect("Sandbox Mediator Server URL invalid"),
                ),

                blob_mirror_server: BlobMirrorServerConfig {
                    upload_url: BlobMirrorServerUploadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.test.threema.ch/{{{}}}/upload",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                        ))
                        .expect("Sandbox Blob Mirror Server upload URL invalid"),
                    ),

                    download_url: BlobMirrorServerDownloadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.test.threema.ch/{{{}}}/{{{}}}",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Sandbox Blob Server download URL invalid"),
                    ),

                    done_url: BlobMirrorServerDoneUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.test.threema.ch/{{{}}}/{{{}}}/done",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Sandbox Blob Server download URL invalid"),
                    ),
                },
            }),

            predefined_contacts: PredefinedContact::sandbox(),
        }
    }

    #[cfg(test)]
    pub(crate) fn testing() -> Self {
        Self {
            chat_server_address: ChatServerAddress {
                hostname: format!("ds.g-{{{}}}.0.example.threema.ch", ChatServerAddress::PREFIX_8),
                ports: vec![5222, 443],
            },

            #[rustfmt::skip]
            chat_server_public_keys: vec![PublicKey::from([
                0x5a, 0x98, 0xf2, 0x3d, 0xe6, 0x56, 0x05, 0xd0,
                0x50, 0xdc, 0x00, 0x64, 0xbe, 0x07, 0xdd, 0xdd,
                0x81, 0x1d, 0xa1, 0x16, 0xa5, 0x43, 0xce, 0x43,
                0xaa, 0x26, 0x87, 0xd1, 0x9f, 0x20, 0xaf, 0x3c,
            ])],

            directory_server_url: DirectoryServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip.example.threema.ch/".to_owned())
                    .expect("Testing Directory Server URL invalid"),
            ),

            blob_server: BlobServerConfig {
                upload_url: BlobServerUploadUrl(
                    HttpsUrl::try_from("https://ds-blobp-upload.example.threema.ch/upload".to_owned())
                        .expect("Testing Blob Server upload URL invalid"),
                ),

                download_url: BlobServerDownloadUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.example.threema.ch/{{{}}}",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Testing Blob Server download URL invalid"),
                ),

                done_url: BlobServerDoneUrl(
                    HttpsUrl::try_from(format!(
                        "https://ds-blobp-{{{}}}.example.threema.ch/{{{}}}/done",
                        blob_id_template_url::PREFIX_8,
                        blob_id_template_url::FULL,
                    ))
                    .expect("Testing Blob Server download URL invalid"),
                ),
            },

            work_server_url: WorkServerBaseUrl(
                HttpsBaseUrl::try_from("https://ds-apip-work.example.threema.ch/".to_owned())
                    .expect("Testing Work Server URL invalid"),
            ),

            gateway_avatar_server_url: GatewayAvatarBaseServerUrl(
                HttpsBaseUrl::try_from("https://avatar.example.threema.ch/".to_owned())
                    .expect("Testing Gateway Avatar Server URL invalid"),
            ),

            safe_server_url: SafeServerBaseUrl(
                HttpsBaseUrl::try_from(format!(
                    "https://safe-{{{}}}.example.threema.ch/",
                    SafeServerBaseUrl::PREFIX_8
                ))
                .expect("Testing Safe Server URL invalid"),
            ),

            multi_device: Some(MultiDeviceConfig {
                rendezvous_server_url: RendezvousServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://rendezvous-{{{}}}.example.threema.ch/{{{}}}/",
                        RendezvousServerBaseUrl::PREFIX_4,
                        RendezvousServerBaseUrl::PREFIX_8,
                    ))
                    .expect("Testing Rendezvous Server URL invalid"),
                ),

                mediator_server_url: MediatorServerBaseUrl(
                    WssBaseUrl::try_from(format!(
                        "wss://mediator-{{{}}}.example.threema.ch/{{{}}}/",
                        device_group_id_template_url::PREFIX_4,
                        device_group_id_template_url::PREFIX_8,
                    ))
                    .expect("Testing Mediator Server URL invalid"),
                ),

                blob_mirror_server: BlobMirrorServerConfig {
                    upload_url: BlobMirrorServerUploadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.example.threema.ch/{{{}}}/upload",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                        ))
                        .expect("Testing Blob Mirror Server upload URL invalid"),
                    ),

                    download_url: BlobMirrorServerDownloadUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.example.threema.ch/{{{}}}/{{{}}}",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Testing Blob Server download URL invalid"),
                    ),

                    done_url: BlobMirrorServerDoneUrl(
                        HttpsUrl::try_from(format!(
                            "https://blob-mirror-{{{}}}.example.threema.ch/{{{}}}/{{{}}}/done",
                            device_group_id_template_url::PREFIX_4,
                            device_group_id_template_url::PREFIX_8,
                            blob_id_template_url::FULL,
                        ))
                        .expect("Testing Blob Server download URL invalid"),
                    ),
                },
            }),

            predefined_contacts: HashMap::new(),
        }
    }
}
impl From<OnPremConfig> for Config {
    fn from(config: OnPremConfig) -> Self {
        Self {
            chat_server_address: config.chat_server_address,
            chat_server_public_keys: config.chat_server_public_keys,
            directory_server_url: config.directory_server_url,
            blob_server: config.blob_server,
            work_server_url: config.work_server_url,
            gateway_avatar_server_url: config.gateway_avatar_server_url,
            safe_server_url: config.safe_server_url,
            multi_device: config.multi_device,
            predefined_contacts: HashMap::default(),
        }
    }
}
impl From<ConfigEnvironment> for Config {
    fn from(environment: ConfigEnvironment) -> Self {
        match environment {
            ConfigEnvironment::Sandbox => Self::sandbox(),
            ConfigEnvironment::Production => Self::production(),
            ConfigEnvironment::OnPrem(config) => Config::from(*config),
        }
    }
}

/// Implementations for the CLI
#[cfg(feature = "cli")]
pub mod cli {
    use anyhow::bail;

    use crate::{
        common::{ClientInfo, config::OnPremLicense},
        https,
    };

    impl OnPremLicense {
        pub(crate) async fn download_configuration(
            &self,
            http_client: &reqwest::Client,
        ) -> anyhow::Result<Vec<u8>> {
            let request = https::HttpsRequest {
                timeout: https::endpoint::TIMEOUT,
                url: self.config_url.as_str().to_owned(),
                method: https::HttpsMethod::Get,
                headers: https::endpoint::https_headers_with_authentication(&self.work_context)
                    .accept("application/json")
                    .build(&ClientInfo::Libthreema),
                body: vec![],
            };
            let response = request.send(http_client).await?;
            if response.status != 200 {
                bail!(https::endpoint::HttpsEndpointError::UnexpectedStatus(
                    response.status,
                ));
            }
            Ok(response.body)
        }
    }
}

#[cfg(test)]
mod tests {
    use assert_matches::assert_matches;
    use rstest::rstest;

    use super::*;

    mod on_prem {
        use super::*;

        // The corresponding private key is: 7b30ca0627bde879eedfd8293368a2218c1f13a7115f338ddf62bf29b2eeb189
        #[rustfmt::skip]
        const VALID_SIGNATURE_VERIFICATION_KEYS: [[u8; ed25519::PUBLIC_KEY_LENGTH]; 1] = [[
            0x8d, 0xa7, 0xb5, 0x96, 0x0c, 0x11, 0xdd, 0x6e,
            0xd8, 0xc8, 0xa8, 0x86, 0x42, 0x5b, 0x1b, 0x76,
            0xa3, 0x9b, 0x1b, 0x5d, 0xc5, 0x47, 0x51, 0x2f,
            0x8d, 0x57, 0x22, 0xd9, 0xa0, 0xcd, 0x22, 0x2f,
        ]];

        #[rustfmt::skip]
        const INVALID_SIGNATURE_VERIFICATION_KEYS: [[u8; ed25519::PUBLIC_KEY_LENGTH]; 1] = [[
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        ]];

        #[rstest]
        #[case("", UrlError::InvalidUrl("Invalid format"))]
        #[case("dflgjhdshlki", UrlError::InvalidUrl("Invalid format"))]
        #[case(
            "threemaonprem://license&username=a&password=b&server=https%3A%2F%2Ffoo",
            UrlError::InvalidUrl("Invalid format")
        )]
        #[case(
            "dreimaonprem://license?username=a&password=b&server=https%3A%2F%2Ffoo",
            UrlError::UnexpectedScheme { expected: "threemaonprem", actual: "dreimaonprem".to_owned() }
        )]
        #[case(
            "threemaonprem://license?password=b&server=https%3A%2F%2Ffoo",
            UrlError::InvalidUrl("Missing username")
        )]
        #[case(
            "threemaonprem://license?username=a&server=https%3A%2F%2Ffoo",
            UrlError::InvalidUrl("Missing password")
        )]
        #[case(
            "threemaonprem://license?username=a&password=b",
            UrlError::InvalidUrl("Missing configuration URL")
        )]
        #[case(
            "threemaonprem://license?username=a&password=b&server=http%3A%2F%2Ffoo",
            UrlError::UnexpectedScheme { expected: "https", actual: "http".to_owned() }
        )]
        fn license_url_invalid(#[case] url: &'static str, #[case] error: UrlError) {
            let result: Result<OnPremLicense, UrlError> = Err(error);
            assert_eq!(OnPremLicense::from_url(url), result);
        }

        #[rstest]
        #[case(
            "threemaonprem://license?username=n%C3%B6&password=%C3%A4%C3%B6%C3%BC%21%C2%A7%24%25%26%2F%28%\
             29%3D%3F&server=https%3A%2F%2Fonprem.example.threema.ch",
            "truncated configuration URL"
        )]
        #[case(
            "threemaonprem://license?username=n%C3%B6&password=%C3%A4%C3%B6%C3%BC%21%C2%A7%24%25%26%2F%28%\
             29%3D%3F&server=https%3A%2F%2Fonprem.example.threema.ch%2F",
            "truncated configuration URL (with non-conformant trailing slash)"
        )]
        #[case(
            "threemaonprem://license?username=n%C3%B6&password=%C3%A4%C3%B6%C3%BC%21%C2%A7%24%25%26%2F%28%\
             29%3D%3F&server=https%3A%2F%2Fonprem.example.threema.ch%2Fprov%2Fconfig.oppf",
            "explicit configuration URL"
        )]
        fn license_url_valid(#[case] url: &'static str, #[case] description: &'static str) {
            assert_eq!(
                OnPremLicense::from_url(url).unwrap(),
                OnPremLicense {
                    config_url: OnPremConfigUrl::try_from(
                        "https://onprem.example.threema.ch/prov/config.oppf".to_owned(),
                    )
                    .unwrap(),
                    work_context: WorkContext {
                        credentials: WorkCredentials {
                            username: "nö".to_owned(),
                            password: "äöü!§$%&/()=?".to_owned(),
                        },
                        flavor: WorkFlavor::OnPrem,
                    },
                },
                "with {description}",
            );
        }

        #[test]
        fn configuration_unknown_signature_key() {
            assert_matches!(
                OnPremConfig::verify_with_keys(
                    &INVALID_SIGNATURE_VERIFICATION_KEYS,
                    OnPremConfig::decode(include_bytes!("../../resources/test/on-prem/minimal.oppf"))
                        .unwrap(),
                ),
                Err(OnPremConfigError::SignatureKeyMismatch)
            );
        }

        #[test]
        fn configuration_invalid_signature() {
            let (_, raw_configuration, configuration) =
                OnPremConfig::decode(include_bytes!("../../resources/test/on-prem/minimal.oppf")).unwrap();
            let invalid_signature = ed25519::Signature::from_bytes(&[0_u8; ed25519::SIGNATURE_LENGTH]);
            assert_matches!(
                OnPremConfig::verify_with_keys(
                    &VALID_SIGNATURE_VERIFICATION_KEYS,
                    (invalid_signature, raw_configuration, configuration),
                ),
                Err(OnPremConfigError::InvalidSignature)
            );
        }

        #[test]
        fn minimal_configuration_valid() -> anyhow::Result<()> {
            let config = OnPremConfig::verify_with_keys(
                &VALID_SIGNATURE_VERIFICATION_KEYS,
                OnPremConfig::decode(include_bytes!("../../resources/test/on-prem/minimal.oppf"))?,
            )?;
            assert_eq!(
                config,
                OnPremConfig {
                    version: OnPremConfigVersion::V1_0,
                    refresh_interval: Duration::from_secs(86400),
                    chat_server_address: ChatServerAddress {
                        hostname: "chat.onprem.example.threema.ch".to_owned(),
                        ports: vec![5222, 443]
                    },
                    chat_server_public_keys: vec![PublicKey::from_hex(
                        "afdbad20737d9e0a36d6af4e959728b6c42ed5fd87c005b65a2faee8fb29e167"
                    )?],
                    directory_server_url: DirectoryServerBaseUrl::try_from(
                        "https://onprem.example.threema.ch/directory/".to_owned()
                    )?,
                    blob_server: BlobServerConfig {
                        upload_url: BlobServerUploadUrl::try_from(
                            "https://blob.onprem.example.threema.ch/blob/upload".to_owned()
                        )?,
                        download_url: BlobServerDownloadUrl::try_from(
                            "https://blob-{blobIdPrefix}.onprem.example.threema.ch/blob/{blobId}".to_owned()
                        )?,
                        done_url: BlobServerDoneUrl::try_from(
                            "https://blob-{blobIdPrefix}.onprem.example.threema.ch/blob/{blobId}/done"
                                .to_owned()
                        )?,
                    },
                    work_server_url: WorkServerBaseUrl::try_from(
                        "https://work.onprem.example.threema.ch/".to_owned()
                    )?,
                    gateway_avatar_server_url: GatewayAvatarBaseServerUrl::try_from(
                        "https://avatar.onprem.example.threema.ch/".to_owned()
                    )?,
                    safe_server_url: SafeServerBaseUrl::try_from(
                        "https://safe.onprem.example.threema.ch/".to_owned()
                    )?,
                    multi_device: None,
                }
            );
            Ok(())
        }

        #[test]
        fn full_configuration_valid() -> anyhow::Result<()> {
            let config = OnPremConfig::verify_with_keys(
                &VALID_SIGNATURE_VERIFICATION_KEYS,
                OnPremConfig::decode(include_bytes!("../../resources/test/on-prem/full.oppf"))?,
            )?;
            assert_eq!(
                config,
                OnPremConfig {
                    version: OnPremConfigVersion::V1_0,
                    refresh_interval: Duration::from_secs(86400),
                    chat_server_address: ChatServerAddress {
                        hostname: "chat.onprem.example.threema.ch".to_owned(),
                        ports: vec![5222, 443]
                    },
                    chat_server_public_keys: vec![PublicKey::from_hex(
                        "afdbad20737d9e0a36d6af4e959728b6c42ed5fd87c005b65a2faee8fb29e167"
                    )?],
                    directory_server_url: DirectoryServerBaseUrl::try_from(
                        "https://onprem.example.threema.ch/directory/".to_owned()
                    )?,
                    blob_server: BlobServerConfig {
                        upload_url: BlobServerUploadUrl::try_from(
                            "https://blob.onprem.example.threema.ch/blob/upload".to_owned()
                        )?,
                        download_url: BlobServerDownloadUrl::try_from(
                            "https://blob-{blobIdPrefix}.onprem.example.threema.ch/blob/{blobId}".to_owned()
                        )?,
                        done_url: BlobServerDoneUrl::try_from(
                            "https://blob-{blobIdPrefix}.onprem.example.threema.ch/blob/{blobId}/done"
                                .to_owned()
                        )?,
                    },
                    work_server_url: WorkServerBaseUrl::try_from(
                        "https://work.onprem.example.threema.ch/".to_owned()
                    )?,
                    gateway_avatar_server_url: GatewayAvatarBaseServerUrl::try_from(
                        "https://avatar.onprem.example.threema.ch/".to_owned()
                    )?,
                    safe_server_url: SafeServerBaseUrl::try_from(
                        "https://safe.onprem.example.threema.ch/".to_owned()
                    )?,
                    multi_device: Some(MultiDeviceConfig {
                        rendezvous_server_url: RendezvousServerBaseUrl::try_from(
                            "wss://rendezvous.onprem.example.threema.ch/".to_owned()
                        )?,
                        mediator_server_url: MediatorServerBaseUrl::try_from(
                            "wss://mediator.onprem.example.threema.ch/".to_owned()
                        )?,
                        blob_mirror_server: BlobMirrorServerConfig {
                            upload_url: BlobMirrorServerUploadUrl::try_from(
                                "https://blob-mirror.onprem.example.threema.ch/blob/upload".to_owned()
                            )?,
                            download_url: BlobMirrorServerDownloadUrl::try_from(
                                "https://blob-mirror.onprem.example.threema.ch/blob/{blobId}".to_owned()
                            )?,
                            done_url: BlobMirrorServerDoneUrl::try_from(
                                "https://blob-mirror.onprem.example.threema.ch/blob/{blobId}/done".to_owned()
                            )?,
                        },
                    }),
                }
            );
            Ok(())
        }
    }
}
