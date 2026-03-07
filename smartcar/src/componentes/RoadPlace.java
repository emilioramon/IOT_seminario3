package componentes;

public class RoadPlace {

	protected String road = null;
	protected int km = 0;
	
	public RoadPlace(String road, int km) {
		this.road = road;
		this.km = km;
	}
	
	public void setKm(int km) {
		this.km = km;
	}
	
	public int getKm() {
		return km;
	}
	
	public String getRoad() {
		return road;
	}
	
	public void setRoad(String road) {
		this.road = road;
	}
	
}
