package ch.threema.app.activities.ballot;


import androidx.fragment.app.Fragment;

abstract class BallotWizardFragment extends Fragment {
    private BallotWizardActivity ballotWizardActivity = null;

    /**
     * update the data fields
     */
    abstract void updateView();

    /**
     * cast activity to ballotActivity
     */
    public BallotWizardActivity getBallotActivity() {
        if (this.ballotWizardActivity == null) {
            if (super.getActivity() instanceof BallotWizardActivity) {
                this.ballotWizardActivity = (BallotWizardActivity) this.getActivity();
            }
        }

        return this.ballotWizardActivity;
    }
}
