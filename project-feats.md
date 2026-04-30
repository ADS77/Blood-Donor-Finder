# 🩸 Blood Donor Finder - Project Features

**Blood Donor Finder** is a production-ready RESTful backend API that connects blood donors with recipients in emergencies. Every feature listed here is fully implemented and backed by working code.

---

## ✅ Implemented Features

### 1. 🔐 User Management & Role-Based Access
- **Registration**: Donors and receivers can register with name, email, phone, blood group, and geo-location via `POST /api/user/register`.
- **Profile Update**: Users can update their availability, location, blood group, and last donation date via `POST /api/user/update`.
- **Role System**: Three distinct roles — `ADMIN`, `DONOR`, `RECEIVER` — are enforced at the data layer.

---

### 2. 📍 Proximity-Based Donor Discovery
- **Geo-Location Search**: Finds nearby donors using latitude & longitude coordinates.
- **Haversine Formula**: Calculates real earth-surface distances between the requester and each donor to ensure accuracy.
- **Blood Group Filtering**: Only donors with a matching blood group are fetched from the database.
- **Radius-Based Results**: Defaults to a 10 km radius if not specified by the caller.
- **Endpoint**: `GET /api/search/notify-near-by-donors`

---

### 3. 🩺 Donation Eligibility & History Tracking *(new)*
- **120-Day Eligibility Rule**: Automatically skips donors who donated within the last **120 days** during every search — enforcing safe donation intervals without any manual check.
- **Smart Search Integration**: Eligibility filtering runs inside `DonorSearchServiceImpl` before distance calculation, keeping ineligible donors completely out of results.
- **Record Donation**: Logs a completed donation (donor, optional recipient, optional blood request, notes) and instantly updates the donor's `lastDonationDate`.
- **Donation History**: Retrieves a donor's full donation log, sorted from most recent to oldest.
- **Endpoints**:
  - `POST /api/donations/record`
  - `GET  /api/donations/history/{donorId}`
  - `GET  /api/donations/eligible/{donorId}`

---

### 4. 📧 Email Notification Pipeline
- **Automated Email Alerts**: Matched donors automatically receive a rich HTML email containing the requester's location, phone, and hospital name.
- **Email Validation**: Only donors with valid email addresses are notified — invalid addresses are logged and skipped.
- **Spring Mail Integration**: Backed by `spring-boot-starter-mail` for reliable SMTP delivery.
- **Endpoint**: `POST /api/mail/send`

---

### 5. ⚡ Asynchronous Notification via Apache Kafka
- **Event-Driven Architecture**: After finding eligible donors, `DonorListPublisher` serialises them into a Kafka message and publishes to a dedicated topic.
- **Decoupled Consumer**: `DonorListConsumer` listens on the topic and dispatches notifications in a separate thread pool (`ExecutorService` with 10 threads), keeping the search API fast and non-blocking.
- **Rich Message Headers**: Each Kafka message carries `X-Notification-ID`, `X-Emergency-Level`, `X-Blood-Group`, and `X-Donor-Count` headers for traceability.
- **Acknowledgment Support**: The consumer uses manual acknowledgment for reliable message processing.

---

### 6. 🔍 Elasticsearch-Powered User Search
- **Index & Store**: User profiles are indexed into Elasticsearch via `POST /es/users/save`.
- **Blood Group Search**: Query donors by blood group at high speed via `GET /es/users/search_by_bloodgroup`.
- **Paginated Full-Text Search**: Advanced multi-field search with pagination support via `POST /es/users/search-user`.
- **Dedicated Document Model**: `UserSearchDocument` is a separate Elasticsearch document (decoupled from the JPA `User` entity).

---

### 7. 🩸 Blood Request Management
- **Create Request**: Receivers submit a blood request with needed blood group, location, quantity, and an optional message via `POST /api/blood-request/create`.
- **Request Tracking**: Each request is timestamped and stored with a `PENDING` status by default.
- **Linked to Users**: Every request is linked to the requester's user record.

---

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend** | Spring Boot 3.4.4 | Core application framework |
| **Database** | PostgreSQL 15 | Persistent relational data storage |
| **Messaging** | Apache Kafka (Confluent 7.5) | Async, event-driven notification pipeline |
| **Search Engine** | Elasticsearch 8.11 | High-speed donor indexing and search |
| **ORM** | Spring Data JPA | Simplified database access layer |
| **Email** | Spring Boot Mail (SMTP) | HTML email delivery to donors |
| **API Docs** | SpringDoc OpenAPI 2.5 | Auto-generated Swagger UI |
| **Containerization** | Docker & Docker Compose | One-command infra setup |
| **Build Tool** | Maven Wrapper | Reproducible builds without a local Maven install |
| **Utilities** | Lombok, Jackson | Boilerplate reduction and JSON serialization |

---

## 📑 API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/user/register` | Register a new user |
| `POST` | `/api/user/update` | Update user profile |
| `POST` | `/api/blood-request/create` | Create a blood request |
| `GET` | `/api/search/notify-near-by-donors` | Find & notify nearby eligible donors |
| `POST` | `/api/donations/record` | Record a completed donation |
| `GET` | `/api/donations/history/{donorId}` | Get a donor's full donation history |
| `GET` | `/api/donations/eligible/{donorId}` | Check if a donor is eligible to donate |
| `POST` | `/api/mail/send` | Send a notification email manually |
| `POST` | `/es/users/save` | Index a user into Elasticsearch |
| `GET` | `/es/users/search_by_bloodgroup` | Search donors by blood group (ES) |
| `POST` | `/es/users/search-user` | Paginated full-text search (ES) |

> 🔗 Full interactive docs: **http://localhost:8080/swagger-ui/index.html**
