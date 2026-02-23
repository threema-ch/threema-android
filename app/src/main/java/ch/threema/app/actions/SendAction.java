package ch.threema.app.actions;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;

public abstract class SendAction {
    public interface ActionHandler {
        default void onError(String errorMessage) {
        }

        default void onWarning(String warning, boolean continueAction) {
        }

        default void onProgress(int progress, int total) {
        }

        default void onCompleted() {
        }
    }

    private final ServiceManager serviceManager;

    public SendAction() {
        this.serviceManager = ThreemaApplication.getServiceManager();
    }

    protected ServiceManager getServiceManager() {
        return this.serviceManager;
    }

}
