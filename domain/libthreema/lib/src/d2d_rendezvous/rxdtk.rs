//! Rendezvous Initiator/Responder Device Transport Key (RIDTK/RRDTK) utilities.
use blake2::digest::Mac as _;
use libthreema_macros::concat_fixed_bytes;

use super::{AuthenticationKey, EphemeralTransportKey, RendezvousPathHash, rxdak, rxdxk};
use crate::crypto::{
    blake2b::Blake2bMac256, chacha20, consts::U32, digest::FixedOutput as _, generic_array::GenericArray,
};

#[inline]
fn derive_stk(ak: &AuthenticationKey, etk: &EphemeralTransportKey) -> GenericArray<u8, U32> {
    // Derive STK from AK and ETK
    let key: [u8; 64] = concat_fixed_bytes!(ak.0, etk.0.to_bytes());
    let mut stk = GenericArray::default();
    Blake2bMac256::new_with_salt_and_personal(Some(&key), b"st", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .finalize_into(&mut stk);
    stk
}

#[inline]
fn derive_keys(
    ak: &AuthenticationKey,
    etk: &EphemeralTransportKey,
) -> (chacha20::Key, chacha20::Key, GenericArray<u8, U32>) {
    // Derive STK from AK and ETK
    let stk = derive_stk(ak, etk);

    // Derive RIDTK, RRDTK and RPH
    let ridtk = Blake2bMac256::new_with_salt_and_personal(Some(&stk), b"ridt", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .finalize_fixed();
    let rrdtk = Blake2bMac256::new_with_salt_and_personal(Some(&stk), b"rrdt", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .finalize_fixed();
    let rph = Blake2bMac256::new_with_salt_and_personal(None, b"ph", b"3ma-rendezvous")
        .expect("Blake2bMac256 failed")
        .chain_update(stk)
        .finalize_fixed();
    (ridtk, rrdtk, rph)
}

/// RID's encrypt/decrypt context used after authentication.
pub(super) struct ForRid {
    pub(super) ridtk: rxdxk::Encrypt,
    pub(super) rrdtk: rxdxk::Decrypt,
}

/// RRD's encrypt/decrypt context used after authentication.
pub(super) struct ForRrd {
    pub(super) ridtk: rxdxk::Decrypt,
    pub(super) rrdtk: rxdxk::Encrypt,
}

impl ForRid {
    #[expect(clippy::needless_pass_by_value, reason = "Prevent key re-use")]
    pub(super) fn new(
        ak: &AuthenticationKey,
        rxdak: rxdak::ForRid,
        etk: EphemeralTransportKey,
    ) -> (Self, RendezvousPathHash) {
        let (ridtk, rrdtk, rph) = derive_keys(ak, &etk);
        let rxdtk = Self {
            ridtk: rxdxk::Encrypt::new_from(rxdak.ridak, ridtk),
            rrdtk: rxdxk::Decrypt::new_from(rxdak.rrdak, rrdtk),
        };
        (rxdtk, RendezvousPathHash(rph.into()))
    }
}

impl ForRrd {
    #[expect(clippy::needless_pass_by_value, reason = "Prevent key re-use")]
    pub(super) fn new(
        ak: &AuthenticationKey,
        rxdak: rxdak::ForRrd,
        etk: EphemeralTransportKey,
    ) -> (Self, RendezvousPathHash) {
        let (ridtk, rrdtk, rph) = derive_keys(ak, &etk);
        let rxdtk = Self {
            ridtk: rxdxk::Decrypt::new_from(rxdak.ridak, ridtk),
            rrdtk: rxdxk::Encrypt::new_from(rxdak.rrdak, rrdtk),
        };
        (rxdtk, RendezvousPathHash(rph.into()))
    }
}
