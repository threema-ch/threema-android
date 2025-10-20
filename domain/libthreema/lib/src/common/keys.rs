//! Key structures (or keys moved into associated ciphers).
use core::{array::TryFromSliceError, fmt};

#[cfg(any(test, feature = "cli"))]
use anyhow;
use data_encoding::HEXLOWER;
use educe::Educe;
use libthreema_macros::{ConstantTimeEq, Name, concat_fixed_bytes};
use rand::{self, Rng as _};
use zeroize::ZeroizeOnDrop;

use crate::{
    common::ThreemaId,
    crypto::{
        blake2b,
        cipher::KeyInit as _,
        digest::{FixedOutput as _, Mac as _},
        salsa20, x25519,
    },
    utils::debug::debug_static_secret,
};

/// Key for solving authentication challenges during the CSP handshake (aka _vouch key_).
pub(crate) struct CspAuthenticationKey(pub(crate) blake2b::Blake2bMac256);

/// Cipher associated to the Message Key (MK).
pub(crate) struct MessageCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Message Metadata Key (MMK).
pub(crate) struct MessageMetadataCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Shared secret context for usage between two identities (i.e. client to client).
pub(crate) struct CspE2eKey(x25519::SharedSecretHSalsa20);
impl CspE2eKey {
    /// Get the Message Key (MK).
    ///
    /// IMPORTANT: This key should not be used any other purpose but for messages. Otherwise, it's
    /// just another story of _payload confusion_.
    #[must_use]
    pub(crate) fn message_cipher(&self) -> MessageCipher {
        MessageCipher(salsa20::XSalsa20Poly1305::new(self.0.as_bytes().into()))
    }

    /// Derive the Message Metadata Key (MMK).
    #[must_use]
    pub(crate) fn message_metadata_cipher(&self) -> MessageMetadataCipher {
        let mmk =
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(self.0.as_bytes()), b"mm", b"3ma-csp")
                .expect("Blake2bMac256 failed")
                .finalize_fixed();
        MessageMetadataCipher(salsa20::XSalsa20Poly1305::new(&mmk))
    }
}

/// Key for solving authentication challenges of the directory server.
pub(crate) struct DirectoryAuthenticationKey(pub(crate) blake2b::Blake2bMac256);

/// Key for solving authentication challenges of the work directory server.
pub(crate) struct WorkDirectoryAuthenticationKey(pub(crate) blake2b::Blake2bMac256);

/// The Client key (often internally referred to as `CK` in the code and documentation) is a 32 bytes long,
/// permanent secret key associated to the Threema ID.
///
/// IMPORTANT: This is **THE** key which requires ultimate care!
#[derive(Educe, ZeroizeOnDrop)]
#[educe(Debug)]
pub struct ClientKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);
impl ClientKey {
    /// Byte length of the client key.
    pub const LENGTH: usize = x25519::KEY_LENGTH;

    /// Sample a random client key.
    #[must_use]
    pub fn random() -> Self {
        let mut client_key = [0_u8; Self::LENGTH];
        rand::thread_rng().fill(&mut client_key);
        Self::from(client_key)
    }

    /// Get the public key associated with this client key secret.
    #[must_use]
    pub fn public_key(&self) -> PublicKey {
        PublicKey(x25519::PublicKey::from(&self.0))
    }

    /// Byte representation of the client key.
    #[must_use]
    pub fn as_bytes(&self) -> &[u8; Self::LENGTH] {
        self.0.as_bytes()
    }

    /// Derive the key to solve an authentication challenge during the CSP handshake (aka _vouch key_).
    #[must_use]
    pub(crate) fn derive_csp_authentication_key(
        &self,
        permanent_server_key: &PublicKey,
        temporary_server_key: &PublicKey,
    ) -> CspAuthenticationKey {
        // Calculate the secret as the concatenation of two shared secrets
        let secret: [u8; 2 * x25519::SharedSecretHSalsa20::LENGTH] = concat_fixed_bytes!(
            // Compute first half as X25519HSalsa20(CK.secret, SK.public)
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&permanent_server_key.0)).to_bytes(),
            // Compute second half as X25519HSalsa20(CK.secret, temporary_server_key.public)
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&temporary_server_key.0)).to_bytes(),
        );

        // Apply Blake2b to obtain the CSP authentication secret (aka _vouch key_)
        let key = blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&secret), b"v2", b"3ma-csp")
            .expect("Blake2bMac256 failed")
            .finalize()
            .into_bytes();
        CspAuthenticationKey(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&key), &[], &[])
                .expect("Blake2bMac256 failed"),
        )
    }

    /// Derive the key to solve an authentication challenge against the directory server.
    #[must_use]
    pub(crate) fn derive_directory_authentication_key(
        &self,
        challenge_public_key: &PublicKey,
    ) -> DirectoryAuthenticationKey {
        let secret = x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&challenge_public_key.0));
        let key =
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(secret.as_bytes()), b"dir", b"3ma-csp")
                .expect("Blake2bMac256 failed")
                .finalize()
                .into_bytes();
        DirectoryAuthenticationKey(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&key), &[], &[])
                .expect("Blake2bMac256 failed"),
        )
    }

    /// Derive the key to solve an authentication challenge against the work directory server.
    #[must_use]
    pub(crate) fn derive_work_directory_authentication_key(
        &self,
        challenge_public_key: &PublicKey,
    ) -> WorkDirectoryAuthenticationKey {
        let secret = x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&challenge_public_key.0));
        let key =
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(secret.as_bytes()), b"wdir", b"3ma-csp")
                .expect("Blake2bMac256 failed")
                .finalize()
                .into_bytes();
        WorkDirectoryAuthenticationKey(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&key), &[], &[])
                .expect("Blake2bMac256 failed"),
        )
    }

    /// Derive the shared secret for usage between two identities (i.e. client to client).
    #[must_use]
    pub(crate) fn derive_csp_e2e_key(&self, client_public_key: &PublicKey) -> CspE2eKey {
        CspE2eKey(x25519::SharedSecretHSalsa20::from(
            self.0.diffie_hellman(&client_public_key.0),
        ))
    }
}
impl From<[u8; Self::LENGTH]> for ClientKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::StaticSecret::from(bytes))
    }
}
#[cfg(any(test, feature = "cli"))]
impl From<&RawClientKey> for ClientKey {
    fn from(client_key: &RawClientKey) -> Self {
        Self(client_key.0.clone())
    }
}

/// Also the Client key but [`Clone`].
///
/// For example needed for the CLI which requires this struct to be [`Clone`] but we don't want
/// [`ClientKey`] to be [`Clone`].
#[cfg(any(test, feature = "cli"))]
#[derive(Clone, Educe, ZeroizeOnDrop)]
#[educe(Debug)]
pub struct RawClientKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);
#[cfg(any(test, feature = "cli"))]
impl RawClientKey {
    /// Convert a hex string to a [`ClientKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(any(test, feature = "cli"))]
    pub fn from_hex(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; ClientKey::LENGTH] = bytes.as_slice().try_into().context(format!(
            "must be {} bytes, got {}",
            ClientKey::LENGTH,
            bytes.len()
        ))?;
        Ok(Self(x25519::StaticSecret::from(bytes)))
    }
}

/// Public portion associated to an X25519 secret key.
#[derive(Clone, Copy, Eq, Hash, PartialEq, Name)]
pub struct PublicKey(pub x25519::PublicKey);
impl PublicKey {
    /// Byte length of the public portion of an X25519 secret key.
    pub const LENGTH: usize = x25519::KEY_LENGTH;

    /// Convert a hex string to a [`PublicKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(any(test, feature = "cli"))]
    pub fn from_hex(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let bytes = HEXLOWER.decode(string.as_bytes())?;
        Self::try_from(bytes.as_slice()).context(format!(
            "must be {} bytes, got {}",
            Self::LENGTH,
            bytes.len()
        ))
    }
}
impl From<&x25519::StaticSecret> for PublicKey {
    fn from(private_key: &x25519::StaticSecret) -> Self {
        Self(x25519::PublicKey::from(private_key))
    }
}
impl From<[u8; Self::LENGTH]> for PublicKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::PublicKey::from(bytes))
    }
}
impl TryFrom<&[u8]> for PublicKey {
    type Error = TryFromSliceError;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self::from(TryInto::<[u8; Self::LENGTH]>::try_into(bytes)?))
    }
}
impl fmt::Display for PublicKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(self.0.as_bytes()))
    }
}
impl fmt::Debug for PublicKey {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}

/// Cipher associated to the key computed as `X25519HSalsa20(DGPK.secret, ephemeral_server_key)`.
#[expect(dead_code, reason = "Will use later")]
pub(crate) struct DeviceGroupPathAuthenticationCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// The Device Group Path Key (DGPK)
pub(crate) struct DeviceGroupPathKey(x25519_dalek::StaticSecret);
impl DeviceGroupPathKey {
    /// Byte length of the device group key.
    pub(crate) const LENGTH: usize = 32;

    /// Get the public key associated with this client key secret.
    ///
    /// This public key is used as the Mediator Device ID.
    pub(crate) fn public_key(&self) -> PublicKey {
        PublicKey::from(&self.0)
    }

    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn authentication_cipher(
        self,
        ephemeral_server_key: &PublicKey,
    ) -> DeviceGroupPathAuthenticationCipher {
        DeviceGroupPathAuthenticationCipher(salsa20::XSalsa20Poly1305::new(
            x25519::SharedSecretHSalsa20::from(self.0.diffie_hellman(&ephemeral_server_key.0))
                .as_bytes()
                .into(),
        ))
    }
}

/// Cipher associated to the Device Group Reflect Key (DGRK).
pub(crate) struct DeviceGroupReflectCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Device Group Device Info Key (DGDIK).
#[expect(dead_code, reason = "Will use later")]
pub(crate) struct DeviceGroupDeviceInfoCipher(pub(crate) salsa20::XSalsa20Poly1305);

/// Cipher associated to the Device Group Transaction Scope Key (DGTSK).
pub(crate) struct DeviceGroupTransactionScopeCipher(pub(crate) salsa20::XSalsa20Poly1305);

#[derive(Educe, ZeroizeOnDrop)]
#[educe(Debug)]
/// The Device Group Key (DGK)
pub struct DeviceGroupKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);
impl DeviceGroupKey {
    /// Byte length of the device group key.
    pub const LENGTH: usize = 32;

    /// Sample a random Device Group Key
    #[must_use]
    pub fn random() -> Self {
        let mut device_group_key = [0_u8; Self::LENGTH];
        rand::thread_rng().fill(&mut device_group_key);
        Self::from(device_group_key)
    }

    /// Derive the Device Group Path Key (DGPK).
    #[must_use]
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn path_key(&self) -> DeviceGroupPathKey {
        let path_key: [u8; DeviceGroupPathKey::LENGTH] = self.derive_key(b"p").into();
        DeviceGroupPathKey(x25519::StaticSecret::from(path_key))
    }

    /// Derive the Device Group Reflect Key (DGRK).
    #[must_use]
    pub(crate) fn reflect_key(&self) -> DeviceGroupReflectCipher {
        DeviceGroupReflectCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"r")))
    }

    /// Derive the Device Group Device Info Key (DGDIK).
    #[must_use]
    #[expect(dead_code, reason = "Will use later")]
    pub(crate) fn device_info_key(&self) -> DeviceGroupDeviceInfoCipher {
        DeviceGroupDeviceInfoCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"di")))
    }

    /// Derive the Device Group Transaction Scope Key (DGTSK).
    #[must_use]
    pub(crate) fn transaction_scope_key(&self) -> DeviceGroupTransactionScopeCipher {
        DeviceGroupTransactionScopeCipher(salsa20::XSalsa20Poly1305::new(&self.derive_key(b"ts")))
    }

    fn derive_key(&self, salt: &[u8]) -> salsa20::Key {
        blake2b::Blake2bMac256::new_with_salt_and_personal(Some(self.0.as_bytes()), salt, b"3ma-mdev")
            .expect("Blake2bMac256 failed")
            .finalize_fixed()
    }
}
impl From<[u8; DeviceGroupKey::LENGTH]> for DeviceGroupKey {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(x25519::StaticSecret::from(bytes))
    }
}
impl TryFrom<&[u8]> for DeviceGroupKey {
    type Error = TryFromSliceError;

    fn try_from(bytes: &[u8]) -> Result<Self, Self::Error> {
        Ok(Self::from(TryInto::<[u8; Self::LENGTH]>::try_into(bytes)?))
    }
}
#[cfg(feature = "cli")]
impl From<&RawDeviceGroupKey> for DeviceGroupKey {
    fn from(device_group_key: &RawDeviceGroupKey) -> Self {
        Self(device_group_key.0.clone())
    }
}

/// Also the Device Group Key but [`Clone`].
///
/// For example needed for the CLI which requires this struct to be [`Clone`] but we don't want
/// [`DeviceGroupKey`] to be [`Clone`].
#[cfg(feature = "cli")]
#[derive(Clone, Educe, ZeroizeOnDrop)]
#[educe(Debug)]
pub struct RawDeviceGroupKey(#[educe(Debug(method(debug_static_secret)))] x25519::StaticSecret);
#[cfg(feature = "cli")]
impl RawDeviceGroupKey {
    /// Convert a hex string to a [`DeviceGroupKey`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(any(test, feature = "cli"))]
    pub fn from_hex(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; DeviceGroupKey::LENGTH] = bytes.as_slice().try_into().context(format!(
            "must be {} bytes, got {}",
            DeviceGroupKey::LENGTH,
            bytes.len()
        ))?;
        Ok(Self(x25519::StaticSecret::from(bytes)))
    }
}

/// Remote Secret Hash (RSH) derived from a Remote Secret (RS).
#[derive(Clone, ConstantTimeEq, Name)]
pub struct RemoteSecretHash(pub [u8; Self::LENGTH]);
impl RemoteSecretHash {
    /// Byte length of the remote secret hash.
    pub const LENGTH: usize = 32;

    /// Convert a hex string to a [`RemoteSecretHash`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_hex(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; Self::LENGTH] = bytes.as_slice().try_into().context(format!(
            "must be {} bytes, got {}",
            Self::LENGTH,
            bytes.len()
        ))?;
        Ok(Self(bytes))
    }

    /// Derive the associated Remote Secret Hash tied to an identity (RSHID).
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    #[must_use]
    pub fn derive_for_identity(&self, identity: ThreemaId) -> RemoteSecretHashForIdentity {
        let input: [u8; Self::LENGTH + ThreemaId::LENGTH] = concat_fixed_bytes!(self.0, identity.to_bytes());
        RemoteSecretHashForIdentity(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&input), b"rshid", b"3ma-rs")
                .expect("Blake2bMac256 failed")
                .finalize_fixed()
                .into(),
        )
    }
}
impl From<[u8; Self::LENGTH]> for RemoteSecretHash {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(bytes)
    }
}
impl fmt::Display for RemoteSecretHash {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(&self.0))
    }
}
impl fmt::Debug for RemoteSecretHash {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}

/// Remote Secret Hash tied to an identity (RSHID) derived from a Remote Secret Hash (RSH).
#[derive(Clone, ConstantTimeEq, Name)]
pub struct RemoteSecretHashForIdentity(pub [u8; Self::LENGTH]);
impl RemoteSecretHashForIdentity {
    /// Byte length of the remote secret hash tied to an identity.
    pub const LENGTH: usize = 32;
}
impl From<[u8; Self::LENGTH]> for RemoteSecretHashForIdentity {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(bytes)
    }
}
impl fmt::Display for RemoteSecretHashForIdentity {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&HEXLOWER.encode(&self.0))
    }
}
impl fmt::Debug for RemoteSecretHashForIdentity {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple(Self::NAME)
            .field(&self.to_string())
            .finish()
    }
}

/// A Remote Secret (RS).
///
/// Concrete usage depends on the implementation:
///
/// - Android/Desktop: Should be used to derive a key to be able to encrypt/decrypt the _intermediate key
///   storage_ that is sandwiched between any outer protection by the platform or a custom passphrase and the
///   keys protected by it.
/// - iOS: Should be used to derive the Wonky Field Cipher Key (WFCK) that can then be used to encrypt/decrypt
///   various pieces of data stored in the iOS keychain services as well as files on disk and protecting
///   _some_ fields in the database (hence it's name).
///
/// Note: An implementation must always make some kind of derivation and not use the secret as-is.
#[derive(Educe, ZeroizeOnDrop)]
#[educe(Debug)]
pub struct RemoteSecret(pub [u8; Self::LENGTH]);
impl RemoteSecret {
    /// Byte length of the remote secret.
    pub const LENGTH: usize = 32;

    /// Sample a random Remote Secret
    #[must_use]
    pub fn random() -> Self {
        let mut remote_secret = [0_u8; Self::LENGTH];
        rand::thread_rng().fill(&mut remote_secret);
        Self::from(remote_secret)
    }

    /// Derive the associated Remote Secret Hash (RSH).
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    #[must_use]
    pub fn derive_hash(&self) -> RemoteSecretHash {
        RemoteSecretHash(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&self.0), b"rsh", b"3ma-rs")
                .expect("Blake2bMac256 failed")
                .finalize_fixed()
                .into(),
        )
    }

    /// Derive the Wonky Field Cipher Key (WFCK).
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    #[must_use]
    pub fn wonky_field_cipher_key(&self) -> WonkyFieldCipherKey {
        WonkyFieldCipherKey(
            blake2b::Blake2bMac256::new_with_salt_and_personal(Some(&self.0), b"wfck", b"3ma-rs")
                .expect("Blake2bMac256 failed")
                .finalize_fixed()
                .into(),
        )
    }
}
impl From<[u8; Self::LENGTH]> for RemoteSecret {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(bytes)
    }
}

/// Wonky Field Cipher Key (WFCK).
///
/// This key is derived from the [`RemoteSecret`] and used solely on iOS for the wonky field encryption.
pub struct WonkyFieldCipherKey(pub(crate) [u8; Self::LENGTH]);
impl WonkyFieldCipherKey {
    /// Byte length of the Wonky Field Cipher Key.
    pub const LENGTH: usize = 32;
}

/// Remote Secret Authentication Token (RSAT) associated to a Remote Secret (RS)
#[derive(Clone, ZeroizeOnDrop)]
pub struct RemoteSecretAuthenticationToken(pub [u8; Self::LENGTH]);
impl RemoteSecretAuthenticationToken {
    /// Byte length of the remote secret hash.
    pub const LENGTH: usize = 32;

    /// Convert a hex string to a [`RemoteSecretAuthenticationToken`].
    ///
    /// # Errors
    ///
    /// Returns a string describing the error.
    #[cfg(feature = "cli")]
    pub fn from_hex(string: &str) -> anyhow::Result<Self> {
        use anyhow::Context as _;

        let bytes = HEXLOWER.decode(string.as_bytes())?;
        let bytes: [u8; Self::LENGTH] = bytes.as_slice().try_into().context(format!(
            "must be {} bytes, got {}",
            Self::LENGTH,
            bytes.len()
        ))?;
        Ok(Self(bytes))
    }
}
impl From<[u8; Self::LENGTH]> for RemoteSecretAuthenticationToken {
    fn from(bytes: [u8; Self::LENGTH]) -> Self {
        Self(bytes)
    }
}
