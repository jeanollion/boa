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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ObjectClassParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.DevPlugin;
import boa.plugins.Measurement;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class ChromaticShiftBeads implements Measurement, DevPlugin{
    protected ObjectClassParameter structure = new ObjectClassParameter("Structure 1", 0, false, false);
    protected ObjectClassParameter structure2 = new ObjectClassParameter("Structure 2", 1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure, structure2};

    public ChromaticShiftBeads() {}
    
    public ChromaticShiftBeads(int structureIdx1, int structureIdx2) {
       structure.setSelectedIndex(structureIdx1);
       structure2.setSelectedIndex(structureIdx2);
    }
    
    public int getCallObjectClassIdx() {
        return structure.getFirstCommonParentObjectClassIdx(structure2.getSelectedIndex());
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject("dXPix", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("dYPix", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("dZSlice", structure.getSelectedIndex()));
        return res;
    }

    public void performMeasurement(StructureObject object) {
        
        List<StructureObject> objects1 = object.getChildren(structure.getSelectedIndex());
        List<StructureObject> objects2 = object.getChildren(structure2.getSelectedIndex());
        logger.debug("chromatic shift: s1: {}, count: {}, s2: {}, count: {}", structure.getSelectedIndex(), objects1.size(), structure2.getSelectedIndex(), objects2.size());
        // for each object from s1, get closest object from s2
        for (StructureObject o1 : objects1) {
            StructureObject closest = null;
            double dist = Double.MAX_VALUE;
            for (StructureObject o2 : objects2) {
                double d = GeometricalMeasurements.getDistance(o1.getRegion(), o2.getRegion());
                if (d<dist) {
                    dist = d;
                    closest = o2;
                }
            }
            if (closest !=null) {
                Point c1 = o1.getRegion().getMassCenter(object.getRawImage(structure.getSelectedIndex()), false);
                Point c2 = closest.getRegion().getMassCenter(object.getRawImage(structure2.getSelectedIndex()), false);
                o1.getMeasurements().setValue("dXPix", c2.get(0)-c1.get(0));
                o1.getMeasurements().setValue("dYPix", c2.get(1)-c1.get(1));
                o1.getMeasurements().setValue("dZSlice", c2.get(2)-c1.get(2));
                logger.debug("Chromatic Shift: o1: {}, closest: {} (dist: {}), dX: {}, dY: {}, dZ: {}", o1, closest, dist, c2.get(0)-c1.get(0), c2.get(1)-c1.get(1), c2.get(2)-c1.get(2));
            }
        }
        
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
