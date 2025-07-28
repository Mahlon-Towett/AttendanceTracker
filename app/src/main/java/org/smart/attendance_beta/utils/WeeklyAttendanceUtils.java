// Enhanced WeeklyAttendanceUtils.java - Comprehensive weekly metrics
package org.smart.attendance_beta.utils;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklyAttendanceUtils {

    public static class WeeklyStats {
        public double totalHours;
        public int daysPresent;
        public int daysLate;
        public int totalWorkDays;
        public double averageHours;
        public boolean perfectAttendance;
        public List<DayStats> dailyStats;
        public String weekRange;
        public double attendancePercentage;

        public WeeklyStats() {
            this.dailyStats = new ArrayList<>();
        }
    }

    public static class DayStats {
        public String date;
        public String dayName;
        public boolean present;
        public boolean late;
        public double hoursWorked;
        public String clockInTime;
        public String clockOutTime;
        public String status;

        public DayStats(String date, String dayName) {
            this.date = date;
            this.dayName = dayName;
            this.present = false;
            this.late = false;
            this.hoursWorked = 0.0;
            this.clockInTime = "--:--";
            this.clockOutTime = "--:--";
            this.status = "Absent";
        }
    }

    public interface WeeklyStatsCallback {
        void onStatsLoaded(WeeklyStats stats);
        void onError(String error);
    }

    /**
     * Load comprehensive weekly attendance statistics
     */
    public static void loadWeeklyStats(String employeeDocId, WeeklyStatsCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get current week date range
        String[] weekDates = getCurrentWeekDates();
        String startDate = weekDates[0];
        String endDate = weekDates[6]; // Include weekend for completeness

        // Query attendance records for the current week
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        WeeklyStats stats = calculateWeeklyStats(task.getResult().getDocuments(), weekDates);
                        callback.onStatsLoaded(stats);
                    } else {
                        callback.onError("Failed to load weekly stats: " + task.getException().getMessage());
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error loading weekly stats: " + e.getMessage()));
    }

    /**
     * Get current week dates (Monday to Sunday)
     */
    private static String[] getCurrentWeekDates() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // Get Monday of current week
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysToSubtract = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        calendar.add(Calendar.DAY_OF_MONTH, -daysToSubtract);

        String[] weekDates = new String[7];
        for (int i = 0; i < 7; i++) {
            weekDates[i] = dateFormat.format(calendar.getTime());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        return weekDates;
    }

    /**
     * Calculate comprehensive weekly statistics
     */
    private static WeeklyStats calculateWeeklyStats(List<DocumentSnapshot> attendanceDocs, String[] weekDates) {
        WeeklyStats stats = new WeeklyStats();

        // Create map for quick lookup
        Map<String, DocumentSnapshot> attendanceMap = new HashMap<>();
        for (DocumentSnapshot doc : attendanceDocs) {
            String date = doc.getString("date");
            if (date != null) {
                attendanceMap.put(date, doc);
            }
        }

        // Calculate stats for each day of the week
        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

        for (int i = 0; i < 7; i++) {
            String date = weekDates[i];
            String dayName = dayNames[i];
            DayStats dayStats = new DayStats(date, dayName);

            if (attendanceMap.containsKey(date)) {
                DocumentSnapshot doc = attendanceMap.get(date);
                processAttendanceRecord(doc, dayStats, stats);
            }

            stats.dailyStats.add(dayStats);
        }

        // Calculate overall statistics
        stats.totalWorkDays = 5; // Monday to Friday
        stats.averageHours = stats.daysPresent > 0 ? stats.totalHours / stats.daysPresent : 0.0;
        stats.perfectAttendance = (stats.daysPresent == 5 && stats.daysLate == 0);
        stats.attendancePercentage = (stats.daysPresent * 100.0) / stats.totalWorkDays;

        // Format week range
        stats.weekRange = formatWeekRange(weekDates[0], weekDates[4]); // Monday to Friday

        return stats;
    }

    /**
     * Process individual attendance record
     */
    private static void processAttendanceRecord(DocumentSnapshot doc, DayStats dayStats, WeeklyStats stats) {
        String clockInTime = doc.getString("clockInTime");
        String clockOutTime = doc.getString("clockOutTime");
        Boolean isLate = doc.getBoolean("isLate");
        Double totalHours = doc.getDouble("totalHours");
        String status = doc.getString("status");

        dayStats.present = true;
        dayStats.clockInTime = clockInTime != null ?
                DateTimeUtils.formatTimeForDisplay(clockInTime) : "--:--";
        dayStats.clockOutTime = clockOutTime != null ?
                DateTimeUtils.formatTimeForDisplay(clockOutTime) : "--:--";
        dayStats.late = isLate != null && isLate;
        dayStats.hoursWorked = totalHours != null ? totalHours : 0.0;
        dayStats.status = status != null ? status : "Present";

        // Update weekly totals (only count weekdays)
        if (isWeekday(dayStats.dayName)) {
            stats.daysPresent++;
            if (dayStats.late) {
                stats.daysLate++;
            }
            if (dayStats.hoursWorked > 0) {
                stats.totalHours += dayStats.hoursWorked;
            }
        }
    }

    /**
     * Check if day is a weekday (Monday-Friday)
     */
    private static boolean isWeekday(String dayName) {
        return !dayName.equals("Saturday") && !dayName.equals("Sunday");
    }

    /**
     * Format week range for display
     */
    private static String formatWeekRange(String startDate, String endDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

            Date start = inputFormat.parse(startDate);
            Date end = inputFormat.parse(endDate);

            return outputFormat.format(start) + " - " + outputFormat.format(end);
        } catch (Exception e) {
            return startDate + " - " + endDate;
        }
    }

    /**
     * Get attendance trend (improving, declining, stable)
     */
    public static void getAttendanceTrend(String employeeDocId, TrendCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get last 4 weeks of data
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.WEEK_OF_YEAR, -4);
        String fourWeeksAgo = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereGreaterThanOrEqualTo("date", fourWeeksAgo)
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AttendanceTrend trend = calculateTrend(task.getResult().getDocuments());
                        callback.onTrendCalculated(trend);
                    } else {
                        callback.onError("Failed to calculate trend: " + task.getException().getMessage());
                    }
                });
    }

    public static class AttendanceTrend {
        public String direction; // "improving", "declining", "stable"
        public double changePercentage;
        public String message;
        public List<WeeklyPoint> weeklyPoints;

        public AttendanceTrend() {
            this.weeklyPoints = new ArrayList<>();
        }
    }

    public static class WeeklyPoint {
        public String weekOf;
        public int daysPresent;
        public double hoursWorked;
        public double attendanceRate;
    }

    public interface TrendCallback {
        void onTrendCalculated(AttendanceTrend trend);
        void onError(String error);
    }

    /**
     * Calculate attendance trend over the last 4 weeks
     */
    private static AttendanceTrend calculateTrend(List<DocumentSnapshot> docs) {
        AttendanceTrend trend = new AttendanceTrend();

        // Group by weeks
        Map<String, List<DocumentSnapshot>> weeklyGroups = new HashMap<>();
        SimpleDateFormat weekFormat = new SimpleDateFormat("yyyy-'W'ww", Locale.getDefault());

        for (DocumentSnapshot doc : docs) {
            String date = doc.getString("date");
            if (date != null) {
                try {
                    Date docDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date);
                    String weekKey = weekFormat.format(docDate);

                    weeklyGroups.computeIfAbsent(weekKey, k -> new ArrayList<>()).add(doc);
                } catch (Exception e) {
                    // Skip invalid dates
                }
            }
        }

        // Calculate weekly points
        for (Map.Entry<String, List<DocumentSnapshot>> entry : weeklyGroups.entrySet()) {
            WeeklyPoint point = new WeeklyPoint();
            point.weekOf = entry.getKey();

            for (DocumentSnapshot doc : entry.getValue()) {
                point.daysPresent++;
                Double hours = doc.getDouble("totalHours");
                if (hours != null) {
                    point.hoursWorked += hours;
                }
            }

            point.attendanceRate = (point.daysPresent * 100.0) / 5; // Assuming 5-day work week
            trend.weeklyPoints.add(point);
        }

        // Determine trend direction
        if (trend.weeklyPoints.size() >= 2) {
            WeeklyPoint first = trend.weeklyPoints.get(0);
            WeeklyPoint last = trend.weeklyPoints.get(trend.weeklyPoints.size() - 1);

            double change = last.attendanceRate - first.attendanceRate;
            trend.changePercentage = Math.abs(change);

            if (change > 5) {
                trend.direction = "improving";
                trend.message = "Your attendance is improving! Keep up the good work! 📈";
            } else if (change < -5) {
                trend.direction = "declining";
                trend.message = "Your attendance needs attention. Let's get back on track! 📉";
            } else {
                trend.direction = "stable";
                trend.message = "Your attendance is consistent. Great job maintaining regularity! 📊";
            }
        } else {
            trend.direction = "insufficient_data";
            trend.message = "Not enough data to determine trend. Keep tracking! 📅";
        }

        return trend;
    }

    /**
     * Format hours worked for display
     */
    public static String formatHoursWorked(double hours) {
        if (hours == 0) return "0h 0m";

        int totalMinutes = (int) (hours * 60);
        int hrs = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hrs > 0 && mins > 0) {
            return String.format("%dh %dm", hrs, mins);
        } else if (hrs > 0) {
            return String.format("%dh", hrs);
        } else {
            return String.format("%dm", mins);
        }
    }

    /**
     * Get performance badge based on weekly stats
     */
    public static String getPerformanceBadge(WeeklyStats stats) {
        if (stats.perfectAttendance) {
            return "🏆 Perfect Week!";
        } else if (stats.daysPresent >= 4 && stats.daysLate == 0) {
            return "⭐ Excellent!";
        } else if (stats.daysPresent >= 3) {
            return "👍 Good!";
        } else if (stats.daysPresent >= 2) {
            return "📈 Improving";
        } else {
            return "📅 Needs Focus";
        }
    }

    /**
     * Get motivational message based on performance
     */
    public static String getMotivationalMessage(WeeklyStats stats, AttendanceTrend trend) {
        if (stats.perfectAttendance) {
            return "Outstanding! You had perfect attendance this week! 🌟";
        } else if (trend.direction.equals("improving")) {
            return "Great progress! Your attendance is getting better each week! 🚀";
        } else if (stats.daysPresent >= 4) {
            return "Almost there! Just one more day for perfect attendance! 💪";
        } else if (trend.direction.equals("declining")) {
            return "Let's bounce back! Every great comeback starts with showing up! 🔄";
        } else {
            return "Every day counts! You've got this! 💫";
        }
    }
}