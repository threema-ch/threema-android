import blake2bReference from 'blake2b';
import _sodium from 'libsodium-wrappers';
import tweetNaCl from 'tweetnacl';

import {
    argon2id,
    blake2bMac256,
    blake2bMac512,
    hmacSha256,
    scrypt,
    sha256,
    x25519DerivePublicKey,
    x25519HSalsa20DeriveSharedSecret,
    xChaCha20Poly1305Decrypt,
    xChaCha20Poly1305Encrypt,
    xSalsa20Poly1305Decrypt,
    xSalsa20Poly1305Encrypt,
} from '../../build/wasm/nodejs/libthreema.js';

import {assert, evaluateTestResults, init, runTest} from './utils.js';

// Test SHA-256 against test vectors
function testSha256() {
    // Test vectors taken from https://www.di-mgt.com.au/sha_testvectors.html
    const testVectors = [
        {
            data: '',
            expected:
                'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        },
        {
            data: 'abc',
            expected:
                'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
        },
        {
            data: 'a'.repeat(1_000_000),
            expected:
                'cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0',
        },
    ];

    for (const testVector of testVectors) {
        const computed = Buffer.from(
            sha256(Buffer.from(testVector.data, 'utf8')),
        ).toString('hex');

        assert(
            testVector.expected === computed,
            `Computed SHA-256 should match expected one. Input: ${testVector.data}`,
        );
    }
}

// Test HMAC-SHA-256 against test vectors
function testHmacSha256() {
    // Test vectors taken from https://datatracker.ietf.org/doc/html/rfc4231#section-4
    const testVectors = [
        {
            key: '0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b',
            data: 'Hi There',
            expected:
                'b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7',
        },
        {
            key: '4a656665',
            data: 'what do ya want for nothing?',
            expected:
                '5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843',
        },
        {
            key: 'a'.repeat(262),
            data: 'Test Using Larger Than Block-Size Key - Hash Key First',
            expected:
                '60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54',
        },
    ];

    for (const testVector of testVectors) {
        const computed = Buffer.from(
            hmacSha256(
                Buffer.from(testVector.key, 'hex'),
                Buffer.from(testVector.data, 'utf8'),
            ),
        ).toString('hex');

        assert(
            testVector.expected === computed,
            `Computed HMAC-SHA-256 should match expected one. Input: ${testVector.data}, Key: ${testVector.key}`,
        );
    }
}

// Test Argon2id against test vectors
function testArgon2id() {
    // Test vector taken from
    // https://github.com/RustCrypto/password-hashes/blob/master/argon2/tests/kat.rs

    const argon2idParameters = {
        memoryCost: 32,
        timeCost: 3,
        parallelism: 4,
        outputLength: 32,
    };

    const computed = Buffer.from(
        argon2id(
            Buffer.from('01'.repeat(32), 'hex'),
            Buffer.from('02'.repeat(16), 'hex'),
            argon2idParameters,
        ),
    ).toString('hex');

    assert(
        '03aab965c12001c9d7d0d2de33192c0494b684bb148196d73c1df1acaf6d0c2e' ===
            computed,
        'Computed Argon2id hash should match expected one. ',
    );
}

// Test Scrypt against test vectors
function testScrypt() {
    // Test vector taken from the original paper (https://www.tarsnap.com/scrypt/scrypt.pdf)

    const scryptParameters = {
        logMemoryCost: 10,
        blockSize: 8,
        parallelism: 16,
        outputLength: 64,
    };

    const computed = Buffer.from(
        scrypt(
            Buffer.from('password', 'utf8'),
            Buffer.from('NaCl', 'utf8'),
            scryptParameters,
        ),
    ).toString('hex');

    assert(
        'fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640' ===
            computed,
        'Computed Scrypt hash should match expected one',
    );
}

// Test BLake2b against test vectors
function testBlake2b() {
    const outputLengths = [
        [32, blake2bMac256],
        [64, blake2bMac512],
    ];

    const testVectors = [
        {key: undefined, salt: undefined, personal: undefined, data: undefined},
        {
            key: undefined,
            salt: undefined,
            personal: undefined,
            data: 'no data with privacy',
        },
        {
            key: undefined,
            salt: '01'.repeat(16),
            personal: undefined,
            data: undefined,
        },
        {
            key: undefined,
            salt: '01'.repeat(16),
            personal: undefined,
            data: 'datagram',
        },
        {
            key: undefined,
            salt: '02'.repeat(16),
            personal: 'cd'.repeat(16),
            data: undefined,
        },
        {
            key: undefined,
            salt: '02'.repeat(16),
            personal: 'cd'.repeat(16),
            data: 'data done right',
        },
        {
            key: 'hello my fancy key',
            salt: undefined,
            personal: undefined,
            data: undefined,
        },
        {
            key: 'hello my fancy key',
            salt: undefined,
            personal: undefined,
            data: 'have you seen my data?',
        },
        {
            key: 'hello dear personal',
            salt: '03'.repeat(16),
            personal: 'ab'.repeat(16),
            data: undefined,
        },
        {
            key: 'hello dear personal',
            salt: '03'.repeat(16),
            personal: 'ab'.repeat(16),
            data: 'good luck',
        },
        {
            key: 'hello my dear salt',
            salt: '04'.repeat(16),
            personal: 'ef'.repeat(16),
            data: undefined,
        },
        {
            key: 'hello my dear salt',
            salt: '04'.repeat(16),
            personal: 'ef'.repeat(16),
            data: 'no joke, it is real',
        },
    ];

    for (const testVector of testVectors) {
        const key =
            testVector.key === undefined
                ? undefined
                : Buffer.from(testVector.key, 'utf8');
        const salt =
            testVector.salt === undefined
                ? undefined
                : Buffer.from(testVector.salt, 'hex');
        const personal =
            testVector.personal === undefined
                ? undefined
                : Buffer.from(testVector.personal, 'hex');
        const data =
            testVector.data === undefined
                ? new Uint8Array(0)
                : Buffer.from(testVector.data, 'utf8');

        for (const [outputLength, blake2b] of outputLengths) {
            const computed = Buffer.from(
                blake2b(
                    key,
                    personal ?? new Uint8Array(0),
                    salt ?? new Uint8Array(0),
                    data,
                ),
            ).toString('hex');

            // Dispatch to the correct function call depending on the existance of
            // salt and personal
            const expected = blake2bReference(
                outputLength,
                key,
                salt,
                personal,
                true,
            )
                .update(data)
                .digest('hex');

            assert(
                expected === computed,
                `Computed Blake2b with output length ${outputLength} should match expected one. Key: ${testVector.key}, Salt: ${testVector.salt}, Personal: ${testVector.personal}, Data: ${testVector.data}`,
            );
        }
    }
}

// Test XSalsa20Poly1305 by encrypting/decrypting between libthreema and TweetNacl
function testXSalsa20Poly1305() {
    // Compute Keys
    // Alice uses libthreema
    const aliceSecretKey = Buffer.from('aa'.repeat(32), 'hex');
    const alicePublicKey = x25519DerivePublicKey(aliceSecretKey);

    // Bob uses tweetNaCl
    const bobSecretKey = Buffer.from('bb'.repeat(32), 'hex');
    const bobPublicKey =
        tweetNaCl.box.keyPair.fromSecretKey(bobSecretKey).publicKey;

    // Derive shared keys between Alice and Bob
    const sharedKeyAliceBob = x25519HSalsa20DeriveSharedSecret(
        bobPublicKey,
        aliceSecretKey,
    );
    const sharedKeyBobAlice = tweetNaCl.box.before(
        alicePublicKey,
        bobSecretKey,
    );

    // Encrypt from Alice to Bob
    const nonceAliceBob = Buffer.from('01'.repeat(24), 'hex');
    const messageAliceBob = 'Hi Bob';
    const encryptedMessageAliceBob = xSalsa20Poly1305Encrypt(
        sharedKeyAliceBob,
        nonceAliceBob,
        Buffer.from(messageAliceBob, 'utf8'),
        [],
    );

    // Decrypt
    const decryptedMessageAliceBob = Buffer.from(
        tweetNaCl.box.open.after(
            encryptedMessageAliceBob,
            nonceAliceBob,
            sharedKeyBobAlice,
        ),
    ).toString('utf8');
    assert(
        decryptedMessageAliceBob === messageAliceBob,
        'Decrypted message from Alice to Bob does not match original one',
    );

    // Encrypt from Bob to Alice
    const nonceBobAlice = Buffer.from('02'.repeat(24), 'hex');
    const messageBobAlice = 'Hi Alice from Bob';
    const encryptedMessageBobAlice = tweetNaCl.box.after(
        Buffer.from(messageBobAlice, 'utf8'),
        nonceBobAlice,
        sharedKeyBobAlice,
    );

    // Decrypt
    const decryptedMessageBobAlice = Buffer.from(
        xSalsa20Poly1305Decrypt(
            sharedKeyAliceBob,
            nonceBobAlice,
            encryptedMessageBobAlice,
        ),
    ).toString('utf8');
    assert(
        decryptedMessageBobAlice === messageBobAlice,
        'Decrypted message from Bob to Alice does not match original one',
    );
}

async function testXChaCha20Poly1305() {
    await _sodium.ready;
    const sodium = _sodium;

    // Compute Keys
    // Alice uses libthreema
    const aliceSecretKey = Buffer.from('aa'.repeat(32, 'hex'));
    const alicePublicKey = x25519DerivePublicKey(aliceSecretKey);

    // Bob uses libsodium
    const bobKeys = sodium.crypto_box_seed_keypair(
        Buffer.from('bb'.repeat(32, 'hex')),
    );
    const bobSecretKey = bobKeys.privateKey;
    const bobPublicKey = bobKeys.publicKey;

    // Derive shared keys between Alice and Bob
    const sharedKeyAliceBob = x25519HSalsa20DeriveSharedSecret(
        bobPublicKey,
        aliceSecretKey,
    );
    const sharedKeyBobAlice = sodium.crypto_box_beforenm(
        alicePublicKey,
        bobSecretKey,
    );
    assert(
        Buffer.from(sharedKeyAliceBob).toString('hex') ===
            Buffer.from(sharedKeyBobAlice).toString('hex'),
        'Derived Keys do not match.',
    );

    // Encrypt from Alice to Bob
    const nonceAliceBob = Buffer.from('01'.repeat(24, 'hex'));
    const messageAliceBob = 'Hi Bob';
    const encryptedMessageAliceBob = xChaCha20Poly1305Encrypt(
        sharedKeyAliceBob,
        nonceAliceBob,
        Buffer.from(messageAliceBob, 'utf8'),
        [],
    );

    // Decrypt
    const decryptedMessageAliceBob = Buffer.from(
        sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            null,
            encryptedMessageAliceBob,
            null,
            nonceAliceBob,
            sharedKeyBobAlice,
        ),
    ).toString('utf8');
    assert(
        decryptedMessageAliceBob === messageAliceBob,
        'Decrypted message from Alice to Bob does not match original one',
    );

    // Encrypt from Bob to Alice
    const nonceBobAlice = Buffer.from('02'.repeat(24, 'hex'));
    const messageBobAlice = 'Hi Alice from Bob';
    const encryptedMessageBobAlice =
        sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            Buffer.from(messageBobAlice, 'utf8'),
            null,
            null,
            nonceBobAlice,
            sharedKeyBobAlice,
        );

    // Decrypt
    const decryptedMessageBobAlice = Buffer.from(
        xChaCha20Poly1305Decrypt(
            sharedKeyAliceBob,
            nonceBobAlice,
            encryptedMessageBobAlice,
            [],
        ),
    ).toString('utf8');

    assert(
        decryptedMessageBobAlice === messageBobAlice,
        'Decrypted message from Bob to Alice does not match original one',
    );
}

// Initialize libthreema
init();

// Run tests
evaluateTestResults([
    runTest(testSha256, 'SHA-256'),
    runTest(testHmacSha256, 'HMAC-SHA-256'),
    runTest(testArgon2id, 'Argon2id'),
    runTest(testScrypt, 'Scrypt'),
    runTest(testBlake2b, 'Blake2b'),
    runTest(testXSalsa20Poly1305, 'XSalsa20Poly1305'),
    runTest(testXChaCha20Poly1305, 'XChaCha20Poly1305'),
]);
