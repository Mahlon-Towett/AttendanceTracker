package org.smart.attendance_beta.utils;

/**
 * Office location data class for multi-office support
 */
public class OfficeLocation {
    public String id;
    public String name;
    public double latitude;
    public double longitude;
    public int radius;

    public OfficeLocation() {
        // Default constructor
    }

    public OfficeLocation(String id, String name, double latitude, double longitude, int radius) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    @Override
    public String toString() {
        return name + " (" + latitude + ", " + longitude + ", " + radius + "m)";
    }
}

/**
 * Office detection result class
 */
class OfficeDetectionResult {
    public boolean isAtOffice;
    public OfficeLocation currentOffice;  // Office user is currently at
    public double currentDistance;        // Distance to current office
    public OfficeLocation closestOffice;  // Closest office if not at any
    public double closestDistance;        // Distance to closest office

    public OfficeDetectionResult() {
        this.isAtOffice = false;
        this.currentDistance = 0.0;
        this.closestDistance = Double.MAX_VALUE;
    }

    @Override
    public String toString() {
        if (isAtOffice && currentOffice != null) {
            return "At " + currentOffice.name + " (" + String.format("%.0f", currentDistance) + "m)";
        } else if (closestOffice != null) {
            return "Not at office. Closest: " + closestOffice.name + " (" + String.format("%.0f", closestDistance) + "m)";
        } else {
            return "No office locations available";
        }
    }
}