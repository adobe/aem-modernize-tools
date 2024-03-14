package com.adobe.aem.modernize.job;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2024 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.adobe.aem.modernize.model.ConversionJob.PageHandling.COPY;
import static com.adobe.aem.modernize.model.ConversionJob.PageHandling.RESTORE;

@Component(
        service = { JobExecutor.class },
        property = {
                JobExecutor.PROPERTY_TOPICS + "=" + FormConversionJobExecutor.JOB_TOPIC
        }
)
public class FormConversionJobExecutor extends AbstractConversionJobExecutor {
    public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/form";

    private static final String AF_ROOT = "/content/forms/af";
    private static final String DAM_ROOT = "/content/dam/formsanddocuments";

    @Reference
    private ComponentRewriteRuleService componentService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket) {
        final ConversionJob.PageHandling pageHandling = getPageHandling(bucket);
        String sourceRoot = getSourceRoot(bucket);
        String targetRoot = getTargetRoot(bucket);

        Resource resource = bucket.getResource();
        ResourceResolver rr = resource.getResourceResolver();
        PageManager pm = rr.adaptTo(PageManager.class);
        Set<String> componentRules = getComponentRules(bucket);

        List<String> paths = bucket.getPaths();
        context.initProgress(paths.size(), -1);

        for (String path : paths) {
            Page page = pm.getPage(path);
            if (page == null) {
                bucket.getNotFound().add(path);
                context.incrementProgressCount(1);
                continue;
            }

            if (pageHandling == COPY && (StringUtils.isBlank(sourceRoot) || StringUtils.isBlank(targetRoot))) {
                bucket.getFailed().add(path);
                continue;
            }

            try {
                if (pageHandling == RESTORE) {
                    page = RewriteUtils.restore(pm, page);
                }

                RewriteUtils.createVersion(pm, page);

                if (pageHandling == COPY) {
                    // copy asset part for forms
                    Node formAssetNode = rr.getResource(getFormsAssetPathFromPagePath(page.getPath())).adaptTo(Node.class);
                    Node targetNode = rr.getResource(getFormsAssetPathFromPagePath(targetRoot)).adaptTo(Node.class);
                    JcrUtil.copy(formAssetNode, targetNode, formAssetNode.getName());

                    // copy page part for forms
                    page = RewriteUtils.copyPage(pm, page, sourceRoot, targetRoot);
                }

                if (componentRules.isEmpty()) {
                    context.log("No component rules found, skipping skipping component conversion.");
                } else {
                    componentService.apply(page.getContentResource(), componentRules, true);
                    fixChildrenOrder(page);
                }

                bucket.getSuccess().add(path);
            } catch (WCMException e) {
                logger.error("Error occurred while trying to manage page versions.", e);
                bucket.getFailed().add(path);
            } catch (RewriteException e) {
                logger.error("Conversion resulted in an error", e);
                bucket.getFailed().add(path);
            } catch (RepositoryException e) {
                logger.error("Failed to copy forms asset node. Conversion resulted in an error", e);
                bucket.getFailed().add(path);
            }
            context.incrementProgressCount(1);
        }
    }

    @Override
    protected ResourceResolverFactory getResourceResolverFactory() {
        return resourceResolverFactory;
    }

    private void fixChildrenOrder(Page page) throws RewriteException {
        Iterator<Page> children = page.listChildren();
        if (children.hasNext()) {
            Node node = page.adaptTo(Node.class);
            try {
                node.orderBefore(NameConstants.NN_CONTENT, children.next().getName());
            } catch (RepositoryException e) {
                throw new RewriteException("Unable to re-order page's JCR Content Node.", e);
            }
        }
    }

    private String getFormsAssetPathFromPagePath(String pagePath) {
        return StringUtils.replace(pagePath, AF_ROOT, DAM_ROOT);
    }
}
