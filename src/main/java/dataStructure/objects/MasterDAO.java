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

import boa.gui.GUI;
import core.ProgressCallback;
import dataStructure.configuration.Experiment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

/**
 *
 * @author jollion
 */
public interface MasterDAO {
    public static final Logger logger = LoggerFactory.getLogger(MasterDAO.class);
    public void eraseAll();
    public void clearCache();
    public void clearCache(String position);
    public ObjectDAO getDao(String fieldName);
    public boolean isReadOnly();
    public boolean setReadOnly(boolean readOnly);
    public String getDBName();
    public String getDir();
    public void deleteAllObjects();
    public void deleteExperiment();
    public static void deleteObjectsAndSelectionAndXP(MasterDAO dao) {
        dao.deleteAllObjects();
        dao.getSelectionDAO().deleteAllObjects();
        dao.deleteExperiment();
    }
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
    
    public static boolean compareDAOContent(MasterDAO dao1, MasterDAO dao2, boolean config, boolean positions, boolean selections, ProgressCallback pcb) {
        boolean sameContent = true;
        if (config) {
            boolean same = dao1.getExperiment().sameContent(dao2.getExperiment());
            if (!same) {
                pcb.log("config differs");
                sameContent = false;
            }
        }
        if (positions) {
            pcb.log("comparing positions");
            Collection<String> pos = new HashSet<>(Arrays.asList(dao1.getExperiment().getPositionsAsString()));
            pcb.log(Utils.toStringList(pos));
            Collection<String> pos2 = new HashSet<>(Arrays.asList(dao2.getExperiment().getPositionsAsString()));
            pcb.log(Utils.toStringList(pos2));
            if (!pos.equals(pos2)) {
                pcb.log("position count differs");
                sameContent = false;
            }
            else {
                pcb.log("position count: "+pos.size());
                pcb.incrementTaskNumber(pos.size());
                pos = new ArrayList<>(pos);
                Collections.sort((List)pos);
                for (String p : pos) {
                    pcb.log("comparing position: "+p);
                    ObjectDAO od1 = dao1.getDao(p);
                    ObjectDAO od2 = dao2.getDao(p);
                    try {
                        if (!ObjectDAO.sameContent(od1, od2, pcb)) return false;
                    } catch (Exception e) {
                        logger.error("error comparing position: "+p, e);
                        return false;
                    }
                    
                    pcb.incrementProgress();
                }
            }
        }
        return sameContent;
    }
}
