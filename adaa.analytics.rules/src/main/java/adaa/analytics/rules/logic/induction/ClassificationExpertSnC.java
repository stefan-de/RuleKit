/*******************************************************************************
 * Copyright (C) 2019 RuleKit Development Team
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/
package adaa.analytics.rules.logic.induction;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;

import adaa.analytics.rules.logic.representation.ClassificationRule;
import adaa.analytics.rules.logic.representation.ClassificationRuleSet;
import adaa.analytics.rules.logic.representation.CompoundCondition;
import adaa.analytics.rules.logic.representation.ElementaryCondition;
import adaa.analytics.rules.logic.representation.Knowledge;
import adaa.analytics.rules.logic.representation.Logger;
import adaa.analytics.rules.logic.representation.Rule;
import adaa.analytics.rules.logic.representation.SingletonSet;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.NominalMapping;
import com.rapidminer.tools.container.Pair;

/**
 * User-guided separate'n'conquer algorithm for generating classification rule sets.
 * 
 * @author Adam Gudys
 *
 */
public class ClassificationExpertSnC extends ClassificationSnC {

	/**
	 * User's knowledge.
	 */
	protected Knowledge knowledge;
	
	/**
	 * Invokes base class constructor, creates factory for generating 
	 * 
	 * @param finder
	 * @param params
	 * @param knowledge
	 */
	public ClassificationExpertSnC(ClassificationFinder finder, InductionParameters params, Knowledge knowledge) {
		super(finder, params);
		factory = new RuleFactory(RuleFactory.CLASSIFICATION, true, params, knowledge);
		this.knowledge = (Knowledge)SerializationUtils.clone(knowledge);
	}
	
	@Override
	public ClassificationRuleSet run(ExampleSet dataset)
	{
		Logger.log("ClassificationExpertSnC.run()\n", Level.FINE);
		double beginTime;
		beginTime = System.nanoTime();
		
		ClassificationRuleSet ruleset = (ClassificationRuleSet)factory.create(dataset);
		Attribute label = dataset.getAttributes().getLabel();
		NominalMapping mapping = label.getMapping();
		
		int totalExpertRules = 0;
		int totalAutoRules = 0;
		
		double defaultClassWeight = 0;
		
		// iterate over all classes
		for (int classId = 0; classId < mapping.size(); ++classId) {
			
			Set<Integer> uncoveredPositives = new HashSet<Integer>();
			Set<Integer> uncovered = new HashSet<Integer>();
			double weighted_P = 0;
			double weighted_N = 0;
			
			// at the beginning rule set does not cover any examples
			for (int id = 0; id < dataset.size(); ++id) {
				Example e = dataset.getExample(id);
				double w = dataset.getAttributes().getWeight() == null ? 1.0 : e.getWeight();
				
				if ((double)e.getLabel() == classId) {
					weighted_P += w;
					uncoveredPositives.add(id);
				} else {
					weighted_N += w;
				}
				uncovered.add(id);
			}
			
			// change default class if necessary
			if (weighted_P > defaultClassWeight) {
				defaultClassWeight = weighted_P;
				ruleset.setDefaultClass(classId);
			}
			
			Knowledge classKnowledge = null;
			
			// if no expert rules and conditions specified
			if (knowledge.getRules(classId).size() == 0 
					&& knowledge.getPreferredConditions(classId).size() == 0
					&& knowledge.getPreferredAttributes(classId).size() == 0
					&& knowledge.getForbiddenAttributes(classId).size() == 0
					&& knowledge.getForbiddenAttributes(classId).size() == 0) {
				
				if (knowledge.isConsiderOtherClasses()) {
					// if flag specified allow induction for non-expert rules
					classKnowledge = (Knowledge) SerializationUtils.clone(knowledge);
					classKnowledge.setInduceUsingAutomatic(true);
				} else {
					continue;
				}
				
			} else {
				classKnowledge = knowledge;
				
			}
			
			// add expert rules to the ruleset and try to refine them
			Logger.log("Processing expert rules...\n", Level.FINE);
			for (Rule r : knowledge.getRules(classId)) {
				Rule rule = (Rule) SerializationUtils.clone(r);
				rule.setWeighted_P(weighted_P);
				rule.setWeighted_N(weighted_N);
				
				ClassificationExpertFinder erf = (ClassificationExpertFinder)finder;
				
				erf.adjust(rule, dataset, uncoveredPositives);
				
				Covering cov = rule.covers(dataset, uncovered);
				rule.setCoveringInformation(cov);
				Pair<Double,Double> qp = finder.calculateQualityAndPValue(dataset, cov, params.getVotingMeasure());
				rule.setWeight(qp.getFirst());
				rule.setPValue(qp.getSecond());
				Logger.log("Expert rule: " + rule.toString() + "\n", Level.FINE);
				
				erf.setKnowledge(classKnowledge);
				double t = System.nanoTime();
				finder.grow(rule, dataset, uncoveredPositives);
				ruleset.setGrowingTime( ruleset.getGrowingTime() + (System.nanoTime() - t) / 1e9);
				
				if (params.isPruningEnabled()) {
					Logger.log("Before prunning: " + rule.toString() + "\n" , Level.FINE);
					t = System.nanoTime();
					finder.prune(rule, dataset);
					ruleset.setPruningTime( ruleset.getPruningTime() + (System.nanoTime() - t) / 1e9);
				}
				Logger.log(".", Level.INFO);
				Logger.log("Candidate rule:" + rule.toString() + "\n", Level.FINE);
				
				ruleset.addRule(rule);
				Logger.log( "\r" + StringUtils.repeat("\t", 10) + "\r", Level.INFO);
				Logger.log("\t" + (++totalExpertRules) + " expert rules, " + totalAutoRules + " auto rules" , Level.INFO);
				
				cov = rule.covers(dataset, uncovered);
				
				// remove covered examples
				uncoveredPositives.removeAll(cov.positives);
				uncovered.removeAll(cov.positives);
				uncovered.removeAll(cov.negatives);
			}
			
			// try to generate new rules
			Logger.log("Processing other rules...\n", Level.FINE);
			boolean carryOn = uncoveredPositives.size() > 0; 
			while (carryOn) {
				Rule rule = new ClassificationRule(
						new CompoundCondition(),
						new ElementaryCondition(label.getName(), new SingletonSet((double)classId, mapping.getValues())));
				
				rule.setWeighted_P(weighted_P);
				rule.setWeighted_N(weighted_N);
				
				ClassificationExpertFinder erf = (ClassificationExpertFinder)finder;
				erf.setKnowledge(classKnowledge);
				double t = System.nanoTime();
				carryOn = (finder.grow(rule, dataset, uncoveredPositives) > 0);
				ruleset.setGrowingTime( ruleset.getGrowingTime() + (System.nanoTime() - t) / 1e9);
				
				if (carryOn) {
					if (params.isPruningEnabled()) {
						Logger.log("Before prunning: " + rule.toString() + "\n" , Level.FINE);
						t = System.nanoTime();
						finder.prune(rule, dataset);
						ruleset.setPruningTime( ruleset.getPruningTime() + (System.nanoTime() - t) / 1e9);
					}
					Logger.log("Candidate rule:" + rule.toString() + "\n", Level.FINE);
					Logger.log(".", Level.INFO);
					
					Covering covered = rule.covers(dataset, uncovered);
					
					// remove covered examples
					int previouslyUncovered = uncoveredPositives.size();
					uncoveredPositives.removeAll(covered.positives);
					uncovered.removeAll(covered.positives);
					uncovered.removeAll(covered.negatives);
					
					// stop if no positive examples remaining
					if (uncoveredPositives.size() == 0) {
						carryOn = false;
					}
					
					// stop and ignore last rule if no new positive examples covered
					if (uncoveredPositives.size() == previouslyUncovered) {
						carryOn = false; 
					} else {
						ruleset.addRule(rule);
						Logger.log( "\r" + StringUtils.repeat("\t", 10) + "\r", Level.INFO);
						Logger.log("\t" + totalExpertRules + " expert rules, " + (++totalAutoRules) + " auto rules" , Level.INFO);
					}
				}
			}
		}
			
		ruleset.setTotalTime((System.nanoTime() - beginTime) / 1e9);
		return ruleset;
	}
	

}
