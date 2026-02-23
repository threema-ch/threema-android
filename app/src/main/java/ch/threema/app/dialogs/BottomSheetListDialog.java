package ch.threema.app.dialogs;

import android.os.Bundle;

import org.slf4j.Logger;

import java.util.ArrayList;

import androidx.annotation.StringRes;
import ch.threema.app.ui.BottomSheetItem;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BottomSheetListDialog extends BottomSheetAbstractDialog {
    private static final Logger logger = getThreemaLogger("BottomSheetListDialog");

    public static BottomSheetListDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items, int selected) {
        BottomSheetListDialog dialog = new BottomSheetListDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("selected", selected);
        args.putParcelableArrayList("items", items);
        dialog.setArguments(args);
        return dialog;
    }


    public static BottomSheetListDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items, int selected, BottomSheetDialogInlineClickListener listener) {
        // do not use inline callbacks in activities that don't have android:configChanges="orientation|screenSize|keyboardHidden" set
        // or fragments without setRetainInstance(true)
        BottomSheetListDialog dialog = new BottomSheetListDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("selected", selected);
        args.putParcelableArrayList("items", items);
        args.putParcelable("listener", listener);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }
}
