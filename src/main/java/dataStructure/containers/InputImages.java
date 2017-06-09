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
package dataStructure.containers;

import image.Image;
import static image.Image.logger;
import image.ImageFloat;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import plugins.plugins.thresholders.BackgroundThresholder;
import utils.ArrayUtil;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author nasique
 */
public interface InputImages {
    public Image getImage(int channelIdx, int timePoint);
    public int getFrameNumber();
    public int getChannelNumber();
    public int getDefaultTimePoint();
    public int getSizeZ(int channelIdx);
    public void flush();
    public double getCalibratedTimePoint(int c, int t, int z);
    public boolean singleFrameChannel(int channelIdx);
    public static Image getAverageFrame(InputImages images, int channelIdx, int frame,  int numberOfFramesToAverage) {
        if (numberOfFramesToAverage<=1) return images.getImage(channelIdx, frame);
        List<Image> imagesToAv = new ArrayList<>(numberOfFramesToAverage);
        int fMin = Math.max(0, frame-numberOfFramesToAverage/2);
        int fMax = Math.min(images.getFrameNumber(), fMin+numberOfFramesToAverage);
        if (fMax-fMin<numberOfFramesToAverage) fMin = Math.max(0, fMax-numberOfFramesToAverage);
        for (int f = fMin; f<fMax; ++f) imagesToAv.add(images.getImage(channelIdx, f));
        return ImageOperations.meanZProjection(Image.mergeZPlanes(imagesToAv));
    }
    public static List<Integer> chooseNImagesWithSignal(InputImages images, int channelidx, int n) {
        if (n>=images.getFrameNumber()) return Utils.toList(ArrayUtil.generateIntegerArray(images.getFrameNumber()));
        // signal is measured with BackgroundThresholder
        long t0 = System.currentTimeMillis();
        List<Pair<Integer, Double>> signal = new ArrayList<>();
        double[] count = new double[3];
        double sTot = images.getImage(channelidx, 0).getSizeXYZ();
        for (int t = 0; t<images.getFrameNumber(); ++t) {
            BackgroundThresholder.runThresholder(images.getImage(channelidx, t), null, 2.5, 4, 2, count);
            signal.add(new Pair(t, (sTot - count[2]) /  sTot ));
        }
        Collections.sort(signal, (p1, p2)->Double.compare(p1.value, p2.value));
        // choose n frames among the X frames with most signal
        int candidateNumber = Math.max(images.getFrameNumber() /4, n);
        double delta = (double)candidateNumber / (double)(n+1);
        signal = signal.subList(0, candidateNumber);
        List<Pair<Integer, Double>> res = new ArrayList<>(n);
        for (int i =0; i<n; ++i) {
            int idx = (int)(delta*i);
            res.add(signal.get(idx));
        }
        long t1 = System.currentTimeMillis();
        logger.debug("choose {} images: {} t={} (among: {})", n, res, t1-t0, signal);
        return Pair.unpairKeys(res);
    }
}
