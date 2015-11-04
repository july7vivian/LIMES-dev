package org.aksw.limes.core.execution.engine;

import java.util.ArrayList;
import java.util.List;

import org.aksw.limes.core.execution.engine.filter.LinearFilter;
import org.aksw.limes.core.execution.planning.plan.ExecutionPlan;
import org.aksw.limes.core.execution.planning.plan.Instruction;
import org.aksw.limes.core.execution.planning.plan.Instruction.Command;
import org.aksw.limes.core.execution.planning.plan.Plan;
import org.aksw.limes.core.io.cache.Cache;
import org.aksw.limes.core.io.mapping.MemoryMapping;
import org.aksw.limes.core.measures.mapper.AtomicMapper;
import org.aksw.limes.core.measures.mapper.SetOperations;

/**
 * Implements the default execution engine class. The idea is that the engine
 * gets a series of instructions in the form of an execution plan and runs these
 * instructions sequentially and returns a MemoryMemoryMapping.
 *
 * @author ngonga
 * @author kleanthi
 */
public class DefaultExecutionEngine extends ExecutionEngine {

    public DefaultExecutionEngine(Cache source, Cache target, String sourceVar, String targetVar) {
	super(source, target, sourceVar, targetVar);
	// TODO Auto-generated constructor stub
    }

    /**
     * Implementation of the execution of a plan. Be aware that this doesn't
     * executes complex Link Specifications.
     *
     * @param plan
     *            An execution plan
     * @return The MemoryMapping that results from running the plan
     */
    public MemoryMapping executeAtomic(ExecutionPlan plan) {
	buffer = new ArrayList<MemoryMapping>();
	if (plan.isEmpty()) {
	    logger.info("Plan is empty. Done.");
	    return new MemoryMapping();
	}

	List<Instruction> instructions = plan.getInstructionList();
	MemoryMapping m = new MemoryMapping();
	for (int i = 0; i < instructions.size(); i++) {
	    Instruction inst = instructions.get(i);
	    // get the index for writing the results
	    int index = inst.getResultIndex();

	    // first process the RUN operator
	    if (inst.getCommand().equals(Command.RUN)) {

		m = executeRun(inst);
	    } // runs the filter operator
	    else if (inst.getCommand().equals(Command.FILTER)) {
		m = executeFilter(inst, buffer.get(inst.getSourceMapping()));
	    } // runs set operations such as intersection,
	    else if (inst.getCommand().equals(Command.INTERSECTION)) {
		m = SetOperations.intersection(buffer.get(inst.getSourceMapping()),
			buffer.get(inst.getTargetMapping()));
	    } // union
	    else if (inst.getCommand().equals(Command.UNION)) {
		m = SetOperations.union(buffer.get(inst.getSourceMapping()), buffer.get(inst.getTargetMapping()));
	    } // diff
	    else if (inst.getCommand().equals(Command.DIFF)) {
		m = SetOperations.difference(buffer.get(inst.getSourceMapping()), buffer.get(inst.getTargetMapping()));
	    } // end of processing. Return the indicated MemoryMapping
	    else if (inst.getCommand().equals(Command.RETURN)) {
		logger.info("Reached return command. Returning results.");
		if (buffer.isEmpty()) {
		    return m;
		}
		if (index < 0) {
		    return buffer.get(buffer.size() - 1);
		} else {
		    return buffer.get(index);
		}
	    }

	    if (index < 0) {
		buffer.add(m);
	    } else {
		// add placeholders to ensure that the MemoryMapping can be placed
		// where the user wanted to have it
		while ((index + 1) > buffer.size()) {
		    buffer.add(new MemoryMapping());
		}
		buffer.set(index, m);
	    }
	}

	// just in case the return operator was forgotten.
	// Then we return the last MemoryMapping computed
	if (buffer.isEmpty()) {
	    return new MemoryMapping();
	} else {
	    return buffer.get(buffer.size() - 1);
	}

    }

    /**
     * Implements running the run operator. Assume atomic measures
     *
     * @param inst
     *            Instruction
     * @param source
     *            Source cache
     * @param target
     *            Target cache
     * @return MemoryMapping
     */
    public MemoryMapping executeRun(Instruction inst) {
	// get threshold

	double threshold = Double.parseDouble(inst.getThreshold());
	// generate correct mapper

	AtomicMapper mapper;
	if (inst.getMeasureExpression().startsWith("leven")) {
	    mapper = new EDJoin();
	} else if (inst.getMeasureExpression().startsWith("euclid")) {
	    mapper = new TotalOrderBlockingMapper();
	} else if (inst.getMeasureExpression().startsWith("jaro")) {
	    mapper = new JaroMapper();
	} else if (inst.getMeasureExpression().startsWith("qgrams")) {
	    mapper = new FastNGram();
	} else if (inst.getMeasureExpression().startsWith("hausdorff")
		|| inst.getMeasureExpression().startsWith("geomean")) {
	    mapper = new OrchidMapper();
	} else {
	    mapper = new PPJoinPlusPlus();
	}

	// run mapper
	return mapper.getMapping(source, target, sourceVariable, targetVariable, inst.getMeasureExpression(),
		threshold);
    }

    /**
     * Runs the filtering operator
     *
     * @param inst
     *            Instruction
     * @param input
     *            MemoryMapping that is to be filtered
     * @return Filtered MemoryMapping
     */
    private MemoryMapping executeFilter(Instruction inst, MemoryMapping input) {
	LinearFilter filter = new LinearFilter();
	return filter.filter(input, inst.getMeasureExpression(), Double.parseDouble(inst.getThreshold()), source,
		target, sourceVariable, targetVariable);
    }

    /**
     * Implements the intersection of MemoryMappings
     *
     * @param inst
     *            Instruction
     * @param m1
     *            First MemoryMapping
     * @param m2
     *            Second MemoryMapping
     * @return Intersection of m1 and m2
     */
    public MemoryMapping executeIntersection(Instruction inst, MemoryMapping m1, MemoryMapping m2) {
	return SetOperations.intersection(m1, m2);
    }

    public MemoryMapping executeUnion(Instruction inst, MemoryMapping m1, MemoryMapping m2) {
	return SetOperations.union(m1, m2);
    }

    /**
     * Runs a nested plan
     *
     * @param ExecutionPlan
     *            ExecutionPlan
     * @return MemoryMapping
     */
    public MemoryMapping execute(ExecutionPlan ExecutionPlan) {
	// empty nested plan contains nothing
	long begin = System.currentTimeMillis();

	MemoryMapping m = new MemoryMapping();
	if (ExecutionPlan.isEmpty()) {
	} // atomic nested plan just contain simple list of instructions
	else if (ExecutionPlan.isAtomic()) {

	    m = executeAtomic(ExecutionPlan);

	} // nested plans contain subplans, an operator for merging the results
	  // of the
	  // subplans and a filter for filtering the results of the subplan
	else {

	    // run all the subplans
	    m = execute(ExecutionPlan.subPlans.get(0));
	    MemoryMapping m2, result = m;
	    for (int i = 1; i < ExecutionPlan.subPlans.size(); i++) {
		m2 = execute(ExecutionPlan.subPlans.get(i));
		if (ExecutionPlan.operator.equals(Command.INTERSECTION)) {
		    result = SetOperations.intersection(m, m2);
		} // union
		else if (ExecutionPlan.operator.equals(Command.UNION)) {
		    result = SetOperations.union(m, m2);
		} // diff
		else if (ExecutionPlan.operator.equals(Command.DIFF)) {
		    result = SetOperations.difference(m, m2);
		} else if (ExecutionPlan.operator.equals(Command.XOR)) {

		    result = SetOperations.xor(m, m2);
		}
		
		m = result;
	    }
	    // only run filtering if there is a filter indeed, else simply
	    // return MemoryMapping
	    if (ExecutionPlan.filteringInstruction != null) {
		if (Double.parseDouble(ExecutionPlan.filteringInstruction.getThreshold()) > 0) {
		    m = executeFilter(ExecutionPlan.filteringInstruction, m);
		}
	    }
	}
	long end = System.currentTimeMillis();
	return m;
    }

}