package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.ea.base.Individual;
import es.uma.lcc.caesium.ea.fitness.DiscreteObjectiveFunction;
import es.uma.lcc.caesium.ea.fitness.OptimizationSense;
import es.uma.lcc.caesium.frequencyassignment.Emitter;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;

public class FAPObjectiveFunctionDirect extends DiscreteObjectiveFunction implements FAPObjectiveFunction {

	    /** The problem instance */
	    private FrequencyAssignmentProblem fap;

	    /**
	     * Constructor
	     * @param fap the problem instance
	     * @param numGenes number of variables in the genotype (depends on encoding)
	     * @param alphabetSize maximum frequency value that a gene can take
	     */
	    public FAPObjectiveFunctionDirect(FrequencyAssignmentProblem fap) {
	    	/*, int numGenes, int alphabetSize*/
	    	super(fap.numEmitters()*fap.maxFrequency() , 2);
	        this.fap = fap;
	    }

	    @Override
	    public FrequencyAssignmentProblem getProblemData() {
	        return fap;
	    }

	    /**
	     * Decodes a genotype into a map of emitter -> set of frequencies.
	     * NOTE: The actual decoding depends on how you encode emitters and frequencies in the genotype.
	     */
	    @Override
	    public Map<String, Set<Integer>> genotype2map(Genotype g) {
	        Map<String, Set<Integer>> assignment = new HashMap<>();

	        // For simplicity, assume genotype.length = totalDemand()
	        // and that genes are frequencies directly assigned to emitters in order.
	        int idx = 0;
	        for (String id : fap.getEmitterNames()) {
	            Emitter e = fap.getEmitter(id);
	            Set<Integer> freqs = new HashSet<>();
	            for (int d = 0; d < e.demand(); d++) {
	                int freq = (int)g.getGene(idx); // assume gene encodes frequency as integer
	                freqs.add(freq);
	                idx++;
	            }
	            assignment.put(id, freqs);
	        }

	        return assignment;
	    }

	    /**
	     * Evaluates the objective value of a genotype:
	     * - if infeasible, return a penalized score
	     * - otherwise, return the frequency span
	     */
	    public double evaluate(Genotype g) {
	        Map<String, Set<Integer>> assignment = genotype2map(g);
	        if (!fap.isFeasible(assignment)) {
	            // Penalization: infeasible solutions get large span
	            return Double.MAX_VALUE;
	        }
	        return FrequencyAssignmentProblem.frequencySpan(assignment);
	    }

		@Override
		public OptimizationSense getOptimizationSense() {
			return OptimizationSense.MINIMIZATION;
		}

		@Override
		protected double _evaluate(Individual i) {
			// TODO Auto-generated method stub
			return 0;
		}
}


