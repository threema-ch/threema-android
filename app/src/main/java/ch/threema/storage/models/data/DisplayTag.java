package ch.threema.storage.models.data;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef(
    flag = true,
    value = {
        DisplayTag.DISPLAY_TAG_NONE,
        DisplayTag.DISPLAY_TAG_STARRED
    }
)
public @interface DisplayTag {
    /* Regular messages */
    int DISPLAY_TAG_NONE = 0;
    /* Messages that have been marked with a star by user. Such messages can be displayed separately */
    int DISPLAY_TAG_STARRED = 1;
    /* More tags could be added here - note that these must be flags */
}


