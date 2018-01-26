/*
 * Copyright (C) 2015 nasique
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
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ParameterUI;

/**
 *
 * @author jollion
 */
public class TimePointParameter extends BoundedNumberParameter {
    private int timePointNumber=-1;
    boolean useRawInputFrames;
    
    public TimePointParameter(String name, int defaultTimePoint, boolean useRawInputFrames) {
        super(name, 0, defaultTimePoint, 0, null);
        this.useRawInputFrames=useRawInputFrames;
    }
    public TimePointParameter(String name, int defaultTimePoint) {
        this(name, defaultTimePoint, false);
    }
    public TimePointParameter(String name) {
        this(name, 0, false);
    }
    public TimePointParameter() {this("");}
    
    
    public int getMaxTimePoint() {
        if (timePointNumber==-1) {
            timePointNumber = ParameterUtils.getTimePointNumber(this, useRawInputFrames);
            //logger.debug("tp param: {} after trim: {} tpnb: {}", name, useRawInputFrames, timePointNumber);
            if (timePointNumber>0) super.upperBound=timePointNumber-1;
        }
        return Math.max(0, timePointNumber-1);
    }

    public void setUseRawInputFrames(boolean useRawInputFrames) {
        this.useRawInputFrames = useRawInputFrames;
    }
    
    public void setTimePoint(int timePoint) {
        super.setValue(timePoint);
    }
    
    private int checkWithBounds(int timePoint) {
        int max = getMaxTimePoint();
        if (max>=0) {
            if (timePoint>max) return Math.max(0, max);
            else return Math.max(0, timePoint);
        } else return 0;
    }
    
    public int getSelectedTimePoint() {
        return checkWithBounds(super.getValue().intValue());
    }
    
    @Override public ParameterUI getUI() {
        getMaxTimePoint(); // sets the upper bound
        return super.getUI();
    }
    
}