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
package TestUtils;

import static TestUtils.Utils.logger;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class TestClass {
    @Test
    public void test() {
        Number n = -1d;
        logger.info("bytevalue of -1: {} " , n.byteValue()& 0xff);
        logger.info("shortvalue of -1: {} " , n.shortValue()& 0xffff);
        logger.info("intvalue of -1: {} " , n.intValue());
        
        
        logger.info("(byte)255: {}" , ((byte)255)& 0xff);
        logger.info("(byte)0: {}" , ((byte)0)& 0xff);
        logger.info("7+8/2: {}" , (7+8)/2);
    }
}