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
import boa.gui.imageInteraction.ImageDisplayer;
import static core.Processor.logger;
import ij.ImagePlus;
import ij.measure.Calibration;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.ImageOperations;
import image.ImagescienceWrapper;
import image.TypeConverter;
import imagescience.feature.Differentiator;
import imagescience.feature.Hessian;
import imagescience.feature.Laplacian;
import imagescience.feature.Structure;
import imagescience.image.FloatImage;
import imagescience.segment.Thresholder;
import imageware.Builder;
import imageware.ImageWare;
import java.util.ArrayList;
import java.util.Vector;
import utils.ThreadRunner;

/**
 *
 * @author jollion
 */
public class ImageFeatures {
    public static void hysteresis(Image image, double lowval, double highval) {
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        final Thresholder thres = new Thresholder();
        thres.hysteresis(is, lowval, highval);
    }
    
    public static ImageFloat[] structureTransform(Image image, double smoothScale, double integrationScale, boolean overrideIfFloat) {
        ImageFloat[] res = new ImageFloat[image.getScaleZ()==1?2:3];
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        double sscale = smoothScale;
        double iscale = integrationScale;
        sscale *= image.getScaleXY();
        iscale *= image.getScaleXY();
        
        Vector vector = (new Structure()).run(duplicate?is.duplicate():is, sscale, iscale);
        for (int i=0;i<res.length;i++) res[i] = (ImageFloat)ImagescienceWrapper.wrap((imagescience.image.Image)vector.get(i));
        
        for (int i = 0; i < res.length; i++) {
            res[i].setName(image.getName() + ":structure:" + (i + 1));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
        }
        return res;
    }
    
    public static ImageFloat getDerivative(Image image, double scale, int xOrder, int yOrder, int zOrder, boolean overrideIfFloat) {
        if (image.getSizeZ()==1) zOrder=0;
        if (image.getSizeY()==1) yOrder=0;
        if (image.getSizeX()==1) xOrder=0;
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        scale *= (double) image.getScaleXY(); // FIXME scaleZ?
        final Differentiator differentiator = new Differentiator();
        ImageFloat res = (ImageFloat)ImagescienceWrapper.wrap(differentiator.run(duplicate?is.duplicate():is, scale, xOrder, yOrder, zOrder));
        res.setCalibration(image);
        res.resetOffset().addOffset(image);
        return res;
    }
    
    public static ImageFloat[] getGradient(Image image, double scale, boolean overrideIfFloat) {
        final int dims = image.getSizeZ()==1?2:3;
        final ImageFloat[] res = new ImageFloat[dims];
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        scale *= (double) image.getScaleXY(); // FIXME scaleZ?
        final Differentiator differentiator = new Differentiator();
        for (int i =0;i<dims; i++) {
            boolean dup= i==dims-1?duplicate : image instanceof ImageFloat;
            res[i] = (ImageFloat)ImagescienceWrapper.wrap(differentiator.run(dup?is.duplicate():is, scale, i==0?1:0, i==1?1:0, i==2?1:0));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
        }
        return res;
    }
    
    public static ImageFloat getGradientMagnitude(Image image, double scale, boolean overrideIfFloat) {
        ImageFloat[] grad = getGradient(image, scale, overrideIfFloat);
        ImageFloat res = new ImageFloat(image.getName() + ":gradientMagnitude", image);
        final float[][] pixels = res.getPixelArray();
        if (grad.length == 3) {
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            final float[][] grad2 = grad[2].getPixelArray();
            int offY, off;
            for (int z = 0; z< image.getSizeZ(); ++z) {
                for (int y = 0; y< image.getSizeY(); ++y) {
                    offY = y * sizeX;
                    for (int x = 0; x< sizeX; ++x) {
                        off = x + offY;
                        pixels[z][off] = (float) Math.sqrt(grad0[z][off] * grad0[z][off] + grad1[z][off] * grad1[z][off] + grad2[z][off] * grad2[z][off]);
                    }
                }
            }
        } else {
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            int offY, off;
            for (int y = 0; y< image.getSizeY(); ++y) {
                offY = y * sizeX;
                for (int x = 0; x< sizeX; ++x) {
                    off = x + offY;
                    pixels[0][off] = (float) Math.sqrt(grad0[0][off] * grad0[0][off] + grad1[0][off] * grad1[0][off]);
                }
            }
        }
        
        
        /*if (grad.length == 3) {
            final ThreadRunner tr = new ThreadRunner(0, image.getSizeY(), nbCPUs);
            final int sizeZ = image.getSizeZ();
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            final float[][] grad2 = grad[2].getPixelArray();
            for (int i = 0; i < tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                        new Runnable() {
                            public void run() {
                                int offY, off;
                                for (int y = tr.ai.getAndIncrement(); y < tr.end; y = tr.ai.getAndIncrement()) {
                                    offY = y * sizeX;                                    
                                    for (int x = 0; x < sizeX; ++x) {
                                        off = x + offY;
                                        for (int z = 0; z< sizeZ; ++z) pixels[z][off] = (float) Math.sqrt(grad0[z][off] * grad0[z][off] + grad1[z][off] * grad1[z][off] + grad2[z][off] * grad2[z][off]);
                                    }

                                }
                            }
                        });
            }
            tr.startAndJoin();
        } else {
            final ThreadRunner tr = new ThreadRunner(0, image.getSizeY(), nbCPUs);
            final int sizeX = image.getSizeX();
            final float[][] grad0 = grad[0].getPixelArray();
            final float[][] grad1 = grad[1].getPixelArray();
            for (int i = 0; i < tr.threads.length; i++) {
                tr.threads[i] = new Thread(
                        new Runnable() {
                            public void run() {
                                int offY, off;
                                for (int y = tr.ai.getAndIncrement(); y < tr.end; y = tr.ai.getAndIncrement()) {
                                    offY = y * sizeX;                                    
                                    for (int x = 0; x < sizeX; ++x) {
                                        off = x + offY;
                                        pixels[0][off] = (float) Math.sqrt(grad0[0][off] * grad0[0][off] + grad1[0][off] * grad1[0][off]);
                                    }

                                }
                            }
                        });
            }
            tr.startAndJoin();
        }*/
        return res;
    }
    
    public static ImageFloat getLaplacian(Image image, double scale, boolean invert, boolean overrideIfFloat) {
        double s = scale * image.getScaleXY();
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        ImageFloat res = (ImageFloat)ImagescienceWrapper.wrap(new Laplacian().run(duplicate?is.duplicate():is, s));
        if (invert) ImageOperations.affineOperation(res, res, -scale*scale, 0);
        else ImageOperations.affineOperation(res, res, scale*scale, 0);
        res.setCalibration(image).resetOffset().addOffset(image).setName(image.getName() + ":laplacian:"+scale);
        return res;
    }
    
    public static ImageFloat[] getHessian(Image image, double scale, boolean overrideIfFloat) {
        ImageFloat[] res = new ImageFloat[image.getSizeZ()==1?2:3];
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        double s = scale * image.getScaleXY();
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        Vector vector = new Hessian().run(duplicate?is.duplicate():is, s, false);
        for (int i=0;i<res.length;i++) {
            res[i] = (ImageFloat)ImagescienceWrapper.wrap((imagescience.image.Image) vector.get(i));
            res[i].setCalibration(image);
            res[i].resetOffset().addOffset(image);
            res[i].setName(image.getName() + ":hessian" + (i + 1));
            ImageOperations.affineOperation(res[i], res[i], scale*scale, 0);
        }
        return res;
    }
    public static ImageFloat[] getHessianMaxAndDeterminant(Image image, double scale, boolean overrideIfFloat) {
        ImageFloat[] hess=getHessian(image, scale, overrideIfFloat);
        ImageFloat det = hess[hess.length-1];
        if (hess.length==2) {
            for (int xy = 0; xy<hess[0].getSizeXY(); ++xy) det.setPixel(xy, 0, hess[0].getPixel(xy, 0)*hess[1].getPixel(xy, 0)); 
        } else if (hess.length==3) {
            //double pow = 1d/3d;
            for (int z = 0; z<hess[0].getSizeZ(); ++z) {
                for (int xy = 0; xy<hess[0].getSizeXY(); ++xy) {
                    det.setPixel(xy, z, hess[0].getPixel(xy, z)*hess[1].getPixel(xy, z)*hess[2].getPixel(xy, z)); 
                }
            }
        } else {
            logger.warn("wrong number of dimension {}, hessian determient cannot be computed", hess.length);
            return null;
        }
        
        return new ImageFloat[]{hess[0], det};
    }
    
    public static Image getScaleSpaceHessianDet(Image plane, double[] scales) {
        if (plane.getSizeZ()>1) throw new IllegalArgumentException("2D image only");
        ArrayList<ImageFloat> planes = new ArrayList<ImageFloat>(scales.length);
        for (double s : scales) planes.add(ImageFeatures.getHessianMaxAndDeterminant(plane, s, false)[1]);
        return Image.mergeZPlanes(planes);
    }
    
    public static Image getScaleSpaceLaplacian(Image plane, double[] scales) {
        if (plane.getSizeZ()>1) throw new IllegalArgumentException("2D image only");
        ArrayList<ImageFloat> planes = new ArrayList<ImageFloat>(scales.length);
        for (double s : scales) planes.add(ImageFeatures.getLaplacian(plane, s, true, false));
        return Image.mergeZPlanes(planes);
    }
    
    private static double sqrt(double number) {
        return number>=0?Math.sqrt(number):-Math.sqrt(-number);
    }
    
    public static ImageFloat gaussianSmooth(Image image, double scaleXY, double scaleZ, boolean overrideIfFloat) {
        if (image.getSizeZ()>1 && scaleZ<=0) throw new IllegalArgumentException("Scale Z should be >0 ");
        else if (scaleZ<=0) scaleZ=1;
        if (scaleXY<=0) throw new IllegalArgumentException("Scale XY should be >0 ");
        float old_scaleXY = image.getScaleXY();
        float old_scaleZ = image.getScaleZ();
        image.setCalibration(1, (float)(scaleXY / scaleZ));
        boolean duplicate = !((image instanceof ImageFloat) && overrideIfFloat);
        final imagescience.image.Image is = ImagescienceWrapper.getImagescience(image);
        Differentiator differentiator = new Differentiator();
        ImageFloat res = (ImageFloat)ImagescienceWrapper.wrap(differentiator.run(duplicate?is.duplicate():is, scaleXY, 0, 0, 0));
        image.setCalibration(old_scaleXY, old_scaleZ);
        res.setCalibration(old_scaleXY, old_scaleZ);
        res.resetOffset().addOffset(image);
        return res;
    }
    
    public static ImageFloat gaussianSmoothScaled(Image image, double scaleXY, double scaleZ, boolean overrideIfFloat) {
        ImageFloat res = gaussianSmooth(image, scaleXY, scaleZ, overrideIfFloat);
        ImageOperations.affineOperation(image, image, scaleXY, 0);
        return res;
    }
    
    public static ImageFloat differenceOfGaussians(Image image, double scaleXYMin, double scaleXYMax, double ratioScaleZ, boolean trimNegativeValues) {
        Image bcg = gaussianSmooth(image, scaleXYMax, scaleXYMax*ratioScaleZ, false);
        Image fore = scaleXYMin>0?gaussianSmooth(image, scaleXYMin, scaleXYMin*ratioScaleZ, false):image;
        ImageFloat res  = (ImageFloat)ImageOperations.addImage(fore, bcg, bcg, -1);
        if (trimNegativeValues) ImageOperations.trim(res, 0, true, true);
        return res;
    }
    
    public static ImageFloat LoG(Image image, double radX, double radZ) {
        ImageWare in = Builder.create(IJImageWrapper.getImagePlus(image), 3);
            ImageWare res;
            if (image.getSizeZ() > 1) {
                res = LoG.doLoG(in, radX, radX, radZ);
            } else {
                res = LoG.doLoG(in, radX, radX);
            }
            res.invert();
        return (ImageFloat)IJImageWrapper.wrap(new ImagePlus("LoG of "+image.getName(), res.buildImageStack())).setCalibration(image).resetOffset().addOffset(image);
    }
    
}
