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
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class StructureObjectMask extends ImageObjectInterface {

    BoundingBox[] offsets;
    List<StructureObject> objects;
    BoundingBox additionalOffset;

    public StructureObjectMask(StructureObject parent, int childStructureIdx) {
        super(parent, childStructureIdx);
        this.additionalOffset = new BoundingBox(0, 0, 0);
    }

    public StructureObjectMask(StructureObject parent, int childStructureIdx, BoundingBox additionalOffset) {
        super(parent, childStructureIdx);
        this.additionalOffset = additionalOffset;
    }

    @Override
    public ImageObjectInterfaceKey getKey() {
        return new ImageObjectInterfaceKey(parent, childStructureIdx, false);
    }

    public BoundingBox[] getOffsets() {
        if (offsets==null || objects==null || offsets.length!=objects.size()) reloadObjects();
        return offsets;
    }
    
    public void reloadObjects() {
        if (childStructureIdx == parent.getStructureIdx()) {
            objects = new ArrayList<StructureObject>(1);
            objects.add(parent);
            offsets = new BoundingBox[1];
            offsets[0] = parent.getRelativeBoundingBox(parent).translate(additionalOffset);
        } else {
            objects = parent.getChildren(childStructureIdx);
            offsets = new BoundingBox[objects.size()];
            for (int i = 0; i < offsets.length; ++i) {
                offsets[i] = objects.get(i).getRelativeBoundingBox(parent).translate(additionalOffset);
            }
        }
    }

    @Override public ArrayList<Pair<StructureObject, BoundingBox>> getObjects() {
        getOffsets();
        ArrayList<Pair<StructureObject, BoundingBox>> res = new ArrayList<Pair<StructureObject, BoundingBox>>(objects.size());
        for (int i = 0; i < offsets.length; ++i) {
            res.add(new Pair(objects.get(i), offsets[i]));
        }
        return res;
    }

    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (objects == null) reloadObjects();
        if (is2D) {
            z = 0;
        }
        getOffsets();
        for (int i = 0; i < offsets.length; ++i) {
            if (offsets[i].contains(x, y, z)) {
                if (objects.get(i).getMask().insideMask(x - offsets[i].getxMin(), y - offsets[i].getyMin(), z - offsets[i].getzMin())) {
                    return new Pair(objects.get(i), offsets[i]);
                }
            }
        }
        return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        if (is2D && selection.getSizeZ()>0) selection=new BoundingBox(selection.getxMin(), selection.getxMax(), selection.getyMin(), selection.getyMax(), 0, 0);
        getOffsets();
        for (int i = 0; i < offsets.length; ++i) if (offsets[i].hasIntersection(selection)) list.add(new Pair(objects.get(i), offsets[i]));
    }

    @Override
    public BoundingBox getObjectOffset(StructureObject object) {
        if (object == null) {
            return null;
        }
        if (objects==null) reloadObjects();
        int i = this.childStructureIdx==object.getStructureIdx()? objects.indexOf(object) : -1;
        if (i >= 0) {
            return offsets[i];
        } else {
            StructureObject p = object.getFirstCommonParent(parent); // do not display objects that don't have a common parent not root
            if (p!=null && !p.isRoot()) return object.getRelativeBoundingBox(parent).translate(additionalOffset);
            else return null;
        }
    }

    @Override
    public ImageInteger generateImage() {
        ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Segmented Image of structure: " + childStructureIdx, getMaxLabel(), parent.getMaskProperties());
        draw(displayImage);
        return displayImage;
    }

    @Override
    public Image generateRawImage(int structureIdx) {
        return parent.getRawImage(structureIdx);
    }

    @Override
    public void draw(ImageInteger image) {
        if (objects == null) reloadObjects();
        for (int i = 0; i < getOffsets().length; ++i) {
            objects.get(i).getObject().drawWithoutObjectOffset(image, objects.get(i).getObject().getLabel(), offsets[i]);
        }
    }

    public int getMaxLabel() {
        if (objects == null) reloadObjects();
        int maxLabel = 0;
        for (StructureObject o : objects) {
            if (o.getObject().getLabel() > maxLabel) {
                maxLabel = o.getObject().getLabel();
            }
        }
        return maxLabel;
    }

    @Override
    public boolean isTimeImage() {
        return false;
    }



}
