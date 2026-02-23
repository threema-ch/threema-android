package ch.threema.app.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AttributeSet;

import com.google.android.material.textfield.TextInputEditText;

import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.preference.service.KeyboardDataCollectionPolicySetting;

public class ThreemaTextInputEditText extends TextInputEditText {

    public ThreemaTextInputEditText(Context context) {
        super(context);

        init(context);
    }

    public ThreemaTextInputEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }

    public ThreemaTextInputEditText(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        // PreferenceService may not yet be available at this time
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences != null && sharedPreferences.getBoolean(getResources().getString(
            KeyboardDataCollectionPolicySetting.getPreferenceKeyStringRes()
        ), false)) {
            setImeOptions(getImeOptions() | 0x1000000);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    public int getAutofillType() {
        // disable Autofill in EditText due to privacy and TransactionTooLargeException as well as bug https://issuetracker.google.com/issues/67675432
        return AUTOFILL_TYPE_NONE;
    }

    /**
     * Get a password or passphrase from the EditText without using a String, as Strings are immutable
     * See https://docs.oracle.com/javase/1.5.0/docs/guide/security/jce/JCERefGuide.html#PBEEx
     *
     * @return passphrase as char array
     */
    public char[] getPassphrase() {
        int length = length();
        char[] passphrase = new char[length];

        if (length > 0 && getText() != null) {
            getText().getChars(0, length, passphrase, 0);
        }
        return passphrase;
    }
}
