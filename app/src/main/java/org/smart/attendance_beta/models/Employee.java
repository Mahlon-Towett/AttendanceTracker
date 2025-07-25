// models/Employee.java
package org.smart.attendance_beta.models;

import com.google.firebase.Timestamp;

public class Employee {
    private String userId;
    private String employeeId;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private String role;
    private boolean isActive;
    private Timestamp createdAt;
    private String loginMethod;
    private boolean profileCompleted;

    // Attendance related fields
    private String attendanceStatus;
    private String clockInTime;
    private String clockOutTime;

    // Default constructor required for Firebase
    public Employee() {}

    public Employee(String userId, String employeeId, String fullName, String email,
                    String phone, String department, String role) {
        this.userId = userId;
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.role = role;
        this.isActive = true;
        this.attendanceStatus = "absent";
        this.clockInTime = "-";
        this.clockOutTime = "-";
        this.profileCompleted = true;
        this.loginMethod = "email";
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getLoginMethod() { return loginMethod; }
    public void setLoginMethod(String loginMethod) { this.loginMethod = loginMethod; }

    public boolean isProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(boolean profileCompleted) { this.profileCompleted = profileCompleted; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    public String getClockInTime() { return clockInTime; }
    public void setClockInTime(String clockInTime) { this.clockInTime = clockInTime; }

    public String getClockOutTime() { return clockOutTime; }
    public void setClockOutTime(String clockOutTime) { this.clockOutTime = clockOutTime; }
}