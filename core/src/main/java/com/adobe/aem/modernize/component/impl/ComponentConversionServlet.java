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
package com.adobe.aem.modernize.component.impl;

import java.io.IOException;
import java.util.List;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/components/convert/component",
        SLING_SERVLET_METHODS + "=POST",
        SLING_SERVLET_SELECTORS + "=convert",
        SLING_SERVLET_EXTENSIONS + "=json"
    }
)
public class ComponentConversionServlet extends SlingAllMethodsServlet {

    public static final String PARAM_PATHS = "paths";
    private static final String KEY_RESULT_PATH = "resultPath";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    private Logger logger = LoggerFactory.getLogger(ComponentConversionServlet.class);

    @Reference
    ComponentRewriteRuleService componentRewriteRuleService;

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

        try {
            // get component rewrite rules
            List<ComponentRewriteRule> rules = componentRewriteRuleService.getRules(request.getResourceResolver());

            long tick = System.currentTimeMillis();
            Session session = request.getResourceResolver().adaptTo(Session.class);
            ComponentTreeRewriter rewriter = new ComponentTreeRewriter(rules);
            JSONObject results = new JSONObject();
            logger.debug("Converting {} components", paths.length);

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
                    // rewrite the component
                    Node result = rewriter.rewrite(session.getNode(path));
                    json.put(KEY_RESULT_PATH, result.getPath());
                    logger.debug("Successfully converted component {} to {}", path, result.getPath());
                } catch (RewriteException e) {
                    json.put(KEY_ERROR_MESSAGE, e.getMessage());
                    logger.warn("Converting component {} failed", path, e);
                }
            }
            response.setContentType("application/json");
            response.getWriter().write(results.toString());

            long tack = System.currentTimeMillis();
            logger.debug("Rewrote {} components in {} ms", paths.length, tack - tick);
        } catch (Exception e) {
            throw new ServletException("Caught exception while rewriting components", e);
        }
    }
}
