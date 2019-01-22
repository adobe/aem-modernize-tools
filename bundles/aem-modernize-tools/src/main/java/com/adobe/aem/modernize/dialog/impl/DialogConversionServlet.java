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
package com.adobe.aem.modernize.dialog.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;

import com.adobe.aem.modernize.dialog.DialogRewriteException;
import com.adobe.aem.modernize.dialog.DialogRewriteRule;
import com.adobe.aem.modernize.dialog.impl.rules.NodeBasedRewriteRule;
import com.day.cq.commons.jcr.JcrConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingServlet(
        methods = "POST",
        paths = "/libs/cq/dialogconversion/content/convert",
        extensions = "json"
)
public class DialogConversionServlet extends SlingAllMethodsServlet {

    /**
     * Relative path to the node containing node-based dialog rewrite rules
     */
    public static final String RULES_SEARCH_PATH = "cq/dialogconversion/rules";

    public static final String PARAM_PATHS = "paths";
    private static final String KEY_RESULT_PATH = "resultPath";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    private Logger logger = LoggerFactory.getLogger(DialogConversionServlet.class);

    /**
     * Keeps track of OSGi services implementing dialog rewrite rules
     */
    @Reference(
            referenceInterface = DialogRewriteRule.class,
            cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindRule",
            unbind = "unbindRule"
    )
    private List<DialogRewriteRule> rules = Collections.synchronizedList(new LinkedList<DialogRewriteRule>());

    @SuppressWarnings("unused")
    public void bindRule(DialogRewriteRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(DialogRewriteRule rule) {
        rules.remove(rule);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // validate 'paths' parameter
        RequestParameter[] paths = request.getRequestParameters(PARAM_PATHS);
        if (paths == null) {
            logger.warn("Missing parameter '" + PARAM_PATHS + "'");
            response.setContentType("text/html");
            response.getWriter().println("Missing parameter '" + PARAM_PATHS + "'");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // get dialog rewrite rules
        List<DialogRewriteRule> rules = getRules(request.getResourceResolver());

        long tick = System.currentTimeMillis();
        Session session = request.getResourceResolver().adaptTo(Session.class);
        DialogTreeRewriter rewriter = new DialogTreeRewriter(rules);
        JSONObject results = new JSONObject();
        logger.debug("Converting {} dialogs", paths.length);

        try {
            // iterate over all paths
            for (RequestParameter parameter : paths) {
                String path = parameter.getString();
                JSONObject json = new JSONObject();
                results.put(path, json);

                // verify that the path exists
                if (!session.nodeExists(path)) {
                    json.put(KEY_ERROR_MESSAGE, "Invalid path");
                    logger.debug("Path {} doesn't exist", path);
                    continue;
                }

                try {
                    // rewrite the dialog
                    Node result = rewriter.rewrite(session.getNode(path));
                    json.put(KEY_RESULT_PATH, result.getPath());
                    logger.debug("Successfully converted dialog {} to {}", path, result.getPath());
                } catch (DialogRewriteException e) {
                    json.put(KEY_ERROR_MESSAGE, e.getMessage());
                    logger.warn("Converting dialog {} failed", path, e);
                }
            }
            response.setContentType("application/json");
            response.getWriter().write(results.toString());

            long tack = System.currentTimeMillis();
            logger.debug("Rewrote {} dialogs in {} ms", paths.length, tack - tick);
        } catch (Exception e) {
            throw new ServletException("Caught exception while rewriting dialogs", e);
        }
    }

    private List<DialogRewriteRule> getRules(ResourceResolver resolver)
            throws ServletException {
        final List<DialogRewriteRule> rules = new LinkedList<DialogRewriteRule>();

        // 1) rules provided as OSGi services
        // (we need to synchronize, since the 'addAll' will iterate over 'rules')
        synchronized (this.rules) {
            rules.addAll(this.rules);
        }
        int nb = rules.size();

        // 2) node-based rules
        Resource resource = resolver.getResource(RULES_SEARCH_PATH);
        if (resource != null) {
            try {
                Node rulesContainer = resource.adaptTo(Node.class);
                NodeIterator iterator = rulesContainer.getNodes();
                while (iterator.hasNext()) {
                    Node nextNode = iterator.nextNode();
                    if (isFolder(nextNode)) {
                        // add first level folder rules
                        NodeIterator nodeIterator = nextNode.getNodes();
                        while (nodeIterator.hasNext()) {
                            Node nestedNode = nodeIterator.nextNode();
                            // don't include nested folders
                            if (!isFolder(nestedNode)) {
                                rules.add(new NodeBasedRewriteRule(nestedNode));
                            }
                        }
                    } else {
                        // add rules directly at the rules search path
                        rules.add(new NodeBasedRewriteRule(nextNode));
                    }
                }
            } catch (RepositoryException e) {
                throw new ServletException("Caught exception while collecting rewrite rules", e);
            }
        }

        // sort rules according to their ranking
        Collections.sort(rules, new RuleComparator());

        logger.debug("Found {} rules ({} Java-based, {} node-based)", nb, rules.size() - nb);
        for (DialogRewriteRule rule : rules) {
            logger.debug(rule.toString());
        }
        return rules;
    }

    private class RuleComparator implements Comparator<DialogRewriteRule> {

        public int compare(DialogRewriteRule rule1, DialogRewriteRule rule2) {
            int ranking1 = rule1.getRanking();
            int ranking2 = rule2.getRanking();
            return Double.compare(ranking1, ranking2);
        }

    }

    private boolean isFolder(Node node) throws RepositoryException {
        String primaryType = node.getPrimaryNodeType().getName();

        return primaryType.equals("sling:Folder")
            || primaryType.equals("sling:OrderedFolder")
            || primaryType.equals(JcrConstants.NT_FOLDER);
    }
}
