package ch.threema.app.ui;

import org.msgpack.core.annotations.Nullable;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import ch.threema.app.AppConstants;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.GroupService;
import ch.threema.storage.models.group.GroupModelOld;

public class AvatarEditViewModel extends ViewModel {

    private static final String KEY_CAMERA_FILE = "cam";
    private static final String KEY_CROPPED_FILE = "crop";
    private static final String KEY_CONTACT_IDENTITY = "contact";

    private final SavedStateHandle savedState;
    private GroupService groupService;

    public AvatarEditViewModel(SavedStateHandle savedStateHandle) {
        this.savedState = savedStateHandle;

        try {
            this.groupService = ThreemaApplication.requireServiceManager().getGroupService();
        } catch (Exception e) {
            //
        }
    }

    public File getCameraFile() {
        return this.savedState.get(KEY_CAMERA_FILE);
    }

    public void setCameraFile(File cameraFile) {
        this.savedState.set(KEY_CAMERA_FILE, cameraFile);
    }

    public File getCroppedFile() {
        return this.savedState.get(KEY_CROPPED_FILE);
    }

    public void setCroppedFile(File croppedFile) {
        this.savedState.set(KEY_CROPPED_FILE, croppedFile);
    }

    public @Nullable GroupModelOld getGroupModel() {
        if (!this.savedState.contains(AppConstants.INTENT_DATA_GROUP_DATABASE_ID)) {
            return null;
        }
        Long groupId = this.savedState.get(AppConstants.INTENT_DATA_GROUP_DATABASE_ID);
        if (groupService != null && groupId != null) {
            return groupService.getById((int) groupId.longValue());
        }
        return null;
    }

    public void setGroupModel(@NonNull GroupModelOld groupModel) {
        this.savedState.set(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, (long) groupModel.getId());
    }

    public @Nullable String getContactIdentity() {
        return this.savedState.get(KEY_CONTACT_IDENTITY);
    }

    public void setContactIdentity(@NonNull String identity) {
        this.savedState.set(KEY_CONTACT_IDENTITY, identity);
    }
}
