package org.aksw.limes.core.measures.measure.temporal.allenAlgebra;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.aksw.limes.core.io.cache.Instance;
import org.aksw.limes.core.measures.measure.temporal.TemporalMeasure;

/**
 * Implements the temporal is met by measure class.
 *
 * @author Kleanthi Georgala (georgala@informatik.uni-leipzig.de)
 * @version 1.0
 */
public class IsMetByMeasure extends TemporalMeasure {

    // BE0
    /**
     * {@inheritDoc}
     */
    @Override
    public double getSimilarity(Object object1, Object object2) {
        double sim = 0;
        String split1[] = ((String) object1).split("\\|");
        String split2[] = ((String) object2).split("\\|");

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        Date date1, date2;
        long beginDate1 = 0, endDate2 = 0;
        try {
            date1 = df.parse(split1[0]);
            beginDate1 = date1.getTime();
        } catch (ParseException e) {
            System.err.println("Exiting..");
        }
        try {
            date2 = df.parse(split2[1]);
            endDate2 = date2.getTime();
        } catch (ParseException e) {
            System.err.println("Exiting..");
        }

        if (beginDate1 == endDate2)
            sim = 1;
        else
            sim = 0;

        return sim;
    }

    /**
     * Returns the similarity between two instances given their begin and end
     * dates. If the first instance has a begin date equal to the end date of
     * the second Instance, then their similarity is 1, and 0 otherwise.
     * 
     *
     * @return The similarity of the instances
     */
    @Override
    public double getSimilarity(Instance instance1, Instance instance2, String property1, String property2) {
        String endDate2 = null;
        endDate2 = this.getSecondProperty(property2);
        String beginDate1 = this.getFirstProperty(property1);

        String s1 = new String(instance1.getProperty(beginDate1).first());
        String s2 = new String("*" + "|" + instance2.getProperty(endDate2).first());

        return this.getSimilarity(s1, s2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getRuntimeApproximation(double mappingSize) {
        return mappingSize / 1000d;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "IsMetBy";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return "temporal";

    }
}
