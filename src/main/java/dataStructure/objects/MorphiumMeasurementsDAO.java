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

import com.mongodb.BasicDBObject;
import com.mongodb.QueryBuilder;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import static dataStructure.objects.StructureObject.logger;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import de.caluga.morphium.writer.MorphiumWriterImpl;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class MorphiumMeasurementsDAO {
    final MorphiumMasterDAO masterDAO;
    public final String fieldName, collectionName;
    public MorphiumMeasurementsDAO(MorphiumMasterDAO masterDAO, String fieldName) {
        this.fieldName=fieldName;
        this.collectionName=getCollectionName(fieldName);
        this.masterDAO=masterDAO;
        masterDAO.m.ensureIndicesFor(Measurements.class, collectionName);
    }
    
    public static String getCollectionName(String name) {
        return "measurements_"+name;
    }
    
    protected Query<Measurements> getQuery() {
        Query<Measurements> res =  masterDAO.m.createQueryFor(Measurements.class); 
        res.setCollectionName(collectionName);
        return res;
    }
    
    public Measurements getObject(ObjectId id) {
        Measurements m =  getQuery().getById(id);
        if (m!=null) m.positionName=fieldName;
        return m;
    }
    
    public void store(Measurements o) {
        masterDAO.m.storeNoCache(o, collectionName, null);
    }
    
    public void delete(ObjectId id) {
        if (id==null) return;
        //masterDAO.m.delete(getQuery().f("id").eq(id));
        BasicDBObject db = new BasicDBObject().append("_id", id);
        //logger.debug("delete meas by id: {}, from colleciton: {}", db, collectionName);
        masterDAO.m.getDatabase().getCollection(collectionName).remove(db);
    }
    
    public void delete(Collection<ObjectId> id) {
        if (id.isEmpty()) return;
        WriteResult r = masterDAO.m.getDatabase().getCollection(collectionName).remove(QueryBuilder.start("_id").in(id).get(), WriteConcern.ACKNOWLEDGED);
        logger.debug("deleting: {} measurements, write result: {}", id.size(), r);
    }
    
    public void delete(Measurements o) {
        if (o==null) return;
        if (o.getId()!=null) masterDAO.m.delete(o, collectionName, null);
        //logger.debug("delete meas: {}, from colleciton: {}", o.getId(), collectionName);
    }
    
    public void deleteByStructureIdx(int structureIdx) {
        //getQuery().f("structure_idx").eq(structureIdx).delete();
        masterDAO.getMorphium().getDatabase().getCollection(collectionName).remove( new BasicDBObject("structure_idx", structureIdx));
    }
    
    public void deleteAllObjects() {
        logger.debug("deleting measurements for position: {}", fieldName);
        masterDAO.m.getDatabase().getCollection(collectionName).drop();
        //masterDAO.m.clearCollection(Measurements.class, collectionName);
    }
    
    protected Query<Measurements> getQuery(int structureIdx, String... measurements) {
        Query<Measurements> q= getQuery().f("structure_idx").eq(structureIdx);
        if (measurements.length>0) q.setReturnedFields(Measurements.getReturnedFields(measurements));
        return q;
    }
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        List<Measurements> res = getQuery(structureIdx, measurements).asList();
        for (Measurements m : res) m.positionName=fieldName;
        Collections.sort(res);
        return res;
    }
}
