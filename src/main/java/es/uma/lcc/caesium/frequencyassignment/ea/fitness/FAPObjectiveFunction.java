package es.uma.lcc.caesium.frequencyassignment.ea.fitness;

import java.util.Map;
import java.util.Set;

import es.uma.lcc.caesium.ea.base.Genotype;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;


/**
 * Interface for objective functions for the Frequency Assignment Problem.
 * @author ccottap
 * @version 1.0
 */
public interface FAPObjectiveFunction {
	/**
	 * Returns the problem instance
	 * @return the problem instance
	 */
	public FrequencyAssignmentProblem getProblemData();
	
	/**
	 * Returns the frequency assignment encoded in a genotype as
	 * the corresponding map from emitters to set of frequencies.
	 * @param g the genotype
	 * @return a map from emitters to set of frequencies
	 */
	public Map<String, Set<Integer>> genotype2map (Genotype g);
}
