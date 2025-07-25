#!/usr/bin/env python3
"""
Android Attendance System Project Structure Generator
Run this script in your app/ directory to create the complete file structure
"""

import os
import sys

def create_directory(path):
    """Create directory if it doesn't exist"""
    if not os.path.exists(path):
        os.makedirs(path)
        print(f"✅ Created directory: {path}")
    else:
        print(f"📁 Directory exists: {path}")

def create_empty_file(file_path):
    """Create empty file"""
    # Create directory if it doesn't exist
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    
    # Create empty file if it doesn't exist
    if not os.path.exists(file_path):
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write("")
        print(f"📄 Created file: {file_path}")
    else:
        print(f"📄 File exists: {file_path}")

def main():
    print("🚀 Starting Android Attendance System Project Structure Generation...")
    print("📍 Current directory:", os.getcwd())
    
    # Check if we're in the right directory
    if not os.path.exists("build.gradle"):
        print("❌ Error: build.gradle not found. Please run this script in the app/ directory")
        sys.exit(1)
    
    # Base paths
    base_src = "src/main"
    java_base = f"{base_src}/java/org/smart/attendance_beta"
    res_base = f"{base_src}/res"
    
    print("\n📁 Creating directory structure...")
    
    # Create main directories
    directories = [
        f"{java_base}/models",
        f"{java_base}/adapters", 
        f"{java_base}/services",
        f"{java_base}/receivers",
        f"{java_base}/utils",
        f"{res_base}/layout",
        f"{res_base}/values",
        f"{res_base}/drawable",
        f"{res_base}/menu",
        f"{res_base}/xml"
    ]
    
    for directory in directories:
        create_directory(directory)
    
    print("\n📄 Creating Java files...")
    
    # Main Activity Files
    java_files = [
        f"{java_base}/SplashActivity.java",
        f"{java_base}/LoginActivity.java",
        f"{java_base}/RegistrationActivity.java",
        f"{java_base}/AdminDashboardActivity.java",
        f"{java_base}/EmployeeDashboardActivity.java",
        f"{java_base}/AttendanceActivity.java",
        f"{java_base}/EmployeeDetailsActivity.java",
        f"{java_base}/AddEmployeeActivity.java",
        f"{java_base}/LocationManagementActivity.java",
        f"{java_base}/ReportsActivity.java",
        f"{java_base}/SettingsActivity.java"
    ]
    
    # Model Files
    model_files = [
        f"{java_base}/models/Employee.java",
        f"{java_base}/models/AttendanceRecord.java",
        f"{java_base}/models/Location.java"
    ]
    
    # Adapter Files
    adapter_files = [
        f"{java_base}/adapters/EmployeeAdapter.java",
        f"{java_base}/adapters/AttendanceAdapter.java",
        f"{java_base}/adapters/LocationAdapter.java"
    ]
    
    # Service Files
    service_files = [
        f"{java_base}/services/LocationService.java",
        f"{java_base}/services/GeofenceTransitionsIntentService.java"
    ]
    
    # Receiver Files
    receiver_files = [
        f"{java_base}/receivers/GeofenceBroadcastReceiver.java"
    ]
    
    # Utility Files
    util_files = [
        f"{java_base}/utils/FirebaseUtils.java",
        f"{java_base}/utils/LocationUtils.java",
        f"{java_base}/utils/DateTimeUtils.java"
    ]
    
    # Create all Java files
    all_java_files = java_files + model_files + adapter_files + service_files + receiver_files + util_files
    
    for file_path in all_java_files:
        create_empty_file(file_path)
    
    print("\n📱 Creating layout files...")
    
    # Layout Files
    layout_files = [
        f"{res_base}/layout/activity_splash.xml",
        f"{res_base}/layout/activity_login.xml",
        f"{res_base}/layout/activity_registration.xml",
        f"{res_base}/layout/activity_admin_dashboard.xml",
        f"{res_base}/layout/activity_employee_dashboard.xml",
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
    
    for file_path in layout_files:
        create_empty_file(file_path)
    
    print("\n🎨 Creating resource files...")
    
    # Resource Files
    resource_files = [
        f"{res_base}/values/colors.xml",
        f"{res_base}/values/strings.xml",
        f"{res_base}/values/styles.xml",
        f"{res_base}/values/themes.xml",
        f"{res_base}/drawable/gradient_background.xml",
        f"{res_base}/drawable/gradient_background_green.xml",
        f"{res_base}/drawable/circle_background.xml",
        f"{res_base}/drawable/status_present_bg.xml",
        f"{res_base}/drawable/status_late_bg.xml",
        f"{res_base}/drawable/status_absent_bg.xml",
        f"{res_base}/menu/admin_menu.xml",
        f"{res_base}/menu/employee_item_menu.xml",
        f"{res_base}/xml/file_paths.xml",
        f"{res_base}/xml/data_extraction_rules.xml",
        f"{res_base}/xml/backup_rules.xml"
    ]
    
    for file_path in resource_files:
        create_empty_file(file_path)
    
    print("\n✅ Project structure generation completed!")
    print("\n📋 Summary:")
    print(f"   📁 Created {len(directories)} directories")
    print(f"   📄 Created {len(all_java_files)} Java files")
    print(f"   📱 Created {len(layout_files)} layout files") 
    print(f"   🎨 Created {len(resource_files)} resource files")
    print(f"   📊 Total files: {len(all_java_files) + len(layout_files) + len(resource_files)}")
    
    print("\n🎯 Next Steps:")
    print("1. Copy and paste the Java code into the respective activity files")
    print("2. Copy and paste the XML layouts into the layout files")
    print("3. Copy and paste the resource values (colors, strings, etc.)")
    print("4. Update package names in Java files to 'org.smart.attendance_beta'")
    print("5. Add google-services.json to the app/ directory")
    print("6. Sync the project in Android Studio")
    
    print("\n💡 File Structure Created:")
    print("   org/smart/attendance_beta/")
    print("   ├── Activities (11 files)")
    print("   ├── models/ (3 files)")
    print("   ├── adapters/ (3 files)")
    print("   ├── services/ (2 files)")
    print("   ├── receivers/ (1 file)")
    print("   └── utils/ (3 files)")
    print(f"   {res_base}/")
    print("   ├── layout/ (14 files)")
    print("   ├── values/ (4 files)")
    print("   ├── drawable/ (6 files)")
    print("   ├── menu/ (2 files)")
    print("   └── xml/ (3 files)")

if __name__ == "__main__":
    main()