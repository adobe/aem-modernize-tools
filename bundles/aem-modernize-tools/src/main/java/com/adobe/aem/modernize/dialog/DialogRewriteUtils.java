/*
 *  (c) 2014 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.dialog;

import java.util.Arrays;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;

import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;

/**
 * Provides helper methods to be used by dialog rewrite rules.
 */
public class DialogRewriteUtils {

    public static final String CORAL_2_BACKUP_SUFFIX = ".coral2";
    public static final String NT_DIALOG = "cq:Dialog";
    public static final String NN_CQ_DIALOG = "cq:dialog";
    public static final String NN_CQ_DESIGN_DIALOG = "cq:design_dialog";
    public static final String DIALOG_CONTENT_RESOURCETYPE_PREFIX_CORAL3 = "granite/ui/components/coral/foundation";

    private static final String[] CLASSIC_DIALOG_NAMES = {NameConstants.NN_DIALOG, NameConstants.NN_DESIGN_DIALOG};
    private static final String[] DIALOG_NAMES = {NN_CQ_DIALOG, NN_CQ_DESIGN_DIALOG, NN_CQ_DIALOG + CORAL_2_BACKUP_SUFFIX, NN_CQ_DESIGN_DIALOG + CORAL_2_BACKUP_SUFFIX};

    /**
     * Checks if a node has a certain xtype.
     *
     * @param node The node to check
     * @param xtype The xtype to check or null to check if the node has no xtype
     * @return true if the node has the specified xtype, false otherwise
     * @throws RepositoryException
     */
    public static boolean hasXtype(Node node, String xtype)
            throws RepositoryException {
        if (xtype == null) {
            // if 'xtype' is null, we return true if the node does not have an xtype
            return !node.hasProperty("xtype");
        }
        return node.hasProperty("xtype") && xtype.equals(node.getProperty("xtype").getString());
    }

    /**
     * Checks if a node has a certain type.
     *
     * @param node The node to check
     * @param type The type to check or null to check if the node has no type
     * @return true if the node has the specified type, false otherwise
     * @throws RepositoryException
     */
    public static boolean hasType(Node node, String type)
            throws RepositoryException {
        if (type == null) {
            // if 'type' is null, we return true if the node does not have a type
            return !node.hasProperty("type");
        }
        return node.hasProperty("type") && type.equals(node.getProperty("type").getString());
    }

    /**
     * Copies a property from a source to a destination node.
     *
     * @param source The source node
     * @param relPropertyPath The relative path to the property to be copied
     * @param destination The destination node
     * @param name The name for the copy of the property
     * @return The copied property or null if the source property doesn't exist
     * @throws RepositoryException
     */
    public static Property copyProperty(Node source, String relPropertyPath, Node destination, String name)
            throws RepositoryException {
        if (!source.hasProperty(relPropertyPath)) {
            return null;
        }
        return JcrUtil.copy(source.getProperty(relPropertyPath), destination, name);
    }

    /**
     * Determines the dialog type of a node in the repository.
     *
     * @param node The dialog node
     * @return The dialog type of the node
     * @throws RepositoryException
     */
    public static DialogType getDialogType(Node node) throws RepositoryException {
        DialogType type = DialogType.UNKNOWN;

        if (node == null) {
            return type;
        }

        String name = node.getName();

        if (Arrays.asList(CLASSIC_DIALOG_NAMES).contains(name) && NT_DIALOG.equals(node.getPrimaryNodeType().getName())) {
            type = DialogType.CLASSIC;
        } else if (Arrays.asList(DIALOG_NAMES).contains(name) && node.hasNode("content")) {
            Node contentNode = node.getNode("content");
            type = DialogType.CORAL_2;

            if (contentNode != null) {
                if (contentNode.hasProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE)) {
                    String resourceType = contentNode.getProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE).getString();
                    type = resourceType.startsWith(DIALOG_CONTENT_RESOURCETYPE_PREFIX_CORAL3) ? DialogType.CORAL_3 : DialogType.CORAL_2;
                }
            }
        }

        return type;
    }

    /**
     * Checks whether a given node represents a design dialog.
     *
     * @param node The dialog node
     * @return 'true' if the node represents a design dialog
     * @throws RepositoryException
     */
    public static boolean isDesignDialog(Node node) throws RepositoryException {
        if (node == null) {
            return false;
        }

        String name = node.getName();

        return name.equals(NameConstants.NN_DESIGN_DIALOG) || name.equals(NN_CQ_DESIGN_DIALOG) || name.equals(NN_CQ_DESIGN_DIALOG + CORAL_2_BACKUP_SUFFIX);
    }
}
