package ch.threema.storage.models.data;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
    MessageContentsType.UNDEFINED,
    MessageContentsType.TEXT,
    MessageContentsType.IMAGE,
    MessageContentsType.VIDEO,
    MessageContentsType.AUDIO,
    MessageContentsType.VOICE_MESSAGE,
    MessageContentsType.LOCATION,
    MessageContentsType.STATUS,
    MessageContentsType.BALLOT,
    MessageContentsType.FILE,
    MessageContentsType.VOIP_STATUS,
    MessageContentsType.GIF,
    MessageContentsType.CONTACT,
    MessageContentsType.GROUP_CALL_STATUS,
    MessageContentsType.FORWARD_SECURITY_STATUS,
    MessageContentsType.GROUP_STATUS
})

@Retention(RetentionPolicy.SOURCE)
public @interface MessageContentsType {
    int UNDEFINED = 0;
    int TEXT = 1;
    int IMAGE = 2;
    int VIDEO = 3;
    int AUDIO = 4;
    int VOICE_MESSAGE = 5;
    int LOCATION = 6;
    int STATUS = 7;
    int BALLOT = 8;
    int FILE = 9;
    int VOIP_STATUS = 10;
    int GIF = 11;
    int CONTACT = 12;
    int GROUP_CALL_STATUS = 13;
    int FORWARD_SECURITY_STATUS = 14;
    int GROUP_STATUS = 15;
}

