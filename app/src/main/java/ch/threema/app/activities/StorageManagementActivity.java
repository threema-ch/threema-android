/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.ActionBar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import ch.threema.app.R;
import ch.threema.app.asynctasks.DeleteIdentityAsyncTask;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.MessageType;

public class StorageManagementActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener, CancelableHorizontalProgressDialog.ProgressDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(StorageManagementActivity.class);

	private final static String DELETE_CONFIRM_TAG = "delconf";
	private final static String DELETE_PROGRESS_TAG = "delprog";
	private static final String DELETE_MESSAGES_CONFIRM_TAG = "delmsgsconf";
	private static final String DELETE_MESSAGES_PROGRESS_TAG = "delmsgs";
	private static final String DIALOG_TAG_DELETE_ID = "delid";
	private static final String DIALOG_TAG_REALLY_DELETE = "rlydelete";

	private FileService fileService;
	private MessageService messageService;
	private ConversationService conversationService;
	private TextView totalView, usageView, freeView, messageView, inuseView;
	private Spinner timeSpinner, messageTimeSpinner;
	private Button deleteButton, messageDeleteButton;
	private ProgressBar progressBar;
	private boolean isCancelled, isMessageDeleteCancelled;
	private int selectedSpinnerItem, selectedMessageSpinnerItem;
	private FrameLayout storageFull, storageThreema, storageEmpty;
	private CoordinatorLayout coordinatorLayout;
	private int[] dayValues = {730, 365, 183, 92, 31, 7, 0};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.storage_management);
		}

		try {
			this.fileService = serviceManager.getFileService();
			this.messageService = serviceManager.getMessageService();
			this.conversationService = serviceManager.getConversationService();
		} catch (Exception e) {
			logger.error("Exception", e);
			finish();
			return;
		}

		coordinatorLayout = findViewById(R.id.content);
		totalView = findViewById(R.id.total_view);
		usageView = findViewById(R.id.usage_view);
		freeView = findViewById(R.id.free_view);
		inuseView = findViewById(R.id.in_use_view);
		messageView = findViewById(R.id.num_messages_view);
		timeSpinner = findViewById(R.id.time_spinner);
		messageTimeSpinner = findViewById(R.id.time_spinner_messages);
		deleteButton = findViewById(R.id.delete_button);
		messageDeleteButton = findViewById(R.id.delete_button_messages);
		storageFull = findViewById(R.id.storage_full);
		storageThreema = findViewById(R.id.storage_threema);
		storageEmpty = findViewById(R.id.storage_empty);
		progressBar = findViewById(R.id.progressbar);
		selectedSpinnerItem = 0;
		selectedMessageSpinnerItem = 0;

		if (deleteButton == null) {
			logger.info("deleteButton is null");
			finish();
			return;
		}

		deleteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GenericAlertDialog.newInstance(R.string.delete_data, R.string.delete_date_confirm_message, R.string.delete_data, R.string.cancel).show(getSupportFragmentManager(), DELETE_CONFIRM_TAG);
			}
		});

		messageDeleteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GenericAlertDialog.newInstance(R.string.delete_message, R.string.really_delete_messages, R.string.delete_message, R.string.cancel).show(getSupportFragmentManager(), DELETE_MESSAGES_CONFIRM_TAG);
			}
		});

		Button deleteAllButton = findViewById(R.id.delete_everything_button);
		deleteAllButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GenericAlertDialog.newInstance(
					R.string.delete_id_title,
					R.string.delete_id_message,
					R.string.delete_everything,
					R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_DELETE_ID);
			}
		});

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.storagemanager_timeout, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		timeSpinner.setAdapter(adapter);
		timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				selectedSpinnerItem = position;
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				selectedSpinnerItem = 0;
			}
		});

		ArrayAdapter<CharSequence> messageCleanupAdapter = ArrayAdapter.createFromResource(this,
				R.array.storagemanager_timeout, android.R.layout.simple_spinner_item);
		messageCleanupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		messageTimeSpinner.setAdapter(messageCleanupAdapter);
		messageTimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				selectedMessageSpinnerItem = position;
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				selectedMessageSpinnerItem = 0;
			}
		});

		storageFull.post(new Runnable() {
			@Override
			public void run() {
				updateStorageDisplay();
			}
		});
	}

	@SuppressLint("StaticFieldLeak")
	private void updateStorageDisplay() {
		new AsyncTask<Void, Void, Void>() {
			long total, usage, free, messages;

			@Override
			protected void onPreExecute() {
				progressBar.setVisibility(View.VISIBLE);
			}

			@Override
			protected Void doInBackground(Void... params) {
				total = fileService.getInternalStorageSize();
				usage = fileService.getInternalStorageUsage();
				free = fileService.getInternalStorageFree();
				messages = messageService.getTotalMessageCount();

				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				messageView.setText(String.valueOf(messages));
				progressBar.setVisibility(View.GONE);

				totalView.setText(Formatter.formatFileSize(StorageManagementActivity.this, total));
				usageView.setText(Formatter.formatFileSize(StorageManagementActivity.this, usage));
				freeView.setText(Formatter.formatFileSize(StorageManagementActivity.this, free));

				if (total > 0) {
					inuseView.setText(Formatter.formatFileSize(StorageManagementActivity.this, total - free));

					int fullWidth = storageFull.getWidth();
					storageThreema.setLayoutParams(new FrameLayout.LayoutParams((int) (fullWidth * usage / total), FrameLayout.LayoutParams.MATCH_PARENT));
					FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) (fullWidth * free / total), FrameLayout.LayoutParams.MATCH_PARENT);
					params.gravity = Gravity.RIGHT;
					storageEmpty.setLayoutParams(params);
				} else {
					inuseView.setText(Formatter.formatFileSize(StorageManagementActivity.this, 0));

					storageFull.setVisibility(View.GONE);
					storageThreema.setVisibility(View.GONE);
					storageEmpty.setVisibility(View.GONE);
				}
			}
		}.execute();
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_storagemanagement;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * TODO: replace with Date.before
	 *
	 * @param d1
	 * @param d2
	 * @return
	 */
	private long getDifferenceDays(Date d1, Date d2) {
		if (d1 != null && d2 != null) {
			long diff = d2.getTime() - d1.getTime();
			return TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
		}
		return 0;
	}

	@SuppressLint("StaticFieldLeak")
	private boolean deleteMessages(final int days) {
		final Date today = new Date();

		new AsyncTask<Void, Integer, Void>() {
			int delCount = 0;

			@Override
			protected void onPreExecute() {
				isMessageDeleteCancelled = false;
				CancelableHorizontalProgressDialog.newInstance(R.string.delete_message, 0, R.string.cancel, 100).show(getSupportFragmentManager(), DELETE_MESSAGES_PROGRESS_TAG);
			}

			@Override
			protected void onProgressUpdate(Integer... values) {
				DialogUtil.updateProgress(getSupportFragmentManager(), DELETE_MESSAGES_PROGRESS_TAG, values[0]);
			}

			@Override
			protected Void doInBackground(Void... params) {
				final List<ConversationModel> conversations = new CopyOnWriteArrayList<>(conversationService.getAll(true));
				final int numConversations = conversations.size();
				int i = 0;

				for (Iterator<ConversationModel> iterator = conversations.iterator(); iterator.hasNext();) {
					ConversationModel conversationModel = iterator.next();

					if (isMessageDeleteCancelled) {
						// cancel task if aborted by user
						break;
					}
					publishProgress(i++ * 100 / numConversations);

					final List<AbstractMessageModel> messageModels = messageService.getMessagesForReceiver(conversationModel.getReceiver(), null);

					for (AbstractMessageModel messageModel : messageModels) {
						if (isMessageDeleteCancelled) {
							// cancel task if aborted by user
							break;
						}

						Date postedDate = messageModel.getPostedAt();
						if (postedDate == null) {
							postedDate = messageModel.getCreatedAt();
						}

						if (days == 0 || (postedDate != null && getDifferenceDays(postedDate, today) > days)) {
							messageService.remove(messageModel, true);
							delCount++;
						}
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DELETE_MESSAGES_PROGRESS_TAG, true);

				Snackbar.make(coordinatorLayout, String.valueOf(delCount) + " " + getString(R.string.message_deleted), Snackbar.LENGTH_LONG).show();

				updateStorageDisplay();

				conversationService.reset();

				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onModifiedAll();
					}
				});
			}
		}.execute();

		return false;
	}

	@SuppressLint("StaticFieldLeak")
	private boolean deleteMediaFiles(final int days) {
		final Date today = new Date();
		final MessageService.MessageFilter messageFilter = new MessageService.MessageFilter() {
			@Override
			public long getPageSize() {return 0;}

			@Override
			public Integer getPageReferenceId() {return null;}

			@Override
			public boolean withStatusMessages() {return false;}

			@Override
			public boolean withUnsaved() {return true;}

			@Override
			public boolean onlyUnread() {return false;}

			@Override
			public boolean onlyDownloaded() {return true;}

			@Override
			public MessageType[] types() {
				return new MessageType[]{MessageType.IMAGE, MessageType.VIDEO, MessageType.VOICEMESSAGE, MessageType.FILE};
			}

			@Override
			public int[] contentTypes() {
				return null;
			}
		};

		new AsyncTask<Void, Integer, Void>() {
			int delCount = 0;

			@Override
			protected void onPreExecute() {
				isCancelled = false;
				CancelableHorizontalProgressDialog.newInstance(R.string.delete_data, 0, R.string.cancel, 100).show(getSupportFragmentManager(), DELETE_PROGRESS_TAG);
			}

			@Override
			protected void onProgressUpdate(Integer... values) {
				DialogUtil.updateProgress(getSupportFragmentManager(), DELETE_PROGRESS_TAG, values[0]);
			}

			@Override
			protected Void doInBackground(Void... params) {
				final List<ConversationModel> conversations = new ArrayList<>(conversationService.getAll(true));
				final int numConversations = conversations.size();
				int i = 0;

				for (ConversationModel conversationModel : conversations) {
					if (isCancelled) {
						// cancel task if aborted by user
						break;
					}
					publishProgress(i++ * 100 / numConversations);

					final List<AbstractMessageModel> messageModels = messageService.getMessagesForReceiver(conversationModel.getReceiver(), messageFilter);

					for (AbstractMessageModel messageModel : messageModels) {
						if (isCancelled) {
							// cancel task if aborted by user
							break;
						}

						Date postedDate = messageModel.getPostedAt();
						if (postedDate == null) {
							postedDate = messageModel.getCreatedAt();
						}

						if (days == 0 || (postedDate != null && getDifferenceDays(postedDate, today) > days)) {
							if (fileService.removeMessageFiles(messageModel, false)) {
								delCount++;
							}
						}
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DELETE_PROGRESS_TAG, true);

				Snackbar.make(coordinatorLayout, String.format(getString(R.string.media_files_deleted), delCount), Snackbar.LENGTH_LONG).show();

				updateStorageDisplay();

				conversationService.reset();

				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onModifiedAll();
					}
				});
			}
		}.execute();

		return false;
	}

	@Override
	public void onYes(String tag, Object data) {
		if (tag.equals(DELETE_CONFIRM_TAG)) {
			deleteMediaFiles(dayValues[selectedSpinnerItem]);
		} else if (tag.equals(DELETE_MESSAGES_CONFIRM_TAG)) {
			deleteMessages(dayValues[selectedMessageSpinnerItem]);
		} else if (DIALOG_TAG_DELETE_ID.equals(tag)) {
			GenericAlertDialog.newInstance(
				R.string.delete_id_title,
				R.string.delete_id_message2,
				R.string.delete_everything,
				R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE);
		} else if (DIALOG_TAG_REALLY_DELETE.equals(tag)) {
			new DeleteIdentityAsyncTask(getSupportFragmentManager(), new Runnable() {
				@Override
				public void run() {
					finishAffinity();
					System.exit(0);
				}
			}).execute();
		}
	}

	@Override
	public void onNo(String tag, Object data) {

	}

	@Override
	public void onCancel(String tag, Object object) {
		if (tag.equals(DELETE_PROGRESS_TAG)) {
			isCancelled = true;
		} else if (tag.equals(DELETE_MESSAGES_PROGRESS_TAG)) {
			isMessageDeleteCancelled = true;
		}
	}
}
