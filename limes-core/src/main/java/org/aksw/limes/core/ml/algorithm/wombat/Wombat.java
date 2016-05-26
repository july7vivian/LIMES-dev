/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.limes.core.ml.algorithm.wombat;

import org.aksw.limes.core.datastrutures.GoldStandard;
import org.aksw.limes.core.datastrutures.Tree;
import org.aksw.limes.core.evaluation.qualititativeMeasures.Recall;
import org.aksw.limes.core.execution.engine.ExecutionEngine;
import org.aksw.limes.core.execution.engine.ExecutionEngineFactory;
import org.aksw.limes.core.execution.engine.ExecutionEngineFactory.ExecutionEngineType;
import org.aksw.limes.core.execution.engine.SimpleExecutionEngine;
import org.aksw.limes.core.execution.planning.plan.Instruction;
import org.aksw.limes.core.execution.planning.plan.Plan;
import org.aksw.limes.core.execution.planning.planner.ExecutionPlannerFactory;
import org.aksw.limes.core.execution.planning.planner.ExecutionPlannerFactory.ExecutionPlannerType;
import org.aksw.limes.core.execution.planning.planner.IPlanner;
import org.aksw.limes.core.execution.rewriter.Rewriter;
import org.aksw.limes.core.execution.rewriter.RewriterFactory;
import org.aksw.limes.core.execution.rewriter.RewriterFactory.RewriterFactoryType;
import org.aksw.limes.core.io.cache.Cache;
import org.aksw.limes.core.io.config.Configuration;
import org.aksw.limes.core.io.ls.LinkSpecification;
import org.aksw.limes.core.io.mapping.AMapping;
import org.aksw.limes.core.io.mapping.MappingFactory;
import org.aksw.limes.core.io.mapping.MappingFactory.MappingType;
import org.aksw.limes.core.ml.oldalgorithm.MLAlgorithm;
import org.apache.log4j.Logger;

import java.util.*;


/**
 * This class uses Least General Generalization (LGG) to learn Link Specifications
 *
 * @author sherif
 */
public abstract class Wombat extends MLAlgorithm {


    // Parameters
    protected static final String PARAMETER_MAX_REFINEMENT_TREE_SIZE = "max refinement tree size";
    protected static final String PARAMETER_MAX_ITERATIONS_NUMBER = "max iterations number";
    protected static final String PARAMETER_MAX_ITERATION_TIME_IN_M = "max iteration time in minutes";
    protected static final String PARAMETER_EXECUTION_TIME_IN_M = "max execution time in minutes";
    protected static final String PARAMETER_MAX_FITNESS_THRESHOLD = "max fitness threshold";
    protected static final String PARAMETER_MIN_PROPERTY_COVERAGE = "minimum properity coverage";
    protected static final String PARAMETER_PROPERTY_LEARNING_RATE = "properity learning rate";
    protected static long maxRefineTreeSize = 2000;
    protected static int maxIterationNumber = 3;
    protected static int maxIterationTimeInMin = 20;
    protected static int maxExecutionTimeInMin = 600;
    protected static double maxFitnessThreshold = 1;
    static Logger logger = Logger.getLogger(Wombat.class.getName());
    protected Set<String> wombatParameterNames = new HashSet<>();
    protected Tree<RefinementNode> refinementTreeRoot = null;
    protected double minPropertyCoverage = 0.4;
    protected double propertyLearningRate = 0.9;

    protected Map<String, Double> sourcePropertiesCoverageMap; //coverage map for latter computations
    protected Map<String, Double> targetPropertiesCoverageMap; //coverage map for latter computations

    protected Set<String> measures = new HashSet<>(Arrays.asList("jaccard", "trigrams", "cosine", "ngrams"));

    protected boolean verbose = false;

    protected AMapping reference;

    public Wombat(Cache sourceCache, Cache targetCache, Configuration configuration) {
        super(sourceCache, targetCache, configuration);
        wombatParameterNames.add(PARAMETER_MAX_REFINEMENT_TREE_SIZE);
        wombatParameterNames.add(PARAMETER_MAX_ITERATIONS_NUMBER);
        wombatParameterNames.add(PARAMETER_MAX_ITERATION_TIME_IN_M);
        wombatParameterNames.add(PARAMETER_EXECUTION_TIME_IN_M);
        wombatParameterNames.add(PARAMETER_MAX_FITNESS_THRESHOLD);
        wombatParameterNames.add(PARAMETER_MIN_PROPERTY_COVERAGE);
        wombatParameterNames.add(PARAMETER_PROPERTY_LEARNING_RATE);
    }

    ;

    /**
     * @return initial classifiers
     */
    public List<ExtendedClassifier> getAllInitialClassifiers() {
        logger.info("Geting all initial classifiers ...");
        List<ExtendedClassifier> initialClassifiers = new ArrayList<>();
        for (String p : sourcePropertiesCoverageMap.keySet()) {
            for (String q : targetPropertiesCoverageMap.keySet()) {
                for (String m : measures) {
                    ExtendedClassifier cp = getInitialClassifier(p, q, m);
                    //only add if classifier covers all entries
                    initialClassifiers.add(cp);
                }
            }
        }
        logger.info("Done computing all initial classifiers.");
        return initialClassifiers;
    }

    /**
     * Computes the atomic classifiers by finding the highest possible F-measure
     * achievable on a given property pair
     *
     * @param sourceCache
     *         Source cache
     * @param targetCache
     *         Target cache
     * @param sourceProperty
     *         Property of source to use
     * @param targetProperty
     *         Property of target to use
     * @param measure
     *         Measure to be used
     * @param reference
     *         Reference mapping
     * @return Best simple classifier
     */
    private ExtendedClassifier getInitialClassifier(String sourceProperty, String targetProperty, String measure) {
        double maxOverlap = 0;
        double theta = 1.0;
        AMapping bestMapping = MappingFactory.createMapping(MappingType.MEMORY_MAPPING);
        for (double threshold = 1d; threshold > minPropertyCoverage; threshold = threshold * propertyLearningRate) {
            AMapping mapping = executeAtomicMeasure(sourceProperty, targetProperty, measure, threshold);
            double overlap = new Recall().calculate(mapping, new GoldStandard(reference));
            if (maxOverlap < overlap) { //only interested in largest threshold with recall 1
                bestMapping = mapping;
                theta = threshold;
                maxOverlap = overlap;
                bestMapping = mapping;
            }
        }
        ExtendedClassifier cp = new ExtendedClassifier(measure, theta);
        cp.setfMeasure(maxOverlap);
        cp.sourceProperty = sourceProperty;
        cp.targetProperty = targetProperty;
        cp.setMapping(bestMapping);
        return cp;
    }

    /**
     * @param sourceCache
     *         cache
     * @param targetCache
     *         cache
     * @param sourceProperty
     * @param targetProperty
     * @param measure
     * @param threshold
     * @return Mapping from source to target resources after applying
     * the atomic mapper measure(sourceProperity, targetProperty)
     */
    public AMapping executeAtomicMeasure(String sourceProperty, String targetProperty, String measure, double threshold) {
        String measureExpression = measure + "(x." + sourceProperty + ", y." + targetProperty + ")";
        Instruction inst = new Instruction(Instruction.Command.RUN, measureExpression, threshold + "", -1, -1, -1);
        ExecutionEngine ee = ExecutionEngineFactory.getEngine(ExecutionEngineType.DEFAULT, sourceCache, targetCache, "?x", "?y");
        Plan plan = new Plan();
        plan.addInstruction(inst);
        return ((SimpleExecutionEngine) ee).executeInstructions(plan);
    }

    /**
     * Looks first for the input metricExpression in the already constructed tree,
     * if found the corresponding mapping is returned.
     * Otherwise, the SetConstraintsMapper is generate the mapping from the metricExpression.
     *
     * @param metricExpression
     * @return Mapping corresponding to the input metric expression
     * @author sherif
     */
    protected AMapping getMapingOfMetricExpression(String metricExpression) {
        AMapping map = null;
        if (RefinementNode.isSaveMapping()) {
            map = getMapingOfMetricFromTree(metricExpression, refinementTreeRoot);
        }
        if (map == null) {
            Double threshold = Double.parseDouble(metricExpression.substring(metricExpression.lastIndexOf("|") + 1, metricExpression.length()));
            Rewriter rw = RewriterFactory.getRewriter(RewriterFactoryType.DEFAULT);
            LinkSpecification ls = new LinkSpecification(metricExpression, threshold);
            LinkSpecification rwLs = rw.rewrite(ls);
            IPlanner planner = ExecutionPlannerFactory.getPlanner(ExecutionPlannerType.DEFAULT, sourceCache, targetCache);
            assert planner != null;
            ExecutionEngine engine = ExecutionEngineFactory.getEngine(ExecutionEngineType.DEFAULT, sourceCache, targetCache, "?x", "?y");
            assert engine != null;
            AMapping resultMap = engine.execute(rwLs, planner);
            map = resultMap.getSubMap(threshold);
        }
        return map;
    }

    /**
     * @param string
     * @return return mapping of the input metricExpression from the search tree
     * @author sherif
     */
    protected AMapping getMapingOfMetricFromTree(String metricExpression, Tree<RefinementNode> r) {
        if (r != null) {
            if (r.getValue().getMetricExpression().equals(metricExpression)) {
                return r.getValue().getMapping();
            }
            if (r.getchildren() != null && r.getchildren().size() > 0) {
                for (Tree<RefinementNode> c : r.getchildren()) {
                    AMapping map = getMapingOfMetricFromTree(metricExpression, c);
                    if (map != null && map.size() != 0) {
                        return map;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Set<String> parameters() {
        return wombatParameterNames;
    }

    public enum Operator {
        AND, OR, MINUS
    }
}
