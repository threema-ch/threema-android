package ch.threema.app.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import ch.threema.app.R;

public class AddAccountActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this, getString(R.string.add_acount_from_within_threema), Toast.LENGTH_LONG).show();
        finish();
    }
}
