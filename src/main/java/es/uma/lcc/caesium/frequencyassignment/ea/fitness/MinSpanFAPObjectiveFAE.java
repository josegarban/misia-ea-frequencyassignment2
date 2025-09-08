package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.*;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.ea.fitness.PermutationalObjectiveFunction;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

/**
 * Minimizes span using the First-Available-Emitter (FAE) decoder.
 * Genotype encodes a permutation (priority order) over emitters.
 */
public class MinSpanFAPObjectiveFAE
        extends PermutationalObjectiveFunction
        implements FAPObjectiveFunction {

    private final FrequencyAssignmentProblem problem;
    private final int nEmitters;
    private final int universeSize; // U = totalDemand()
    private final double infeasiblePenalty = 1e9; // > any achievable span

    public MinSpanFAPObjectiveFAE(FrequencyAssignmentProblem problem) {
        // Permutation length = number of emitters (FAE scans emitters)
        super(problem.numEmitters());
        this.problem = problem;
        this.nEmitters = problem.numEmitters();
        this.universeSize = problem.totalDemand();
        for (int i = 0; i < getNumVars(); i++) setAlphabetSize(i, nEmitters);
    }

    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return problem;
    }

    @Override
    public OptimizationSense getOptimizationSense() {
        return OptimizationSense.MINIMIZATION;
    }

    // ---------- Decoder (FAE) ----------
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        // 1) Emitter visit order P derived from genotype
        final List<String> emitterIds = new ArrayList<>(problem.getEmitterNames()); // deterministic base order
        final int[] order = genotypeToEmitterOrder(g, emitterIds.size());

        // 2) Init empty assignment
        Map<String, Set<Integer>> A = new LinkedHashMap<>();
        for (String id : problem.getEmitterNames()) A.put(id, new TreeSet<>());

        // 3) Demands remaining
        Map<String, Integer> remaining = new HashMap<>();
        int T = 0;
        for (String id : problem.getEmitterNames()) {
            int d = problem.getEmitter(id).demand();
            remaining.put(id, d);
            T += d;
        }

        // 4) Frequencies tried left-to-right (f = 0,1,2,...)
        //    Cap with a safe upper bound (no reuse): maxFrequency()
        int f = 0;
        final int F_MAX = Math.max(problem.maxFrequency(), universeSize); // be generous but finite

        while (T > 0 && f <= F_MAX) {
            // sweep emitters in GA-defined order
            for (int idx : order) {
                String e = emitterIds.get(idx);
                if (remaining.get(e) == 0) continue;           // already satisfied
                if (canAssign(e, f, A)) {
                    A.get(e).add(f);
                    remaining.put(e, remaining.get(e) - 1);
                    T--;
                    if (T == 0) break;
                }
            }
            f++;
        }

        // If T > 0 here, infeasible under this cap; evaluator will penalize
        return A;
    }

    // Check feasibility of assigning f to emitter e given current partial assignment A
    private boolean canAssign(String e, int f, Map<String, Set<Integer>> A) {
        // Intra-emitter (distance 0)
        for (int f2 : A.get(e)) {
            if (!problem.checkSeparation(f, f2, 0.0)) return false;
        }
        // Inter-emitter
        for (Map.Entry<String, Set<Integer>> other : A.entrySet()) {
            String o = other.getKey();
            if (o.equals(e) || other.getValue().isEmpty()) continue;
            double d = problem.getDistance(e, o);
            for (int f2 : other.getValue()) {
                if (!problem.checkSeparation(f, f2, d)) return false;
            }
        }
        return true;
    }

    // ---------- Evaluation ----------
    @Override
    protected double _evaluate(Individual ind) {
        Map<String, Set<Integer>> assignment = genotype2map(ind.getGenome());

        // unmet demand?
        int missing = 0;
        for (String id : problem.getEmitterNames()) {
            int have = assignment.get(id).size();
            int need = problem.getEmitter(id).demand();
            if (have < need) missing += (need - have);
        }
        if (missing > 0) return infeasiblePenalty + missing;

        // feasible → span
        return FrequencyAssignmentProblem.frequencySpan(assignment);
    }

    // ---------- Utilities ----------
    /** Robustly derive a permutation over emitters from arbitrary-typed genes. */
    private int[] genotypeToEmitterOrder(Genotype g, int n) {
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;

        Arrays.sort(idx, (i, j) -> {
            double ai = alleleKey(g.getGene(i), i);
            double aj = alleleKey(g.getGene(j), j);
            int cmp = Double.compare(ai, aj);
            return (cmp != 0) ? cmp : Integer.compare(i, j);
        });

        int[] order = new int[n];
        for (int k = 0; k < n; k++) order[k] = idx[k];
        return order;
    }

    /** Deterministic numeric key for sorting arbitrary gene objects. */
    private static double alleleKey(Object o, int fallbackIndex) {
        if (o instanceof Integer) return ((Integer)o).doubleValue();
        if (o instanceof Double)  return (Double)o;
        return o.toString().hashCode();
    }
}
