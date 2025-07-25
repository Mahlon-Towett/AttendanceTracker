#!/usr/bin/env python3
"""
Remove Empty Files Script
Run this in your app/ directory to remove empty files that cause build errors
"""

import os
import sys

def remove_file_if_empty(file_path):
    """Remove file if it exists and is empty"""
    if os.path.exists(file_path):
        if os.path.getsize(file_path) == 0:
            os.remove(file_path)
            print(f"🗑️  Removed empty file: {file_path}")
        else:
            print(f"📄 File has content (keeping): {file_path}")
    else:
        print(f"📄 File doesn't exist: {file_path}")

def main():
    print("🧹 Removing empty files that cause build errors...")
    print("📍 Current directory:", os.getcwd())
    
    # Check if we're in the right directory
    if not os.path.exists("build.gradle"):
        print("❌ Error: build.gradle not found. Please run this script in the app/ directory")
        sys.exit(1)
    
    # Base paths
    base_src = "src/main"
    java_base = f"{base_src}/java/org/smart/attendance_beta"
    res_base = f"{base_src}/res"
    
    print("\n🗑️  Removing empty Java files...")
    
    # Java files we don't have code for yet
    empty_java_files = [
        f"{java_base}/AttendanceActivity.java",
        f"{java_base}/EmployeeDetailsActivity.java",
        f"{java_base}/AddEmployeeActivity.java",
        f"{java_base}/LocationManagementActivity.java",
        f"{java_base}/ReportsActivity.java",
        f"{java_base}/SettingsActivity.java",
        f"{java_base}/models/AttendanceRecord.java",
        f"{java_base}/models/Location.java",
        f"{java_base}/adapters/EmployeeAdapter.java",
        f"{java_base}/adapters/AttendanceAdapter.java",
        f"{java_base}/adapters/LocationAdapter.java",
        f"{java_base}/services/LocationService.java",
        f"{java_base}/services/GeofenceTransitionsIntentService.java",
        f"{java_base}/receivers/GeofenceBroadcastReceiver.java",
        f"{java_base}/utils/LocationUtils.java",
        f"{java_base}/utils/DateTimeUtils.java"
    ]
    
    for file_path in empty_java_files:
        remove_file_if_empty(file_path)
    
    print("\n🗑️  Removing empty layout files...")
    
    # Layout files we don't have designs for yet
    empty_layout_files = [
        f"{res_base}/layout/activity_attendance.xml",
        f"{res_base}/layout/activity_employee_details.xml",
        f"{res_base}/layout/activity_add_employee.xml",
        f"{res_base}/layout/activity_location_management.xml",
        f"{res_base}/layout/activity_reports.xml",
        f"{res_base}/layout/activity_settings.xml",
        f"{res_base}/layout/item_employee.xml",
        f"{res_base}/layout/item_attendance.xml",
        f"{res_base}/layout/item_location.xml"
    ]
    
    for file_path in empty_layout_files:
        remove_file_if_empty(file_path)
    
    print("\n🗑️  Removing empty resource files...")
    
    # Resource files we don't need yet
    empty_resource_files = [
        f"{res_base}/values/styles.xml",  # Using themes.xml instead
        f"{res_base}/xml/file_paths.xml"  # Not needed until we implement file sharing
    ]
    
    for file_path in empty_resource_files:
        remove_file_if_empty(file_path)
    
    # Remove empty directories
    empty_dirs = [
        f"{java_base}/services",
        f"{java_base}/receivers",
        f"{res_base}/xml"
    ]
    
    print("\n🗑️  Removing empty directories...")
    
    for directory in empty_dirs:
        try:
            if os.path.exists(directory) and not os.listdir(directory):
                os.rmdir(directory)
                print(f"🗑️  Removed empty directory: {directory}")
            elif os.path.exists(directory):
                print(f"📁 Directory not empty (keeping): {directory}")
        except OSError as e:
            print(f"📁 Could not remove directory {directory}: {e}")
    
    print("\n✅ Cleanup completed!")
    print("\n📋 What was removed:")
    print("   🗑️  Empty Java files (activities, models, adapters we haven't implemented)")
    print("   🗑️  Empty layout files (screens we haven't designed)")
    print("   🗑️  Empty resource files (not needed yet)")
    print("   📁 Empty directories")
    
    print("\n💡 Your project should now build without 'premature end of file' errors!")
    print("\n🎯 Remaining files (with content):")
    print("   ✅ LoginActivity.java")
    print("   ✅ RegistrationActivity.java") 
    print("   ✅ AdminDashboardActivity.java")
    print("   ✅ EmployeeDashboardActivity.java")
    print("   ✅ SplashActivity.java")
    print("   ✅ models/Employee.java")
    print("   ✅ utils/FirebaseUtils.java")
    print("   ✅ All layout files (login, registration, dashboards)")
    print("   ✅ All resource files (colors, strings, drawables)")

if __name__ == "__main__":
    main()