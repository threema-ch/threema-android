package ch.threema.app.fragments.mediaviews;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MimeUtil;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class FileViewFragment extends MediaViewFragment {
    private static final Logger logger = getThreemaLogger("FileViewFragment");

    private @Nullable WeakReference<ImageView> mimeCategoryImageViewRef;
    private @Nullable WeakReference<TextView> fileNameTextViewRef;
    private @Nullable WeakReference<TextView> mimeCategoryLabelTextViewRef;

    public FileViewFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected int getFragmentResourceId() {
        return R.layout.fragment_media_viewer_file;
    }

    @Override
    protected void created(@Nullable Bundle savedInstanceState, @NonNull ViewGroup rootView) {
        final @NonNull LinearLayout messageInfoContainer = rootView.findViewById(R.id.message_info_container);
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            messageInfoContainer,
            InsetSides.all(),
            SpacingValues.all(R.dimen.grid_unit_x2)
        );
        this.mimeCategoryImageViewRef = new WeakReference<>(rootView.findViewById(R.id.mime_category_image));
        this.fileNameTextViewRef = new WeakReference<>(rootView.findViewById(R.id.filename));
        this.mimeCategoryLabelTextViewRef = new WeakReference<>(rootView.findViewById(R.id.mime_category_label));
    }

    @Override
    protected void handleDecryptingFile() {
        //on decoding, do nothing!
    }

    @Override
    protected void handleDecryptFailure() {
        //
    }

    @Override
    protected void handleDecryptedFile(final File file) {
        if (this.isAdded() && getContext() != null && mimeCategoryImageViewRef != null) {
            mimeCategoryImageViewRef.get().setOnClickListener(
                v -> ((MediaViewerActivity) requireActivity()).viewMediaInGallery()
            );
        }
    }

    @Override
    protected void handleFileName(@Nullable String fileName) {
        final @Nullable TextView fileNameTextView = (fileNameTextViewRef != null)
            ? fileNameTextViewRef.get()
            : null;
        if (fileNameTextView == null) {
            return;
        }
        fileNameTextView.setVisibility(
            (fileName != null && !fileName.isBlank()) ? View.VISIBLE : View.GONE
        );
        fileNameTextView.setText(fileName);
    }

    @Override
    protected void handleMimeCategory(@NonNull MimeUtil.MimeCategory category) {
        final @Nullable ImageView mimeCategoryImageView = (mimeCategoryImageViewRef != null)
            ? mimeCategoryImageViewRef.get()
            : null;
        final @Nullable TextView mimeCategoryLabelTextView = (mimeCategoryLabelTextViewRef != null)
            ? mimeCategoryLabelTextViewRef.get()
            : null;
        if (mimeCategoryImageView == null || mimeCategoryLabelTextView == null) {
            return;
        }
        mimeCategoryImageView.setImageDrawable(
            ResourcesCompat.getDrawable(
                getResources(),
                IconUtil.getMimeCategoryIcon(category),
                requireActivity().getTheme()
            )
        );
        final @Nullable @StringRes Integer mimeCategoryDescriptionRes = MimeUtil.getMimeDescriptionRes(category);
        if (mimeCategoryDescriptionRes != null) {
            mimeCategoryLabelTextView.setText(mimeCategoryDescriptionRes);
        } else {
            mimeCategoryLabelTextView.setText(null);
        }
        mimeCategoryLabelTextView.setVisibility(
            mimeCategoryDescriptionRes != null ? View.VISIBLE : View.GONE
        );
    }
}
