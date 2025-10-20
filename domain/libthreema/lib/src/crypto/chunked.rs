//! Implement chunked encryption of XChaCha20Poly1305 and XSalsa20Poly1305.
//!
//! The provided API should be mainly used for large chunks of data, e.g. blobs or backups.
use libthreema_macros::concat_fixed_bytes;
use zeroize::Zeroizing;

use crate::crypto::{
    chacha20,
    cipher::{KeyIvInit as _, StreamCipher as _, StreamCipherSeek as _},
    poly1305, salsa20,
    subtle::ConstantTimeEq as _,
};

/// Invalid Tag (aka Message Authentication Code or MAC)
#[derive(thiserror::Error, Debug)]
#[error("Invalid tag")]
pub struct InvalidTag;

pub(super) struct ChunkedXChaCha20Poly1305Cipher {
    cipher: chacha20::XChaCha20,
    mac: poly1305::ChunkedPoly1305,
    associated_data_length: u64,
    ciphertext_length: u64,
}
impl ChunkedXChaCha20Poly1305Cipher {
    #[inline]
    fn new(
        key: &[u8; chacha20::KEY_LENGTH],
        nonce: &[u8; chacha20::NONCE_LENGTH],
        associated_data: &[u8],
    ) -> Self {
        use crate::crypto::poly1305::ChunkedPoly1305XChaCha20 as _;

        let mut cipher = chacha20::XChaCha20::new(key.into(), nonce.into());

        // Derive Poly1305 key from the first 32 bytes of the keystream.
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.6
        let mut mac_key = Zeroizing::new(poly1305::Key::default());
        cipher.apply_keystream(&mut mac_key);

        // Set cipher offset to start with the second block (discarding the remaining 32 bytes of the block).
        // This sets the cipher _counter_ to `1`.
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.8
        cipher.seek(64_usize);

        // Add associated data and `padding1` to the MAC early since we need this at the beginning of the
        // hash.
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.8
        let mut mac = poly1305::ChunkedPoly1305::new(&mac_key);
        mac.update(associated_data);
        mac.zeropad_pending_block();

        Self {
            cipher,
            mac,
            associated_data_length: associated_data.len() as u64,
            ciphertext_length: 0,
        }
    }

    #[inline]
    fn finalize(mut self) -> poly1305::Tag {
        use crate::crypto::poly1305::ChunkedPoly1305XChaCha20 as _;

        // Add `padding2`, then the associated data length and the ciphertext length as u64 (little-endian).
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.8
        self.mac.zeropad_pending_block();
        let length_fields: [u8; 16] = concat_fixed_bytes!(
            self.associated_data_length.to_le_bytes(),
            self.ciphertext_length.to_le_bytes()
        );
        self.mac.update(&length_fields);

        self.mac.finalize_complete_block()
    }
}

/// Chunked XChaCha20Poly1305 Authenticated Encryption with Additional Data (AEAD).
///
/// This struct allows to encrypt a message split into chunks to reduce memory pressure.
pub struct ChunkedXChaCha20Poly1305Encryptor(ChunkedXChaCha20Poly1305Cipher);
impl ChunkedXChaCha20Poly1305Encryptor {
    /// Create a new chunked XChaCha20 encryptor for the given `key`, `nonce` and `associated_data`.
    #[inline]
    #[must_use]
    pub fn new(
        key: &[u8; chacha20::KEY_LENGTH],
        nonce: &[u8; chacha20::NONCE_LENGTH],
        associated_data: &[u8],
    ) -> Self {
        Self(ChunkedXChaCha20Poly1305Cipher::new(key, nonce, associated_data))
    }

    /// Encrypt a chunk.
    ///
    /// Note: To ensure good performance, the chunk should always be a multiple of 16 bytes. To balance
    /// function call overhead with memory pressure, 1 MiB chunks are recommended.
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    #[inline]
    pub fn encrypt(&mut self, chunk: &mut [u8]) {
        // Encrypt and add ciphertext to the MAC.
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.8
        self.0.cipher.apply_keystream(chunk);
        self.0.mac.update(chunk);
        self.0.ciphertext_length = self
            .0
            .ciphertext_length
            .checked_add(chunk.len() as u64)
            .expect("Total ciphertext length should not exceed u64");
    }

    /// Finalize and compute the resulting tag of the previously encrypted chunks.
    #[inline]
    #[must_use]
    pub fn finalize(self) -> [u8; chacha20::TAG_LENGTH] {
        self.0.finalize().into()
    }
}

/// Chunked XChaCha20Poly1305 Decryption.
///
/// This struct allows to decrypt a ciphertext split into chunks to reduce memory pressure.
///
/// IMPORTANT: Do not use this API unless you're absolutely sure you need it. Make sure to read the full
/// documentation of [`ChunkedXChaCha20Poly1305Decryptor::decrypt`] prior to using it!
pub struct ChunkedXChaCha20Poly1305Decryptor(ChunkedXChaCha20Poly1305Cipher);
impl ChunkedXChaCha20Poly1305Decryptor {
    /// Create a new chunked XChaCha20 decryptor for the given `key`, `nonce` and `associated_data`.
    #[inline]
    #[must_use]
    pub fn new(
        key: &[u8; chacha20::KEY_LENGTH],
        nonce: &[u8; chacha20::NONCE_LENGTH],
        associated_data: &[u8],
    ) -> Self {
        Self(ChunkedXChaCha20Poly1305Cipher::new(key, nonce, associated_data))
    }

    /// Decrypt a chunk.
    ///
    /// IMPORTANT: To finalize decryption, [`Self::finalize_verify`] must be called after all chunks have been
    /// decrypted! Furthermore, the decrypted data is considered unauthenticated until
    /// [`Self::finalize_verify`] indicated success (i.e. a valid MAC). Decrypted data that was not yet
    /// authenticated or failed the authentication check must not be used!
    ///
    /// Note: To ensure good performance, the chunk should always be a multiple of 16 bytes. To balance
    /// function call overhead with memory pressure, 1 MiB chunks are recommended.
    #[expect(clippy::missing_panics_doc, reason = "Panic will never happen")]
    #[inline]
    pub fn decrypt(&mut self, chunk: &mut [u8]) {
        // Add ciphertext to the MAC and decrypt.
        //
        // See: https://datatracker.ietf.org/doc/html/rfc8439#section-2.8
        self.0.mac.update(chunk);
        self.0.cipher.apply_keystream(chunk);
        self.0.ciphertext_length = self
            .0
            .ciphertext_length
            .checked_add(chunk.len() as u64)
            .expect("Total ciphertext length should not exceed u64");
    }

    /// Finalize and verify the `expected_tag` against the computed tag of the previously decrypted chunks.
    ///
    /// # Errors
    ///
    /// Returns an error in case the tag does not match.
    #[inline]
    pub fn finalize_verify(self, expected_tag: &[u8; chacha20::TAG_LENGTH]) -> Result<(), InvalidTag> {
        let actual_tag: [u8; chacha20::TAG_LENGTH] = self.0.finalize().into();
        if actual_tag.ct_eq(expected_tag).into() {
            Ok(())
        } else {
            Err(InvalidTag)
        }
    }
}

struct ChunkedXSalsa20Poly1305Cipher {
    cipher: salsa20::XSalsa20,
    mac: poly1305::ChunkedPoly1305,
}
impl ChunkedXSalsa20Poly1305Cipher {
    #[inline]
    fn new(key: &[u8; salsa20::KEY_LENGTH], nonce: &[u8; salsa20::NONCE_LENGTH]) -> Self {
        let mut cipher = salsa20::XSalsa20::new(key.into(), nonce.into());

        // Derive Poly1305 key from the first 32 bytes of the keystream.
        let mut mac_key = Zeroizing::new(poly1305::Key::default());
        cipher.apply_keystream(&mut mac_key);

        Self {
            cipher,
            mac: poly1305::ChunkedPoly1305::new(&mac_key),
        }
    }

    /// Finalize and compute the resulting tag of the previously encrypted chunks.
    #[inline]
    fn finalize(self) -> poly1305::Tag {
        use crate::crypto::poly1305::ChunkedPoly1305XSalsa20 as _;

        self.mac.finalize_unpadded()
    }
}

/// Chunked XSalsa20Poly1305 Authenticated Encryption.
///
/// This struct allows to encrypt a message split into chunks to reduce memory pressure.
pub struct ChunkedXSalsa20Poly1305Encryptor(ChunkedXSalsa20Poly1305Cipher);
impl ChunkedXSalsa20Poly1305Encryptor {
    /// Create a new chunked XSalsa20 encryptor for the given `key` and `nonce`.
    #[inline]
    #[must_use]
    pub fn new(key: &[u8; salsa20::KEY_LENGTH], nonce: &[u8; salsa20::NONCE_LENGTH]) -> Self {
        Self(ChunkedXSalsa20Poly1305Cipher::new(key, nonce))
    }

    /// Encrypt a chunk.
    ///
    /// Ensure to call [`Self::finalize`] to obatain the message authentication code (MAC aka tag) that
    /// provides integrity of the ciphertext.
    ///
    /// Note: To ensure good performance, the chunk should always be a multiple of 16 bytes. To balance
    /// function call overhead with memory pressure, 1 MiB chunks are recommended.
    #[inline]
    pub fn encrypt(&mut self, chunk: &mut [u8]) {
        // Encrypt and add ciphertext to the MAC
        self.0.cipher.apply_keystream(chunk);
        self.0.mac.update(chunk);
    }

    /// Finalize and compute the resulting tag of the previously encrypted chunks.
    #[inline]
    #[must_use]
    pub fn finalize(self) -> [u8; salsa20::TAG_LENGTH] {
        self.0.finalize().into()
    }
}

/// Chunked XSalsa20Poly1305 Decryption.
///
/// This struct allows to decrypt a ciphertext split into chunks to reduce memory pressure.
///
/// IMPORTANT: Do not use this API unless you're absolutely sure you need it. Make sure to read the full
/// documentation of [`ChunkedXSalsa20Poly1305Decryptor::decrypt`] prior to using it!
pub struct ChunkedXSalsa20Poly1305Decryptor(ChunkedXSalsa20Poly1305Cipher);
impl ChunkedXSalsa20Poly1305Decryptor {
    /// Create a new chunked XSalsa20 decryptor for the given `key` and `nonce`.
    #[inline]
    #[must_use]
    pub fn new(key: &[u8; salsa20::KEY_LENGTH], nonce: &[u8; salsa20::NONCE_LENGTH]) -> Self {
        Self(ChunkedXSalsa20Poly1305Cipher::new(key, nonce))
    }

    /// Decrypt a chunk.
    ///
    /// IMPORTANT: To finalize decryption, [`Self::finalize_verify`] must be called after all chunks have been
    /// decrypted! Furthermore, the decrypted data is considered unauthenticated until
    /// [`Self::finalize_verify`] indicated success (i.e. a valid MAC). Decrypted data that was not yet
    /// authenticated or failed the authentication check must not be used!
    ///
    /// Note: To ensure good performance, the chunk should always be a multiple of 16 bytes. To balance
    /// function call overhead with memory pressure, 1 MiB chunks are recommended.
    #[inline]
    pub fn decrypt(&mut self, chunk: &mut [u8]) {
        // Add ciphertext to the MAC and decrypt
        self.0.mac.update(chunk);
        self.0.cipher.apply_keystream(chunk);
    }

    /// Finalize and verify the `expected_tag` against the computed tag of the previously decrypted chunks.
    ///
    /// # Errors
    ///
    /// Returns an error in case the tag does not match.
    #[inline]
    pub fn finalize_verify(self, expected_tag: &[u8; salsa20::TAG_LENGTH]) -> Result<(), InvalidTag> {
        let actual_tag = self.0.finalize();
        if actual_tag.ct_eq(expected_tag).into() {
            Ok(())
        } else {
            Err(InvalidTag)
        }
    }
}

#[cfg(test)]
mod tests {

    use super::*;

    mod xchacha20poly1305 {
        use data_encoding::HEXLOWER;
        use rstest::rstest;
        use rstest_reuse::{apply, template};

        use super::{ChunkedXChaCha20Poly1305Decryptor, ChunkedXChaCha20Poly1305Encryptor};
        use crate::crypto::{aead::AeadInPlace as _, chacha20, cipher::KeyInit as _};

        struct TestCase {
            plaintext: Vec<u8>,
            associated_data: Vec<u8>,
            key: [u8; chacha20::KEY_LENGTH],
            nonce: [u8; chacha20::NONCE_LENGTH],
            reference_ciphertext: Vec<u8>,
            reference_tag: [u8; chacha20::TAG_LENGTH],
        }

        impl TestCase {
            fn new(associated_data_length: usize, plaintext_length: usize) -> Self {
                let plaintext = vec![0; plaintext_length];
                let associated_data = vec![0; associated_data_length];
                let key = [0xee_u8; chacha20::KEY_LENGTH];
                let nonce = [0xaa_u8; chacha20::NONCE_LENGTH];

                let (reference_ciphertext, reference_tag): (_, [u8; chacha20::TAG_LENGTH]) = {
                    let mut buffer = plaintext.clone();
                    let tag = chacha20::XChaCha20Poly1305::new((&key).into())
                        .encrypt_in_place_detached((&nonce).into(), &associated_data, &mut buffer)
                        .expect("Reference XChaCha20Poly1305 encryption should not fail");
                    (buffer, tag.into())
                };
                Self {
                    plaintext,
                    associated_data,
                    key,
                    nonce,
                    reference_ciphertext,
                    reference_tag,
                }
            }

            fn test_encryption(self, chunk_size: usize, interleave_zero_byte_chunks: bool) {
                let (actual_ciphertext, actual_tag) = {
                    let mut buffer = self.plaintext.clone();
                    let mut cipher =
                        ChunkedXChaCha20Poly1305Encryptor::new(&self.key, &self.nonce, &self.associated_data);
                    for chunk in buffer.chunks_mut(chunk_size) {
                        cipher.encrypt(chunk);
                        if interleave_zero_byte_chunks {
                            cipher.encrypt(&mut []);
                        }
                    }
                    (buffer, cipher.finalize())
                };

                // Compare results
                assert_eq!(
                    actual_ciphertext, self.reference_ciphertext,
                    "ciphertexts do not match"
                );
                assert_eq!(actual_tag, self.reference_tag, "tags do not match");
            }

            fn test_decryption(mut self, chunk_size: usize, interleave_zero_byte_chunks: bool) {
                let recovered_plaintext = {
                    let mut cipher =
                        ChunkedXChaCha20Poly1305Decryptor::new(&self.key, &self.nonce, &self.associated_data);
                    for chunk in self.reference_ciphertext.chunks_mut(chunk_size) {
                        cipher.decrypt(chunk);
                        if interleave_zero_byte_chunks {
                            cipher.decrypt(&mut []);
                        }
                    }
                    cipher
                        .finalize_verify(&self.reference_tag)
                        .expect("Authentication should pass");
                    self.reference_ciphertext
                };

                // Compare results
                assert_eq!(
                    self.plaintext, recovered_plaintext,
                    "Recovered plaintext does not match expected one"
                );
            }
        }

        #[template]
        #[rstest]
        fn xchacha20poly1305_template(
            #[values(0, 1, 15, 16, 17, 30, 31, 32)] associated_data_length: usize,
            #[values(0, 1, 15, 16, 17, 30, 31, 32, 63, 64, 65, 123, 666, 999)] plaintext_length: usize,
            #[values(1, 15, 16, 1024, 1039, 104857, 104858)] chunk_size: usize,
            #[values(false, true)] interleave_zero_byte_chunks: bool,
        ) {
        }

        #[apply(xchacha20poly1305_template)]
        fn xchacha20poly1305_encryption_lengths(
            associated_data_length: usize,
            plaintext_length: usize,
            chunk_size: usize,
            interleave_zero_byte_chunks: bool,
        ) {
            TestCase::new(associated_data_length, plaintext_length)
                .test_encryption(chunk_size, interleave_zero_byte_chunks);
        }

        /// Implements test vector A.3 of draft-irtf-cfrg-xchacha-03
        /// See <https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-xchacha#appendix-A.3>
        #[test]
        fn xchacha20poly1305_encryption_rfc() {
            TestCase {
                plaintext: HEXLOWER
                    .decode(
                        b"4c616469657320616e642047656e746c656d656e206f662074686520636c6173\
                          73206f66202739393a204966204920636f756c64206f6666657220796f75206f\
                          6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73\
                          637265656e20776f756c642062652069742e",
                    )
                    .expect("plaintext should be hex encoded"),
                associated_data: HEXLOWER
                    .decode(b"50515253c0c1c2c3c4c5c6c7")
                    .expect("associated data should be hex encoded"),
                nonce: HEXLOWER
                    .decode(b"404142434445464748494a4b4c4d4e4f5051525354555657")
                    .expect("nonce should be hex encoded")
                    .try_into()
                    .expect("nonce should have valid length"),
                key: HEXLOWER
                    .decode(b"808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
                    .expect("key should be hex encoded")
                    .try_into()
                    .expect("key should have valid length"),
                reference_ciphertext: HEXLOWER
                    .decode(
                        b"bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb\
                          731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452\
                          2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9\
                          21f9664c97637da9768812f615c68b13b52e",
                    )
                    .expect("reference ciphertext should be hex encoded"),

                reference_tag: HEXLOWER
                    .decode(b"c0875924c1c7987947deafd8780acf49")
                    .expect("tag should be hex encoded")
                    .try_into()
                    .expect("tag should have valid length"),
            }
            .test_encryption(100, false);
        }

        #[apply(xchacha20poly1305_template)]
        fn xchacha20poly1305_decryption(
            associated_data_length: usize,
            plaintext_length: usize,
            chunk_size: usize,
            interleave_zero_byte_chunks: bool,
        ) {
            TestCase::new(associated_data_length, plaintext_length)
                .test_decryption(chunk_size, interleave_zero_byte_chunks);
        }

        #[test]
        #[should_panic(expected = "Authentication should pass: InvalidTag")]
        fn xchacha20poly1305_decryption_wrong_tag() {
            let mut test_case = TestCase::new(32, 100);

            // Change tag to make verification fail
            test_case.reference_tag = [0; chacha20::TAG_LENGTH];

            test_case.test_decryption(16, false);
        }

        #[test]
        #[should_panic(expected = "Authentication should pass: InvalidTag")]
        fn xchacha20poly1305_decryption_aad() {
            let mut test_case = TestCase::new(32, 100);

            // Remove the associated data to make verification fail
            test_case.associated_data = vec![];

            test_case.test_decryption(16, false);
        }

        #[test]
        #[should_panic(expected = "Authentication should pass: InvalidTag")]
        fn xchacha20poly1305_swap_aad_ciphertext() {
            let mut test_case = TestCase::new(32, 100);

            (test_case.associated_data, test_case.plaintext) =
                (test_case.plaintext, test_case.associated_data);

            test_case.test_decryption(16, false);
        }
    }

    mod xsalsa20poly1305 {
        use data_encoding::HEXLOWER;
        use rstest::rstest;
        use rstest_reuse::{apply, template};

        use super::{ChunkedXSalsa20Poly1305Decryptor, ChunkedXSalsa20Poly1305Encryptor};
        use crate::crypto::{aead::AeadInPlace as _, cipher::KeyInit as _, salsa20};
        struct TestCase {
            plaintext: Vec<u8>,
            key: [u8; salsa20::KEY_LENGTH],
            nonce: [u8; salsa20::NONCE_LENGTH],
            reference_ciphertext: Vec<u8>,
            reference_tag: [u8; salsa20::TAG_LENGTH],
        }

        impl TestCase {
            fn new(plaintext_length: usize) -> Self {
                let plaintext = vec![0; plaintext_length];
                let key = [0xee_u8; salsa20::KEY_LENGTH];
                let nonce = [0xaa_u8; salsa20::NONCE_LENGTH];

                let (reference_ciphertext, reference_tag): (_, [u8; salsa20::TAG_LENGTH]) = {
                    let mut buffer = plaintext.clone();
                    let tag = salsa20::XSalsa20Poly1305::new((&key).into())
                        .encrypt_in_place_detached((&nonce).into(), &[], &mut buffer)
                        .expect("Reference XSalsa20Poly1305 encryption should not fail");
                    (buffer, tag.into())
                };
                Self {
                    plaintext,
                    key,
                    nonce,
                    reference_ciphertext,
                    reference_tag,
                }
            }

            fn test_encryption(self, chunk_size: usize, interleave_zero_byte_chunks: bool) {
                let (actual_ciphertext, actual_tag) = {
                    let mut buffer = self.plaintext.clone();
                    let mut cipher = ChunkedXSalsa20Poly1305Encryptor::new(&self.key, &self.nonce);
                    for chunk in buffer.chunks_mut(chunk_size) {
                        cipher.encrypt(chunk);
                        if interleave_zero_byte_chunks {
                            cipher.encrypt(&mut []);
                        }
                    }
                    (buffer, cipher.finalize())
                };

                // Compare results
                assert_eq!(
                    actual_ciphertext, self.reference_ciphertext,
                    "ciphertexts do not match"
                );
                assert_eq!(actual_tag, self.reference_tag, "tags do not match");
            }

            fn test_decryption(mut self, chunk_size: usize, interleave_zero_byte_chunks: bool) {
                let recovered_plaintext = {
                    let mut cipher = ChunkedXSalsa20Poly1305Decryptor::new(&self.key, &self.nonce);
                    for chunk in self.reference_ciphertext.chunks_mut(chunk_size) {
                        cipher.decrypt(chunk);
                        if interleave_zero_byte_chunks {
                            cipher.decrypt(&mut []);
                        }
                    }
                    cipher
                        .finalize_verify(&self.reference_tag)
                        .expect("Authentication should pass");
                    self.reference_ciphertext
                };

                // Compare results
                assert_eq!(
                    self.plaintext, recovered_plaintext,
                    "Recovered plaintext does not match expected one"
                );
            }
        }

        #[template]
        #[rstest]
        fn xsalsa20poly1305_template(
            #[values(0, 1, 63, 64, 65, 123, 666, 999)] plaintext_length: usize,
            #[values(1, 15, 16, 1024, 1039, 104857, 104858)] chunk_size: usize,
            #[values(false, true)] interleave_zero_byte_chunks: bool,
        ) {
        }

        #[apply(xsalsa20poly1305_template)]
        fn xsalsa20poly1305_encryption_lengths(
            plaintext_length: usize,
            chunk_size: usize,
            interleave_zero_byte_chunks: bool,
        ) {
            TestCase::new(plaintext_length).test_encryption(chunk_size, interleave_zero_byte_chunks);
        }

        #[rustfmt::skip]
        /// Implements Rooterberg's test vector number 3, see
        /// <https://github.com/bleichenbacher-daniel/Rooterberg/blob/0d4bc48105dd817de4af746c602621f2be086b0a/test_vectors/auth_enc/nacl_xsalsa20_poly1305.json#L62-L72>
        #[test]
        fn xsalsa20poly1305_encryption_rooterberg() {
            TestCase {
                plaintext: HEXLOWER
                    .decode(b"2021222324252627")
                    .expect("plaintext should be hex encoded"),
                key: [0; salsa20::KEY_LENGTH],
                nonce: [0; salsa20::NONCE_LENGTH],
                reference_ciphertext: HEXLOWER
                    .decode(b"e61f99dcdaa0e80b")
                    .expect("ciphertext should be hex encoded"),
                reference_tag: HEXLOWER
                    .decode(b"f9ad226979fb26db0379ec522f3e0903")
                    .expect("reference tag should be hex encoded")
                    .try_into()
                    .expect("reference tag should have valid length"),
            }
            .test_decryption(10, false);
        }

        #[apply(xsalsa20poly1305_template)]
        fn xsalsa20poly1305_decryption(
            plaintext_length: usize,
            chunk_size: usize,
            interleave_zero_byte_chunks: bool,
        ) {
            TestCase::new(plaintext_length).test_decryption(chunk_size, interleave_zero_byte_chunks);
        }

        #[test]
        #[should_panic(expected = "Authentication should pass: InvalidTag")]
        fn xsalsapoly1305_decryption_wrong_tag() {
            let mut test_case = TestCase::new(32);

            test_case.reference_tag = [0; salsa20::TAG_LENGTH];

            test_case.test_decryption(16, false);
        }
    }
}
