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
package plugins.plugins.measurements.objectFeatures;

import configuration.parameters.Parameter;
import configuration.parameters.SiblingStructureParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import image.BoundingBox;
import plugins.objectFeature.IntensityMeasurement;
import plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;

/**
 *
 * @author jollion
 */
public class SNR extends IntensityMeasurement {
    protected SiblingStructureParameter backgroundObject = new SiblingStructureParameter("Background Object", true);
    
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity, backgroundObject};}
    
    public SNR() {}
    
    public SNR setBackgroundObjectStructureIdx(int structureIdx) {
        backgroundObject.setSelectedStructureIdx(structureIdx);
        return this;
    }
    
    public double performMeasurement(Object3D object, BoundingBox offset) {
        if (core==null) synchronized(this) {setUpOrAddCore(null);}
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(super.parent.getObject(), super.parent.getBounds().duplicate().reverseOffset());
        //double fore = super.core.getIntensityMeasurements(object, offset).mean;
        double fore = super.core.getIntensityMeasurements(object, offset).max;
        return ( fore-iParent.mean ) / iParent.sd;
    }

    public String getDefaultName() {
        return "snr";
    }
    
}