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
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import image.BoundingBox;
import image.Image;
import static image.Image.logger;
import image.ImageProperties;
import image.ImagescienceWrapper;
import imagescience.image.Axes;
import imagescience.image.Coordinates;
import imagescience.image.Dimensions;
import imagescience.transform.Rotate;
import imagescience.transform.Turn;
import imagescience.transform.Translate;
import imagescience.transform.Embed;
import imagescience.transform.Mirror;
/**
 *
 * @author jollion
 */
public class ImageTransformation {
    public static enum Axis {
        X, Y, Z, XY, XZ, YZ;
        public  static Axes getAxes(Axis axis) {
            if (Axis.X.equals(axis)) return new Axes(true);
            else if (Axis.Y.equals(axis)) return new Axes(false, true);
            else if (Axis.Z.equals(axis)) return new Axes(false, false, true);
            else if (Axis.XY.equals(axis)) return new Axes(true, true, false);
            else if (Axis.XZ.equals(axis)) return new Axes(true, false, true);
            else if (Axis.YZ.equals(axis)) return new Axes(false, true, true);
            else return new Axes();
        }
    };
    public static enum InterpolationScheme {
        NEAREST(0), LINEAR(1), CUBIC(2), BSPLINE3(3), OMOMS3(4), BSPLINE5(5);
        private final int value;
        InterpolationScheme(int value){
            this.value = value;
        }
        public int getValue(){return value;};
    }
    
    public static Image rotateXY(Image image, double angle, InterpolationScheme interpolation, boolean removeIncompleteRowsAndColumns) {
        if (angle%90==0) {
            return turn(image, (int)angle/90, 0, 0);
        } else {
            Image im =  rotate(image, angle, 0, 0, interpolation, false, true);
            if (removeIncompleteRowsAndColumns) {
                //int delta = (int)(Math.abs(Math.sin(angle*Math.PI/180d))*Math.max(im.getSizeX(), im.getSizeY()))+1; // cf Geages Debregas 
                double[] deltas = new double[2];
                double tanAbs = Math.abs(Math.tan(angle * Math.PI/180d));
                //logger.debug("angle: {}, tan: {}", angle, tanAbs);
                for (int i = 0; i<5; ++i) computeDeltas(tanAbs, image.getSizeX()/2, image.getSizeY()/2, deltas);
                int dX = (int)Math.ceil(deltas[0]);
                int dY = (int)Math.ceil(deltas[1]);
                Image crop =  im.crop(new BoundingBox(dX, im.getSizeX()-dX, dY, im.getSizeY()-dY, 0, image.getSizeZ()-1));
                return crop;
            } else return im;
        }
    }
    private static void computeDeltas(double tanAbs, int sX, int sY, double[] deltas) {
        double dX = tanAbs * (sY-deltas[1]);
        double dY = tanAbs * (sX-deltas[0]);
        deltas[0] = dX;
        deltas[1] = dY;
        //logger.debug("dX: {}, dY: {}", dX, dY);
    } 
    
    public static Image rotate(Image image, double zAngle, double yAngle, double xAngle, InterpolationScheme scheme, boolean fit, boolean antialiasing) {
        return ImagescienceWrapper.wrap((new  Rotate()).run(ImagescienceWrapper.getImagescience(image), zAngle, yAngle, xAngle, scheme.getValue(), fit, false, antialiasing)).setCalibration(image);
    }
    
    public static Image turn(Image image, int times90z, int times90y, int times90x) {
        return ImagescienceWrapper.wrap((new Turn()).run(ImagescienceWrapper.getImagescience(image), times90z, times90y, times90x)).setCalibration(image);
    }
    
    public static Image translate(Image image, double xTrans, double yTrans, double zTrans, InterpolationScheme scheme) {
        return ImagescienceWrapper.wrap((new  Translate()).run(ImagescienceWrapper.getImagescience(image), xTrans*image.getScaleXY(), yTrans*image.getScaleXY(), zTrans*image.getScaleZ(), scheme.getValue())).setCalibration(image);
    }
    
    public static Image resize(Image image, ImageProperties newImage, int posX, int posY, int posZ) {
        Dimensions dim = new Dimensions(newImage.getSizeX(), newImage.getSizeY(), newImage.getSizeZ(), 1, 1);
        Coordinates pos = new Coordinates(posX, posY, posZ);
        return ImagescienceWrapper.wrap((new Embed()).run(ImagescienceWrapper.getImagescience(image), dim, pos, 0)).setCalibration(image);
    }
    
    public static Image flip(Image image, Axis axis) {
        new Mirror().run(ImagescienceWrapper.getImagescience(image), Axis.getAxes(axis));
        return image;
    }
}
