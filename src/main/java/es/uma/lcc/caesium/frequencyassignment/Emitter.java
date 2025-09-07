package es.uma.lcc.caesium.frequencyassignment;

/**
 * Information of an emitter.
 * @param id name of the emitter
 * @param x X-coordinate 
 * @param y Y-coordinate 
 * @param z Z-coordinate
 * @param demand number of frequencies demanded by the emitter.
 * @author ccottap
 * @version 1.0
 */
public record Emitter(String id, double x, double y, double z, int demand) {
	/**
	 * Returns the distance between this emitter and another
	 * @param other the other emitter
	 * @return the distance between this emitter and the other
	 */
	public double distance (Emitter other) {
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	/**
	 * Returns a printable version of the emitter
	 * @return a printable version of the emitter
	 */
	public String toString() {
		return id + "(" + x + ", " + y + ", " + z + "): " + demand;
	}
}
