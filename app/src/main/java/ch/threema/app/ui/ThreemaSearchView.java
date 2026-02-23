package ch.threema.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.SearchView;

import android.util.AttributeSet;

import ch.threema.app.R;
import ch.threema.app.preference.service.KeyboardDataCollectionPolicySetting;

public class ThreemaSearchView extends SearchView {

    public ThreemaSearchView(Context context) {
        super(context);

        init(context);
    }

    public ThreemaSearchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context);
    }

    public ThreemaSearchView(Context context, AttributeSet attrs) {
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
}
