package ch.threema.app.activities;

import android.os.Bundle;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class DownloadApkActivity extends AppCompatActivity {
    public static final String EXTRA_FORCE_UPDATE_DIALOG = "";
    // stub

    private static final Logger logger = getThreemaLogger("DownloadApkActivity");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger.error("This activity may not be used in this build variant");

        finish();
    }
}
