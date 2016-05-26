package org.aksw.limes.core.ml.algorithm.lion.operator;

import org.aksw.limes.core.io.ls.LinkSpecification;

import java.util.Set;

/**
 * Interface for decreasing the threshold of atomic measures.
 *
 * @author Klaus Lyko
 */
public interface ThresholdDecreaser {
    /**
     * Applies threshold decreasing function at the LinkSpecification.
     *
     * @param spec
     * @return decreased threshold[0,1]
     */
    public Set<Double> decrease(LinkSpecification spec);
}
