package org.smart.attendance_beta.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DateTimeUtils {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat displayTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    /**
     * Get current date in YYYY-MM-DD format
     */
    public static String getCurrentDate() {
        return dateFormat.format(new Date());
    }

    /**
     * Get current time in HH:mm:ss format
     */
    public static String getCurrentTime() {
        return timeFormat.format(new Date());
    }

    /**
     * Get current time in HH:mm format for display
     */
    public static String getCurrentDisplayTime() {
        return displayTimeFormat.format(new Date());
    }

    /**
     * Format date for display (e.g., "Jan 27, 2025")
     */
    public static String formatDateForDisplay(String dateString) {
        try {
            Date date = dateFormat.parse(dateString);
            return displayDateFormat.format(date);
        } catch (ParseException e) {
            return dateString;
        }
    }

    /**
     * Format time for display (e.g., "09:30")
     */
    public static String formatTimeForDisplay(String timeString) {
        try {
            Date time = timeFormat.parse(timeString);
            return displayTimeFormat.format(time);
        } catch (ParseException e) {
            return timeString;
        }
    }

    /**
     * Calculate hours worked between clock in and clock out
     */
    public static double calculateHoursWorked(String clockInTime, String clockOutTime) {
        try {
            Date clockIn = timeFormat.parse(clockInTime);
            Date clockOut = timeFormat.parse(clockOutTime);

            long diffInMillis = clockOut.getTime() - clockIn.getTime();
            return diffInMillis / (1000.0 * 60 * 60); // Convert to hours
        } catch (ParseException e) {
            return 0.0;
        }
    }

    /**
     * Check if employee is late (after 9:00 AM)
     */
    public static boolean isLateArrival(String clockInTime, String workStartTime) {
        try {
            Date clockIn = timeFormat.parse(clockInTime);
            Date workStart = timeFormat.parse(workStartTime + ":00");

            return clockIn.after(workStart);
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Calculate late minutes
     */
    public static int calculateLateMinutes(String clockInTime, String workStartTime) {
        try {
            Date clockIn = timeFormat.parse(clockInTime);
            Date workStart = timeFormat.parse(workStartTime + ":00");

            if (clockIn.after(workStart)) {
                long diffInMillis = clockIn.getTime() - workStart.getTime();
                return (int) TimeUnit.MILLISECONDS.toMinutes(diffInMillis);
            }
            return 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * Format hours worked for display
     */
    public static String formatHoursWorked(double hours) {
        int totalMinutes = (int) (hours * 60);
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hrs > 0) {
            return String.format("%dh %dm", hrs, mins);
        } else {
            return String.format("%dm", mins);
        }
    }

    /**
     * Get greeting based on current time
     */
    public static String getTimeBasedGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (hour < 12) {
            return "Good Morning";
        } else if (hour < 17) {
            return "Good Afternoon";
        } else {
            return "Good Evening";
        }
    }
}