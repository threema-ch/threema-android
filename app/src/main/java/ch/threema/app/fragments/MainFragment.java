package ch.threema.app.fragments;

import androidx.fragment.app.Fragment;

public abstract class MainFragment extends Fragment {
    public boolean onBackPressed() {
        return false;
    }
}
