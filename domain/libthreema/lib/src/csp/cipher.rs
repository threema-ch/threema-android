use tracing::{debug, warn};

use super::{
    ClientCookie, ClientSequenceNumber, Context, CspProtocolError, Extensions, ServerCookie,
    ServerSequenceNumber, TemporaryClientKey, TemporaryServerKey,
    handshake_messages::{LoginAck, LoginData, ServerChallengeResponse},
    payload::{IncomingPayload, OutgoingPayload},
};
use crate::{
    common::{ClientKey, Nonce, PublicKey},
    crypto::{
        aead::{self, AeadInPlace as _},
        blake2b::Blake2bMac256,
        cipher::KeyInit as _,
        digest::{MAC_256_LENGTH, Mac as _},
        salsa20::XSalsa20Poly1305,
        x25519,
    },
};

/// Decrypt an incoming `server-challenge-response`.
///
/// Try all permanent server keys of `context`, and returns an error iff none of them could be used
/// to decrypt the `server-challenge-response`.
pub(super) fn decrypt_server_challenge_response(
    context: &Context,
    temporary_client_key: &TemporaryClientKey,
    server_cookie: &ServerCookie,
    server_sequence_number: &mut ServerSequenceNumber,
    mut server_challenge_response_box: Vec<u8>,
) -> Result<(PublicKey, Vec<u8>), CspProtocolError> {
    // Compute the nonce once. Secure because we use different public keys for the same nonce.
    let nonce = Nonce::from_cookie_and_sequence_number(
        server_cookie.0,
        server_sequence_number.0.get_and_increment()?,
    );

    // Try to decrypt the server challenge response with all available permanent server keys
    for permanent_server_key in &context.permanent_server_keys {
        match try_decrypt_server_challenge_response_with_public_key(
            &permanent_server_key.0,
            temporary_client_key,
            &nonce,
            &mut server_challenge_response_box,
        ) {
            Ok(()) => {
                debug!(?permanent_server_key, "Selected permanent server key");
                return Ok((*permanent_server_key, server_challenge_response_box));
            },
            Err(_) => {
                warn!(mismatching_permanent_server_key = ?permanent_server_key,
                    "Decrypting server challenge box with server public key failed. \
                     Trying next one (if any)."
                );
            },
        }
    }

    // None of the permanent server keys was able to decrypt the challenge response
    Err(CspProtocolError::DecryptionFailed {
        name: ServerChallengeResponse::NAME,
    })
}

/// Decrypt an incoming `server-challenge-response` in-place for a given public key.
fn try_decrypt_server_challenge_response_with_public_key(
    permanent_server_key: &x25519_dalek::PublicKey,
    temporary_client_key: &TemporaryClientKey,
    nonce: &Nonce,
    server_challenge_response_box: &mut Vec<u8>,
) -> Result<(), aead::Error> {
    let server_hello_cipher = XSalsa20Poly1305::new(
        x25519::SharedSecretHSalsa20::from(temporary_client_key.0.diffie_hellman(permanent_server_key))
            .as_bytes()
            .into(),
    );
    server_hello_cipher.decrypt_in_place(nonce.into(), &[], server_challenge_response_box)
}

struct VouchCipher {
    temporary_server_key: TemporaryServerKey,
    temporary_client_key_public: x25519::PublicKey,
}
impl VouchCipher {
    fn vouch_session(
        &self,
        client_key: &ClientKey,
        permanent_server_key: &PublicKey,
        server_cookie: &ServerCookie,
    ) -> [u8; MAC_256_LENGTH] {
        // Obtain the CSP authentication secret (aka Vouch Key)
        let vouch_key =
            client_key.derive_csp_authentication_key(permanent_server_key, &self.temporary_server_key.0);

        // Compute the vouch from the vouch key and server_cookie ||
        // temporary_client_key_public
        Blake2bMac256::new_with_salt_and_personal(Some(&vouch_key.0), &[], &[])
            .expect("Blake2bMac256 failed")
            .chain_update(server_cookie.0.0)
            .chain_update(self.temporary_client_key_public.as_bytes())
            .finalize()
            .into_bytes()
            .into()
    }
}

pub(super) struct SessionCipher {
    client_cookie: ClientCookie,
    client_sequence_number: ClientSequenceNumber,
    server_cookie: ServerCookie,
    server_sequence_number: ServerSequenceNumber,
    cipher: XSalsa20Poly1305,
}
impl SessionCipher {
    /// Encrypt outgoing data in-place
    fn encrypt(&mut self, name: &'static str, mut data: Vec<u8>) -> Result<Vec<u8>, CspProtocolError> {
        let nonce = Nonce::from_cookie_and_sequence_number(
            self.client_cookie.0,
            self.client_sequence_number.0.get_and_increment()?,
        );
        self.cipher
            .encrypt_in_place((&nonce).into(), &[], &mut data)
            .map_err(|_| CspProtocolError::EncryptionFailed { name })?;
        Ok(data)
    }

    /// Decrypt incoming data in-place
    fn decrypt(&mut self, name: &'static str, mut data: Vec<u8>) -> Result<Vec<u8>, CspProtocolError> {
        let nonce = Nonce::from_cookie_and_sequence_number(
            self.server_cookie.0,
            self.server_sequence_number.0.get_and_increment()?,
        );
        self.cipher
            .decrypt_in_place((&nonce).into(), &[], &mut data)
            .map_err(|_| CspProtocolError::DecryptionFailed { name })?;
        Ok(data)
    }
}

pub(super) struct LoginBoxes {
    pub(super) login_data_box: Vec<u8>,
    pub(super) extensions_box: Vec<u8>,
}

pub(super) struct LoginCipher {
    vouch_cipher: VouchCipher,
    session_cipher: SessionCipher,
}
impl LoginCipher {
    /// Create the cipher needed to encrypt `login` contents
    pub(super) fn new(
        temporary_client_key: &TemporaryClientKey,
        client_cookie: ClientCookie,
        client_sequence_number: ClientSequenceNumber,
        server_cookie: ServerCookie,
        server_sequence_number: ServerSequenceNumber,
        temporary_server_key: TemporaryServerKey,
    ) -> Self {
        let temporary_client_key_public = x25519::PublicKey::from(&temporary_client_key.0);
        let session_key = x25519::SharedSecretHSalsa20::from(
            temporary_client_key.0.diffie_hellman(&temporary_server_key.0.0),
        );
        let session_cipher = SessionCipher {
            client_cookie,
            client_sequence_number,
            server_cookie,
            server_sequence_number,
            cipher: XSalsa20Poly1305::new(session_key.as_bytes().into()),
        };
        let vouch_cipher = VouchCipher {
            temporary_server_key,
            temporary_client_key_public,
        };
        Self {
            vouch_cipher,
            session_cipher,
        }
    }

    /// Dissolve the cipher, returning the wrapped [`SessionCipher`].
    pub(super) fn dissolve(self) -> SessionCipher {
        self.session_cipher
    }

    /// Create a vouch MAC for use in the `login`
    #[inline]
    pub(super) fn vouch_session(
        &self,
        client_key: &ClientKey,
        permanent_server_key: &PublicKey,
    ) -> [u8; MAC_256_LENGTH] {
        self.vouch_cipher.vouch_session(
            client_key,
            permanent_server_key,
            &self.session_cipher.server_cookie,
        )
    }

    /// Encrypt data of the `login` message in-place
    #[inline]
    pub(super) fn encrypt_login(
        &mut self,
        login_data: Vec<u8>,
        extensions: Vec<u8>,
    ) -> Result<LoginBoxes, CspProtocolError> {
        let login_data_box = self.session_cipher.encrypt(LoginData::NAME, login_data)?;
        let extensions_box = self.session_cipher.encrypt(Extensions::NAME, extensions)?;
        Ok(LoginBoxes {
            login_data_box,
            extensions_box,
        })
    }
}

pub(super) struct LoginAckCipher {
    session_cipher: SessionCipher,
}
impl LoginAckCipher {
    /// Create the cipher needed to decrypt `login-ack` contents
    pub(super) fn new(session_cipher: SessionCipher) -> LoginAckCipher {
        Self { session_cipher }
    }

    /// Dissolve the cipher, returning the wrapped [`SessionCipher`].
    pub(super) fn dissolve(self) -> SessionCipher {
        self.session_cipher
    }

    /// Decrypt data of the `login-ack` message in-place
    pub(super) fn decrypt(&mut self, login_ack_box: Vec<u8>) -> Result<Vec<u8>, CspProtocolError> {
        self.session_cipher.decrypt(LoginAck::NAME, login_ack_box)
    }
}

/// Cipher to encrypt/decrypt outgoing/incoming payloads
pub(super) struct PayloadCipher(SessionCipher);
impl PayloadCipher {
    /// Create the cipher needed to encrypt/decrypt payloads.
    pub(super) fn new(session_cipher: SessionCipher) -> Self {
        Self(session_cipher)
    }

    /// Encrypt an outgoing payload in-place
    #[inline]
    pub(super) fn encrypt_payload(&mut self, payload: Vec<u8>) -> Result<Vec<u8>, CspProtocolError> {
        self.0.encrypt(OutgoingPayload::NAME, payload)
    }

    /// Decrypt an incoming payload in-place
    #[inline]
    pub(super) fn decrypt_payload(&mut self, payload: Vec<u8>) -> Result<Vec<u8>, CspProtocolError> {
        self.0.decrypt(IncomingPayload::NAME, payload)
    }
}
