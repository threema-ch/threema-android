package ch.threema.app.mediaattacher;

import android.view.View;

import androidx.fragment.app.Fragment;

public abstract class PreviewFragment extends Fragment {
    protected MediaAttachItem mediaAttachItem;
    protected MediaAttachViewModel mediaAttachViewModel;
    protected View rootView;
    protected boolean isChecked = false;

    public PreviewFragment(MediaAttachItem mediaItem, MediaAttachViewModel mediaAttachViewModel) {
        this.mediaAttachItem = mediaItem;
        this.mediaAttachViewModel = mediaAttachViewModel;

        setRetainInstance(true);
    }
}

