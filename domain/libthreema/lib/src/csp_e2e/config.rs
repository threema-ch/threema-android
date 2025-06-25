//! Configuration of the end-to-end encryption layer of the _Chat Server Protocol_.
use core::fmt;
use std::{collections::HashMap, sync::LazyLock};

use regex::Regex;

use super::contact::predefined::PredefinedContact;
use crate::{common::ThreemaId, crypto::x25519::PublicKey};

#[derive(Debug, thiserror::Error)]
pub(crate) enum UrlError {
    #[error("Invalid URL")]
    InvalidUrl,

    #[error("Unexpected protocol: {0}")]
    UnexpectedProtocol(String),
}

static URL_REGEX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^(?<protocol>[a-zA-Z:]+)//(?<host>[^\s?#&/]+)/(?<path>[^\s?#&]+/)?$")
        .expect("URL regex compilation failed")
});

#[derive(Debug, Clone)]
pub(crate) struct BaseUrl(String);
impl BaseUrl {
    pub(crate) fn new(url: String, expected_protocol: &str) -> Result<BaseUrl, UrlError> {
        let captures = URL_REGEX.captures(&url).ok_or(UrlError::InvalidUrl)?;
        let protocol = captures.name("protocol").ok_or(UrlError::InvalidUrl)?;
        if protocol.as_str() != expected_protocol {
            return Err(UrlError::UnexpectedProtocol(protocol.as_str().to_owned()));
        }
        Ok(BaseUrl(url))
    }

    pub(crate) fn path(&self, path: fmt::Arguments) -> String {
        format!("{}{}", self.0, path)
    }
}

#[derive(Clone)]
#[expect(dead_code, reason = "Will use later")]
pub(crate) struct Config {
    /// Blob mirror URL.
    pub(crate) blob_mirror_url: BaseUrl,

    /// Blob server download URL.
    pub(crate) blob_server_download_url: BaseUrl,

    /// Blob server upload URL.
    pub(crate) blob_server_upload_url: BaseUrl,

    /// Chat server hostname (for non multi-device/legacy connections).
    pub(crate) csp_hostname: String,

    /// Chat server ports (for non multi-device/legacy connections).
    pub(crate) csp_ports: Vec<u16>,

    /// Chat server public keys.
    ///
    /// Note: The first public key is considered primary. All following public keys are fallbacks.
    pub(crate) csp_public_keys: Vec<PublicKey>,

    /// Mediator server URL.
    pub(crate) d2m_url: BaseUrl,

    /// Directory server URL.
    pub(crate) directory_server_url: BaseUrl,

    /// Rendezvous server URL.
    pub(crate) rendezvous_server_url: BaseUrl,

    /// Threema Safe server URL.
    pub(crate) safe_server_url: BaseUrl,

    /// Work server URL.
    pub(crate) work_server_url: BaseUrl,

    /// Predefined contacts.
    pub(super) predefined_contacts: HashMap<ThreemaId, PredefinedContact>,
}

#[expect(dead_code, reason = "Will use later")]
impl Config {
    pub(crate) fn production() -> Self {
        Self {
            blob_mirror_url: BaseUrl::new(
                "https://blob-mirror-{device-group-id-prefix4}.threema.ch/{device-group-id-prefix8}/"
                    .to_owned(),
                "https:",
            )
            .expect("Production Blob Mirror URL invalid"),

            blob_server_download_url: BaseUrl::new(
                "https://ds-blobp-{blob-id-prefix8}.threema.ch/".to_owned(),
                "https:",
            )
            .expect("Production Blob Server download URL invalid"),

            blob_server_upload_url: BaseUrl::new("https://ds-blobp-upload.threema.ch/".to_owned(), "https:")
                .expect("Production Blob Server upload URL invalid"),

            csp_hostname: "ds.g-{server-group-prefix4}.0.threema.ch".to_owned(),

            csp_ports: vec![5222, 443],

            #[rustfmt::skip]
            csp_public_keys: vec![
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

            d2m_url: BaseUrl::new(
                "wss://mediator-{device-group-id-prefix4}.threema.ch/{device-group-id-prefix8}/".to_owned(),
                "wss:",
            )
            .expect("Production Mediator Server URL invalid"),

            directory_server_url: BaseUrl::new("https://ds-apip.threema.ch/".to_owned(), "https:")
                .expect("Production Directory Server URL invalid"),

            rendezvous_server_url: BaseUrl::new(
                "wss://rendezvous-{rendezvous-path-prefix4}.threema.ch/{rendezvous-path-prefix8}/".to_owned(),
                "wss:",
            )
            .expect("Production Rendezvous Server URL invalid"),

            safe_server_url: BaseUrl::new(
                "https://safe-{backup-id-prefix8}.threema.ch/".to_owned(),
                "https:",
            )
            .expect("Production Safe Server URL invalid"),

            work_server_url: BaseUrl::new("https://ds-apip-work.threema.ch/".to_owned(), "https:")
                .expect("Production Work Server URL invalid"),

            predefined_contacts: PredefinedContact::production(),
        }
    }

    pub(crate) fn sandbox() -> Self {
        Self {
            blob_mirror_url: BaseUrl::new(
                "https://blob-mirror-{device-group-id-prefix4}.test.threema.ch/{device-group-id-prefix8}/"
                    .to_owned(),
                "https:",
            )
            .expect("Sandbox Blob Mirror URL invalid"),

            blob_server_download_url: BaseUrl::new(
                "https://ds-blobp-{blob-id-prefix8}.test.threema.ch/".to_owned(),
                "https:",
            )
            .expect("Sandbox Blob Server download URL invalid"),

            blob_server_upload_url: BaseUrl::new(
                "https://ds-blobp-upload.test.threema.ch/".to_owned(),
                "https:",
            )
            .expect("Sandbox Blob Server upload URL invalid"),

            csp_hostname: "ds.g-{server-group-prefix4}.0.test.threema.ch".to_owned(),

            csp_ports: vec![5222, 443],

            #[rustfmt::skip]
            csp_public_keys: vec![PublicKey::from([
                0x5a, 0x98, 0xf2, 0x3d, 0xe6, 0x56, 0x05, 0xd0,
                0x50, 0xdc, 0x00, 0x64, 0xbe, 0x07, 0xdd, 0xdd,
                0x81, 0x1d, 0xa1, 0x16, 0xa5, 0x43, 0xce, 0x43,
                0xaa, 0x26, 0x87, 0xd1, 0x9f, 0x20, 0xaf, 0x3c,
            ])],

            d2m_url: BaseUrl::new(
                "wss://mediator-{device-group-id-prefix4}.test.threema.ch/{device-group-id-prefix8}/"
                    .to_owned(),
                "wss:",
            )
            .expect("Sandbox Mediator Server URL invalid"),

            directory_server_url: BaseUrl::new("https://ds-apip.test.threema.ch/".to_owned(), "https:")
                .expect("Sandbox Directory Server URL invalid"),

            rendezvous_server_url: BaseUrl::new(
                "wss://rendezvous-{rendezvous-path-prefix4}.test.threema.ch/{rendezvous-path-prefix8}/"
                    .to_owned(),
                "wss:",
            )
            .expect("Sandbox Rendezvous Server URL invalid"),

            safe_server_url: BaseUrl::new(
                "https://safe-{backup-id-prefix8}.threema.ch/".to_owned(),
                "https:",
            )
            .expect("Sandbox Safe Server URL invalid"),

            work_server_url: BaseUrl::new("https://ds-apip-work.threema.ch/".to_owned(), "https:")
                .expect("Sandbox Work Server URL invalid"),

            predefined_contacts: PredefinedContact::sandbox(),
        }
    }
}
