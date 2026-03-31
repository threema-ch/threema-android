package ch.threema.app.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardIntroActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.startup.AppStartupAware;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConnectionIndicatorUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.protocol.connection.ConnectionStateListener;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;

/**
 * Helper class for activities that use the new toolbar
 */
public abstract class ThreemaToolbarActivity extends ThreemaActivity implements ConnectionStateListener {
    private static final Logger logger = getThreemaLogger("ThreemaToolbarActivity");

    private AppBarLayout appBarLayout;
    private Toolbar toolbar;
    private View connectionIndicator;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Override
    protected void onResume() {
        if (isSessionScopeReady()) {
            dependencies.getServerConnection().addConnectionStateListener(this);
            ConnectionState connectionState = dependencies.getServerConnection().getConnectionState();
            ConnectionIndicatorUtil.getInstance().updateConnectionIndicator(connectionIndicator, connectionState);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (isSessionScopeReady()) {
            dependencies.getServerConnection().removeConnectionStateListener(this);
        }
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        resetKeyboard();

        super.onCreate(savedInstanceState);

        // The license can not be checked if the session scope is not ready, so we potentially skip the check here.
        // This isn't ideal, but at the latest in the next ThreemaToolbarActivity, another check will be made.
        if (isSessionScopeReady() && ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
            startActivity(new Intent(this, EnterSerialActivity.class));
            finish();
            return;
        }

        // TODO(ANDR-4389): Improve app-startup behavior
        if (!(this instanceof AppStartupAware) && !initActivity(savedInstanceState)) {
            finish();
            return;
        }

        handleDeviceInsets();
    }

    /**
     * Applies left, top and right device insets padding to the AppBarLayout {@code R.id.appbar} if present.
     */
    protected void handleDeviceInsets() {
        final @Nullable AppBarLayout appBarLayout = findViewById(R.id.appbar);
        if (appBarLayout != null) {
            ViewExtensionsKt.applyDeviceInsetsAsPadding(appBarLayout, InsetSides.ltr());
        }
    }

    /**
     * This method sets up the layout, the connection indicator, language override and screenshot blocker.
     * It is called from onCreate() after all the basic initialization has been done.
     * Override this to do your own initialization.
     *
     * @param savedInstanceState the bundle provided to onCreate()
     * @return true on success, false otherwise, in which case the activity is finished
     */
    @CallSuper
    protected boolean initActivity(@Nullable Bundle savedInstanceState) {
        if (!isSessionScopeReady()) {
            return false;
        }

        @LayoutRes int layoutResource = getLayoutResource();

        if (dependencies.getNotificationPreferenceService().getWizardRunning()) {
            startActivity(WizardIntroActivity.createIntent(this));
            return false;
        }

        ConfigUtils.applyScreenshotPolicy(this,
            dependencies.getSynchronizedSettingsService(),
            dependencies.getLockAppService()
        );

        if (layoutResource != 0) {
            logger.debug("setContentView");

            setContentView(layoutResource);

            this.appBarLayout = findViewById(R.id.appbar);

            this.toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
            }
            if (appBarLayout != null) {
                MaterialToolbar materialToolbar = appBarLayout.findViewById(R.id.material_toolbar);
                if (materialToolbar != null) {
                    materialToolbar.setNavigationContentDescription(R.string.abc_action_bar_up_description);
                }
            }

            connectionIndicator = findViewById(R.id.connection_indicator);
        }

        return true;
    }

    public abstract @LayoutRes int getLayoutResource();

    public void setToolbar(Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Nullable
    public AppBarLayout getAppBarLayout() {
        return this.appBarLayout;
    }

    public Toolbar getToolbar() {
        return this.toolbar;
    }

    protected View getConnectionIndicator() {
        return connectionIndicator;
    }

    @Override
    public void updateConnectionState(final ConnectionState connectionState) {
        RuntimeUtil.runOnUiThread(() -> ConnectionIndicatorUtil.getInstance().updateConnectionIndicator(connectionIndicator, connectionState));
    }

    /* Soft keyboard tracking */

    private static final String PORTRAIT_HEIGHT = "kbd_portrait_height";
    private static final String LANDSCAPE_HEIGHT = "kbd_landscape_height";
    private final Set<OnSoftKeyboardChangedListener> softKeyboardChangedListeners = new HashSet<>();
    private boolean softKeyboardOpen = false;
    private int minEmojiPickerHeight;

    public interface OnSoftKeyboardChangedListener {
        void onKeyboardHidden();

        void onKeyboardShown();
    }

    public void addOnSoftKeyboardChangedListener(OnSoftKeyboardChangedListener listener) {
        softKeyboardChangedListeners.add(listener);
    }

    public void removeOnSoftKeyboardChangedListener(OnSoftKeyboardChangedListener listener) {
        softKeyboardChangedListeners.remove(listener);
    }

    public void notifySoftKeyboardHidden() {
        final Set<OnSoftKeyboardChangedListener> listeners = new HashSet<>(softKeyboardChangedListeners);
        for (OnSoftKeyboardChangedListener listener : listeners) {
            listener.onKeyboardHidden();
        }
    }

    public void notifySoftKeyboardShown() {
        final Set<OnSoftKeyboardChangedListener> listeners = new HashSet<>(softKeyboardChangedListeners);
        for (OnSoftKeyboardChangedListener listener : listeners) {
            listener.onKeyboardShown();
        }
    }

    public void onSoftKeyboardOpened(int softKeyboardHeight) {
        logger.debug("Soft keyboard open detected");

        if (!this.softKeyboardOpen) {
            this.softKeyboardOpen = true;
            saveSoftKeyboardHeight(softKeyboardHeight);
            notifySoftKeyboardShown();
        }
    }

    public void onSoftKeyboardClosed() {
        logger.debug("Soft keyboard closed");

        if (this.softKeyboardOpen) {
            this.softKeyboardOpen = false;
            notifySoftKeyboardHidden();
        }
    }

    public void runOnSoftKeyboardClose(final Runnable runnable) {
        if (this.softKeyboardOpen) {
            addOnSoftKeyboardChangedListener(new OnSoftKeyboardChangedListener() {
                @Override
                public void onKeyboardHidden() {
                    removeOnSoftKeyboardChangedListener(this);
                    runnable.run();
                }

                @Override
                public void onKeyboardShown() {
                }
            });
        } else {
            runnable.run();
        }
    }

    public void runOnSoftKeyboardOpen(@NonNull final Runnable runnable) {
        if (!isSoftKeyboardOpen()) {
            addOnSoftKeyboardChangedListener(new OnSoftKeyboardChangedListener() {
                @Override
                public void onKeyboardShown() {
                    removeOnSoftKeyboardChangedListener(this);
                    runnable.run();
                }

                @Override
                public void onKeyboardHidden() {
                }
            });
        } else {
            runnable.run();
        }
    }

    @UiThread
    public void openSoftKeyboard(@NonNull final EditText messageText) {
        EditTextUtil.focusWindowAndShowSoftKeyboard(messageText);
    }

    public boolean isSoftKeyboardOpen() {
        return softKeyboardOpen;
    }

    public void saveSoftKeyboardHeight(int softKeyboardHeight) {
        if (ConfigUtils.isLandscape(this)) {
            logger.info("Keyboard height (landscape): {}", softKeyboardHeight);
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putInt(LANDSCAPE_HEIGHT, softKeyboardHeight).apply();
        } else {
            logger.info("Keyboard height (portrait): {}", softKeyboardHeight);
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putInt(PORTRAIT_HEIGHT, softKeyboardHeight).apply();
        }
    }

    public int loadStoredSoftKeyboardHeight() {
        boolean isLandscape = ConfigUtils.isLandscape(this);

        int savedSoftKeyboardHeight = isLandscape ?
            PreferenceManager.getDefaultSharedPreferences(this).getInt(LANDSCAPE_HEIGHT, getResources().getDimensionPixelSize(R.dimen.default_emoji_picker_height_landscape)) :
            PreferenceManager.getDefaultSharedPreferences(this).getInt(PORTRAIT_HEIGHT, getResources().getDimensionPixelSize(R.dimen.default_emoji_picker_height));

        if (savedSoftKeyboardHeight < minEmojiPickerHeight) {
            return getResources().getDimensionPixelSize(isLandscape ?
                R.dimen.default_emoji_picker_height_landscape :
                R.dimen.default_emoji_picker_height);
        }

        return savedSoftKeyboardHeight;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        loadStoredSoftKeyboardHeight();

        super.onConfigurationChanged(newConfig);
    }

    public void resetKeyboard() {
        minEmojiPickerHeight = getResources().getDimensionPixelSize(R.dimen.min_emoji_keyboard_height);

        softKeyboardChangedListeners.clear();
        softKeyboardOpen = false;
    }

    @Override
    protected void onDestroy() {
        softKeyboardChangedListeners.clear();
        super.onDestroy();
    }
}
