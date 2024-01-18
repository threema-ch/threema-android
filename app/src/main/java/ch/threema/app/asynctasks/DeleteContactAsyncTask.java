/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;

import java.util.Iterator;
import java.util.Set;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.storage.models.ContactModel;

public class DeleteContactAsyncTask extends AsyncTask<Void, Integer, Integer> {
	private static final String DIALOG_TAG_DELETE_CONTACT = "dc";

	private final Set<ContactModel> contacts;
	private final ContactService contactService;
	private final FragmentManager fragmentManager;
	private final DeleteContactsPostRunnable runOnCompletion;
	private boolean cancelled = false;

	public static class DeleteContactsPostRunnable implements Runnable {
		protected Integer failed;

		protected void setFailed(Integer failed) {
			this.failed = failed;
		}

		@Override
		public void run() {}
	}

	public DeleteContactAsyncTask(FragmentManager fragmentManager,
	                              Set<ContactModel> contacts,
	                              ContactService contactService,
	                              DeleteContactsPostRunnable runOnCompletion) {

		this.contacts = contacts;
		this.contactService = contactService;
		this.fragmentManager = fragmentManager;
		this.runOnCompletion = runOnCompletion;
	}

	@Override
	protected void onPreExecute() {
		CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(R.string.deleting_contact, R.string.cancel, contacts.size());
		dialog.setOnCancelListener((dialog1, which) -> cancelled = true);
		dialog.show(fragmentManager, DIALOG_TAG_DELETE_CONTACT);

		ThreemaApplication.onAndroidContactChangeLock.lock();
	}

	@Override
	protected Integer doInBackground(Void... params) {
		int failed = 0, i = 0;
		Iterator<ContactModel> checkedItemsIterator = contacts.iterator();
		while (checkedItemsIterator.hasNext() && !cancelled) {
			publishProgress(i++);

			ContactModel contact = checkedItemsIterator.next();

			if (contact == null || !contactService.remove(contact)) {
				failed++;
			}
		}
		return failed;
	}

	@Override
	protected void onProgressUpdate(Integer... index) {
		DialogUtil.updateProgress(fragmentManager, DIALOG_TAG_DELETE_CONTACT, index[0] + 1);
	}

	@Override
	protected void onPostExecute(Integer failed) {
		DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_DELETE_CONTACT, true);

		// note: ContactListener.onRemoved() will be triggered by ContactStore.removeContact()

		if (runOnCompletion != null) {
			runOnCompletion.setFailed(failed);
			runOnCompletion.run();
		}

		ThreemaApplication.onAndroidContactChangeLock.unlock();
	}

	@Override
	protected void onCancelled(Integer integer) {
		super.onCancelled(integer);

		// Release the lock just in case this async task was cancelled
		ThreemaApplication.onAndroidContactChangeLock.unlock();
	}
}
