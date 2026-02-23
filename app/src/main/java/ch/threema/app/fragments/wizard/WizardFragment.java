package ch.threema.app.fragments.wizard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;

import org.slf4j.Logger;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LocaleService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public abstract class WizardFragment extends Fragment {
    private static final Logger logger = getThreemaLogger("WizardFragment");

    private static final String DIALOG_TAG_ADDITIONAL_INFO = "ai";

    protected PreferenceService preferenceService;
    protected UserService userService;
    protected LocaleService localeService;
    protected ViewStub contentViewStub;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (!requiredInstances()) {
            requireActivity().finish();
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater,
        ViewGroup container,
        Bundle savedInstanceState
    ) {
        View rootView = inflater.inflate(R.layout.fragment_wizard, container, false);

        contentViewStub = rootView.findViewById(R.id.stub_content);

        ImageView infoIcon = rootView.findViewById(R.id.wizard_icon_info);
        infoIcon.setOnClickListener(v -> showAdditionalInfo());

        return rootView;
    }

    private void showAdditionalInfo() {
        int infoStringRes = getAdditionalInfoText();
        if (infoStringRes != 0) {
            WizardDialog wizardDialog = WizardDialog.newInstance(infoStringRes, R.string.ok);
            wizardDialog.show(getParentFragmentManager(), DIALOG_TAG_ADDITIONAL_INFO);
        }
    }

    private boolean requiredInstances() {
        if (!this.checkInstances()) {
            this.instantiate();
        }
        return this.checkInstances();
    }

    private boolean checkInstances() {
        return TestUtil.required(
            this.preferenceService,
            this.userService,
            this.localeService
        );
    }

    private void instantiate() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            this.preferenceService = serviceManager.getPreferenceService();
            try {
                this.userService = serviceManager.getUserService();
                this.localeService = serviceManager.getLocaleService();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    protected void setPage(int page) {
        ((WizardBaseActivity) requireActivity()).setPage(page);
    }

    protected abstract @StringRes int getAdditionalInfoText();
}
