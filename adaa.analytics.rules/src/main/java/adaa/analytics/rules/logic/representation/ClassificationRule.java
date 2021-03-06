/*
 *  RuleKit
 *
 *  Copyright (C) 2019 by RuleKit Development Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package adaa.analytics.rules.logic.representation;

import adaa.analytics.rules.logic.induction.ContingencyTable;
import adaa.analytics.rules.logic.induction.Covering;

import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;

import java.util.Set;

/**
 * Class representing a classification rule.
 * @author Adam Gudys
 *
 */
public class ClassificationRule extends Rule {
	/** Serialization identifier. */
	private static final long serialVersionUID = -809625670611500594L;

	/**
	 * Creates empty classification rule.
	 */
	public ClassificationRule() {
		super();
	}
	
	/**
	 * Creates classification rule with a given premise and a consequence.
	 * @param premise Rule premise.
	 * @param consequence Rule consequence.
	 */
	public ClassificationRule(CompoundCondition premise, ElementaryCondition consequence) {
		super(premise, consequence);
	}

	/**
	 * Applies the rule on a part of a specified example set.
	 * @param set Example set.
	 * @param filterIds Set of identifiers to be examined.
	 * @return Information about covering.
	 */
	public Covering covers(ExampleSet set, Set<Integer> filterIds) {
		Covering covered = new Covering();
		
		for (int id : filterIds) {
			Example ex = set.getExample(id);
			double w = set.getAttributes().getWeight() == null ? 1.0 : ex.getWeight();
			
			boolean consequenceAgree = this.getConsequence().evaluate(ex);
			if (consequenceAgree) {
				covered.weighted_P += w;
			} else {
				covered.weighted_N += w;
			}
			
			if (this.getPremise().evaluate(ex)) {
				if (consequenceAgree) {
					covered.positives.add(id);
					covered.weighted_p += w;
				} else {
					covered.negatives.add(id);
					covered.weighted_n += w;
				}
			} 
		}
		return covered;
	}
	
	/**
	 * Applies the rule on a specified example set.
	 * @param set Example set.
	 * @return Information about covering.
	 */
	public Covering covers(ExampleSet set) {
		Covering covered = new Covering();
		covers(set, covered, covered.positives, covered.negatives);
		return covered;
	}
	
	/**
	 * Applies the rule on a specified example set.
	 * @param set Example set.
	 * @param ct Output contingency table.
	 * @param positives Output collection of covered positive ids.
	 * @param negatives Output collection of covered negative ids.
	 */
	public void covers(ExampleSet set, ContingencyTable ct, Set<Integer> positives, Set<Integer> negatives) {

		int id = 0;
		for (Example ex : set) {
			double w = set.getAttributes().getWeight() == null ? 1.0 : ex.getWeight();
			
			boolean consequenceAgree = this.getConsequence().evaluate(ex);
			if (consequenceAgree) {
				ct.weighted_P += w;
			} else {
				ct.weighted_N += w;
			}
			
			if (this.getPremise().evaluate(ex)) {
				if (consequenceAgree) {
					positives.add(id);
					ct.weighted_p += w;
				} else {
					negatives.add(id);
					ct.weighted_n += w;
				}
			}
			++id;
		}
	}
}
