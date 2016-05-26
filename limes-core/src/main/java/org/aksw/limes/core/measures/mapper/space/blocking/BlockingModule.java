/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.aksw.limes.core.measures.mapper.space.blocking;

import org.aksw.limes.core.io.cache.Instance;

import java.util.ArrayList;

/**
 * @author ngonga
 */
public interface BlockingModule {
    public ArrayList<ArrayList<Integer>> getBlocksToCompare(ArrayList<Integer> blockId);

    public ArrayList<Integer> getBlockId(Instance a);

    public ArrayList<ArrayList<Integer>> getAllBlockIds(Instance a);

    public ArrayList<ArrayList<Integer>> getAllSourceIds(Instance a, String properties);
}
