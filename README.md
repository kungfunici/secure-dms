<div align="center">

# 🔐 Secure Document Management System

**Full-stack document storage with granular permissions, rich text editing, full-text search, audit logging, and a React 19 frontend.**

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)
![JWT](https://img.shields.io/badge/Auth-JWT-black?style=flat-square&logo=jsonwebtokens)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![LibreOffice](https://img.shields.io/badge/LibreOffice-Converter-18A303?style=flat-square&logo=libreoffice)

</div>

---

## Features

- **JWT Auth** – HS384-signed tokens, 24h expiry, refresh tokens, password reset
- **File Upload/Download** – Multipart, UUID-based storage, path traversal prevention
- **Rich Text Editor** – Tiptap-based editor with save/auto-save/preview mode for Word, ODT, RTF, CSV, and text files
- **Document Preview** – Inline preview for images, PDFs, and rendered Office documents (via LibreOffice)
- **Full-Text Search** – Search across filenames, descriptions, and extracted text content
- **Tags** – Create/manage tags, add/remove on documents, filter by tag
- **Favorites** – Star documents for quick access
- **Sorting** – Sort by name, date, size, or type
- **Trash/Recycle Bin** – Soft delete with restore, search within trash
- **Permissions** – Per-document READ/WRITE grants by user ID
- **Folders** – Organize documents into folders
- **Shared views** – "Shared with me" and "Shared by me" tabs
- **Versioning** – Keep track of document edits over time
- **Batch Operations** – Batch download, delete, and permission changes
- **User Profiles** – Avatar upload, account deletion, password change
- **Admin Dashboard** – User management, system stats, configuration
- **Audit Logging** – Every action logged asynchronously
- **Rate Limiting** – 10 req/min login, 100 req/min API (Bucket4j, per IP)
- **E2EE Support** – End-to-end encryption key management
- **Webhooks** – Event-driven outbound webhooks
- **Retention Policies & Legal Hold** – Compliance-focused document lifecycle
- **Notifications** – In-app notification system
- **Password Security** – BCrypt cost factor 12
- **DB Migrations** – Flyway versioned (13 migrations)
- **Frontend** – React 19, TypeScript, Vite, shadcn/ui, Tailwind 4, Tiptap, react-router-dom

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
| Document Conversion | LibreOffice (Docker sidecar or CLI) |
| File Storage | Local filesystem or S3-compatible (MinIO) |
| Frontend | React 19, TypeScript, Vite, Tailwind 4, shadcn/ui, Tiptap 3.28 |
| Build | Maven (Maven Wrapper included) |

---

## Getting Started

```bash
# 1. Clone
git clone https://github.com/KungFuNici/secure-dms.git
cd secure-dms

# 2. Start PostgreSQL + LibreOffice sidecar
docker compose up -d

# 3. Run backend
./mvnw spring-boot:run

# 4. Run frontend (separate terminal)
cd frontend
npm install
npm run dev
```

Backend: `http://localhost:8080` | Frontend: `http://localhost:5173`

> **Note:** Docker PostgreSQL runs on port **5433** to avoid conflicts with native PostgreSQL installs.  
> **Note:** LibreOffice sidecar runs on port **3001** (used for document conversion to/from HTML).  
> **Note:** Default upload dir is `./uploads/`. Override with `UPLOAD_DIR` env var.

---

## API Overview

All document/user endpoints require `Authorization: Bearer <token>`.

### Auth

| Method | Endpoint | Body |
|---|---|---|
| `POST` | `/api/auth/register` | `{ username, email, password }` |
| `POST` | `/api/auth/login` | `{ username, password }` |
| `POST` | `/api/auth/refresh` | `{ refreshToken }` |
| `POST` | `/api/auth/change-password` | `{ currentPassword, newPassword }` |
| `POST` | `/api/auth/forgot-password` | `{ email }` |
| `POST` | `/api/auth/reset-password` | `{ token, newPassword }` |

### Documents

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload (multipart: `file`, `description`, `folderId?`) |
| `GET` | `/api/documents` | My documents (paginated, sortable, filterable by tag/favorite/type) |
| `GET` | `/api/documents/search?q=` | Search owned + shared (filename, description, extracted text) |
| `GET` | `/api/documents/shared-with-me?q=` | Documents shared with me (optional search) |
| `GET` | `/api/documents/shared-by-me?q=` | My documents I've shared (optional search) |
| `GET` | `/api/documents/trash?q=` | Trashed documents (optional search) |
| `GET` | `/api/documents/{id}` | Document details |
| `GET` | `/api/documents/{id}/download` | Download file |
| `GET` | `/api/documents/{id}/render` | Render file as HTML (for editor/preview) |
| `PUT` | `/api/documents/{id}/save-rendered` | Save edited HTML back to document |
| `GET` | `/api/documents/{id}/preview-blob` | Preview blob (for images/PDFs) |
| `GET` | `/api/documents/{id}/versions` | List versions |
| `PUT` | `/api/documents/{id}` | Update file/description (multipart) |
| `PATCH` | `/api/documents/{id}/move?folderId=` | Move to folder (omit to remove from folder) |
| `PATCH` | `/api/documents/{id}/favorite` | Toggle favorite |
| `PATCH` | `/api/documents/{id}/restore` | Restore from trash |
| `DELETE` | `/api/documents/{id}` | Soft delete (move to trash) |
| `DELETE` | `/api/documents/{id}/permanent` | Permanently delete |

### Tags

| Method | Endpoint | Body |
|---|---|---|
| `GET` | `/api/tags` | List all my tags |
| `POST` | `/api/tags` | `{ name, color? }` |
| `PUT` | `/api/tags/{id}` | `{ name, color? }` |
| `DELETE` | `/api/tags/{id}` | Delete tag (removed from all documents) |
| `POST` | `/api/documents/{id}/tags` | `{ tagId }` — add tag to document |
| `DELETE` | `/api/documents/{id}/tags/{tagId}` | Remove tag from document |

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
| `GET` | `/api/users/me` | Current user profile |
| `GET` | `/api/users/{id}` | Profile |
| `GET` | `/api/users/{id}/avatar` | Avatar image (returns 404 if none) |
| `PUT` | `/api/users/{id}` | Update avatar (multipart: `avatar`) |
| `DELETE` | `/api/users/{id}` | Delete account (clears all data) |
| `GET` | `/api/users/search?q=` | Search users by username |

### Admin

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/stats` | System statistics |
| `GET` | `/api/admin/users` | List all users |
| `PATCH` | `/api/admin/users/{id}` | Update user role/status |
| `DELETE` | `/api/admin/users/{id}` | Delete user |
| `GET` | `/api/admin/config` | System configuration |
| `PUT` | `/api/admin/config` | Update configuration |

### Notifications

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/notifications` | List my notifications |
| `PATCH` | `/api/notifications/{id}/read` | Mark as read |
| `POST` | `/api/notifications/read-all` | Mark all as read |

### Webhooks

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/webhooks` | List webhooks |
| `POST` | `/api/webhooks` | Create webhook |
| `DELETE` | `/api/webhooks/{id}` | Delete webhook |

### Keys (E2EE)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/keys` | List keys |
| `POST` | `/api/keys` | Store key |
| `DELETE` | `/api/keys/{id}` | Delete key |

---

## Database Schema

```
users (id, username, email, password_hash, role, profile_picture, enabled, created_at, updated_at)
 ├── documents (id, owner_id → users, folder_id → folders, original_filename, stored_filename, ...)
 │    ├── document_permissions (id, document_id → documents, user_id → users, permission_type, granted_at)
 │    └── document_tags (document_id → documents, tag_id → tags)
 ├── tags (id, name, color, owner_id → users)
 ├── folders (id, name, owner_id → users, created_at, updated_at)
 ├── audit_logs (id, user_id → users, document_id?, action, details, ip_address, timestamp)
 ├── notifications (id, user_id → users, message, type, read, created_at)
 ├── document_keys (id, document_id → documents, user_id → users, encrypted_key)
 ├── user_keys (id, user_id → users, public_key, created_at)
 ├── retention_policies (id, name, duration_days, action, enabled)
 ├── legal_holds (id, name, document_id → documents, user_id → users, active, created_at)
 ├── webhooks (id, name, url, secret, events, enabled, owner_id → users)
 ├── password_reset_tokens (id, user_id → users, token, expires_at)
 └── system_config (id, key, value)
```

---

## Project Structure

```
├── src/main/java/dev/securecdms/
│   ├── controller/          # REST endpoints (Auth, Document, Folder, Tag, Permission, User, Admin, Key, Webhook)
│   ├── service/             # Business logic (Document, Auth, Folder, Storage, Conversion, Crypto, Admin, Webhook, Retention, S3, Notification)
│   ├── repository/          # JPA repositories
│   ├── model/               # JPA entities (Document, User, Tag, Folder, Permission, Key, Webhook, etc.)
│   ├── dto/                 # Request & response DTOs
│   ├── security/            # JWT utils, filters, rate limiter
│   ├── config/              # Spring Security, CORS, CSP
│   └── exception/           # Global error handler
├── src/main/resources/db/migration/  # Flyway SQL migrations (V1–V13)
├── frontend/
│   ├── src/
│   │   ├── pages/           # LoginPage, Dashboard, ProfilePage, AdminDashboard
│   │   ├── components/      # RichTextEditor, TagManager, shadcn/ui components
│   │   ├── contexts/        # AuthContext
│   │   └── lib/             # api.ts (all HTTP calls)
│   └── Dockerfile           # Frontend container build
├── libreoffice-sidecar/     # LibreOffice Docker sidecar for document conversion
├── scripts/                 # Backup and restore scripts
├── docker-compose.yml       # Local development stack (PostgreSQL, LibreOffice)
└── docker-compose.prod.yml  # Production stack (with Caddy, MinIO, DocuSeal)
```

---

## Configuration

### Environment Variables (`cp .env.example .env`)

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `changeme-...` | JWT signing secret (min 32 chars) |
| `UPLOAD_DIR` | `./uploads` | File storage directory |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/secure_dms` | DB (port 5433) |
| `SPRING_PROFILES_ACTIVE` | — | Set to `s3` for S3/MinIO storage |
| `S3_BUCKET` | `secure-dms` | S3 bucket name |

### Application Profiles

| Profile | Description |
|---|---|
| `default` | Local filesystem storage, PostgreSQL on 5433 |
| `s3` | S3-compatible storage (MinIO), requires MinIO container |

---

## Running Tests

```bash
./mvnw test
```

Uses H2 in-memory database with `@ActiveProfiles("test")` (Flyway disabled). Currently **233 tests** across controllers, services, and integration.

---

## Deployment

### Production

```bash
docker compose -f docker-compose.prod.yml up -d
```

Includes:
- **Caddy** — Reverse proxy with automatic TLS
- **Backend** — Spring Boot application
- **Frontend** — Static files served by Nginx
- **PostgreSQL** — Database
- **MinIO** — S3-compatible object storage
- **LibreOffice** — Sidecar for document conversion
- **DocuSeal** — E-Signature integration (optional)

© 2026 Niclas Luca Koch — All Rights Reserved
