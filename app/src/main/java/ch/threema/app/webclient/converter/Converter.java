package ch.threema.app.webclient.converter;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;

/**
 * A converter converts arbitrary data to MessagePack representation.
 */
@AnyThread
public abstract class Converter {

    private static ServiceManager serviceManager = null;

    @NonNull
    protected static ServiceManager getServiceManager() {
        if (serviceManager == null) {
            serviceManager = ThreemaApplication.requireServiceManager();
        }
        return serviceManager;
    }

    protected static BlockedIdentitiesService getBlockedContactsService() {
        return getServiceManager().getBlockedIdentitiesService();
    }

    protected static ContactService getContactService() throws ConversionException {
        try {
            return getServiceManager().getContactService();
        } catch (NullPointerException | MasterKeyLockedException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    protected static ConversationCategoryService getConversationCategoryService() throws ConversionException {
        try {
            return getServiceManager().getConversationCategoryService();
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    protected static Context getContext() {
        return getServiceManager().getContext();
    }

    protected static GroupService getGroupService() throws ConversionException {
        try {
            return getServiceManager().getGroupService();
        } catch (NullPointerException | MasterKeyLockedException e) {
            throw new ConversionException(e);
        }
    }

    protected static DistributionListService getDistributionListService() throws ConversionException {
        try {
            return getServiceManager().getDistributionListService();
        } catch (NullPointerException | MasterKeyLockedException | NoIdentityException e) {
            throw new ConversionException(e);
        }
    }

    protected static PreferenceService getPreferenceService() throws ConversionException {
        try {
            return getServiceManager().getPreferenceService();
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    protected static FileService getFileService() throws ConversionException {
        try {
            return getServiceManager().getFileService();
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }
}
