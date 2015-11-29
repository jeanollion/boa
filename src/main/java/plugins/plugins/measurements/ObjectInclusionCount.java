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
package plugins.plugins.measurements;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import java.util.ArrayList;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class ObjectInclusionCount implements Measurement {
    protected StructureParameter structureContainer = new StructureParameter("Containing Structure", -1, false, false);
    protected StructureParameter structureToCount = new StructureParameter("Structure to count", -1, false, false);
    protected BoundedNumberParameter percentageInclusion = new BoundedNumberParameter("Minimum percentage of inclusion", 0, 100, 0, 100);
    protected TextParameter inclusionText = new TextParameter("Inclusion Key Name", "ObjectNumber", false);
    protected Parameter[] parameters = new Parameter[]{structureContainer, structureToCount, percentageInclusion, inclusionText};
    
    @Override
    public int getStructure() {
        return structureContainer.getSelectedIndex();
    }
    
    @Override
    public void performMeasurement(StructureObject object, ArrayList<StructureObject> modifiedObjects) {
        int common = object.getExperiment().getFirstCommonParentStructureIdx(structureContainer.getSelectedIndex(), structureToCount.getSelectedIndex());
        StructureObject parent = object.getParent(common);
        object.setMeasurement(inclusionText.getValue(), count(parent, object, structureToCount.getSelectedIndex(), percentageInclusion.getValue().doubleValue()/100d));
        modifiedObjects.add(object);
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject(inclusionText.getValue(), structureContainer.getSelectedIndex()));
        return res;
    }
    
    public static int count(StructureObject commonParent, StructureObject container, int structureToCount, double percentageInclusion) {
        ArrayList<StructureObject> toCount = commonParent.getChildren(structureToCount);
        if (toCount==null || toCount.isEmpty()) return 0;
        int count = 0;
        BoundingBox parent = container.getBounds();
        for (StructureObject o : toCount) {
            if (o.getBounds().hasIntersection(parent)) {
                 if (percentageInclusion==0) ++count;
                 else {
                    if (o.getObject().getVoxels().isEmpty()) continue;
                    double incl = o.getObject().getIntersection(container.getObject()).size() / o.getObject().getVoxels().size();
                    if (incl>=percentageInclusion) ++count;
                 }
            }
        }
        return count;
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}