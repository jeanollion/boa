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
package boa.image.processing.neighborhood;

import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.ImageMask;

/**
 *
 * @author jollion
 */
public abstract class DisplacementNeighborhood implements Neighborhood {
    public int[] dx, dy, dz;
    boolean is3D;        
    float[] values;
    float[] distances;
    int valueCount=0;
    
    @Override public void setPixels(Voxel v, Image image, ImageMask mask) {setPixels(v.x, v.y, v.z, image, mask);}
    
    @Override public void setPixels(int x, int y, int z, Image image, ImageMask mask) {
        valueCount=0;
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && (mask==null||mask.insideMask(xx, yy, zz))) values[valueCount++]=image.getPixel(xx, yy, zz);
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && (mask==null||mask.insideMask(xx, yy, z))) values[valueCount++]=image.getPixel(xx, yy, z);
            }
        }
    }
    public void setPixelsByIndex(Voxel v, Image image) {setPixelsByIndex(v.x, v.y, v.z, image);}
    /**
     * The value array is filled according to the index of the displacement array; if a voxel is not in the neighborhood, sets its value to NaN
     * @param x coord along X-axis
     * @param y coord along Y-axis
     * @param z coord along Z-axis
     * @param image 
     */
    public void setPixelsByIndex(int x, int y, int z, Image image) {
        valueCount=0;
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    values[i]=image.getPixel(xx, yy, zz);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    values[i]=image.getPixel(xx, yy, z);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        }
    }
    
    @Override public int getSize() {return dx.length;}

    @Override public float[] getPixelValues() {
        return values;
    }
    @Override public int getValueCount() {
        return valueCount;
    }
    @Override public float[] getDistancesToCenter() {
        return distances;
    }
    
    @Override public float getMin(int x, int y, int z, Image image, float... outOfBoundValue) {
        int xx, yy;
        float min = Float.MAX_VALUE;
        boolean returnOutOfBoundValue = outOfBoundValue.length>=1;
        float ofbv = returnOutOfBoundValue? outOfBoundValue[0] : 0;
        float temp;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    temp=image.getPixel(xx, yy, zz);
                    if (temp<min) min=temp;
                } else if (returnOutOfBoundValue) return ofbv;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    temp=image.getPixel(xx, yy, z);
                    if (temp<min) min=temp;
                } else if (returnOutOfBoundValue) return ofbv;
            }
        }
        if (min==Float.MAX_VALUE) min = Float.NaN;
        return min;
    }

    @Override public float getMax(int x, int y, int z, Image image) {
        int xx, yy;
        float max = -Float.MAX_VALUE;
        float temp;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    temp=image.getPixel(xx, yy, zz);
                    if (temp>max) max=temp;
                }
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    temp=image.getPixel(xx, yy, z);
                    if (temp>max) max=temp;
                }
            }
        }
        if (max==Float.MIN_VALUE) max = Float.NaN;
        return max;
    }
    @Override public boolean hasNonNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNonNull) {
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    if (image.getPixel(xx, yy, zz)!=0) return true;
                } else if (outOfBoundIsNonNull) return true;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    if (image.getPixel(xx, yy, z)!=0) return true;
                } else if (outOfBoundIsNonNull) return true;
            }
        }
        return false;
    }
    @Override public boolean hasNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNull) {
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz)) {
                    if (image.getPixel(xx, yy, zz)==0) return true;
                } else if (outOfBoundIsNull) return true;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z)) {
                    if (image.getPixel(xx, yy, z)==0) return true;
                } else if (outOfBoundIsNull) return true;
            }
        }
        return false;
    }
    
    public boolean is3D() {
        return is3D;
    }
}