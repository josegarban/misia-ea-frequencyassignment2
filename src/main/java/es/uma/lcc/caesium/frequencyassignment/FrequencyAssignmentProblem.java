package es.uma.lcc.caesium.frequencyassignment;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * An instance of the Frequency Assignment Problem
 * 
 * @author ccottap
 * @version 1.0
 */
public class FrequencyAssignmentProblem {
	/**
	 * the emitters
	 */
	private Map<String, Emitter> emitters;
	/**
	 * distances between emitters
	 */
	private Map<String, Map<String, Double>> distances;
	/**
	 * interference constraints: for each frequency separation, 
	 * which is the minimum distance required.
	 */
	private Map<Integer, Double> interferences;
	/**
	 * separation required for two frequencies assigned to the same emitter
	 */
	private int maxSeparation = Integer.MAX_VALUE;

	
	/**
	 * Main constructor. Creates the instance without any emitter
	 * or interference constraints.
	 */
	public FrequencyAssignmentProblem() {
		emitters = new HashMap<String,Emitter>();
		distances = new HashMap<String, Map<String, Double>>();
		interferences = new HashMap<Integer, Double>();
	}
	
	/**
	 * Constructor that loads the problem from a file.
	 * No sanity checks are performed on the interference constraints.
	 * @param filename the file to load the problem from
	 * @throws FileNotFoundException if file cannot be found
	 */
	public FrequencyAssignmentProblem(String filename) throws FileNotFoundException {
		this();
		
		Scanner inputFile = new Scanner (new File(filename));
		inputFile.useLocale(java.util.Locale.US);
		
		int numEmitters = inputFile.nextInt();
		for (int i = 0; i < numEmitters; i++) {
			String id = inputFile.next();
			double x = inputFile.nextDouble();
			double y = inputFile.nextDouble();
			double z = inputFile.nextDouble();
			int demand = inputFile.nextInt();
			addEmitter(new Emitter(id, x, y, z, demand));
		}
		do {
			int separation = inputFile.nextInt();
			double distance = inputFile.nextDouble();
			addInterference(separation, distance);
		} while (inputFile.hasNext());
		inputFile.close();
	}
	
	/**
	 * Returns the number of emitters
	 * @return the number of emitters
	 */
	public int numEmitters() {
		return emitters.size();
	}
	
	/**
	 * Gets the names of all emitters
	 * @return a set with the names of all emitters
	 */
	public Set<String> getEmitterNames() {
		return new TreeSet<String> (emitters.keySet());
	}
	
	/**
	 * Gets an emitter given its id
	 * @param id the id of the emitter
	 * @return the emitter with said id
	 */
	public Emitter getEmitter (String id) {
		return emitters.get(id);
	}
	
	/**
	 * Adds an emitter to the problem
	 * @param e	the emitter to add
	 */
	public void addEmitter (Emitter e) {
		Map<String, Double> rowdist = new HashMap<String, Double>();
		
		for (var other: emitters.entrySet()) {
			double d = e.distance(other.getValue());
			rowdist.put(other.getKey(), d);
			distances.get(other.getKey()).put(e.id(), d);
		}
		rowdist.put(e.id(), 0.0);
		distances.put(e.id(), rowdist);
		emitters.put(e.id(), e);
	}
	
	/**
	 * Gets the distance between two emitters
	 * @param id1 the id of the first emitter
	 * @param id2 the id of the second emitter
	 * @return the distance between the two emitters
	 */
	public double getDistance(String id1, String id2) {
		return distances.get(id1).get(id2);
	}
	
	
	/**
	 * Adds an interference constraint
	 * @param separation frequency separation
	 * @param distance minimum distance required to reuse frequencies with such separation
	 */
	public void addInterference(int separation, double distance) {
		interferences.put(separation, distance);
		if (distance == 0) {
			maxSeparation = Math.min(maxSeparation, separation);
		}
	}
	
	
	/**
	 * Gets the minimum distance required for a given frequency separation
	 * @param separation the frequency separation
	 * @return the minimum distance required
	 */
	public double getMinimumDistance(int separation) {
		if (interferences.containsKey(separation)) {
			return interferences.get(separation);
		}
		else
			return 0.0; // No interference constraint
	}
	
	/**
	 * small constant used to avoid floating point comparison problems
	 */
	private static double EPSILON = 1e-8;
	
	/**
	 * Gets the minimum separation required for a given distance
	 * @param distance the distance between emitters
	 * @return the minimum separation required
	 */
	public int getMinimumSeparation(double distance) {
		Set<Integer> separations = new TreeSet<Integer>(interferences.keySet());
		for (int i: separations) {
			if (interferences.get(i) <= (distance + EPSILON)) {
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}
	
	/**
	 * Checks if two frequencies {@code f1} and {@code f2} can be assigned to 
	 * two emitters separated by distance {@code distance}
	 * @param f1 a frequency
	 * @param f2 another frequency
	 * @param distance separation between the emitters
	 * @return true if the frequencies can be assigned, false otherwise
	 */
	public boolean checkSeparation(int f1, int f2, double distance) {
		return (distance + EPSILON) >= getMinimumDistance(Math.abs(f1-f2));
	}
	
	/**
	 * Checks if an assignment is feasible
	 * @param assignment the assignment to check
	 * @return true if the assignment is feasible, false otherwise
	 */
	public boolean isFeasible(Map<String, Set<Integer>> assignment) {
		Set<String> ids = getEmitterNames();
		
		for (String id: ids) {
			Set<Integer> freqs = assignment.get(id);
			if (freqs.size() != emitters.get(id).demand()) {
				// The number of frequencies assigned to the emitter is not correct
                System.out.println("Not enough frequencies for emitter " + id + " (" + freqs.size() + " instead of " + emitters.get(id).demand() + ")");
				return false;
            }
			for (int f1: freqs) {
                for (int f2: freqs) {
                    if ((f1 != f2) && !checkSeparation(f1, f2, 0.0)) {
                    	System.out.println("Separation constraint violated in " + id  + " (" + f1 + " and " + f2 + ")");
                    	System.out.println("The minimum separation is " + getMinimumSeparation(0.0));
                        return false;
                    }
                }
            }
			for (String other: ids) {
                if (id.compareTo(other) < 0) {
                	// Checks separation constraints
                    double d = getDistance(id, other);
                    for (int f1: freqs) {
                        for (int f2: assignment.get(other)) {
                            if (!checkSeparation(f1, f2, d)) {
                            	System.out.println("Separation constraint violated between " + id + " and " + other + " (" + f1 + " and " + f2 + ")");
                            	System.out.println("The distance is " + d + " and the minimum separation is " + getMinimumSeparation(d));
                                return false;
                            }
                        }
                    }
                }
            }
		}
		
        return true;
    }
	
	
	/**
	 * Returns a printable version of the problem
	 * @return a printable version of the problem
     */
	@Override
	public String toString() {
		String cad = "Emitters:\n";
		for (var id : getEmitterNames()) {
			cad += "\t" + getEmitter(id) + "\n";
		}
		cad += "Interferences:\n";
		for (var i : new TreeSet<Integer>(interferences.keySet())) {
			cad += "\t" + i + " -> " + interferences.get(i) + "\n";
		}
		return cad;
	}
	
	
	// ---------------------------------------------------
	// Static methods to evaluate solutions
	// ---------------------------------------------------
	
	/**
	 * Returns the span of frequencies used in an assignment (difference between the 
	 * lowest and the highest frequency assigned)
	 * @param assignment the assignment
	 * @return the span of frequencies used in the assignment
	 */
	public static int frequencySpan (Map<String, Set<Integer>> assignment) {
		int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (var e: assignment.entrySet()) {
            for (int f: e.getValue()) {
                min = Math.min(min, f);
                max = Math.max(max, f);
            }
        }
        return max - min;
	}
	
	/**
	 * Returns the number of frequencies used in an assignment
	 * @param assignment the assignment
	 * @return the number of frequencies used in the assignment
	 */
	public static int numberOfFrequencies(Map<String, Set<Integer>> assignment) {
		Set<Integer> freqs = new HashSet<Integer>();
		for (var e : assignment.entrySet()) {
			freqs.addAll(e.getValue());
		}
		return freqs.size();
	}
	
	
	// ---------------------------------------------------
	// Utility functions
	// ---------------------------------------------------
		
	/**
	 * Returns the total demand of all emitters. It is also an upper bound for the
	 * number of frequencies required (i.e., if no frequency was reused)
	 * @return the total demand of all emitters
	 */
	public int totalDemand() {
		int total = 0;
		for (var e : emitters.values()) {
			total += e.demand();
		}
		return total;
	}
	
	/**
	 * Returns an upper bound for the span of frequencies required by assuming
	 * no frequency is reused
	 * @return an upper bound for the span of frequencies required
	 */
	public int maxFrequency() {
		return totalDemand() * getMinimumSeparation(0.0);
	}
	
	
	/**
	 * Returns a printable string with a certain frequency assignment
	 * @param assignment the assignment to print
	 * @return a printable string with the assignment
	 */
	public String formatFrequencyAssignment(Map<String, Set<Integer>> assignment) {
		String cad = "";
		for (var id : getEmitterNames()) {
			cad += id + ": ";
			for (int f : assignment.get(id)) {
				cad += f + " ";
			}
			cad += "\n";
		}
		return cad;
	}
	
	 /**
     * Returns the set of usable frequencies as an array of integers
     * from 0 up to maxFrequency().
     * @return array of usable frequencies
     */
    public int[] getFrequencies() {
        int maxF = maxFrequency();
        return IntStream.rangeClosed(0, maxF).toArray();
    }

    
    private List<String> emitterOrder = new ArrayList<>();
    private Map<String,Integer> emitterIndex = new HashMap<>();

    /** Demand per emitter aligned with emitterOrder[i] */
    private int[] demandArray;

    /** Distances in a dense matrix [i][j] aligned with emitterOrder */
    private double[][] distanceMatrix;

    /**
     * For every distance value appearing in the instance (interference thresholds),
     * we keep, for each emitter i, the neighbor indices j (j!=i) with distance(i,j) <= threshold,
     * sorted by actual distance ascending.
     *
     * Keyed by the *exact* distance threshold read from the instance.
     */
    private Map<Double, List<List<Integer>>> neighborsWithin = new HashMap<>();
    
    public void recomputeCaches() {
        // 1) stable order and index
        this.emitterOrder = new ArrayList<>(getEmitterNames()); // TreeSet order already sorted
        this.emitterIndex.clear();
        for (int i = 0; i < emitterOrder.size(); i++) {
            emitterIndex.put(emitterOrder.get(i), i);
        }

        // 2) demand array
        int n = emitterOrder.size();
        this.demandArray = new int[n];
        for (int i = 0; i < n; i++) {
            demandArray[i] = emitters.get(emitterOrder.get(i)).demand();
        }

        // 3) dense distance matrix
        this.distanceMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            String idi = emitterOrder.get(i);
            for (int j = 0; j < n; j++) {
                String idj = emitterOrder.get(j);
                distanceMatrix[i][j] = getDistance(idi, idj);
            }
        }

        // 4) neighborsWithin for each distance threshold in the instance
        this.neighborsWithin.clear();
        // Collect unique thresholds found in the instance file (values of interferences)
        // Note: we intentionally use the actual doubles from the file.
        TreeSet<Double> thresholds = new TreeSet<>(new java.util.HashSet<>(interferences.values()));

        for (double threshold : thresholds) {
            List<List<Integer>> perEmitter = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int ii = i;
                List<Integer> neigh = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    if (i == j) continue;
                    if (distanceMatrix[i][j] <= threshold + EPSILON) {
                        neigh.add(j);
                    }
                }
                // sort by actual distance ascending
                neigh.sort((a, b) -> Double.compare(distanceMatrix[ii][a], distanceMatrix[ii][b]));
                perEmitter.add(neigh);
            }
            neighborsWithin.put(threshold, perEmitter);
        }
    }
    
    /** index in [0..n) for an emitter id */
    public int indexOf(String id) { return emitterIndex.get(id); }

    /** stable order of emitter ids (same order used by demandArray and distanceMatrix) */
    public List<String> getEmitterOrder() { return emitterOrder; }

    /** demand per emitter aligned with getEmitterOrder() */
    public int[] getDemandArray() { return demandArray; }

    /** distance(i,j) with i,j in 0..n-1 aligned with getEmitterOrder() */
    public double[][] getDistanceMatrix() { return distanceMatrix; }

    /**
     * Neighbors (indices) for each threshold value present in the instance.
     * neighborsWithin.get(threshold).get(i) => list of neighbor indices of i within threshold,
     * sorted from closest to furthest.
     */
    public Map<Double, List<List<Integer>>> getNeighborsWithin() { return neighborsWithin; }
    
}
