# 🩸 Blood Donor Finder

A RESTful backend API built with **Spring Boot** to streamline the process of finding **nearby blood donors** and handling **urgent blood requests**. The system supports geoLocation-based search, user roles (admin/donor/receiver), and real-time SMS notifications to available donors.

---

### 🔧 Prerequisites

Make sure you have the following installed:

- Java 17+
- Maven (`mvn -v`)
- Docker & Docker Compose (`docker -v`, `docker-compose -v`)

### 🔑 Step 0: Configure local secrets

The application reads secrets from environment variables. For local
development, create a git-ignored `.env` file from the template:

```bash
cp .env.example .env
```

Then set at least:

- `DB_PASSWORD` — password for the local PostgreSQL `bduser`. The Docker stack
  in `Docker/docker-compose.yml` uses the same `DB_USERNAME` and `DB_PASSWORD`
  values from `.env`, so no separate Postgres password needs to be exported.
- `PII_ENCRYPTION_KEY`, `PII_HASH_KEY`, `OTP_HASH_KEY` — generate strong random
  values with `openssl rand -base64 48`.
- `MAIL_USERNAME`, `MAIL_PASSWORD` — SMTP credentials, if email is needed.

`application.properties` imports `.env` automatically. There is intentionally no
default database password, so startup fails fast instead of silently connecting
with a development credential. Never commit `.env`.

### 📦 Step 1: Build the Spring Boot Application

```bash
./mvnw clean package
```
### 🐳  Step 2: Start required tools with Docker
```bash
cd Docker
docker compose --env-file ../.env up -d
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
