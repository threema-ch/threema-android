/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;

import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import ch.threema.app.utils.TestUtil;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

/**
 * This is a copy of androidx.core.content.FileProvider that adds the option of providing a filename override.
 *
 * The default implementation always uses the actual filename of the file on the file system.
 * But there are cases when we do not want to use the real filename as it may just be a temporary file with a random name.
 */

public class NamedFileProvider extends FileProvider {
	private static final String
		META_DATA_FILE_PROVIDER_PATHS = "android.support.FILE_PROVIDER_PATHS";

	private static final String TAG_ROOT_PATH = "root-path";
	private static final String TAG_FILES_PATH = "files-path";
	private static final String TAG_CACHE_PATH = "cache-path";
	private static final String TAG_EXTERNAL = "external-path";
	private static final String TAG_EXTERNAL_FILES = "external-files-path";
	private static final String TAG_EXTERNAL_CACHE = "external-cache-path";
	private static final String TAG_EXTERNAL_MEDIA = "external-media-path";

	private static final String ATTR_NAME = "name";
	private static final String ATTR_PATH = "path";

	private static final File DEVICE_ROOT = new File("/");

	private static final String[] COLUMNS = { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };

	private PathStrategy mStrategy;

	@GuardedBy("sCache")
	private static final HashMap<String,PathStrategy> sCache = new HashMap<>();
	private static final SimpleArrayMap<Uri, String> sUriToDisplayNameMap =	new SimpleArrayMap<>();

	@Override
	public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
		super.attachInfo(context, info);

		mStrategy = getPathStrategy(context, info.authority);
	}

	@Override
	public Cursor query(@NonNull final Uri uri, String[] projection, final String selection,
	                    final String[] selectionArgs, final String sortOrder) {
		if (projection == null) {
			projection = COLUMNS;
		}

		final File file = mStrategy.getFileForUri(uri);

		String[] cols = new String[projection.length];
		Object[] values = new Object[projection.length];
		int i = 0;
		for (String col : projection) {
			if (OpenableColumns.DISPLAY_NAME.equals(col)) {
				cols[i] = OpenableColumns.DISPLAY_NAME;
				synchronized (sUriToDisplayNameMap) {
					if (TestUtil.empty(sUriToDisplayNameMap.get(uri))) {
						values[i++] = file.getName();
					} else {
						values[i++] = sUriToDisplayNameMap.get(uri);
					}
				}
			} else if (OpenableColumns.SIZE.equals(col)) {
				cols[i] = OpenableColumns.SIZE;
				values[i++] = file.length();
			}
		}

		cols = copyOf(cols, i);
		values = copyOf(values, i);

		final MatrixCursor cursor = new MatrixCursor(cols, 1);
		cursor.addRow(values);
		return cursor;
	}

	/**
	 * Return a content URI for a given {@link File}. Specific temporary
	 * permissions for the content URI can be set with
	 * {@link Context#grantUriPermission(String, Uri, int)}, or added
	 * to an {@link Intent} by calling {@link Intent#setData(Uri) setData()} and then
	 * {@link Intent#setFlags(int) setFlags()}; in both cases, the applicable flags are
	 * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION} and
	 * {@link Intent#FLAG_GRANT_WRITE_URI_PERMISSION}. A FileProvider can only return a
	 * <code>content</code> {@link Uri} for file paths defined in their <code>&lt;paths&gt;</code>
	 * meta-data element. See the Class Overview for more information.
	 *
	 * @param context A {@link Context} for the current component.
	 * @param authority The authority of a {@link FileProvider} defined in a
	 *            {@code <provider>} element in your app's manifest.
	 * @param file A {@link File} pointing to the filename for which you want a
	 * <code>content</code> {@link Uri}.
	 * @param filename File name to be used for this file. Will be provided to consumers in the DISPLAY_NAME xolumn
	 * @return A content URI for the file.
	 * @throws IllegalArgumentException When the given {@link File} is outside
	 * the paths supported by the provider.
	 */
	public static Uri getUriForFile(@NonNull Context context, @NonNull String authority,
	                                @NonNull File file, @Nullable String filename) {
		final Uri uri = FileProvider.getUriForFile(context, authority, file);
		if (!TestUtil.empty(filename)) {
			synchronized (sUriToDisplayNameMap) {
				sUriToDisplayNameMap.put(uri, filename);
			}
		}
		return uri;
	}

	/**
	 * Strategy for mapping between {@link File} and {@link Uri}.
	 * <p>
	 * Strategies must be symmetric so that mapping a {@link File} to a
	 * {@link Uri} and then back to a {@link File} points at the original
	 * target.
	 * <p>
	 * Strategies must remain consistent across app launches, and not rely on
	 * dynamic state. This ensures that any generated {@link Uri} can still be
	 * resolved if your process is killed and later restarted.
	 *
	 * @see SimplePathStrategy
	 */
	interface PathStrategy {
		/**
		 * Return a {@link File} that represents the given {@link Uri}.
		 */
		File getFileForUri(Uri uri);
	}

	/**
	 * Strategy that provides access to files living under a narrow whitelist of
	 * filesystem roots. It will throw {@link SecurityException} if callers try
	 * accessing files outside the configured roots.
	 * <p>
	 * For example, if configured with
	 * {@code addRoot("myfiles", context.getFilesDir())}, then
	 * {@code context.getFileStreamPath("foo.txt")} would map to
	 * {@code content://myauthority/myfiles/foo.txt}.
	 */
	static class SimplePathStrategy implements PathStrategy {
		private final HashMap<String, File> mRoots = new HashMap<String, File>();

		SimplePathStrategy(String authority) {
		}

		/**
		 * Add a mapping from a name to a filesystem root. The provider only offers
		 * access to files that live under configured roots.
		 */
		void addRoot(String name, File root) {
			if (TextUtils.isEmpty(name)) {
				throw new IllegalArgumentException("Name must not be empty");
			}

			try {
				// Resolve to canonical path to keep path checking fast
				root = root.getCanonicalFile();
			} catch (IOException e) {
				throw new IllegalArgumentException(
					"Failed to resolve canonical path for " + root, e);
			}

			mRoots.put(name, root);
		}

		@Override
		public File getFileForUri(Uri uri) {
			String path = uri.getEncodedPath();

			final int splitIndex = path.indexOf('/', 1);
			final String tag = Uri.decode(path.substring(1, splitIndex));
			path = Uri.decode(path.substring(splitIndex + 1));

			final File root = mRoots.get(tag);
			if (root == null) {
				throw new IllegalArgumentException("Unable to find configured root for " + uri);
			}

			File file = new File(root, path);
			try {
				file = file.getCanonicalFile();
			} catch (IOException e) {
				throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
			}

			if (!file.getPath().startsWith(root.getPath())) {
				throw new SecurityException("Resolved path jumped beyond configured root");
			}

			return file;
		}
	}


	/**
	 * Return {@link PathStrategy} for given authority, either by parsing or
	 * returning from cache.
	 */
	private static PathStrategy getPathStrategy(Context context, String authority) {
		PathStrategy strat;
		synchronized (sCache) {
			strat = sCache.get(authority);
			if (strat == null) {
				try {
					strat = parsePathStrategy(context, authority);
				} catch (IOException e) {
					throw new IllegalArgumentException(
						"Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e);
				} catch (XmlPullParserException e) {
					throw new IllegalArgumentException(
						"Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e);
				}
				sCache.put(authority, strat);
			}
		}
		return strat;
	}

	/**
	 * Parse and return {@link PathStrategy} for given authority as defined in
	 * {@link #META_DATA_FILE_PROVIDER_PATHS} {@code <meta-data>}.
	 */
	private static PathStrategy parsePathStrategy(Context context, String authority)
		throws IOException, XmlPullParserException {
		final SimplePathStrategy strat = new SimplePathStrategy(authority);

		final ProviderInfo info = context.getPackageManager()
			.resolveContentProvider(authority, PackageManager.GET_META_DATA);
		if (info == null) {
			throw new IllegalArgumentException(
				"Couldn't find meta-data for provider with authority " + authority);
		}

		final XmlResourceParser in = info.loadXmlMetaData(
			context.getPackageManager(), META_DATA_FILE_PROVIDER_PATHS);
		if (in == null) {
			throw new IllegalArgumentException(
				"Missing " + META_DATA_FILE_PROVIDER_PATHS + " meta-data");
		}

		int type;
		while ((type = in.next()) != END_DOCUMENT) {
			if (type == START_TAG) {
				final String tag = in.getName();

				final String name = in.getAttributeValue(null, ATTR_NAME);
				String path = in.getAttributeValue(null, ATTR_PATH);

				File target = null;
				if (TAG_ROOT_PATH.equals(tag)) {
					target = DEVICE_ROOT;
				} else if (TAG_FILES_PATH.equals(tag)) {
					target = context.getFilesDir();
				} else if (TAG_CACHE_PATH.equals(tag)) {
					target = context.getCacheDir();
				} else if (TAG_EXTERNAL.equals(tag)) {
					target = Environment.getExternalStorageDirectory();
				} else if (TAG_EXTERNAL_FILES.equals(tag)) {
					File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
					if (externalFilesDirs.length > 0) {
						target = externalFilesDirs[0];
					}
				} else if (TAG_EXTERNAL_CACHE.equals(tag)) {
					File[] externalCacheDirs = ContextCompat.getExternalCacheDirs(context);
					if (externalCacheDirs.length > 0) {
						target = externalCacheDirs[0];
					}
				} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
					&& TAG_EXTERNAL_MEDIA.equals(tag)) {
					File[] externalMediaDirs = context.getExternalMediaDirs();
					if (externalMediaDirs.length > 0) {
						target = externalMediaDirs[0];
					}
				}

				if (target != null) {
					strat.addRoot(name, buildPath(target, path));
				}
			}
		}
		return strat;
	}

	private static File buildPath(File base, String... segments) {
		File cur = base;
		for (String segment : segments) {
			if (segment != null) {
				cur = new File(cur, segment);
			}
		}
		return cur;
	}

	private static String[] copyOf(String[] original, int newLength) {
		final String[] result = new String[newLength];
		System.arraycopy(original, 0, result, 0, newLength);
		return result;
	}

	private static Object[] copyOf(Object[] original, int newLength) {
		final Object[] result = new Object[newLength];
		System.arraycopy(original, 0, result, 0, newLength);
		return result;
	}
}
