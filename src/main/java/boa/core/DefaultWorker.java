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
package boa.core;

import boa.gui.GUI;
import boa.ui.UserInterface;
import ij.IJ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingWorker;
import boa.utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public class DefaultWorker extends SwingWorker<Integer, String>{
    protected final WorkerTask task;
    protected Runnable endOfWork;
    protected int[] taskIdx;
    protected UserInterface gui;
    public static DefaultWorker execute(WorkerTask t, int maxTaskIdx) {
        return execute(t, maxTaskIdx, GUI.hasInstance()?GUI.getInstance():null);
    }
    public static DefaultWorker execute(WorkerTask t, int maxTaskIdx, UserInterface gui) {
        DefaultWorker res = new DefaultWorker(t, maxTaskIdx, gui);
        res.execute();
        return res;
    }
    public static void executeInForeground(WorkerTask t, int maxTaskIdx) {
        for (int i =0; i<maxTaskIdx; ++i) t.run(i);
    }
    public DefaultWorker(WorkerTask task, int maxTaskIdx, UserInterface gui) {
        this.task=task;
        this.gui=gui;
        taskIdx = ArrayUtil.generateIntegerArray(0, maxTaskIdx);
        if (gui!=null) {
            addPropertyChangeListener(new PropertyChangeListener() {
                @Override    
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress = (Integer) evt.getNewValue();
                        gui.setProgress(progress);
                    }
                }
            });
        }
    }
    @Override
    protected Integer doInBackground() throws Exception {
        int count = 0;
        for (int i : taskIdx) {
            String message = task.run(i);
            if (message!=null&&!"".equals(message)) publish(message);
            setProgress(100 * (++count) / taskIdx.length);
        }
        return 0;
    }
    
    @Override
    protected void process(List<String> strings) {
        if (gui!=null) {
            for (String s : strings) gui.setMessage(s);
        } 
    }

    @Override 
    public void done() {
        if (this.endOfWork!=null) endOfWork.run();
        setProgress(0);
        if (gui!=null) {
            gui.setMessage("End of Jobs");
            gui.setRunning(false);
        } //else System.out.println("No GUI. End of JOBS");
    }
    public DefaultWorker setEndOfWork(Runnable endOfWork) {
        this.endOfWork=endOfWork;
        return this;
    }
    public DefaultWorker appendEndOfWork(Runnable endOfWork) {
        Runnable oldEnd = this.endOfWork;
        this.endOfWork = () -> {
            oldEnd.run();
            endOfWork.run();
        };
        return this;
    }
    public static interface WorkerTask {
        public String run(int i);
    }
}