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
package com.adobe.aem.modernize.design.impl;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.design.PoliciesImportRule;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import com.day.cq.wcm.api.policies.ContentPolicyManager;
import com.day.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@SlingServlet(
        methods = "POST",
        paths = "/libs/cq/modernize/design/content/import",
        extensions = "json"
)
public class PoliciesImportServlet extends SlingAllMethodsServlet {

    private static Logger LOGGER = LoggerFactory.getLogger(PoliciesImportServlet.class);

    public static final String PARAM_PATHS = "paths";
    public static final String PARAM_DESIGN_PATH = "designPath";
    public static final String PARAM_TARGET_PATH = "targetPath";

    private static final String KEY_RESULT_PATH = "resultPath";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    @Reference
    PoliciesImportRuleService policiesImportRuleService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // Validate parameters
        RequestParameter[] paths = request.getRequestParameters(PARAM_PATHS);
        if (paths == null) {
            respondWrongParam(response, PARAM_PATHS);
            return;
        }
        String designPath = request.getParameter(PARAM_DESIGN_PATH);
        if (StringUtils.isEmpty(designPath)) {
            respondWrongParam(response, PARAM_DESIGN_PATH);
            return;
        }
        String targetPath = request.getParameter(PARAM_TARGET_PATH);
        if (StringUtils.isEmpty(targetPath)) {
            respondWrongParam(response, PARAM_TARGET_PATH);
            return;
        }

        try {
            // Get Policies import rules
            List<PoliciesImportRule> rules = policiesImportRuleService.getRules(request.getResourceResolver());

            long tick = System.currentTimeMillis();
            ResourceResolver resolver = request.getResourceResolver();

            Designer designer = resolver.adaptTo(Designer.class);
            Design design = designer.getDesign(PoliciesImportUtils.getDesignPath(designPath));

            PoliciesTreeImporter importer = new PoliciesTreeImporter(rules);

            targetPath = Text.makeCanonicalPath(targetPath + "/settings/wcm/policies");
            JSONObject results = new JSONObject();
            LOGGER.debug("Importing {} policies; target path: {}", paths.length, targetPath);

            // Iterate over all paths
            for (RequestParameter parameter : paths) {
                String path = parameter.getString();
                JSONObject json = new JSONObject();
                results.put(path, json);

                // Verify that the path exists
                Style style = design.getStyle(path);

                if (style == null) {
                    json.put(KEY_ERROR_MESSAGE, "Invalid style");
                    LOGGER.debug("Style {} doesn't exist at {}", path, designPath);
                    continue;
                }

                try {
                    // Import the policy
                    String policyPath = importer.importStyleAsPolicy(resolver, style, targetPath);
                    json.put(KEY_RESULT_PATH, policyPath);
                    LOGGER.debug("Successfully imported policy {} to {}", path, policyPath);
                } catch (RewriteException e) {
                    json.put(KEY_ERROR_MESSAGE, e.getMessage());
                    LOGGER.warn("Importing policy {} failed", path, e);
                }
            }
            response.setContentType("application/json");
            response.getWriter().write(results.toString());

            long tack = System.currentTimeMillis();
            LOGGER.debug("Imported {} policies in {} ms", paths.length, tack - tick);

        } catch (Exception e) {
            throw new ServletException("Caught exception while importing policies", e);
        }
    }

    private void respondWrongParam(SlingHttpServletResponse response, String param) throws IOException {
        LOGGER.warn("Missing parameter '{}'", param);
        response.setContentType("text/html");
        response.getWriter().println("Missing parameter '" + param + "'");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
}
