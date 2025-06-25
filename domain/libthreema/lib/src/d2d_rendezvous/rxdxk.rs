//! Shared encryption/decryption utilities for RIDAK/RRDAK and RIDTK/RRDTK.
use libthreema_macros::concat_fixed_bytes;

use super::RendezvousProtocolError;
use crate::crypto::{
    aead::{AeadInPlace as _, Buffer},
    chacha20::{self, ChaCha20Poly1305},
    cipher::KeyInit as _,
};

#[inline]
fn prepare_nonce(pid: u32, sequence_number: u32) -> [u8; 12] {
    // Nonce construction: u32-le(PID) || u32-le(SN) || <4 zero bytes>
    concat_fixed_bytes!(
        u32::to_le_bytes(pid),
        u32::to_le_bytes(sequence_number),
        [0_u8; 4]
    )
}

#[inline]
fn increase_sn(sn: u32) -> Result<u32, RendezvousProtocolError> {
    sn.checked_add(1)
        .ok_or(RendezvousProtocolError::SequenceNumberOverflow)
}

pub(super) struct Base {
    pid: u32,
    sequence_number: u32,
}

/// Common encryption scheme.
pub(super) struct Encrypt {
    base: Base,
    cipher: ChaCha20Poly1305,
}

/// Common decryption scheme.
pub(super) struct Decrypt {
    base: Base,
    cipher: ChaCha20Poly1305,
}

impl Encrypt {
    pub(super) fn new(pid: u32, sequence_number: u32, key: chacha20::Key) -> Self {
        let cipher = ChaCha20Poly1305::new(&key);
        Self {
            base: Base { pid, sequence_number },
            cipher,
        }
    }

    #[expect(clippy::needless_pass_by_value, reason = "Prevent key re-use")]
    pub(super) fn new_from(current: Encrypt, key: chacha20::Key) -> Self {
        Self::new(current.base.pid, current.base.sequence_number, key)
    }

    pub(super) fn encrypt(&mut self, data: &mut dyn Buffer) -> Result<(), RendezvousProtocolError> {
        // Prepare the nonce
        let nonce = prepare_nonce(self.base.pid, self.base.sequence_number);

        // Encrypt
        self.cipher
            .encrypt_in_place(&nonce.into(), &[], data)
            .map_err(|_| RendezvousProtocolError::EncryptionFailed)?;

        // Increase sequence number
        self.base.sequence_number = increase_sn(self.base.sequence_number)?;

        Ok(())
    }
}

impl Decrypt {
    pub(super) fn new(pid: u32, sequence_number: u32, key: chacha20::Key) -> Self {
        let cipher = ChaCha20Poly1305::new(&key);
        Self {
            base: Base { pid, sequence_number },
            cipher,
        }
    }

    #[expect(clippy::needless_pass_by_value, reason = "Prevent key re-use")]
    pub(super) fn new_from(current: Decrypt, key: chacha20::Key) -> Self {
        Self::new(current.base.pid, current.base.sequence_number, key)
    }

    pub(super) fn decrypt(&mut self, data: &mut dyn Buffer) -> Result<(), RendezvousProtocolError> {
        // Prepare the nonce
        let nonce = prepare_nonce(self.base.pid, self.base.sequence_number);

        // Decrypt
        self.cipher
            .decrypt_in_place(&nonce.into(), &[], data)
            .map_err(|_| RendezvousProtocolError::DecryptionFailed)?;

        // Increase sequence number
        self.base.sequence_number = increase_sn(self.base.sequence_number)?;

        Ok(())
    }
}
