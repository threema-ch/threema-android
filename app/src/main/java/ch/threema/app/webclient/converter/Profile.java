package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@AnyThread
public class Profile extends Converter {
    private static final String FIELD_IDENTITY = "identity";
    private static final String FIELD_PUBKEY = "publicKey";
    private static final String FIELD_NICKNAME = "publicNickname";
    private static final String FIELD_AVATAR = "avatar";

    /**
     * Create profile response containing all profile info,
     * including identity and public key.
     */
    public static MsgpackObjectBuilder convert(@NonNull String identity,
                                               @NonNull byte[] publicKey,
                                               @Nullable String nickname,
                                               @Nullable byte[] avatar) {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(FIELD_IDENTITY, identity);
        builder.put(FIELD_PUBKEY, publicKey);
        builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
        builder.maybePut(FIELD_AVATAR, avatar);
        return builder;
    }

    /**
     * Create profile response containing only nickname and avatar.
     */
    public static MsgpackObjectBuilder convert(@Nullable String nickname,
                                               @Nullable byte[] avatar) {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
        builder.put(FIELD_AVATAR, avatar);
        return builder;
    }

    /**
     * Create profile response containing only nickname.
     */
    public static MsgpackObjectBuilder convert(@Nullable String nickname) {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
        return builder;
    }
}
