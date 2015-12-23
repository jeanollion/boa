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

import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class ObjectStoreAgent {
    final LinkedBlockingQueue<Job> queue;
    final ObjectDAO dao;
    Thread thread;
    public ObjectStoreAgent(ObjectDAO dao) {
        this.dao = dao;
        createThread();
        queue = new LinkedBlockingQueue<Job>();
    }
    
    private void createThread() {
        thread = new Thread(new Runnable() {

            public void run() {
                while (!queue.isEmpty()) executeJob(queue.poll());
                //logger.debug("StoreAgent: no more jobs to run");
            }
        });
    }
    
    private void executeJob(Job job) {
        long tStart = System.currentTimeMillis();
        job.executeJob();
        long tEnd = System.currentTimeMillis();
        //if (job.getClass()!=ClearCache.class) logger.debug("Job {} done: {} objects processed in {} ms", job.getClass().getSimpleName(), job.objects.size(), tEnd-tStart);
        //else logger.debug("ClearCache done: in {} ms", tEnd-tStart);
    }
    
    public synchronized void storeObjects(List<StructureObject> list, boolean updateTrackLinks) {
        Job job = new StoreJob(list, updateTrackLinks);
        queue.add(job);
        runThreadIfNecessary();
    }
    
    private void runThreadIfNecessary() {
        if (thread==null || !thread.isAlive()) {        
            //logger.debug("StoreAgent: thread was not running, running thread.. state: {}, isInterrupted: {}", thread!=null?thread.getState():"null", thread!=null?thread.isInterrupted():null);
            createThread();
            thread.start();
        } //else logger.debug("StoreAgent: thread was already running..,  state: {}, isInterrupted: {}", thread!=null?thread.getState():"null", thread!=null?thread.isInterrupted():null);        

    }
    
    public synchronized void upsertMeasurements(List<StructureObject> list) {
        Job job = new UpdateMeasurementJob(list);
        queue.add(job);
        runThreadIfNecessary();
    }
    
    public synchronized void clearCache(String fieldName) {
        Job j = new ClearCache(fieldName);
        queue.add(j);
        runThreadIfNecessary();
        logger.debug("ClearCache job added");
    }
    
    public void join() {
        if (thread!=null) try {
            thread.join();
        } catch (InterruptedException ex) {
            //logger.debug("ObjectStoreAgent Error: join thread", ex);
        }
    }
    
    private abstract class Job {
        protected List<StructureObject> objects;
        public Job(List<StructureObject> list) {
            this.objects=list;
        }
        public abstract void executeJob();
    }
    private class StoreJob extends Job {
        boolean updateTrackLinks;
        public StoreJob(List<StructureObject> list, boolean updateTrackLinks) {
            super(list);
            this.updateTrackLinks=updateTrackLinks;
        }

        @Override
        public void executeJob() {
            dao.storeNow(objects, updateTrackLinks);
        }
        
    }
    private class UpdateMeasurementJob extends Job{

        public UpdateMeasurementJob(List<StructureObject> list) {
            super(list);
        }
        @Override
        public void executeJob() {
            dao.upsertMeasurementsNow(objects);
            
        }
    }
    private class ClearCache extends Job{
        String fieldName;
        public ClearCache(String fieldName) {
            super(null);
            this.fieldName=fieldName;
        }
        @Override
        public void executeJob() {
            dao.clearCacheNow(fieldName);
        }
    }
}
