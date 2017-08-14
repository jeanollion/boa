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
package configuration.parameters;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static configuration.parameters.Parameter.logger;
import configuration.parameters.ui.ChoiceParameterUI;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import plugins.Transformation;
import plugins.Transformation.SelectionMode;
import plugins.TransformationTimeIndependent;
import utils.JSONUtils;

/**
 *
 * @author jollion
 */
public class TransformationPluginParameter<T extends Transformation> extends PluginParameter<T> {
    List configurationData;
    ChannelImageParameter inputChannel = new ChannelImageParameter("Configuration Channel", -1);
    ChannelImageParameter outputChannel = null;
    //Parameter inputTimePoints;
    
    @Override
    public JSONObject toJSONEntry() {
        JSONObject res = super.toJSONEntry();
        res.put("inputChannel", inputChannel.toJSONEntry());
        if (outputChannel!=null) res.put("outputChannel", outputChannel.toJSONEntry());
        if (configurationData!=null) res.put("configurationData", JSONUtils.toJSONList(configurationData));
        return res;
    }
    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        super.initFromJSONEntry(jsonEntry);
        JSONObject jsonO = (JSONObject)jsonEntry;
        inputChannel.initFromJSONEntry(jsonO.get(("inputChannel")));
        if (jsonO.containsKey("outputChannel")) {
            if (outputChannel==null) outputChannel = new ChannelImageParameter("Channels on which apply transformation", null);
            outputChannel.initFromJSONEntry(jsonO.get(("outputChannel")));
        }
        if (jsonO.containsKey("outputChannel")) configurationData = (List)jsonO.get("configurationData");
    }
    
    public TransformationPluginParameter(String name, Class<T> pluginType, boolean allowNoSelection) {
        super(name, pluginType, allowNoSelection);
        outputChannel=null;
    }
    
    public TransformationPluginParameter(String name, Class<T> pluginType, String defaultMethod, boolean allowNoSelection) {
        super(name, pluginType, defaultMethod, allowNoSelection);
        outputChannel=null;
    }
    
    // constructeur désactivé car la methode setPlugin a besoin de l'experience
    /*public TransformationPluginParameter(String name, boolean allowNoSelection, Class<T> pluginType, T pluginInstance) {
        super(name, allowNoSelection, pluginType, pluginInstance);
    }*/
    
    @Override 
    public TransformationPluginParameter<T> setPlugin(T pluginInstance) {
        if (pluginInstance instanceof TransformationTimeIndependent) {  
            SelectionMode oc = ((TransformationTimeIndependent)pluginInstance).getOutputChannelSelectionMode();
            if (SelectionMode.MULTIPLE.equals(oc)) outputChannel = new ChannelImageParameter("Channels on which apply transformation", null);
            else if (SelectionMode.SINGLE.equals(oc)) outputChannel = new ChannelImageParameter("Channels on which apply transformation", -1);
            else outputChannel=null;
        }
        super.setPlugin(pluginInstance);
        configurationData = ParameterUtils.duplicateConfigurationDataList(pluginInstance.getConfigurationData());
        return this;
    }
    
    public void setConfigurationData(List configurationData) {
        this.configurationData = ParameterUtils.duplicateConfigurationDataList(configurationData);
    }
    
    public void setOutputChannel(int... channelIdx) { // null -> all selected OR same channel selected
        if (outputChannel!=null) outputChannel.setSelectedIndicies(channelIdx);
    }
    
    public void setInputChannel(int channelIdx) {
        this.inputChannel.setSelectedIndex(channelIdx);
    }
    
    public int[] getOutputChannels() { // if null -> all selected or same as input...
        if (outputChannel==null) return null;
        else return outputChannel.getSelectedItems();
    }
    
    /*public int[] getInputTimePoints() { // if null -> all selected
        if (inputTimePoints==null) return null;
        else if (inputTimePoints instanceof MultipleChoiceParameter) return ((MultipleChoiceParameter)inputTimePoints).getSelectedItems();
        else if (inputTimePoints instanceof ChoiceParameter) return new int[]{((ChoiceParameter)inputTimePoints).getSelectedIndex()};
        else return null;
    }*/
    
    public int getInputChannel() {
        return inputChannel.getSelectedIndex();
    }
    
    @Override
    protected void initChildList() {
        ArrayList<Parameter> p = new ArrayList<Parameter>(2+(pluginParameters!=null?pluginParameters.size():0));
        p.add(inputChannel);
        if (outputChannel!=null) p.add(outputChannel);
        if (pluginParameters!=null) p.addAll(pluginParameters);
        if (additionalParameters!=null) p.addAll(additionalParameters);
        //System.out.println("init child list! for: "+toString()+ " number of pp:"+(pluginParameters==null?0:pluginParameters.length)+" number total:"+p.size());
        super.initChildren(p);
    }
    
    @Override
    public T instanciatePlugin() {
        T instance = super.instanciatePlugin();
        if (instance!=null) {
            List target = instance.getConfigurationData();
            if (target!=null && configurationData!=null) for (Object o : configurationData) target.add(ParameterUtils.duplicateConfigurationData(o));
            //logger.debug("copied configuration data to transformation: {}: config:{}", instance.getClass().getSimpleName(), instance.getConfigurationData());
        }
        return instance;
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (!super.sameContent(other)) return false;
        if (other instanceof TransformationPluginParameter) {
            TransformationPluginParameter otherPP = (TransformationPluginParameter) other;
            if ((outputChannel==null && otherPP.outputChannel!=null) || (outputChannel!=null && !outputChannel.sameContent(otherPP.outputChannel))) {
                logger.debug("transformationPP {}!={} differ in output channel: {} vs {}", name, otherPP.name, outputChannel, otherPP.outputChannel);
                return false;
            }
            if (!inputChannel.sameContent(otherPP.inputChannel)) {
                logger.debug("transformationPP {}!={} differ in input channel: {} vs {}", name, otherPP.name, inputChannel, otherPP.inputChannel);
                return false;
            }
            return true;
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof TransformationPluginParameter && ((TransformationPluginParameter)other).getPluginType().equals(getPluginType())) {
            TransformationPluginParameter otherPP = (TransformationPluginParameter) other;
            this.configurationData=ParameterUtils.duplicateConfigurationDataList(otherPP.configurationData);
            if (otherPP.outputChannel==null) this.outputChannel=null;
            else this.outputChannel=otherPP.outputChannel.duplicate();
            inputChannel.setContentFrom(otherPP.inputChannel);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override
    public TransformationPluginParameter<T> duplicate() {
        TransformationPluginParameter res = new TransformationPluginParameter(name, getPluginType(), allowNoSelection);
        res.setListeners(listeners);
        res.setContentFrom(this);
        return res;
    }
    @Override
    public ChoiceParameterUI getUI() {
        ChoiceParameterUI ui = super.getUI();
        if (super.isOnePluginSet()) {
            MicroscopyField f = ParameterUtils.getFirstParameterFromParents(MicroscopyField.class, this, false);
            if (f!=null) {
                int idx = this.getParent().getIndex(this);
                ui.addActions(ParameterUtils.getTransformationTest("Test Transformation", f, idx, false), true);
                ui.addActions(ParameterUtils.getTransformationTest("Test Transformation (show all steps)", f, idx, true), false);
                ui.addActions(ParameterUtils.getTransformationTestOnCurrentImage("Test Transformation on current Image", f, idx), false);
            }            
        }
        return ui;
    }
}
