/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.AsyncTask;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.geometry.LatLng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import ch.threema.app.R;
import ch.threema.app.activities.MapActivity;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.LocationDataModel;

import static android.view.View.GONE;

public class LocationChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(LocationChatAdapterDecorator.class);

	public LocationChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		final LocationDataModel location = this.getMessageModel().getLocationData();

		TextView addressLine = holder.bodyTextView;

		this.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(!isInChoiceMode()) {
					viewLocation(getMessageModel(), v);
				}
			}
		}, holder.messageBlockView);

		//clear texts
		if(holder.bodyTextView != null) {
			holder.bodyTextView.setText("");
		}

		if(holder.secondaryTextView != null) {
			holder.secondaryTextView.setText("");
			holder.secondaryTextView.setVisibility(GONE);
		}

		if(!TestUtil.empty(location.getPoi())) {
			if(holder.bodyTextView != null) {
				holder.bodyTextView.setText(highlightMatches(location.getPoi(), filterString));
				holder.bodyTextView.setWidth(this.getThumbnailWidth());
				holder.bodyTextView.setVisibility(View.VISIBLE);
			}
			addressLine = holder.secondaryTextView;
		}

		if(addressLine != null) {
			String address = location.getAddress();
			if (address == null) {
				GeoLocationUtil geoLocation = new GeoLocationUtil(addressLine);
				Location l = new Location("X");
				l.setLatitude(location.getLatitude());
				l.setLongitude(location.getLongitude());
				l.setAccuracy(location.getAccuracy());
				geoLocation.updateAddressAndModel(getContext(), l);
			}

			if (address != null) {
				addressLine.setText(highlightMatches(address, filterString));
				addressLine.setWidth(this.getThumbnailWidth());
				addressLine.setVisibility(View.VISIBLE);
			}
		}

		new AsyncTask<ComposeMessageHolder, Void, RoundedBitmapDrawable>() {
			private ComposeMessageHolder holder;

			@Override
			protected RoundedBitmapDrawable doInBackground(ComposeMessageHolder... params) {
				this.holder = params[0];

				try {
					Bitmap locationBitmap = getFileService().getMessageThumbnailBitmap(getMessageModel(), getThumbnailCache());
					if (locationBitmap != null) {
						RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getContext().getResources(), BitmapUtil.cropToSquare(locationBitmap));
						drawable.setCircular(true);
						return drawable;
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
				return null;
			}

			@Override
			protected void onPostExecute(RoundedBitmapDrawable drawable) {
				if (position == holder.position) {
					if (drawable == null) {
						holder.controller.setBackgroundImage(null);
						holder.controller.setImageResource(R.drawable.ic_map_marker_outline);
					}
					else {
						holder.controller.setNeutral();
						holder.controller.setBackgroundDrawable(drawable);
					}
				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder);
	}

	private void viewLocation(AbstractMessageModel messageModel, final View v) {
		if (!ConfigUtils.hasNoMapboxSupport()) {
			if (messageModel != null) {
				LocationDataModel locationData = messageModel.getLocationData();
				if (locationData != null) {
					Intent intent = new Intent(getContext(), MapActivity.class);
					IntentDataUtil.append(new LatLng(messageModel.getLocationData().getLatitude(),
							messageModel.getLocationData().getLongitude()),
						getContext().getString(R.string.app_name),
						messageModel.getLocationData().getPoi(),
						messageModel.getLocationData().getAddress(),
						intent);
					getContext().startActivity(intent);
				}
			}
		} else {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getContext(), "Feature not available due to firmware error", Toast.LENGTH_LONG).show();
				}
			});
		}
	}
}
