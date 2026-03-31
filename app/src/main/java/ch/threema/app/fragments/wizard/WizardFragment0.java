package ch.threema.app.fragments.wizard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;

import java.util.Objects;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;

import static ch.threema.app.dev.UtilsKt.hasDevFeatures;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardFragment0 extends WizardFragment {
    private static final Logger logger = getThreemaLogger("WizardFragment0");
    public static final int PAGE_ID = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        TextView title = rootView.findViewById(R.id.wizard_title);

        // inflate content layout
        contentViewStub.setLayoutResource(R.layout.fragment_wizard0);
        contentViewStub.inflate();

        TextView idTitle = rootView.findViewById(R.id.wizard_id_title);
        idTitle.setText(this.userService.getIdentity());

        if (((WizardBaseActivity) getActivity()).isNewIdentity()) {
            title.setText(R.string.new_wizard_welcome);
        } else {
            title.setText(R.string.welcome_back);
            ((TextView) rootView.findViewById(R.id.scooter)).setText(R.string.id_restored_successfully);
            rootView.findViewById(R.id.wizard_id_explain).setVisibility(View.GONE);
        }

        if (hasDevFeatures()) {
            // We use a tag instead of an id to find the button, as the id won't be available in non-dev builds
            rootView.findViewWithTag("wizard_dev_skip").setOnClickListener((v) -> ((WizardBaseActivity) getActivity()).skipWizard());
        }

        return rootView;
    }

    @Override
    protected int getAdditionalInfoText() {
        return R.string.new_wizard_info_id;
    }
}
