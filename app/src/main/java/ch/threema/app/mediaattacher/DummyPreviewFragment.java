package ch.threema.app.mediaattacher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;

public class DummyPreviewFragment extends PreviewFragment {

    DummyPreviewFragment(MediaAttachItem mediaItem, MediaAttachViewModel mediaAttachViewModel) {
        super(mediaItem, mediaAttachViewModel);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.rootView = inflater.inflate(R.layout.fragment_dummy_preview, container, false);

        return rootView;
    }
}
