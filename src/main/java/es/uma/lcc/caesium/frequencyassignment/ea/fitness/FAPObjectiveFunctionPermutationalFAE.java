package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.*;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.ea.fitness.PermutationalObjectiveFunction;
import es.uma.lcc.caesium.frequencyassignment.Emitter;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

/**
 * Minimum-span FAP objective using a permutation + FAE decoder.
 * Assumes Genotype stores an int[] permutation (0..n-1) with no duplicates.
 */
public class FAPObjectiveFunctionPermutationalFAE
        extends PermutationalObjectiveFunction
        implements FAPObjectiveFunction {

    private final FrequencyAssignmentProblem problem;
    private final String[] emitterIds;         // stable index -> emitter ID
    private final Map<String, Integer> id2pos; // emitter ID -> stable index
    private final int baseFrequency;

    public FAPObjectiveFunctionPermutationalFAE(FrequencyAssignmentProblem problem) {
        this(problem, 0);
    }

    public FAPObjectiveFunctionPermutationalFAE(FrequencyAssignmentProblem problem, int baseFrequency) {
        super(problem.numEmitters());
        this.problem = problem;
        this.baseFrequency = baseFrequency;

        List<String> ids = new ArrayList<>(problem.getEmitterNames());
        this.emitterIds = ids.toArray(new String[0]);
        this.id2pos = new HashMap<>();
        for (int i = 0; i < emitterIds.length; i++) {
            id2pos.put(emitterIds[i], i);
            setAlphabetSize(i, emitterIds.length);
        }
    }

    // ---------------- FAPObjectiveFunction ----------------
    @Override
    public FrequencyAssignmentProblem getProblemData() {
        return problem;
    }


    // ---------------- ObjectiveFunction ----------------
    @Override
    public OptimizationSense getOptimizationSense() {
        return OptimizationSense.MINIMIZATION;
    }


    private boolean canAssignFrequency(String id, int f, Map<String, Set<Integer>> assignment) {
        for (int f2 : assignment.get(id)) if (!problem.checkSeparation(f, f2, 0.0)) return false;
        for (String otherId : assignment.keySet()) {
            if (otherId.equals(id)) continue;
            double d = problem.getDistance(id, otherId);
            for (int f2 : assignment.get(otherId)) if (!problem.checkSeparation(f, f2, d)) return false;
        }
        return true;
    }

    // ---------------- Frequency-preserving crossover (FX) ----------------
    public int[] crossoverFX(int[] parent1, int[] parent2) {
        Map<String, Set<Integer>> A1 = decodeFAE(parent1);

        Map<Integer, LinkedHashSet<String>> freqGroups = new LinkedHashMap<>();
        for (var e : A1.entrySet()) {
            for (int f : e.getValue()) {
                freqGroups.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(e.getKey());
            }
        }

        int[] posP1 = positionIndex(parent1);
        int[] posP2 = positionIndex(parent2);

        List<Map.Entry<Integer, LinkedHashSet<String>>> orderedGroups =
                new ArrayList<>(freqGroups.entrySet());
        orderedGroups.sort(Comparator.comparingInt(e -> minPos(e.getValue(), posP1)));

        LinkedHashSet<String> childOrderIds = new LinkedHashSet<>();
        for (var group : orderedGroups) {
            List<String> members = new ArrayList<>(group.getValue());
            members.sort(Comparator.comparingInt(id -> posP2[id2pos.get(id)]));
            childOrderIds.addAll(members);
        }

        for (int p : parent2) childOrderIds.add(emitterIds[p]);

        int[] child = new int[emitterIds.length];
        int i = 0;
        for (String id : childOrderIds) child[i++] = id2pos.get(id);
        return child;
    }

    // ---------------- Helpers ----------------
    private int[] positionIndex(int[] perm) {
        int[] pos = new int[emitterIds.length];
        for (int i = 0; i < perm.length; i++) pos[perm[i]] = i;
        return pos;
    }

    private int minPos(Collection<String> ids, int[] pos) {
        int best = Integer.MAX_VALUE;
        for (String id : ids) best = Math.min(best, pos[id2pos.get(id)]);
        return best;
    }

    private int[] extractPermutation(Genotype g) {
        int n = g.length();
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) {
            perm[i] = (Integer) g.getGene(i); // safe cast, we know genes are Integers
        }
        return perm;
    }
    
    @Override
    public Map<String, Set<Integer>> genotype2map(Genotype g) {
        int[] perm = extractPermutation(g);
        return decodeFAE(perm);
    }

    @Override
    protected double _evaluate(Individual ind) {
        Map<String, Set<Integer>> assignment = genotype2map(ind.getGenome());
        return (double) FrequencyAssignmentProblem.frequencySpan(assignment);
    }

    // ---------------- Decoder: First-Available-Emitter (FAE) ----------------
    private Map<String, Set<Integer>> decodeFAE(int[] perm) {
        Map<String, Set<Integer>> assignment = new HashMap<>();
        for (String id : emitterIds) assignment.put(id, new LinkedHashSet<>());

        int remaining = problem.totalDemand();
        if (remaining == 0) return assignment;

        int f = baseFrequency;
        while (remaining > 0) {
            for (int idx = 0; idx < perm.length && remaining > 0; idx++) {
                String id = emitterIds[perm[idx]];
                Emitter e = problem.getEmitter(id);
                Set<Integer> freqs = assignment.get(id);
                if (freqs.size() >= e.demand()) continue;

                if (canAssignFrequency(id, f, assignment)) {
                    freqs.add(f);
                    remaining--;
                }
            }
            f++;
        }
        return assignment;
    }

	
}
