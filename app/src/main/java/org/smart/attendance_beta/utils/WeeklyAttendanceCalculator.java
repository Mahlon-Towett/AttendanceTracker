// WeeklyAttendanceCalculator.java - Comprehensive weekly attendance statistics
package org.smart.attendance_beta.utils;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeeklyAttendanceCalculator {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    /**
     * Calculate comprehensive weekly statistics from attendance data
     */
    public WeeklyStats calculateWeeklyStats(QuerySnapshot attendanceSnapshot) {
        WeeklyStats stats = new WeeklyStats();

        if (attendanceSnapshot == null || attendanceSnapshot.isEmpty()) {
            return stats;
        }

        List<AttendanceDay> weekDays = new ArrayList<>();

        // Process each attendance record
        for (DocumentSnapshot doc : attendanceSnapshot.getDocuments()) {
            AttendanceDay day = processAttendanceRecord(doc);
            if (day != null) {
                weekDays.add(day);
            }
        }

        // Sort by date
        Collections.sort(weekDays, (a, b) -> a.date.compareTo(b.date));

        // Calculate statistics
        calculateBasicStats(stats, weekDays);
        calculateStreaks(stats, weekDays);
        calculatePerformanceMetrics(stats, weekDays);

        return stats;
    }

    /**
     * Process individual attendance record
     */
    private AttendanceDay processAttendanceRecord(DocumentSnapshot doc) {
        try {
            AttendanceDay day = new AttendanceDay();
            day.date = doc.getString("date");
            day.clockInTime = doc.getString("clockInTime");
            day.clockOutTime = doc.getString("clockOutTime");

            Double totalHours = doc.getDouble("totalHours");
            day.hoursWorked = totalHours != null ? totalHours : 0.0;

            Boolean isLate = doc.getBoolean("isLate");
            day.isLate = isLate != null ? isLate : false;

            Boolean isEarlyClockOut = doc.getBoolean("isEarlyClockOut");
            day.isEarlyDeparture = isEarlyClockOut != null ? isEarlyClockOut : false;

            Integer lateMinutes = doc.getLong("lateMinutes") != null ?
                    doc.getLong("lateMinutes").intValue() : 0;
            day.lateMinutes = lateMinutes;

            String status = doc.getString("status");
            day.status = status != null ? status : "Unknown";

            // Determine if this is a valid work day
            day.isWorkDay = day.hoursWorked > 0 || day.clockInTime != null;

            return day;
        } catch (Exception e) {
            // Skip invalid records
            return null;
        }
    }

    /**
     * Calculate basic weekly statistics
     */
    private void calculateBasicStats(WeeklyStats stats, List<AttendanceDay> weekDays) {
        stats.workDaysInWeek = getWorkDaysInCurrentWeek();

        for (AttendanceDay day : weekDays) {
            if (day.isWorkDay) {
                stats.daysWorked++;
                stats.totalHours += day.hoursWorked;

                if (day.isLate) {
                    stats.lateArrivals++;
                    stats.totalLateMinutes += day.lateMinutes;
                }

                if (day.isEarlyDeparture) {
                    stats.earlyDepartures++;
                }

                // Track daily hours for analysis
                stats.dailyHours.add(day.hoursWorked);
            }
        }

        // Calculate averages
        if (stats.daysWorked > 0) {
            stats.averageHoursPerDay = stats.totalHours / stats.daysWorked;
            stats.averageLateMinutes = stats.totalLateMinutes / (double) stats.lateArrivals;
        }
    }

    /**
     * Calculate attendance streaks
     */
    private void calculateStreaks(WeeklyStats stats, List<AttendanceDay> weekDays) {
        if (weekDays.isEmpty()) return;

        int currentStreak = 0;
        int longestStreak = 0;
        int tempStreak = 0;

        // Calculate streaks including current week
        WeekBoundaries currentWeek = getCurrentWeekBoundaries();
        List<String> expectedWorkDays = getExpectedWorkDays(currentWeek);

        // Check each expected work day
        for (String expectedDate : expectedWorkDays) {
            boolean worked = false;

            for (AttendanceDay day : weekDays) {
                if (expectedDate.equals(day.date) && day.isWorkDay) {
                    worked = true;
                    break;
                }
            }

            if (worked) {
                tempStreak++;
                longestStreak = Math.max(longestStreak, tempStreak);

                // If this is today or a future date, it counts toward current streak
                if (isDateTodayOrFuture(expectedDate)) {
                    currentStreak = tempStreak;
                }
            } else {
                // Streak broken
                longestStreak = Math.max(longestStreak, tempStreak);
                tempStreak = 0;

                // If this is today or earlier, current streak is 0
                if (!isDateFuture(expectedDate)) {
                    currentStreak = 0;
                }
            }
        }

        stats.currentStreak = currentStreak;
        stats.longestStreak = longestStreak;
    }

    /**
     * Calculate performance metrics
     */
    private void calculatePerformanceMetrics(WeeklyStats stats, List<AttendanceDay> weekDays) {
        // Calculate expected vs actual hours
        double expectedHours = stats.daysWorked * 8.0; // 8 hours per day
        if (expectedHours > 0) {
            stats.performancePercentage = (stats.totalHours / expectedHours) * 100;
        }

        // Calculate punctuality rate
        if (stats.daysWorked > 0) {
            int onTimeArrivals = stats.daysWorked - stats.lateArrivals;
            stats.punctualityRate = (onTimeArrivals / (double) stats.daysWorked) * 100;
        }

        // Calculate consistency (standard deviation of daily hours)
        if (stats.dailyHours.size() > 1) {
            double mean = stats.averageHoursPerDay;
            double sumSquaredDiffs = 0;

            for (double hours : stats.dailyHours) {
                sumSquaredDiffs += Math.pow(hours - mean, 2);
            }

            stats.hoursConsistency = Math.sqrt(sumSquaredDiffs / stats.dailyHours.size());
        }

        // Determine overall grade
        stats.weeklyGrade = calculateWeeklyGrade(stats);
    }

    /**
     * Calculate overall weekly performance grade
     */
    private String calculateWeeklyGrade(WeeklyStats stats) {
        double score = 0;

        // Performance score (40% weight)
        if (stats.performancePercentage >= 95) score += 40;
        else if (stats.performancePercentage >= 85) score += 35;
        else if (stats.performancePercentage >= 75) score += 30;
        else if (stats.performancePercentage >= 65) score += 25;
        else score += 20;

        // Punctuality score (30% weight)
        if (stats.punctualityRate >= 95) score += 30;
        else if (stats.punctualityRate >= 85) score += 25;
        else if (stats.punctualityRate >= 75) score += 20;
        else score += 15;

        // Attendance rate (30% weight)
        double attendanceRate = (stats.daysWorked / (double) stats.workDaysInWeek) * 100;
        if (attendanceRate >= 95) score += 30;
        else if (attendanceRate >= 85) score += 25;
        else if (attendanceRate >= 75) score += 20;
        else score += 15;

        // Determine grade based on total score
        if (score >= 90) return "A+";
        else if (score >= 85) return "A";
        else if (score >= 80) return "B+";
        else if (score >= 75) return "B";
        else if (score >= 70) return "C+";
        else if (score >= 65) return "C";
        else if (score >= 60) return "D";
        else return "F";
    }

    /**
     * Get current week boundaries (Monday to Friday)
     */
    public WeekBoundaries getCurrentWeekBoundaries() {
        Calendar cal = Calendar.getInstance();

        // Set to Monday of current week
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);

        String startDate = dateFormat.format(cal.getTime());

        // Set to Friday of current week
        cal.add(Calendar.DAY_OF_MONTH, 4);
        String endDate = dateFormat.format(cal.getTime());

        return new WeekBoundaries(startDate, endDate);
    }

    /**
     * Get expected work days for a week (Monday to Friday)
     */
    private List<String> getExpectedWorkDays(WeekBoundaries week) {
        List<String> workDays = new ArrayList<>();

        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateFormat.parse(week.startDate));

            for (int i = 0; i < 5; i++) { // Monday to Friday
                workDays.add(dateFormat.format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            // Return empty list on error
        }

        return workDays;
    }

    /**
     * Get number of work days in current week (considering only past and current days)
     */
    private int getWorkDaysInCurrentWeek() {
        Calendar today = Calendar.getInstance();
        Calendar weekStart = Calendar.getInstance();

        // Set to Monday of current week
        int dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        weekStart.add(Calendar.DAY_OF_MONTH, -daysFromMonday);

        int workDays = 0;
        Calendar counter = (Calendar) weekStart.clone();

        // Count work days from Monday to today (or Friday if today is past Friday)
        for (int i = 0; i < 5; i++) { // Monday to Friday
            if (counter.compareTo(today) <= 0) {
                workDays++;
            }
            counter.add(Calendar.DAY_OF_MONTH, 1);
        }

        return Math.max(1, workDays); // At least 1 to avoid division by zero
    }

    /**
     * Check if date is today or in the future
     */
    private boolean isDateTodayOrFuture(String dateStr) {
        try {
            Date date = dateFormat.parse(dateStr);
            Date today = new Date();

            // Normalize to start of day for comparison
            Calendar cal = Calendar.getInstance();
            cal.setTime(today);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            return date.compareTo(cal.getTime()) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if date is in the future
     */
    private boolean isDateFuture(String dateStr) {
        try {
            Date date = dateFormat.parse(dateStr);
            Date today = new Date();

            // Normalize to start of day for comparison
            Calendar cal = Calendar.getInstance();
            cal.setTime(today);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            return date.compareTo(cal.getTime()) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Data class for week boundaries
     */
    public static class WeekBoundaries {
        public final String startDate;
        public final String endDate;

        public WeekBoundaries(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    /**
     * Data class for individual attendance day
     */
    private static class AttendanceDay {
        String date;
        String clockInTime;
        String clockOutTime;
        double hoursWorked;
        boolean isLate;
        boolean isEarlyDeparture;
        int lateMinutes;
        String status;
        boolean isWorkDay;
    }

    /**
     * Comprehensive weekly statistics data class
     */
    public static class WeeklyStats {
        // Basic metrics
        public double totalHours = 0;
        public int daysWorked = 0;
        public int workDaysInWeek = 5;
        public double averageHoursPerDay = 0;

        // Attendance patterns
        public int currentStreak = 0;
        public int longestStreak = 0;
        public int lateArrivals = 0;
        public int earlyDepartures = 0;
        public int totalLateMinutes = 0;
        public double averageLateMinutes = 0;

        // Performance metrics
        public double performancePercentage = 0; // Actual vs expected hours
        public double punctualityRate = 0; // On-time arrival rate
        public double hoursConsistency = 0; // Standard deviation of daily hours
        public String weeklyGrade = "N/A";

        // Internal tracking
        public List<Double> dailyHours = new ArrayList<>();

        /**
         * Get formatted summary for display
         */
        public String getFormattedSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("📊 Weekly Performance Summary\n\n");
            summary.append("⏱️ Total Hours: ").append(formatHours(totalHours)).append("\n");
            summary.append("📅 Days Worked: ").append(daysWorked).append("/").append(workDaysInWeek).append("\n");
            summary.append("🔥 Current Streak: ").append(currentStreak).append(" days\n");
            summary.append("🏆 Best Streak: ").append(longestStreak).append(" days\n");
            summary.append("🎯 Performance: ").append(String.format("%.1f%%", performancePercentage)).append("\n");
            summary.append("⏰ Punctuality: ").append(String.format("%.1f%%", punctualityRate)).append("\n");
            summary.append("📈 Grade: ").append(weeklyGrade).append("\n");

            if (lateArrivals > 0) {
                summary.append("⚠️ Late Arrivals: ").append(lateArrivals).append("\n");
            }

            if (earlyDepartures > 0) {
                summary.append("🚪 Early Departures: ").append(earlyDepartures).append("\n");
            }

            return summary.toString();
        }

        /**
         * Format hours for display
         */
        private String formatHours(double hours) {
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
         * Get performance status for UI styling
         */
        public PerformanceStatus getPerformanceStatus() {
            if (performancePercentage >= 95 && punctualityRate >= 95) {
                return PerformanceStatus.EXCELLENT;
            } else if (performancePercentage >= 85 && punctualityRate >= 85) {
                return PerformanceStatus.GOOD;
            } else if (performancePercentage >= 75 && punctualityRate >= 75) {
                return PerformanceStatus.AVERAGE;
            } else if (performancePercentage >= 60 && punctualityRate >= 60) {
                return PerformanceStatus.BELOW_AVERAGE;
            } else {
                return PerformanceStatus.POOR;
            }
        }
    }

    /**
     * Performance status enum for UI styling
     */
    public enum PerformanceStatus {
        EXCELLENT,
        GOOD,
        AVERAGE,
        BELOW_AVERAGE,
        POOR
    }
}