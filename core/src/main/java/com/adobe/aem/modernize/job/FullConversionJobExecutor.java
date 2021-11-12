package com.adobe.aem.modernize.job;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.NameConstants;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.api.designer.Cell;
import com.day.cq.wcm.api.designer.Designer;
import com.day.cq.wcm.api.designer.Style;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.policy.PolicyImportRuleService.*;

@Component(
    service = { JobExecutor.class },
    property = {
        JobExecutor.PROPERTY_TOPICS + "=" + FullConversionJobExecutor.JOB_TOPIC
    }
)
public class FullConversionJobExecutor extends AbstractConversionJobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/full";

  private static final String TMP_POLICY_PATH = "cq:policyPath";
  private static final String POLICIES = "policies";
  private static final String PN_POLICY = "cq:policy";
  private static final String POLICY_MAPPING_RESOURCE_TYPE = "wcm/core/components/policies/mappings";

  @Reference
  private PolicyImportRuleService policyService;

  @Reference
  private StructureRewriteRuleService structureService;

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(Job job, JobExecutionContext context, ConversionJobBucket bucket) {

    final boolean reprocess = isReprocess(bucket);
    final boolean overwritePolicies = isOverwrite(bucket);
    String targetPath = getTargetPath(bucket);

    Resource resource = bucket.getResource();
    ResourceResolver rr = resource.getResourceResolver();
    PageManager pm = rr.adaptTo(PageManager.class);

    Set<String> templateRules = getTemplateRules(bucket);
    Set<String> policyRules = getPolicyRules(bucket);
    String confDest = getTargetConfPath(bucket);
    Set<String> componentRules = getComponentRules(bucket);

    List<String> paths = bucket.getPaths();
    Set<String> importedPolicies = new HashSet<>();

    context.initProgress(paths.size(), -1);
    for (String path : paths) {
      Page page = pm.getPage(path);
      if (page == null) {
        context.log("Path [{}] was not a page, skipping.", path);
        bucket.getNotFound().add(path);
        context.incrementProgressCount(1);
        continue;
      }
      try {
        if (reprocess) {
          page = RewriteUtils.restore(pm, page);
        }

        // Walk page's content tree and find all styles and import them
        if (policyRules.isEmpty() || StringUtils.isBlank(confDest)) {
          context.log("No policy rules or target found, skipping skipping policy import.");
        } else {
          importPolicies(page, confDest, policyRules, overwritePolicies, importedPolicies);
        }

        RewriteUtils.createVersion(pm, page);
        if (!reprocess && StringUtils.isNotBlank(targetPath)) {
          page = RewriteUtils.copyPage(pm, page, targetPath);
        }

        if (templateRules.isEmpty()) {
          context.log("No template rules found, skipping structure conversion.");
        } else {
          structureService.apply(page, templateRules);
        }

        // Policies need to be applied before component processing - that will remove temp property.
        applyPolicies(context, page, confDest);

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

  // Import any styles used by this page - set the new policy reference for later use.
  private void importPolicies(Page page, String confDest, Set<String> rules, boolean overwrite, Set<String> imported) {

    ResourceResolver rr = page.getContentResource().getResourceResolver();
    Designer designer = rr.adaptTo(Designer.class);
    new AbstractResourceVisitor() {
      @Override
      protected void visit(@NotNull Resource resource) {
        Style style = designer.getStyle(resource);
        if (style == null) {
          return;
        }
        Cell cell = style.getCell();
        Iterator<String> it = cell.paths();
        Resource styleRes = null;
        while (it.hasNext() && styleRes == null) {
          style = designer.getStyle(resource, it.next());
          if (style == null) {
            continue;
          }
          styleRes = rr.getResource(style.getPath());
        }

        if (styleRes != null && !imported.contains(styleRes.getPath())) {
          try {
            policyService.apply(styleRes, confDest, rules, false, overwrite);
            String policyPath = styleRes.getValueMap().get(PN_IMPORTED, String.class);
            if (StringUtils.isNotBlank(policyPath)) {
              ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
              mvm.put(TMP_POLICY_PATH, policyPath);
            }
            imported.add(styleRes.getPath());
          } catch (RewriteException e) {
            logger.error("Unable to import style.", e);
          }
        }
      }
    }.accept(page.getContentResource());
  }

  private void applyPolicies(JobExecutionContext context, Page page, String confRoot) {
    ResourceResolver rr = page.getContentResource().getResourceResolver();
    String confPolicyRoot = PathUtils.concat(confRoot, POLICY_REL_PATH);
    String templatePath = page.getProperties().get(NameConstants.PN_TEMPLATE, String.class);
    String templatePolicyRoot = PathUtils.concat(templatePath, POLICIES);
    String pagePath = page.getPath();
    new AbstractResourceVisitor() {
      @Override
      protected void visit(@NotNull Resource resource) {
        ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
        String policyPath = mvm.get(TMP_POLICY_PATH, String.class);
        try {
          if (StringUtils.isNotBlank(policyPath)) {
            if (policyPath.startsWith(confPolicyRoot)) {
              String policyRef = policyPath.replaceFirst(confPolicyRoot + "/", ""); // Strip off root
              String compType = PathUtils.getParentPath(policyRef); // Get component type for applying mapping
              String containerPath = PathUtils.getParentPath(resource.getPath()).replace(pagePath, templatePolicyRoot);
              String mappingPath = PathUtils.concat(containerPath, compType);
              Resource mapping = ResourceUtil.getOrCreateResource(rr, mappingPath, POLICY_MAPPING_RESOURCE_TYPE, null, false);
              ModifiableValueMap mappingVm = mapping.adaptTo(ModifiableValueMap.class);
              if (StringUtils.isBlank(mappingVm.get(PN_POLICY, String.class))) { // Don't overwrite existing Policies
                mappingVm.put(PN_POLICY, policyRef);
              }
            }
            mvm.remove(TMP_POLICY_PATH);
          }
        } catch (PersistenceException e) {
          logger.error("Unable to apply policy due to repository error.", e);
          context.log("Unable to apply policy due to repository error: {}", e.getLocalizedMessage());
        }
      }
    }.accept(page.getContentResource());
  }

}
