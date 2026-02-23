package ch.threema.app.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;

import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;

import ch.threema.app.preference.service.KeyboardDataCollectionPolicySetting;

public class ThreemaEditText extends TextInputEditText {

    public ThreemaEditText(Context context) {
        super(context);

        init(context);
    }

    public ThreemaEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }

    public ThreemaEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        // PreferenceService may not yet be available at this time
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (sharedPreferences != null) {
                    return sharedPreferences.getBoolean(getResources().getString(
                        KeyboardDataCollectionPolicySetting.getPreferenceKeyStringRes()
                    ), false);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                if (aBoolean != null && aBoolean == true) {
                    setImeOptions(getImeOptions() | 0x1000000);
                }
            }
        }.execute();
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            // Hack to prevent rich text pasting
            id = android.R.id.pasteAsPlainText;
        }
        return super.onTextContextMenuItem(id);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public int getAutofillType() {
        // disable Autofill in EditText due to privacy and TransactionTooLargeException as well as bug https://issuetracker.google.com/issues/67675432
        return AUTOFILL_TYPE_NONE;
    }

    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        try {
            super.dispatchWindowFocusChanged(hasFocus);
        } catch (Exception ignore) {
            // catch Security Exception in com.samsung.android.content.clipboard.SemClipboardManager.getLatestClip() on Samsung devices
        }
    }
}
