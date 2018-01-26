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
package boa.image;

import boa.data_structure.Voxel;
import java.io.Serializable;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import boa.utils.JSONSerializable;

/**
 *
 * @author jollion
 */

public class BoundingBox implements JSONSerializable {

    int xMin, xMax, yMin, yMax, zMin, zMax;
    transient int count;
    public BoundingBox(){
        xMin=Integer.MAX_VALUE;
        yMin=Integer.MAX_VALUE;
        zMin=Integer.MAX_VALUE;
        xMax=Integer.MIN_VALUE;
        yMax=Integer.MIN_VALUE;
        zMax=Integer.MIN_VALUE;
    }
    
    public BoundingBox(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }
    /**
     * creates a new bounding box containing the voxel of coordinates {@param x}, {@param y}, {@param z}
     * @param x coordinate in the X-Axis
     * @param y coordinate in the Y-Axis
     * @param z coordinate in the Z-Axis
     */
    public BoundingBox(int x, int y, int z) {
        xMin=x;
        xMax=x;
        yMin=y;
        yMax=y;
        zMin=z;
        zMax=z;
        count=1;
    }

    public void setxMin(int xMin) {
        this.xMin = xMin;
    }

    public void setxMax(int xMax) {
        this.xMax = xMax;
    }

    public void setyMin(int yMin) {
        this.yMin = yMin;
    }

    public void setyMax(int yMax) {
        this.yMax = yMax;
    }

    public void setzMin(int zMin) {
        this.zMin = zMin;
    }

    public void setzMax(int zMax) {
        this.zMax = zMax;
    }
    
    
    
    /**
     * 
     * @param image
     * @param useOffset 
     */
    public BoundingBox(Image image, boolean useOffset) {
        if (useOffset) {
            xMin=image.getOffsetX();
            yMin=image.getOffsetY();
            zMin=image.getOffsetZ();
        }
        xMax=xMin+image.getSizeX()-1;
        yMax=yMin+image.getSizeY()-1;
        zMax=zMin+image.getSizeZ()-1;
    }
    
    public boolean contains(int x, int y, int z) {
        return xMin<=x && xMax>=x && yMin<=y && yMax>=y && zMin<=z && zMax>=z;
    }
    
    /**
     * Modify the bounds so that is contains the {@param x} coordinate
     * @param x coordinate in the X-Axis
     */
    public BoundingBox expandX(int x) {
        if (x < xMin) {
            xMin = x;
        } 
        if (x > xMax) {
            xMax = x;
        }
        return this;
    }
    /**
     * Modify the bounds so that is contains the {@param y} coordinate
     * @param y coordinate in the X-Axis
     */
    public BoundingBox expandY(int y) {
        if (y < yMin) {
            yMin = y;
        } 
        if (y > yMax) {
            yMax = y;
        }
        return this;
    }
    /**
     * Modify the bounds so that is contains the {@param z} coordinate
     * @param z coordinate in the X-Axis
     */
    public BoundingBox expandZ(int z) {
        if (z < zMin) {
            zMin = z;
        } 
        if (z > zMax) {
            zMax = z;
        }
        return this;
    }
    public BoundingBox fitToImageZ(ImageProperties im) {
        this.zMin = im.getOffsetZ();
        this.zMax = im.getOffsetZ()+im.getSizeZ()-1;
        return this;
    }
    public BoundingBox contractX(int xm, int xM) {
        if (xm > xMin) {
            xMin = xm;
        } 
        if (xM < xMax) {
            xMax = xM;
        }
        return this;
    }
    public BoundingBox contractY(int ym, int yM) {
        if (ym > yMin) {
            yMin = ym;
        } 
        if (yM < yMax) {
            yMax = yM;
        }
        return this;
    }
    public BoundingBox contractZ(int zm, int zM) {
        if (zm > zMin) {
            zMin = zm;
        } 
        if (zM < zMax) {
            zMax = zM;
        }
        return this;
    }
    
    public BoundingBox expand(int x, int y, int z) {
        expandX(x);
        expandY(y);
        expandZ(z);
        return this;
    }
    
    public BoundingBox expand(Voxel v) {
        expandX(v.x);
        expandY(v.y);
        expandZ(v.z);
        return this;
    }
    
    public BoundingBox expand(BoundingBox other) {
        expandX(other.xMin);
        expandX(other.xMax);
        expandY(other.yMin);
        expandY(other.yMax);
        expandZ(other.zMin);
        expandZ(other.zMax);
        return this;
    }
    public BoundingBox contract(BoundingBox other) {
        contractX(other.xMin, other.xMax);
        contractY(other.yMin, other.yMax);
        contractZ(other.zMin, other.zMax);
        return this;
    }
    
    public BoundingBox center(BoundingBox other) {
        int deltaX = (int)(other.getXMean() - this.getXMean());
        int deltaY = (int)(other.getYMean() - this.getYMean());
        int deltaZ = (int)(other.getZMean() - this.getZMean());
        return translate(deltaX, deltaY, deltaZ);
    }
    
    public void addToCounter() {
        count++;
    }
    public void setCounter(int count) {
        this.count = count;
    }
    /**
     * add {@param border} value in each direction and both ways
     * @param border value of the border
     */
    public BoundingBox addBorder(int border, boolean addInZDirection) {
        xMin-=border;
        xMax+=border;
        yMin-=border;
        yMax+=border;
        if (addInZDirection) {
            zMin-=border;
            zMax+=border;
        }
        return this;
    }
    /**
     * adds a border of 1 pixel in each directions and both ways
     */
    public BoundingBox addBorder() {
        xMin--;
        xMax++;
        yMin--;
        yMax++;
        zMin--;
        zMax++;
        return this;
    }
    /**
     * ensures the bounds are included in the bounds of the {@param properties} object.
     * @param properties 
     * @return  current modified boundingbox object
     */
    public BoundingBox trim(BoundingBox properties) {
        if (xMin<properties.xMin) xMin=properties.xMin;
        if (yMin<properties.yMin) yMin=properties.yMin;
        if (zMin<properties.zMin) zMin=properties.zMin;
        if (xMax>properties.xMax) xMax=properties.xMax;
        if (yMax>properties.yMax) yMax=properties.yMax;
        if (zMax>properties.zMax) zMax=properties.zMax;
        return this;
    }
    public boolean sameBounds(BoundingBox bounds) {
        return this.equals(bounds);
    }
    public boolean sameBounds(ImageProperties properties) {
        return xMin==properties.getOffsetX() && yMin==properties.getOffsetY() && zMin==properties.getOffsetZ() && xMax==(properties.getSizeX()-1+properties.getOffsetX()) && yMax==(properties.getSizeY()-1+properties.getOffsetY()) && zMax==(properties.getSizeZ()-1+properties.getOffsetZ());
    }
    
    /**
     * Translate the bounding box in the 3 axes
     * @param dX translation in the X-Axis in pixels
     * @param dY translation in the Y-Axis in pixels
     * @param dZ translation in the X-Axis in pixels
     * @return the same instance of bounding box, after the translation operation
     */
    public BoundingBox translate(int dX, int dY, int dZ) {
        xMin+=dX; xMax+=dX; yMin+=dY; yMax+=dY; zMin+=dZ; zMax+=dZ;
        return this;
    }
    
    public BoundingBox translateToOrigin() {
        return translate(-xMin, -yMin, -zMin);
    }
    
    public int getxMin() {
        return xMin;
    }

    public int getxMax() {
        return xMax;
    }

    public int getyMin() {
        return yMin;
    }

    public int getyMax() {
        return yMax;
    }

    public int getzMin() {
        return zMin;
    }

    public int getzMax() {
        return zMax;
    }

    public int getCount() {
        return count;
    }
    
    public int getSizeX() {
        return xMax-xMin+1;
    }
    
    public int getSizeY() {
        return yMax-yMin+1;
    }
    
    public int getSizeZ() {
        return zMax-zMin+1;
    }
    
    public int getSizeXY() {
        return (xMax-xMin+1) * (yMax-yMin+1);
    }
    
    public int getSizeXYZ() {
        return (xMax-xMin+1) * (yMax-yMin+1) * (zMax-zMin+1);
    }
    
    public double getXMean() {
        return (xMin+xMax)/2.0;
    }
    
    public double getYMean() {
        return (yMin+yMax)/2.0;
    }
    
    public double getZMean() {
        if (getSizeZ()<=1) return zMin;
        return (zMin+zMax)/2.0;
    }
    
    public double getDistance(BoundingBox other) {
        return Math.sqrt(Math.pow(this.getXMean()-other.getXMean(), 2) + Math.pow(this.getYMean()-other.getYMean(), 2) + Math.pow(this.getZMean()-other.getZMean(), 2));
    }
    
    public boolean intersect2D(BoundingBox other) {
        return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax) && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax);
    }
    
    public boolean intersect(BoundingBox other) {
        if (getSizeZ()<=1 && other.getSizeZ()<=1) return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax) && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax);
        else return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax) && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax) && Math.max(zMin, other.zMin)<=Math.min(zMax, other.zMax);
    }
    public boolean intersect2D(BoundingBox other, int tolerance) {
        return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax)+tolerance && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax)+tolerance;
    }
    public boolean intersect(BoundingBox other, int tolerance) {
        if (getSizeZ()<=1 && other.getSizeZ()<=1) return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax)+tolerance && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax)+tolerance;
        else return Math.max(xMin, other.xMin)<=Math.min(xMax, other.xMax)+tolerance && Math.max(yMin, other.yMin)<=Math.min(yMax, other.yMax)+tolerance && Math.max(zMin, other.zMin)<=Math.min(zMax, other.zMax)+tolerance;
    }
    /**
     * 
     * @param other
     * @return intersection bounding box. If the size in one direction is negative => there are no intersection in this direction
     */
    public BoundingBox getIntersection(BoundingBox other) {
        return new BoundingBox(Math.max(xMin, other.xMin), Math.min(xMax, other.xMax), Math.max(yMin, other.yMin), Math.min(yMax, other.yMax), Math.max(zMin, other.zMin), Math.min(zMax, other.zMax));
    }
    
    /**
     * 
     * @param other
     * @return intersection bounding box. If the size in one direction is negative => there are no intersection in this direction. Zmin and Zmax are those of current object
     */
    public BoundingBox getIntersection2D(BoundingBox other) {
        return new BoundingBox(Math.max(xMin, other.xMin), Math.min(xMax, other.xMax), Math.max(yMin, other.yMin), Math.min(yMax, other.yMax), zMin, zMax);
    }
    /**
     * 
     * @param container
     * @return 3D inclusion
     */
    public boolean isIncluded(BoundingBox container) {
        return xMin>=container.xMin && xMax<=container.xMax && yMin>=container.yMin && yMax<=container.yMax && zMin>=container.zMin && zMax<=container.zMax;
    }
    /**
     * 
     * @param container
     * @return 2D inclusion
     */
    public boolean isIncluded2D(BoundingBox container) {
        return xMin>=container.xMin && xMax<=container.xMax && yMin>=container.yMin && yMax<=container.yMax;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof BoundingBox) {
            BoundingBox otherBB = (BoundingBox) other;
            return xMin==otherBB.getxMin() && yMin==otherBB.getyMin() && zMin==otherBB.getzMin() && xMax==otherBB.getxMax() && yMax==otherBB.getyMax() && zMax==otherBB.getzMax();
        } else if (other instanceof ImageProperties) {
            return this.sameBounds((ImageProperties)other);
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + this.xMin;
        hash = 29 * hash + this.xMax;
        hash = 29 * hash + this.yMin;
        hash = 29 * hash + this.yMax;
        hash = 29 * hash + this.zMin;
        hash = 29 * hash + this.zMax;
        return hash;
    }
    
    public BlankMask getImageProperties(float scaleXY, float scaleZ) {
        return new BlankMask("", this, scaleXY, scaleZ);
    }
    
    public BlankMask getImageProperties(String name, float scaleXY, float scaleZ) {
        return new BlankMask(name, this, scaleXY, scaleZ);
    }
    
    public ImageProperties getImageProperties() {
        return new BlankMask("", this, 1, 1);
    }
    
    public BoundingBox duplicate() {
        return new BoundingBox(xMin, xMax, yMin, yMax, zMin, zMax);
    }
    
    public BoundingBox translate(BoundingBox other) {
        this.translate(other.xMin, other.yMin, other.zMin);
        return this;
    }
    
    public BoundingBox reverseOffset() {
        this.xMin=-xMin;
        this.yMin=-yMin;
        this.zMin=-zMin;
        return this;
    }
    
    public boolean isOffsetNull() {
        return xMin==0 && yMin==0 && zMin==0;
    }
    
    @Override
    public String toString() {
        return "[x:["+xMin+";"+xMax+"], y:["+yMin+";"+yMax+"], z:["+zMin+";"+zMax+"]]";
    }
    public String toStringOffset() {
        return "[x:"+xMin+";y:"+yMin+";z:"+zMin+"]";
    }
    public void loop(LoopFunction function) {
        if (function instanceof LoopFunction2) ((LoopFunction2)function).setUp();
        for (int z = zMin; z<=zMax; ++z) {
            for (int y = yMin; y<=yMax; ++y) {
                for (int x=xMin; x<=xMax; ++x) {
                    function.loop(x, y, z);
                }
            }
        }
        if (function instanceof LoopFunction2) ((LoopFunction2)function).tearDown();
    }
    @Override
    public void initFromJSONEntry(Object json) {
        JSONArray bds =  (JSONArray)json;
        setxMin(((Number)bds.get(0)).intValue());
        setxMax(((Number)bds.get(1)).intValue());
        setyMin(((Number)bds.get(2)).intValue());
        setyMax(((Number)bds.get(3)).intValue());
        if (bds.size()>=6) {
            setzMin(((Number)bds.get(4)).intValue());
            setzMax(((Number)bds.get(5)).intValue());
        } else {
            setzMin(0);
            setzMax(0);
        }
    }
    @Override
    public JSONArray toJSONEntry() {
        JSONArray bds =  new JSONArray();
        bds.add(getxMin());
        bds.add(getxMax());
        bds.add(getyMin());
        bds.add(getyMax());
        if (getSizeZ()>1 || getzMin()!=0) {
            bds.add(getzMin());
            bds.add(getzMax());
        }
        return bds;
    }
    
    public static interface LoopFunction2 extends LoopFunction {
        public void setUp();
        public void tearDown();
    }
    public static interface LoopFunction {
        public void loop(int x, int y, int z);
    }
}