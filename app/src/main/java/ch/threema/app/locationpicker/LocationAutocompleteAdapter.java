/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.locationpicker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.utils.TestUtil;

public class LocationAutocompleteAdapter extends EmptyRecyclerView.Adapter<EmptyRecyclerView.ViewHolder> {
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private List<Poi> places;
    private OnItemClickListener onItemClickListener;

    LocationAutocompleteAdapter(List<Poi> places) {
        this.places = places;
    }

    @Override
    public void onBindViewHolder(@NonNull EmptyRecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PlacesViewHolder) {
            ((PlacesViewHolder) holder).onBind(position);
            if (this.onItemClickListener != null) {
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (places != null && position < places.size()) {
                            onItemClickListener.onClick(places.get(position), position);
                        }
                    }
                });
            }
        }
    }

    @NonNull
    @Override
    public EmptyRecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            return new PlacesViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location_picker_place, parent, false));
        } else {
            return new FooterViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location_picker_copyright, parent, false));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position >= places.size() ? TYPE_FOOTER : TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        if (places != null && !places.isEmpty()) {
            return places.size() + 1;
        } else {
            return 0;
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public class PlacesViewHolder extends EmptyRecyclerView.ViewHolder {
        private int currentPosition;
        TextView name, description, distance;

        PlacesViewHolder(View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.name);
            description = itemView.findViewById(R.id.address);
            distance = itemView.findViewById(R.id.distance);
        }

        protected void clear() {
            name.setText("");
            description.setText("");
            distance.setText("");
        }

        void onBind(int position) {
            currentPosition = position;
            clear();

            final Poi place = places.get(position);

            if (place.getName() != null) {
                name.setText(place.getName());
            }

            description.setText(getLocalizedDescription(ThreemaApplication.getAppContext(), place.getDescription()));

            if (place.getDistance() != -1) {
                String pattern = "#.#";
                if (place.getDistance() > 10000) {
                    pattern = "#,###";
                }
                String distanceS = new DecimalFormat(pattern + " km", DecimalFormatSymbols.getInstance(Locale.getDefault())).format((float) place.getDistance() / 1000);
                distance.setText(distanceS);
            }
        }

        public int getCurrentPosition() {
            return currentPosition;
        }
    }

    private @NonNull String getLocalizedDescription(Context context, String id) {
        if (!TestUtil.isEmptyOrNull(id)) {
            @StringRes int resId = context.getResources().getIdentifier(id, "string", context.getPackageName());

            if (resId != 0) {
                String value = context.getString(resId);
                if (!TestUtil.isEmptyOrNull(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    public class FooterViewHolder extends EmptyRecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }

    public interface OnItemClickListener {
        void onClick(Poi poi, int position);
    }
}
