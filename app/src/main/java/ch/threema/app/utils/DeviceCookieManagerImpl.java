package ch.threema.app.utils;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.stores.EncryptedPreferenceStore;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager;
import ch.threema.storage.factories.ServerMessageModelFactory;
import ch.threema.storage.models.ServerMessageModel;

import static ch.threema.common.SecureRandomExtensionsKt.generateRandomBytes;
import static ch.threema.common.SecureRandomExtensionsKt.secureRandom;

public class DeviceCookieManagerImpl implements DeviceCookieManager {
    private static final Logger logger = getThreemaLogger("DeviceCookieManagerImpl");

    private static final int DEVICE_COOKIE_SIZE = 16;

    @NonNull
    private final EncryptedPreferenceStore encryptedPreferenceStore;
    @NonNull
    private final ServerMessageModelFactory serverMessageModelFactory;
    @Nullable
    private NotificationService notificationService;
    private boolean skipNextIndication;

    public DeviceCookieManagerImpl(
        @NonNull EncryptedPreferenceStore encryptedPreferenceStore,
        @NonNull ServerMessageModelFactory serverMessageModelFactory
    ) {
        this.encryptedPreferenceStore = encryptedPreferenceStore;
        this.serverMessageModelFactory = serverMessageModelFactory;
        this.skipNextIndication = false;
    }

    public void setNotificationService(@NonNull NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public byte[] obtainDeviceCookie() {
        // TODO(ANDR-2155): When the target API level is >= 23, use Android Keystore to store the device cookie

        byte[] deviceCookie = encryptedPreferenceStore.getBytes(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie));
        if (deviceCookie != null && deviceCookie.length == DEVICE_COOKIE_SIZE) {
            logger.debug("Got existing device cookie {}...", Utils.byteArrayToHexString(deviceCookie).substring(0, 4));
            return deviceCookie;
        }

        // Generate and store new random device cookie
        deviceCookie = generateRandomBytes(secureRandom(), DEVICE_COOKIE_SIZE);
        encryptedPreferenceStore.save(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie), deviceCookie);

        logger.info("Generated new device cookie {}...", Utils.byteArrayToHexString(deviceCookie).substring(0, 4));

        // Skip the next indication, as we have just generated a new cookie and
        // will get an indication for sure if this is a restored ID (where the
        // server has already stored a device cookie).
        this.skipNextIndication = true;

        return deviceCookie;
    }

    @Override
    public void changeIndicationReceived() {
        if (this.skipNextIndication) {
            logger.info("Skipping change indication because new cookie has been generated");
            this.skipNextIndication = false;
            return;
        }

        logger.info("Device cookie change indication received, showing warning message");

        ServerMessageModel serverMessageModel = new ServerMessageModel(ThreemaApplication.getAppContext().getString(R.string.rogue_device_warning), ServerMessageModel.TYPE_ALERT);
        serverMessageModelFactory.storeServerMessageModel(serverMessageModel);

        if (notificationService == null) {
            logger.error("Could not display device cookie change indication as notification service is null");
        } else {
            notificationService.showServerMessage(serverMessageModel);
        }
    }

    @Override
    public void deleteDeviceCookie() {
        encryptedPreferenceStore.remove(ThreemaApplication.getAppContext().getString(R.string.preferences__device_cookie));
    }
}
