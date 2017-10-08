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
package plugins.plugins.trackers.bacteriaInMicrochannelTracker;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import static plugins.Plugin.logger;
import plugins.plugins.trackers.ObjectIdxTracker;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.getObjectSize;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.significativeSIErrorThld;
import static plugins.plugins.trackers.bacteriaInMicrochannelTracker.BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit;
import plugins.plugins.trackers.bacteriaInMicrochannelTracker.ObjectModifier.Split;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class RearrangeObjectsFromPrev extends ObjectModifier {
    protected List<RearrangeAssignment> assignements;
    protected final Assignment assignment;
    protected HashMapGetCreate<Object3D, Double> sizeMap = new HashMapGetCreate<>(o -> getObjectSize(o));
    public RearrangeObjectsFromPrev(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, int frame, Assignment assignment) { // idxMax included
        super(frame, frame, tracker);
        objects.put(frame, new ArrayList(assignment.nextObjects)); // new arraylist -> can be modified
        objects.put(frame-1, assignment.prevObjects);
        assignements = new ArrayList<>(assignment.objectCountPrev());
        this.assignment=assignment;
        for (Object3D o : assignment.prevObjects) {
            double[] sizeRange = new double[2];
            double si = tracker.sizeIncrementFunction.apply(o);
            double size = tracker.sizeFunction.apply(o);
            if (Double.isNaN(si)) {
                sizeRange[0] = tracker.minGR * size;
                sizeRange[1] = tracker.maxGR * size;
            } else {
                sizeRange[0] = (si-significativeSIErrorThld/2) * size;
                sizeRange[1] = (si+significativeSIErrorThld/2) * size;
            }
            assignements.add(new RearrangeAssignment(o, sizeRange));
        }
        // TODO: take into acount endo-of-channel
        // split phase
        RearrangeAssignment a = needToSplit();
        while(a!=null) {
            if (debugCorr) logger.debug("RO: assignments: {}", assignements);
            if (!a.split()) break;
            a = needToSplit();
        }
        // merge phase: merge until 2 objects per assignment & remove each merge cost to global cost
        if (a==null && needToMerge()) { 
            if (frame+1<tracker.maxT) { 
                TrackAssigner ta = tracker.getTrackAssigner(frame+1).setVerboseLevel(verboseLevelLimit);
                ta.assignUntil(assignment.getLastObject(false), true);
                // check that ta's has assignments included in current assignments
                if (debugCorr) logger.debug("RO: merge from next: current sizes: {}", Utils.toStringList(getObjects(frame), o->""+tracker.sizeFunction.apply(o)));
                if (debugCorr) logger.debug("RO: merge from next: current assignment: [{}->{}] assignment with next: {}", assignment.idxNext, assignment.idxNextEnd()-1, ta.toString());
                Assignment ass1 = ta.getAssignmentContaining(assignment.nextObjects.get(0), true);
                if (ta.currentAssignment.getLastObject(true)==assignment.getLastObject(false) && ass1.prevObjects.get(0)==assignment.nextObjects.get(0)) {
                    // functions for track assigner -> use prev object assigned to next object
                    Function<Object3D, Double> sizeIncrementFunction = o -> { 
                        RearrangeAssignment ra = getAssignement(o, false, false);
                        if (ra==null) return Double.NaN;
                        else return tracker.sizeIncrementFunction.apply(ra.prevObject);
                    };
                    BiFunction<Object3D, Object3D, Boolean> areFromSameLine = (o1, o2) -> {
                        RearrangeAssignment ra1 = getAssignement(o1, false, false);
                        if (ra1==null) return false;
                        RearrangeAssignment ra2 = getAssignement(o2, false, false);
                        if (ra2==null) return false;
                        return tracker.areFromSameLine.apply(ra1.prevObject, ra2.prevObject);
                    };
                    ta = new TrackAssigner(getObjects(frame), tracker.getObjects(frame+1).subList(ass1.idxNext, ta.currentAssignment.idxNextEnd()), tracker.baseGrowthRate, assignment.truncatedEndOfChannel(), tracker.sizeFunction, sizeIncrementFunction, areFromSameLine).setVerboseLevel(ta.verboseLevel);
                    ta.assignAll();
                    for (RearrangeAssignment ass : assignements) ass.mergeUsingNext(ta);
                }
            } 
            for (RearrangeAssignment ass : assignements) if (ass.objects.size()>2) ass.mergeUntil(2);
        }
        if (debugCorr) logger.debug("Rearrange objects: tp: {}, {}, cost: {}", timePointMax, assignment.toString(false), cost);
    }
    
    private boolean needToMerge() {
        for (RearrangeAssignment ass : assignements) if (ass.objects.size()>2) return true;
        return false;
    }

    private int getNextVoidAssignementIndex() {
        for (int i = 0; i<assignements.size(); ++i) if (assignements.get(i).isEmpty()) return i;
        return -1;
    }
    
    protected RearrangeAssignment getAssignement(Object3D o, boolean prev, boolean reset) {
        if (prev) return assignUntil(reset, (a, i) -> a.prevObject==o ? a : null);
        else return assignUntil(reset, (a, i) -> a.contains(o) ? a : null);
    }
          
    protected RearrangeAssignment needToSplit() { // assigns from start and check range size
        return assignUntil(true, (a, i) -> a.overSize() ? a : ((a.underSize() && i>0) ? assignements.get(i-1) : null)); // if oversize: return current, if undersize return previous
    }
        
    protected RearrangeAssignment assignUntil(boolean reset, BiFunction<RearrangeAssignment, Integer, RearrangeAssignment> exitFunction) { // assigns from start with custom exit function -> if return non null value -> exit assignment loop with value
        List<Object3D> allObjects = getObjects(timePointMax);
        if (reset) for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) assignements.get(rangeIdx).clear();
        int currentOIdx = 0;
        if (!reset) {
            int idx = getNextVoidAssignementIndex();
            if (idx>0) currentOIdx = allObjects.indexOf(assignements.get(idx-1).getLastObject());
        }
        for (int rangeIdx = 0; rangeIdx<assignements.size(); ++rangeIdx) {
            RearrangeAssignment cur = assignements.get(rangeIdx);
            if (cur.isEmpty()) {
                while(currentOIdx<allObjects.size() && cur.underSize()) assignements.get(rangeIdx).add(allObjects.get(currentOIdx++));
            }
            RearrangeAssignment a = exitFunction.apply(cur, rangeIdx);
            if (a!=null) return a;
        }
        return null;
    }

    @Override protected RearrangeObjectsFromPrev getNextScenario() { 
        return null;
    }

    @Override
    protected void applyScenario() {
        for (int i = this.assignment.idxNextEnd()-1; i>=assignment.idxNext; --i) tracker.objectAttributeMap.remove(tracker.populations.get(timePointMin).remove(i));
        List<Object3D> allObjects = getObjects(timePointMax);
        sortAndRelabel();
        int idx = assignment.idxNext;
        for (Object3D o : allObjects) {
            tracker.populations.get(timePointMin).add(idx, o);
            tracker.objectAttributeMap.put(o, tracker.new TrackAttribute(o, idx, timePointMax));
            idx++;
        }
        tracker.resetIndices(timePointMax);
    }
    
    public void sortAndRelabel() {
        List<Object3D> allObjects = getObjects(timePointMax);
        Collections.sort(allObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ));
        for (int i = 0; i<allObjects.size(); ++i) allObjects.get(i).setLabel(i+1);
    }
    
    @Override 
    public String toString() {
        return "Rearrange@"+timePointMax+"["+this.assignment.idxNext+";"+(assignment.idxNextEnd()-1)+"]/c="+cost;
    }
    
    protected class RearrangeAssignment {
        final List<Object3D> objects;
        final Object3D prevObject;
        final double[] sizeRange;
        double size;
        public RearrangeAssignment(Object3D prevObject, double[] sizeRange) {
            this.prevObject=prevObject;
            this.sizeRange=sizeRange;
            this.objects = new ArrayList<>(3);
        }
        public void add(Object3D o) {
            this.objects.add(o);
            this.size+=sizeMap.getAndCreateIfNecessary(o);
        }
        public boolean isEmpty() {
            return objects.isEmpty();
        }
        public boolean contains(Object3D o) {
            return objects.contains(o);
        }
        public void clear() {
            size=0;
            objects.clear();
        }
        public boolean overSize() {
            return size>sizeRange[1];
        }
        public boolean underSize() {
            return size<sizeRange[0];
        }
        public Object3D getLastObject() {
            if (objects.isEmpty()) return null;
            return objects.get(objects.size()-1);
        }
        public boolean split() { 
            TreeSet<Split> res = new TreeSet<>();
            for (Object3D o : objects) {
                Split s = getSplit(timePointMax, o);
                if (Double.isFinite(s.cost)) res.add(s);
            }
            if (res.isEmpty()) return false;
            Split s = res.first(); // lowest cost
            List<Object3D> allObjects = getObjects(timePointMax);
            if (debugCorr) logger.debug("RO: split: {}, cost: {}", allObjects.indexOf(s.source)+assignment.idxNext, s.cost);
            s.apply(objects);
            s.apply(getObjects(s.frame));
            cost+=s.cost;
            sortAndRelabel();
            return true;
        }
        
        public void mergeUsingNext(TrackAssigner assignments) {
            if (debugCorr) logger.debug("RO: merge using next: current: {} all assignments: {}", this, assignments);
            if (objects.size()<=1) return;
            Iterator<Object3D> it = objects.iterator();
            Object3D lastO = it.next();
            Assignment lastAss = assignments.getAssignmentContaining(lastO, true);
            boolean reset = false;
            while(it.hasNext()) {
                Object3D currentO = it.next();
                Assignment ass = assignments.getAssignmentContaining(currentO, true);
                if (ass!=null && ass == lastAss && (ass.objectCountNext()<ass.objectCountPrev())) {
                    Merge m = getMerge(timePointMax, new Pair(lastO, currentO));
                    if (debugCorr) logger.debug("RO: merge using next: cost: {} assignement containing objects {}", m.cost, ass);
                    if (true || Double.isFinite(m.cost)) {
                        m.apply(objects);
                        m.apply(getObjects(m.frame));
                        m.apply(ass.prevObjects); 
                        ass.ta.resetIndices(true); // merging modifies following prev indices
                        currentO = m.value;
                        reset = true;
                    }
                }
                if (reset) {
                    if (objects.size()<=1) return;
                    it = objects.iterator();
                    lastO = it.next();
                    lastAss = assignments.getAssignmentContaining(lastO, true);
                    reset = false;
                } else {
                    lastO = currentO;
                    lastAss = ass;
                }
                
            }
        }
        public void mergeUntil(int limit) {
            double additionalCost = Double.NEGATIVE_INFINITY;
            while(objects.size()>limit) { // critère merge = cout le plus bas. // TODO: inclure les objets du temps suivants dans les contraintes
                Merge m = getBestMerge();
                if (m!=null) {
                    m.apply(objects);
                    m.apply(getObjects(m.frame));
                    if (m.cost>additionalCost) additionalCost=m.cost;
                } else break;
            }
            if (Double.isFinite(additionalCost)) cost+=additionalCost;
        }
        private Merge getBestMerge() {
            sortAndRelabel();
            TreeSet<Merge> res = new TreeSet();
            Iterator<Object3D> it = objects.iterator();
            Object3D lastO = it.next();
            while (it.hasNext()) {
                Object3D currentO = it.next();
                Merge m = getMerge(timePointMax, new Pair(lastO, currentO));
                if (Double.isFinite(m.cost)) res.add(m);
                lastO = currentO;
            }
            if (res.isEmpty()) return null;
            else return res.first();
        }
        
        @Override public String toString() {
            return "RO:["+tracker.populations.get(timePointMax-1).indexOf(this.prevObject)+"]->["+(objects.isEmpty()? "" : getObjects(timePointMax).indexOf(objects.get(0))+";"+getObjects(timePointMax).indexOf(getLastObject()))+"]/size: "+size+"/cost: "+cost+ "/sizeRange: ["+this.sizeRange[0]+";"+this.sizeRange[1]+"]";
        }
        
        
    }

}
