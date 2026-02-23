package ch.threema.base.crypto;

import ch.threema.domain.protocol.csp.ProtocolDefines;

public class NonceCounter {
    private final byte[] cookie;
    private long nextNonce;

    public NonceCounter(byte[] cookie) {
        this.cookie = cookie;
        nextNonce = 1;
    }

    public synchronized byte[] nextNonce() {
        byte[] nonce = new byte[NaCl.NONCE_BYTES];
        System.arraycopy(cookie, 0, nonce, 0, ProtocolDefines.COOKIE_LEN);
        for (int i = 0; i < 8; i++) {
            nonce[i + ProtocolDefines.COOKIE_LEN] = (byte) (nextNonce >> (i * 8));
        }

        nextNonce++;
        return nonce;
    }
}
