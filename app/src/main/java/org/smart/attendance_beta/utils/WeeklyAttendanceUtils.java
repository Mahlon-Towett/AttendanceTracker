package org.smart.attendance_beta.utils;

import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.*;

public class WeeklyAttendanceUtils {

    private static final String TAG = "WeeklyAttendanceUtils";

    // Callbacks for async operations
    public interface WeeklyStatsCallback {
        void onStatsLoaded(WeeklyStats stats);
        void onError(String error);
    }

    public interface TrendCallback {
        void onTrendCalculated(AttendanceTrend trend);
        void onError(String error);
    }

    /**
     * Load weekly statistics for an employee
     */
    public static void loadWeeklyStats(String employeeDocId, WeeklyStatsCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get current week date range
        String[] weekDates = getCurrentWeekDates();
        String startDate = weekDates[0];
        String endDate = weekDates[6];

        db.collection("attendance")
                .whereEqualTo("employeeId", employeeDocId)
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
     * Get attendance trend for an employee
     */
    public static void getAttendanceTrend(String employeeDocId, TrendCallback callback) {
        // For now, return a simple trend based on current week
        loadWeeklyStats(employeeDocId, new WeeklyStatsCallback() {
            @Override
            public void onStatsLoaded(WeeklyStats stats) {
                AttendanceTrend trend = new AttendanceTrend();

                // Determine trend based on performance
                if (stats.attendancePercentage >= 90) {
                    trend.direction = "improving";
                    trend.message = "📈 Excellent attendance trend!";
                    trend.description = "Outstanding performance this week";
                } else if (stats.attendancePercentage >= 70) {
                    trend.direction = "stable";
                    trend.message = "📊 Steady attendance pattern";
                    trend.description = "Consistent performance";
                } else {
                    trend.direction = "declining";
                    trend.message = "📉 Room for improvement";
                    trend.description = "Focus on better attendance";
                }

                callback.onTrendCalculated(trend);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
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

        // Initialize
        stats.dailyStats = new ArrayList<>();
        stats.totalWorkDays = 5; // Monday to Friday
        stats.daysPresent = 0;
        stats.totalHours = 0.0;
        stats.lateDays = 0;
        stats.earlyDepartures = 0;

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
        stats.attendancePercentage = stats.totalWorkDays > 0 ?
                (double) stats.daysPresent / stats.totalWorkDays * 100 : 0;
        stats.averageHours = stats.daysPresent > 0 ?
                stats.totalHours / stats.daysPresent : 0;

        // ✅ ADDED: Sync daysLate with lateDays for compatibility
        stats.daysLate = stats.lateDays;

        // Set week range
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        try {
            SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date startDate = parseFormat.parse(weekDates[0]);
            Date endDate = parseFormat.parse(weekDates[6]);
            stats.weekRange = displayFormat.format(startDate) + " - " + displayFormat.format(endDate);
        } catch (Exception e) {
            stats.weekRange = "This Week";
        }

        return stats;
    }

    private static void processAttendanceRecord(DocumentSnapshot doc, DayStats dayStats, WeeklyStats stats) {
        String clockInTime = doc.getString("clockInTime");
        String clockOutTime = doc.getString("clockOutTime");
        Double hoursWorked = doc.getDouble("hoursWorked");

        dayStats.clockInTime = clockInTime;
        dayStats.clockOutTime = clockOutTime;
        dayStats.hoursWorked = hoursWorked != null ? hoursWorked : 0.0;
        dayStats.isPresent = clockInTime != null;

        // Only count work days (Monday-Friday) for main stats
        if (dayStats.isWorkDay()) {
            if (dayStats.isPresent) {
                stats.daysPresent++;
                stats.totalHours += dayStats.hoursWorked;

                // Check for late arrival (after 9:00 AM)
                if (isLateArrival(clockInTime)) {
                    stats.lateDays++;
                    dayStats.isLate = true;
                }

                // Check for early departure (before 5:00 PM)
                if (isEarlyDeparture(clockOutTime)) {
                    stats.earlyDepartures++;
                    dayStats.isEarlyDeparture = true;
                }
            }
        }
    }

    private static boolean isLateArrival(String clockInTime) {
        if (clockInTime == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date clockIn = sdf.parse(clockInTime);
            Date nineAM = sdf.parse("09:00");
            return clockIn.after(nineAM);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isEarlyDeparture(String clockOutTime) {
        if (clockOutTime == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date clockOut = sdf.parse(clockOutTime);
            Date fivePM = sdf.parse("17:00");
            return clockOut.before(fivePM);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Format hours worked for display
     */
    public static String formatHoursWorked(double hours) {
        if (hours == 0) {
            return "0.0 hrs";
        }
        return String.format(Locale.getDefault(), "%.1f hrs", hours);
    }

    /**
     * Get performance badge based on attendance percentage
     */
    public static String getPerformanceBadge(WeeklyStats stats) {
        if (stats.attendancePercentage >= 100) {
            return "🏆 Perfect";
        } else if (stats.attendancePercentage >= 90) {
            return "⭐ Excellent";
        } else if (stats.attendancePercentage >= 80) {
            return "👍 Good";
        } else if (stats.attendancePercentage >= 70) {
            return "📈 Improving";
        } else {
            return "⚠️ Needs Attention";
        }
    }

    /**
     * Get motivational message based on stats and trend
     */
    public static String getMotivationalMessage(WeeklyStats stats, AttendanceTrend trend) {
        if (stats.attendancePercentage >= 100) {
            return "🎉 Perfect attendance! Keep up the excellent work!";
        } else if (stats.attendancePercentage >= 90) {
            return "⭐ Great job this week! You're doing fantastic!";
        } else if (stats.attendancePercentage >= 80) {
            return "👍 Good work! A few more days and you'll be excellent!";
        } else if (trend.direction.equals("improving")) {
            return "📈 You're improving! Keep pushing forward!";
        } else {
            return "💪 Let's aim for better attendance next week!";
        }
    }

    // Data classes
    public static class WeeklyStats {
        public List<DayStats> dailyStats;
        public double totalHours;
        public int daysPresent;
        public int totalWorkDays;
        public double attendancePercentage;
        public double averageHours;
        public int lateDays;
        public int earlyDepartures;
        public int daysLate;  // ✅ ADDED: Alias for lateDays to match your existing code
        public String weekRange;

        // Constructor to ensure daysLate matches lateDays
        public WeeklyStats() {
            this.lateDays = 0;
            this.earlyDepartures = 0;
            this.daysLate = 0;  // Initialize both for compatibility
        }
    }

    public static class DayStats {
        public String date;
        public String dayName;
        public String clockInTime;
        public String clockOutTime;
        public double hoursWorked;
        public boolean isPresent;
        public boolean isLate;
        public boolean isEarlyDeparture;

        public DayStats(String date, String dayName) {
            this.date = date;
            this.dayName = dayName;
            this.hoursWorked = 0.0;
            this.isPresent = false;
            this.isLate = false;
            this.isEarlyDeparture = false;
        }

        public boolean isWorkDay() {
            // Monday to Friday are work days
            return !dayName.equals("Saturday") && !dayName.equals("Sunday");
        }
    }

    public static class AttendanceTrend {
        public String direction; // "improving", "declining", "stable"
        public String description;
        public String message;  // ✅ ADDED: Message field for trend display

        public AttendanceTrend() {
            this.direction = "stable";
            this.description = "Based on current week's performance";
            this.message = "Keep up the good work!";
        }
    }
}