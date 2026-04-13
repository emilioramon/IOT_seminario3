#!/bin/bash
#
# Despliega el semáforo en la Raspberry Pi (ttmi056).
#
# Uso:
#   ./deploy-device.sh              # Solo sube el JAR
#   ./deploy-device.sh --run        # Sube y ejecuta
#
# Prerequisitos:
#   - Ejecutar ./build-device.sh primero
#   - La RPi debe estar accesible en ttmi056.iot.upv.es
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/device/traffic-light-device.jar"

# Configuración de la RPi
RPI_HOST="ttmi053.iot.upv.es"
RPI_USER="ina"
RPI_DIR="/home/ina/disp-grupo1"

# Configuración del semáforo
DEVICE_ID="TL.R3s1.main"
BROKER_URL="tcp://tambori.dsic.upv.es:10083"
ROAD="R3"
ROAD_SEGMENT="R3s1"
START_POS="100"
END_POS="100"
INITIAL_STATE="RED"

if [ ! -f "$JAR" ]; then
    echo "ERROR: No se encontro $JAR"
    echo "Ejecuta primero: ./build-device.sh"
    exit 1
fi

echo "=== Subiendo JAR a la Raspberry Pi ==="
echo "Destino: $RPI_USER@$RPI_HOST:$RPI_DIR/"
echo "(password: ina.2022)"
echo ""

# Crear directorio remoto si no existe
ssh "$RPI_USER@$RPI_HOST" "mkdir -p $RPI_DIR"

# Subir JAR
scp "$JAR" "$RPI_USER@$RPI_HOST:$RPI_DIR/"

echo ""
echo "=== JAR desplegado correctamente ==="

if [ "$1" = "--run" ]; then
    echo ""
    echo "=== Ejecutando semaforo en la RPi ==="
    echo "Ctrl-C para detener"
    echo ""
    ssh "$RPI_USER@$RPI_HOST" "cd $RPI_DIR && java -jar traffic-light-device.jar $DEVICE_ID $BROKER_URL $ROAD $ROAD_SEGMENT $START_POS $END_POS $INITIAL_STATE"
else
    echo ""
    echo "Para ejecutar en la RPi:"
    echo "  ssh $RPI_USER@$RPI_HOST"
    echo "  cd $RPI_DIR"
    echo "  java -jar traffic-light-device.jar $DEVICE_ID $BROKER_URL $ROAD $ROAD_SEGMENT $START_POS $END_POS $INITIAL_STATE"
fi
