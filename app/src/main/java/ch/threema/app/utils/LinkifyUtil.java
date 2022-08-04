/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;

import java.net.IDN;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.util.LinkifyCompat;
import androidx.core.view.GestureDetectorCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.adapters.decorators.ChatAdapterDecorator;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.MentionClickableSpan;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

import static android.content.Context.CLIPBOARD_SERVICE;

public class LinkifyUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LinkifyUtil");
	public static final String DIALOG_TAG_CONFIRM_LINK = "cnfl";
	private final Pattern compose, add, license, geo;
	private final GestureDetectorCompat gestureDetector;
	private boolean isLongClick;
	private Uri uri;

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

			public void onLongPress(MotionEvent e) {
				isLongClick = true;
				logger.debug("Longpress detected");

				if (uri != null) {
					ClipboardManager clipboard = (ClipboardManager) ThreemaApplication.getAppContext().getSystemService(CLIPBOARD_SERVICE);
					ClipData clip = ClipData.newPlainText(ThreemaApplication.getAppContext().getString(R.string.web_link), uri.toString());
					clipboard.setPrimaryClip(clip);
					Toast.makeText(ThreemaApplication.getAppContext(), ThreemaApplication.getAppContext().getString(R.string.link_copied, uri.toString()), Toast.LENGTH_SHORT).show();
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

	public void linkifyText(TextView bodyTextView, boolean includePhoneNumbers) {
		// do not linkify phone numbers in longer texts because things can get messy on samsung devices
		// which linkify every kind of number combination imaginable
		if (includePhoneNumbers) {
			LinkifyCompatUtil.addLinks(bodyTextView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
		} else {
			LinkifyCompatUtil.addLinks(bodyTextView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
		}

		// Add geo uris based on regex. Remove spans later when they contain invalid coordinates
		LinkifyCompat.addLinks(bodyTextView, this.geo, null);

		// Check that geo uris contain valid values
		CharSequence text = bodyTextView.getText();
		SpannableString spanString = SpannableString.valueOf(text);
		URLSpan[] allSpans = spanString.getSpans(0, text != null ? text.length() : 0, URLSpan.class);
		for (URLSpan span : allSpans) {
			String url = span.getURL();
			if (url != null && url.startsWith("geo:") && !GeoLocationUtil.isValidGeoUri(url)) {
				spanString.removeSpan(span);
			}
		}

		LinkifyCompat.addLinks(bodyTextView, this.add, null);
		LinkifyCompat.addLinks(bodyTextView, this.compose, null);
		LinkifyCompat.addLinks(bodyTextView, this.license, null);
	}

	/**
	 * Linkify (Add links to) TextView hosted in a chat bubble
	 *
	 * @param fragment The hosting Fragment
	 * @param bodyTextView TextView where linkify will be applied to
	 * @param messageModel MessageModel of the message
	 * @param includePhoneNumbers Whether to linkify number sequences that may represent phone number
	 * @param actionModeEnabled Whether action mode (item selection) is currently enabled
	 * @param onClickElement Callback to which unhandled clicks are forwarded
	 */
	public void linkify(@NonNull ComposeMessageFragment fragment,
	                    @NonNull TextView bodyTextView,
	                    @NonNull AbstractMessageModel messageModel,
	                    boolean includePhoneNumbers,
	                    boolean actionModeEnabled,
	                    @Nullable ChatAdapterDecorator.OnClickElement onClickElement) {
		linkify(fragment, null, bodyTextView, messageModel, includePhoneNumbers, actionModeEnabled, onClickElement);
	}

	/**
	 * Linkify (Add links to) TextView hosted in a chat bubble
	 *
	 * @param fragment The hosting Fragment
	 * @param activity The hosting activity (fragment must be null)
	 * @param bodyTextView TextView where linkify will be applied to
	 * @param messageModel MessageModel of the message
	 * @param includePhoneNumbers Whether to linkify number sequences that may represent phone number
	 * @param actionModeEnabled Whether action mode (item selection) is currently enabled
	 * @param onClickElement Callback to which unhandled clicks are forwarded
	 */
	@SuppressLint("ClickableViewAccessibility")
	public void linkify(@Nullable ComposeMessageFragment fragment,
	                    @Nullable AppCompatActivity activity,
	                    @NonNull TextView bodyTextView,
	                    @NonNull AbstractMessageModel messageModel,
	                    boolean includePhoneNumbers,
	                    boolean actionModeEnabled,
	                    @Nullable ChatAdapterDecorator.OnClickElement onClickElement) {
		Context context = fragment != null ? fragment.getContext() : activity;

		linkifyText(bodyTextView, includePhoneNumbers);

		if (context == null) {
			return;
		}

		if (fragment != null) {
			// handle click on linkify here - otherwise it confuses the listview
			bodyTextView.setMovementMethod(null);
		}

		isLongClick = false;

		// handle taps on links
		bodyTextView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				TextView widget = (TextView) v;
				Object text = widget.getText();

				// we're only interested in spannables
				if (!(text instanceof Spannable)) {
					return false;
				}

				Spannable buffer = (Spannable) text;
				int action = event.getAction();

				int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
				int y = (int) event.getY()- widget.getTotalPaddingTop() + widget.getScrollY();

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
						if (!actionModeEnabled) {
							if (uri != null) {
								if (fragment == null) {
									Selection.removeSelection(buffer);
								}

								if (isLongClick) {
									isLongClick = false;
									return true;
								}

								if (UrlUtil.isLegalUri(uri)) {
									LinkifyUtil.this.openLink(context, uri);
								} else {
									String host = uri.getHost();
									if (!TestUtil.empty(host)) {
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
										GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.url_warning_title, warningMessage, R.string.ok, R.string.cancel);
										dialog.setData(uri);

										if (fragment != null) {
											dialog.setTargetFragment(fragment, 0);
											dialog.show(fragment.getFragmentManager(), DIALOG_TAG_CONFIRM_LINK);
										} else {
											dialog.show(activity.getSupportFragmentManager(), DIALOG_TAG_CONFIRM_LINK);
										}
									} else {
										LinkifyUtil.this.openLink(context, uri);
									}
								}
							} else if (link[0] instanceof MentionClickableSpan) {
								MentionClickableSpan clickableSpan = (MentionClickableSpan) link[0];

								if (!clickableSpan.getText().equals(ContactService.ALL_USERS_PLACEHOLDER_ID)) {
									Intent intent = new Intent(context, ContactDetailActivity.class);
									intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, clickableSpan.getText());
									intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
									context.startActivity(intent);
								}
							}
						} else {
							if (onClickElement != null) {
								onClickElement.onClick(messageModel);
							}
						}
						return true;
					case MotionEvent.ACTION_DOWN:
						logger.debug("ACTION_DOWN");
						if (fragment == null) {
							Selection.setSelection(buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]));
						}
						return true;
				}
				return false;
			}
		});
	}

	private @Nullable Uri getUriFromSpan(@Nullable ClickableSpan clickableSpan) {
		if (clickableSpan instanceof URLSpan) {
			URLSpan urlSpan = (URLSpan) clickableSpan;
			return Uri.parse(urlSpan.getURL());
		}
		return null;
	}

	public void openLink(@NonNull Context context, Uri uri) {
		// Open geo uris with internal map activity
		if (uri.toString().startsWith("geo:")) {
			GeoLocationUtil.viewLocation(context, uri);
			return;
		}

		Bundle bundle = new Bundle();
		bundle.putBoolean("new_window", true);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtras(bundle);
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_SHORT).show();
		}
	}

}
