/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import android.location.Location;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.LocationDataModel;

import static android.view.View.GONE;

public class LocationChatAdapterDecorator extends ChatAdapterDecorator {

    public LocationChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
        super(context, messageModel, helper);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void configureChatMessage(@NonNull final ComposeMessageHolder holder, final int position) {
        final LocationDataModel locationDataModel = this.getMessageModel().getLocationData();

        TextView addressLine = holder.bodyTextView;

        this.setOnClickListener(v -> {
            if (
                getMessageModel().getState() != MessageState.FS_KEY_MISMATCH &&
                    getMessageModel().getState() != MessageState.SENDFAILED
            ) {
                if (!isInChoiceMode()) {
                    viewLocation(getMessageModel());
                }
            }
        }, holder.messageBlockView);

        //clear texts
        if (holder.bodyTextView != null) {
            holder.bodyTextView.setText("");
        }

        if (holder.secondaryTextView != null) {
            holder.secondaryTextView.setText("");
            holder.secondaryTextView.setVisibility(GONE);
        }

        if (locationDataModel.poiNameOrNull != null) {
            if (holder.bodyTextView != null) {
                holder.bodyTextView.setText(highlightMatches(locationDataModel.poiNameOrNull, filterString));
                holder.bodyTextView.setWidth(this.getThumbnailWidth());
                holder.bodyTextView.setVisibility(View.VISIBLE);
            }
            addressLine = holder.secondaryTextView;
        }

        if (addressLine != null) {
            final @Nullable String poiAddress = locationDataModel.poiAddressOrNull;
            if (poiAddress != null) {
                addressLine.setText(highlightMatches(poiAddress, filterString));
                addressLine.setWidth(this.getThumbnailWidth());
                addressLine.setVisibility(View.VISIBLE);
            } else {
                GeoLocationUtil geoLocation = new GeoLocationUtil(addressLine);
                Location location = new Location("X");
                location.setLatitude(locationDataModel.latitude);
                location.setLongitude(locationDataModel.longitude);
                location.setAccuracy((long) locationDataModel.accuracyOrFallback);
                geoLocation.updateAddressAndModel(getContext(), location);
            }
        }

        if (position == holder.position) {
            holder.controller.setBackgroundImage(null);
            holder.controller.setIconResource(R.drawable.ic_map_marker_outline);
        }

        RuntimeUtil.runOnUiThread(() -> setupResendStatus(holder));
    }

    private void viewLocation(AbstractMessageModel messageModel) {
        if (messageModel == null) {
            return;
        }

        if (!GeoLocationUtil.viewLocation(getContext(), messageModel.getLocationData())) {
            RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), "Feature not available due to firmware error", Toast.LENGTH_LONG).show());
        }
    }
}
