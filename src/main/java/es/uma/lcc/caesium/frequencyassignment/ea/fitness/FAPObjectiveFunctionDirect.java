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
     * Mapear el índice del genotipo al índice del emisor más la frecuencia.
     * Cada emisor comienza en e * freqCount.
     * índice = e * frecuencias + f
     */
    private int indexOf(int emitterIndex, int frequency) {
        return emitterIndex * freqCount + frequency;
    }

    /**
     * Convertir el genotipo en un objeto. 
     */
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        Map<String, Set<Integer>> assignment = new HashMap<>();

        // Iterar por los emisores
        for (int e = 0; e < emitterOrder.size(); e++) {
        	// ID del emisor: e01, e02, ... e21.
            String id = emitterOrder.get(e);
            Set<Integer> freqs = new HashSet<>();
            
            // Iterar por las frecuencias
            for (int f = 0; f < freqCount; f++) {
                int gene = (int) g.getGene(indexOf(e, f)); 
                // Los valores = 1 representan que la frecuencia se ha asignado al genotipo.
                if (gene != 0) {
                    freqs.add(f);
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
        Map<String, Set<Integer>> assignment = genotype2map(i.getGenome());

        // Si no hay frecuencias asignadas, no es una asignación factible
        // Como todas las penalizaciones se aplican igual, se puede cerrar el bucle más rápidamente para ahorrar tiempo.
        for (String id : emitterOrder) {
            int assigned = assignment.get(id).size();
            int demand = fap.getEmitter(id).demand();
            if (assigned != demand) {
                return Double.MAX_VALUE;
            }
        }
        // Si no es factible por otras razones
        if (!fap.isFeasible(assignment)) {
            return Double.MAX_VALUE;
        }
        // Si es factible, devolver la distancia entre la frecuencia máxima y la mínima
        return FrequencyAssignmentProblem.frequencySpan(assignment);
    }

}

