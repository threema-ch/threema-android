/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.emojis.EmojiButton;
import ch.threema.app.emojis.EmojiPicker;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.motionviews.FaceItem;
import ch.threema.app.motionviews.viewmodel.Font;
import ch.threema.app.motionviews.viewmodel.Layer;
import ch.threema.app.motionviews.viewmodel.TextLayer;
import ch.threema.app.motionviews.widget.FaceBlurEntity;
import ch.threema.app.motionviews.widget.FaceEmojiEntity;
import ch.threema.app.motionviews.widget.FaceEntity;
import ch.threema.app.motionviews.widget.ImageEntity;
import ch.threema.app.motionviews.widget.MotionEntity;
import ch.threema.app.motionviews.widget.MotionView;
import ch.threema.app.motionviews.widget.PathEntity;
import ch.threema.app.motionviews.widget.TextEntity;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.ComposeEditText;
import ch.threema.app.ui.LockableScrollView;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.PaintSelectionPopup;
import ch.threema.app.ui.PaintView;
import ch.threema.app.ui.SendButton;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.BitmapWorkerTask;
import ch.threema.app.utils.BitmapWorkerTaskParams;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.utils.BitmapUtil.FLIP_NONE;

public class ImagePaintActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ImagePaintActivity");

	private  enum ActivityMode {
		/**
		 * This is the mode where an image is taken as background and the user can draw on it.
		 */
		EDIT_IMAGE,
		/**
		 * In this mode, an image and a receiver is given and the user can directly send the image
		 * after drawing on it.
		 */
		IMAGE_REPLY,
		/**
		 * In this mode, only a receiver is given and the user can directly send the drawing without
		 * a background image.
		 */
		DRAWING
	}

	private static final String EXTRA_IMAGE_REPLY = "imageReply";
	private static final String EXTRA_GROUP_ID = "groupId";
	private static final String EXTRA_ACTIVITY_MODE = "activityMode";

	private static final String DIALOG_TAG_COLOR_PICKER = "colp";
	private static final String KEY_PEN_COLOR = "pc";
	private static final String KEY_BACKGROUND_COLOR = "bc";
	private static final int REQUEST_CODE_STICKER_SELECTOR = 44;
	private static final int REQUEST_CODE_ENTER_TEXT = 45;
	private static final String DIALOG_TAG_QUIT_CONFIRM = "qq";
	private static final String DIALOG_TAG_SAVING_IMAGE = "se";
	private static final String DIALOG_TAG_BLUR_FACES = "bf";

	private static final String SMILEY_PATH = "emojione/3_Emoji_classic/1f600.png";

	private static final int STROKE_MODE_BRUSH = 0;
	private static final int STROKE_MODE_PENCIL = 1;
	private static final int MAX_FACES = 16;

	private ImageView imageView;
	private PaintView paintView;
	private MotionView motionView;
	private FrameLayout imageFrame;
	private LockableScrollView scrollView;
	private ComposeEditText captionEditText;
	private ProgressBar progressBar;
	private EmojiPicker emojiPicker;

	private int orientation, exifOrientation, flip, exifFlip, clipWidth, clipHeight;

	private File inputFile;
	private Uri imageUri, outputUri;

	@ColorInt private int penColor, backgroundColor;

	private MenuItem undoItem, drawParentItem, paintItem, pencilItem, blurFacesItem;
	private Drawable brushIcon, pencilIcon;
	private PaintSelectionPopup paintSelectionPopup;
	private final ArrayList<MotionEntity> undoHistory = new ArrayList<>();
	private boolean saveSemaphore = false;
	private int strokeMode = STROKE_MODE_BRUSH;
	private ActivityMode activityMode = ActivityMode.EDIT_IMAGE;
	private int groupId = -1;
	private final ExecutorService threadPoolExecutor = Executors.newSingleThreadExecutor();

	/**
	 * Returns an intent to start the activity for editing a picture. The edited picture is stored
	 * in the output file. On success, the activity finishes with {@code RESULT_OK}. If the activity
	 * finishes with {@code RESULT_CANCELED}, no changes were made or an error occurred.
	 *
	 * @param context    the context
	 * @param mediaItem  the media item containing the image uri and the orientation/flip information
	 * @param outputFile the file where the edited image is stored in
	 * @return the intent to start the {@code ImagePaintActivity}
	 */
	public static Intent getImageEditIntent(
		@NonNull Context context,
		@NonNull MediaItem mediaItem,
		@NonNull File outputFile
	) {
		Intent intent = new Intent(context, ImagePaintActivity.class);
		intent.putExtra(EXTRA_ACTIVITY_MODE, ActivityMode.EDIT_IMAGE.name());
		intent.putExtra(Intent.EXTRA_STREAM, mediaItem);
		intent.putExtra(ThreemaApplication.EXTRA_OUTPUT_FILE, Uri.fromFile(outputFile));
		return intent;
	}

	/**
	 * Returns an intent to start the activity for creating a fast reply. The edited picture is
	 * stored in the output file. The message receiver and the updated media item will be part of
	 * the activity result data.
	 *
	 * @param context         the context
	 * @param mediaItem       the media item containing the image uri
	 * @param outputFile      the output file where the edited image is stored in
	 * @param messageReceiver the message receiver
	 * @param groupModel      the group model (if sent to a group) for mentions
	 * @return the intent to start the {@code ImagePaintActivity}
	 */
	public static Intent getImageReplyIntent(
		@NonNull Context context,
		@NonNull MediaItem mediaItem,
		@NonNull File outputFile,
		@SuppressWarnings("rawtypes") @NonNull MessageReceiver messageReceiver,
		@Nullable GroupModel groupModel
	) {
		Intent intent = new Intent(context, ImagePaintActivity.class);
		intent.putExtra(EXTRA_ACTIVITY_MODE, ActivityMode.IMAGE_REPLY.name());
		intent.putExtra(Intent.EXTRA_STREAM, mediaItem);
		intent.putExtra(ThreemaApplication.EXTRA_OUTPUT_FILE, Uri.fromFile(outputFile));
		intent.putExtra(ImagePaintActivity.EXTRA_IMAGE_REPLY, true);
		if (groupModel != null) {
			intent.putExtra(EXTRA_GROUP_ID, groupModel.getId());
		}
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
		return intent;
	}

	/**
	 * Returns an intent to start the activity for creating a drawing. The edited picture is stored
	 * in a file. The message receiver and the media item will be part of the activity result data.
	 *
	 * @param context         the context
	 * @param messageReceiver the message receiver
	 * @return the intent to start the {@code ImagePaintActivity}
	 */
	public static Intent getDrawingIntent(
		@NonNull Context context,
		@SuppressWarnings("rawtypes") @NonNull MessageReceiver messageReceiver
	) {
		Intent intent = new Intent(context, ImagePaintActivity.class);
		intent.putExtra(EXTRA_ACTIVITY_MODE, ActivityMode.DRAWING.name());
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
		return intent;
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_image_paint;
	}

	@Override
	public void onBackPressed() {
		if (hasChanges()) {
			GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
					R.string.discard_changes_title,
					R.string.discard_changes,
					R.string.discard,
					R.string.cancel);
			dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_QUIT_CONFIRM);
		} else {
			finishWithoutChanges();
		}
	}

	private boolean hasChanges() {
		return undoHistory.size() > 0;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK && data != null) {
			switch (requestCode) {
				case REQUEST_CODE_STICKER_SELECTOR:
					final String stickerPath = data.getStringExtra(StickerSelectorActivity.EXTRA_STICKER_PATH);
					if (!TestUtil.empty(stickerPath)) {
						addSticker(stickerPath);
					}
					break;
				case REQUEST_CODE_ENTER_TEXT:
					final String text = data.getStringExtra(ImagePaintKeyboardActivity.INTENT_EXTRA_TEXT);
					if (!TestUtil.empty(text)) {
						addText(text);
					}
			}
		}
	}

	private void addSticker(final String stickerPath) {
		paintView.setActive(false);

		new AsyncTask<Void, Void, Bitmap>() {
			@Override
			protected Bitmap doInBackground(Void... params) {
				try {
					return BitmapFactory.decodeStream(getAssets().open(stickerPath));
				} catch (IOException e) {
					logger.error("Exception", e);
					return null;
				}
			}

			@Override
			protected void onPostExecute(final Bitmap bitmap) {
				if (bitmap != null) {
					motionView.post(new Runnable() {
						@Override
						public void run() {
							Layer layer = new Layer();
							ImageEntity entity = new ImageEntity(layer, bitmap, motionView.getWidth(), motionView.getHeight());
							motionView.addEntityAndPosition(entity);
						}
					});
				}
			}
		}.execute();
	}

	private void addText(final String text) {
		paintView.setActive(false);

		TextLayer textLayer = new TextLayer();
		Font font = new Font();

		font.setColor(penColor);
		font.setSize(getResources().getDimensionPixelSize(R.dimen.imagepaint_default_font_size));

		textLayer.setFont(font);
		textLayer.setText(text);

		TextEntity textEntity = new TextEntity(textLayer, motionView.getWidth(),
				motionView.getHeight());
		textEntity.setColor(penColor);
		motionView.addEntityAndPosition(textEntity);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

		Intent intent = getIntent();

		groupId = intent.getIntExtra(EXTRA_GROUP_ID, -1);

		MediaItem mediaItem = intent.getParcelableExtra(Intent.EXTRA_STREAM);

		try {
			String activityModeOrdinal = intent.getStringExtra(EXTRA_ACTIVITY_MODE);
			activityMode = ActivityMode.valueOf(activityModeOrdinal);
		} catch (IllegalArgumentException e) {
			logger.error("Invalid activity mode", e);
			finishWithoutChanges();
			return;
		}

		if (mediaItem != null) {
			this.orientation = mediaItem.getRotation();
			this.flip = mediaItem.getFlip();
			this.exifOrientation = mediaItem.getExifRotation();
			this.exifFlip = mediaItem.getExifFlip();
		}

		this.outputUri = intent.getParcelableExtra(ThreemaApplication.EXTRA_OUTPUT_FILE);

		setSupportActionBar(getToolbar());
		ActionBar actionBar = getSupportActionBar();

		if (actionBar == null) {
			finishWithoutChanges();
			return;
		}

		actionBar.setDisplayHomeAsUpEnabled(activityMode == ActivityMode.EDIT_IMAGE);
		actionBar.setTitle("");

		this.paintView = findViewById(R.id.paint_view);
		this.progressBar = findViewById(R.id.progress);
		this.imageView = findViewById(R.id.preview_image);
		this.motionView = findViewById(R.id.motion_view);

		this.brushIcon = AppCompatResources.getDrawable(this, R.drawable.ic_brush);
		this.pencilIcon = AppCompatResources.getDrawable(this, R.drawable.ic_pencil_outline);

		this.penColor = getResources().getColor(R.color.material_red);
		this.backgroundColor = Color.WHITE;
		if (savedInstanceState != null) {
			this.penColor = savedInstanceState.getInt(KEY_PEN_COLOR, penColor);
			this.backgroundColor = savedInstanceState.getInt(KEY_BACKGROUND_COLOR, backgroundColor);
		}

		initializeCaptionEditText();

		// Lock the scroll view (the scroll view is needed so that the keyboard does not resize the drawing)
		scrollView = findViewById(R.id.content_scroll_view);
		scrollView.setScrollingEnabled(false);

		// Set the height of the image to the size of the scrollview
		this.imageFrame = findViewById(R.id.content_frame);

		this.paintView.setColor(penColor);
		this.paintView.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.imagepaint_brush_stroke_width));
		this.paintView.setTouchListener(new PaintView.TouchListener() {
			@Override
			public void onTouchUp() {
				invalidateOptionsMenu();
			}

			@Override
			public void onTouchDown() {
			}

			@Override
			public void onAdded() {
				undoHistory.add(new PathEntity());
			}

			@Override
			public void onDeleted() {
				if (undoHistory.size() > 0) {
					undoHistory.remove(undoHistory.size() - 1);
				}
			}
		});

		this.motionView.setTouchListener(new MotionView.TouchListener() {
			@Override
			public void onSelected(boolean isSelected) {
				invalidateOptionsMenu();
			}

			@Override
			public void onLongClick(@NonNull MotionEntity entity, int x, int y) {
				paintSelectionPopup.show((int) motionView.getX() + x, (int) motionView.getY() + y, entity);
			}

			@Override
			public void onAdded(MotionEntity entity) {
				undoHistory.add(entity);
			}

			@SuppressLint("UseValueOf")
			@Override
			public void onDeleted(MotionEntity entity) {
				undoHistory.remove(entity);
			}

			@Override
			public void onTouchUp() {
				if (!paintView.getActive()) {
					invalidateOptionsMenu();
				}
			}

			@Override
			public void onTouchDown() {
			}
		});

		this.paintSelectionPopup = new PaintSelectionPopup(this, this.motionView);
		this.paintSelectionPopup.setListener(new PaintSelectionPopup.PaintSelectPopupListener() {
			@Override
			public void onRemoveClicked() {
				deleteEntity();
			}

			@Override
			public void onFlipClicked() {
				flipEntity();
			}

			@Override
			public void onBringToFrontClicked() {
				bringToFrontEntity();
			}

			@Override
			public void onColorClicked() {
				colorEntity();
			}

			@Override
			public void onOpen() {
				motionView.setClickable(false);
				paintView.setClickable(false);
			}

			@Override
			public void onClose() {
				motionView.setClickable(true);
				paintView.setClickable(true);
			}
		});

		if (activityMode == ActivityMode.DRAWING) {
			inputFile = createDrawingInputFile();
			File outputFile = createDrawingOutputFile();

			if (inputFile == null || outputFile == null) {
				logger.error("Input file '{}' or output file '{}' is null", inputFile, outputFile);
				finishWithoutChanges();
				return;
			}

			imageUri = Uri.fromFile(inputFile);
			outputUri = Uri.fromFile(outputFile);

			createBackground(inputFile, Color.WHITE);
		} else {
			if (mediaItem == null || mediaItem.getUri() == null) {
				logger.error("No media uri given");
				finishWithoutChanges();
				return;
			}
			this.imageUri = mediaItem.getUri();
			loadImageOnLayout();
		}

		// Don't show tooltip when creating a drawing or for image replies
		if (activityMode == ActivityMode.EDIT_IMAGE) {
			showTooltip();
		}
	}

	/**
	 * Create a file that is used for the drawing input (the background)
	 */
	private File createDrawingInputFile() {
		try {
			return serviceManager.getFileService().createTempFile(".blank", ".png");
		} catch (IOException | FileSystemNotPresentException e) {
			logger.error("Error while creating temporary drawing input file");
			return null;
		}
	}

	/**
	 * Create a file that is used for the resulting output image (background + drawings)
	 */
	private File createDrawingOutputFile() {
		try {
			return serviceManager.getFileService().createTempFile(".drawing", ".png");
		} catch (IOException | FileSystemNotPresentException e) {
			logger.error("Error while creating temporary drawing output file", e);
			return null;
		}
	}

	/**
	 * Create a background with the given color and store it into the given file. Afterwards display
	 * the background.
	 *
	 * @param inputFile the file where the background is stored
	 * @param color     the color of the background
	 */
	private void createBackground(File inputFile, int color) {
		Futures.addCallback(
			getDrawingImageFuture(inputFile, color),
			new FutureCallback<>() {
				@Override
				public void onSuccess(@Nullable Void result) {
					loadImageOnLayout();
				}

				@Override
				public void onFailure(@NonNull Throwable t) {
					logger.error("Error while getting the image uri", t);
					finishWithoutChanges();
				}
			},
			ContextCompat.getMainExecutor(this)
		);
	}

	/**
	 * Get a listenable future that creates a background image of the given color and stores it in
	 * the given file.
	 *
	 * @param file  the file where the image of the given color is stored in
	 * @param color the color of the background
	 * @return the listenable future
	 */
	private ListenableFuture<Void> getDrawingImageFuture(@NonNull File file, int color) {
		ListeningExecutorService executorService = MoreExecutors.listeningDecorator(threadPoolExecutor);
		return executorService.submit(() -> {
			try {
				int dimension = ConfigUtils.getPreferredImageDimensions(PreferenceService.ImageScale_MEDIUM);
				Bitmap bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.RGB_565);
				Canvas canvas = new Canvas(bitmap);
				canvas.drawColor(color);
				bitmap.compress(Bitmap.CompressFormat.PNG, 0, new FileOutputStream(file));
			} catch (IOException e) {
				logger.error("Exception while creating blanc drawing", e);
			}
			return null;
		});
	}

	private void loadImage() {
		BitmapWorkerTaskParams bitmapParams = new BitmapWorkerTaskParams();
		bitmapParams.imageUri = this.imageUri;
		bitmapParams.width = this.imageFrame.getWidth();
		bitmapParams.height = this.scrollView.getHeight();
		bitmapParams.contentResolver = getContentResolver();
		bitmapParams.orientation = this.orientation;
		bitmapParams.flip = this.flip;
		bitmapParams.exifOrientation = this.exifOrientation;
		bitmapParams.exifFlip = this.exifFlip;

		logger.debug("screen height: {}", bitmapParams.height);

		// load main image
		new BitmapWorkerTask(this.imageView) {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				progressBar.setVisibility(View.VISIBLE);
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				super.onPostExecute(bitmap);
				progressBar.setVisibility(View.GONE);

				// clip other views to image size
				if (bitmap != null) {
					clipWidth = bitmap.getWidth();
					clipHeight = bitmap.getHeight();

					paintView.recalculate(clipWidth, clipHeight);
					resizeView(paintView, clipWidth, clipHeight);
					resizeView(motionView, clipWidth, clipHeight);
				}
			}
		}.execute(bitmapParams);
	}

	private void resizeView(View view, int width, int height) {
		ViewGroup.LayoutParams params = view.getLayoutParams();
		params.width = width;
		params.height = height;

		view.requestLayout();
	}

	private void selectSticker() {
		startActivityForResult(new Intent(ImagePaintActivity.this, StickerSelectorActivity.class), REQUEST_CODE_STICKER_SELECTOR);
		overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
	}

	private void enterText() {
		Intent intent = new Intent(ImagePaintActivity.this, ImagePaintKeyboardActivity.class);
		intent.putExtra(ImagePaintKeyboardActivity.INTENT_EXTRA_COLOR, penColor);
		startActivityForResult(intent, REQUEST_CODE_ENTER_TEXT);
		overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
	}

	@SuppressLint("StaticFieldLeak")
	private void blurFaces(final boolean useEmoji) {
		this.paintView.setActive(false);

		new AsyncTask<Void, Void, List<FaceItem>>() {
			int numFaces = -1;
			int originalImageWidth, originalImageHeight;

			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(-1, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_BLUR_FACES);
			}

			@Override
			protected List<FaceItem> doInBackground(Void... voids) {
				BitmapFactory.Options options;
				Bitmap bitmap, orgBitmap;
				List<FaceItem> faceItemList = new ArrayList<>();

				try (InputStream measure = getContentResolver().openInputStream(imageUri)) {
					options = BitmapUtil.getImageDimensions(measure);
				} catch (IOException | SecurityException | IllegalStateException | OutOfMemoryError e) {
					logger.error("Exception", e);
					return null;
				}

				if (options.outWidth < 16 || options.outHeight < 16) {
					return null;
				}

				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				options.inJustDecodeBounds = false;

				try (InputStream data = getContentResolver().openInputStream(imageUri)) {
					if (data != null) {
						orgBitmap = BitmapFactory.decodeStream(new BufferedInputStream(data), null, options);
						if (orgBitmap != null) {
							if (exifOrientation != 0 || exifFlip != FLIP_NONE) {
								orgBitmap = BitmapUtil.rotateBitmap(orgBitmap, exifOrientation, exifFlip);
							}
							if (orientation != 0 || flip != FLIP_NONE) {
								orgBitmap = BitmapUtil.rotateBitmap(orgBitmap, orientation, flip);
							}
							bitmap = Bitmap.createBitmap(orgBitmap.getWidth() & ~0x1, orgBitmap.getHeight(), Bitmap.Config.RGB_565);
							new Canvas(bitmap).drawBitmap(orgBitmap, 0, 0, null);

							originalImageWidth = orgBitmap.getWidth();
							originalImageHeight = orgBitmap.getHeight();
						} else {
							logger.info("could not open image");
							return null;
						}
					} else {
						logger.info("could not open input stream");
						return null;
					}
				} catch (Exception e) {
					logger.error("Exception", e);
					return null;
				}

				try {
					FaceDetector faceDetector = new FaceDetector(bitmap.getWidth(), bitmap.getHeight(), MAX_FACES);
					FaceDetector.Face[] faces = new FaceDetector.Face[MAX_FACES];

					numFaces = faceDetector.findFaces(bitmap, faces);
					if (numFaces < 1) {
						return null;
					}

					logger.debug("{} faces found.", numFaces);

					Bitmap emoji = null;
					if (useEmoji) {
						emoji = BitmapFactory.decodeStream(getAssets().open(SMILEY_PATH));
					}

					for (FaceDetector.Face face: faces) {
						if (face != null) {
							if (useEmoji) {
								faceItemList.add(new FaceItem(face, emoji, 1));
							} else {
								float offsetY = face.eyesDistance() * FaceEntity.BLUR_RADIUS;
								PointF midPoint = new PointF();
								face.getMidPoint(midPoint);

								int croppedBitmapSize = (int) (offsetY * 2);
								float scale = 1f;
								// pixelize large bitmaps
								if (croppedBitmapSize > 64) {
									scale = (float) croppedBitmapSize / 64f;
								}

								float scaleFactor = 1f / scale;
								Matrix matrix = new Matrix();
								matrix.setScale(scaleFactor, scaleFactor);

								Bitmap croppedBitmap = Bitmap.createBitmap(
									orgBitmap,
									offsetY > midPoint.x ? 0 : (int) (midPoint.x - offsetY),
									offsetY > midPoint.y ? 0 : (int) (midPoint.y - offsetY),
									croppedBitmapSize,
									croppedBitmapSize,
									matrix,
									false);

								faceItemList.add(new FaceItem(face, croppedBitmap, scale));
							}
						}
					}

					return faceItemList;
				} catch (Exception e) {
					logger.error("Face detection failed", e);
					return null;
				} finally {
					bitmap.recycle();
				}
			}

			@Override
			protected void onPostExecute(List<FaceItem> faceItemList) {
				if (faceItemList != null && faceItemList.size() > 0) {
					motionView.post(() -> {
						for (FaceItem faceItem : faceItemList) {
							Layer layer = new Layer();
							if (useEmoji) {
								FaceEmojiEntity entity = new FaceEmojiEntity(layer, faceItem, originalImageWidth, originalImageHeight, motionView.getWidth(), motionView.getHeight());
								motionView.addEntity(entity);
							} else {
								FaceBlurEntity entity = new FaceBlurEntity(layer, faceItem, originalImageWidth, originalImageHeight, motionView.getWidth(), motionView.getHeight());
								motionView.addEntity(entity);
							}
						}
					});
				} else {
					Toast.makeText(ImagePaintActivity.this, R.string.no_faces_detected, Toast.LENGTH_LONG).show();
				}

				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_BLUR_FACES, true);
			}
		}.execute();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (this.strokeMode == STROKE_MODE_PENCIL) {
			drawParentItem.setIcon(pencilIcon);
		} else {
			drawParentItem.setIcon(brushIcon);
		}

		ConfigUtils.themeMenuItem(drawParentItem, Color.WHITE);
		ConfigUtils.themeMenuItem(paintItem, Color.WHITE);
		ConfigUtils.themeMenuItem(pencilItem, Color.WHITE);

		if (motionView.getSelectedEntity() == null) {
			// no selected entities => draw mode or neutral mode
			if (paintView.getActive()) {
				if (this.strokeMode == STROKE_MODE_PENCIL) {
					ConfigUtils.themeMenuItem(pencilItem, this.penColor);
					drawParentItem.setIcon(pencilIcon);
					ConfigUtils.themeMenuItem(drawParentItem, this.penColor);
				} else {
					ConfigUtils.themeMenuItem(paintItem, this.penColor);
					drawParentItem.setIcon(brushIcon);
					ConfigUtils.themeMenuItem(drawParentItem, this.penColor);
				}
			}
		}
		undoItem.setVisible(undoHistory.size() > 0);
		blurFacesItem.setVisible(activityMode != ActivityMode.DRAWING && motionView.getEntitiesCount() == 0);
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.activity_image_paint, menu);

		undoItem = menu.findItem(R.id.item_undo);
		drawParentItem = menu.findItem(R.id.item_draw_parent);
		paintItem = menu.findItem(R.id.item_draw);
		pencilItem = menu.findItem(R.id.item_pencil);
		blurFacesItem = menu.findItem(R.id.item_face);

		if (activityMode == ActivityMode.DRAWING) {
			menu.findItem(R.id.item_background).setVisible(true);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		int id = item.getItemId();
		if (id == android.R.id.home) {
			if (undoHistory.size() > 0) {
				item.setEnabled(false);
				renderImage();
			} else {
				finishWithoutChanges();
			}
			return true;
		} else if (id == R.id.item_undo) {
			undo();
		} else if (id == R.id.item_stickers) {
			selectSticker();
		} else if (id == R.id.item_palette) {
			choosePenColor();
		} else if (id == R.id.item_text) {
			enterText();
		} else if (id == R.id.item_draw) {
			if (strokeMode == STROKE_MODE_BRUSH && this.paintView.getActive()) {
				// switch to selection mode
				setDrawMode(false);
			} else {
				setStrokeMode(STROKE_MODE_BRUSH);
				setDrawMode(true);
			}
		} else if (id == R.id.item_pencil) {
			if (strokeMode == STROKE_MODE_PENCIL && this.paintView.getActive()) {
				// switch to selection mode
				setDrawMode(false);
			} else {
				setStrokeMode(STROKE_MODE_PENCIL);
				setDrawMode(true);
			}
		} else if (id == R.id.item_face_blur) {
			blurFaces(false);
		} else if (id == R.id.item_face_emoji) {
			blurFaces(true);
		} else if (id == R.id.item_background) {
			chooseBackgroundColor();
		}
		return false;
	}

	@UiThread
	public void showTooltip() {
		if (!preferenceService.getIsFaceBlurTooltipShown()) {
			if (getToolbar() != null) {
				getToolbar().postDelayed(() -> {
					final View v = findViewById(R.id.item_face);
					try {
						TapTargetView.showFor(this,
							TapTarget.forView(v, getString(R.string.face_blur_tooltip_title), getString(R.string.face_blur_tooltip_text))
								.outerCircleColor(R.color.dark_accent)      // Specify a color for the outer circle
								.outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
								.targetCircleColor(android.R.color.white)   // Specify a color for the target circle
								.titleTextSize(24)                  // Specify the size (in sp) of the title text
								.titleTextColor(android.R.color.white)      // Specify the color of the title text
								.descriptionTextSize(18)            // Specify the size (in sp) of the description text
								.descriptionTextColor(android.R.color.white)  // Specify the color of the description text
								.textColor(android.R.color.white)            // Specify a color for both the title and description text
								.textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
								.dimColor(android.R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
								.drawShadow(true)                   // Whether to draw a drop shadow or not
								.cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
								.tintTarget(true)                   // Whether to tint the target view's color
								.transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
								.targetRadius(50)                  // Specify the target radius (in dp)
						);
						preferenceService.setFaceBlurTooltipShown(true);
					} catch (Exception ignore) {
						// catch null typeface exception on CROSSCALL Action-X3
					}
				}, 2000);
			}
		}
	}

	private void setStrokeMode(int strokeMode) {
		this.strokeMode = strokeMode;
		this.paintView.setStrokeWidth(
			getResources().getDimensionPixelSize(strokeMode == STROKE_MODE_PENCIL ?
				R.dimen.imagepaint_pencil_stroke_width :
				R.dimen.imagepaint_brush_stroke_width));
	}

	private void deleteEntity() {
		motionView.deletedSelectedEntity();
		invalidateOptionsMenu();
	}

	private void flipEntity() {
		motionView.flipSelectedEntity();
		invalidateOptionsMenu();
	}

	private void bringToFrontEntity() {
		motionView.moveSelectedEntityToFront();
		invalidateOptionsMenu();
	}

	private void colorEntity() {
		final MotionEntity selectedEntity = motionView.getSelectedEntity();
		if (selectedEntity == null) {
			logger.warn("Cannot change entity color when no entity is selected");
			return;
		}
		chooseColor(selectedEntity::setColor, selectedEntity.getColor());
	}

	private void undo() {
		if (undoHistory.size() > 0) {
			MotionEntity entity = undoHistory.get(undoHistory.size() - 1);

			motionView.unselectEntity();
			if (entity instanceof PathEntity) {
				paintView.undo();
			} else {
				motionView.deleteEntity(entity);
			}
			invalidateOptionsMenu();
		}
	}

	private void setDrawMode(boolean enable) {
		if (enable) {
			motionView.unselectEntity();
			paintView.setActive(true);
		} else {
			paintView.setActive(false);
		}
		invalidateOptionsMenu();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// hack to adjust toolbar height after rotate
		ConfigUtils.adjustToolbar(this, getToolbar());

		loadImageOnLayout();
	}

	/**
	 * Updates the image frame height on next layout of the scroll view
	 */
	private void loadImageOnLayout() {
		if (scrollView == null || imageFrame == null) {
			logger.warn("scrollView or imageFrame is null");
			return;
		}
		scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				int softKeyboardHeight = 0;
				if (isSoftKeyboardOpen()) {
					softKeyboardHeight = loadStoredSoftKeyboardHeight();
				}

				// If soft keyboard is open then add its height to the image frame
				imageFrame.setMinimumHeight(bottom - top + softKeyboardHeight);

				// If the image frame is larger than it's parent (scroll view), we need to wait for another relayout.
				// Otherwise we can remove this listener and load the image
				if (imageFrame.getMinimumHeight() <= scrollView.getHeight()) {
					scrollView.removeOnLayoutChangeListener(this);
					loadImage();
				}
			}
		});
		scrollView.requestLayout();
	}

	/**
	 * Show a color picker and set the selected color as pen color
	 */
	private void choosePenColor() {
		chooseColor(color -> {
			paintView.setColor(color);
			penColor = color;
			setDrawMode(true);
		}, penColor);
	}

	/**
	 * Show a color picker and writes the selected color to the input file.
	 */
	private void chooseBackgroundColor() {
		chooseColor(color -> {
			backgroundColor = color;
			createBackground(inputFile, color);
		}, backgroundColor);
	}

	private void chooseColor(@NonNull ColorPickerSwatch.OnColorSelectedListener colorSelectedListener, int selectedColor) {
		int[] colors = {
				getResources().getColor(R.color.material_cyan),
				getResources().getColor(R.color.material_blue),
				getResources().getColor(R.color.material_indigo),
				getResources().getColor(R.color.material_deep_purple),
				getResources().getColor(R.color.material_purple),
				getResources().getColor(R.color.material_pink),
				getResources().getColor(R.color.material_red),
				getResources().getColor(R.color.material_orange),
				getResources().getColor(R.color.material_amber),
				getResources().getColor(R.color.material_yellow),
				getResources().getColor(R.color.material_lime),
				getResources().getColor(R.color.material_green),
				getResources().getColor(R.color.material_green_700),
				getResources().getColor(R.color.material_teal),
				getResources().getColor(R.color.material_brown),
				getResources().getColor(R.color.material_grey_600),
				getResources().getColor(R.color.material_grey_500),
				getResources().getColor(R.color.material_grey_300),
				Color.WHITE,
				Color.BLACK,
		};

		ColorPickerDialog colorPickerDialog = new ColorPickerDialog();
		colorPickerDialog.initialize(R.string.color_picker_default_title, colors, selectedColor, 4, colors.length);
		colorPickerDialog.setOnColorSelectedListener(colorSelectedListener);
		colorPickerDialog.show(getSupportFragmentManager(), DIALOG_TAG_COLOR_PICKER);
	}

	private void renderImage() {
		logger.debug("renderImage");
		if (saveSemaphore) {
			return;
		}

		saveSemaphore = true;

		BitmapWorkerTaskParams bitmapParams = new BitmapWorkerTaskParams();
		bitmapParams.imageUri = this.imageUri;
		bitmapParams.contentResolver = getContentResolver();
		bitmapParams.orientation = this.orientation;
		bitmapParams.flip = this.flip;
		bitmapParams.exifOrientation = this.exifOrientation;
		bitmapParams.exifFlip = this.exifFlip;
		bitmapParams.mutable = true;

		new BitmapWorkerTask(null) {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();

				GenericProgressDialog.newInstance(R.string.draw, R.string.saving_media).show(getSupportFragmentManager(), DIALOG_TAG_SAVING_IMAGE);
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				Canvas canvas = new Canvas(bitmap);
				motionView.renderOverlay(canvas);
				paintView.renderOverlay(canvas, clipWidth, clipHeight);

				new AsyncTask<Bitmap, Void, Boolean>() {

					@Override
					protected Boolean doInBackground(Bitmap... params) {
						try {
							File output = new File(outputUri.getPath());

							FileOutputStream outputStream = new FileOutputStream(output);
							params[0].compress(Bitmap.CompressFormat.PNG, 100, outputStream);
							outputStream.flush();
							outputStream.close();
						} catch (Exception e) {
							return false;
						}
						return true;
					}

					@Override
					protected void onPostExecute(Boolean success) {
						DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_SAVING_IMAGE, true);

						if (success) {
							finishWithChanges();
						} else {
							Toast.makeText(ImagePaintActivity.this, R.string.error_saving_file, Toast.LENGTH_SHORT).show();
						}
					}
				}.execute(bitmap);
			}
		}.execute(bitmapParams);
	}

	private void initializeCaptionEditText() {
		if (activityMode == ActivityMode.EDIT_IMAGE) {
			// Don't show caption edit text when just editing the image
			return;
		}

		captionEditText = findViewById(R.id.caption_edittext);

		SendButton sendButton = findViewById(R.id.send_button);
		sendButton.setEnabled(true);
		sendButton.setOnClickListener(v -> renderImage());

		View bottomPanel = findViewById(R.id.bottom_panel);
		bottomPanel.setVisibility(View.VISIBLE);

		if (preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID) {
			initializeEmojiView();
		} else {
			findViewById(R.id.emoji_button).setVisibility(View.GONE);
			captionEditText.setPadding(getResources().getDimensionPixelSize(R.dimen.no_emoji_button_padding_left), this.captionEditText.getPaddingTop(), this.captionEditText.getPaddingRight(), this.captionEditText.getPaddingBottom());
		}

		if (groupId != -1) {
			initializeMentions();
		}

	}

	private void initializeMentions() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Cannot enable mention popup: serviceManager is null");
			return;
		}
		try {
			GroupService groupService = serviceManager.getGroupService();
			ContactService contactService = serviceManager.getContactService();
			UserService userService = serviceManager.getUserService();
			GroupModel groupModel = groupService.getById(groupId);

			if (groupModel == null) {
				logger.error("Cannot enable mention popup: no group model with id {} found", groupId);
				return;
			}

			captionEditText.enableMentionPopup(
				this,
				groupService,
				contactService,
				userService,
				preferenceService,
				groupModel
			);
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			logger.error("Cannot enable mention popup", e);
		}
	}

	@SuppressWarnings("deprecation")
	private void initializeEmojiView() {
		final EmojiPicker.EmojiKeyListener emojiKeyListener = new EmojiPicker.EmojiKeyListener() {
			@Override
			public void onBackspaceClick() {
				captionEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
			}

			@Override
			public void onEmojiClick(String emojiCodeString) {
				RuntimeUtil.runOnUiThread(() -> captionEditText.addEmoji(emojiCodeString));
			}

			@Override
			public void onShowPicker() {
				logger.info("onShowPicker");
				showEmojiPicker();
			}
		};

		EmojiButton emojiButton = findViewById(R.id.emoji_button);
		emojiButton.setOnClickListener(v -> showEmojiPicker());
		emojiButton.setColorFilter(getResources().getColor(android.R.color.white));

		emojiPicker = (EmojiPicker) ((ViewStub) findViewById(R.id.emoji_stub)).inflate();
		emojiPicker.init(ThreemaApplication.requireServiceManager().getEmojiService());
		emojiButton.attach(this.emojiPicker, preferenceService.isFullscreenIme());
		emojiPicker.setEmojiKeyListener(emojiKeyListener);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.image_paint_root).getRootView(), (v, insets) -> {
			if (insets.getSystemWindowInsetBottom() <= insets.getStableInsetBottom()) {
				onSoftKeyboardClosed();
			} else {
				onSoftKeyboardOpened(insets.getSystemWindowInsetBottom() - insets.getStableInsetBottom());
			}
			return insets;
		});

		addOnSoftKeyboardChangedListener(new OnSoftKeyboardChangedListener() {
			@Override
			public void onKeyboardHidden() {
				// Nothing to do
			}

			@Override
			public void onKeyboardShown() {
				if (emojiPicker != null && emojiPicker.isShown()) {
					emojiPicker.onKeyboardShown();
				}
			}
		});
	}

	private void showEmojiPicker() {
		if (isSoftKeyboardOpen() && !isEmojiPickerShown()) {
			logger.info("Show emoji picker after keyboard close");
			runOnSoftKeyboardClose(() -> {
				if (emojiPicker != null) {
					emojiPicker.show(loadStoredSoftKeyboardHeight());
				}
			});

			captionEditText.post(() -> EditTextUtil.hideSoftKeyboard(captionEditText));
		} else {
			if (emojiPicker != null) {
				if (emojiPicker.isShown()) {
					logger.info("EmojiPicker currently shown. Closing.");
					if (ConfigUtils.isLandscape(this) &&
						!ConfigUtils.isTabletLayout() &&
						preferenceService.isFullscreenIme()) {
						emojiPicker.hide();
					} else {
						openSoftKeyboard(emojiPicker, captionEditText);
						if (getResources().getConfiguration().keyboard == Configuration.KEYBOARD_QWERTY) {
							emojiPicker.hide();
						}
					}
				} else {
					emojiPicker.show(loadStoredSoftKeyboardHeight());
				}
			}
		}
	}

	private boolean isEmojiPickerShown() {
		return emojiPicker != null && emojiPicker.isShown();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(KEY_PEN_COLOR, penColor);
		outState.putInt(KEY_BACKGROUND_COLOR, backgroundColor);
	}

	@Override
	public void onYes(String tag, Object data) {
		finishWithoutChanges();
	}

	@Override
	public void onNo(String tag, Object data) {}

	/**
	 * Finish activity with changes (result ok)
	 */
	private void finishWithChanges() {
		if (activityMode == ActivityMode.IMAGE_REPLY || activityMode == ActivityMode.DRAWING) {
			MediaItem mediaItem = new MediaItem(outputUri, MediaItem.TYPE_IMAGE);
			if (captionEditText != null && captionEditText.getText() != null) {
				mediaItem.setCaption(captionEditText.getText().toString());
			}

			Intent result = new Intent();
			boolean messageReceiverCopied = IntentDataUtil.copyMessageReceiverFromIntentToIntent(this, getIntent(), result);
			if (!messageReceiverCopied) {
				logger.warn("Could not copy message receiver to intent");
				finishWithoutChanges();
				return;
			}
			result.putExtra(Intent.EXTRA_STREAM, mediaItem);

			setResult(RESULT_OK, result);
		} else {
			setResult(RESULT_OK);
		}
		finish();
	}

	/**
	 * Finish activity without changes (result canceled)
	 */
	private void finishWithoutChanges() {
		setResult(RESULT_CANCELED);
		finish();
	}

}
