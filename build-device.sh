#!/bin/bash
#
# Compila y empaqueta el semáforo (TrafficLightDeviceApp) en un JAR único
# que incluye todas las dependencias (fat jar), listo para desplegar en la RPi.
#
# Uso:
#   ./build-device.sh
#
# Resultado:
#   device/traffic-light-device.jar
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/smartcar/src"
LIB_DIR="$SCRIPT_DIR/lib"
BUILD_DIR="$SCRIPT_DIR/device/build"
OUTPUT_DIR="$SCRIPT_DIR/device"
JAR_NAME="traffic-light-device.jar"

echo "=== Compilando TrafficLightDeviceApp ==="

# Limpiar build anterior
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Compilar todos los fuentes necesarios
CLASSPATH="$LIB_DIR/org.eclipse.paho.client.mqttv3-1.2.5.jar:$LIB_DIR/java-json.jar"

# Compilar para Java 11 (la RPi tiene Java 11, class file version 55)
javac -cp "$CLASSPATH" \
    --release 11 \
    -d "$BUILD_DIR" \
    "$SRC_DIR/utils/MySimpleLogger.java" \
    "$SRC_DIR/componentes/RoadPlace.java" \
    "$SRC_DIR/componentes/MyMqttClient.java" \
    "$SRC_DIR/componentes/SmartCar.java" \
    "$SRC_DIR/componentes/SmartCar_TrafficNotifier.java" \
    "$SRC_DIR/componentes/SmartCar_RoadInfoSubscriber.java" \
    "$SRC_DIR/componentes/SmartCar_InicidentNotifier.java" \
    "$SRC_DIR/componentes/TrafficSign.java" \
    "$SRC_DIR/componentes/TrafficLightSign.java" \
    "$SRC_DIR/TrafficLightDeviceApp.java"

echo "Compilacion OK"

# Extraer dependencias en el build dir (fat jar)
echo "=== Incluyendo dependencias ==="
cd "$BUILD_DIR"
jar xf "$LIB_DIR/org.eclipse.paho.client.mqttv3-1.2.5.jar"
jar xf "$LIB_DIR/java-json.jar"
# Preservar META-INF/services (Paho lo necesita para registrar el módulo TCP)
# pero reemplazar el MANIFEST.MF con el nuestro
echo "=== Creando JAR ==="
cat > META-INF/MANIFEST.MF << 'EOF'
Manifest-Version: 1.0
Main-Class: TrafficLightDeviceApp
EOF

# Empaquetar todo en un solo JAR
jar cfm "$OUTPUT_DIR/$JAR_NAME" META-INF/MANIFEST.MF .

# Limpiar
cd "$SCRIPT_DIR"
rm -rf "$BUILD_DIR"

echo ""
echo "=== BUILD EXITOSO ==="
echo "JAR generado: device/$JAR_NAME"
echo ""
echo "Para desplegar en la Raspberry Pi:"
echo "  scp device/$JAR_NAME ina@ttmi053.iot.upv.es:/home/ina/disp-grupo1/"
echo ""
echo "Para ejecutar en la Raspberry Pi:"
echo "  ssh ina@ttmi053.iot.upv.es"
echo "  cd /home/ina/disp-grupo1"
echo "  java -jar $JAR_NAME TL.R3s1.main tcp://tambori.dsic.upv.es:10083 R3 R3s1 100 100 RED"
