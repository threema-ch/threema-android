package ch.threema.app.activities.ballot;

import org.koin.java.KoinJavaComponent;

import androidx.annotation.NonNull;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.storage.models.ballot.BallotModel;

abstract class BallotDetailActivity extends ThreemaToolbarActivity {

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private BallotModel ballotModel = null;

    interface ServiceCall {
        void call(BallotService service);
    }

    protected boolean setBallotModel(final BallotModel ballotModel) {
        this.ballotModel = ballotModel;
        this.updateViewState();

        return this.ballotModel != null;
    }

    protected BallotModel getBallotModel() {
        return this.ballotModel;
    }

    protected Integer getBallotModelId() {
        if (this.ballotModel != null) {
            return this.ballotModel.getId();
        }

        return null;
    }

    private void updateViewState() {
        if (this.ballotModel != null) {
            this.callService(service -> {
                service.viewingBallot(ballotModel, true);
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.ballotModel != null) {
            this.callService(service -> {
                service.viewingBallot(ballotModel, true);
            });
        }
    }

    @Override
    public void onPause() {
        if (this.ballotModel != null) {
            this.callService(service -> {
                service.viewingBallot(ballotModel, false);
            });
        }
        super.onPause();
    }

    private void callService(ServiceCall serviceCall) {
        serviceCall.call(dependencies.getBallotService());
    }
}
