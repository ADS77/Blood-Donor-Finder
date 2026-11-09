# 🩸 Blood Donor Finder

A RESTful backend API built with **Spring Boot** to streamline the process of finding **nearby blood donors** and handling **urgent blood requests**. The system supports geoLocation-based search, user roles (admin/donor/receiver), and real-time SMS notifications to available donors.

---

### 🔧 Prerequisites

Make sure you have the following installed:

- Java 17+
- Maven (`mvn -v`)
- Docker & Docker Compose (`docker -v`, `docker-compose -v`)
### 📦 Step 1: Build the Spring Boot Application

```bash
./mvnw clean package
```
### 🐳  Step 2: Start required tools with Docker
```bash
cd Docker
docker-compose up -d
```
### ▶️  Step 3: Deploy Application locally(WIndows)
```bash
./mvnw spring-boot:run -DskipTests
```
### ▶️  Step 3: Deploy Application locally(Ubuntu)
```bash
./mvn spring-boot:run -DskipTests
```
OR
### ▶️ Deploy Application with jar
```bash
java -jar target/blood-doner-finder-0.0.1-SNAPSHOT.jar

```
## 🚀 Features

- 🔐 User Authentication & Role-based Access (Admin / Donor / Receiver)
- 📍 Geo-based Search for Nearby Donors (Haversine formula)
- 🩸 Blood Group Matching
- 📲 SMS Notifications to Available Donors (Twilio integration)
- 📦 RESTful API endpoints for all core operations
- 🧠 Modular and Extensible Service Architecture

---

## 🏗️ Tech Stack

| Layer      | Technology     |
|-----------|----------------|
| Backend   | Spring Boot    |
| Database  | PostgreSQL     |
| Messaging | Twilio SMS API |
| Build     | Maven          |
| Versioning| Git            |

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

---

## 📑 API Documentation

Once the application is running, you can explore and test all available endpoints via **Swagger UI**, which is automatically generated from  REST controllers.

🔗 **Access Swagger UI**:
--
http://localhost:8080/swagger-ui/index.html
### Clone the repo 
git clone https://github.com/ADS77/Blood-Donor-Finder
