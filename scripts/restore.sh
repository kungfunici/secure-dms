#!/bin/bash
set -euo pipefail

# =============================================================================
# Secure DMS — Restore Script
# Restores PostgreSQL database and MinIO/S3 data from a backup.
# Usage: ./scripts/restore.sh <backup-dir>
# =============================================================================

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup-dir>"
    echo "Example: $0 ./backups/secure-dms-backup-20260101_120000"
    exit 1
fi

BACKUP_DIR="$1"
COMPOSE_FILE="docker-compose.prod.yml"

if [ ! -f "${BACKUP_DIR}/manifest.txt" ]; then
    echo "[!] Invalid backup directory: ${BACKUP_DIR}"
    exit 1
fi

echo "[+] Restoring from: ${BACKUP_DIR}"
cat "${BACKUP_DIR}/manifest.txt"

read -p "[?] This will OVERWRITE existing data. Continue? (y/N) " CONFIRM
if [ "${CONFIRM}" != "y" ] && [ "${CONFIRM}" != "Y" ]; then
    echo "[!] Restore cancelled."
    exit 0
fi

echo "[+] Restoring PostgreSQL..."
gunzip -c "${BACKUP_DIR}/database.sql.gz" | docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U "${POSTGRES_USER:-dms_user}" -d "${POSTGRES_DB:-secure_dms}"
echo "[+] Database restored."

echo "[+] Restoring MinIO/S3 data..."
docker compose -f "${COMPOSE_FILE}" exec -T minio mc alias set local \
    http://localhost:9000 \
    "${MINIO_ROOT_USER:-minioadmin}" \
    "${MINIO_ROOT_PASSWORD:-minioadmin}" > /dev/null 2>&1
docker compose -f "${COMPOSE_FILE}" cp \
    "${BACKUP_DIR}/storage.tar.gz" \
    "minio:/tmp/storage.tar.gz"
docker compose -f "${COMPOSE_FILE}" exec -T minio sh -c "
    tar -xzf /tmp/storage.tar.gz -C /tmp && \
    mc mirror /tmp/storage local/${S3_BUCKET:-secure-dms} && \
    rm -rf /tmp/storage /tmp/storage.tar.gz
"
echo "[+] Storage restored."

echo "[+] Restore complete! Restarting services..."
docker compose -f "${COMPOSE_FILE}" restart backend
echo "[+] Done."
