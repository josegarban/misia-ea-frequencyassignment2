package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.DiscreteObjectiveFunction;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;
/**
 * Direct objective for FAP with a 0/1 matrix encoding:
 * variables = nEmitters * M
 * gene(i,f) = 1 iff emitter i uses frequency f
 */
public class FAPObjectiveFunctionDirect extends DiscreteObjectiveFunction implements FAPObjectiveFunction {

    // ------------ config ------------
    private static final double BIG_PENALTY = 1e9;          // "large penalty"
    private static final double PER_UNIT_PENALTY = 1e6;     // scaled supplement

    // ------------ problem / encoding ------------
    private final FrequencyAssignmentProblem problem;
    private final int nEmitters;
	private int M;

    // Derived caches from problem
    private final List<String> emitterOrder;
    private final int[] demand;

    // Optional: frequency base offset if you want real frequencies to start elsewhere
    private final int frequencyOffset;

    // ------------ ctor ------------
    public FAPObjectiveFunctionDirect(FrequencyAssignmentProblem problem) {
        super(problem.numEmitters() * problem.maxFrequency(), 2); // binary alphabet
        this.problem = problem;
        this.nEmitters = problem.numEmitters();
        this.M = problem.maxFrequency();
        this.frequencyOffset = 0; // using frequencies 0..M-1; change if desired

        // pull from precomputed caches
        problem.recomputeCaches();
        this.emitterOrder = problem.getEmitterOrder();
        this.demand = problem.getDemandArray();
        

        if (this.emitterOrder.size() != this.nEmitters) {
            throw new IllegalStateException("Problem caches not computed; call problem.recomputeCaches() after building the instance.");
        }
    }

    // ------------ FAPObjectiveFunction ------------
    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return problem;
    }

    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        Map<String, Set<Integer>> assignment = new HashMap<>();
        for (int i = 0; i < nEmitters; i++) {
            String id = emitterOrder.get(i);
            Set<Integer> freqs = new TreeSet<>();
            int base = i * M;
            for (int f = 0; f < M; f++) {
                int allele = (int) g.getGene(base + f); // assumes Genotype.get(int) -> {0,1}
                if (allele == 1) {
                    freqs.add(frequencyOffset + f);
                }
            }
            assignment.put(id, freqs);
        }
        return assignment;
    }

    // ------------ objective ------------
    /**
     * Evaluate genome:
     * 1) Hard demand check (too many -> penalty, too few -> penalty) with early return
     * 2) Same-emitter quick span check vs min separation at d=0 (early return on violation)
     * 3) Full feasibility check with problem.isFeasible(...)
     * 4) If feasible, objective = frequency span
     */
	protected double _evaluate(Individual ind) {
        // ---- 1 & 2 operate directly on the 0/1 genome without constructing full sets when possible ----
        final int sameEmitterMinSep = problem.getMinimumSeparation(0.0);
        Genotype g = ind.getGenome();

        int globalMin = Integer.MAX_VALUE;
        int globalMax = Integer.MIN_VALUE;
        
     // Global "too many 1s" early-out: if total 1s > total demand, penalize and stop
        final int totalDemand = problem.totalDemand();
        int totalOnes = 0;
        for (int i = 0; i < nEmitters; i++) {
            int base = i * M;
            for (int f = 0; f < M; f++) {
                if ((int) g.getGene(base + f) == 1) {
                    totalOnes++;
                    if (totalOnes > totalDemand) {
                        return BIG_PENALTY + PER_UNIT_PENALTY * (totalOnes - totalDemand);
                    }
                }
            }
        }
        
        for (int i = 0; i < nEmitters; i++) {
            int base = i * M;

            // Count assigned freqs & collect (we need them for step 3.1/3.2 anyway)
            int assigned = 0;
            // To avoid allocations, first scan to compute count and max,
            // then if needed re-scan to build a small array.
            int localMax = Integer.MIN_VALUE;

            for (int f = 0; f < M; f++) {
                if ((int) g.getGene(base + f) == 1) {
                    assigned++;
                    localMax = Math.max(localMax, f);
                    // track global bounds now (we'll refine after feasibility)
                    globalMin = Math.min(globalMin, f);
                    globalMax = Math.max(globalMax, f);
                }
            }

            // --- Step 1: too many frequencies? early penalty ---
            if (assigned > demand[i]) {
                int excess = assigned - demand[i];
                return BIG_PENALTY + PER_UNIT_PENALTY * excess;
            }

            // --- Step 2: too few frequencies? early penalty ---
            if (assigned < demand[i]) {
                int missing = demand[i] - assigned;
                return BIG_PENALTY + PER_UNIT_PENALTY * missing;
            }

            // If assigned == demand[i], continue with step 3 for this emitter
            if (assigned > 1 && sameEmitterMinSep > 0) {
                // 3.1 Rank frequencies: since M can be large, we avoid sorting a long list
                // by scanning from the top (localMax down) just for the assigned ones.

                // Collect assigned freqs in ascending order (cheaply):
                int[] fs = new int[assigned];
                int k = 0;
                for (int f = 0; f < M; f++) {
                    if ((int) g.getGene(base + f) == 1) {
                        fs[k++] = f;
                    }
                }
                // fs is ascending because we filled in increasing f
                int fMax = fs[assigned - 1];

                // 3.2 quick check: compare max with every other frequency
                for (int idx = 0; idx < assigned - 1; idx++) {
                    int gap = fMax - fs[idx];
                    if (gap < sameEmitterMinSep) {
                        // violation -> large penalty, stop
                        return BIG_PENALTY + PER_UNIT_PENALTY * (sameEmitterMinSep - gap + 1);
                    }
                }
            }
        }

        // If we reached here, the quick checks passed for all emitters.
        // Now build the actual assignment and let the full feasibility checker decide inter-emitter constraints.
        Map<String, Set<Integer>> assignment = genotype2map(g);

        // --- Full feasibility check ---
        if (!problem.isFeasible(assignment)) {
            return BIG_PENALTY; // inter-emitter or same-emitter finer violation
        }

        // --- Feasible: objective is to minimize span ---
        // (If no frequency was assigned globally—which shouldn't happen because of demand checks—handle defensively)
        if (globalMin == Integer.MAX_VALUE) return BIG_PENALTY;

        int span = globalMax - globalMin;
        return span;
    }

    @Override
    public OptimizationSense getOptimizationSense() {
        return OptimizationSense.MINIMIZATION;
    }

}

