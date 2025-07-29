package org.smart.attendance_beta.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Locale;

public class LocationManagerUtils {

    private static final String TAG = "LocationManager";
    private static final long MIN_TIME_BETWEEN_UPDATES = 5000; // 5 seconds
    private static final float MIN_DISTANCE_CHANGE = 10; // 10 meters

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Context context;
    private LocationUpdateCallback callback;
    private boolean isLocationUpdatesActive = false;

    // Callback interface for location updates
    public interface LocationUpdateCallback {
        void onLocationUpdated(Location location, boolean isWithinWorkArea);
        void onLocationError(String error);
        void onPermissionRequired();
    }

    // Work location configuration (you can make this configurable)
    private static final double WORK_LATITUDE = -1.2921; // Nairobi example
    private static final double WORK_LONGITUDE = 36.8219;
    private static final float WORK_RADIUS_METERS = 200; // 200 meter radius

    public LocationManagerUtils(Context context, LocationUpdateCallback callback) {
        this.context = context;
        this.callback = callback;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        setupLocationListener();
    }

    private void setupLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d(TAG, "Location updated: " + location.getLatitude() + ", " + location.getLongitude());

                // Check if location is within work area
                boolean isWithinWorkArea = isLocationWithinWorkArea(location);

                // Notify callback
                if (callback != null) {
                    callback.onLocationUpdated(location, isWithinWorkArea);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "Location provider status changed: " + provider + " status: " + status);
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d(TAG, "Location provider enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.w(TAG, "Location provider disabled: " + provider);
                if (callback != null) {
                    callback.onLocationError("GPS is disabled. Please enable location services.");
                }
            }
        };
    }

    /**
     * Start automatic location updates
     * Call this in onResume() of your activity
     */
    public void startLocationUpdates() {
        if (!hasLocationPermission()) {
            if (callback != null) {
                callback.onPermissionRequired();
            }
            return;
        }

        if (locationManager == null) {
            if (callback != null) {
                callback.onLocationError("Location service not available");
            }
            return;
        }

        try {
            // Try GPS first for better accuracy
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE,
                        locationListener
                );
                Log.d(TAG, "GPS location updates started");
            }

            // Also use network provider as backup
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE,
                        locationListener
                );
                Log.d(TAG, "Network location updates started");
            }

            isLocationUpdatesActive = true;

            // Get last known location immediately
            getLastKnownLocation();

        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
            if (callback != null) {
                callback.onLocationError("Location permission denied");
            }
        }
    }

    /**
     * Stop location updates
     * Call this in onPause() of your activity
     */
    public void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
                isLocationUpdatesActive = false;
                Log.d(TAG, "Location updates stopped");
            } catch (SecurityException e) {
                Log.e(TAG, "Error stopping location updates", e);
            }
        }
    }

    /**
     * Get last known location immediately
     */
    public void getLastKnownLocation() {
        if (!hasLocationPermission()) {
            if (callback != null) {
                callback.onPermissionRequired();
            }
            return;
        }

        Location lastLocation = null;

        try {
            // Try GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLocation != null) {
                    lastLocation = gpsLocation;
                }
            }

            // Try network if GPS not available
            if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null) {
                    lastLocation = networkLocation;
                }
            }

            if (lastLocation != null) {
                Log.d(TAG, "Last known location: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                boolean isWithinWorkArea = isLocationWithinWorkArea(lastLocation);
                if (callback != null) {
                    callback.onLocationUpdated(lastLocation, isWithinWorkArea);
                }
            } else {
                Log.d(TAG, "No last known location available");
                if (callback != null) {
                    callback.onLocationError("No location data available. Please wait for GPS fix.");
                }
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for last known location", e);
            if (callback != null) {
                callback.onLocationError("Location permission denied");
            }
        }
    }

    /**
     * Check if current location is within work area
     */
    private boolean isLocationWithinWorkArea(Location location) {
        if (location == null) return false;

        // Create work location
        Location workLocation = new Location("work");
        workLocation.setLatitude(WORK_LATITUDE);
        workLocation.setLongitude(WORK_LONGITUDE);

        // Calculate distance
        float distance = location.distanceTo(workLocation);

        Log.d(TAG, "Distance to work: " + distance + " meters (allowed: " + WORK_RADIUS_METERS + ")");

        return distance <= WORK_RADIUS_METERS;
    }

    /**
     * Check if app has location permissions
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Update work location coordinates
     */
    public static void updateWorkLocation(double latitude, double longitude, float radiusMeters) {
        // You can implement this to make work location configurable
        // For now, you'll need to update the constants above
        Log.d(TAG, "Work location updated to: " + latitude + ", " + longitude + " (radius: " + radiusMeters + "m)");
    }

    /**
     * Get current location status
     */
    public boolean isLocationUpdatesActive() {
        return isLocationUpdatesActive;
    }

    /**
     * Format location for display
     */
    public static String formatLocation(Location location) {
        if (location == null) return "Location not available";

        return String.format(Locale.getDefault(),
                "%.6f, %.6f (±%.0fm)",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy());
    }

    /**
     * Get location accuracy description
     */
    public static String getAccuracyDescription(Location location) {
        if (location == null) return "Unknown";

        float accuracy = location.getAccuracy();
        if (accuracy < 5) {
            return "Excellent (±" + (int)accuracy + "m)";
        } else if (accuracy < 15) {
            return "Good (±" + (int)accuracy + "m)";
        } else if (accuracy < 50) {
            return "Fair (±" + (int)accuracy + "m)";
        } else {
            return "Poor (±" + (int)accuracy + "m)";
        }
    }
}