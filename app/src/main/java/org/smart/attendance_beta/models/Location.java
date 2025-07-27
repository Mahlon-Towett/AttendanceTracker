package org.smart.attendance_beta.models;

import com.google.firebase.Timestamp;

public class Location {
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private int radius;
    private String startTime;
    private String endTime;
    private boolean isActive;
    private Timestamp createdAt;

    // Default constructor required for Firebase
    public Location() {}

    public Location(String name, String address, double latitude, double longitude,
                    int radius, String startTime, String endTime) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isActive = true;
        this.createdAt = Timestamp.now();
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}