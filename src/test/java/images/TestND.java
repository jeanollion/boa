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
package images;

import image.ImageReader;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class TestND {
    //@Test
    public void testDim() {
        ImageReader r = new ImageReader("/data/Images/mg6300WT_LB_LR62silicium_oil37.nd2");
        int[][] stc = r.getSTCNumbers();
        System.out.println("number of series: "+stc.length+ " time points: "+stc[0][0]+ " channels: "+stc[0][1]);
        //>5min .. 60 series 985 timePoints 1 channel
    }
}