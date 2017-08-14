/*
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

import configuration.parameters.ui.ParameterUI;
import configuration.parameters.ui.ChoiceParameterUI;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import org.json.simple.JSONAware;
import utils.Utils;

/**
 *
 * @author jollion
 */

public class ChoiceParameter extends SimpleParameter implements ActionableParameter, ChoosableParameter {
    String selectedItem;
    @Transient String[] listChoice;
    @Transient boolean allowNoSelection;
    @Transient private int selectedIndex=-2;
    @Transient ChoiceParameterUI gui;
    @Transient ConditionalParameter cond;
    @Transient boolean postLoaded = false;
    
    
    
    public ChoiceParameter(String name, String[] listChoice, String selectedItem, boolean allowNoSelection) {
        super(name);
        this.listChoice=listChoice;
        setSelectedItem(selectedItem);
        this.allowNoSelection=allowNoSelection;
    }
    
    public String getSelectedItem() {return selectedItem;}
    public int getSelectedIndex() {
        return selectedIndex;
    }
    
    public void setSelectedItem(String selectedItem) {
        this.selectedIndex=Utils.getIndex(listChoice, selectedItem);
        if (selectedIndex==-1) this.selectedItem = "no item selected";
        else this.selectedItem=selectedItem;
        setCondValue();
    }
    
    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex>=0) {
            this.selectedItem=listChoice[selectedIndex];
            this.selectedIndex=selectedIndex;
        } else {
            selectedIndex=-1;
            selectedItem="no item selected";
        }
        setCondValue();
    }
    
    @Override
    public String toString() {return name + ": "+ selectedItem;}

    @Override
    public ParameterUI getUI() {
        if (gui==null) gui = new ChoiceParameterUI(this, true);
        return gui;
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof ChoiceParameter) {
            return this.getSelectedItem().equals(((ChoiceParameter)other).getSelectedItem());
        }
        else return false;
        
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof ChoiceParameter) {
            ChoiceParameter otherC = (ChoiceParameter)other;
            setSelectedItem(otherC.getSelectedItem());
            //logger.debug("choice {} set content from: {} current item: {}, current idx {}, other item: {}, other idx : {}", this.hashCode(), otherC.hashCode(), this.getSelectedItem(), this.getSelectedIndex(), otherC.getSelectedItem(), otherC.getSelectedIndex());
        } else throw new IllegalArgumentException("wrong parameter type: "+(other==null? "null":other.getClass()) +" instead of ChoiceParameter");
    }
    
    // choosable parameter

    public boolean isAllowNoSelection() {
        return this.allowNoSelection;
    }
    
    // actionable parameter
    public String[] getChoiceList() {
        return listChoice;
    }
    
    
    protected void setCondValue() {
        if (cond!=null) cond.setActionValue(selectedItem);
    }
    
    public String getValue() {
        return getSelectedItem();
    }

    public void setValue(String value) {
        this.setSelectedItem(value);
    }
    
    public void setConditionalParameter(ConditionalParameter cond) {
        this.cond=cond;
    }
    /**
     * 
     * @return the asociated conditional parameter, or null if no conditionalParameter is associated
     */
    public ConditionalParameter getConditionalParameter() {
        return cond;
    }
    
    @Override public ChoiceParameter duplicate() {
        return new ChoiceParameter(name, selectedItem);
    }
    private ChoiceParameter(String name, String selectedItem) {
        super(name);
        this.selectedItem=selectedItem;
    }
    
    //@PostLoad
    /*public void postLoad() {
        if (!postLoaded) {
            selectedIndex=Utils.getIndex(listChoice, selectedItem); 
            postLoaded = true;
        }
    }
    */

    @Override
    public Object toJSONEntry() {
        return selectedItem;
    }

    @Override
    public void initFromJSONEntry(Object json) {
        if (json instanceof String) {
            setSelectedItem((String)json);
        } else throw new IllegalArgumentException("JSON Entry is not String");
    }
}
