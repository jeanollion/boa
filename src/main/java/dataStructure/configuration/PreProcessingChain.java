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
package dataStructure.configuration;

import configuration.parameters.MultipleChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.ui.MultipleChoiceParameterUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.plaf.basic.BasicMenuItemUI;
import plugins.Registration;
import plugins.TransformationTimeIndependent;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class PreProcessingChain extends SimpleContainerParameter {
    
    SimpleListParameter<PluginParameter<TransformationTimeIndependent>> constantTransformations= new SimpleListParameter<PluginParameter<TransformationTimeIndependent>>("Constant transformations", new PluginParameter<TransformationTimeIndependent>("Transformation", TransformationTimeIndependent.class, false));
    PluginParameter<Registration> registration = new PluginParameter<Registration>("Transformation", Registration.class, false);
    
    public PreProcessingChain(String name) {
        super(name);
        initChildList();
    }
    
    @Override
    protected void initChildList() {
        super.initChildren(constantTransformations, registration);
    }
    
    @Override public ParameterUI getUI() {
        return new PreProcessingChainUI(this);
    }
    
    class PreProcessingChainUI implements ParameterUI {
        Object[] actions;
        MultipleChoiceParameter fields;
        MultipleChoiceParameterUI fieldUI;
        Experiment xp;
        PreProcessingChain ppc;
        public PreProcessingChainUI(PreProcessingChain ppc) {
            xp = ParameterUtils.getExperiment(ppc);
            this.ppc=ppc;
            fields = new MultipleChoiceParameter("Fields", xp.getFieldsAsString(), false);
            
        }
        
        public Object[] getDisplayComponent() {
            fieldUI = (MultipleChoiceParameterUI)fields.getUI();
            actions = new Object[fieldUI.getDisplayComponent().length + 2];
            for (int i = 2; i < actions.length; i++) {
                actions[i] = fieldUI.getDisplayComponent()[i - 2];
                if (i<actions.length-1) ((JMenuItem)actions[i]).setUI(new StayOpenMenuItemUI());
            }
            JMenuItem overide = new JMenuItem("Overide configuration on selected fields");
            overide.setAction(
                new AbstractAction("Overide configuration on selected fields") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        for (int f : fieldUI.getSelectedItems()) {
                            System.out.println("overrinding config for field:"+f);
                            xp.fields.getChildAt(f).setPreProcessingChains(ppc);
                        }
                    }
                }
            );
            actions[0]=overide;
            actions[1]=new JSeparator();
            return actions;
        }
    }
    class StayOpenMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
        }
    }
}