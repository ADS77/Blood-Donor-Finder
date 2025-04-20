# 🩸 Blood Donation System - Spring Boot Backend

A RESTful backend API built with **Spring Boot** to streamline the process of finding **nearby blood donors** and handling **urgent blood requests**. The system supports location-based search, user roles (admin/donor/receiver), and real-time SMS notifications to available donors.

---

## 🚀 Features

- 🔐 User Authentication & Role-based Access (Admin / Donor / Receiver)
- 📍 Geo-based Search for Nearby Donors (Haversine formula)
- 🩸 Blood Group Matching
- 📲 SMS Notifications to Available Donors (Twilio integration)
- 📦 RESTful API endpoints for all core operations
- 🧠 Modular and Extensible Service Architecture

---

## 🏗️ Tech Stack

| Layer      | Technology |
|-----------|------|
| Backend   | Spring Boot |
| Database  | MySQL|
| Messaging | Twilio SMS API |
| Build     | Maven  |
| Versioning| Git  |

---
<!--

## 🧱 Core Entities

- **User**
    - Name, Email, Phone
    - Role: `ADMIN`, `DONOR`, `RECEIVER`
    - Blood Group
    - Availability & Verification Flags
    - Location (Lat, Lon, City)

- **Location**
    - Address, City, Latitude, Longitude

- **BloodRequest**
    - Requested Blood Group
    - Requester (linked to User)
    - Request Time
    - Location

---
-->

## 📡 Key APIs

### 📝 Register User
### 🔍 Donor Search
