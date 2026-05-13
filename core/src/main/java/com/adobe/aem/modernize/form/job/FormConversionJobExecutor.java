package com.adobe.aem.modernize.form.job;

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
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.sling.api.resource.ModifiableValueMap;
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
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private static final String FRAGMENTS_SUBFOLDER = "fragments";
    private static final String FRAG_REF_PROPERTY = "fragRef";
    private static final String FRAGMENT_PATH_PROPERTY = "fragmentPath";
    private static final String SLING_RESOURCE_TYPE = "sling:resourceType";
    private static final String AF1_RESOURCE_TYPE_PREFIX = "fd/af/";

    @Reference
    private ComponentRewriteRuleService componentService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket) {
        final ConversionJob.PageHandling pageHandling = getPageHandling(bucket);
        String sourceRoot = getSourceRoot(bucket);

        Resource resource = bucket.getResource();
        ResourceResolver rr = resource.getResourceResolver();
        PageManager pm = rr.adaptTo(PageManager.class);
        String targetRoot = normalizeTargetRoot(rr, getTargetRoot(bucket));
        Set<String> componentRules = getComponentRules(bucket);

        List<String> paths = bucket.getPaths();
        context.initProgress(paths.size(), -1);

        // Shared across all forms in this bucket — each unique fragment converted at most once
        Set<String> convertedFragments = new HashSet<>();
        // path → list of human-readable warnings written to the JCR bucket node for UI display
        List<String> bucketWarnings = new ArrayList<>();

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

            long startMs = System.currentTimeMillis();
            context.log("Converting form [{0}] ({1}/{2})", path, paths.indexOf(path) + 1, paths.size());
            logger.info("Starting conversion for form [{}]", path);
            try {
                if (pageHandling == RESTORE) {
                    context.log("Restoring previous version for [{0}]", path);
                    page = RewriteUtils.restore(pm, page);
                }

                context.log("Creating pre-conversion version for [{0}]", path);
                RewriteUtils.createVersion(pm, page);

                if (pageHandling == COPY) {
                    context.log("Copying form asset and page for [{0}]", path);
                    Node formAssetNode = rr.getResource(getFormsAssetPathFromPagePath(page.getPath())).adaptTo(Node.class);
                    Node targetNode = rr.getResource(getFormsAssetPathFromPagePath(targetRoot)).adaptTo(Node.class);
                    JcrUtil.copy(formAssetNode, targetNode, formAssetNode.getName());
                    page = RewriteUtils.copyPage(pm, page, sourceRoot, targetRoot);
                }

                convertReferencedFragments(page, pageHandling, targetRoot, componentRules, pm, rr, context, convertedFragments, path, bucketWarnings);

                if (componentRules.isEmpty()) {
                    context.log("No component rules found, skipping component conversion for [{0}].", path);
                } else {
                    context.log("Applying {0} component rule(s) to form [{1}]", componentRules.size(), page.getPath());
                    long ruleMs = System.currentTimeMillis();
                    componentService.apply(page.getContentResource(), componentRules, true);
                    context.log("Form rules applied in {0}ms", System.currentTimeMillis() - ruleMs);
                    collectWarnings(page.getContentResource(), path, null, bucketWarnings, context);
                }

                if (pageHandling == COPY && !convertedFragments.isEmpty()) {
                    try {
                        updateFragmentPaths(page.adaptTo(Node.class).getNode(JcrConstants.JCR_CONTENT),
                                targetRoot + "/" + FRAGMENTS_SUBFOLDER);
                        rr.adaptTo(Session.class).save();
                    } catch (RepositoryException e) {
                        logger.warn("Could not update fragment paths in converted form [{}]: {}", page.getPath(), e.getMessage());
                    }
                }

                long elapsed = System.currentTimeMillis() - startMs;
                context.log("Successfully converted [{0}] in {1}ms", path, elapsed);
                logger.info("Completed conversion for form [{}] in {}ms", path, elapsed);
                bucket.getSuccess().add(path);
            } catch (WCMException e) {
                bucketWarnings.add(path + "||Conversion failed: " + e.getMessage());
                logger.error("Error occurred while trying to manage page versions for [{}]", path, e);
                context.log("Failed to manage page version for [{0}]: {1}", path, e.getMessage());
                bucket.getFailed().add(path);
            } catch (RewriteException e) {
                logger.error("Conversion resulted in an error for [{}]", path, e);
                context.log("Conversion failed for [{0}]: {1}", path, e.getMessage());
                bucketWarnings.add(path + "||Conversion error: " + e.getMessage());
                bucket.getFailed().add(path);
            } catch (RepositoryException e) {
                logger.error("Failed to copy forms asset node for [{}]", path, e);
                context.log("Repository error during conversion of [{0}]: {1}", path, e.getMessage());
                bucketWarnings.add(path + "||Repository error: " + e.getMessage());
                bucket.getFailed().add(path);
            }
            context.incrementProgressCount(1);
        }

        if (!bucketWarnings.isEmpty()) {
            ModifiableValueMap mvm = bucket.getResource().adaptTo(ModifiableValueMap.class);
            if (mvm != null) {
                mvm.put("warnings", bucketWarnings.toArray(new String[0]));
            }
        }
    }

    @Override
    protected ResourceResolverFactory getResourceResolverFactory() {
        return resourceResolverFactory;
    }

    private String getFormsAssetPathFromPagePath(String pagePath) {
        return StringUtils.replace(pagePath, AF_ROOT, DAM_ROOT);
    }

    // If targetRoot was provided as a DAM path (/content/dam/formsanddocuments/...),
    // translate it to the corresponding forms/af path and create the folder if absent.
    private String normalizeTargetRoot(ResourceResolver rr, String targetRoot) {
        if (StringUtils.isBlank(targetRoot)) {
            return targetRoot;
        }
        String afPath = targetRoot.startsWith(DAM_ROOT)
                ? StringUtils.replace(targetRoot, DAM_ROOT, AF_ROOT)
                : targetRoot;
        if (rr.getResource(afPath) == null) {
            try {
                JcrUtil.createPath(afPath, JcrConstants.NT_UNSTRUCTURED, "sling:Folder", rr.adaptTo(Session.class), false);
            } catch (javax.jcr.RepositoryException e) {
                logger.warn("Could not create target folder at [{}]: {}", afPath, e.getMessage());
            }
        }
        return afPath;
    }

    private void convertReferencedFragments(Page formPage, ConversionJob.PageHandling pageHandling,
            String targetRoot, Set<String> componentRules,
            PageManager pm, ResourceResolver rr, JobExecutionContext context,
            Set<String> convertedFragments, String formPath, List<String> bucketWarnings) {

        Set<String> fragRefs;
        try {
            Node contentNode = formPage.adaptTo(Node.class).getNode(JcrConstants.JCR_CONTENT);
            fragRefs = collectFragmentRefs(contentNode);
        } catch (RepositoryException e) {
            logger.warn("Could not scan fragment refs in [{}]: {}", formPage.getPath(), e.getMessage());
            return;
        }

        if (fragRefs.isEmpty()) {
            return;
        }

        context.log("Found {0} fragment reference(s) in [{1}]", fragRefs.size(), formPage.getPath());
        int fragIdx = 0;
        for (String fragRef : fragRefs) {
            fragIdx++;
            if (convertedFragments.contains(fragRef)) {
                context.log("  [{0}/{1}] Fragment already converted, skipping: {2}", fragIdx, fragRefs.size(), fragRef);
                continue;
            }

            // fragRef may be a DAM path (/content/dam/formsanddocuments/...) — resolve to the AF page path
            String fragPagePath = fragRef.startsWith(DAM_ROOT)
                    ? StringUtils.replace(fragRef, DAM_ROOT, AF_ROOT)
                    : fragRef;
            Page fragPage = pm.getPage(fragPagePath);
            if (fragPage == null) {
                context.log("  [{0}/{1}] Fragment page not found, skipping: {2}", fragIdx, fragRefs.size(), fragRef);
                continue;
            }

            context.log("  [{0}/{1}] Converting fragment: {2}", fragIdx, fragRefs.size(), fragPage.getName());
            long fragMs = System.currentTimeMillis();
            try {
                Resource convertedFragResource;
                if (pageHandling == COPY) {
                    convertedFragResource = convertFragmentCopy(fragPage, targetRoot, componentRules, pm, rr, context);
                } else {
                    convertFragmentInPlace(fragPage, componentRules, context);
                    convertedFragResource = fragPage.getContentResource();
                }
                convertedFragments.add(fragRef);
                long fragElapsed = System.currentTimeMillis() - fragMs;
                context.log("  [{0}/{1}] Fragment [{2}] done in {3}ms", fragIdx, fragRefs.size(),
                        fragPage.getName(), fragElapsed);
                collectWarnings(convertedFragResource, formPath, "fragment " + fragPage.getName(), bucketWarnings, context);
            } catch (WCMException | RewriteException | RepositoryException e) {
                context.log("  [{0}/{1}] Fragment [{2}] FAILED: {3}", fragIdx, fragRefs.size(),
                        fragPage.getName(), e.getMessage());
                bucketWarnings.add(formPath + "||Fragment " + fragPage.getName() + " failed: " + e.getMessage());
                logger.error("Fragment conversion failed for [{}]", fragRef, e);
            }
        }
    }

    private Resource convertFragmentCopy(Page fragPage, String targetRoot, Set<String> componentRules,
            PageManager pm, ResourceResolver rr, JobExecutionContext context)
            throws WCMException, RewriteException, RepositoryException {

        String fragmentsTarget = targetRoot + "/" + FRAGMENTS_SUBFOLDER;

        ensureFolderExists(rr, fragmentsTarget);
        ensureFolderExists(rr, getFormsAssetPathFromPagePath(fragmentsTarget));

        String fragDamPath = getFormsAssetPathFromPagePath(fragPage.getPath());
        Resource fragDamResource = rr.getResource(fragDamPath);
        if (fragDamResource != null) {
            Node fragDamNode = fragDamResource.adaptTo(Node.class);
            Node damTargetFolder = rr.getResource(getFormsAssetPathFromPagePath(fragmentsTarget)).adaptTo(Node.class);
            JcrUtil.copy(fragDamNode, damTargetFolder, fragDamNode.getName());
        }

        String fragParent = fragPage.getPath().substring(0, fragPage.getPath().lastIndexOf('/'));
        Page copiedFrag = RewriteUtils.copyPage(pm, fragPage, fragParent, fragmentsTarget);

        if (!componentRules.isEmpty()) {
            componentService.apply(copiedFrag.getContentResource(), componentRules, true);
        }
        return copiedFrag.getContentResource();
    }

    private void convertFragmentInPlace(Page fragPage, Set<String> componentRules,
            JobExecutionContext context) throws RewriteException {

        if (componentRules.isEmpty()) {
            return;
        }

        componentService.apply(fragPage.getContentResource(), componentRules, true);
    }

    private Set<String> collectFragmentRefs(Node contentNode) throws RepositoryException {
        Set<String> refs = new LinkedHashSet<>();
        for (Node node : new TreeTraverser(contentNode)) {
            if (node.hasProperty(FRAG_REF_PROPERTY)) {
                refs.add(node.getProperty(FRAG_REF_PROPERTY).getString());
            }
        }
        return refs;
    }

    private void updateFragmentPaths(Node contentNode, String fragmentsTargetFolder)
            throws RepositoryException {
        for (Node node : new TreeTraverser(contentNode)) {
            if (node.hasProperty(FRAGMENT_PATH_PROPERTY)) {
                String oldPath = node.getProperty(FRAGMENT_PATH_PROPERTY).getString();
                String fragName = oldPath.substring(oldPath.lastIndexOf('/') + 1);
                node.setProperty(FRAGMENT_PATH_PROPERTY, fragmentsTargetFolder + "/" + fragName);
            }
        }
    }

    private void ensureFolderExists(ResourceResolver rr, String path) {
        if (rr.getResource(path) == null) {
            try {
                JcrUtil.createPath(path, JcrConstants.NT_UNSTRUCTURED, "sling:Folder",
                        rr.adaptTo(Session.class), false);
            } catch (RepositoryException e) {
                logger.warn("Could not create folder at [{}]: {}", path, e.getMessage());
            }
        }
    }

    private void collectWarnings(Resource resource, String formPath, String label,
            List<String> bucketWarnings, JobExecutionContext context) {
        if (resource == null) {
            return;
        }
        try {
            Node contentNode = resource.adaptTo(Node.class);
            if (contentNode == null) {
                return;
            }
            int count = 0;
            for (Node node : new TreeTraverser(contentNode)) {
                if (node.hasProperty(SLING_RESOURCE_TYPE)) {
                    String rt = node.getProperty(SLING_RESOURCE_TYPE).getString();
                    if (rt.startsWith(AF1_RESOURCE_TYPE_PREFIX)) {
                        String prefix = label != null ? label + ": " : "";
                        bucketWarnings.add(formPath + "||" + prefix + node.getPath() + " — unconverted resource type: " + rt);
                        count++;
                    }
                }
            }
            if (count > 0) {
                context.log("    {0} component(s) not converted in [{1}]", count,
                        label != null ? label : resource.getPath());
            }
        } catch (RepositoryException e) {
            logger.warn("Could not scan for unconverted components in [{}]", resource.getPath(), e);
        }
    }
}
