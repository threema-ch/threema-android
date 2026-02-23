package ch.threema.app.mediaattacher;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;


public class MediaFilterQuery {

    @Retention(SOURCE)
    @IntDef({FILTER_MEDIA_TYPE, FILTER_MEDIA_BUCKET, FILTER_MEDIA_LABEL, FILTER_MEDIA_SELECTED, FILTER_MEDIA_DATE})
    public @interface FilterType {
    }

    public static final int FILTER_MEDIA_TYPE = 0;
    public static final int FILTER_MEDIA_BUCKET = 1;
    public static final int FILTER_MEDIA_LABEL = 2;
    public static final int FILTER_MEDIA_SELECTED = 3;
    public static final int FILTER_MEDIA_DATE = 4;

    public final String query;
    @FilterType
    public final int type;

    public MediaFilterQuery(@NonNull String query, @FilterType int type) {
        this.query = query;
        this.type = type;
    }

    public String getQuery() {
        return query;
    }

    @FilterType
    public int getType() {
        return type;
    }

}
