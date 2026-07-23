#!/bin/bash
set -euo pipefail

# =============================================================================
# Secure DMS — Backup Script
# Creates a timestamped backup of PostgreSQL database and MinIO/S3 data.
# Usage: ./scripts/backup.sh [output-dir]
# =============================================================================

OUTPUT_DIR="${1:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${OUTPUT_DIR}/secure-dms-backup-${TIMESTAMP}"
COMPOSE_FILE="docker-compose.prod.yml"

mkdir -p "${BACKUP_DIR}"

echo "[+] Backing up PostgreSQL..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres pg_dump \
    -U "${POSTGRES_USER:-dms_user}" \
    -d "${POSTGRES_DB:-secure_dms}" \
    --clean --if-exists --no-owner \
    > "${BACKUP_DIR}/database.sql"
gzip "${BACKUP_DIR}/database.sql"
echo "[+] Database backup: ${BACKUP_DIR}/database.sql.gz"

echo "[+] Backing up MinIO/S3 data..."
docker compose -f "${COMPOSE_FILE}" exec -T minio mc alias set local \
    http://localhost:9000 \
    "${MINIO_ROOT_USER:-minioadmin}" \
    "${MINIO_ROOT_PASSWORD:-minioadmin}" > /dev/null 2>&1
docker compose -f "${COMPOSE_FILE}" exec -T minio mc mirror \
    "local/${S3_BUCKET:-secure-dms}" \
    "/tmp/s3-backup-${TIMESTAMP}"
docker compose -f "${COMPOSE_FILE}" cp \
    "minio:/tmp/s3-backup-${TIMESTAMP}" \
    "${BACKUP_DIR}/storage"
tar -czf "${BACKUP_DIR}/storage.tar.gz" -C "${BACKUP_DIR}" storage
rm -rf "${BACKUP_DIR}/storage"
echo "[+] Storage backup: ${BACKUP_DIR}/storage.tar.gz"

echo "[+] Creating backup manifest..."
cat > "${BACKUP_DIR}/manifest.txt" <<EOF
Backup Timestamp: ${TIMESTAMP}
Database: ${POSTGRES_DB:-secure_dms}
Storage Bucket: ${S3_BUCKET:-secure-dms}
Files:
  - database.sql.gz
  - storage.tar.gz
EOF

echo "[+] Backup complete: ${BACKUP_DIR}"
echo "[+] Total size: $(du -sh "${BACKUP_DIR}" | cut -f1)"
