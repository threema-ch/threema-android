package ch.threema.app.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.storage.models.ContactModel;

public class ShareUtil {
    public static void shareContact(@NonNull Context context, @Nullable ContactModel contact) {
        final @Nullable UserService userService;
        final @Nullable PreferenceService preferenceService;
        final @Nullable ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            userService = serviceManager.getUserService();
            preferenceService = serviceManager.getPreferenceService();
        } else {
            return;
        }

        final @NonNull String contactName = contact != null
            ? NameUtil.getContactDisplayName(contact, preferenceService.getContactNameFormat())
            : context.getString(R.string.title_mythreemaid);
        String identity = contact != null ? contact.getIdentity() : userService.getIdentity();

        String text = contactName + ": https://" + BuildConfig.contactActionUrl + "/" + identity;
        try {
            shareText(context, text, "text/plain");
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
        }
    }

    public static void shareText(@NonNull Context context, @NonNull String text, @NonNull String type) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(type);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(createChooser(context, shareIntent), null);
    }

    /**
     * Opens the share dialog for a file. The file must be in a location that is exposed via file_paths.xml, such as the cache directory.
     */
    public static void shareFile(@NonNull Context context, @NonNull File file, @NonNull String fileName, @NonNull String type) {
        Uri fileUri = getShareFileUri(context, file, fileName);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);

        shareIntent.setType(type);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(createChooser(context, shareIntent), null);
    }

    private static Intent createChooser(@NonNull Context context, @NonNull Intent intent) {
        var chooserIntent = Intent.createChooser(intent, context.getString(R.string.share_via));
        if (!(context instanceof Activity)) {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return chooserIntent;
    }

    /**
     * Get an Uri for the destination file that can be shared to other apps.
     *
     * @param file File to get an Uri for. Must be in a location that is exposed via file_paths.xml.
     * @param filename Desired filename for this file. Can be different from the filename of destFile
     * @return The shareable Uri, using the 'content' scheme
     */
    @NonNull
    public static Uri getShareFileUri(@NonNull Context context, @NonNull File file, @Nullable String filename) {
        if (filename != null) {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file, filename);
        } else {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        }
    }
}
