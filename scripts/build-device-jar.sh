#!/usr/bin/env bash
# Construye el fat jar del dispositivo semaforo que se sube a la Raspberry Pi.
# Incluye: clases propias + Paho MQTT + org.json.
# NO incluye pi4j (esta en el classpath de la Pi separadamente).
#
# Salida: device/traffic-light-device.jar
# Main-Class: TrafficLightDeviceApp

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$ROOT/bin"
LIB="$ROOT/lib"
OUT="$ROOT/device/traffic-light-device.jar"
STAGING="$(mktemp -d)"
trap "rm -rf '$STAGING'" EXIT

echo "[1/4] Compilando fuentes (target Java 11)..."
rm -rf "$BIN" && mkdir -p "$BIN"
javac --release 11 -cp "$LIB/*" -d "$BIN" $(find "$ROOT/smartcar/src" -name "*.java")

echo "[2/4] Staging clases propias..."
cp -R "$BIN/." "$STAGING/"

echo "[3/4] Desempaquetando dependencias en staging..."
JARS=(
    "$LIB/org.eclipse.paho.client.mqttv3-1.2.5.jar"
    "$LIB/java-json.jar"
    "$LIB/pi4j-core-2.6.1.jar"
    "$LIB/pi4j-library-pigpio-2.6.1.jar"
    "$LIB/pi4j-plugin-pigpio-2.6.1.jar"
    "$LIB/slf4j-api-2.0.12.jar"
    "$LIB/slf4j-simple-2.0.12.jar"
)
for j in "${JARS[@]}"; do
    (cd "$STAGING" && unzip -qo "$j" -x 'META-INF/*.SF' 'META-INF/*.DSA' 'META-INF/*.RSA' 'META-INF/MANIFEST.MF' 'module-info.class' 2>/dev/null) || true
done

echo "[4/4] Empaquetando $OUT..."
mkdir -p "$(dirname "$OUT")"
(cd "$STAGING" && jar cfe "$OUT" TrafficLightDeviceApp .)

echo "OK. Fat jar: $OUT"
echo "Tamano: $(du -h "$OUT" | cut -f1)"
