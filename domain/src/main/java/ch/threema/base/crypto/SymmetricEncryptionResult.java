package ch.threema.base.crypto;

import androidx.annotation.NonNull;

public class SymmetricEncryptionResult {
    private final @NonNull byte[] data;
    private final @NonNull byte[] key;

    public SymmetricEncryptionResult(@NonNull byte[] data, @NonNull byte[] key) {
        this.data = data;
        this.key = key;
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    @NonNull
    public byte[] getData() {
        return data;
    }

    @NonNull
    public byte[] getKey() {
        return key;
    }
}
