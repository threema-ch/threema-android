package ch.threema.app.preference;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// Frontend to call the app's media settings directly from notification or system settings
public class SettingsMediaDummyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = SettingsActivity.createIntent(this, SettingsActivity.InitialScreen.MEDIA);
        startActivity(intent);
        finish();
    }
}
