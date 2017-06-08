/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;
import static core.Processor.logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
/**
Copyright (C) Jean Ollion

License:
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ThreadRunner {

    /** Start all given threads and wait on each of them until all are done.
     * From Stephan Preibisch's Multithreading.java class. See:
     * http://repo.or.cz/w/trakem2.git?a=blob;f=mpi/fruitfly/general/MultiThreading.java;hb=HEAD
     */

    /* To initiate Threads:
    final ThreadRunner tr = new ThreadRunner(0, sizeZ, multithread?0:1);
    for (int i = 0; i<tr.threads.length; i++) {
        tr.threads[i] = new Thread(
            new Runnable() {
                public void run() {
                    for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {

                    }
                }
            }
        );
    } 
    tr.startAndJoin();

     * 
     */
    public final int start, end;
    public final Thread[] threads;
    public final AtomicInteger ai;
    public final List<Pair<String, Exception>> errors = new ArrayList<>();
    public ThreadRunner(int start, int end) {
        this(start, end, 0);
    }
    
    public int size() {
        return this.threads.length;
    }
    public static boolean leaveOneCPUFree = true;
    /**
     * 
     * @param start inclusive
     * @param end exclusive 
     * @param cpulimit 
     */
    public ThreadRunner(int start, int end, int cpulimit) {
        this.start=start;
        this.end=end;
        this.ai= new AtomicInteger(this.start);
        int nb = getNbCpus();
        if (cpulimit>0 && nb>cpulimit) {
            nb=cpulimit;
            if (leaveOneCPUFree && nb==getNbCpus() && nb>1) nb--;
        }
       
        this.threads = new Thread[nb];
        
    }

    public void startAndJoin() {
       startAndJoin(threads);
    }
    
    
    protected static void startAndJoin(Thread[] threads) {
        for (int ithread = 0; ithread < threads.length; ++ithread) {
            threads[ithread].setPriority(Thread.NORM_PRIORITY);
            threads[ithread].start();
            //SwingUtilities.invokeLater(threads[ithread]);
            
        }

        try {
            for (int ithread = 0; ithread < threads.length; ++ithread) {
                threads[ithread].join();
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }
    

    public void resetAi(){
        ai.set(start);
    }
    
    private int getNbCpus() {
        return Math.max(1, Math.min(getMaxCPUs(), end-start));
    }
    
    public static int getMaxCPUs() {
        return Runtime.getRuntime().availableProcessors();
    }
    
    public static <T> List<Pair<String, Exception>> execute(final T[] array, final boolean setToNull, final ThreadAction<T> action) {
        return execute(array, setToNull, 0, action);
    }
    
    public static <T> List<Pair<String, Exception>> execute(final T[] array, final boolean setToNull, final int nThreadLimit, final ThreadAction<T> action) {
        if (array==null) return Collections.EMPTY_LIST;
        if (array.length==0) return Collections.EMPTY_LIST;
        if (array.length==1) {
            if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
            List<Pair<String, Exception>> errors = new ArrayList<>(1);
            try {
                action.run(array[0], 0, 0);
            } catch (Exception e) {
                errors.add(new Pair(array[0].toString(), e));
            }
            if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
            return errors;
        }
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<String> completion = new ExecutorCompletionService<>(executor);
        ... tester depuis la classe de test!!
        final ThreadRunner tr = new ThreadRunner(0, array.length, nThreadLimit);
        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i<tr.threads.length; i++) {
            final int threadIdx = i;
            //final ThreadAction<T> localAction = action
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() { 
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            try {
                                action.run(array[idx], idx,threadIdx );
                            } catch (Exception e) {
                                //logger.debug("ta {}", e);
                                synchronized(tr.errors) {
                                    tr.errors.add(new Pair(array[idx].toString(), e));
                                }
                            }
                            int currentCount = count.incrementAndGet();
                            //logger.debug("Processed: {}/{}", currentCount, array.length);
                            if (setToNull) array[idx]=null;
                        }
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
                    }
                }
            );
        }
        tr.startAndJoin();
        return tr.errors;
    }
    public static <T> List<Pair<String, Exception>> execute(Collection<T> array, final ThreadAction<T> action) {
        if (array==null) return Collections.EMPTY_LIST;
        if (array.isEmpty()) return Collections.EMPTY_LIST;
        if (array.size()==1) {
            if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
            List<Pair<String, Exception>> errors = new ArrayList<>(1);
            try {
                action.run(array.iterator().next(), 0, 0);
            } catch (Exception e) {              
                errors.add(new Pair(array.iterator().next().toString(), e));           
            }
            if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
            return errors;
        }
        final List<T> list = (array instanceof List) ? (List)array : new ArrayList(array);
        final ThreadRunner tr = new ThreadRunner(0, list.size(), 0);
        for (int i = 0; i<tr.threads.length; i++) {
            final int threadIdx = i;
            //final ThreadAction<T> localAction = action
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() { 
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            try {
                                action.run(list.get(idx), idx, threadIdx);
                            } catch (Exception e) {
                                synchronized(tr.errors) {
                                    tr.errors.add(new Pair(list.get(idx).toString(), e));
                                }
                            }
                        }
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
                    }
                }
            );
        }
        tr.startAndJoin();
        return tr.errors;
    }
    
    public static interface ThreadAction<T> {
        public void run(T object, int idx, int threadIdx);
    }
    public static interface ThreadAction2<T> extends ThreadAction<T> {
        public void setUp();
        public void tearDown();
    }
}
