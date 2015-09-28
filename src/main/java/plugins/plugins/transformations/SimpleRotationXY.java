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
package plugins.plugins.transformations;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class SimpleRotationXY implements TransformationTimeIndependent {
    NumberParameter angle = new NumberParameter("Angle (degree)", 4, 0);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", ImageTransformation.InterpolationScheme.getValues(), ImageTransformation.InterpolationScheme.LINEAR.toString(), false);
    Parameter[] parameters = new Parameter[]{angle, interpolation};
    
    public void computeParameters(int structureIdx, StructureObjectPreProcessing structureObject) {
        
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return ImageTransformation.rotateXY(image, angle.getValue().floatValue(), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public Object[] getConfigurationData() {
        return null;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }
}