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
package boa.data_structure.region_container;

import boa.data_structure.Region;
import static boa.data_structure.Region.logger;
import boa.data_structure.StructureObject;
import boa.image.BlankMask;
import boa.image.MutableBoundingBox;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */

public class RegionContainerBlankMask extends RegionContainer {
    
    public RegionContainerBlankMask(StructureObject structureObject) {
        super(structureObject);
    }
    
    public BlankMask getImage() {
        return new BlankMask(bounds, getScaleXY(), getScaleZ());
    }
    
    @Override
    public Region getObject() {
        return new Region(getImage(), structureObject.getIdx()+1, is2D);
    }
    
    @Override public void deleteObject(){bounds=null;}

    @Override
    public void relabelObject(int newIdx) {
        
    }
    protected RegionContainerBlankMask() {}
}