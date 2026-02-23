package ch.threema.app.dialogs;

import android.os.Bundle;

import org.slf4j.Logger;

import java.util.ArrayList;

import androidx.annotation.StringRes;
import ch.threema.app.ui.BottomSheetItem;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BottomSheetGridDialog extends BottomSheetAbstractDialog {
    private static final Logger logger = getThreemaLogger("BottomSheetGridDialog");

    public static BottomSheetGridDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items) {
        BottomSheetGridDialog dialog = new BottomSheetGridDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putParcelableArrayList("items", items);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    /* Hack to prevent TransactionTooLargeException when hosting activity goes into the background */
    @Override
    public void onPause() {
        dismiss();

        super.onPause();
    }
}
