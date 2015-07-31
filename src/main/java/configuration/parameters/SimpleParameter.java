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

import dataStructure.configuration.Experiment;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 *
 * @author jollion
 */
@Embedded(polymorph = true)
@Lifecycle
public abstract class SimpleParameter implements Parameter {
    protected String name;
    @Transient private ContainerParameter parent;
    
    protected SimpleParameter(String name) {
        this.name=name;
    }
    
    @Override
    public String getName(){
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public Parameter duplicate() {
        try {
            Parameter p = (Parameter)this.getClass().newInstance();
            p.setName(name);
            p.setContentFrom(this);
            return p;
        } catch (InstantiationException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } return null;
    }
    
    @Override
    public TreeNode getChildAt(int childIndex) {
        return null;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        return 0;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public Enumeration children() {
        return Collections.emptyEnumeration();
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}

    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override
    public void setUserObject(Object object) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        //this.name=object.toString();
    }

    @Override
    public void removeFromParent() {
        if (parent!=null) parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }

    @Override
    public ArrayList<Parameter> getPath() {
        return getPath(this);
    }
    
    public static ArrayList<Parameter> getPath(Parameter p){
        if (p.getParent()==null) {
            ArrayList<Parameter> res = new ArrayList<Parameter>();
            res.add(p);
            return res;
        }
        else {
            ArrayList<Parameter> path = ((Parameter)p.getParent()).getPath();
            path.add(p);
            return path;
        }
    }
    
    @Override
    public String toString() {
        return name;
    }
    
}