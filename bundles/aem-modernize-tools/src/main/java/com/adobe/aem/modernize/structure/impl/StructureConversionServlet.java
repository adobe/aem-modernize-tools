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
package com.adobe.aem.modernize.structure.impl;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.aem.modernize.structure.impl.rules.PageRewriteRule;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@SlingServlet(
        methods = "POST",
        paths = "/libs/cq/modernize/templatestructure/content/convert",
        extensions = "json"
)
public class StructureConversionServlet extends SlingAllMethodsServlet {

    public static final String PARAM_PATHS = "paths";
    private static final String KEY_RESULT_PATH = "resultPath";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    private Logger logger = LoggerFactory.getLogger(StructureConversionServlet.class);

    @Reference
    StructureRewriteRuleService structureRewriteRuleService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException {
        // validate request data
        try {
            // FIXME: need to send json content type and not handle this like a form post
            String requestData = request.getRequestParameterList().get(0).getName();
            JSONObject jsonRequest = new JSONObject(requestData);

            if (!jsonRequest.has("pages")) {
                logger.warn("Missing pages");
                response.setContentType("text/html");
                response.getWriter().println("Missing parameter");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            long tick = System.currentTimeMillis();
            Session session = request.getResourceResolver().adaptTo(Session.class);
            JSONObject results = new JSONObject();
            JSONArray pages = jsonRequest.getJSONArray("pages");
            logger.debug("Converting {} structures", pages.length());

            // iterate over all paths
            for (int i = 0; i < pages.length(); i++) {
                // get rules for the structure
                String path = pages.getJSONObject(i).getString("path");
                String template = pages.getJSONObject(i).getString("template");
                List<StructureRewriteRule> rules = structureRewriteRuleService.getRules(request.getResourceResolver());

                // remove rule not pertaining to chosen editable template
                for (int i1 = 0; i1 < rules.size(); i1++) {
                    StructureRewriteRule rule = rules.get(i1);
                    if (rule instanceof PageRewriteRule) {
                        String editableTemplate = ((PageRewriteRule) rule).getEditableTemplate();
                        if (!editableTemplate.equals(template)) {
                            rules.remove(rule);
                        }
                    }
                }
                StructureTreeRewriter rewriter = new StructureTreeRewriter(rules);

                JSONObject json = new JSONObject();
                results.put(path, json);

                // verify that the path exists
                if (!session.nodeExists(path)) {
                    json.put(KEY_ERROR_MESSAGE, "Invalid path");
                    logger.debug("Path {} doesn't exist", path);
                    continue;
                }

                try {
                    // rewrite the structure
                    Node result = rewriter.rewrite(session.getNode(path));
                    json.put(KEY_RESULT_PATH, result.getPath());
                    logger.debug("Successfully convertexd structure {} to {}", path, result.getPath());
                } catch (RewriteException e) {
                    json.put(KEY_ERROR_MESSAGE, e.getMessage());
                    logger.warn("Converting structure {} failed", path, e);
                }
            }

            response.setContentType("application/json");
            response.getWriter().write(results.toString());

            long tack = System.currentTimeMillis();
            logger.debug("Rewrote {} structures in {} ms", pages.length(), tack - tick);
        } catch (Exception e) {
            throw new ServletException("Caught exception while rewriting structures", e);
        }
    }
}
