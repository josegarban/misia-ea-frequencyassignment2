package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.*;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.ea.fitness.PermutationalObjectiveFunction;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;
/**
 * Minimizes frequency span with a greedy feasibility-decoder guided by a
 * permutation of U = totalDemand() frequency priorities {0..U-1}.
 */
public class MinSpanFAPObjective
        extends PermutationalObjectiveFunction
        implements FAPObjectiveFunction {

    private final FrequencyAssignmentProblem problem;
    private final int universeSize;      // U = totalDemand()
    private final double infeasiblePenalty; // base penalty for infeasible decodings

    public MinSpanFAPObjective(FrequencyAssignmentProblem problem) {
        // the chromosome length = universe size = sum of demands
        super(problem.totalDemand());
        this.problem = problem;
        this.universeSize = problem.totalDemand();
        // A safe large penalty: bigger than any achievable span
        // (max span cannot exceed universeSize-1)
        this.infeasiblePenalty = 1e9;
        // make it explicit that each position uses an alphabet of size U
        for (int i = 0; i < getNumVars(); i++) setAlphabetSize(i, universeSize);
    }

    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return problem;
    }

    @Override
    public OptimizationSense getOptimizationSense() {
        return OptimizationSense.MINIMIZATION;
    }
    
    /** Convert arbitrary gene object to a sortable numeric key (deterministic). */
    private static double alleleKey(Object o, int fallbackIndex) {
        if (o instanceof Integer) return ((Integer)o).doubleValue();
        if (o instanceof Double)  return (Double)o;
        return o.toString().hashCode();
    }

    private int[] genotypeToPriorityOrder(Genotype g) {
        final int L = g.length();                  // should equal problem.totalDemand()
        Integer[] idx = new Integer[L];
        for (int i = 0; i < L; i++) idx[i] = i;

        Arrays.sort(idx, (i, j) -> {
            double ai = alleleKey(g.getGene(i), i);
            double aj = alleleKey(g.getGene(j), j);
            int cmp = Double.compare(ai, aj);
            return (cmp != 0) ? cmp : Integer.compare(i, j); // tie-break by position
        });

        int[] order = new int[L];
        for (int k = 0; k < L; k++) order[k] = idx[k];
        return order;
    }
    
    // -------- decoder: permutation -> assignment --------
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        // 1) extract permutation π from the genotype
        final int L = getNumVars(); // == universeSize
        final int[] perm = genotypeToPriorityOrder(g);
        for (int i = 0; i < L; i++) {
            // Assumption: Genotype provides integer alleles via getGene(i)
            // If your Genotype API differs, adapt here.

        }

        // 2) init empty assignment map with deterministic emitter order
        Map<String, Set<Integer>> assignment = new LinkedHashMap<>();
        for (String id : problem.getEmitterNames()) {
            assignment.put(id, new TreeSet<>());
        }

        // 3) greedy fill: emitters in order, each needs demand(e) freqs
        for (String e1 : problem.getEmitterNames()) {
            int need = problem.getEmitter(e1).demand();
            Set<Integer> mySet = assignment.get(e1);

            // try to satisfy demand using greedy scan over π
            int idx = 0;
            while (mySet.size() < need && idx < perm.length) {
                int f = perm[idx++];
                if (isFeasibleToAdd(e1, f, assignment)) {
                    mySet.add(f);
                }
            }
            // if we exit with unmet demand, we leave it (the evaluator will penalize)
        }

        return assignment;
    }

    /**
     * Feasibility test to add frequency f to emitter e1, given current partial assignment.
     * Checks intra-emitter separation (distance 0) and inter-emitter separation vs all already assigned emitters.
     */
    private boolean isFeasibleToAdd(String e1, int f, Map<String, Set<Integer>> partial) {
        // Intra-emitter (distance 0):
        for (int f2 : partial.get(e1)) {
            if (!problem.checkSeparation(f, f2, 0.0)) return false;
        }
        // Inter-emitter: compare only with emitters already having some freqs
        for (Map.Entry<String, Set<Integer>> other : partial.entrySet()) {
            String e2 = other.getKey();
            if (e2.equals(e1)) continue;
            Set<Integer> freqs2 = other.getValue();
            if (freqs2.isEmpty()) continue;

            double d = problem.getDistance(e1, e2);
            for (int f2 : freqs2) {
                if (!problem.checkSeparation(f, f2, d)) return false;
            }
        }
        return true;
    }

    // -------- fitness evaluation --------
    @Override
    protected double _evaluate(Individual ind) {
        Genotype g = ind.getGenome();

        Map<String, Set<Integer>> assignment = genotype2map(g);

        // Count unmet demand
        int missing = 0;
        for (String id : problem.getEmitterNames()) {
            int have = assignment.get(id).size();
            int need = problem.getEmitter(id).demand();
            if (have < need) missing += (need - have);
        }

        if (missing > 0) {
            // penalize infeasible decodings; smaller missing -> smaller penalty
            // (still strictly worse than any feasible span)
            return infeasiblePenalty + missing;
        }

        // feasible → return span
        return FrequencyAssignmentProblem.frequencySpan(assignment);
    }
}
