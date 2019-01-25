package com.adobe.aem.modernize.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.day.cq.commons.jcr.JcrUtil;

/**
 * Provides helper methods to be used by rewrite rules.
 */

public class RewriteUtils {

    public static final String RESPONSIVE_GRID_RES_TYPE = "wcm/foundation/components/responsivegrid";
    /**
     * Checks if a node has a certain primary type.
     *
     * @param node The node to check
     * @param typeName The name of the primary type to check
     * @return true if the node has the specified primary type, false otherwise
     * @throws RepositoryException
     */
    public static boolean hasPrimaryType(Node node, String typeName)
            throws RepositoryException {
        return typeName != null && typeName.equals(node.getPrimaryNodeType().getName());
    }

    /**
     * Renames the specified node to a temporary name.
     *
     * @param node The node to be renamed
     * @throws RepositoryException
     */
    public static void rename(Node node)
            throws RepositoryException {
        Node destination = node.getParent();
        Session session = node.getSession();
        String tmpName = JcrUtil.createValidChildName(destination, "tmp-" + System.currentTimeMillis());
        String tmpPath = destination.getPath() + "/" + tmpName;
        session.move(node.getPath(), tmpPath);
    }

}
