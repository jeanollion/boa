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
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.plugins.TransformationTimeIndependent;
import boa.image.processing.Filters;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class Sigma implements PreFilter, Filter {
    ScaleXYZParameter radius = new ScaleXYZParameter("Radius", 3, 1, true).setToolTipText("Radius in pixel");
    ScaleXYZParameter medianRadius = new ScaleXYZParameter("Median Filtering Radius", 0, 1, true).setToolTipText("Radius for median filtering, prior to sigma, in pixel. 0 = no median filtering");
    Parameter[] parameters = new Parameter[]{radius};
    public Sigma() {}
    public Sigma(double radius) {
        this.radius.setScaleXY(radius);
        this.radius.setUseImageCalibration(true);
    }
    public Sigma(double radiusXY, double radiusZ) {
        this.radius.setScaleXY(radiusXY);
        this.radius.setScaleZ(radiusZ);
    }
    public Sigma setMedianRadius(double radius) {
        this.medianRadius.setScaleXY(radius);
        this.medianRadius.setUseImageCalibration(true);
        return this;
    }
    public Sigma setMedianRadius(double radiusXY, double radiusZ) {
        this.medianRadius.setScaleXY(radiusXY);
        this.medianRadius.setScaleZ(radiusZ);
        return this;
    }
    @Override
    public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, radius.getScaleXY(), radius.getScaleZ(input.getScaleXY(), input.getScaleZ()), medianRadius.getScaleXY(), medianRadius.getScaleZ(input.getScaleXY(), input.getScaleZ()));
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ, double medianXY, double medianZ) {
        if (medianXY>1) input = Filters.median(input, null, Filters.getNeighborhood(medianXY, medianZ, input));
        return Filters.sigma(input, null, Filters.getNeighborhood(radiusXY, radiusZ, input));
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) { }
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }
    @Override 
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return runPreFilter(image, null);
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}