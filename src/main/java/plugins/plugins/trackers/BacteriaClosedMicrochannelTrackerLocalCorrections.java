/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.trackers;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import measurement.GeometricalMeasurements;
import static plugins.Plugin.logger;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.plugins.segmenters.BacteriaFluo;
import static plugins.plugins.trackers.ObjectIdxTracker.getComparatorObject3D;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaClosedMicrochannelTrackerLocalCorrections implements TrackerSegmenter {
    
    // parametrization-related attributes
    protected PluginParameter<SegmenterSplitAndMerge> segmenter = new PluginParameter<SegmenterSplitAndMerge>("Segmentation algorithm", SegmenterSplitAndMerge.class, false);
    BoundedNumberParameter maxGrowthRate = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, null);
    BoundedNumberParameter minGrowthRate = new BoundedNumberParameter("Minimum growth rate", 2, 0.9, 0.01, null);
    BoundedNumberParameter divisionCriterion = new BoundedNumberParameter("Division Criterion", 2, 0.80, 0.01, 1);
    BoundedNumberParameter costLimit = new BoundedNumberParameter("Correction: operation cost limit", 3, 1, 0, null);
    BoundedNumberParameter cumCostLimit = new BoundedNumberParameter("Correction: cumulative cost limit", 3, 5, 0, null);
    Parameter[] parameters = new Parameter[]{segmenter, divisionCriterion, minGrowthRate, maxGrowthRate, costLimit, cumCostLimit};

    public Segmenter getSegmenter() {
        return segmenter.instanciatePlugin();
    }

    // tracking-related attributes
    private enum Flag {error, correctionMerge, correctionSplit;}
    List<Object3D>[] populations;
    //SegmenterSplitAndMerge[] segmenters;
    private boolean segment, correction;
    Image[] inputImages;
    ArrayList<TrackAttribute>[] trackAttributes;
    List<StructureObject> parents;
    int structureIdx;
    double maxGR, minGR, div, costLim, cumCostLim;
    final static int maxCorrectionLength = 10;
    PreFilterSequence preFilters; 
    PostFilterSequence postFilters;
    
    static int loopLimit=3;
    public static boolean debug=false, debugCorr=false;
    
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections() {}
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections(SegmenterSplitAndMerge segmenter, double divisionCriterion, double minGrowthRate, double maxGrowthRate, double costLimit, double cumulativeCostLimit) {
        this.segmenter.setPlugin(segmenter);
        this.maxGrowthRate.setValue(maxGrowthRate);
        this.minGrowthRate.setValue(minGrowthRate);
        this.divisionCriterion.setValue(divisionCriterion);
        this.costLimit.setValue(costLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
    }
    
    public BacteriaClosedMicrochannelTrackerLocalCorrections setCostParameters(double operationCostLimit, double cumulativeCostLimit) {
        this.costLimit.setValue(operationCostLimit);
        this.cumCostLimit.setValue(cumulativeCostLimit);
        return this;
    }
    
    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        init(parentTrack, structureIdx, false);
        segmentAndTrack(false);
    }

    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        this.preFilters=preFilters;
        this.postFilters=postFilters;
        init(parentTrack, structureIdx, true);
        segmentAndTrack(true);
    }
    
    public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, boolean performCorrection) {
        init(parentTrack, structureIdx, true);
        segmentAndTrack(performCorrection);
    }
    public static boolean correctionStep;
    public static List<List<StructureObject>> stepParents;
    protected void segmentAndTrack(boolean performCorrection) {
        if (performCorrection && correctionStep) {
            stepParents = new ArrayList<>();
        }
        this.correction=performCorrection;
        if (correction) inputImages=new Image[populations.length];
        // 1) assign all
        for (int t = 1; t<populations.length; ++t) assignPrevious(t, false);
        // 2) perform corrections by idx 

        if (correctionStep) step();
        int idxMax=0;
        int idxLim = populations[0].size();
        int[] tRange = new int[]{1, populations.length-1};
        while(idxMax<idxLim) {
            tRange = performCorrectionsByIdx(tRange[0], tRange[1], idxMax);
            if (tRange[0]<tRange[1] && idxMax>0) { // corrections have been performed : run correction from 0 to idxMax within time range
                int nLoop=1;
                for (int idx = 0; idx<=idxMax; ++idx) {
                    int[] tRange2 = performCorrectionsByIdx(tRange[0], tRange[1], idx);
                    if (tRange2[0]<tRange[0]) tRange[0] = tRange2[0];
                    if (tRange2[1]>tRange[1]) tRange[1] = tRange2[1];
                    if (idx>0 && tRange2[0]<tRange2[1] && nLoop<=loopLimit) { // corrections have been performed : reset idx
                        idx = 0;
                        nLoop++;
                    } 
                }
            }
            idxMax++;
            tRange = new int[]{1, populations.length-1};
        }
        applyLinksToParents(parents);
    }
    
    private int[] performCorrectionsByIdx(int tMin, int tMax, int idx) {
        if (debugCorr) logger.debug("performing corrections [{};{}] @ {}", tMin, tMax, idx);
        int nLoop=1;
        int currentT = tMin;
        int globalMinT = tMax;
        int globalMaxT = tMin;
        while(currentT<=tMax) {
            int minT = currentT;
            if (idx<populations[currentT].size()) {
                TrackAttribute ta = getAttribute(currentT, idx);
                CorLoop : while (ta.nPrev>1 && nLoop<=loopLimit) { // il y a une erreur à corriger (type nPrev>1)
                    TrackAssigner assigner = new TrackAssigner(currentT);
                    while (assigner.nextTrack() && assigner.idxEnd<=idx){} // idx > idxEnd 
                    if (assigner.canBeCorrected()) {
                        int corrT = assigner.performCorrection();
                        if (corrT>=1) {
                            minT = Math.min(minT, corrT);
                            if (corrT<=currentT) {
                                globalMinT = Math.min(globalMinT, corrT);
                                globalMaxT = Math.max(globalMaxT, currentT);
                                for (int t = corrT; t<=currentT; ++t) assignPrevious(t, false);
                            } else {
                                globalMinT = Math.min(globalMinT, currentT);
                                globalMaxT = Math.max(globalMaxT, corrT);
                                for (int t = currentT; t<=Math.min(populations.length-1, corrT+1); ++t) assignPrevious(t, false);
                            }
                            if (correctionStep) {
                                step();
                                //if (stepParents.size()>=4) return;
                            }
                        }
                    } else break CorLoop;
                    nLoop++;
                }
            }
            if (minT<currentT) currentT=minT;
            else ++currentT;
        }
        if (globalMaxT>=populations.length-1) globalMaxT = populations.length-1;
        if (debugCorr) logger.debug("out range for @ {}: [{};{}]", idx, globalMinT, globalMaxT);
        return new int[]{globalMinT, globalMaxT};
    }
    
    /*protected void segmentAndTrack2(boolean performCorrection) {
        if (performCorrection && correctionStep) {
            stepParents = new ArrayList<>();
        }
        this.correction=performCorrection;
        if (correction) inputImages=new Image[populations.length];
        int currentTimePoint = 1;
        int maxTimePoint = 1;
        int nLoop = 0;
        // TODO: assgin all (multithread), record windows with errors within trackAttributes OR create a list (problem: si nPrev>1 & nCur>2 -> comment faire l'assignement). Then perform corrections
        if (correctionStep) step();
        while(currentTimePoint<populations.length) {
            int corr = assignPrevious(currentTimePoint, performCorrection);
            if (corr==-1) {
                ++currentTimePoint;
                if (currentTimePoint%100==0) logger.debug("tp: {}", currentTimePoint);
            } else {
                if (corr==0) corr=1;
                if (corr>currentTimePoint) corr=currentTimePoint; // TODO REVOIR
                if (correctionStep) {
                    step();
                    //if (stepParents.size()>=6) return;
                }
                ++nLoop;
                if (debugCorr) logger.debug("t: {}, correction performed return to: {}, loop: {}", currentTimePoint, corr, nLoop);
                if (nLoop>=loopLimit) {
                    logger.warn("BCMTLC: loop limit exceded, performing assignment without correction from t: {} to: {}", corr, maxTimePoint);
                    for (currentTimePoint = corr; currentTimePoint<=maxTimePoint; ++currentTimePoint) assignPrevious(currentTimePoint, false);
                    currentTimePoint = maxTimePoint+1;
                    nLoop=0;
                } else currentTimePoint = corr;
            }
            if (maxTimePoint<currentTimePoint) { // when max timePoint in reached -> reset counter
                maxTimePoint=currentTimePoint;
                freeMemoryUntil(currentTimePoint-maxCorrectionLength);
                nLoop=0;
            }
        }
        
        // apply to structureObject
        applyLinksToParents(parents);
        
    }*/
    private void step() {
        List<StructureObject> newParents = new ArrayList<StructureObject>(parents.size());
        for (StructureObject p : parents) newParents.add(p.duplicate());
        stepParents.add(newParents);
        // perform assignment without corrections
        logger.debug("perform assignement without corrections");
        //for (int currentTimePoint = 1; currentTimePoint<populations.length; ++currentTimePoint) assignPrevious(currentTimePoint, false); 
        applyLinksToParents(newParents);
        logger.debug("step: {}", stepParents.size());
    }
    private void applyLinksToParents(List<StructureObject> parents) {
        List<StructureObject> childrenPrev = null;
        List<StructureObject> children = null;
        int errors = 0;
        for (int t = 0; t<populations.length; ++t) {
            StructureObject parent = parents.get(t);
            //logger.debug("setting objects from parent: {}, prevChildren null?", parent, childrenPrev==null);
            if (!segment) { // modifiy existing structureObjects
                children = parent.getChildren(structureIdx);
                if (children ==null || populations[t]==null) {}
                else if (children.size()!=populations[t].size()) logger.error("BCMTLC: error @ parent: {}, children and tracker objects differ in number", parent);
                else setAttributes(t, children, childrenPrev);
            } else { // creates new structureObjects
                List<Object3D> cObjects;
                if (correctionStep) {
                    cObjects = new ArrayList<>(populations[t].size());
                    for (Object3D o : populations[t]) cObjects.add(o.duplicate());
                } else cObjects = populations[t];
                children = parent.setChildrenObjects(new ObjectPopulation(cObjects, null), structureIdx); // will translate all voxels
                setAttributes(t, children, childrenPrev);
                if (debug) for (StructureObject c : children) if (c.getTrackFlag()==StructureObject.TrackFlag.trackError) ++errors;
            }
            childrenPrev=children;
        }
        if (debug) logger.debug("Errors: {}", errors);
    }
    
    private void freeMemoryUntil(int timePoint) {
        /**if (segmenters==null) return;
        --timePoint;
        if (timePoint<0) return;
        while(true) {
            segmenters[timePoint--]=null;
            if (timePoint<0 || segmenters[timePoint]==null) return;
        }**/
        if (inputImages==null) return;
        --timePoint;
        if (timePoint<0) return;
        while(true) {
            inputImages[timePoint--]=null;
            if (timePoint<0 || inputImages[timePoint]==null) return;
        }
    }
    private void setAttributes(int timePoint, List<StructureObject> children, List<StructureObject> childrenPrev) {
        for (int i = 0; i<children.size(); ++i) {
            TrackAttribute ta= getAttribute(timePoint, i);
            if (ta.prev==null || childrenPrev==null) children.get(i).resetTrackLinks();
            //else children.get(i).setPreviousInTrack(childrenPrev.get(ta.ta.idx), ta.trackHead, getFlag(ta.flag));
            else {
                childrenPrev.get(ta.prev.idx).setTrackLinks(children.get(i), true, !ta.trackHead, getFlag(ta));
            }
        }
    }
    
    private TrackFlag getFlag(TrackAttribute ta) {
        if (ta==null) return null;
        if (ta.errorPrev || ta.errorCur) return TrackFlag.trackError;
        if (ta.flag==Flag.correctionSplit) return TrackFlag.correctionSplit;
        if (ta.flag==Flag.correctionMerge) return TrackFlag.correctionMerge;
        return null;
    }
    
    protected void init(List<StructureObject> parentTrack, int structureIdx, boolean segment) {
        if (preFilters==null) this.preFilters=new PreFilterSequence("");
        if (postFilters==null) this.postFilters=new PostFilterSequence("");
        this.segment=segment;
        this.parents=parentTrack;
        int timePointNumber = parentTrack.size();
        trackAttributes = (ArrayList<TrackAttribute>[])new ArrayList[timePointNumber];
        populations = (ArrayList<Object3D>[])new ArrayList[timePointNumber];
        //if (segment) segmenters  = new SegmenterSplitAndMerge[timePointNumber];
        this.maxGR=this.maxGrowthRate.getValue().doubleValue();
        this.minGR=this.minGrowthRate.getValue().doubleValue();
        this.div=this.divisionCriterion.getValue().doubleValue();
        this.costLim = this.costLimit.getValue().doubleValue();
        this.cumCostLim = this.cumCostLimit.getValue().doubleValue();
        this.structureIdx=structureIdx;
    }
    
    /**
     * 
     * @param timePoint
     * @param performCorrection
     * @return minimal timePoint where correction has been performed, -1 if no correction has been performed
     */
    protected int assignPrevious(int timePoint, boolean performCorrection) {
        if (debug) logger.debug("assign previous timePoint: {}, correction? {}", timePoint, performCorrection);
        TrackAssigner assigner = new TrackAssigner(timePoint);
        assigner.resetTrackAttributes();
        while(assigner.nextTrack()) {
            if (debug) logger.debug("assigner: {}", assigner);
            if (performCorrection && assigner.needCorrection()) {
                int res =  assigner.performCorrection();
                if (res==-1) assigner.assignCurrent(); // no correction was performed
                else return res;
            } else assigner.assignCurrent();
        }
        return -1;
    }
    
    protected SegmenterSplitAndMerge getSegmenter(int timePoint) {
        /*if (segmenters==null) return null;
        if (segmenters[timePoint]==null) segmenters[timePoint] = this.segmenter.instanciatePlugin();
        return segmenters[timePoint];*/
        return segmenter.instanciatePlugin();
    }
    
    protected Image getImage(int timePoint) {
        if (inputImages==null || inputImages[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
            if (inputImages!=null) inputImages[timePoint] = input;
            return input;
        }
        return inputImages[timePoint];
    }
    
    protected List<Object3D> getObjects(int timePoint) {
        if (this.populations[timePoint]==null) {
            StructureObject parent = this.parents.get(timePoint);
            if (!segment) { // no segmentation, object should be already set as children of their parents
                List<StructureObject> list = parent.getChildren(structureIdx);
                if (list!=null) {
                    populations[timePoint] = new ArrayList<Object3D>(list.size());
                    for (StructureObject o : list)  populations[timePoint].add(o.getObject());
                } else populations[timePoint] = new ArrayList<Object3D>(0);
            } else {
                //logger.debug("tp: {}, seg null? {} image null ? {}", timePoint, getSegmenter(timePoint)==null, parent.getRawImage(structureIdx)==null);
                Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
                ObjectPopulation pop= getSegmenter(timePoint).runSegmenter(input, structureIdx, parent);
                pop = postFilters.filter(pop, structureIdx, parent);
                if (pop!=null) populations[timePoint] = pop.getObjects();
                else populations[timePoint] = new ArrayList<Object3D>(0);
            }
            //logger.debug("get object @ {}, size: {}", timePoint, populations[timePoint].size());
        }
        return populations[timePoint];
    }
    
    protected ArrayList<TrackAttribute> getAttributes(int timePoint) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint];
    }
    
    protected TrackAttribute getAttribute(int timePoint, int idx) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint].get(idx);
    }
    
    protected double getSize(int timePoint, int idx) {
        if (this.trackAttributes[timePoint]==null) createAttributes(timePoint);
        return trackAttributes[timePoint].get(idx).getSize();
    }
    
    protected void createAttributes(int timePoint) {
        List<Object3D> pop = getObjects(timePoint);
        this.trackAttributes[timePoint] = new ArrayList<TrackAttribute>(pop.size());
        for (int i = 0; i<pop.size(); ++i) trackAttributes[timePoint].add(new TrackAttribute(pop.get(i), i, timePoint));
    }

    protected void resetIndicies(int timePoint) {
        if (trackAttributes[timePoint]!=null) for (int i = 0; i<trackAttributes[timePoint].size(); ++i) trackAttributes[timePoint].get(i).idx=i;
    }
    protected double defaultSizeIncrement() {
        return (minGR+maxGR)/2.0;
    }
    protected class TrackAttribute {
        final static int sizeIncrementLimit = 5;
        int idx;
        final int timePoint;
        TrackAttribute prev;
        TrackAttribute next;
        Flag flag;
        boolean errorPrev, errorCur;
        int nPrev;
        Object3D o;
        boolean division=false, trackHead=true;
        private double objectSize=Double.NaN;
        //final boolean touchEndOfChannel;
        protected TrackAttribute(Object3D o, int idx, int timePoint) {
            this.o=o;
            this.idx=idx;
            this.timePoint=timePoint;
            //touchEndOfChannel=idx!=0 && o.getBounds().getyMax()==parents.get(timePoint).getBounds().getSizeY(); // TODO: error here -> problem absolute/relative landmark?
        }
        protected TrackAttribute setFlag(Flag flag) {this.flag=flag; return this;}
        public void resetTrackAttributes(boolean previous, boolean next) {
            if (previous) {
                this.prev=null;
                errorPrev=false;
                trackHead=true;
            }
            if (next) {
                this.next=null;
                errorCur=false;
                this.division=false;
                
            }
        }
        public double getSize() {
            if (Double.isNaN(objectSize)) this.objectSize=GeometricalMeasurements.getVolume(o);
            return objectSize;
        }
        private List<Double> getLineageSizeIncrementList() {
            List<Double> res=  new ArrayList<>(sizeIncrementLimit);
            TrackAttribute ta = this.prev;
            
            WL: while(res.size()<sizeIncrementLimit && ta!=null) {
                if (!ta.errorCur) {
                    if (ta.next==null) logger.error("Prev's NEXT NULL ta: {}: prev: {}", this, this.prev);
                    if (ta.division) {
                        double nextSize = 0;
                        List<TrackAttribute> n = ta.getNext();
                        if (n.size()>1) {
                            //boolean touch = false;
                            for (TrackAttribute t : n) {
                                nextSize+=t.getSize();
                                //if (t.touchEndOfChannel) touch=true;
                            }
                            res.add(nextSize/ta.getSize()); //if (!touch) 
                        }
                        if (debug) logger.debug("division: {}, next: {}, nextSize: {}", ta, n, nextSize);
                    } else res.add(ta.next.getSize()/ta.getSize()); //if (!ta.next.touchEndOfChannel) 
                }
                ta = ta.prev;
            }
            return res;
        }
        public double getLineageSizeIncrement() {
            List<Double> list = getLineageSizeIncrementList();
            if (list.isEmpty()) return Double.NaN;
            double res = ArrayUtil.median(list);
            if (res<minGR) res = minGR;
            else if (res>maxGR) res = maxGR;
            if (debug) logger.debug("getSizeIncrement for {}-{}: {} list:{}", timePoint, idx, res, list);
            return res;
        }
        public List<TrackAttribute> getNext() {
            if (trackAttributes.length<=timePoint) return Collections.EMPTY_LIST;
            if (this.division) {
                List<TrackAttribute> res = new ArrayList<>(3);
                for (TrackAttribute t : getAttributes(timePoint+1)) if (t.prev==this) res.add(t);
                return res;
            } else if (next!=null) return new ArrayList<TrackAttribute>(){{add(next);}};
            else return Collections.EMPTY_LIST;
        }
        @Override public String toString() {
            return timePoint+"-"+idx+"(s:"+getSize()+"/th:"+this.trackHead+"/div:"+division+")";
        }
    }
    
    protected class TrackAssigner {
        int timePoint;
        int idxPrev=0, idxPrevEnd=0, idxPrevLim, idx=0, idxEnd=0, idxLim;
        double sizePrev=0, size=0;
        
        protected TrackAssigner(int timePoint) {
            if (timePoint<=0) throw new IllegalArgumentException("timePoint cannot be <=0");
            this.timePoint=timePoint;
            idxPrevLim = getObjects(timePoint-1).size();
            idxLim = getObjects(timePoint).size();
            //logger.debug("ini assigner: {}", timePoint);
        }
        protected TrackAssigner duplicate() {
            TrackAssigner res = new TrackAssigner(timePoint);
            res.idx=idx;
            res.idxEnd=idxEnd;
            res.idxPrev=idxPrev;
            res.idxPrevEnd=idxPrevEnd;
            res.size=size;
            res.sizePrev=sizePrev;
            return res;
        }
        protected boolean isValid() {
            return size>0 && sizePrev>0;
        }
        /**
         * 
         * @return true if there is at least 1 remaining object @ timePoint & timePoint -1
         */
        public boolean nextTrack() {
            if (idxPrevEnd==idxPrevLim || idxEnd==idxLim) return false;
            idxPrev = idxPrevEnd;
            sizePrev = getSize(timePoint-1, idxPrevEnd++);
            idx = idxEnd;
            size = getSize(timePoint, idxEnd++);
            incrementIfNecessary();
            return true;
        }
        protected void incrementIfNecessary() {
            boolean change = true;
            while (change) {
                if (size>=sizePrev) change=incrementPrev();
                else change=increment();
                //if (!change) change=incrementPrevAndCur();
            }
        }

        protected double getCurrentAssignmentScore() {
            double prevSizeIncrement = getAttribute(timePoint-1, idxPrev).getLineageSizeIncrement();
            if (Double.isNaN(prevSizeIncrement)) return Double.NaN;
            if (idxPrevEnd-idxPrev>1) {
                double totalSize=getAttribute(timePoint-1, idxPrev).getSize();
                prevSizeIncrement *= getAttribute(timePoint-1, idxPrev).getSize();
                for (int i = idxPrev+1; i<idxPrevEnd; ++i) { // size-weighted barycenter of size increment lineage
                    double curSI = getAttribute(timePoint-1, i).getLineageSizeIncrement();
                    if (!Double.isNaN(curSI)) {
                        prevSizeIncrement+= curSI * getAttribute(timePoint-1, i).getSize();
                        totalSize += getAttribute(timePoint-1, i).getSize();
                    }
                }
                prevSizeIncrement/=totalSize;
            }
            return Math.abs(prevSizeIncrement - size/sizePrev);
        }

        protected double getAssignmentScoreForWholeScenario(int idxPrevLimit, int idxLimit) { // will modify the current scenario!!
            double score = getCurrentAssignmentScore();
            if (debug) logger.debug("score for whole scenario: start: {}", score);
            if (Double.isNaN(score)) return score;
            while(nextTrack() && idx<idxEnd && idx<=idxLimit && idxPrev<=idxPrevLimit) { // worst case scenario cannot be the last one because of removed cells
                //if (getAttribute(timePoint, idxEnd-1).touchEndOfChannel || getAttribute(timePoint-1, idxPrevEnd-1).touchEndOfChannel) continue; // do not use the last assignemt as cells can get out of the channel and score might be unrelevant
                double newScore=getCurrentAssignmentScore();
                score = Math.max(score, newScore); // maximum score = worst case scenario
                if (debug) logger.debug("score for whole scenario: new score for [{};{}[->[{};{}[={}, wcs: {}", idxPrev, idxPrevEnd, idx, idxEnd, newScore, score);
            }
            return score;
        }
        protected boolean canMerge(int i, int iLim, int tp) {
            return true || Double.isFinite(new MergeScenario(i, iLim, tp).cost);
        }
        protected boolean incrementPrev() { // maximum index so that prevSize * minGR remain inferior to size-1
            boolean change = false;
            while(idxPrevEnd<idxPrevLim) {
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (!canMerge(idxPrev, idxPrevEnd+1, timePoint-1)) return false;
                if (debug) logger.debug("t: {}, increment prev: [{};{}[->[{};{}[ , old size prev: {} (size€[{};{}]) new size prev: {} (size€[{};{}]), size: {}, will increment: {}", timePoint, idxPrev, idxPrevEnd, idx, idxEnd,sizePrev, sizePrev*minGR, sizePrev*maxGR, newSizePrev, newSizePrev*minGR, newSizePrev*maxGR, size, sizePrev * maxGR < size || newSizePrev * minGR <= size  );
                if (sizePrev * maxGR < size || newSizePrev * minGR < size) { // previous is too small compared to current -> add another second one, so that is is not too big
                    if (verifyInequality()) { // if the current assignment already verify the inequality, increment only if there is improvement
                        TrackAssigner newScenario = duplicate();
                        newScenario.idxPrevEnd+=1;
                        newScenario.sizePrev=newSizePrev;
                        TrackAssigner currenScenario = duplicate();
                        if (debug) logger.debug("[{};{}[->[{};{}[: getting score for current scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                        double scoreCur = currenScenario.getAssignmentScoreForWholeScenario(newScenario.idxPrevEnd, idxLim);
                        if (debug) logger.debug("[{};{}[->[{};{}[: getting score for other scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                        double scoreNew = newScenario.getAssignmentScoreForWholeScenario(newScenario.idxPrevEnd, idxLim);
                        if (debug) logger.debug("[{};{}[->[{};{}[: comparison of two solution for prevInc: old {} new: {}", idxPrev, idxPrevEnd, idx, idxEnd, scoreCur, scoreNew);
                        if (Double.isNaN(scoreNew) || scoreCur<scoreNew) return change;
                    }
                    sizePrev=newSizePrev;
                    ++idxPrevEnd;
                    change = true;
                } else return change;
            }
            return change;
        }
        protected boolean increment() {
            boolean change = false;
            while(idxEnd<idxLim && size<sizePrev) {
                double newSize = size + getSize(timePoint, idxEnd);
                if (debug) logger.debug("t: {}, increment: [{};{}[->[{};{}[, old size: {} new size: {}, size prev: {}, theo size€[{};{}], will increment: {}", timePoint, idxPrev, idxPrevEnd, idx, idxEnd,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR, sizePrev * minGR > size && sizePrev * maxGR > newSize );
                if (sizePrev * div > size && sizePrev * maxGR > newSize) { // division criterion + don't grow too much. Div criterio is mostly used at 1rst slide, afterwards the adaptative incrementSize is more useful 
                    size=newSize;
                    ++idxEnd;
                    change = true;
                } else if (sizePrev * maxGR > newSize) { // don't grow too much
                    TrackAssigner newScenario = duplicate();
                    newScenario.idxEnd+=1;
                    newScenario.size=newSize;
                    TrackAssigner currenScenario = duplicate();
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for current scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for other scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: comparison of two solution for curInc: old {} new: {}", idxPrev, idxPrevEnd, idx, idxEnd, scoreCur, scoreNew);
                    if (scoreCur<scoreNew) return change;
                    else {
                        size=newSize;
                        ++idxEnd;
                        change = true;
                    }
                } else if (sizePrev * minGR > size && idxPrevEnd<idxPrevLim && canMerge(idxPrev, idxPrevEnd+1, timePoint-1) ) { // cannot increment because grow too much but need to: increment & increment prev. REMOVE AND PUT IN OTHER METHOD?s
                    TrackAssigner newScenario = duplicate();
                    newScenario.idxEnd+=1;
                    newScenario.idxPrevEnd+=1;
                    newScenario.size=newSize;
                    newScenario.sizePrev+=getSize(timePoint-1, idxPrevEnd);
                    TrackAssigner currenScenario = duplicate();
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for current scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for other scenario... ", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: comparison of two solution for prevInc&curInc: old {} new: {}", idxPrev, idxPrevEnd, idx, idxEnd, scoreCur, scoreNew);
                    if (scoreCur<scoreNew) return change;
                    else {
                        size=newSize;
                        ++idxEnd;
                        sizePrev+=getSize(timePoint-1, idxPrevEnd);
                        ++idxPrevEnd;
                        change = true;
                    }
                }
                else return change;
            }
            return change;
        }
        /*protected boolean incrementPrevAndCur() { // CONDITIONS??
            boolean change = false;
            if(idxEnd<idxLim && idxPrevEnd<idxPrevLim) {
                double newSize = size + getSize(timePoint, idxEnd);
                double newSizePrev = sizePrev + getSize(timePoint-1, idxPrevEnd);
                if (debug) logger.debug("t: {}, incrementPrev&Cur: [{};{}[->[{};{}[, old size: {} new size: {}, size prev: {}, theo size€[{};{}], will increment: {}", timePoint, idxPrev, idxPrevEnd, idx, idxEnd,size, newSize, sizePrev, sizePrev*minGR, sizePrev*maxGR, sizePrev * maxGR < newSize && newSizePrev * minGR < size && newSizePrev * minGR < newSize && newSizePrev * maxGR>newSize );
                if (sizePrev * maxGR < newSize && newSizePrev * minGR < size && newSizePrev * minGR < newSize && newSizePrev * maxGR>newSize ) { // 1) cannot increment or increment prev only, because grow too much but need to: increment & increment prev
                    TrackAssigner newScenario = duplicate();
                    newScenario.idxEnd+=1;
                    newScenario.idxPrevEnd+=1;
                    newScenario.size=newSize;
                    newScenario.sizePrev=newSizePrev;
                    TrackAssigner currenScenario = duplicate();
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for current scenario...", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreCur = currenScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: getting score for other scenario... ", idxPrev, idxPrevEnd, idx, idxEnd);
                    double scoreNew = newScenario.getAssignmentScoreForWholeScenario(idxPrevLim, newScenario.idxEnd);
                    if (debug) logger.debug("[{};{}[->[{};{}[: comparison of two solution for prevInc&curInc: old {} new: {}", idxPrev, idxPrevEnd, idx, idxEnd, scoreCur, scoreNew);
                    if (scoreCur<scoreNew) return change;
                    else {
                        size=newSize;
                        ++idxEnd;
                        sizePrev=newSizePrev;
                        ++idxPrevEnd;
                        change = true;
                    }
                }
                else return change;
            }
            return change;
        }*/
        protected boolean verifyInequality() {
            return sizePrev * minGR <= size && size <= sizePrev * maxGR;
        }
        public boolean needCorrection() {
            return (idxPrevEnd-idxPrev)>1; //|| (sizePrev * maxGR < size); et supprimer @ increment.. 
        }
        public boolean canBeCorrected() {
            return needCorrection()&&idxEnd-idx==1;
        }
        public void resetTrackAttributes() {
            if (trackAttributes[timePoint]!=null) for (TrackAttribute ta : trackAttributes[timePoint]) ta.resetTrackAttributes(true, false);
            if (trackAttributes[timePoint-1]!=null) for (TrackAttribute ta : trackAttributes[timePoint-1]) ta.resetTrackAttributes(false, true);
        }
        public void assignCurrent() {
            int nPrev = idxPrevEnd-idxPrev;
            int nCur = idxEnd-idx;
            boolean error = nPrev>1 || nCur>2 || (idxEnd<idxLim && !verifyInequality()); // case of division and 2nd object out of channel not considered as error
            // TODO: inclure aussi l'info si ne vérifie pas l'inegalité. 
            if (nPrev==nCur) {
                for (int i = 0; i<nPrev; ++i) {
                    TrackAttribute taCur = getAttribute(timePoint, idx+i);
                    TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev+i);
                    taCur.prev=taPrev;
                    taPrev.next=taCur;
                    taCur.trackHead=false;
                    taCur.errorPrev=error;
                    taPrev.errorCur=error;
                    taCur.nPrev=nPrev;
                }
            } else if (nPrev==1 && nCur>1) { // division
                TrackAttribute taPrev = getAttribute(timePoint-1, idxPrev);
                TrackAttribute taCur = getAttribute(timePoint, idx);
                taPrev.division=true;
                taPrev.errorCur=error;
                taPrev.next=taCur;
                taCur.trackHead=false;
                for (int i = idx; i<idxEnd; ++i) {
                    getAttribute(timePoint, i).prev=taPrev;
                    getAttribute(timePoint, i).errorPrev=error;
                    getAttribute(timePoint, i).nPrev=nPrev;
                }
            } else if (nPrev>1 && nCur==1) { // merging
                TrackAttribute taCur = getAttribute(timePoint, idx);
                taCur.trackHead=false;
                taCur.prev=getAttribute(timePoint-1, idxPrev);
                taCur.errorPrev=true;
                taCur.nPrev=nPrev;
                for (int i = idxPrev; i<idxPrevEnd; ++i) {
                    getAttribute(timePoint-1, i).next=taCur;
                    getAttribute(timePoint-1, i).errorCur=true;
                }
            } else if (nPrev>1 && nCur>1) { // algorithm assign first with first or last with last (the most likely) and recursive call. // TODO REWRITE USING ADAPTATIVE INCREMENT SIZE
                TrackAttribute taCur1 = getAttribute(timePoint, idx);
                TrackAttribute taPrev1 = getAttribute(timePoint-1, idxPrev);
                double diff1 = Math.abs(taCur1.getSize()-taPrev1.getSize());
                TrackAttribute taCurEnd = getAttribute(timePoint, idxEnd-1);
                TrackAttribute taPrevEnd = getAttribute(timePoint-1, idxPrevEnd-1);
                double diffEnd = Math.abs(taCurEnd.getSize()-taPrevEnd.getSize());
                TrackAttribute taCur, taPrev;
                if (diff1<diffEnd) { // assign first with first
                    taCur = taCur1;
                    taPrev= taPrev1;
                    idx++;
                    idxPrev++;
                } else { // assign last with last
                    taCur = taCurEnd;
                    taPrev= taPrevEnd;
                    idxEnd--;
                    idxPrevEnd--;
                }
                taCur.prev=taPrev;
                taPrev.next=taCur;
                taCur.errorPrev=true;
                taPrev.errorCur=true;
                taCur.trackHead=false;
                taCur.nPrev=nPrev;
                if (debug) logger.debug("assignment {} with {} objects, assign {}", nPrev, nCur, diff1<diffEnd ? "first" : "last");
                assignCurrent(); // recursive call
            }
        }
        
        /**
         * 
         * @return minimal/maximal timePoint where correction has been performed
         */
        public int performCorrection() {
            if (debugCorr) logger.debug("t: {}: performing correction, idxPrev: {}, idxPrevEnd: {}", timePoint, idxPrev, idxPrevEnd);
            MergeScenario m = new MergeScenario(idxPrev, idxPrevEnd, timePoint-1);
            if (Double.isInfinite(m.cost)) return -1; //cannot merge
            List<CorrectionScenario> merge = m.getWholeScenario(maxCorrectionLength, costLim, cumCostLim); // merge scenario
            double mergeCost = 0; for (CorrectionScenario c : merge) mergeCost+=c.cost; if (merge.isEmpty()) mergeCost=Double.POSITIVE_INFINITY;
            SplitScenario ss = new SplitScenario(getAttribute(timePoint, idx), timePoint);
            for (int i = idx+1; i<idxEnd; ++i) {
                SplitScenario sss = new SplitScenario(getAttribute(timePoint, i), timePoint);
                if (sss.cost<ss.cost) ss=sss;
            }
            List<CorrectionScenario> split =ss.getWholeScenario(maxCorrectionLength, costLim, mergeCost>0? Math.min(mergeCost, cumCostLim) : cumCostLim);
            double splitCost = 0; for (CorrectionScenario c : split) splitCost+=c.cost; if (split.isEmpty()) splitCost=Double.POSITIVE_INFINITY;
            if (debugCorr) logger.debug("t: {}: performing correction: merge scenario length: {}, cost: {}, split scenario: length {}, cost: {}", timePoint, merge.size(), mergeCost, split.size(), splitCost);
            if (Double.isInfinite(mergeCost) && Double.isInfinite(splitCost)) return -1;
            if (mergeCost>splitCost) {
                for (CorrectionScenario c : split) c.applyScenario();
                return timePoint+split.size();
            }
            else {
                for (CorrectionScenario c : merge) c.applyScenario();
                return Math.max(1, timePoint-merge.size());
            }
        }
        @Override public String toString() {
            return "timePoint: "+timePoint+ " prev: ["+idxPrev+";"+idxPrevEnd+"[ (lim: "+idxPrevLim+ ") next: ["+idx+";"+idxEnd+"[ (lim: "+idxLim+ ") size prev: "+sizePrev+ " size: "+size+ " error prev: "+(idxPrevEnd-idxPrev)+" error next: "+(idxEnd-idx)+" inequality: "+verifyInequality();
        }
    }
    
    protected abstract class CorrectionScenario {
        double cost=Double.POSITIVE_INFINITY;
        final int timePoint;
        protected CorrectionScenario(int timePoint) {this.timePoint=timePoint;}
        protected abstract CorrectionScenario getNextScenario();
        /**
         * 
         * @param lengthLimit if >0 limits the length of the scenario
         * @param costLimit if >0 cost limit per operation
         * @param cumulativeCostLimit if >0 cost limit for the whole scenario
         * @return 
         */
        public List<CorrectionScenario> getWholeScenario(int lengthLimit, double costLimit, double cumulativeCostLimit) {
            ArrayList<CorrectionScenario> res = new ArrayList<CorrectionScenario>();
            CorrectionScenario cur = this;
            if (cur instanceof MergeScenario && ((MergeScenario)cur).listO.isEmpty()) return Collections.emptyList();
            double sum = 0;
            while(cur!=null && (!Double.isNaN(cur.cost)) && Double.isFinite(cur.cost)) {
                res.add(cur);
                sum+=cur.cost;
                if (cur.cost > costLimit) return Collections.emptyList();
                if (cumulativeCostLimit>0 && sum>cumulativeCostLimit) return Collections.emptyList();
                if (lengthLimit>0 && res.size()>=lengthLimit) return Collections.emptyList();
                cur = cur.getNextScenario();
            }
            return res;
        }
        protected abstract void applyScenario();
    }
    protected class MergeScenario extends CorrectionScenario {
        int idxMin, idxMax;
        ArrayList<Object3D> listO;
        public MergeScenario(int idxMin, int idxMax, int timePoint) { // idxMax excluded
            super(timePoint);
            this.idxMax=idxMax;
            this.idxMin = idxMin;
            listO = new ArrayList<Object3D>(idxMax - idxMin);
            for (int i = idxMin; i<idxMax; ++i) {
                TrackAttribute ta = getAttribute(timePoint, i);
                listO.add(ta.o);
            }
            if (!listO.isEmpty()) {
                this.cost = getSegmenter(timePoint).computeMergeCost(getImage(timePoint), listO);
            }
            if (debugCorr) logger.debug("Merge scenario: tp: {}, idxMin: {}, #objects: {}, cost: {}", timePoint, idxMin, listO.size(), cost);
        }
        @Override protected MergeScenario getNextScenario() { // @ previous time, until there is one single parent ie no more bacteria to merge
            if (timePoint==0 || idxMin==idxMax) return null;
            int iMin = Integer.MAX_VALUE;
            int iMax = Integer.MIN_VALUE;
            for (int i = idxMin; i<idxMax; ++i) { // get all connected trackAttributes from previous timePoint
                TrackAttribute ta = getAttribute(timePoint, i).prev;
                if (ta==null) continue;
                if (iMin>ta.idx) iMin = ta.idx;
                if (iMax<ta.idx) iMax = ta.idx;
                if (ta.idx != trackAttributes[timePoint-1].indexOf(ta)) logger.error("BCMTLC: inconsistent data: t: {}, expected idx: {}, actual: {}", timePoint-1, ta.idx, trackAttributes[timePoint-1].indexOf(ta));
            }
            if (iMin==iMax) return null; // no need to merge
            if (iMin==Integer.MAX_VALUE || iMax==Integer.MIN_VALUE) return null; // no previous objects 
            return new MergeScenario(iMin,iMax+1, timePoint-1);
        }

        @Override
        protected void applyScenario() {
            Object3D o = populations[timePoint].get(idxMin);
            trackAttributes[timePoint].get(idxMin).flag=Flag.correctionMerge;
            trackAttributes[timePoint].get(idxMin).objectSize=Double.NaN; // reset object size;
            for (int i = idxMax-1; i>idxMin; --i) {
                Object3D rem = populations[timePoint].remove(i);
                o.addVoxels(rem.getVoxels());
                trackAttributes[timePoint].remove(i);
            }
            resetIndicies(timePoint);
        }
    }
    
    protected class SplitScenario extends CorrectionScenario {
        TrackAttribute o;
        List<Object3D> splitObjects;
        public SplitScenario(TrackAttribute o, int timePoint) {
            super(timePoint);
            this.o=o;
            splitObjects= new ArrayList<Object3D>();
            cost = getSegmenter(timePoint).split(getImage(timePoint), o.o, splitObjects);
            if (debugCorr) logger.debug("Split scenario: tp: {}, idx: {}, cost: {} # objects: {}", timePoint, o.idx, cost, splitObjects.size());
        }
        @Override protected SplitScenario getNextScenario() { // until next division event OR reach end of channel & division with 2n sister lost
            if (timePoint == populations.length-1) return null;
            if (o.next==null) {
                if (debugCorr) logger.debug("getNextScenario: assign @:{}", timePoint+1);
                assignPrevious(timePoint+1, false);
            }
            if (o.next!=null) {
                if (o.division || (o.next.idx==getObjects(timePoint+1).size()-1 && o.getSize() * minGR > o.next.getSize())) return null;
                else return new SplitScenario(o.next, timePoint+1);
            }
            else return null;
        }

        @Override
        protected void applyScenario() {
            Collections.sort(splitObjects, getComparatorObject3D(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
            populations[timePoint].remove(o.idx);
            populations[timePoint].addAll(o.idx, splitObjects);
            trackAttributes[timePoint].remove(o.idx);
            int curIdx = o.idx;
            for (Object3D splitObject : splitObjects) {
                trackAttributes[timePoint].add(curIdx, new TrackAttribute(splitObject, curIdx, timePoint).setFlag(Flag.correctionSplit));
                ++curIdx;
            }
            resetIndicies(timePoint);
        }
    }
}
