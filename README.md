# car_rental_system
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
