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

package ch.threema.app.locationpicker;

import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LAT;
import static ch.threema.app.utils.IntentDataUtil.INTENT_DATA_LOCATION_LNG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.mapbox.android.gestures.MoveGestureDetector;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LocationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;

public class LocationPickerActivity extends ThreemaActivity implements
	LocationPickerAdapter.OnClickItemListener {

	private static final Logger logger = LoggingUtil.getThreemaLogger("LocationPickerActivity");

	private static final String DIALOG_TAG_ENABLE_LOCATION_SERVICES = "lss";
	private static final String DIALOG_TAG_CONFIRM_PLACE = "conf";

	private boolean firstLocationZoom = true;

	private static final int REQUEST_CODE_PLACES = 22228;

	private static final int APPBAR_HEIGHT_PERCENT = 68;

	// URLs for Threema Map server
	public static final String MAP_STYLE_URL = "https://map.threema.ch/styles/streets/style.json";

	public static final int POI_RADIUS = 750; // meters
	private static final int MAX_POI_COUNT = 30;

	// Threema services
	private PreferenceService preferenceService;

	private MapView mapView;
	private MapboxMap mapboxMap;

	private LocationManager locationManager;
	private LocationComponent locationComponent;

	private View root;
	private List<Poi> places;
	private EmptyRecyclerView recyclerView;
	private TextView poilistDescription;
	MaterialCardView searchView;
	AppBarLayout appBarLayout;
	CollapsingToolbarLayout collapsingToolbarBarLayout;
	private LatLng lastPosition = new LatLng(0,0);

	private LocationPickerAdapter locationPickerAdapter;
	private CircularProgressIndicator loadingProgressBar;

	/**
	 * Launcher to request location permissions. When the location permission is given, it zooms to the current position (or asks to enable location services).
	 */
	private final ActivityResultLauncher<String[]> locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
		if (result.get(Manifest.permission.ACCESS_FINE_LOCATION) || result.get(Manifest.permission.ACCESS_COARSE_LOCATION)) {
			zoomToCurrentLocationWithPermission();
		} else {
			if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
				ConfigUtils.showPermissionRationale(this, root, R.string.permission_location_required, new BaseTransientBottomBar.BaseCallback<>() {});
			}
		}
		firstLocationZoom = false;
	});

	/**
	 * Launcher to request location services. When the location services are enabled, it zooms to the current position.
	 */
	private final ActivityResultLauncher<Intent> locationEnableLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
		if (isLocationEnabled()) {
			zoomToCurrentLocationWithLocationEnabled();
		}
		firstLocationZoom = false;
	});

	@SuppressLint("StaticFieldLeak")
	private class NearbyPOITask extends AsyncTask<LatLng, Void, List<Poi>> {
		@Override
		protected void onPreExecute() {
			loadingProgressBar.setVisibility(View.VISIBLE);
		}

		@Override
		protected List<Poi> doInBackground(LatLng... latLngs) {
			LatLng latLng = latLngs[0];

			logger.debug("NearbyPoiTask: get POIs for {}", latLng);

			List<Poi> pois = new ArrayList<>();
			NearbyPoiUtil.getPOIs(latLng, pois, MAX_POI_COUNT, preferenceService);
			return pois;
		}

		@Override
		protected void onPostExecute(List<Poi> pois) {
			loadingProgressBar.setVisibility(View.GONE);

			if (pois != null) {
				// update markers and list
				bindPlaces(pois);
				if (pois.size() > 0) {
					poilistDescription.setVisibility(View.VISIBLE);
					places = pois;
					return;
				}
			}

			places = null;
			if (locationPickerAdapter != null) {
				locationPickerAdapter.setPois(new ArrayList<>());
			}

			poilistDescription.setVisibility(View.INVISIBLE);
		}
	}
	private NearbyPOITask nearbyPOITask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ConfigUtils.configureSystemBars(this);

		ConfigUtils.getMapLibreInstance();

		setContentView(R.layout.activity_location_picker);

		ConfigUtils.configureTransparentStatusBar(this);

		root = findViewById(R.id.coordinator);
		appBarLayout = findViewById(R.id.appbar);
		collapsingToolbarBarLayout = findViewById(R.id.collapsingToolbarLayout);
		mapView = findViewById(R.id.map);

		MaterialToolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			finish();
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		// Get Threema services
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Could not obtain service manager");
			finish();
			return;
		}
		this.preferenceService = serviceManager.getPreferenceService();
		if (this.preferenceService == null) {
			logger.error("Could not obtain preference service");
			finish();
			return;
		}

		if (BuildConfig.DEBUG && this.preferenceService.getPoiServerHostOverride() != null) {
			Toast.makeText(this, "Using POI host override", Toast.LENGTH_SHORT).show();
		}

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) {
			finish();
			return;
		}

		mapView.onCreate(savedInstanceState);

		initUi();
		initMap();
	}

	private void initUi() {
		recyclerView = findViewById(R.id.poi_list);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));

		findViewById(R.id.center_map).setOnClickListener(new DebouncedOnClickListener(1000) {
			@Override
			public void onDebouncedClick(View v) {
				zoomToCurrentLocation();
			}
		});
		findViewById(R.id.search_container).setOnClickListener(new DebouncedOnClickListener(1000) {
			@Override
			public void onDebouncedClick(View v) {
				requestPlacesSearch();
			}
		});
		findViewById(R.id.send_location_container).setOnClickListener(new DebouncedOnClickListener(1000) {
			@Override
			public void onDebouncedClick(View v) {
				returnData(null);
			}
		});

		loadingProgressBar = findViewById(R.id.loading_progressbar);
		poilistDescription = findViewById(R.id.poi_list_description);

		searchView = findViewById(R.id.search_container);
		searchView.setVisibility(View.VISIBLE);
		((AppBarLayout)findViewById(R.id.appbar)).addOnOffsetChangedListener(((appBarLayout, verticalOffset) -> {
			MaterialToolbar toolbar = findViewById(R.id.toolbar);
			float verticalOffset1 = (float)verticalOffset;
			toolbar.setAlpha(Math.abs(verticalOffset1 / (float)appBarLayout.getTotalScrollRange()));
		}));

		adjustAppBarHeight();
	}

	private void adjustAppBarHeight() {
		ViewGroup.LayoutParams layoutParams = appBarLayout.getLayoutParams();

		if (layoutParams != null) {
			CoordinatorLayout.LayoutParams appBarLayoutParams = (CoordinatorLayout.LayoutParams)layoutParams;
			AppBarLayout.LayoutParams collapsingToolBarLayoutParams = (AppBarLayout.LayoutParams)collapsingToolbarBarLayout.getLayoutParams();
			appBarLayoutParams.setBehavior((new AppBarLayout.Behavior()));
			CoordinatorLayout.Behavior appBarLayoutParamsBehavior = appBarLayoutParams.getBehavior();

			if (appBarLayoutParamsBehavior != null) {
				AppBarLayout.Behavior behavior = (AppBarLayout.Behavior)appBarLayoutParamsBehavior;
				behavior.setDragCallback((new AppBarLayout.Behavior.DragCallback() {
					@Override
					public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
						return false;
					}
				}));

				// Set the size of AppBarLayout to 68% of the total height
				CoordinatorLayout coordinatorLayout = findViewById(R.id.coordinator);
				if (ViewCompat.isLaidOut(coordinatorLayout) && !coordinatorLayout.isLayoutRequested()) {
					collapsingToolBarLayoutParams.height = coordinatorLayout.getHeight() * APPBAR_HEIGHT_PERCENT / 100 - getResources().getDimensionPixelSize(R.dimen.send_location_container_height);
					collapsingToolbarBarLayout.setLayoutParams(collapsingToolBarLayoutParams);
				} else {
					coordinatorLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
						@Override
						public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
							coordinatorLayout.removeOnLayoutChangeListener(this);
							collapsingToolBarLayoutParams.height = coordinatorLayout.getHeight() * APPBAR_HEIGHT_PERCENT / 100 - getResources().getDimensionPixelSize(R.dimen.send_location_container_height);
							collapsingToolbarBarLayout.setLayoutParams(collapsingToolBarLayoutParams);
						}
					});
				}
			}
		}

		searchView.setVisibility(ConfigUtils.isLandscape(this) ? View.GONE: View.VISIBLE);
	}

	private void requestPlacesSearch() {
		Intent intent = new Intent(this, LocationAutocompleteActivity.class);
		intent.putExtra(INTENT_DATA_LOCATION_LAT, getMapCenterPosition().getLatitude());
		intent.putExtra(INTENT_DATA_LOCATION_LNG, getMapCenterPosition().getLongitude());
		startActivityForResult(intent, REQUEST_CODE_PLACES);
		overridePendingTransition(R.anim.slide_in_right_short, R.anim.slide_out_left_short);
	}

	private void initMap() {
		mapView.getMapAsync(mapboxMap1 -> {
			mapboxMap = mapboxMap1;
			mapboxMap.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL), style -> {
				// Map is set up and the style has loaded. Now you can add data or make other mapView adjustments
				setupLocationComponent(style);
				// Initialize map to world view (gets changed as soon as current location is available)
				setMapWithLocationFallback();
				// hack: delay location query
				mapView.postDelayed(this::zoomToCurrentLocation, 500);
			});
			mapboxMap.getUiSettings().setAttributionEnabled(false);
			mapboxMap.getUiSettings().setLogoEnabled(false);
			mapboxMap.setOnMarkerClickListener(marker -> {
				for (Poi poi : places) {
					if (poi.getId() == Long.parseLong(marker.getSnippet())) {
						returnData(poi);
						return true;
					}
				}
				return false;
			});
			mapboxMap.addOnMoveListener(new MapboxMap.OnMoveListener() {
				@Override
				public void onMoveBegin(@NonNull MoveGestureDetector detector) {}

				@Override
				public void onMove(@NonNull MoveGestureDetector detector) {}

				@Override
				public void onMoveEnd(@NonNull MoveGestureDetector detector) {
					updatePois();
				}
			});
			mapboxMap.addOnCameraIdleListener(this::updatePois);
		});
	}

	@SuppressLint("MissingPermission")
	private void setupLocationComponent(Style style) {
		logger.debug("setupLocationComponent");

		if (style == null) {
			return;
		}

		locationComponent = mapboxMap.getLocationComponent();
		locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(this, style).build());
		locationComponent.setCameraMode(CameraMode.TRACKING);
		locationComponent.setRenderMode(RenderMode.COMPASS);
		if (hasLocationPermission()) {
			locationComponent.setLocationComponentEnabled(true);
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void bindPlaces(List<Poi> newPois) {

		if (locationPickerAdapter == null) {
			locationPickerAdapter = new LocationPickerAdapter(this);
			recyclerView.setAdapter(locationPickerAdapter);
			locationPickerAdapter.setOnClickItemListener(this);
		}
		locationPickerAdapter.setPois(newPois);

		new AsyncTask<Void, Void, List<MarkerOptions>>() {
			HashMap<Long, Marker> poiMarkerMap = new HashMap<>();
			List<Marker> markerList;
			long startTime;

			@Override
			protected void onPreExecute() {
				startTime = System.currentTimeMillis();

				markerList = mapboxMap.getMarkers();
			}

			@Override
			protected List<MarkerOptions> doInBackground(Void...voids) {
				startTime = System.currentTimeMillis();
				for(Marker marker: markerList) {
					poiMarkerMap.put(Long.valueOf(marker.getSnippet()), marker);
				}
				List<MarkerOptions> markerOptions = new ArrayList<>();
				for (Poi poi: newPois) {
					if (!poiMarkerMap.containsKey(poi.getId())) {
						markerOptions.add(new MarkerOptions()
								.position(poi.getLatLng())
								.title(poi.getName())
								.setIcon(LocationUtil.getMarkerIcon(LocationPickerActivity.this, poi))
								.setSnippet(String.valueOf(poi.getId()))
						);
						logger.debug("Add marker {}", poi.getName());
					} else {
						logger.debug("Retain marker {}", poi.getName());
						poiMarkerMap.remove(poi.getId());
					}
				}
				startTime = System.currentTimeMillis();
				return markerOptions;
			}


			@Override
			protected void onPostExecute(List<MarkerOptions> markerOptionsList) {
				startTime = System.currentTimeMillis();
				for (Map.Entry<Long, Marker> marker : poiMarkerMap.entrySet()) {
					logger.debug("Remove marker {}", marker.getValue().getTitle());
					mapboxMap.removeMarker(marker.getValue());
				}
				startTime = System.currentTimeMillis();
				mapboxMap.addMarkers(markerOptionsList);
			}
		}.execute();
	}

	@Override
	protected void onStart() {
		logger.debug("onStart");
		super.onStart();
		if (mapView != null) {
			mapView.onStart();
		}
	}

	@Override
	public void onResume() {
		logger.debug("onResume");
		super.onResume();
		mapView.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mapView.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		mapView.onStop();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapView.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mapView.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(@NonNull Menu menu) {
		getMenuInflater().inflate(R.menu.activity_location_picker, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
		} else if (item.getItemId() == R.id.action_search) {
			requestPlacesSearch();
		}
		return true;
	}

	private void returnData(Poi poi) {
		String name = poi != null ? poi.getName() : null;
		LatLng latLng = poi != null ? poi.getLatLng() : getMapCenterPosition();

		LocationPickerConfirmDialog dialog = LocationPickerConfirmDialog.newInstance(
			getString(R.string.lp_use_this_location),
			name,
			latLng,
			mapboxMap.getProjection().getVisibleRegion().latLngBounds,
			(tag, object) -> reallyReturnData((Poi) object));

		dialog.setData(poi);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_CONFIRM_PLACE);
	}

	private void reallyReturnData(Poi poi) {
		Intent data = new Intent();

		if (poi != null) {
			IntentDataUtil.append(poi.getLatLng(), getString(R.string.app_name), poi.getName(), null, data);
		} else {
			IntentDataUtil.append(getMapCenterPosition(), getString(R.string.app_name), null, null, data);
		}
		setResult(RESULT_OK, data);

		finish();
	}

	private LatLng getMapCenterPosition() {
		CameraPosition cameraPosition = mapboxMap.getCameraPosition();
		if (cameraPosition != null && cameraPosition.target != null) {
			return new LatLng(cameraPosition.target.getLatitude(), cameraPosition.target.getLongitude());
		}
		return new LatLng(0,0);
	}

	@SuppressLint("StaticFieldLeak")
	private void updatePois() {
		logger.debug("updatePOIs");

		LatLng latLng = getMapCenterPosition();
		if (latLng.distanceTo(lastPosition) > 30) {
			logger.debug("...updating");
			lastPosition = latLng;

			if (nearbyPOITask != null) {
				nearbyPOITask.cancel(true);
			}
			nearbyPOITask = new NearbyPOITask();
			nearbyPOITask.execute(latLng);
		} else {
			logger.debug("...no update necessary");
		}
	}

	/**
	 * Zoom to current position. Asks for location permission if no location permissions are given.
	 */
	private void zoomToCurrentLocation() {
		if (hasLocationPermission()) {
			zoomToCurrentLocationWithPermission();
			firstLocationZoom = false;
		} else {
			try {
				locationPermissionRequest.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
			} catch (IllegalStateException e) {
				logger.debug("Unable to launch permission request", e);
			}
		}
	}

	/**
	 * Zoom to current position. Only call this if location permission is given! Asks for enabling
	 * location services if disabled.
	 */
	private void zoomToCurrentLocationWithPermission() {
		if (isLocationEnabled()) {
			zoomToCurrentLocationWithLocationEnabled();
			return;
		}
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.send_location, R.string.location_services_disabled, R.string.yes, R.string.no);
		dialog.setCallback(new GenericAlertDialog.DialogClickListener() {
			@Override
			public void onYes(String tag, Object data) {
				locationEnableLauncher.launch(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
			}

			@Override
			public void onNo(String tag, Object data) {
				// Don't zoom to location if user does not want to activate location services
			}
		});
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_ENABLE_LOCATION_SERVICES);
	}

	/**
	 * Zoom to current position. Only call this if location permissions are given and location services are enabled.
	 */
	@SuppressLint("MissingPermission")
	private void zoomToCurrentLocationWithLocationEnabled() {
		if (locationComponent != null) {
			if (!locationComponent.isLocationComponentEnabled()) {
				locationComponent.setLocationComponentEnabled(true);
				mapView.postDelayed(this::zoomToCurrentLocationWithLocationEnabled, 500);
				return;
			}
			// The first (automatic) zoom to the current location is zoomed in. The following zoom to location calls don't change the zoom level.
			int zoomLevel = firstLocationZoom ? 16 : -1;
			Location location = locationComponent.getLastKnownLocation();
			if (location != null) {
				moveCameraAndUpdatePOIs(new LatLng(location.getLatitude(), location.getLongitude()), true, zoomLevel);
			} else {
				showLocationNotAvailable();
			}
		}
	}

	private void showLocationNotAvailable() {
		RuntimeUtil.runOnUiThread(() -> Toast.makeText(LocationPickerActivity.this, R.string.unable_to_get_current_location, Toast.LENGTH_LONG).show());
	}

	@Override
	public void onClick(Poi poi, View view) {
		returnData(poi);
	}

	@SuppressLint("MissingPermission")
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_PLACES) {
			if (resultCode == RESULT_OK) {
				logger.debug("onActivityResult");

				if (locationComponent != null) {
					locationComponent.setLocationComponentEnabled(false);
				}
				Location newLocation = IntentDataUtil.getLocation(data);

				if (mapboxMap != null) {
					int zoom = (int) (mapboxMap.getCameraPosition().zoom < 12 ? 12 : mapboxMap.getCameraPosition().zoom);
					moveCameraAndUpdatePOIs(new LatLng(newLocation.getLatitude(), newLocation.getLongitude()), false, zoom);
				}
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void moveCameraAndUpdatePOIs(LatLng latLng, boolean animate, int zoomLevel) {
		long time = System.currentTimeMillis();
		logger.debug("moveCamera to {}", latLng);

		mapboxMap.cancelTransitions();
		mapboxMap.addOnCameraIdleListener(new MapboxMap.OnCameraIdleListener() {
			@Override
			public void onCameraIdle() {
				mapboxMap.removeOnCameraIdleListener(this);
				RuntimeUtil.runOnUiThread(() -> {
					logger.debug("camera has been moved. Time in ms = {}", (System.currentTimeMillis() - time));
					updatePois();
				});
			}
		});

		moveCamera(latLng, animate, zoomLevel);
	}

	private void moveCamera(LatLng latLng, boolean animate, int zoomLevel) {
		CameraUpdate cameraUpdate = zoomLevel != -1 ?
			CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel) :
			CameraUpdateFactory.newLatLng(latLng);

		if (animate) {
			mapboxMap.animateCamera(cameraUpdate);
		} else {
			mapboxMap.moveCamera(cameraUpdate);
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (appBarLayout != null) {
			appBarLayout.post(this::adjustAppBarHeight);
		}
	}

	private void setMapWithLocationFallback() {
		mapView.post(() -> {
			LatLng lastLocation = getLastKnownPosition(locationManager);
			if (lastLocation == null) {
				lastPosition = new LatLng(0, 0);
				moveCamera(lastPosition, true, 0);
			} else {
				lastPosition = lastLocation;
				moveCamera(lastPosition, true, 9);
				updatePois();
			}
		});
	}

	@SuppressLint("MissingPermission")
	@Nullable
	private LatLng getLastKnownPosition(@NonNull LocationManager locationManager) {
		// try to get a last location from gps and network provider
		Location location = null;
		if (hasLocationPermission()) {
			logger.debug("getting last known position");
			location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location == null) {
				logger.debug("couldn't get last known position from gps; trying network");
				location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			}
		}
		if (location != null) {
			return new LatLng(location.getLatitude(), location.getLongitude());
		} else {
			return null;
		}
	}

	private boolean hasLocationPermission() {
		return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
			ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private boolean isLocationEnabled() {
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
	}
}
