package ch.threema.app.webviews;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;

/**
 * Warning! Do not start an Activity extending this class from an application context!
 */
public abstract class SimpleWebViewActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {

    public static final String FORCE_DARK_THEME = "darkTheme";
    private static final String DIALOG_TAG_NO_CONNECTION = "nc";
    private LinearProgressIndicator progressBar;
    private WebView webView;

    private boolean isDarkThemeForced = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MaterialToolbar toolbar = findViewById(R.id.material_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.setTitle(getWebViewTitle());

        Intent intent = getIntent();
        final @Nullable Bundle extras = intent.getExtras();

        if (extras != null && extras.getBoolean(FORCE_DARK_THEME, false)) {
            isDarkThemeForced = true;
            if (getConnectionIndicator() != null) {
                // hide connection indicator when dark theme is forced
                getConnectionIndicator().setVisibility(View.INVISIBLE);
            }
        }

        if (!ConfigUtils.isTheDarkSide(this) && isDarkThemeForced) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        progressBar = findViewById(R.id.progress);
        webView = findViewById(R.id.simple_webview);
        webView.getSettings().setJavaScriptEnabled(requiresJavaScript());
        webView.setWebViewClient(new ThreemaWebViewClient(this::onNavigationRequest));

        if (requiresConnection()) {
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (newProgress >= 99) {
                        progressBar.setVisibility(View.INVISIBLE);
                    } else {
                        progressBar.setProgress(newProgress);
                    }
                }
            });
            checkConnection();
        } else {
            progressBar.setVisibility(View.GONE);

            loadWebView();
        }
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.webview_scroller),
            InsetSides.lbr()
        );
    }

    private boolean onNavigationRequest(@NonNull Uri url) {
        // We don't want to allow navigating to other pages within the web view,
        // so instead we open the URL in the external browser
        startActivity(new Intent(Intent.ACTION_VIEW, url));
        return true;
    }

    private void loadWebView() {
        final boolean isDarkTheme = ConfigUtils.isTheDarkSide(this) || isDarkThemeForced;
        webView.loadUrl(getWebViewUrl(isDarkTheme));
    }

    private void checkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected()) {
            loadWebView();
        } else {
            GenericAlertDialog.newInstance(getWebViewTitle(), R.string.internet_connection_required, R.string.retry, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_NO_CONNECTION);
        }
    }

    public int getLayoutResource() {
        return R.layout.activity_simple_webview;
    }

    @Override
    public void onYes(String tag, Object data) {
        checkConnection();
    }

    @Override
    public void onNo(String tag, Object data) {
        finish();
    }

    protected abstract @StringRes int getWebViewTitle();

    @NonNull
    protected abstract String getWebViewUrl(boolean isDarkTheme);

    protected boolean requiresConnection() {
        return true;
    }

    protected boolean requiresJavaScript() {
        return false;
    }
}

