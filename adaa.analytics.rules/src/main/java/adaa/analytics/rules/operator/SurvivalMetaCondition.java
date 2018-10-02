package adaa.analytics.rules.operator;

import adaa.analytics.rules.logic.representation.SurvivalRule;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.metadata.ExampleSetMetaData;
import com.rapidminer.parameter.ParameterHandler;
import com.rapidminer.parameter.conditions.ParameterCondition;

public class SurvivalMetaCondition extends ParameterCondition{

	protected Operator operator;
	
	public SurvivalMetaCondition(ParameterHandler parameterHandler, boolean becomeMandatory, Operator operator) {
		super(parameterHandler, becomeMandatory);
		this.operator = operator;
	}
	
	
	@Override
	public boolean isConditionFullfilled() {
		if (operator == null) {
			throw new IllegalAccessError("Invalid classification meta condition!");
		}
		
		// try learner operators
		InputPort port = operator.getInputPorts().getPortByName("training set");
		if (port == null) {
			port = operator.getInputPorts().getPortByName("labelled data");
		} 
				
		ExampleSetMetaData setMeta = (ExampleSetMetaData)port.getMetaData();
		boolean out =
				setMeta != null && 
				setMeta.getLabelMetaData() != null &&
				setMeta.getAttributeByRole(SurvivalRule.SURVIVAL_TIME_ROLE) != null;
		return out;	
	}

}
