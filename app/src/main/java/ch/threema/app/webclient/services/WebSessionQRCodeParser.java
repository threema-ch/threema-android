package ch.threema.app.webclient.services;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;

@AnyThread
public interface WebSessionQRCodeParser {
    @AnyThread
    class Result {
        public final int versionNumber;
        public final boolean isSelfHosted;
        public final boolean isPermanent;
        @NonNull
        public final byte[] key;
        @NonNull
        public final byte[] authToken;
        public final int saltyRtcPort;
        @NonNull
        public final String saltyRtcHost;
        @Nullable
        public final byte[] serverKey;

        Result(
            int versionNumber,
            boolean isSelfHosted,
            boolean isPermanent,
            @NonNull byte[] key,
            @NonNull byte[] authToken,
            @Nullable byte[] serverKey,
            int saltyRtcPort,
            @NonNull String saltyRtcHost
        ) {
            this.versionNumber = versionNumber;
            this.isSelfHosted = isSelfHosted;
            this.isPermanent = isPermanent;
            this.key = key;
            this.authToken = authToken;
            this.serverKey = serverKey;
            this.saltyRtcPort = saltyRtcPort;
            this.saltyRtcHost = saltyRtcHost;
        }

        @NonNull
        @Override
        public String toString() {
            return "version: " + this.versionNumber
                + ", isSelfHosted: " + this.isSelfHosted
                + ", isPermanent: " + this.isPermanent
                + ", key: " + Utils.byteArrayToHexString(this.key)
                + ", authToken: " + Utils.byteArrayToHexString(this.authToken)
                + ", serverKey: " + Utils.byteArrayToHexString(this.serverKey)
                + ", saltyRtcPort: " + this.saltyRtcPort
                + ", saltyRtcHost: " + this.saltyRtcHost;
        }
    }

    class InvalidQrCodeException extends ThreemaException {
        public InvalidQrCodeException(String msg) {
            super(msg);
        }
    }

    @NonNull
    Result parse(byte[] payload) throws InvalidQrCodeException;
}
