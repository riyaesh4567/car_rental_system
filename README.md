# 🚗 SRV FleetOS - Enterprise Car Rental & Fleet Management System

A robust full-stack web application for automated vehicle rental operations, fleet inventory control, driver KYC verification, and dynamic billing. Built using high-performance Core Java HTTP microservices, Vanilla JavaScript, Tailwind CSS, and MySQL.

---

## 🌟 Key Features

### 👤 Role-Based Portals (RBAC)
- **Customer Portal:** Browse real-time fleet availability, filter vehicle types, apply for bookings with KYC details, complete simulated checkouts, and print invoice receipts.
- **Admin Control Center:** Full CRUD operations over fleet inventory (Add/Update/Delete cars), approve or reject booking requests, monitor active trips, and access business analytics.

### 🔄 Multi-Stage Rental & Verification Lifecycle
1. **Booking & Verification Application:** Customers select pickup/return dates, enter driving license numbers, age (18+ validation), and choose handover preference (Self-Pickup Hub or Doorstep Delivery).
2. **Admin Verification & Screening:** Requests enter `PENDING_APPROVAL`. Admins review driver credentials and either `APPROVE` or `REJECT` the booking.
3. **Simulated Payment Gateway:** Once approved, customer unlocks instant checkout (UPI, Cards, NetBanking). Total pricing is dynamically computed based on day-duration.
4. **Automated Notification Dispatch:** On successful payment, the backend service logs and dispatches booking receipts, payment IDs, and dispatch coordinates.
5. **Atomic Return & Fleet Sync:** Single-click return workflow restoring car availability instantly using ACID-compliant SQL transactions.

### 📊 Business Intelligence & Reporting
- Real-time stat cards tracking total bookings, gross fleet revenue, and live vehicles on road.
- Export bookings data to `.CSV` format for auditing.
- Integrated customer review and rating showcase.
- Instant browser-printable tax invoices.

---

## 🛠️ Tech Stack

- **Frontend:** HTML5, Tailwind CSS (CDN), Vanilla JavaScript (ES6+), Lucide Icons
- **Backend:** Java 17+ (`com.sun.net.httpserver.HttpServer`), RESTful JSON Endpoints
- **Database:** MySQL 8.x, JDBC (`mysql-connector-j`)
- **Architecture:** Client-Server Monolith with REST architecture and CORS support

---

## 🗄️ Database Architecture

The system operates on three primary relational tables within the `car_rental_db` schema:

- **`customers`:** Stores user profiles, authentication credentials, and access roles (`ADMIN`, `CUSTOMER`).
- **`cars`:** Tracks fleet units, model names, rates per day, image URLs, and real-time availability flags.
- **`rentals`:** Maintains booking records, duration, total costs, driver KYC details, delivery addresses, transaction IDs, and lifecycle states (`PENDING_APPROVAL`, `APPROVED`, `PAID_CONFIRMED`, `RETURNED`, `REJECTED`).

---

## 🚀 Getting Started

### 1. Prerequisites
- Java Development Kit (JDK 17 or higher)
- MySQL Server 8.x
- MySQL Connector JAR (`mysql-connector-j-8.4.0.jar`) in project root

### 2. Database Setup
Log into your MySQL instance and initialize the schema:

```sql
CREATE DATABASE IF NOT EXISTS car_rental_db;
USE car_rental_db;

CREATE TABLE IF NOT EXISTS customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'CUSTOMER'
);

CREATE TABLE IF NOT EXISTS cars (
    car_id VARCHAR(20) PRIMARY KEY,
    model VARCHAR(100) NOT NULL,
    price_per_day DECIMAL(10,2) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    image TEXT
);

CREATE TABLE IF NOT EXISTS rentals (
    rental_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(50),
    car_id VARCHAR(20),
    days INT NOT NULL,
    total_cost DECIMAL(10,2) NOT NULL,
    active BOOLEAN DEFAULT FALSE,
    start_date DATE,
    end_date DATE,
    age INT,
    license_no VARCHAR(50),
    delivery_type VARCHAR(20) DEFAULT 'SELF_PICKUP',
    delivery_address TEXT,
    status VARCHAR(30) DEFAULT 'PENDING_APPROVAL',
    payment_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE SET NULL,
    FOREIGN KEY (car_id) REFERENCES cars(car_id) ON DELETE SET NULL
);


# Compile the Java Server
javac -cp ".;mysql-connector-j-8.4.0.jar" FullAppServer.java

# Start the application
java -cp ".;mysql-connector-j-8.4.0.jar" FullAppServer
