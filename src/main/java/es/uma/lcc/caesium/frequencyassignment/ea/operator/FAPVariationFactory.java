package es.uma.lcc.caesium.frequencyassignment.ea.operator;

import java.util.List;

import es.uma.lcc.caesium.ea.operator.variation.VariationFactory;
import es.uma.lcc.caesium.ea.operator.variation.VariationOperator;

/**
 * User-defined factory for operators specific for the Frequency Assignment Problem (FAP).
 * @author ccottap
 * @version 1.0
 */
public class FAPVariationFactory extends VariationFactory {

	@Override
	public VariationOperator create (String name, List<String> pars) {
		VariationOperator op = null;
				
		switch (name.toUpperCase()) {

		default:
			op = super.create(name, pars);
		}
				
		return op;
	}

}
