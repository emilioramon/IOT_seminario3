#!/usr/bin/env bash
# Genera un keystore PKCS#12 a partir de los certificados de AWS IoT.
# Java estandar no parsea claves PKCS#1 (BEGIN RSA PRIVATE KEY) directamente,
# asi que convertimos cert + key a PKCS#12 que si es nativo.
#
# Requiere: openssl
# Uso: ./scripts/generate-aws-keystore.sh

set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")/.." && pwd)/certificates"
CERT="${CERT_DIR}/433b07645875-certificate.pem.crt"
KEY="${CERT_DIR}/433b07645875-private.pem.key"
OUT="${CERT_DIR}/aws-iot-client.p12"
ALIAS="aws-iot"
PASSWORD="${AWS_IOT_KEYSTORE_PASSWORD:-changeit}"

if [[ ! -f "$CERT" || ! -f "$KEY" ]]; then
  echo "ERROR: No se encontraron los PEMs en $CERT_DIR"
  echo "  Esperado: $CERT"
  echo "  Esperado: $KEY"
  exit 1
fi

openssl pkcs12 -export \
  -in  "$CERT" \
  -inkey "$KEY" \
  -out "$OUT" \
  -name "$ALIAS" \
  -passout "pass:${PASSWORD}"

echo "Keystore generado: $OUT"
echo "Password: ${PASSWORD}  (exporta AWS_IOT_KEYSTORE_PASSWORD para cambiarlo)"
