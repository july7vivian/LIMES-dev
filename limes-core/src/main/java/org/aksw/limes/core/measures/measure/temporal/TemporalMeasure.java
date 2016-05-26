package org.aksw.limes.core.measures.measure.temporal;

import org.aksw.limes.core.measures.measure.Measure;

public abstract class TemporalMeasure extends Measure implements ITemporalMeasure {
    /**
     * Extract first property (beginDate) from metric expression.
     *
     * @param expression,
     *         metric expression
     * @return first property of metric expression as string
     */
    public String getFirstProperty(String properties) throws IllegalArgumentException {
        int plusIndex = properties.indexOf("|");
        if (properties.indexOf("|") != -1) {
            String p1 = properties.substring(0, plusIndex);
            return p1;
        } else
            return properties;
    }

    /**
     * Extract second property (endDate or machineID) from metric expression.
     *
     * @param expression,
     *         the metric expression
     * @return second property of metric expression as string
     */
    public String getSecondProperty(String properties) throws IllegalArgumentException {
        int plusIndex = properties.indexOf("|");
        if (properties.indexOf("|") != -1) {
            String p1 = properties.substring(plusIndex + 1, properties.length());
            return p1;
        } else
            throw new IllegalArgumentException();
    }
}
