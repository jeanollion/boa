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
package dataStructure.containers;

import image.BoundingBox;
import image.Image;
import org.json.simple.JSONObject;
import utils.JSONSerializable;

/**
 *
 * @author jollion
 */

public abstract class MultipleImageContainer implements JSONSerializable{
    double scaleXY, scaleZ;
    public abstract int getFrameNumber();
    public abstract int getChannelNumber();
    public abstract int getSizeZ(int channel);
    public abstract Image getImage(int timePoint, int channel);
    public abstract Image getImage(int timePoint, int channel, BoundingBox bounds);
    public abstract void close();
    public abstract String getName();
    public float getScaleXY() {return (float)scaleXY;}
    public float getScaleZ() {return (float)scaleZ;}
    public abstract double getCalibratedTimePoint(int t, int c, int z);
    public abstract MultipleImageContainer duplicate();
    public abstract boolean singleFrame(int channel);
    public MultipleImageContainer(double scaleXY, double scaleZ) {
        this.scaleXY = scaleXY;
        this.scaleZ = scaleZ;
    }
    public abstract boolean sameContent(MultipleImageContainer other);
    public static MultipleImageContainer createImageContainerFromJSON(JSONObject jsonEntry) {
        MultipleImageContainer res=null;
        if (jsonEntry.containsKey("filePathC")) {
            res = new MultipleImageContainerChannelSerie();
        } else if (jsonEntry.containsKey("filePath")) {
            res = new MultipleImageContainerSingleFile();
        } else if (jsonEntry.containsKey("inputDir")) {
            res = new MultipleImageContainerPositionChannelFrame();
        }
        if (res!=null) res.initFromJSONEntry(jsonEntry);
        return res;
    }
    public static String getKey(int c, int z, int t) {
        return new StringBuilder(11).append(c).append(";").append(z).append(";").append(t).toString();
    }
}
