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
        super(fap.numEmitters() * (fap.maxFrequency() + 1), 2); // binary alphabet
		this.fap = new FrequencyAssignmentProblem();
        this.maxFreq = fap.maxFrequency();
        this.freqCount = maxFreq + 1;

        // Fix a stable emitter order (sorted by id as in getEmitterNames()).
        this.emitterOrder = new ArrayList<>(fap.getEmitterNames());
    }

    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return fap;
    }

    /**
     * Maps genotype index to (emitterIndex, frequency).
     * idx = e * freqCount + f
     */
    private int indexOf(int emitterIndex, int frequency) {
        return emitterIndex * freqCount + frequency;
    }

    /**
     * Decodes the flattened matrix genotype into the assignment map.
     * Any gene value != 0 is treated as "assigned".
     */
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        Map<String, Set<Integer>> assignment = new HashMap<>();

        for (int e = 0; e < emitterOrder.size(); e++) {
            String id = emitterOrder.get(e);
            Set<Integer> freqs = new HashSet<>();

            for (int f = 0; f < freqCount; f++) {
                int gene = (int) g.getGene(indexOf(e, f)); // assumes integer access
                if (gene != 0) {
                    freqs.add(f);
                }
            }

            assignment.put(id, freqs);
        }

        return assignment;
    }

    /**
     * Objective: minimize span (max frequency - min frequency) subject to feasibility.
     * Infeasible solutions are penalized with Double.MAX_VALUE.
     */
    @Override
	protected double _evaluate(Individual i) {
        Map<String, Set<Integer>> assignment = genotype2map(i.getGenome());

        // Quick early exits: if any emitter has no frequencies but demand > 0, infeasible
        for (String id : emitterOrder) {
            int assigned = assignment.get(id).size();
            int demand = fap.getEmitter(id).demand();
            if (assigned != demand) {
                return Double.MAX_VALUE;
            }
        }

        // Full feasibility (pairwise separations, etc.)
        if (!fap.isFeasible(assignment)) {
            return Double.MAX_VALUE;
        }

        // Feasible: return span
        return FrequencyAssignmentProblem.frequencySpan(assignment);
    }

    /**
     * Convenience getters if needed by the EA.
     */
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

}

