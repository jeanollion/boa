/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

import boa.image.processing.ImageOperations;
import boa.plugins.Plugin;
import boa.utils.DoubleStatistics;
import boa.utils.Utils;
import static boa.utils.Utils.parallele;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public class HistogramFactory {
    public final static Logger logger = LoggerFactory.getLogger(HistogramFactory.class);
    public static int MIN_N_BINS = 256;
    public static int MAX_N_BINS = 5000;

    public enum BIN_SIZE_METHOD {
        NBINS_256,
        AUTO_WITH_LIMITS,
        AUTO
    };
    /**
     * Automatic bin size computation
     * @param stream value distribution used for the histogram
     * @param method binning method: NBINS_256: forces number of bins to be 256; AUTO: uses Scott, D. 1979 formula for optimal bin size computation: 3.49 * std * N^-1/3 (if no decimal places in values, this value can't be lower than 1); AUTO_WITH_LIMITS same as auto, but if no decimal places, binsize=1, and bin size is adjusted to that number of bins is within range [256; 2000]
     * @return {min, max bin size}
     */
    public static double[] getMinAndMaxAndBinSize(DoubleStream stream, BIN_SIZE_METHOD method) {
        // stats -> 0-2: Sum of Square with compensation variables, 3: count, 4: sum, 5 : min; 6: max; 7: max decimal place 
        BiConsumer<double[], double[]> combiner = (stats1, stats2)-> {
            stats1[4]+=stats2[4];
            stats1[3]+=stats2[3];
            DoubleStatistics.combine(stats1, stats2);
            if (stats1[5]>stats2[5]) stats1[5] = stats2[5];
            if (stats1[6]<stats2[6]) stats1[6] = stats2[6];
            if (stats1[7]<stats2[7]) stats1[7] = stats2[7];
        };
        ObjDoubleConsumer<double[]> cons = (double[] stats, double v) -> {
            stats[3]++;
            stats[4]+=v;
            DoubleStatistics.add(v, stats);
            if (stats[5]>v) stats[5] = v;
            if (stats[6]<v) stats[6] = v;
            double dec = v-(long)v;
            if (stats[7]<dec) stats[7] = dec;
        };
        double[] stats = stream.collect(() -> new double[]{0, 0, 0, 0, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0},cons, combiner);
        double std = stats[3]>0 ?  Math.sqrt((DoubleStatistics.getSumOfSquare(stats) / stats[3]) - Math.pow(stats[4]/stats[3], 2)) : 0.0d;
        double binSize = 3.49 * std * Math.pow(stats[3], -1/3d);
        /* FROM : 
        Scott, D. 1979.
        On optimal and data-based histograms.
        Biometrika, 66:605-610. */
        
        if (stats[7]==0) binSize = Math.max(1, binSize); // no decimal places
        switch(method) {
            case NBINS_256:
                binSize = getBinSize(stats[5], stats[6], 256); 
                break;
            case AUTO_WITH_LIMITS:
                if (stats[7]==0) binSize=1;
                else {
                    // enshure histogram will respect nbins range
                    int nBins = getNBins(stats[5], stats[6], binSize);
                    if (nBins<MIN_N_BINS) binSize = getBinSize(stats[5], stats[6], MIN_N_BINS); 
                    if (nBins>MAX_N_BINS) binSize = getBinSize(stats[5], stats[6], MAX_N_BINS);
                }
                break;
        }
        //logger.debug("autobin: range: [{};{}], count: {}, sigma: {}, max decimal place: {} binSize: {}", stats[5], stats[6], stats[3], std, stats[7], binSize);
        return new double[]{stats[5], stats[6], binSize};
    }
    /**
     * Computes histogram with automatic bin size computation
     * @param streamSupplier
     * @param method see {@link #getMinAndMaxAndBinSize(java.util.stream.DoubleStream, boa.image.HistogramFactory.BIN_METHOD) }
     * @return 
     */
    public static Histogram getHistogram(Supplier<DoubleStream> streamSupplier, BIN_SIZE_METHOD method) {
        double[] mmb = getMinAndMaxAndBinSize(streamSupplier.get(), method);
        return getHistogram(streamSupplier.get(), mmb[2], getNBins(mmb[0], mmb[1], mmb[2]), mmb[0]);
    }
    public static Histogram getHistogram(Supplier<DoubleStream> streamSupplier, double binSize) {
        double[] mm = getMinAndMax(streamSupplier.get());
        return getHistogram(streamSupplier.get(), binSize, getNBins(mm[0], mm[1], binSize), mm[0]);
    }
    public static Histogram getHistogram(Supplier<DoubleStream> streamSupplier, int nBins) {
        double[] mm = getMinAndMax(streamSupplier.get());
        return getHistogram(streamSupplier.get(), getBinSize(mm[0], mm[1], nBins), nBins, mm[0]);
    }
    public static Histogram getHistogram(DoubleStream stream, double binSize, int nBins, double min) {
        double coeff = 1 / binSize;
        ObjDoubleConsumer<int[]> fillHisto = (int[] histo, double v) -> {
            int idx = (int)((v-min) * coeff);
            if (idx==nBins) histo[nBins-1]++;
            else if (idx>=0 && idx<nBins) histo[idx]++;
        };
        BiConsumer<int[], int[]> combiner = (int[] h1, int[] h2) -> {
            for (int i = 0; i<nBins; ++i) h1[i]+=h2[i];
        };
        int[] histo = stream.collect(()->new int[nBins], fillHisto, combiner);
        return new Histogram(histo, binSize, min);
    }
    public static double[] getMinAndMax(DoubleStream stream) {
        BiConsumer<double[], double[]> combiner = (mm1, mm2)-> {
            if (mm1[0]>mm2[0]) mm1[0] = mm2[0];
            if (mm1[1]<mm2[1]) mm1[1] = mm2[1];
        };
        ObjDoubleConsumer<double[]> cons = (double[] mm, double v) -> {
            if (mm[0]>v) mm[0] = v;
            if (mm[1]<v) mm[1] = v;
        };
        return stream.collect(() -> new double[2],cons, combiner);
    }
    
    public static boolean allImagesAreInteger(Collection<Image> images) {
        return Utils.objectsAllHaveSameProperty(images, im -> im instanceof ImageInteger);
    }
    
    public static double getBinSize(double min, double max, int nBins) {
        return (max - min) / nBins;
    }
    public static int getNBins(double min, double max, double binSize) {
        return Math.max(2, (int)((max-min)/binSize));
    }
    
    public static List<Histogram> getHistograms(Collection<Image> images, double binSize, double[] minAndMax, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        double[] mm = minAndMax;
        int nBins = getNBins(mm[0], mm[1], binSize); 
        return parallele(images.stream(), parallele).map((Image im) -> HistogramFactory.getHistogram(im.stream(), binSize, nBins, mm[0])).collect(Collectors.toList());
    }

    public static Map<Image, Histogram> getHistograms(Map<Image, ImageMask> images, double binSize, double[] minAndMax, boolean parallele) {
        if (minAndMax == null) {
            minAndMax = new double[2];
        }
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images, parallele);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        final double[] mm = minAndMax;
        int nBins = getNBins(mm[0], mm[1], binSize); 
        return parallele(images.entrySet().stream(), parallele).collect(Collectors.toMap((Map.Entry<Image, ImageMask> e) -> e.getKey(), (Map.Entry<Image, ImageMask> e) -> HistogramFactory.getHistogram(e.getKey().stream(e.getValue(), true),  binSize, nBins, mm[0])));
    }
}
