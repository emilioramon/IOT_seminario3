import componentes.RoadPlace;
import componentes.SmartCar;
import componentes.SpeedLimitSign;

public class SmartCarStarterApp {
    public static void main(String[] args) throws Exception {

			if ( args.length < 2 )
			{
				System.out.println("Usage: SmartCarStarterApp <smartCarID> <brokerURL>");
				System.exit(1);
			}

			
			int ejercicio = 5;// Cambia este valor para ejecutar el ejercicio deseado (1, 2, 3, 4 o 5)
			int nroCoches = 6;// Número de SmartCars
			String smartCarID = args[0];
			String brokerURL = args[1];
			SmartCar sc1 = new SmartCar(smartCarID, brokerURL);
			SpeedLimitSign sl1 = new SpeedLimitSign("SL1", null, brokerURL, "R3s1", "R3", 100, 60, 30);
			SmartCar[] scs = new SmartCar[nroCoches];
			RoadPlace rp = new RoadPlace("R3s1", 0);

			for (int i=0; i<nroCoches; i++) {
				scs[i] = new SmartCar(smartCarID + "_" + i, brokerURL);
			}

			if (ejercicio == 1) {
				//Ejercicio 5.1 Simulación de entrada/salida de SmartCar en el segmento R3s1
				sc1.getIntoRoad("R3s1", 100);  // indicamos que el SmartCar está en tal segmento
				Thread.sleep(5000); 
				sc1.getOutRoad("R3s1", 150);  // indicamos que el SmartCar ha salido del segmento
			}

			if (ejercicio == 2) { 
				// Ejercicio 5.2 Simulación de entrada/salida de SmartCars en el segmento R3s1
								
				//Ingreso de 10 SmartCars en el segmento R3s1
				for (int i=0; i<nroCoches; i++) {
					scs[i].setCurrentRoadPlace(rp);
					scs[i].getIntoRoad("R3s1", 100);
					scs[i].setSpeed(100); // Asignamos una velocidad inicial a cada SmartCar
				}
				Thread.sleep(5000);

				for (int i=0; i<nroCoches; i++) {
					scs[i].getOutRoad("R3s1", 150	);
				}
				Thread.sleep(5000);
			} 
			
			if (ejercicio == 3) {
					// Ejercicio 5.3 Simulación de nuevo dispositivo de señal de trafico: SpeedLimitSign
					for (int i=0; i<nroCoches; i++) {
						scs[i].setCurrentRoadPlace(rp);
						scs[i].getIntoRoad("R3s1", 100);
						scs[i].setSpeed(100);
					}
					sl1.connect();
					sl1.publishSignal();
			}
			if (ejercicio == 4) {
				// Ejercicio 5.4 Simulación de notificación de cambio de estado de una señal
				for (int i=0; i<nroCoches; i++) {
						scs[i].setCurrentRoadPlace(rp);
						scs[i].getIntoRoad("R3s1", 100);
						scs[i].setSpeed(100);
						Thread.sleep(5000);
					}	
				sl1.connect();
				sl1.updateLimits(50);
				sl1.publishSignal();
				Thread.sleep(5000);
			}

			if (ejercicio == 5) {
				//Ejercicio 5.5 Simulación de notificación de incidente por parte del SmartCar
				scs[0].setCurrentRoadPlace(rp);
				scs[0].getIntoRoad("R3s1", 100);
				scs[0].notifyIncident("accident"); // el SmartCar notifica un accidente en su ubicación actual
				Thread.sleep(5000);

			//Limpieza final
			
			sl1.disconnect();
			}
		if (ejercicio == 3 || ejercicio == 4 || ejercicio == 5) {
			for (int i=1; i<nroCoches; i++) {
				scs[i].getOutRoad("R3s1", 150	);
			}
		}
	}
}
