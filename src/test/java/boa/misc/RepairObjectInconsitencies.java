/*
 * Copyright (C) 2016 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.misc;

import static boa.test_utils.TestUtils.logger;

import boa.gui.ManualCorrection;
import boa.core.Task;
import boa.configuration.experiment.Experiment;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import static boa.misc.RepairObjectInconsitencies.RepairMode.*;
import boa.utils.HashMapGetCreate;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class RepairObjectInconsitencies {

    public static enum RepairMode {
        CORRECT_ALL, ERASE_ONLY_IF_UNLINKED, NEVER_ERASE;
    }
    public static void main(String[] args) {
        String dbName = "boa_fluo160501";
        //String dbName = "boa_160501";
        MasterDAO mDAO = new Task(dbName).getDB();
        repair(mDAO, mDAO.getExperiment().getPositionsAsString()[1], 1, NEVER_ERASE);
        repair(mDAO, mDAO.getExperiment().getPositionsAsString()[3], 1, NEVER_ERASE);
    }
    
    public static void repair(MasterDAO db, String positionName, int structureIdx, RepairMode r) {
        if (r==null) r=NEVER_ERASE;
        ObjectDAO dao = db.getDao(positionName);
        List<StructureObject> toDelete = new ArrayList<StructureObject>();
        List<StructureObject> uncorrected = new ArrayList<StructureObject>();
        int collapseCount = 0;
        for (StructureObject root : dao.getRoots()) {
            Map<StructureObject, List<StructureObject>> oByParent = StructureObjectUtils.splitByParent(root.getChildren(structureIdx));
            for (List<StructureObject> l : oByParent.values()) {
                //Map<Integer, List<StructureObject>> collapseMap = StructureObjectUtils.splitByIdx(l);
                Map<?, Set<StructureObject>> collapseMap = splitByCenter(l, 0.25);
                for (Collection<StructureObject> list : collapseMap.values()) {
                    repair(list, r, toDelete, uncorrected);
                    if (list.size()>1) ++collapseCount;
                }
                
            }
        }
        
        if (!uncorrected.isEmpty()) {
            int posIdx = dao.getExperiment().getPosition(positionName).getIndex();
            Map<StructureObject, List<StructureObject>> uncorrByParentTH = StructureObjectUtils.splitByParentTrackHead(uncorrected);
            for (StructureObject parentTh : uncorrByParentTH.keySet()) {
                String selectionName = "P:"+posIdx+"_objectError_ParentIdx:"+parentTh.getIdx();
                Selection sel = db.getSelectionDAO().getOrCreate(selectionName, false);
                sel.addElements(uncorrByParentTH.get(parentTh));
                sel.setIsDisplayingObjects(true);
                sel.setIsDisplayingTracks(true);
                db.getSelectionDAO().store(sel);
            }
        }
        
        if (!toDelete.isEmpty()) ManualCorrection.deleteObjects(db, toDelete, false);
        logger.debug("Position:{}, total collapse: {}, deleted objects: {}, uncorrected: {}", positionName, collapseCount, toDelete.size(), uncorrected.size());
    }
    
    private static void repair(Collection<StructureObject> collapsingObjects, RepairMode r, Collection<StructureObject> toDelete, Collection<StructureObject> uncorrected) {
        if (collapsingObjects.size()<=1) return;
        if (r==CORRECT_ALL) {
            List<StructureObject> collapsingObjectList = collapsingObjects instanceof List ? (List)collapsingObjects : new ArrayList(collapsingObjects);
            toDelete.addAll(collapsingObjectList.subList(1, collapsingObjects.size()));
        }
        else if (r==ERASE_ONLY_IF_UNLINKED) {
            Set<StructureObject> unlinked = new HashSet<StructureObject>(collapsingObjects.size());
            for (StructureObject o : collapsingObjects) {
                if (o.getPrevious()==null && o.getNext()==null) unlinked.add(o);
            }
            if (unlinked.size()<collapsingObjects.size()) {
                toDelete.addAll(unlinked);
                collapsingObjects.removeAll(unlinked);
                if (collapsingObjects.size()>1) repair(collapsingObjects, NEVER_ERASE, toDelete, uncorrected);
            } else repair(collapsingObjects, CORRECT_ALL, toDelete, uncorrected);
        } else if (r==NEVER_ERASE) uncorrected.addAll(collapsingObjects);
    }
    
    public static Map<RealPoint, Set<StructureObject>> splitByCenter(List<StructureObject> list, double pixelDistanceError) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        double scale = list.get(0).getScaleXY();
        double scaledDistanceSqError = pixelDistanceError*pixelDistanceError * scale * scale;
        List<RealPoint> points = new ArrayList<RealPoint>(list.size());
        for (StructureObject o : list) points.add(new RealPoint(o.getObject().getGeomCenter(true)));
        KDTree<StructureObject> kdTree = new KDTree<StructureObject>(list, points);
        RadiusNeighborSearchOnKDTree<StructureObject> search = new RadiusNeighborSearchOnKDTree(kdTree);
        HashMapGetCreate<RealPoint, Set<StructureObject>> res = new HashMapGetCreate<RealPoint, Set<StructureObject>>(new HashMapGetCreate.SetFactory<RealPoint, StructureObject>());
        Set<StructureObject> remainingObjects = new HashSet<StructureObject>(list);
        while(!remainingObjects.isEmpty()) {
            StructureObject o = remainingObjects.iterator().next();
            remainingObjects.remove(o);
            int i = list.indexOf(o);
            search.search(points.get(i), scaledDistanceSqError, false);
            Set<StructureObject> l = res.getAndCreateIfNecessary(points.get(i));
            for (int j = 0; j<search.numNeighbors(); ++j) {
                StructureObject o2 = search.getSampler(j).get();
                remainingObjects.remove(o2);
                l.add(o2);
            }
        }
        return res;
    }
    
}