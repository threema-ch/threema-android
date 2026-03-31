package ch.threema.app.utils;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.TextView;

import org.slf4j.Logger;

import java.net.IDN;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.util.LinkifyCompat;
import androidx.core.view.GestureDetectorCompat;
import ch.threema.android.ToastDuration;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.contactdetails.ContactDetailActivity;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.MentionClickableSpan;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.models.AbstractMessageModel;

import static android.content.Context.CLIPBOARD_SERVICE;
import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class LinkifyUtil {
    private static final Logger logger = getThreemaLogger("LinkifyUtil");
    private final Pattern compose, add, license, geo;
    private final GestureDetectorCompat gestureDetector;
    private boolean isLongClick;
    private Uri uri;

    public interface UnhandledClickHandler {
        void onUnhandledClick(@NonNull AbstractMessageModel messageModel);
    }

    public interface BottomSheetGridDialogListener {
        void showBottomSheetGridDialog(ArrayList<BottomSheetItem> items);
    }

    public interface LinkifyListener extends LinkConfirmationListener, BottomSheetGridDialogListener {
        /**
         * Indicate whether clicks on links should be handled. If false is returned, clicks are dispatched
         * to the {@link UnhandledClickHandler} if present.
         *
         * @return `true` if linkified link clicks should be handled, false otherwise
         */
        boolean shouldHandleLinkClick();
    }

    public static void launchAddContactActivity(Context context, String packageName, String uriString) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(packageName);
        intent.setData(Uri.parse(uriString));
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // Singleton stuff
    private static LinkifyUtil sInstance = null;

    public static synchronized LinkifyUtil getInstance() {
        if (sInstance == null) {
            sInstance = new LinkifyUtil();
        }
        return sInstance;
    }

    private LinkifyUtil() {
        this.add = Pattern.compile("\\b" + BuildConfig.uriScheme + "://add\\?id=\\S{8}\\b");
        this.compose = Pattern.compile("\\b" + BuildConfig.uriScheme + "://compose\\?\\S+\\b");
        this.license = Pattern.compile("\\b" + BuildConfig.uriScheme + "://license\\?key=\\S{11}\\b");
        this.geo = GeoLocationUtil.getGeoUriPattern();
        this.gestureDetector = new GestureDetectorCompat(null, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                logger.debug("onDown detected");
                isLongClick = false;
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                logger.debug("onShowPress detected");
                isLongClick = false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                logger.debug("onSingleTapUp detected");
                isLongClick = false;
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                logger.debug("onScroll detected");
                isLongClick = false;
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                isLongClick = true;
                logger.debug("Longpress detected");

                if (uri != null) {
                    ClipboardManager clipboard = (ClipboardManager) ThreemaApplication.getAppContext().getSystemService(CLIPBOARD_SERVICE);
                    String contents;
                    @StringRes int label;
                    if ("mailto".equalsIgnoreCase(uri.getScheme())) {
                        contents = uri.getSchemeSpecificPart();
                        label = R.string.linked_email;
                    } else {
                        contents = uri.toString();
                        label = R.string.web_link;
                    }
                    ClipData clip = ClipData.newPlainText(ThreemaApplication.getAppContext().getString(label), contents);
                    clipboard.setPrimaryClip(clip);
                    var context = ThreemaApplication.getAppContext();
                    showToast(context, context.getString(R.string.link_copied, contents));
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                logger.debug("onFling detected");
                isLongClick = false;
                return false;
            }
        });
    }

    private Spanned linkifyText(CharSequence text, boolean includePhoneNumbers) {
        SpannableString spanString = SpannableString.valueOf(text);

        // do not linkify phone numbers in longer texts because things can get messy on samsung devices
        // which linkify every kind of number combination imaginable
        if (includePhoneNumbers) {
            LinkifyCompatUtil.addLinks(spanString, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
        } else {
            LinkifyCompatUtil.addLinks(spanString, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        }

        // Add geo uris based on regex. Remove spans later when they contain invalid coordinates
        LinkifyCompat.addLinks(spanString, this.geo, null);

        // Check that geo uris contain valid values
        URLSpan[] allSpans = spanString.getSpans(0, text != null ? text.length() : 0, URLSpan.class);
        for (URLSpan span : allSpans) {
            String url = span.getURL();
            if (url != null && url.startsWith("geo:") && !GeoLocationUtil.isValidGeoUri(url)) {
                spanString.removeSpan(span);
            }
        }

        LinkifyCompat.addLinks(spanString, this.add, null);
        LinkifyCompat.addLinks(spanString, this.compose, null);
        LinkifyCompat.addLinks(spanString, this.license, null);

        return spanString;
    }

    public void linkifyText(TextView bodyTextView, boolean includePhoneNumbers) {
        bodyTextView.setText(
            linkifyText(bodyTextView.getText(), includePhoneNumbers)
        );
    }

    /**
     * Linkify (Add links to) TextView hosted in a chat bubble
     *
     * @param bodyTextView                 TextView where linkify will be applied to
     * @param messageModel                 MessageModel of the message
     * @param includePhoneNumbers          Whether to linkify number sequences that may represent phone number
     * @param unhandledClickHandler        Callback to which unhandled clicks are forwarded
     * @param linkifyListener              Serves as a composite interface which provides
     *                                     LinkConfirmationListener, BottomSheetGridDialogListener
     *                                     and ActionModeStatus which determines whether action mode
     *                                     (item selection) is currently enabled
     */
    @SuppressLint("ClickableViewAccessibility")
    public void linkify(@NonNull TextView bodyTextView,
                        @Nullable AbstractMessageModel messageModel,
                        boolean includePhoneNumbers,
                        @Nullable UnhandledClickHandler unhandledClickHandler,
                        @NonNull LinkifyListener linkifyListener
    ) {
        linkifyText(bodyTextView, includePhoneNumbers);
        Context context = bodyTextView.getContext();

        isLongClick = false;

        // handle taps on links
        bodyTextView.setOnTouchListener((v, event) -> {
            TextView widget = (TextView) v;
            Object text = widget.getText();

            // we're only interested in spanned texts
            if (!(text instanceof Spanned)) {
                return false;
            }

            Spanned buffer = (Spanned) text;
            int action = event.getAction();

            int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
            int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

            Layout layout = widget.getLayout();
            if (layout == null) {
                return false;
            }

            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
            if (link.length == 0) {
                return false;
            }

            uri = getUriFromSpan(link[0]);

            gestureDetector.onTouchEvent(event);

            switch (action) {
                case MotionEvent.ACTION_UP:
                    logger.debug("ACTION_UP");
                    if (linkifyListener.shouldHandleLinkClick()) {
                        if (uri != null) {
                            if (buffer instanceof Spannable) {
                                Selection.removeSelection((Spannable) buffer);
                            }

                            if (isLongClick) {
                                isLongClick = false;
                                return true;
                            }

                            if (UrlUtil.isSafeUri(uri)) {
                                openLink(uri, context, linkifyListener);
                            } else {
                                String host = uri.getHost();
                                if (!TestUtil.isEmptyOrNull(host)) {
                                    String idnUrl = null;
                                    try {
                                        idnUrl = IDN.toASCII(host);
                                    } catch (IllegalArgumentException e) {
                                        logger.error("Exception", e);
                                    }

                                    String warningMessage;
                                    if (idnUrl != null) {
                                        warningMessage = String.format(context.getString(R.string.url_warning_body), host, idnUrl);
                                    } else {
                                        warningMessage = context.getString(R.string.url_warning_body_alt);
                                    }
                                    linkifyListener.onLinkNeedsConfirmation(warningMessage, uri);
                                } else {
                                    openLink(uri, context, linkifyListener);
                                }
                            }
                        } else if (link[0] instanceof MentionClickableSpan) {
                            MentionClickableSpan clickableSpan = (MentionClickableSpan) link[0];

                            if (!clickableSpan.getText().equals(ContactService.ALL_USERS_PLACEHOLDER_ID)) {
                                Intent intent = new Intent(context, ContactDetailActivity.class);
                                intent.putExtra(AppConstants.INTENT_DATA_CONTACT, clickableSpan.getText());
                                intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                context.startActivity(intent);
                            }
                        }
                    } else {
                        if (unhandledClickHandler != null && messageModel != null) {
                            unhandledClickHandler.onUnhandledClick(messageModel);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_DOWN:
                    logger.debug("ACTION_DOWN");
                    if (buffer instanceof Spannable) {
                        Selection.setSelection((Spannable) buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]));
                    }
                    return true;
            }
            return false;
        });
    }

    private @Nullable Uri getUriFromSpan(@Nullable ClickableSpan clickableSpan) {
        if (clickableSpan instanceof URLSpan) {
            URLSpan urlSpan = (URLSpan) clickableSpan;
            return Uri.parse(urlSpan.getURL());
        }
        return null;
    }

    /**
     * Open the provided uri with the suitable action:
     * - Geo URIs are opened in Threema's map view
     * - For add contact actions the add contact activity is opened
     * - An intent with {@link Intent#ACTION_VIEW} is started in all other cases
     *
     * @param uri      The uri to open
     * @param listener Callback which will be called to select an instance if multiple Threema
     *                 packages (e.g. Private, Work, OnPrem) are available for performing an
     *                 add-contact action. If set to null, an intent with {@link Intent#ACTION_VIEW}
     *                 is started for contact action urls which will usually open the url in a browser (which is fine).
     */
    public void openLink(
        Uri uri,
        @NonNull Context context,
        @Nullable BottomSheetGridDialogListener listener
    ) {
        // Open geo uris with internal map activity
        if (uri.toString().startsWith("geo:")) {
            if (!GeoLocationUtil.viewLocation(context, uri)) {
                showToast(context, R.string.an_error_occurred, ToastDuration.LONG);
            }
            return;
        }

        if (shouldProvideBottomSheetItems(uri)) {
            ArrayList<BottomSheetItem> items = generateBottomSheetItems(context);
            if (openContactLinkTargetAppSelector(items, context, listener)) {
                // intent has been handled
                return;
            } else {
                logger.warn("Could not open contact link target app selector. Selector listener not set.");
            }
        }

        Bundle bundle = new Bundle();
        bundle.putBoolean("new_window", true);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtras(bundle);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showToast(context, R.string.no_activity_for_intent);
        }
    }

    /**
     * Possible target Url schemes for adding Threema contacts
     */
    private final String[] addContactSchemes = {
        "threema",
        "threemawork",
        "threemablue",
        "threemaonprem"
    };

    /**
     * Query the PackageManager API (https://developer.android.com/reference/android/content/pm/PackageManager) to see which other Threema apps are installed.
     * Open a selector that allows picking a Threema app for opening an "add contact" link.
     * <p>
     * Return true if the link target app selector could be shown, false otherwise.
     */
    private boolean openContactLinkTargetAppSelector(
        ArrayList<BottomSheetItem> items,
        Context context,
        @Nullable BottomSheetGridDialogListener listener
    ) {
        if (items.isEmpty()) {
            return false;
        }
        if (items.size() == 1) {
            launchAddContactActivity(context, items.get(0).getTag(), items.get(0).getData());
            return true;
        }
        if (listener != null) {
            listener.showBottomSheetGridDialog(items);
            return true;
        }
        return false;
    }

    private boolean shouldProvideBottomSheetItems(Uri uri) {
        // handle contact Urls internally but ignore contact Urls with a query such as "text=hello"
        if (BuildConfig.contactActionUrl.equals(uri.getAuthority())) {
            List<String> pathSegments = uri.getPathSegments();
            return pathSegments.size() == 1 && pathSegments.get(0).length() == ProtocolDefines.IDENTITY_LEN && TestUtil.isEmptyOrNull(uri.getEncodedQuery());
        }
        return false;
    }

    private ArrayList<BottomSheetItem> generateBottomSheetItems(Context context) {
        ArrayList<BottomSheetItem> items = new ArrayList<>();
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            return items;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        List<ResolveInfo> resolveInfoList;

        for (String addContactScheme : addContactSchemes) {
            Uri targetUri = new Uri.Builder()
                .scheme(addContactScheme)
                .authority("add")
                .appendQueryParameter("id", uri.getLastPathSegment())
                .build();
            intent.setData(targetUri);
            resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resolveInfoList) {
                if (resolveInfo != null) {
                    CharSequence label = resolveInfo.loadLabel(packageManager);
                    Drawable icon = resolveInfo.loadIcon(packageManager);

                    if (icon != null) {
                        Bitmap bitmap = BitmapUtil.getBitmapFromVectorDrawable(icon, null);
                        if (bitmap != null) {
                            items.add(new BottomSheetItem(bitmap, label.toString(), resolveInfo.activityInfo.packageName, targetUri.toString()));
                        }
                    }
                }
            }
        }

        return items;
    }
}
