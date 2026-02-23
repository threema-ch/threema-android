package ch.threema.app.services.license;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.base.SessionScoped;
import ch.threema.domain.models.LicenseCredentials;

@SessionScoped
public interface LicenseService<T extends LicenseCredentials> {

    /**
     * Validate by credentials
     * On success, the credentials will be saved.
     *
     * @param credentials holder of the credential values
     * @return `null` for success or an error message if validation failed
     */
    @Nullable
    @WorkerThread
    String validate(T credentials);

    /**
     * Validate by saved credentials
     *
     * @param allowException If true, general exceptions will be ignored
     * @return `null` for success or an error message if validation failed
     */
    @Nullable
    @WorkerThread
    String validate(boolean allowException);

    /**
     * check if any credentials are saved
     */
    boolean hasCredentials();

    /**
     * check if a validate check was successfully
     */
    boolean isLicensed();

    /**
     * load the credentials
     *
     * @return null or the saved credentials
     */
    @Nullable
    T loadCredentials();
}
