/*
 * Copyright (C) 2017 jollion
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
package plugins.plugins.postFilters;

import configuration.parameters.Parameter;
import configuration.parameters.ScaleXYZParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.ImageInteger;
import plugins.PostFilter;
import processing.Filters;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class BinaryClose implements PostFilter {
    ScaleXYZParameter scale = new ScaleXYZParameter("Closing Radius", 5, 1, true);
    @Override
    public ObjectPopulation runPostFilter(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        Neighborhood n = Filters.getNeighborhood(scale.getScaleXY(), scale.getScaleZ(parent.getScaleXY(), parent.getScaleZ()), parent.getMask());
        for (Object3D o : childPopulation.getObjects()) {
            ImageInteger closed = Filters.binaryCloseExtend(o.getMask(), n);
            o.setMask(closed);
        }
        return childPopulation;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scale};
    }
    
}