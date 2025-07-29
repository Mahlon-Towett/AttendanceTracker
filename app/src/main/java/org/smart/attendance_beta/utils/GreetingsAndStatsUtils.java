package org.smart.attendance_beta.utils;

import java.text.SimpleDateFormat;
import java.util.*;

public class GreetingsAndStatsUtils {

    // Common titles and honorifics
    private static final Set<String> TITLES = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "miss", "dr", "prof", "professor", "sir", "madam",
            "rev", "father", "sister", "brother", "captain", "major", "colonel",
            "lieutenant", "sergeant", "admiral", "general", "hon", "honorable"
    ));

    /**
     * Generates appropriate greeting with smart name handling
     * @param fullName The complete name string
     * @return Formatted greeting string
     */
    public static String generateSmartGreeting(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return getTimeBasedGreeting() + "!";
        }

        String displayName = getDisplayName(fullName.trim());
        return getTimeBasedGreeting() + ", " + displayName + "!";
    }

    /**
     * Smart name extraction logic
     * Rules:
     * 1. If first word is a title -> use next 1-2 words
     * 2. If first word is ≤3 letters -> use first 2 words
     * 3. If first word is >3 letters -> use first word only
     * 4. Handle edge cases like "Prof" (4 letters but is a title)
     */
    private static String getDisplayName(String fullName) {
        String[] nameParts = fullName.split("\\s+");

        if (nameParts.length == 0) return "there";
        if (nameParts.length == 1) return nameParts[0];

        String firstPart = nameParts[0].toLowerCase().replaceAll("[^a-z]", "");

        // Check if first part is a title
        if (TITLES.contains(firstPart)) {
            // It's a title, so use the next part(s)
            if (nameParts.length == 2) {
                return nameParts[1]; // Just the name after title
            } else if (nameParts.length >= 3) {
                // Use first and last name after title, or first two if short
                String secondPart = nameParts[1];
                if (secondPart.length() <= 3 && nameParts.length > 2) {
                    return secondPart + " " + nameParts[2];
                } else {
                    return secondPart + " " + nameParts[nameParts.length - 1];
                }
            }
            return nameParts.length > 1 ? nameParts[1] : nameParts[0];
        }

        // Not a title, apply length-based logic
        if (firstPart.length() <= 3) {
            // Short first name, include second part
            return nameParts[0] + " " + nameParts[1];
        } else {
            // Long enough first name, use only first part
            return nameParts[0];
        }
    }

    /**
     * Returns time-appropriate greeting
     */
    private static String getTimeBasedGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (hour < 12) {
            return "Good Morning";
        } else if (hour < 17) {
            return "Good Afternoon";
        } else if (hour < 21) {
            return "Good Evening";
        } else {
            return "Good Night";
        }
    }

    /**
     * Enhanced Weekly Statistics Calculator
     */
    public static class WeeklyStatsCalculator {

        public static WeeklyStats calculateAdvancedWeeklyStats(List<AttendanceRecord> records) {
            WeeklyStats stats = new WeeklyStats();

            // Get current week boundaries
            Calendar startOfWeek = Calendar.getInstance();
            startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            startOfWeek.set(Calendar.HOUR_OF_DAY, 0);
            startOfWeek.set(Calendar.MINUTE, 0);
            startOfWeek.set(Calendar.SECOND, 0);

            Calendar endOfWeek = (Calendar) startOfWeek.clone();
            endOfWeek.add(Calendar.DAY_OF_WEEK, 6);
            endOfWeek.set(Calendar.HOUR_OF_DAY, 23);
            endOfWeek.set(Calendar.MINUTE, 59);

            // Initialize daily stats
            stats.dailyStats = new ArrayList<>();
            String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

            for (int i = 0; i < 7; i++) {
                Calendar dayCalendar = (Calendar) startOfWeek.clone();
                dayCalendar.add(Calendar.DAY_OF_WEEK, i);

                DayStats dayStats = new DayStats();
                dayStats.dayName = dayNames[i];
                dayStats.date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dayCalendar.getTime());
                dayStats.isWorkDay = i < 5; // Monday-Friday are work days

                stats.dailyStats.add(dayStats);
            }

            // Process attendance records
            double totalHours = 0;
            int daysPresent = 0;
            int lateDays = 0;
            int earlyDepartures = 0;

            for (AttendanceRecord record : records) {
                // Find matching day
                for (DayStats dayStats : stats.dailyStats) {
                    if (dayStats.date.equals(record.date)) {
                        dayStats.clockInTime = record.clockInTime;
                        dayStats.clockOutTime = record.clockOutTime;
                        dayStats.hoursWorked = record.hoursWorked;
                        dayStats.isPresent = true;

                        if (dayStats.isWorkDay) {
                            daysPresent++;
                            totalHours += record.hoursWorked;

                            // Check if late (after 9:00 AM)
                            if (isLateArrival(record.clockInTime)) {
                                lateDays++;
                                dayStats.isLate = true;
                            }

                            // Check if early departure (before 5:00 PM)
                            if (isEarlyDeparture(record.clockOutTime)) {
                                earlyDepartures++;
                                dayStats.isEarlyDeparture = true;
                            }
                        }
                        break;
                    }
                }
            }

            // Calculate summary statistics
            stats.totalHours = totalHours;
            stats.daysPresent = daysPresent;
            stats.totalWorkDays = 5; // Monday-Friday
            stats.attendancePercentage = (daysPresent / 5.0) * 100;
            stats.averageHours = daysPresent > 0 ? totalHours / daysPresent : 0;
            stats.lateDays = lateDays;
            stats.earlyDepartures = earlyDepartures;

            // Calculate performance metrics
            stats.performanceScore = calculatePerformanceScore(stats);
            stats.trend = calculateTrend(stats);
            stats.weekRange = formatWeekRange(startOfWeek, endOfWeek);

            return stats;
        }

        private static boolean isLateArrival(String clockInTime) {
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

        private static double calculatePerformanceScore(WeeklyStats stats) {
            double score = 0;

            // Attendance score (40% weight)
            score += (stats.attendancePercentage / 100.0) * 40;

            // Punctuality score (30% weight)
            double punctualityRate = stats.daysPresent > 0 ?
                    (double)(stats.daysPresent - stats.lateDays) / stats.daysPresent : 1.0;
            score += punctualityRate * 30;

            // Hours worked score (30% weight)
            double expectedHours = stats.totalWorkDays * 8.0; // 8 hours per day
            double hoursScore = Math.min(stats.totalHours / expectedHours, 1.0);
            score += hoursScore * 30;

            return Math.round(score * 100.0) / 100.0;
        }

        private static String calculateTrend(WeeklyStats stats) {
            if (stats.performanceScore >= 90) return "Excellent";
            else if (stats.performanceScore >= 80) return "Good";
            else if (stats.performanceScore >= 70) return "Average";
            else if (stats.performanceScore >= 60) return "Below Average";
            else return "Needs Improvement";
        }

        private static String formatWeekRange(Calendar start, Calendar end) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return sdf.format(start.getTime()) + " - " + sdf.format(end.getTime());
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
        public double performanceScore;
        public String trend;
        public String weekRange;
    }

    public static class DayStats {
        public String dayName;
        public String date;
        public String clockInTime;
        public String clockOutTime;
        public double hoursWorked;
        public boolean isPresent;
        public boolean isWorkDay;
        public boolean isLate;
        public boolean isEarlyDeparture;
    }

    public static class AttendanceRecord {
        public String date;
        public String clockInTime;
        public String clockOutTime;
        public double hoursWorked;
    }

    // Test method to verify greeting logic
    public static void testGreetingLogic() {
        System.out.println("=== Greeting Logic Tests ===");

        // Test cases
        String[] testNames = {
                "John Smith",           // Regular name -> "Good Morning, John!"
                "Dr. Sarah Johnson",    // Title + name -> "Good Morning, Sarah Johnson!"
                "Mr. Li Zhang",         // Title + short name -> "Good Morning, Li Zhang!"
                "Prof. Michael Brown",  // Title (4 letters) -> "Good Morning, Michael Brown!"
                "Ms. Amy Chen",         // Title + short -> "Good Morning, Amy Chen!"
                "Luke Williams",        // 4-letter first name -> "Good Morning, Luke!"
                "Ben Watson",           // 3-letter first name -> "Good Morning, Ben Watson!"
                "Jo Smith",             // 2-letter first name -> "Good Morning, Jo Smith!"
                "Elizabeth Anderson",   // Long first name -> "Good Morning, Elizabeth!"
                "Rev. Father Thomas",   // Multiple titles -> "Good Morning, Father Thomas!"
                "Dr. Kim",              // Title + single name -> "Good Morning, Kim!"
                "Captain Jack Sparrow", // Military title -> "Good Morning, Jack Sparrow!"
        };

        for (String name : testNames) {
            System.out.println(name + " -> " + generateSmartGreeting(name));
        }
    }
}