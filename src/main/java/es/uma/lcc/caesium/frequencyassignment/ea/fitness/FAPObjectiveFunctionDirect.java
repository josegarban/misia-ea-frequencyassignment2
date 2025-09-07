package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.DiscreteObjectiveFunction;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

public class FAPObjectiveFunctionDirect extends DiscreteObjectiveFunction implements FAPObjectiveFunction {

    private final FrequencyAssignmentProblem fap;
    private final List<String> emitterOrder; // fixed order consistent with genotype rows
    private final int maxFreq;               // inclusive upper bound for frequency values
    private final int freqCount;             // = maxFreq + 1
    
    private final int emitterCount;
    private final int[] demandByIdx;

	/**
     * Builds an objective function with binary alphabet and a custom maximum frequency.
     * @param fap the problem instance
     */
    public FAPObjectiveFunctionDirect(FrequencyAssignmentProblem fap) {
    	// Cantidad de emisores multiplicado por la frecuencia máxima si se usara cada frecuencia una sola vez, Alfabeto binario.
    	super(fap.numEmitters() * (fap.maxFrequency() + 1), 2); 
		this.fap = fap;
        this.maxFreq = fap.maxFrequency();
        this.freqCount = maxFreq + 1;

        // Emisores
        this.emitterOrder = new ArrayList<>(fap.getEmitterNames());
        
        // Precálculo de algunas variables para ahorrar tiempo
        this.emitterCount = emitterOrder.size();
        this.demandByIdx = new int[emitterCount];
        for (int e = 0; e < emitterCount; e++) {
            demandByIdx[e] = fap.getEmitter(emitterOrder.get(e)).demand();
        }
    }

    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return fap;
    }
    public int getMaxFreq() {
        return maxFreq;
    }
    public List<String> getEmitterOrder() {
        return Collections.unmodifiableList(emitterOrder);
    }
	@Override
	public OptimizationSense getOptimizationSense() {
		return OptimizationSense.MINIMIZATION;
	}

	/**
     * Convertir el genotipo en un objeto. 
     */
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
    	Map<String, Set<Integer>> assignment = new HashMap<>(emitterCount * 2);
        for (int e = 0; e < emitterCount; e++) {
            String id = emitterOrder.get(e);
            int demand = demandByIdx[e];
            // demand is usually small: pre-size to avoid rehash
            HashSet<Integer> freqs = new HashSet<>(Math.max(4, demand * 2));
            int base = e * freqCount;
            for (int f = 0, found = 0; f < freqCount && found < demand; f++) {
                if ((int) g.getGene(base + f) != 0) {
                    freqs.add(f);
                    found++;
                }
            }
            assignment.put(id, freqs);
        }
        return assignment;
    }
    
    /**
     * Evaluar las soluciones.
     * Se aplica una penalización única a las asignaciones que violen las restrucciones con MAX_VALUE.
     */
    @Override
	protected double _evaluate(Individual i) {
    	// Obtener el atributo genoma del individuo
    	final Genotype g = i.getGenome();
    	
        // CHEQUEO 1: Las frecuencias en el genotipo no están por encima de la frecuencia máxima
    	int globalMin = maxFreq + 1; 
    	int globalMax = -1;          

        for (int e = 0; e < emitterCount; e++) {
            final int demand = demandByIdx[e];
            final int base = e * freqCount;

            int count = 0;
            int localMin = maxFreq + 2;
            int localMax = -1;

            for (int f = 0; f < freqCount; f++) {
                if ((int) g.getGene(base + f) != 0) {
                    count++;
                    if (count > demand) return maxFreq^2; // early reject
                    if (f < localMin) localMin = f;
                    if (f > localMax) localMax = f;
                }
            }
            if (count != demand) return maxFreq^2;

            // contribute to global span
            if (localMin < globalMin) globalMin = localMin;
            if (localMax > globalMax) globalMax = localMax;
        }

        // PASS 2: build assignment only now (most individuals will have exited earlier)
        Map<String, Set<Integer>> assignment = genotype2map(g);

        // Feasibility check (expensive)
        if (!fap.isFeasible(assignment)) return maxFreq^2;

        // Span already computed in PASS 1 (identical to max - min of used freqs).
        return (globalMax - globalMin);
    }
}


/*      // Si no hay frecuencias asignadas, no es una asignación factible
        // Como todas las penalizaciones se aplican igual, se puede cerrar el bucle más rápidamente para ahorrar tiempo.
        // Si no es factible por otras razones
        // Si es factible, devolver la distancia entre la frecuencia máxima y la mínima
 */
