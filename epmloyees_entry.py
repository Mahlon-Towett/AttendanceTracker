#!/usr/bin/env python3
"""
Employee Registration Script for Firebase Firestore
Adds employees to the attendance system database via command line interface
"""

import firebase_admin
from firebase_admin import credentials, firestore
import re
from datetime import datetime
import json
import os
import sys

class EmployeeRegistration:
    def __init__(self):
        self.db = None
        self.initialize_firebase()
        
    def initialize_firebase(self):
        """Initialize Firebase Admin SDK"""
        try:
            # Check if Firebase is already initialized
            firebase_admin.get_app()
            print("✅ Firebase already initialized")
        except ValueError:
            # Initialize Firebase
            service_account_path = self.get_service_account_path()
            if not service_account_path:
                return False
                
            cred = credentials.Certificate(service_account_path)
            firebase_admin.initialize_app(cred)
            print("✅ Firebase initialized successfully")
        
        self.db = firestore.client()
        return True
    
    def get_service_account_path(self):
        """Get the service account key file path"""
        possible_paths = [
            "service-account-key.json",
            "firebase-service-account.json",
            "attendance-system-demo-d6c09-firebase-adminsdk.json",
            "../service-account-key.json"
        ]
        
        # First, ask user for custom path
        print("\n🔐 Firebase Service Account Setup")
        print("Please provide your Firebase service account key file.")
        print("You can download it from: Firebase Console → Project Settings → Service Accounts → Generate New Private Key")
        
        custom_path = input("\nEnter path to your service account JSON file (or press Enter to search): ").strip()
        
        if custom_path and os.path.exists(custom_path):
            return custom_path
        
        # Search for common file names
        for path in possible_paths:
            if os.path.exists(path):
                confirm = input(f"Found service account file: {path}. Use this? (y/n): ").strip().lower()
                if confirm in ['y', 'yes']:
                    return path
        
        print("\n❌ Service account key file not found!")
        print("Please download your service account key from Firebase Console and place it in this directory.")
        return None
    
    def validate_email(self, email):
        """Validate email format"""
        pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        return re.match(pattern, email) is not None
    
    def validate_phone(self, phone):
        """Validate phone number format"""
        # Remove all non-digit characters
        digits_only = re.sub(r'\D', '', phone)
        # Check if it has 10-15 digits (international format)
        return len(digits_only) >= 10 and len(digits_only) <= 15
    
    def validate_pf_number(self, pf_number):
        """Validate PF number format and check for duplicates"""
        # Check format (alphanumeric, 3-20 characters)
        if not re.match(r'^[A-Z0-9]{3,20}$', pf_number.upper()):
            return False, "PF Number must be 3-20 alphanumeric characters"
        
        # Check for duplicates in Firestore
        try:
            existing = self.db.collection('employees').where('pfNumber', '==', pf_number.upper()).get()
            if len(existing) > 0:
                return False, "PF Number already exists in database"
        except Exception as e:
            print(f"⚠️  Warning: Could not check for duplicates: {e}")
        
        return True, "Valid"
    
    def get_employee_input(self):
        """Get employee information from user input"""
        print("\n" + "="*60)
        print("📝 EMPLOYEE REGISTRATION")
        print("="*60)
        
        employee_data = {}
        
        # Full Name
        while True:
            name = input("\n👤 Full Name: ").strip()
            if len(name) >= 2:
                employee_data['name'] = name
                break
            print("❌ Name must be at least 2 characters long")
        
        # PF Number
        while True:
            pf_number = input("\n🆔 PF Number (Employee ID): ").strip().upper()
            is_valid, message = self.validate_pf_number(pf_number)
            if is_valid:
                employee_data['pfNumber'] = pf_number
                break
            print(f"❌ {message}")
        
        # Email
        while True:
            email = input("\n📧 Email Address: ").strip().lower()
            if self.validate_email(email):
                # Check for duplicate email
                try:
                    existing = self.db.collection('employees').where('email', '==', email).get()
                    if len(existing) > 0:
                        print("❌ Email already exists in database")
                        continue
                except Exception as e:
                    print(f"⚠️  Warning: Could not check for duplicate email: {e}")
                
                employee_data['email'] = email
                break
            print("❌ Please enter a valid email address")
        
        # Phone Number
        while True:
            phone = input("\n📱 Phone Number: ").strip()
            if self.validate_phone(phone):
                # Clean and format phone number
                digits_only = re.sub(r'\D', '', phone)
                if not digits_only.startswith('254') and len(digits_only) == 10:
                    digits_only = '254' + digits_only  # Add Kenya country code
                employee_data['phone'] = '+' + digits_only
                break
            print("❌ Please enter a valid phone number (10-15 digits)")
        
        # Department
        print("\n🏢 Department Options:")
        departments = [
            "Human Resources", "Finance", "IT", "Operations", 
            "Marketing", "Sales", "Administration", "Other"
        ]
        for i, dept in enumerate(departments, 1):
            print(f"   {i}. {dept}")
        
        while True:
            choice = input("\nSelect department (1-8) or type custom: ").strip()
            if choice.isdigit() and 1 <= int(choice) <= 8:
                employee_data['department'] = departments[int(choice) - 1]
                break
            elif choice and not choice.isdigit():
                employee_data['department'] = choice.title()
                break
            print("❌ Please select a valid option or enter custom department")
        
        # Role
        print("\n👔 Role Options:")
        roles = ["Employee", "Supervisor", "Manager", "Admin"]
        for i, role in enumerate(roles, 1):
            print(f"   {i}. {role}")
        
        while True:
            choice = input("\nSelect role (1-4): ").strip()
            if choice.isdigit() and 1 <= int(choice) <= 4:
                employee_data['role'] = roles[int(choice) - 1]
                break
            print("❌ Please select a valid role (1-4)")
        
        # Salary (optional)
        salary_input = input("\n💰 Monthly Salary (optional, press Enter to skip): ").strip()
        if salary_input and salary_input.replace('.', '').isdigit():
            employee_data['salary'] = float(salary_input)
        
        # Additional fields with defaults
        employee_data['isActive'] = True
        employee_data['hasPassword'] = False
        employee_data['createdAt'] = firestore.SERVER_TIMESTAMP
        employee_data['registeredAt'] = None
        
        return employee_data
    
    def display_employee_summary(self, employee_data):
        """Display employee data for confirmation"""
        print("\n" + "="*60)
        print("📋 EMPLOYEE SUMMARY")
        print("="*60)
        print(f"👤 Name:         {employee_data['name']}")
        print(f"🆔 PF Number:    {employee_data['pfNumber']}")
        print(f"📧 Email:        {employee_data['email']}")
        print(f"📱 Phone:        {employee_data['phone']}")
        print(f"🏢 Department:   {employee_data['department']}")
        print(f"👔 Role:         {employee_data['role']}")
        if 'salary' in employee_data:
            print(f"💰 Salary:       KES {employee_data['salary']:,.2f}")
        print(f"✅ Status:       {'Active' if employee_data['isActive'] else 'Inactive'}")
        print("="*60)
    
    def save_employee(self, employee_data):
        """Save employee to Firestore"""
        try:
            doc_ref = self.db.collection('employees').add(employee_data)
            return True, doc_ref[1].id
        except Exception as e:
            return False, str(e)
    
    def add_employee(self):
        """Main method to add a single employee"""
        try:
            # Get employee input
            employee_data = self.get_employee_input()
            
            # Display summary for confirmation
            self.display_employee_summary(employee_data)
            
            # Confirm before saving
            confirm = input("\n✅ Save this employee to database? (y/n): ").strip().lower()
            if confirm not in ['y', 'yes']:
                print("❌ Employee registration cancelled")
                return False
            
            # Save to Firestore
            print("\n💾 Saving employee to database...")
            success, result = self.save_employee(employee_data)
            
            if success:
                print(f"✅ Employee successfully added to database!")
                print(f"📄 Document ID: {result}")
                print(f"🔐 The employee can now register using PF Number: {employee_data['pfNumber']}")
                return True
            else:
                print(f"❌ Failed to save employee: {result}")
                return False
                
        except KeyboardInterrupt:
            print("\n\n❌ Registration cancelled by user")
            return False
        except Exception as e:
            print(f"\n❌ Unexpected error: {e}")
            return False
    
    def bulk_add_employees(self):
        """Add multiple employees in batch"""
        print("\n📚 BULK EMPLOYEE REGISTRATION")
        print("="*60)
        
        employees = []
        count = 1
        
        while True:
            print(f"\n--- Adding Employee {count} ---")
            employee_data = self.get_employee_input()
            employees.append(employee_data)
            
            another = input("\n➕ Add another employee? (y/n): ").strip().lower()
            if another not in ['y', 'yes']:
                break
            count += 1
        
        # Show summary of all employees
        print(f"\n📋 BULK REGISTRATION SUMMARY ({len(employees)} employees)")
        print("="*60)
        for i, emp in enumerate(employees, 1):
            print(f"{i}. {emp['name']} (PF: {emp['pfNumber']}) - {emp['department']}")
        
        confirm = input(f"\n✅ Save all {len(employees)} employees to database? (y/n): ").strip().lower()
        if confirm not in ['y', 'yes']:
            print("❌ Bulk registration cancelled")
            return
        
        # Save all employees
        print(f"\n💾 Saving {len(employees)} employees to database...")
        success_count = 0
        failed_count = 0
        
        for i, employee_data in enumerate(employees, 1):
            print(f"Processing {i}/{len(employees)}: {employee_data['name']}...")
            success, result = self.save_employee(employee_data)
            if success:
                success_count += 1
                print(f"  ✅ Saved successfully")
            else:
                failed_count += 1
                print(f"  ❌ Failed: {result}")
        
        print(f"\n📊 BULK REGISTRATION COMPLETE")
        print(f"✅ Successfully added: {success_count}")
        print(f"❌ Failed: {failed_count}")
        print(f"📝 Total processed: {len(employees)}")
    
    def list_employees(self):
        """List all employees in the database"""
        try:
            print("\n👥 EMPLOYEE DATABASE")
            print("="*60)
            
            employees = self.db.collection('employees').order_by('name').get()
            
            if not employees:
                print("📭 No employees found in database")
                return
            
            print(f"📊 Total Employees: {len(employees)}")
            print("-"*60)
            
            for i, emp in enumerate(employees, 1):
                data = emp.to_dict()
                status = "🟢" if data.get('isActive', True) else "🔴"
                password_status = "✅" if data.get('hasPassword', False) else "❌"
                
                print(f"{i:2d}. {status} {data.get('name', 'N/A')}")
                print(f"     PF: {data.get('pfNumber', 'N/A')} | {data.get('department', 'N/A')} | {data.get('role', 'N/A')}")
                print(f"     📧 {data.get('email', 'N/A')} | 📱 {data.get('phone', 'N/A')}")
                print(f"     Password Set: {password_status}")
                print("-"*60)
                
        except Exception as e:
            print(f"❌ Error listing employees: {e}")
    
    def main_menu(self):
        """Display main menu and handle user choices"""
        if not self.db:
            print("❌ Firebase not initialized. Please check your service account key.")
            return
        
        while True:
            print("\n" + "="*60)
            print("🏢 EMPLOYEE MANAGEMENT SYSTEM")
            print("="*60)
            print("1. ➕ Add Single Employee")
            print("2. 📚 Bulk Add Employees")
            print("3. 👥 List All Employees")
            print("4. 🚪 Exit")
            print("="*60)
            
            choice = input("Select option (1-4): ").strip()
            
            if choice == '1':
                self.add_employee()
            elif choice == '2':
                self.bulk_add_employees()
            elif choice == '3':
                self.list_employees()
            elif choice == '4':
                print("\n👋 Goodbye!")
                break
            else:
                print("❌ Invalid option. Please select 1-4.")

def main():
    """Main function"""
    print("🚀 Starting Employee Registration System...")
    
    # Check Python version
    if sys.version_info < (3, 6):
        print("❌ Python 3.6 or higher is required")
        sys.exit(1)
    
    # Check if required packages are installed
    try:
        import firebase_admin
    except ImportError:
        print("❌ Firebase Admin SDK not installed")
        print("Install it with: pip install firebase-admin")
        sys.exit(1)
    
    # Initialize and run the application
    app = EmployeeRegistration()
    app.main_menu()

if __name__ == "__main__":
    main()