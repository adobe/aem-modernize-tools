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

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.adobe.aem.modernize.dialog.AbstractDialogRewriteRule;
import com.adobe.aem.modernize.dialog.DialogRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.osgi.service.component.annotations.Component;
import static com.adobe.aem.modernize.impl.RewriteUtils.rename;
import static com.adobe.aem.modernize.dialog.DialogRewriteUtils.*;

/**
 * Rewrites widgets of xtype "multifield". The "fieldConfig" subnode (if existing) is renamed to "field" and
 * will be handled by subsequent passes of the algorithm.
 */
@Component(
    service = { DialogRewriteRule.class }
)
public class MultifieldRewriteRule extends AbstractDialogRewriteRule {

    private static final String XTYPE = "multifield";
    private static final String GRANITEUI_MULTIFIELD_RT = "granite/ui/components/coral/foundation/form/multifield";
    private static final String GRANITEUI_TEXTFIELD_RT = "granite/ui/components/coral/foundation/form/textfield";

    @Override
    public String getId() {
        return this.getClass().getName();
    }

    public boolean matches(Node root)
            throws RepositoryException {
        return hasXtype(root, XTYPE);
    }

    public Node applyTo(Node root, Set<Node> finalNodes) throws RepositoryException {
        Node parent = root.getParent();
        String name = root.getName();
        rename(root);

        // add node for multifield
        Node newRoot = parent.addNode(name, "nt:unstructured");
        finalNodes.add(newRoot);
        newRoot.setProperty("sling:resourceType", GRANITEUI_MULTIFIELD_RT);
        // set properties
        copyProperty(root, "fieldLabel", newRoot, "fieldLabel");
        copyProperty(root, "fieldDescription", newRoot, "fieldDescription");

        Node field;
        if (root.hasNode("fieldConfig")) {
            field = JcrUtil.copy(root.getNode("fieldConfig"), newRoot, "field");
            field.setPrimaryType("cq:Widget");
            copyProperty(root, "name", field, "name");
        } else {
            field = newRoot.addNode("field", "nt:unstructured");
            finalNodes.add(field);
            field.setProperty("sling:resourceType", GRANITEUI_TEXTFIELD_RT);
        }

        // remove old root and return new root
        root.remove();
        return newRoot;
    }

}
