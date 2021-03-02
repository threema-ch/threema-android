/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
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
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.PaintSelectionPopup;
import ch.threema.app.ui.PaintView;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.BitmapWorkerTask;
import ch.threema.app.utils.BitmapWorkerTaskParams;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.TestUtil;

public class ImagePaintActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(ImagePaintActivity.class);

	private static final String DIALOG_TAG_COLOR_PICKER = "colp";
	private static final String KEY_PEN_COLOR = "pc";
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
	private int orientation, exifOrientation, flip, exifFlip, clipWidth, clipHeight;
	private Uri imageUri, outputUri;
	private ProgressBar progressBar;
	@ColorInt private int penColor;
	private MenuItem undoItem, paletteItem, paintItem, pencilItem, blurFacesItem;
	private PaintSelectionPopup paintSelectionPopup;
	private ArrayList<MotionEntity> undoHistory = new ArrayList<>();
	private boolean saveSemaphore = false;
	private int strokeMode = STROKE_MODE_BRUSH;

	@Override
	public int getLayoutResource() {
		return R.layout.activity_image_paint;
	}

	@Override
	public void onBackPressed() {
		if (hasChanges()) {
			GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
					R.string.draw,
					R.string.discard_changes,
					R.string.discard,
					R.string.cancel);
			dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_QUIT_CONFIRM);
		} else {
			finish();
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
		motionView.addEntityAndPosition(textEntity);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		}

		Intent intent = getIntent();
		MediaItem mediaItem = intent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (mediaItem == null) {
			finish();
			return;
		}

		this.imageUri = mediaItem.getUri();
		if (this.imageUri == null) {
			finish();
			return;
		}

		this.orientation = intent.getIntExtra(ThreemaApplication.EXTRA_ORIENTATION, 0);
		this.flip = intent.getIntExtra(ThreemaApplication.EXTRA_FLIP, BitmapUtil.FLIP_NONE);
		this.exifOrientation = intent.getIntExtra(ThreemaApplication.EXTRA_EXIF_ORIENTATION, 0);
		this.exifFlip = intent.getIntExtra(ThreemaApplication.EXTRA_EXIF_FLIP, BitmapUtil.FLIP_NONE);

		this.outputUri = intent.getParcelableExtra(ThreemaApplication.EXTRA_OUTPUT_FILE);

		setSupportActionBar(getToolbar());
		ActionBar actionBar = getSupportActionBar();

		if (actionBar == null) {
			finish();
			return;
		}

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle("");

		this.paintView = findViewById(R.id.paint_view);
		this.progressBar = findViewById(R.id.progress);
		this.imageView = findViewById(R.id.preview_image);
		this.motionView = findViewById(R.id.motion_view);

		this.penColor = getResources().getColor(R.color.material_red);
		if (savedInstanceState != null) {
			this.penColor = savedInstanceState.getInt(KEY_PEN_COLOR, penColor);
		}

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
			public void onLongClick(MotionEntity entity, int x, int y) {
				paintSelectionPopup.show((int) motionView.getX() + x, (int) motionView.getY() + y, !entity.hasFixedPositionAndSize());
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
			public void onItemSelected(int tag) {
				switch (tag) {
					case PaintSelectionPopup.TAG_REMOVE:
						deleteEntity();
						break;
					case PaintSelectionPopup.TAG_FLIP:
						flipEntity();
						break;
					case PaintSelectionPopup.TAG_TO_FRONT:
						bringToFrontEntity();
						break;
					default:
						break;
				}
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

		this.imageFrame = findViewById(R.id.content_frame);
		this.imageFrame.post(() -> loadImage());

		showTooltip();
	}

	private void loadImage() {
		BitmapWorkerTaskParams bitmapParams = new BitmapWorkerTaskParams();
		bitmapParams.imageUri = this.imageUri;
		bitmapParams.width = this.imageFrame.getWidth();
		bitmapParams.height = this.imageFrame.getHeight();
		bitmapParams.contentResolver = getContentResolver();
		bitmapParams.orientation = this.orientation;
		bitmapParams.flip = this.flip;
		bitmapParams.exifOrientation = this.exifOrientation;
		bitmapParams.exifFlip = this.exifFlip;

		logger.debug("screen height: " + bitmapParams.height);

		// load main image
		new BitmapWorkerTask(this.imageView) {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				imageView.setVisibility(View.INVISIBLE);
				paintView.setVisibility(View.INVISIBLE);
				motionView.setVisibility(View.INVISIBLE);
				progressBar.setVisibility(View.VISIBLE);
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				super.onPostExecute(bitmap);
				progressBar.setVisibility(View.GONE);
				imageView.setVisibility(View.VISIBLE);
				paintView.setVisibility(View.VISIBLE);
				motionView.setVisibility(View.VISIBLE);

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

				originalImageWidth = options.outWidth;
				originalImageHeight = options.outHeight;

				options.inPreferredConfig = Bitmap.Config.ARGB_8888;
				options.inJustDecodeBounds = false;

				try (InputStream data = getContentResolver().openInputStream(imageUri)) {
					if (data != null) {
						orgBitmap = BitmapFactory.decodeStream(new BufferedInputStream(data), null, options);
						if (orgBitmap != null) {
							bitmap = Bitmap.createBitmap(orgBitmap.getWidth() & ~0x1, orgBitmap.getHeight(), Bitmap.Config.RGB_565);
							new Canvas(bitmap).drawBitmap(orgBitmap, 0, 0, null);
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

		ConfigUtils.themeMenuItem(paletteItem, Color.WHITE);
		ConfigUtils.themeMenuItem(paintItem, Color.WHITE);
		ConfigUtils.themeMenuItem(pencilItem, Color.WHITE);

		if (motionView.getSelectedEntity() == null) {
			// no selected entities => draw mode or neutral mode
			if (paintView.getActive()) {
				if (this.strokeMode == STROKE_MODE_PENCIL) {
					ConfigUtils.themeMenuItem(pencilItem, this.penColor);
				} else {
					ConfigUtils.themeMenuItem(paintItem, this.penColor);
				}
			}
		}
		undoItem.setVisible(undoHistory.size() > 0);
		blurFacesItem.setVisible(motionView.getEntitiesCount() == 0);
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.activity_image_paint, menu);

		undoItem = menu.findItem(R.id.item_undo);
		paletteItem = menu.findItem(R.id.item_palette);
		paintItem = menu.findItem(R.id.item_draw);
		pencilItem = menu.findItem(R.id.item_pencil);
		blurFacesItem = menu.findItem(R.id.item_face);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
			case android.R.id.home:
				if (undoHistory.size() > 0) {
					item.setEnabled(false);
					renderImage();
				} else {
					finish();
				}
				return true;
			case R.id.item_undo:
				undo();
				break;
			case R.id.item_stickers:
				selectSticker();
				break;
			case R.id.item_palette:
				chooseColor();
				break;
			case R.id.item_text:
				enterText();
				break;
			case R.id.item_draw:
				if (strokeMode == STROKE_MODE_BRUSH && this.paintView.getActive()) {
					// switch to selection mode
					setDrawMode(false);
				} else {
					setStrokeMode(STROKE_MODE_BRUSH);
					setDrawMode(true);
				}
				break;
			case R.id.item_pencil:
				if (strokeMode == STROKE_MODE_PENCIL && this.paintView.getActive()) {
					// switch to selection mode
					setDrawMode(false);
				} else {
					setStrokeMode(STROKE_MODE_PENCIL);
					setDrawMode(true);
				}
				break;
			case R.id.item_face_blur:
				blurFaces(false);
				break;
			case R.id.item_face_emoji:
				blurFaces(true);
				break;
			default:
				break;
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
								.outerCircleColor(R.color.accent_dark)      // Specify a color for the outer circle
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

		this.imageFrame = findViewById(R.id.content_frame);
		if (this.imageFrame != null) {
			this.imageFrame.post(new Runnable() {
				@Override
				public void run() {
					loadImage();
				}
			});
		}
	}

	private void chooseColor() {
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
		colorPickerDialog.initialize(R.string.color_picker_default_title, colors, 0, 4, colors.length);
		colorPickerDialog.setSelectedColor(penColor);
		colorPickerDialog.setOnColorSelectedListener(new ColorPickerSwatch.OnColorSelectedListener() {
			@Override
			public void onColorSelected(int color) {
				paintView.setColor(color);
				penColor = color;

				ConfigUtils.themeMenuItem(paletteItem, penColor);
				if (motionView.getSelectedEntity() != null) {
					if (motionView.getSelectedEntity() instanceof TextEntity) {
						TextEntity textEntity = (TextEntity) motionView.getSelectedEntity();
						textEntity.getLayer().getFont().setColor(penColor);
						textEntity.updateEntity();
						motionView.invalidate();
					} else {
						// ignore color selection for stickers
					}
				} else {
					setDrawMode(true);
				}
			}
		});
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
							setResult(RESULT_OK);
							finish();
						} else {
							Toast.makeText(ImagePaintActivity.this, R.string.error_saving_file, Toast.LENGTH_SHORT).show();
						}
					}
				}.execute(bitmap);
			}
		}.execute(bitmapParams);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(KEY_PEN_COLOR, penColor);
	}

	@Override
	public void onYes(String tag, Object data) {
		finish();
	}

	@Override
	public void onNo(String tag, Object data) {}
}
