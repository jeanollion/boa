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
package plugins;

import dataStructure.objects.Object3D;
import java.util.List;

/**
 *
 * @author jollion
 */
public interface SegmenterSplitAndMerge extends Segmenter {
    /**
     * Split an object into several objects
     * @param o object to be splitted
     * @param result list in which put the resulting objects
     * @return a value representing the cost of splitting the object, NaN if the object could not be split
     */
    public double split(Object3D o, List<Object3D> result);
    /**
     * Merge two objects into one single object
     * @param o1 object to be splitted
     * @param o2 object to be splitted
     * @param result a list in which the resulting merged object will be inserted
     * @return a value representing the cost of merging the two objects, NaN if the two objects are not in contact
     */
    public double merge(Object3D o1, Object3D o2, List<Object3D> result);
}