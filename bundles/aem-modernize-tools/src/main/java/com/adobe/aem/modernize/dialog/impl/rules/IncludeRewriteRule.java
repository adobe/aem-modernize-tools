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
import com.day.cq.commons.PathInfo;
import com.day.cq.commons.jcr.JcrUtil;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.request.RequestPathInfo;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Set;

import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.hasXtype;
import static com.adobe.aem.modernize.impl.RewriteUtils.hasPrimaryType;

/**
 * Rewrites widgets of xtype "cqinclude". The referenced widget is copied over and will be handled by
 * subsequent passes of the algorithm.
 */
@Component
@Service
@Properties({
        @Property(name = "service.ranking", intValue = 2)
})
public class IncludeRewriteRule extends AbstractDialogRewriteRule {
    
    private static final String CQINCLUDE = "cqinclude";
    
    public boolean matches(Node root)
            throws RepositoryException {
        return hasXtype(root, CQINCLUDE);
    }
    
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {
        // check if the 'path property exists
        if (!root.hasProperty(DialogConstants.PATH)) {
            throw new RewriteException("Missing include path");
        }
        
        // get path to included node
        RequestPathInfo info = new PathInfo(root.getProperty(DialogConstants.PATH).getString());
        String path = info.getResourcePath();
        
        // check if the path is valid
        Session session = root.getSession();
        if (!session.nodeExists(path)) {
            throw new RewriteException("Include path does not exist");
        }
        
        // remove original
        Node parent = root.getParent();
        String name = root.getName();
        root.remove();
        
        Node node = session.getNode(path);
        // check if referenced node is a widget collection
        if (hasPrimaryType(node, DialogConstants.CQ_WIDGET_COLLECTION)) {
            NodeIterator iterator = node.getNodes();
            Node newRoot = null;
            // copy all items of the widget collection
            while (iterator.hasNext()) {
                Node item = iterator.nextNode();
                Node copy = JcrUtil.copy(item, parent, JcrUtil.createValidChildName(parent, item.getName()));
                if (newRoot == null) {
                    newRoot = copy;
                }
            }
            // we return the first item as the new root
            return newRoot;
        } else {
            return JcrUtil.copy(session.getNode(path), parent, name);
        }
    }
    
}
