package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;
import es.uma.lcc.caesium.ea.fitness.PermutationalObjectiveFunction; 

public class FAPObjectiveFunctionDirect extends PermutationalObjectiveFunction 
	implements FAPObjectiveFunction {

	private final 				FrequencyAssignmentProblem fap;
	private final 				List<String> emitterIds; // stable order of emitters
	private final int 			L;                   // frequency domain size (0..L-1)
	private final int 			genomeLen;           // = emitterIds.size() * L
	private static final double PENALTY = 1e6; // big-M penalty for infeasible decodes
	
	public FAPObjectiveFunctionDirect(FrequencyAssignmentProblem fap) {
	    // Constructor de la clase. 
		// Asumimos que las frecuencias comienzan desde 0 y terminan en la máxima frecuencia que se calcule.
	    super(fap.numEmitters() * (fap.maxFrequency() + 1));
	    this.fap 		= fap;
	    this.L 			= fap.maxFrequency() + 1;
	    // Emisores y longitud del genoma: frecuencia máxima + 1
	    this.emitterIds = new ArrayList<>(fap.getEmitterNames()); 
	    this.genomeLen 	= emitterIds.size() * L;
	}
	
	// Minimizar la cantidad de frecuencias
	public OptimizationSense getOptimizationSense() {
	    return OptimizationSense.MINIMIZATION;
	}
	
	public FrequencyAssignmentProblem getProblemData() {
	    return fap;
	}
	
	/**
	 * Greedy decode: walk the permutation; for each candidate (eid, freq),
	 * add it if demand not yet met and it respects all separations w.r.t.
	 * already-assigned frequencies.
	 */
	public Map<String, Set<Integer>> genotype2map(Genotype g) {
	    // Se inicia una asignación vacía
	    Map<String, Set<Integer>>    assignment = new HashMap<>();
	    for (String id : emitterIds) assignment.put(id, new TreeSet<>());
	
	    final int sepInternal = fap.getMinimumSeparation(0.0);
	
	    for (int pos = 0; pos < genomeLen; pos++) {
	        int geneVal = toInt(g.getGene(pos)); // value in 0..genomeLen-1 (permutation)
	        int eid = geneVal / L;
	        int freq = geneVal % L;
	
	        if (eid < 0 || eid >= emitterIds.size()) continue; // safety
	        String eId = emitterIds.get(eid);
	        Set<Integer> set = assignment.get(eId);
	
	        // Demand met? skip
	        if (set.size() >= fap.getEmitter(eId).demand()) continue;
	
	        // Same-emitter separation (|f1 - f2| >= sepInternal)
	        if (!okSameEmitter(set, freq, sepInternal)) continue;
	
	        // Neighbor separations vs. already-assigned neighbors
	        if (!okWithNeighbors(eId, freq, assignment)) continue;
	
	        set.add(freq);
	    }
	
	    return assignment;
	}
	
	protected double _evaluate(Individual ind) {
	    Map<String, Set<Integer>> assign = genotype2map(ind.getGenome());
	
	    // quick completeness check: every emitter must meet its demand
	    for (String id : emitterIds) {
	        if (assign.get(id).size() != fap.getEmitter(id).demand()) {
	            return PENALTY;
	        }
	    }
	
	    // full feasibility (uses exact distances and δ/σ from the instance)
	    if (!fap.isFeasible(assign)) {
	        return PENALTY;
	    }
	
	    // objective = span
	    return FrequencyAssignmentProblem.frequencySpan(assign);
	}
	
	// --- helpers ---
	
	private static int toInt(Object gene) {
	    return (gene instanceof Number)
	            ? ((Number) gene).intValue()
	            : Integer.parseInt(gene.toString());
	}
	
	private static boolean okSameEmitter(Set<Integer> set, int f, int sep) {
	    for (int existing : set) {
	        if (Math.abs(existing - f) < sep) return false; // allow equality
	    }
	    return true;
	}
	
	private boolean okWithNeighbors(String eId, int f, Map<String, Set<Integer>> assignment) {
	    for (String oId : emitterIds) {
	        if (oId.equals(eId)) continue;
	        double d = fap.getDistance(eId, oId);
	        int sep = fap.getMinimumSeparation(d);
	        if (sep <= 0) continue; // no constraint
	
	        for (int f2 : assignment.get(oId)) {
	            if (Math.abs(f - f2) < sep) return false;
	        }
	    }
	    return true;
	}
}

