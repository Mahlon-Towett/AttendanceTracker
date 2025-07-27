package org.smart.attendance_beta.models;

import com.google.firebase.Timestamp;

public class AttendanceRecord {
    private String employeeDocId;
    private String pfNumber;
    private String employeeName;
    private String date;
    private String clockInTime;
    private String clockOutTime;
    private Timestamp clockInTimestamp;
    private Timestamp clockOutTimestamp;
    private double clockInLatitude;
    private double clockInLongitude;
    private double clockOutLatitude;
    private double clockOutLongitude;
    private String status;
    private double totalHours;
    private boolean isLate;
    private int lateMinutes;
    private String locationName;
    private Timestamp createdAt;

    // Default constructor required for Firebase
    public AttendanceRecord() {}

    public AttendanceRecord(String employeeDocId, String pfNumber, String employeeName,
                            String date, double latitude, double longitude, String locationName) {
        this.employeeDocId = employeeDocId;
        this.pfNumber = pfNumber;
        this.employeeName = employeeName;
        this.date = date;
        this.clockInLatitude = latitude;
        this.clockInLongitude = longitude;
        this.locationName = locationName;
        this.clockInTimestamp = Timestamp.now();
        this.status = "Present";
        this.totalHours = 0;
        this.isLate = false;
        this.lateMinutes = 0;
        this.createdAt = Timestamp.now();

        // Set clock in time
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        this.clockInTime = timeFormat.format(new java.util.Date());
    }

    // Getters and Setters
    public String getEmployeeDocId() { return employeeDocId; }
    public void setEmployeeDocId(String employeeDocId) { this.employeeDocId = employeeDocId; }

    public String getPfNumber() { return pfNumber; }
    public void setPfNumber(String pfNumber) { this.pfNumber = pfNumber; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getClockInTime() { return clockInTime; }
    public void setClockInTime(String clockInTime) { this.clockInTime = clockInTime; }

    public String getClockOutTime() { return clockOutTime; }
    public void setClockOutTime(String clockOutTime) { this.clockOutTime = clockOutTime; }

    public Timestamp getClockInTimestamp() { return clockInTimestamp; }
    public void setClockInTimestamp(Timestamp clockInTimestamp) { this.clockInTimestamp = clockInTimestamp; }

    public Timestamp getClockOutTimestamp() { return clockOutTimestamp; }
    public void setClockOutTimestamp(Timestamp clockOutTimestamp) { this.clockOutTimestamp = clockOutTimestamp; }

    public double getClockInLatitude() { return clockInLatitude; }
    public void setClockInLatitude(double clockInLatitude) { this.clockInLatitude = clockInLatitude; }

    public double getClockInLongitude() { return clockInLongitude; }
    public void setClockInLongitude(double clockInLongitude) { this.clockInLongitude = clockInLongitude; }

    public double getClockOutLatitude() { return clockOutLatitude; }
    public void setClockOutLatitude(double clockOutLatitude) { this.clockOutLatitude = clockOutLatitude; }

    public double getClockOutLongitude() { return clockOutLongitude; }
    public void setClockOutLongitude(double clockOutLongitude) { this.clockOutLongitude = clockOutLongitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTotalHours() { return totalHours; }
    public void setTotalHours(double totalHours) { this.totalHours = totalHours; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }

    public int getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(int lateMinutes) { this.lateMinutes = lateMinutes; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}