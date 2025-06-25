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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.maplibre.android.geometry.LatLng;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ThreemaEditText;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NetworkUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.locationpicker.PoiRepository.QUERY_MIN_LENGTH;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LAT;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LNG;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class LocationAutocompleteActivity extends ThreemaActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("LocationAutocompleteActivity");

    private static final String DIALOG_TAG_NO_CONNECTION = "no_connection";

    private static final long QUERY_TIMEOUT = 1000; // ms

    private LocationAutocompleteAdapter autocompleteAdapter;
    private EmptyRecyclerView recyclerView;

    private String queryText;
    private LatLng currentLocation = new LatLng();
    private LocationAutocompleteViewModel viewModel;
    private List<Poi> places = new ArrayList<>();
    private LinearProgressIndicator progressBar;
    private EmptyView emptyView;

    private Handler queryHandler = new Handler();
    private Runnable queryTask = new Runnable() {
        @Override
        public void run() {
            viewModel.search(new PoiQuery(queryText, currentLocation));
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        setContentView(R.layout.activity_location_autocomplete);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(null);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            finish();
            return;
        }
        actionBar.setTitle(null);
        actionBar.setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        currentLocation.setLatitude(intent.getDoubleExtra(INTENT_DATA_LOCATION_LAT, 0));
        currentLocation.setLongitude(intent.getDoubleExtra(INTENT_DATA_LOCATION_LNG, 0));

        ThreemaEditText searchView = findViewById(R.id.search_view);
        searchView.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    queryHandler.removeCallbacksAndMessages(null);
                    queryText = s.toString();
                    queryHandler.postDelayed(queryTask, QUERY_TIMEOUT);
                }
            }
        });

        progressBar = this.findViewById(R.id.progress);

        recyclerView = this.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        emptyView = new EmptyView(this, ConfigUtils.getActionBarSize(this));
        emptyView.setup(R.string.lp_search_place_min_chars);
        ((ViewGroup) recyclerView.getParent()).addView(emptyView);
        recyclerView.setEmptyView(emptyView);

        handleDeviceInsets(emptyView);

        // Get the ViewModel.
        viewModel = new ViewModelProvider(this).get(LocationAutocompleteViewModel.class);

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                progressBar.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.GONE);
            }
        });

        // Create the observer which updates the UI.
        viewModel.getPlaces().observe(this, newplaces -> {
            // Update the UI
            places = newplaces;
            refreshAdapter(places);

            if (!NetworkUtil.isOnline()) {
                SimpleStringAlertDialog.newInstance(R.string.send_location, R.string.internet_connection_required).show(getSupportFragmentManager(), DIALOG_TAG_NO_CONNECTION);
            } else if (places.isEmpty() && (queryText != null && queryText.length() >= QUERY_MIN_LENGTH)) {
                emptyView.setup(R.string.lp_search_place_no_matches);
            } else {
                emptyView.setup(R.string.lp_search_place_min_chars);
            }
        });

        setResult(RESULT_CANCELED);
    }

    private void handleDeviceInsets(@NonNull EmptyView emptyView) {
        ViewExtensionsKt.applyDeviceInsetsAsPadding(findViewById(R.id.appbar), InsetSides.ltr());
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.recycler),
            InsetSides.lbr(),
            SpacingValues.horizontal(R.dimen.tablet_additional_padding_horizontal)
        );
        ViewExtensionsKt.applyDeviceInsetsAsPadding(emptyView, InsetSides.horizontal(), SpacingValues.all(R.dimen.grid_unit_x2));
    }

    private void refreshAdapter(List<Poi> places) {
        if (autocompleteAdapter == null) {
            autocompleteAdapter = new LocationAutocompleteAdapter(places);
            autocompleteAdapter.setOnItemClickListener((poi, position) -> returnResult(poi));
            recyclerView.setAdapter(autocompleteAdapter);
        } else {
            recyclerView.getRecycledViewPool().clear();
            autocompleteAdapter.notifyDataSetChanged();
        }
    }

    private void returnResult(Poi place) {
        Intent data = new Intent();

        if (place != null) {
            IntentDataUtil.append(place.getLatLng(), getString(R.string.app_name), place.getName(), null, data);
            setResult(RESULT_OK, data);
        } else {
            setResult(RESULT_CANCELED);
        }
        this.finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        // Intercepting back navigation is needed as this activity overrides the finish() method
        this.finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left_short, R.anim.slide_out_right_short);
    }
}
