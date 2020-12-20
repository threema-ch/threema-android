/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

package ch.threema.app.fragments.mediaviews;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;

abstract public class MediaViewFragment extends Fragment {
	private static final Logger logger = LoggerFactory.getLogger(MediaViewFragment.class);

	// enums are evil
	private final int ImageState_NONE = 0;
	private final int ImageState_THUMBNAIL = 1;
	private final int ImageState_DECRYPTED = 2;

	public interface OnMediaLoadListener {
		void decrypting();
		void decrypted(boolean success);
		void loaded(File file);
		void thumbnailLoaded(Bitmap bitmap);
	}

	public interface OnMediaOpenListener {
		void closed();
		void open();
	}

	private AbstractMessageModel messageModel;

	private Future threadFullDecrypt;
	private ExecutorService threadPoolExecutor = Executors.newSingleThreadExecutor();
	protected FileService fileService;
	protected MessageService messageService;
	private File[] decryptedFileCache;
	private OnMediaLoadListener onMediaLoadListener;
	private File decryptedFile;
	private int imageState = ImageState_NONE;
	private WeakReference<TextView> emptyTextViewReference;
	WeakReference<ViewGroup> rootViewReference;

	private Activity activity;
	private int position;

	private static final int KEEP_ALIVE_DELAY = 20000;
	private final static Handler keepAliveHandler = new Handler();
	private final Runnable keepAliveTask = new Runnable() {
		@Override
		public void run() {
			if (getActivity() != null) {
				ThreemaApplication.activityUserInteract(getActivity());
				keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
			}
		}
	};

	public MediaViewFragment() {
		super();
	}

	private void processBundle(Bundle bundle) {
		if (bundle != null) {
			this.position = bundle.getInt("position", 0);

			this.messageModel = ((MediaViewerActivity) this.activity).getMessageModel(this.position);
			this.decryptedFileCache = ((MediaViewerActivity) this.activity).getDecryptedFileCache();
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	public void setOnClickListener(View.OnClickListener onClickListener) {
	}

	public void setOnMediaOpenListener(OnMediaOpenListener onMediaOpenListener) {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			return null;
		}

		try {
			this.fileService = serviceManager.getFileService();
			this.messageService = serviceManager.getMessageService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return null;
		}

		ViewGroup rootView = (ViewGroup) inflater.inflate(this.getFragmentResourceId(), container, false);
		if (rootView != null) {
			// keep a reference to the textview
			this.rootViewReference = new WeakReference<>(rootView);
			this.emptyTextViewReference = new WeakReference<>(rootView.findViewById(R.id.empty_text));
		}

		processBundle(getArguments());

		this.created(savedInstanceState);
		this.decryptThumbnail();

		return rootView;
	}

	protected AbstractMessageModel getMessageModel() {
		return this.messageModel;
	}

	public void setOnImageLoaded(OnMediaLoadListener onMediaLoadListener) {
		this.onMediaLoadListener = onMediaLoadListener;

		//if image already loaded!
		this.fireLoadedFile();
	}

	public void killDecryptThread() {
		if(this.threadFullDecrypt != null) {
			this.threadFullDecrypt.cancel(true);
			this.threadFullDecrypt = null;
		}
	}

	private void fireLoadedFile() {
		if(TestUtil.required(this.onMediaLoadListener, this.decryptedFile)) {
			this.onMediaLoadListener.loaded(this.decryptedFile);
		}
	}

	private void decryptThumbnail() {
		if(TestUtil.required(this.messageModel, this.fileService)) {
			boolean isGeneric = false;

			logger.debug("show thumbnail of " + this.position);
			Bitmap thumbnail = null;
			try {
				thumbnail = this.fileService.getMessageThumbnailBitmap(messageModel, null);
			} catch (Exception e) {
				// no thumbnail file
			}

			String filename = null;

			if (thumbnail == null) {
				if (messageModel.getType() == MessageType.FILE) {
					thumbnail = BitmapUtil.tintImage(fileService.getDefaultMessageThumbnailBitmap(getContext(), messageModel, null, messageModel.getFileData().getMimeType()), getResources().getColor(R.color.material_dark_grey));
					filename = messageModel.getFileData().getFileName();
					isGeneric = true;
				} else if (messageModel.getType() == MessageType.VOICEMESSAGE) {
					thumbnail = BitmapUtil.getBitmapFromVectorDrawable(AppCompatResources.getDrawable(getContext(), R.drawable.ic_keyboard_voice_outline), getResources().getColor(R.color.material_dark_grey));
				}
			}

			if(thumbnail != null && !thumbnail.isRecycled()) {
				this.showThumbnail(thumbnail, isGeneric, filename);

				this.imageState = ImageState_THUMBNAIL;
				if(this.onMediaLoadListener != null) {
					this.onMediaLoadListener.thumbnailLoaded(thumbnail);
				}
			}
			else {
				this.showBrokenImage();
			}
		}
	}

	public void destroy() {
		if(TestUtil.required(this.messageModel)) {
			logger.debug("destroy decrypted image in fragment " + this.position);
		}

		this.killDecryptThread();
	}

	public void hide() {
		if(TestUtil.required(this.messageModel)) {
			logger.debug("hide fragment " + this.position);
		}
		this.killDecryptThread();
		this.decryptThumbnail();
	}

	public void showDecrypted() {
		this.killDecryptThread();

		logger.debug("showDecrypted " + position + " imageState = " + this.imageState);

		//already decrypted
		if(this.imageState == ImageState_DECRYPTED) {
			this.fireLoadedFile();
			return;
		}

		this.handleDecryptingFile();
		//use cached files!
		if(this.decryptedFileCache[this.position] != null && this.decryptedFileCache[this.position].exists()) {
			this.fileDecrypted(this.decryptedFileCache[this.position]);
			return;
		}

		//load decrypted image
		if(TestUtil.required(this.messageModel, this.fileService)) {
			this.killDecryptThread();

			this.threadFullDecrypt = threadPoolExecutor.submit(() -> {
				try {
					logger.debug("show decrypted of " + position);
					final File decrypted = fileService.getDecryptedMessageFile(messageModel);
					if (!TestUtil.required(decrypted) || !decrypted.exists()) {
						throw new Exception("Decrypted file not found");
					}

					// TODO: If the fragment has been destroyed in the meantime, stop calling any callbacks!
					RuntimeUtil.runOnUiThread(() -> {
						fileDecrypted(decrypted);

						if(TestUtil.required(onMediaLoadListener)) {
							onMediaLoadListener.decrypted(true);
						}
					});

				} catch (Exception x) {
					logger.error("Exception", x);
					RuntimeUtil.runOnUiThread(() -> {
						if(TestUtil.required(onMediaLoadListener)) {
							onMediaLoadListener.decrypted(false);
						}

						//reload thumbnail, if failed, show broken image!
						decryptThumbnail();
						handleDecryptFailure();
					});
				}
			});
		}
	}

	protected void showBrokenImage() {
		//TODO
		logger.debug("show broken image on position " + this.position);
		if (this.emptyTextViewReference != null && this.emptyTextViewReference.get() != null) {
			this.emptyTextViewReference.get().setText(R.string.media_file_not_found);
			this.emptyTextViewReference.get().setVisibility(View.VISIBLE);
		}
		this.imageState = ImageState_NONE;
	}

	private void fileDecrypted(File file) {
		if(!TestUtil.required(file) || !file.exists()) {
			return;
		}
		logger.debug( "file decrypted " + this.position);
		this.decryptedFile = file;
		this.decryptedFileCache[this.position] = this.decryptedFile;

		if (this.emptyTextViewReference != null && this.emptyTextViewReference.get() != null) {
			this.emptyTextViewReference.get().setVisibility(View.GONE);
		}

		this.handleDecryptedFile(file);
		this.imageState = ImageState_DECRYPTED;
		this.fireLoadedFile();
	}

	protected void keepScreenOn(boolean value) {
		if (value) {
			getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY);
		} else {
			getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			keepAliveHandler.removeCallbacks(keepAliveTask);
		}
	}

	protected void showUi(boolean show) {
		if (isAdded() && getActivity() != null) {
			if (show) {
				((MediaViewerActivity) getActivity()).showUi();
			} else {
				((MediaViewerActivity) getActivity()).hideUi();
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		keepAliveHandler.removeCallbacksAndMessages(null);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	protected abstract void created(Bundle savedInstanceState);
	protected abstract int getFragmentResourceId();
	public abstract boolean inquireClose();
	abstract protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename);
	abstract protected void hideThumbnail();
	protected abstract void handleDecryptingFile();
	protected abstract void handleDecryptFailure();
	abstract protected void handleDecryptedFile(File file);
}
