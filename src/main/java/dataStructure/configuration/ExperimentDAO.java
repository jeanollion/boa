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
package dataStructure.configuration;

import com.mongodb.MongoClient;
import dataStructure.configuration.Experiment;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class ExperimentDAO extends DAO<Experiment>{
    Experiment cache;
    Morphium morphium;
    public ExperimentDAO(Morphium morphium) {
        super(morphium, Experiment.class);
        morphium.ensureIndicesFor(Experiment.class);
        this.morphium=morphium;
    }
    public Experiment getExperiment() {
        if (cache!=null) return cache;
        else {
            cache = this.getQuery().get();
            return cache;
        }
    }
    
    public void store(Experiment xp) {
        cache=xp;
        morphium.store(xp);
    }
    
    public void setToCache(Experiment xp) {
        cache=xp;
    }
    
    public Experiment checkAgainstCache(Experiment e) {
        if (cache==null) setToCache(e);
        return cache;
    }
    
    public void clearCache() {cache=null;}
}
