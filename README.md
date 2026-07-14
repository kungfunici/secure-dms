<div align="center">

# 🔐 Secure Document Management System

**Full-stack document storage with granular permissions, audit logging, and a React frontend.**

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)
![JWT](https://img.shields.io/badge/Auth-JWT-black?style=flat-square&logo=jsonwebtokens)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)

</div>

---

## Features

- **JWT Auth** – HS384-signed tokens, 24h expiry, refresh tokens
- **File Upload/Download** – Multipart, UUID-based storage, path traversal prevention
- **Permissions** – Per-document READ/WRITE grants by user ID
- **Folders** – Organize documents into folders
- **Shared views** – "Shared with me" and "Shared by me" tabs
- **User Profiles** – Avatar upload, account deletion
- **Audit Logging** – Every action logged asynchronously
- **Rate Limiting** – 10 req/min login, 100 req/min API (Bucket4j, per IP)
- **Password Security** – BCrypt cost factor 12
- **DB Migrations** – Flyway versioned
- **Frontend** – React 19, TypeScript, shadcn/ui, Tailwind 4, react-router-dom

---

## Tech Stack

| Layer | Tech |
|---|---|
| Runtime | Java 21 |
| Backend | Spring Boot 3.2.5 (Web, Security, Data JPA, Validation) |
| Database | PostgreSQL 16 (Docker) |
| Auth | jjwt 0.12.5 |
| Rate Limiting | Bucket4j 8.10.1 |
| Migrations | Flyway |
| Frontend | React 19, TypeScript, Vite 8, Tailwind 4, shadcn/ui |
| Build | Maven (Maven Wrapper included) |

---

## Getting Started

```bash
# 1. Clone
git clone https://github.com/KungFuNici/secure-dms.git
cd secure-dms

# 2. Start PostgreSQL
docker compose up -d

# 3. Run backend
./mvnw spring-boot:run

# 4. Run frontend (separate terminal)
cd frontend
npm install
npm run dev
```

Backend: `http://localhost:8080` | Frontend: `http://localhost:5173`

> **Note:** Default upload dir is `./uploads/`. Override with `UPLOAD_DIR` env var.  
> **Note:** Docker PostgreSQL runs on port **5433** to avoid conflicts with native PostgreSQL installs.

---

## API Overview

All document/user endpoints require `Authorization: Bearer <token>`.

### Auth

| Method | Endpoint | Body |
|---|---|---|
| `POST` | `/api/auth/register` | `{ username, email, password }` |
| `POST` | `/api/auth/login` | `{ username, password }` |
| `POST` | `/api/auth/refresh` | `{ refreshToken }` |

### Documents

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload (multipart: `file`, `description`, `folderId?`) |
| `GET` | `/api/documents` | My documents (paginated) |
| `GET` | `/api/documents/search?q=` | Search owned + shared |
| `GET` | `/api/documents/shared-with-me` | Documents shared with me |
| `GET` | `/api/documents/shared-by-me` | My documents I've shared |
| `GET` | `/api/documents/{id}/download` | Download file |
| `PUT` | `/api/documents/{id}` | Update file/description (multipart) |
| `PATCH` | `/api/documents/{id}/move?folderId=` | Move to folder (omit to remove from folder) |
| `DELETE` | `/api/documents/{id}` | Owner: delete permanently; non-owner: remove own access |

### Permissions

| Method | Endpoint | Body |
|---|---|---|
| `GET` | `/api/documents/{id}/permissions` | List (owner only) |
| `POST` | `/api/documents/{id}/permissions` | `{ userId, permissionType: "READ"\|"WRITE" }` |
| `DELETE` | `/api/documents/{id}/permissions/{userId}` | Revoke |

### Folders

| Method | Endpoint | Body |
|---|---|---|
| `GET` | `/api/folders` | List my folders |
| `POST` | `/api/folders` | `{ name }` |
| `PUT` | `/api/folders/{id}` | Rename: `{ name }` |
| `DELETE` | `/api/folders/{id}` | Delete (documents get folder_id = null) |

### Users

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users/{id}` | Profile |
| `GET` | `/api/users/{id}/avatar` | Avatar image (returns 404 if none) |
| `PUT` | `/api/users/{id}` | Update avatar (multipart: `avatar`) |
| `DELETE` | `/api/users/{id}` | Delete account (clears all data) |
| `GET` | `/api/users/search?q=` | Search users by username |

### Example: Grant permission

```http
POST /api/documents/1/permissions
Authorization: Bearer eyJ...
Content-Type: application/json

{ "userId": 2, "permissionType": "WRITE" }
```

Response includes `permission` field (`OWNER`/`WRITE`/`READ`) in every document object.

---

## Database Schema

```
users (id, username, email, password_hash, role, profile_picture, enabled, created_at, updated_at)
 └── documents (id, owner_id → users, folder_id → folders, original_filename, stored_filename, ...)
 │    └── document_permissions (id, document_id → documents, user_id → users, permission_type, granted_at)
 ├── folders (id, name, owner_id → users, created_at, updated_at)
 └── audit_logs (id, user_id → users, document_id?, action, details, ip_address, timestamp)
```

---

## Project Structure

```
├── src/main/java/dev/securecdms/
│   ├── controller/        # REST endpoints (Auth, Document, Folder, Permission, User)
│   ├── service/           # Business logic
│   ├── repository/        # JPA repositories
│   ├── model/             # JPA entities
│   ├── dto/               # Request & response DTOs
│   ├── security/          # JWT utils, filters, rate limiter
│   ├── config/            # Spring Security, CORS, CSP
│   └── exception/         # Global error handler
├── src/main/resources/db/migration/  # Flyway SQL migrations
├── frontend/
│   └── src/
│       ├── pages/         # LoginPage, Dashboard, ProfilePage
│       ├── contexts/      # AuthContext
│       ├── lib/           # api.ts (all HTTP calls)
│       └── components/    # shadcn/ui components
└── docker-compose.yml
```

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `changeme-...` | JWT signing secret (min 32 chars) |
| `UPLOAD_DIR` | `./uploads` | File storage directory |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/secure_dms` | DB (port 5433) |

---

## Running Tests

```bash
./mvnw test
```

Uses H2 in-memory database with `@ActiveProfiles("test")` (Flyway disabled). Currently **68 tests** across controllers, services, and integration.

© 2026 Niclas Luca Koch — All Rights Reserved

