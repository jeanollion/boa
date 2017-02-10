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
package plugins.plugins.thresholders;

import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageOperations;
import java.util.List;
import plugins.Thresholder;
import processing.Filters;
import processing.ImageFeatures;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class LocalContrastThresholder implements Thresholder {
    public static Image getLocalContrast(Image input, double scale) {
        Image localContrast=ImageFeatures.getGradientMagnitude(input, scale, false);
        Image smooth = ImageFeatures.gaussianSmooth(input, scale, scale * input.getScaleXY() / input.getScaleZ() , false);
        ImageOperations.divide(localContrast, smooth, localContrast);
        return localContrast;
    }
    public static double getThreshold(Image input, double min, double max, double thld, boolean backgroundUnderThld) {
        final Image localContrast= getLocalContrast(input, 2);
        double[] meanCount = new double[2];
        input.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
            double v = localContrast.getPixel(x, y, z);
            if (v>=min && v<=max) {
                v = input.getPixel(x, y, z);
                if (v > thld == backgroundUnderThld) {
                    meanCount[0]+=v;
                    ++meanCount[1];
                }
            }
        });
        meanCount[0]/=meanCount[1];
        return meanCount[0];
    }
    
    
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return getThreshold(input, 0.15, 1, Double.POSITIVE_INFINITY, true);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}