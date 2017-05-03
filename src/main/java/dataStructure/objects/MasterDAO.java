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
package dataStructure.objects;

import dataStructure.configuration.Experiment;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author jollion
 */
public interface MasterDAO {
    public void delete();
    public void clearCache();
    
    public ObjectDAO getDao(String fieldName);
    
    public String getDBName();
    public String getDir();
    public void deleteAllObjects();
    public void reset();
    
    // experiments
    public Experiment getExperiment();
    public void updateExperiment();
    public void setExperiment(Experiment xp);
    
    // selections
    public SelectionDAO getSelectionDAO();
    
    // static methods
    public static ObjectDAO getDao(MasterDAO db, int positionIdx) {
        String p = db.getExperiment().getPosition(positionIdx).getName();
        return db.getDao(p);
    }
}
