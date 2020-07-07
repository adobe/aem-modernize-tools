/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.adobe.aem.modernize.dialog.impl.rules;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.dialog.AbstractDialogRewriteRule;
import com.adobe.aem.modernize.dialog.DialogConstants;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Set;

import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.NN_CQ_DESIGN_DIALOG;
import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.NN_CQ_DIALOG;
import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.NT_DIALOG;
import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.hasXtype;
import static com.adobe.aem.modernize.impl.RewriteUtils.hasPrimaryType;

/**
 * Rule that rewrites the basic structure of dialogs. It creates a Granite UI container using either a "tabs" or
 * "fixedcolumns" layout. The components (tabs or widgets) of the original dialog are copied over and will be handled
 * by subsequent passes of the algorithm.
 */
@Component
@Service
@Properties({
        @Property(name = "service.ranking", intValue = 1)
})
public class CqDialogRewriteRule extends AbstractDialogRewriteRule {
    
    public boolean matches(Node root) throws RepositoryException {
        return hasPrimaryType(root, NT_DIALOG);
    }
    
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {
        // Granite UI dialog already exists at this location
        Node parent = root.getParent();
        
        boolean isDesignDialog = root.getName().contains(NameConstants.NN_DESIGN_DIALOG);
        
        if (isDesignDialog) {
            if (parent.hasNode(NN_CQ_DESIGN_DIALOG)) {
                throw new RewriteException("Could not rewrite dialog: " + NN_CQ_DESIGN_DIALOG + " node already exists");
            }
        } else {
            if (parent.hasNode(NN_CQ_DIALOG)) {
                throw new RewriteException("Could not rewrite dialog: " + NN_CQ_DIALOG + " node already exists");
            }
        }
        
        boolean isTabbed = isTabbed(root);
        // get the items: in case of a tabbed dialog, these represent tabs, otherwise widgets
        Node dialogItems = getDialogItems(root);
        if (dialogItems == null) {
            throw new RewriteException("Unable to find the dialog items");
        }
        
        // add cq:dialog or cq:design_dialog node
        Node cqDialog = (isDesignDialog) ? parent.addNode(NN_CQ_DESIGN_DIALOG, DialogConstants.NT_UNSTRUCTURED) : parent.addNode(NN_CQ_DIALOG, DialogConstants.NT_UNSTRUCTURED);
        finalNodes.add(cqDialog);
        cqDialog.setProperty(DialogConstants.SLING_RESOURCE_TYPE, "cq/gui/components/authoring/dialog");
        if (root.hasProperty(DialogConstants.HELP_PATH)) {
            cqDialog.setProperty(DialogConstants.HELP_PATH, root.getProperty(DialogConstants.HELP_PATH).getValue());
        }
        if (root.hasProperty(DialogConstants.TITLE)) {
            cqDialog.setProperty(DialogConstants.JCR_TITLE, root.getProperty(DialogConstants.TITLE).getValue());
        }
        
        // add content node as a panel or tabpanel widget (will be rewritten by the corresponding rule)
        String nodeType = isTabbed ? DialogConstants.CQ_TAB_PANEL : DialogConstants.CQ_PANEL;
        Node content = cqDialog.addNode(DialogConstants.CONTENT, nodeType);
        
        // add items child node
        Node items = content.addNode(DialogConstants.ITEMS, DialogConstants.CQ_WIDGET_COLLECTION);
        
        // copy items
        NodeIterator iterator = dialogItems.getNodes();
        while (iterator.hasNext()) {
            Node item = iterator.nextNode();
            JcrUtil.copy(item, items, item.getName());
        }
        
        // remove old root and return new root
        root.remove();
        return cqDialog;
    }
    
    /**
     * Returns true if this dialog contains tabs, false otherwise.
     */
    private boolean isTabbed(Node dialog)
            throws RepositoryException {
        if (isTabPanel(dialog)) {
            return true;
        }
        Node items = getChild(dialog, DialogConstants.ITEMS);
        if (isTabPanel(items)) {
            return true;
        }
        return items != null && isTabPanel(getChild(items, "tabs"));
    }
    
    /**
     * Returns the items that this dialog consists of. These might be components, or - in case of a tabbed
     * dialog - tabs.
     */
    private Node getDialogItems(Node dialog)
            throws RepositoryException {
        // find first sub node called "items" of type "cq:WidgetCollection"
        Node items = dialog;
        do {
            items = getChild(items, DialogConstants.ITEMS);
        } while (items != null && !DialogConstants.CQ_WIDGET_COLLECTION.equals(items.getPrimaryNodeType().getName()));
        if (items == null) {
            return null;
        }
        
        // check if there is a tab panel child called "tabs"
        Node tabs = getChild(items, "tabs");
        if (isTabPanel(tabs)) {
            return getChild(tabs, DialogConstants.ITEMS);
        }
        
        return items;
    }
    
    /**
     * Returns the child with the given name or null if it doesn't exist.
     */
    private Node getChild(Node node, String name)
            throws RepositoryException {
        if (node.hasNode(name)) {
            return node.getNode(name);
        }
        return null;
    }
    
    /**
     * Returns true if the specified node is a tab panel, false otherwise.
     */
    private boolean isTabPanel(Node node)
            throws RepositoryException {
        if (node == null) {
            return false;
        }
        if (DialogConstants.CQ_TAB_PANEL.equals(node.getPrimaryNodeType().getName())) {
            return true;
        }
        return hasXtype(node, "tabpanel");
    }
    
}
