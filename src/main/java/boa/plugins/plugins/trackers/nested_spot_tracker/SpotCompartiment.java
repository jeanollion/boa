/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.trackers.nested_spot_tracker;

import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BoundingBox;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparator;
import boa.plugins.plugins.trackers.nested_spot_tracker.SpotWithinCompartment.Localization;
import boa.utils.Utils;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class SpotCompartiment {
    public final StructureObject object;
    Point offsetUp, offsetDown;
    //double[] offsetDivisionUp, offsetDivisionDown;
    //int previousDivisionTime;
    int nextDivisionTimePoint;
    Point offsetDivisionMiddle;
    double[] middleYLimits;
    boolean upperDaughterCell=true;
    public static double middleAreaProportion = 0.5;
    public boolean truncated = false;
    public double sizeIncrement=Double.NaN;
    public static double yLengthForXMeanComputation = 0.2; 
    public SpotCompartiment(StructureObject o) {
        //long t0 = System.currentTimeMillis();
        object = o;
        Point[] poles = getPoles(object.getRegion(), 0.5, yLengthForXMeanComputation);
        offsetUp = poles[0];
        offsetDown = poles[1];
        //nextDivisionTimePoint = object.getNextDivisionTimePoint();
        nextDivisionTimePoint = getNextDivisionFrame(object, 0.8);
        computeDivisionOffset(yLengthForXMeanComputation);
        computeIsUpperDaughterCell();
        
        //if (object.getNext()!=null && object.getNext().getDivisionSiblings(false)!=null) divisionAtNextTimePoint = true;
        //previousDivisionTime = object.getPreviousDivisionTimePoint();
        truncated = isTruncated(object);
        //long t1 = System.currentTimeMillis();
        //logger.debug("spotCompartiment: {}, creation time: {}", this, t0-t1);
    }
    
    public static boolean isTruncated(StructureObject o ) {
        return (Double)o.getAttribute("EndOfChannelContact", 0d)>0.45; // estimate if attribute not present
    }
    
    public static int getNextDivisionFrame(StructureObject o, double sizeProportion) {
        while (o!=null) {
            if (divisionAtNextFrame(o, sizeProportion)) return o.getFrame()+1;
            o=o.getNext();
        }
        return -1;
    }
    public static int getPrevDivisionFrame(StructureObject o, double sizeProportion) {
        while (o.getPrevious()!=null) {
            if (divisionAtNextFrame(o.getPrevious(), sizeProportion)) return o.getFrame();
            o=o.getPrevious();
        }
        return -1;
    }
    public static boolean divisionAtNextFrame(StructureObject prev, double sizeProportion) {
        if (prev.getParent().getNext()==null) return false;
        List<StructureObject> next = new ArrayList<>(prev.getParent().getNext().getChildren(prev.getStructureIdx()));
        next.removeIf(o->!prev.equals(o.getPrevious()));
        if (next.size()>1) return true;
        if (next.size()==1) {
            //logger.debug("div next frame for {}->{}: attibute: {}, size {}, sizePrev*0.8:{} eocp: {}, eoc:{}", prev, next.get(0), (Boolean)next.get(0).getAttribute("TruncatedDivision"), (double)next.get(0).getRegion().getSize() , prev.getRegion().getSize() * sizeProportion, isEndOfChannel(prev) , isEndOfChannel(next.get(0)));
            Object o = next.get(0).getAttribute("TruncatedDivision");
            if (o!=null) {
                if (o instanceof String) return Boolean.parseBoolean((String)o);
                return (Boolean)o;
            }
            if (!isEndOfChannel(prev) || !isEndOfChannel(next.get(0))) return false; // only end of channel
            return (double)next.get(0).getRegion().size() < prev.getRegion().size() * sizeProportion;
        } else return false;
    }
    public static boolean isEndOfChannel(StructureObject o) {
        return (o==Collections.max(o.getParent().getChildren(o.getStructureIdx()), (o1, o2)->Double.compare(o1.getBounds().yMean(), o2.getBounds().yMean())));
    }
    @Override public String toString() {
        return "{"+object.toString()+"offX:"+object.getBounds().xMin()*object.getScaleXY()+";Y="+object.getBounds().yMin()*object.getScaleZ()+"|isUpperDaugther:"+upperDaughterCell+"|Ylim:"+ middleYLimits +"|up:"+ offsetUp +"|down:"+ offsetDown +"|middle:"+ offsetDivisionMiddle ;
    }
    
    public Point getOffset(Localization localization) {
        if (Localization.UP.equals(localization)) return offsetUp;
        else if (Localization.LOW.equals(localization)) return this.offsetDown;
        else if (Localization.MIDDLE.equals(localization)) return this.offsetDivisionMiddle;
        else return null;
    }
    
    private static Point getPole(Region o, double margin, int marginYUp, int marginYDown) {
        double xMean = 0, zMean = 0, count=0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        int yMin = marginYUp>=0 ? o.getBounds().yMin()+marginYUp : o.getBounds().yMin();
        int yMax = o.getBounds().yMin()+marginYDown;
        if (yMax>o.getBounds().yMax()) yMax = o.getBounds().yMax();
        if (yMax-yMin<7) yMin = yMax-7;
        if (yMin<o.getBounds().yMin()) yMin = o.getBounds().yMin();
        for (int y = yMin; y<=yMax; ++y) {
            for (int z = bds.zMin(); z<=bds.zMax(); ++z) {
                for (int x = bds.xMin(); x<=bds.xMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        xMean+=x;
                        zMean+=z;
                        count++;
                    }
                }
            }
        }
        if (count==0) {
            Point center = o.getGeomCenter(false); // more stable using cell's center for x & z
            xMean = center.get(0);
            zMean = (center.numDimensions()>2?center.get(2):o.getBounds().zMean());
        } else {
            xMean/=count;
            zMean/=count;
        }
        
        return new Point( (float) (xMean * o.getScaleXY()), (float) ((o.getBounds().yMin()+margin)* o.getScaleXY()),(float) (zMean * o.getScaleZ()));
    }
    
    private static Point[] getPoles(Region o, double proportionOfWidth, double yLenghtForXMean) {
        int[] ySize = new int[o.getBounds().sizeY()];
        double meanYSize = 0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        int yMin = bds.yMin();
        for (int y = yMin; y<=bds.yMax(); ++y) {
            for (int z = bds.zMin(); z<=bds.zMax(); ++z) {
                for (int x = bds.xMin(); x<=bds.xMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        ySize[y-yMin]++;
                    }
                }
            }
            meanYSize +=ySize[y-yMin];
        }
        meanYSize /= (double)ySize.length;
        double minYSize = proportionOfWidth * meanYSize;
        int yUp = 0;
        while(ySize[yUp]<minYSize) yUp++;
        int yL = (int)Math.round(yLenghtForXMean*bds.sizeY());
        Point poleUp = getPole(o, yUp, 0, yL);
        int yDown = ySize.length-1;
        while(ySize[yDown]<minYSize) yDown--;
        Point poleDown = getPole(o, yDown, o.getBounds().sizeY()-yL-1, o.getBounds().sizeY()-1);
        return new Point[]{poleUp, poleDown};
    }
    
    private static Point getPoleByCount(Region o, int limit, int marginYUp, int marginYDown) {
        int count=0, countPrev, countCur=-1;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        for (int y = bds.yMin(); y<=bds.yMax(); ++y) {
            countPrev = count;
            for (int z = bds.zMin(); z<=bds.zMax(); ++z) {
                for (int x = bds.xMin(); x<=bds.xMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        count++;
                        if (count == limit) countCur = count;
                    }
                }
            }
            if (countCur>0) {
                double p = (countCur-countPrev) / (count - countPrev);
                double yApprox = y * p + (y+1) * (1-p) - bds.yMin();
                return getPole(o, yApprox, (int)Math.round(yApprox+marginYUp), (int)Math.round(yApprox+marginYDown));
            }
        }
        throw new RuntimeException("get Division middle : limit unreached");
    }
    
    private static double[] getYPositionWithinCompartimentByCount(Region o, int... limit) {
        int count=0;
        ImageMask mask = o.getMask();
        BoundingBox bds = o.getBounds();
        int currentLimit = 0;
        double[] res = new double[limit.length];
        for (int y = bds.yMin(); y<=bds.yMax(); ++y) {
            for (int z = bds.zMin(); z<=bds.zMax(); ++z) {
                for (int x = bds.xMin(); x<=bds.xMax(); ++x) {
                    if (mask.insideMaskWithOffset(x, y, z)) {
                        count++;
                        if (count == limit[currentLimit]) {
                            res[currentLimit++] = y * o.getScaleXY();
                            if (currentLimit==limit.length) return res;
                        }
                    }
                }
            }
        }
        throw new RuntimeException("get Division middle : limit unreached");
    }
    
    /*protected double[] getPoleDivisionOffsetUp(SpotCompartiment previousCompartiment) {
        if (compartimentOffsetDivisionUp==null) {
            // get previous division sibling @ same timePoint
            StructureObject sibling = compartiment.object.getTrackHead().getPrevious();
            while(sibling!=null && sibling.getTimePoint()!=compartiment.object.getTimePoint()) sibling = sibling.getNext();
            if (sibling!=null) {
                compartimentOffsetDivisionUp = getPole(sibling.getRegion(), poleMargin, true);
            } else {
                throw new RuntimeException("SpotWithinCompartment :: no offset Found: "+compartiment.toString());
                //compartimentOffsetDivision = new double[]{}; // to signal no sibling was found
            }
        }
        return compartimentOffsetDivisionUp;
    }*/
    
    private void computeIsUpperDaughterCell() {
        // suppose div @ trackHead
        List<StructureObject> siblings = object.getTrackHead().getDivisionSiblings(true);
        Collections.sort(siblings, (o1, o2)->Integer.compare(o1.getBounds().yMin(), o2.getBounds().yMin()));
        upperDaughterCell = siblings.get(0).getTrackHead().equals(object.getTrackHead());
        //logger.debug("object: {}, prev siblings: {}, upper?: {}", object, siblings, upperDaughterCell);
        //object.setAttribute("upper daughter cell", upperDaughterCell);
    }
    
    private static List<StructureObject> getDivisionSiblings(StructureObject previous) {
        if (previous==null || previous.getParent().getNext()==null) return Collections.EMPTY_LIST;
        List<StructureObject> candidates = previous.getParent().getNext().getChildren(previous.getStructureIdx());
        List<StructureObject> res= new ArrayList<>(2);
        for (StructureObject o : candidates) if (previous.equals(o.getPrevious())) res.add(o);
        Collections.sort(res, (o1, o2) -> Integer.compare(o1.getBounds().yMin(), o2.getBounds().yMin()));
        return res;
    }
    
    private void computeDivisionOffset(double yLengthForXMean) {
        int count = this.object.getMask().count();
        int upperCompartimentCount = count/2;
        if (this.nextDivisionTimePoint>0) {
            StructureObject beforeDiv = this.object.getInTrack(nextDivisionTimePoint-1);
            if (beforeDiv==null) logger.debug("no object found before div at frame: {}, for track: {}, object: {}", nextDivisionTimePoint-1, object.getTrackHead(), object);
            else {
                int halfyL = (int)Math.round(object.getRegion().getBounds().sizeY()*yLengthForXMean/2d);
                List<StructureObject> siblings = getDivisionSiblings(beforeDiv);
                if (siblings.size()==2) { // cut the object whith the same proportion
                    double c1 = (double) siblings.get(0).getMask().count();
                    double c2 = (double) siblings.get(1).getMask().count();
                    double p = c1 / (c1+c2);
                    upperCompartimentCount = (int)(p * count +0.5);
                    this.offsetDivisionMiddle = getPoleByCount(object.getRegion(), upperCompartimentCount, -halfyL, halfyL); 
                }
            }
        } 
        if (offsetDivisionMiddle==null) offsetDivisionMiddle = object.getRegion().getGeomCenter(true);
        
        // upper and lower bounds for the middle area
        int upperMiddleUpLimit = (int) (upperCompartimentCount/2 - middleAreaProportion/2d * upperCompartimentCount+0.5);
        int upperMiddleLowLimit = (int) (upperCompartimentCount/2 + middleAreaProportion/2d * upperCompartimentCount+0.5);
        int lowerCompartimentCount = count - upperCompartimentCount;
        int lowerMiddleUpLimit = (int) (upperCompartimentCount + lowerCompartimentCount/2 - middleAreaProportion/2d * lowerCompartimentCount+0.5);
        int lowerMiddleLowLimit = (int) (upperCompartimentCount + lowerCompartimentCount/2 + middleAreaProportion/2d * lowerCompartimentCount+0.5);
        middleYLimits = getYPositionWithinCompartimentByCount(object.getRegion(), upperMiddleUpLimit, upperMiddleLowLimit, lowerMiddleUpLimit, lowerMiddleLowLimit);
    }
    
    public boolean sameTrackOrDirectChildren(SpotCompartiment nextCompartiment) {
        return (object.getTrackHead()==nextCompartiment.object.getTrackHead() || // same track
                nextCompartiment.object.getTrackHead().getFrame()>object.getFrame() &&  // from a posterior division
                    nextCompartiment.object.getTrackHead().getPrevious()!=null &&
                    nextCompartiment.object.getTrackHead().getPrevious().getTrackHead()==object.getTrackHead());
        }
}