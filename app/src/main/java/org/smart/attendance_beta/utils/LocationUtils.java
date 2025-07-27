package org.smart.attendance_beta.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;

public class LocationUtils {

    /**
     * Calculate distance between two points in meters
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        Location location1 = new Location("");
        location1.setLatitude(lat1);
        location1.setLongitude(lon1);

        Location location2 = new Location("");
        location2.setLatitude(lat2);
        location2.setLongitude(lon2);

        return location1.distanceTo(location2);
    }

    /**
     * Check if employee is within company geofence
     */
    public static boolean isWithinGeofence(double employeeLat, double employeeLon,
                                           double companyLat, double companyLon, int radiusMeters) {
        double distance = calculateDistance(employeeLat, employeeLon, companyLat, companyLon);
        return distance <= radiusMeters;
    }

    /**
     * Format distance for display
     */
    public static String formatDistance(double distanceMeters) {
        if (distanceMeters < 1000) {
            return String.format("%.0f m", distanceMeters);
        } else {
            return String.format("%.1f km", distanceMeters / 1000);
        }
    }

    /**
     * Get location status message
     */
    public static String getLocationStatus(double distance, int radiusMeters) {
        if (distance <= radiusMeters) {
            return "You're at the office ✅";
        } else if (distance <= radiusMeters + 50) {
            return "Very close to office 🟡";
        } else if (distance <= radiusMeters + 200) {
            return "Near the office 🟠";
        } else {
            return "Too far from office ❌";
        }
    }

    /**
     * Check if location permissions are granted
     */
    public static boolean hasLocationPermissions(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if GPS is enabled
     */
    public static boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}