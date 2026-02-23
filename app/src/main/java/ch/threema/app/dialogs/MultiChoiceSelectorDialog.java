package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class MultiChoiceSelectorDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("MultiChoiceSelectorDialog");

    private SelectorDialogClickListener callback;
    private Activity activity;
    private AlertDialog alertDialog;

    public static MultiChoiceSelectorDialog newInstance(String title, String[] items, boolean[] checkedItems) {
        MultiChoiceSelectorDialog dialog = new MultiChoiceSelectorDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putStringArray("items", items);
        args.putBooleanArray("checked", checkedItems);

        dialog.setArguments(args);
        return dialog;
    }

    public interface SelectorDialogClickListener {
        void onYes(String tag, boolean[] checkedItems);

        default void onCancel(String tag) {
            // optional interface
        }
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);

        callback.onCancel(this.getTag());
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        String title = getArguments().getString("title");
        final String[] items = getArguments().getStringArray("items");
        final boolean[] checkedItems = getArguments().getBooleanArray("checked");

        final String tag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        if (title != null) {
            builder.setTitle(title);
        }
        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
            //
        });

        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            dialog.dismiss();
            callback.onYes(tag, checkedItems);
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            dialog.dismiss();
            callback.onCancel(tag);
        });

        alertDialog = builder.create();

        return alertDialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        try {
            callback = (SelectorDialogClickListener) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // maybe called from an activity rather than a fragment
        if (callback == null) {
            if ((activity instanceof SelectorDialogClickListener)) {
                callback = (SelectorDialogClickListener) activity;
            }
        }
    }
}
