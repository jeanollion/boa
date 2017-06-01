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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import static core.TaskRunner.logger;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ThresholdMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import plugins.SimpleThresholder;
import plugins.Thresholder;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import static plugins.plugins.thresholders.BackgroundFit.smooth;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import utils.ArrayUtil;
import utils.Pair;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SaturateHistogramHyperfluoBacteria implements Transformation {
    PluginParameter<SimpleThresholder> thresholdBck = new PluginParameter<>("Background Threshold", SimpleThresholder.class, new BackgroundThresholder(3, 6, 3), false); //new ConstantValue(50)
    PluginParameter<SimpleThresholder> thresholdHyper = new PluginParameter<>("HyperFluo Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false); //new ConstantValue(50)
    NumberParameter foregroundProportion = new BoundedNumberParameter("Hyperfluorecent cells foreground proportion threshold", 2, 0.3, 0, 1); 
    Parameter[] parameters = new Parameter[]{thresholdBck, thresholdHyper, foregroundProportion};
    ArrayList<Double> configData = new ArrayList<>(2);
    
    public SaturateHistogramHyperfluoBacteria setForegroundProportion(double proportion) {
        this.foregroundProportion.setValue(proportion);
        return this;
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        configData.clear();
        List<Image> allImages = new ArrayList<>();
        //List<Image> imageTemp = new ArrayList<>();
        int tpMax = inputImages.getFrameNumber();
        //int count =0;
        for (int t = 0; t<tpMax; ++t) {
            Image im = inputImages.getImage(channelIdx, t);
            allImages.add(im);
            /*if (im.getSizeZ()>1) imageTemp.addAll(im.splitZPlanes());
            else imageTemp.add(im);
            if (count==imageN) {
                count=0;
                allImages.add(Image.mergeZPlanes(imageTemp));
                imageTemp.clear();
            } else ++count;*/
        }
        
        double pThld = foregroundProportion.getValue().doubleValue();
        long t0 = System.currentTimeMillis();
        Image[] images = allImages.toArray(new Image[0]);
        
        Double[] thlds = new Double[images.length];
        ImageByte[] masks = new ImageByte[ThreadRunner.getMaxCPUs()];
        logger.debug("images: {}, max threads: {}", images.length, images.length);
        List<Pair<String, Exception>> exceptions = ThreadRunner.execute(images, false, new ThreadAction<Image>() {
            @Override
            public void run(Image image, int idx, int threadIdx) {
                if (masks[threadIdx]==null) masks[threadIdx] = new ImageByte("", image);
                else ImageOperations.fill(masks[threadIdx], 0, null);
                thlds[idx] = getThld(image, pThld, thresholdBck.instanciatePlugin(), thresholdHyper.instanciatePlugin() , masks[threadIdx]);
            }
        });
        for (Pair<String, Exception> e : exceptions) logger.error(e.key, e.value);
        double thldMin = Arrays.stream(thlds).min((d1, d2)->Double.compare(d1, d2)).get();
        long t1 = System.currentTimeMillis();
        logger.debug("saturate auto: {}ms", t1-t0);
        if (Double.isFinite(thldMin)) configData.add(thldMin);
        else configData.add(Double.NaN);
        logger.debug("SaturateHistoAuto: {}", Utils.toStringList(configData));
    }
    private static double getThld(Image im, double proportionThld, SimpleThresholder thlderBack, SimpleThresholder thlderHyper, ImageInteger backThld) {
        double thldBack = thlderBack.runThresholder(im);
        double thldHyper = thlderHyper.runThresholder(im);
        backThld=ImageOperations.threshold(im, thldBack, true, true, false, backThld);
        ImageMask hyperThld = new ThresholdMask(im, thldHyper, true, true);
        // remove small obejcts (if background is too low isolated pixels)
        List<Object3D> l = ImageLabeller.labelImageList(backThld);
        l.removeIf(o->o.getSize()<5);
        new ObjectPopulation(l, backThld, backThld, true); // relabel -> fill image
        
        double countHyper = hyperThld.count();
        double count = backThld.count();
        //logger.debug("thldBack:{} hyper: {}, count back: {}, hyper: {}, prop: {}", thldBack, thldHyper, count, countHyper, countHyper / count);
        double proportion = countHyper / count;
        if (proportion<proportionThld) { // recompute hyper thld within seg bact
            ImageMask thldMask = new ThresholdMask(im, thldBack, true, true);
            double thldHyper2 = IJAutoThresholder.runThresholder(im, thldMask, AutoThresholder.Method.Otsu);
            //logger.debug("SaturateHisto: proportion: {} back {}, thldHyper: {} (on whole image: {})", proportion, thldBack, thldHyper2, thldHyper);
            return thldHyper2;
        }
        else return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configData.size()==1;
    }
    

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (Double.isNaN(configData.get(0))) return image;
        SaturateHistogram.saturate(configData.get(0), configData.get(0), image);
        return image;
    }

    @Override
    public ArrayList getConfigurationData() {
        return configData;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}