package es.uma.lcc.caesium.frequencyassignment.ea;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Locale;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import es.uma.lcc.caesium.ea.base.EvolutionaryAlgorithm;
import es.uma.lcc.caesium.ea.config.EAConfiguration;
import es.uma.lcc.caesium.ea.statistics.EntropyDiversity;
import es.uma.lcc.caesium.frequencyassignment.FrequencyAssignmentProblem;
import es.uma.lcc.caesium.frequencyassignment.ea.operator.FAPVariationFactory;
import es.uma.lcc.caesium.frequencyassignment.ea.fitness.MinSpanFAPObjective;
import es.uma.lcc.caesium.frequencyassignment.ea.fitness.MinSpanFAPObjectiveFAE;
import es.uma.lcc.caesium.frequencyassignment.ea.fitness.MinSpanFAPObjectiveFAF;
/**
 * Class for testing the evolutionary algorithm for the Frequency Assignment Problem
 * @author 
 * @version 1.0
 */
public class RunEA4FAP {

	/**
	 * Main method
	 * @param args command-line arguments
	 * @throws FileNotFoundException if configuration file cannot be read 
	 * @throws JsonException if the configuration file is not correctly formatted
	 */
	public static void main(String[] args) throws FileNotFoundException, JsonException {
		if (args.length < 2) {
			System.out.println("Parameters: <algorithm-configuration> <problem-data>");
			System.exit(1);
		}
		
		FileReader reader = new FileReader(args[0] + ".json");
		EAConfiguration conf = new EAConfiguration((JsonObject) Jsoner.deserialize(reader));
		conf.setVariationFactory(new FAPVariationFactory());
		
		int numruns = conf.getNumRuns();
		System.out.println(conf);
		EvolutionaryAlgorithm myEA = new EvolutionaryAlgorithm(conf);
		
		FrequencyAssignmentProblem fap = new FrequencyAssignmentProblem(args[1] + ".fap");
		System.out.println(fap);
		
		// función objetivo
		if (args[0].contains("decoder")) {
			System.out.println("Using decoder (FAE)...");
			MinSpanFAPObjectiveFAE obj = new MinSpanFAPObjectiveFAE(fap);
			myEA.setObjectiveFunction(obj);
			for (int i=0; i<numruns; i++) {
				myEA.run();
				System.out.println ("Run " + i + ": " + 
									String.format(Locale.US, "%.2f", myEA.getStatistics().getTime(i)) + "s\t" +
									myEA.getStatistics().getBest(i).getFitness());
				System.out.println(myEA.getStatistics().getBest(i).getGenome());
				System.out.println(fap.formatFrequencyAssignment(((MinSpanFAPObjectiveFAE)obj).genotype2map(myEA.getStatistics().getBest(i).getGenome())));
				}
			 }
		else if (args[0].contains("decoder-faf")){
			System.out.println("Using decoder (FAF)...");
			MinSpanFAPObjectiveFAF obj = new MinSpanFAPObjectiveFAF(fap);
			myEA.setObjectiveFunction(obj);
			for (int i=0; i<numruns; i++) {
				myEA.run();
				System.out.println ("Run " + i + ": " + 
									String.format(Locale.US, "%.2f", myEA.getStatistics().getTime(i)) + "s\t" +
									myEA.getStatistics().getBest(i).getFitness());
				System.out.println(myEA.getStatistics().getBest(i).getGenome());
				System.out.println(fap.formatFrequencyAssignment(((MinSpanFAPObjectiveFAF)obj).genotype2map(myEA.getStatistics().getBest(i).getGenome())));
				}
		}
		else {
			System.out.println("Using direct...");
			MinSpanFAPObjective obj = new MinSpanFAPObjective(fap);
			myEA.setObjectiveFunction(obj);
			for (int i=0; i<numruns; i++) {
				myEA.run();
				System.out.println ("Run " + i + ": " + 
									String.format(Locale.US, "%.2f", myEA.getStatistics().getTime(i)) + "s\t" +
									myEA.getStatistics().getBest(i).getFitness());
				System.out.println(myEA.getStatistics().getBest(i).getGenome());
				System.out.println(fap.formatFrequencyAssignment(((MinSpanFAPObjective)obj).genotype2map(myEA.getStatistics().getBest(i).getGenome())));
				
				}
			}
		
		myEA.getStatistics().setDiversityMeasure(new EntropyDiversity());

		PrintWriter file = new PrintWriter(args[0] + "-stats-" + args[1] + ".json");
		file.print(myEA.getStatistics().toJSON().toJson());
		file.close();
	}
}
