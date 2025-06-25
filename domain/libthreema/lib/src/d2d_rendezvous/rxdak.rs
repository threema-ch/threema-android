//! Rendezvous Initiator/Responder Device Authentication Key (RIDAK/RRDAK) utilities.
use super::{AuthenticationKey, rxdxk};
use crate::crypto::{blake2b::Blake2bMac256, chacha20, digest::FixedOutput as _};

#[inline]
fn derive_keys(ak: &AuthenticationKey) -> (chacha20::Key, chacha20::Key) {
    let ridak = Blake2bMac256::new_with_salt_and_personal(Some(&ak.0), b"rida", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .finalize_fixed();
    let rrdak = Blake2bMac256::new_with_salt_and_personal(Some(&ak.0), b"rrda", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .finalize_fixed();
    (ridak, rrdak)
}

/// RID's encrypt/decrypt context used for initial authentication.
pub(super) struct ForRid {
    pub(super) ridak: rxdxk::Encrypt,
    pub(super) rrdak: rxdxk::Decrypt,
}

/// RRD's encrypt/decrypt context used for initial authentication.
pub(super) struct ForRrd {
    pub(super) ridak: rxdxk::Decrypt,
    pub(super) rrdak: rxdxk::Encrypt,
}

impl ForRid {
    pub(super) fn new(ak: &AuthenticationKey, pid: u32) -> Self {
        let (ridak, rrdak) = derive_keys(ak);
        Self {
            ridak: rxdxk::Encrypt::new(pid, 1, ridak),
            rrdak: rxdxk::Decrypt::new(pid, 1, rrdak),
        }
    }
}

impl ForRrd {
    pub(super) fn new(ak: &AuthenticationKey, pid: u32) -> Self {
        let (ridak, rrdak) = derive_keys(ak);
        Self {
            ridak: rxdxk::Decrypt::new(pid, 1, ridak),
            rrdak: rxdxk::Encrypt::new(pid, 1, rrdak),
        }
    }
}
