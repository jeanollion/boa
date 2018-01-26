/*
 * Copyright (C) 2016 jollion
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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.GUI;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.core.Processor;
import boa.core.Task;
import boa.configuration.experiment.Experiment;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import boa.image.Image;
import java.awt.Color;
import java.awt.Font;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import boa.plugins.PluginFactory;
import boa.plugins.ProcessingScheme;
import boa.plugins.plugins.trackers.LAPTracker;
import boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections;
import boa.plugins.plugins.trackers.MicrochannelTracker;
import boa.plugins.plugins.trackers.trackmate.SpotWithinCompartmentRoiModifier;
import boa.plugins.plugins.transformations.CropMicroChannelFluo2D;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class TestTracker {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        String dbName = "MutH_150324";
        //String dbName = "fluo170512_WT";
        //String dbName = "fluo171219_WT_750ms";
        // MuttH_150324 -> p0 mc1 -> artefact bord microcannaux
        //String dbName = "MutD5_141202";
        int pIdx =0;
        int mcIdx =1;
        int structureIdx = 1;
        int[] frames = new int[]{0, 200};
        //BacteriaClosedMicrochannelTrackerLocalCorrections.bactTestFrame=4;
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().setDBConnection(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        MasterDAO db = GUI.getDBConnection();
        
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        MicrochannelTracker.debug=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debug=false;
        BacteriaClosedMicrochannelTrackerLocalCorrections.debugCorr=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=3;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debugThreshold = 270;
        testSegmentationAndTracking(db.getDao(db.getExperiment().getPosition(pIdx).getName()), ps, structureIdx, mcIdx, frames[0],frames[1]); //  0,80);
        //testBCMTLCStep(db.getDao(db.getExperiment().getPosition(pIdx).getName()), ps, structureIdx, mcIdx,frames[0],frames[1]); 
    }
    public static void testSegmentationAndTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, false, structureIdx, mcIdx, tStart, tEnd);
    }
    public static void testTracking(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        test(dao, ps, true, structureIdx, mcIdx, tStart, tEnd);
    }
    
    public static void test(ObjectDAO dao, ProcessingScheme ps, boolean trackOnly, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            logger.debug("all tracks: {}", allTracks.size());
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<=tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
                        if (!parentTrack.isEmpty()) break;
                    }
                }
            }
        }
        Map<String, StructureObject> gCutMap = StructureObjectUtils.createGraphCut(parentTrack, true); 
        parentTrack = Utils.transform(parentTrack, o->gCutMap.get(o.getId()));
        for (StructureObject p : parentTrack) p.setChildren(null, structureIdx);
        logger.debug("parent track: {}", parentTrack.size());
        if (parentTrack.isEmpty()) return;
        LAPTracker.registerTMI=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.debug=true;
        //BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        List<Pair<String, Exception>> l;
        CropMicroChannelFluo2D.debug=false;
        //if (ps instanceof ProcessingSchemeWithTracking && structureIdx==0) ((ProcessingSchemeWithTracking)ps).getTrackPostFilters().removeAllElements();
        //ps.getPostFilters().add(new MicrochannelPhaseArtifacts());
        
        if (trackOnly) l=ps.trackOnly(structureIdx, parentTrack, null);
        else l=ps.segmentAndTrack(structureIdx, parentTrack, null);
        for (Pair<String, Exception> p : l) logger.debug(p.key, p.value);
        logger.debug("track: {} ({}) children of {} = ({})", StructureObjectUtils.getAllTracks(parentTrack, structureIdx).size(), Utils.toStringList( StructureObjectUtils.getAllTracks(parentTrack, structureIdx).values(), o->o.size()), parentTrack.get(0), parentTrack.get(0).getChildren(structureIdx));

        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (structureIdx==2 && LAPTracker.debugTMI!=null) iwm.setRoiModifier(new SpotWithinCompartmentRoiModifier(LAPTracker.debugTMI, 2));
        logger.debug("generating TOI");
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(parentTrack, structureIdx);
        Image interactiveImage = i.generateRawImage(structureIdx, true);
        iwm.addImage(interactiveImage, i, structureIdx, true);
        logger.debug("total objects: {} ({})", i.getObjects().size(), StructureObjectUtils.getChildrenByFrame(parentTrack, structureIdx).size());
        
        if (structureIdx==2) {
            Collection<StructureObject> bact = Utils.flattenMap(StructureObjectUtils.getChildrenByFrame(parentTrack, 1));
            Selection bactS = new Selection("bact", dao.getMasterDAO());
            bactS.setColor("Grey");
            bactS.addElements(bact);
            bactS.setIsDisplayingObjects(true);
            GUI.getInstance().addSelection(bactS);
            GUI.updateRoiDisplayForSelections(interactiveImage, i);
            //ImageObjectInterface iB = iwm.getImageTrackObjectInterface(parentTrack, 1);
            //GUI.getInstance().getSelections()
            //iwm.displayObjects(im, iB.pairWithOffset(bact), Color.LIGHT_GRAY, false, false); // should remain on overlay! 
        }
        iwm.setInteractiveStructure(structureIdx);
        iwm.displayAllObjects(interactiveImage);
        iwm.displayAllTracks(interactiveImage);
        
    }
    
    public static void testBCMTLCStep(ObjectDAO dao, ProcessingScheme ps, int structureIdx, int mcIdx, int tStart, int tEnd) {
        List<StructureObject> roots = dao.getRoots();
        
        List<StructureObject> parentTrack=null;
        if (structureIdx==0) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
        }
        else {
            Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
            for (StructureObject th : allTracks.keySet()) {
                if (th.getIdx()==mcIdx && th.getFrame()<tEnd) {
                    if (parentTrack==null || parentTrack.isEmpty()) {
                        parentTrack = allTracks.get(th);
                        parentTrack.removeIf(o -> o.getFrame()<tStart || o.getFrame()>tEnd);
                        //logger.debug("parents: {}", parentTrack);
                        //return;
                    }
                }
            }
        }
        BacteriaClosedMicrochannelTrackerLocalCorrections.correctionStep=true;
        BacteriaClosedMicrochannelTrackerLocalCorrections.verboseLevelLimit=1;
        ps.segmentAndTrack(structureIdx, parentTrack, null);
        //ps.trackOnly(structureIdx, parentTrack);
        GUI.getInstance();
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        for (String name : BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.keySet()) {
            List<StructureObject> pt = BacteriaClosedMicrochannelTrackerLocalCorrections.stepParents.get(name);
            ImageObjectInterface i = iwm.getImageTrackObjectInterface(pt, structureIdx);
            Image im = i.generateRawImage(structureIdx, true).duplicate(name); // duplicate if not hascode collapse in case of trackImage
            //im.setName(name);
            iwm.addImage(im, i, structureIdx, true);
            iwm.setInteractiveStructure(structureIdx);
            iwm.displayAllObjects(im);
            iwm.displayAllTracks(im);
        }
    }
}