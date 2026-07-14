<div align="center">

# 🔐 Secure Document Management System

**A production-ready REST API for secure document storage, access control, and audit logging.**

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)
![JWT](https://img.shields.io/badge/Auth-JWT-black?style=flat-square&logo=jsonwebtokens)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)

</div>

---

## 📋 Overview

Secure DMS is a backend REST API for managing documents with fine-grained access control. Users can upload files, share them with specific permissions, and every action is tracked in an immutable audit log.

Built with security as a first-class citizen — not bolted on afterward.

---

## ✨ Features

| Feature | Details |
|---|---|
| **JWT Authentication** | Stateless, HS384-signed tokens, 24h expiry |
| **File Upload & Download** | Multipart upload, UUID-based storage (no path traversal) |
| **Permission System** | Per-document READ / WRITE / DELETE grants |
| **Audit Logging** | Every action logged asynchronously with IP + timestamp |
| **Rate Limiting** | 10 req/min on login, 100 req/min on API (per IP, Bucket4j) |
| **Password Security** | BCrypt with cost factor 12 |
| **SQL Injection Prevention** | JPA + parameterized queries throughout |
| **DB Migrations** | Flyway versioned migrations |

---

## 🛠️ Tech Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 3.2.5 (Web, Security, Data JPA, Validation)
- **Database:** PostgreSQL 16
- **Auth:** jjwt 0.12.5
- **Rate Limiting:** Bucket4j 8.10.1
- **Migrations:** Flyway
- **Build:** Maven

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Docker & Docker Compose
- Maven (or use IntelliJ's built-in)

### 1. Clone the repo

```bash
git clone https://github.com/KungFuNici/secure-dms.git
cd secure-dms
```

### 2. Start PostgreSQL

```bash
docker-compose up -d
```

### 3. Run the app

```bash
mvn spring-boot:run
```

The API is now live at `http://localhost:8080`.

> **Note:** Uploaded files are stored in `./uploads/` by default. Override with the `UPLOAD_DIR` environment variable.

---

## 📡 API Reference

### Auth

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "niclas",
  "email": "kungfunici@users.noreply.github.com",
  "password": "supersecret"
}
```

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "niclas",
  "password": "supersecret"
}
```

Both return:
```json
{
  "token": "eyJ...",
  "tokenType": "Bearer",
  "username": "niclas",
  "role": "ROLE_USER"
}
```

---

### Documents

All document endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload a file (multipart) |
| `GET` | `/api/documents` | List your documents (paginated) |
| `GET` | `/api/documents/search?q=term` | Search by filename or description |
| `GET` | `/api/documents/{id}/download` | Download a file |
| `DELETE` | `/api/documents/{id}` | Delete a file (owner only) |

**Upload example:**
```http
POST /api/documents/upload
Authorization: Bearer eyJ...
Content-Type: multipart/form-data

file=@report.pdf
description=Q2 Financial Report
```

---

### Permissions

Share documents with other users.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/{id}/permissions` | Grant permission |
| `GET` | `/api/documents/{id}/permissions` | List permissions (owner only) |
| `DELETE` | `/api/documents/{id}/permissions/{username}` | Revoke permission |

**Grant example:**
```http
POST /api/documents/1/permissions
Authorization: Bearer eyJ...
Content-Type: application/json

{
  "username": "other-user",
  "permissionType": "READ"
}
```

Permission types: `READ`, `WRITE`, `DELETE`

---

## 🗄️ Database Schema

```
users
 └── documents (owner_id → users.id)
      └── document_permissions (document_id, user_id)
audit_logs (user_id → users.id, soft ref to document_id)
```

---

## 🔒 Security Notes

- Passwords are hashed with **BCrypt (cost 12)** — never stored in plain text
- JWT secret is configurable via `JWT_SECRET` environment variable — change this in production
- File storage uses **UUID-based filenames** — original filenames are stored in the DB only, preventing path traversal attacks
- Rate limiting is applied **before** JWT validation — unauthenticated brute-force attempts are blocked
- Error responses are deliberately generic on auth failures to prevent username enumeration

---

## ⚙️ Configuration

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `changeme-...` | JWT signing secret (min. 32 chars) |
| `UPLOAD_DIR` | `./uploads` | File storage directory |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/secure_dms` | DB connection |

---

## 📁 Project Structure

```
src/main/java/dev/securecdms/
├── controller/        # REST endpoints
├── service/           # Business logic
├── repository/        # JPA repositories
├── model/             # JPA entities
├── dto/               # Request & response objects
├── security/          # JWT utils, filters
├── config/            # Spring Security config
└── exception/         # Global error handling
```

---

## 🧭 Roadmap

- [ ] React frontend
- [ ] Refresh token support
- [ ] File type validation & virus scanning
- [ ] Admin dashboard (audit log viewer)
- [ ] Docker image & CI/CD pipeline

---

<div align="center">

Built with ❤️ by [KungFuNici](https://github.com/KungFuNici)

</div>