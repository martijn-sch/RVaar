package hu.rijkswaterstaat.rvaar;


import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import hu.rijkswaterstaat.rvaar.dao.MarkerDAOimpl;


public class OverviewMap extends ActionBarActivity implements
        ConnectionCallbacks, OnConnectionFailedListener, LocationListener {
    public static final int DRAW_DISTANCE_MARKERS = 20000;
    public static final int NEAREST_MARKER_METER = 10000;
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    protected static final String TAG = "location-updates-sample";
    public GoogleMap mMap;
    public boolean AnimatedCameraOnce = true;
    public MarkerOptions nearestMarkerLoc;
    // Keys for storing activity state in the Bundle.
    public ArrayList<MarkerOptions> markers;
    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;
    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    protected LocationRequest mLocationRequest;
    /**
     * Represents a geographical location.
     */
    protected Location mCurrentLocation;
    /**
     * Time when the location was updated represented as a String.
     */
    protected String mLastUpdateTime;
    ProgressDialog dialog;

    // UI Widgets.
    /**
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    private ArrayList<LatLng> points;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview_map);
        markers = new ArrayList<>();
        points = new ArrayList<>();
        mLastUpdateTime = "";
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "GPS is enabled... launching rVaart", Toast.LENGTH_SHORT).show();
        } else {
            showGPSDisabledAlertToUser();
        }
        // Update values using data stored in the Bundle.


        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();
        setUpMapIfNeeded();
        dialog = ProgressDialog.show(this, "",
                "Getting your location", true, true);


    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }


    private void setUpMapIfNeeded() {
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            mMap.setMyLocationEnabled(true);
            addMarkersToMap();


        }
        if (mMap != null) {
            setUpMap();
        }


    }

    private void setUpMap() {

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                MarkerDAOimpl positionDao = new MarkerDAOimpl();
                markers = positionDao.getMarkers();
            }
        });
        t1.start();
        try {
            t1.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }


    protected void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        Log.d("startLocup", "startLocup");
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }


    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null) {

            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());


        }


        startLocationUpdates();

    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        Log.d("startLocch", "startLocch");

        mMap.clear();
        addMarkersToMap();
        MarkerOptions k = new MarkerOptions();
        k.position(loc);
        mMap.addMarker(k.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_markericon)));
        findNearestMarker();

        if (AnimatedCameraOnce) { // tijdelijk
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(loc)      // Sets the center of the map to Mountain View
                    .zoom(17)                   // Sets the zoom
                    .bearing(90)                // Sets the orientation of the camera to east
                    .tilt(30)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            AnimatedCameraOnce = false;
        }
        dialog.dismiss();
        drawPolyLineOnLocation(location);

        Log.d("Latitude", "Current Latitude " + location.getLatitude());
        Log.d("Longitude", "Current Longitude " + location.getLongitude());
        Toast.makeText(this, getResources().getString(R.string.location_updated_message),
                Toast.LENGTH_SHORT).show();
    }

    private void drawPolyLineOnLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LatLng latLngP = new LatLng(latitude, longitude);

        PolylineOptions polyLineOpt = new PolylineOptions()
                .width(5)
                .color(Color.RED)
                .geodesic(true);

        points.add(latLngP);

        for (int z = 0; z < points.size(); z++) {
            LatLng point = points.get(z);
            polyLineOpt.add(point);
        }

        Polyline line = mMap.addPolyline(polyLineOpt);

        points.add(latLngP);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    /**
     * Stores activity data in the Bundle.
     */


    public void findNearestMarker() {
        double minDist = 1E38;
        int minIndex = -1;
        for (int i = 0; i < markers.size(); i++) {
            Location currentIndexMarkerLoc = new Location("Marker");
            currentIndexMarkerLoc.setLatitude(markers.get(i).getPosition().latitude);
            currentIndexMarkerLoc.setLongitude(markers.get(i).getPosition().longitude);
            currentIndexMarkerLoc.setTime(new Date().getTime());
            float test = mCurrentLocation.distanceTo(currentIndexMarkerLoc);
            if (test < minDist) {
                minDist = test;
                minIndex = i;
            }
        }
        if (minIndex >= 0) {
            nearestMarkerLoc = markers.get(minIndex);
            nearestMarkerLoc.icon(null); // testen
            nearestMarkerLoc.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
            // mMap.addMarker(nearestMarkerLoc);
            Log.d("nearestLocation name", "nearestLocation name" + nearestMarkerLoc.getTitle());
            notifyUser(nearestMarkerLoc);
        } else {
            nearestMarkerLoc.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        }
    }

    public void notifyUser(MarkerOptions marker) {
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Location notifcationLoc = new Location("Marker");
        notifcationLoc.setLatitude(marker.getPosition().latitude);
        notifcationLoc.setLongitude(marker.getPosition().longitude);
        float distanceInMeters = mCurrentLocation.distanceTo(notifcationLoc);
        int notifyID = 1;
        if (distanceInMeters < NEAREST_MARKER_METER) { // in seconden te doen, in alle gevallen is het dan gelijk
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(this)
                            .setContentTitle("rVaar")
                            .setContentText("Over " + distanceInMeters + " nadert u de kruispunt " + marker.getTitle())
                            .setSmallIcon(R.drawable.ic_rvaar)
                            .setSound(sound);

            Intent resultIntent = new Intent(this, OverviewMap.class);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(OverviewMap.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notifyID, mBuilder.build()); // test on screen update/

            mBuilder.setDefaults(-1); // http://developer.android.com/reference/android/app/Notification.html#DEFAULT_ALL


        }


    }

    private void showGPSDisabledAlertToUser() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("GPS is disabled to use rVaart please enable GPS")
                .setCancelable(false)
                .setPositiveButton("Settings",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
        alertDialogBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }


    public void addMarkersToMap() {
        for (MarkerOptions m : markers) {
            Location loc = new Location("");
            loc.setLongitude(m.getPosition().longitude);
            loc.setLatitude(m.getPosition().latitude);
            if (mCurrentLocation.distanceTo(loc) < DRAW_DISTANCE_MARKERS) {
                if (m == nearestMarkerLoc) {
                    mMap.addMarker(m);
                } else {
                    m.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_iconkruispunt));
                    mMap.addMarker(m);
                }

            }

        }
    }
}