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
package boa.plugins.plugins.pre_filter;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.plugins.TransformationTimeIndependent;
import boa.image.processing.Filters;
import boa.image.processing.IJFFTBandPass;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class BandPass implements PreFilter, Filter {
    NumberParameter min = new BoundedNumberParameter("Remove structures under size (pixels)", 1, 0, 0, null);
    NumberParameter max = new BoundedNumberParameter("Remove structures over size (pixels)", 1, 100, 0, null); 
    ChoiceParameter removeStripes = new ChoiceParameter("Remove Stripes", new String[]{"None", "Horizontal", "Vertical"}, "None", false);
    NumberParameter stripeTolerance = new BoundedNumberParameter("Stripes tolerance (%)", 1, 100, 0, 100);
    ConditionalParameter stripes = new ConditionalParameter(removeStripes).setDefaultParameters(new Parameter[]{stripeTolerance}).setActionParameters("None", new Parameter[0]);
    Parameter[] parameters = new Parameter[]{min, max, stripes};
    
    public BandPass() {}
    public BandPass(double min, double max) {
        this(min, max, 0, 0);
    }
    public BandPass(double min, double max, int removeStripes, double stripeTolerance) {
        this.min.setValue(min);
        this.max.setValue(max);
        this.removeStripes.setSelectedIndex(removeStripes);
        this.stripeTolerance.setValue(stripeTolerance);
    }
    @Override public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, min.getValue().doubleValue(), max.getValue().doubleValue(), removeStripes.getSelectedIndex(), stripeTolerance.getValue().doubleValue());
    }
    
    public static Image filter(Image input, double min, double max, int stripes, double stripeTolerance) {
        return IJFFTBandPass.bandPass(input, min, max, stripes, stripeTolerance);
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override public void computeConfigurationData(int channelIdx, InputImages inputImages) { }
    @Override public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }
    @Override public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return filter(image, min.getValue().doubleValue(), max.getValue().doubleValue(), removeStripes.getSelectedIndex(), stripeTolerance.getValue().doubleValue());
    }

    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
