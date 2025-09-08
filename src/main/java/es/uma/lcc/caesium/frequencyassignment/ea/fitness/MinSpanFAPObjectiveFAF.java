package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.*;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.ea.fitness.PermutationalObjectiveFunction;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

public class MinSpanFAPObjectiveFAF
        extends PermutationalObjectiveFunction
        implements FAPObjectiveFunction {

    private final FrequencyAssignmentProblem problem;
    private final int nEmitters;
    private final double infeasiblePenalty = 1e9; // > any achievable span

    public MinSpanFAPObjectiveFAF(FrequencyAssignmentProblem problem) {
        // Chromosome encodes emitter priority -> length = #emitters
        super(problem.numEmitters());
        this.problem = problem;
        this.nEmitters = problem.numEmitters();
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

    // -------- Decoder: First-Available-Frequency (FAF) --------
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        // Emitter visit order driven by genotype
        final List<String> emitterIds = new ArrayList<>(problem.getEmitterNames()); // deterministic base order
        final int[] emitterOrder = genotypeToEmitterOrder(g, emitterIds.size());

        // Init empty assignment and remaining demand
        Map<String, Set<Integer>> A = new LinkedHashMap<>();
        Map<String, Integer> remaining = new HashMap<>();
        for (String id : emitterIds) {
            A.put(id, new TreeSet<>());
            remaining.put(id, problem.getEmitter(id).demand());
        }

        // Cap to guarantee termination even under heavy conflicts
        final int F_MAX = Math.max(problem.maxFrequency(), problem.totalDemand());

        // Process emitters in GA-defined order; for each, assign its demand
        for (int idx : emitterOrder) {
            String e = emitterIds.get(idx);
            int need = remaining.get(e);

            int assigned = 0;
            int f = 0;
            while (assigned < need && f <= F_MAX) {
                if (canAssign(e, f, A)) {
                    A.get(e).add(f);
                    assigned++;
                }
                f++;
            }
            remaining.put(e, need - assigned); // if >0, evaluator will penalize
        }

        return A;
    }

    // Feasibility to add frequency f to emitter e given partial assignment A
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

    // -------- Evaluation --------
    @Override
    protected double _evaluate(Individual ind) {
        Map<String, Set<Integer>> assignment = genotype2map(ind.getGenome());

        int missing = 0;
        for (String id : problem.getEmitterNames()) {
            int have = assignment.get(id).size();
            int need = problem.getEmitter(id).demand();
            if (have < need) missing += (need - have);
        }
        if (missing > 0) return infeasiblePenalty + missing;

        return FrequencyAssignmentProblem.frequencySpan(assignment);
    }

    // -------- Utilities --------
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
