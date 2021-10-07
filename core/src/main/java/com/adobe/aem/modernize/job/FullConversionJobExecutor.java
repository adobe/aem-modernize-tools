package com.adobe.aem.modernize.job;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

@Component(
    service = { JobExecutor.class },
    property = {
        JobExecutor.PROPERTY_TOPICS + "=" + FullConversionJobExecutor.JOB_TOPIC
    }
)
public class FullConversionJobExecutor extends AbstractConversionJobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/full";
  public static final String VERSION_LABEL = "Pre-Modernization";
  public static final String VERSION_DESC = "Version of content before the modernization process was performed.";

  @Reference
  private PoliciesImportRuleService policyService;

  @Reference
  private StructureRewriteRuleService structureService;

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(Job job, JobExecutionContext context, ResourceResolver resourceResolver) throws RewriteException, PersistenceException {
    String[] paths = job.getProperty(PN_PATHS, String[].class);

    final boolean reprocess = job.getProperty(PN_REPROCESS, false);
    Designer designer = resourceResolver.adaptTo(Designer.class);
    context.initProgress(paths.length * 2, -1);

    String[] preparedPaths = preparePages(context, resourceResolver, paths, reprocess);
    for (String path : preparedPaths) {
      Page root = resourceResolver.getResource(path).adaptTo(Page.class);
      processDesign(context, designer.getDesign(root), job, reprocess);
      processPage(context, root, job, reprocess);
      processComponents(context, root.getContentResource(), job);
      context.incrementProgressCount(1);
    }
  }

  private String[] preparePages(JobExecutionContext context, ResourceResolver resourceResolver, String[] paths, boolean reprocess) {
    List<String> processed = new ArrayList<>(paths.length);
    PageManager pm = resourceResolver.adaptTo(PageManager.class);
    for (String path : paths) {
      Page page = pm.getPage(path);
      if (page == null) {
        context.log("Path [{}] does not resolve to a Page, removing from list.", path);
        context.incrementProgressCount(1);
        continue;
      }

      try {
        String version = pm.createRevision(page, VERSION_LABEL, VERSION_DESC).getId();
        ModifiableValueMap mvm = page.getContentResource().adaptTo(ModifiableValueMap.class);
        // When reprocessing, restore to the previous version, and keep that id for future use.
        if (reprocess) {
          String prevVersion = mvm.get(PN_PRE_MODERNIZE_VERSION, String.class);
          if (StringUtils.isNotBlank(prevVersion)) {
            pm.restore(page.getPath(), prevVersion);
            version = prevVersion;
          }
        }
        mvm.put(PN_PRE_MODERNIZE_VERSION, version);
        resourceResolver.commit();
        processed.add(path);
        context.incrementProgressCount(1);
      } catch (WCMException | PersistenceException e) {
        logger.error("Error occurred trying to create or restore a page version", e);
        context.log("Could not prepare page [{}], skipping.", page.getPath());
      }
    }
    return processed.toArray(new String[] {});
  }

  /*
    Import the policies using the rules.
   */
  private void processDesign(JobExecutionContext context, Design design, Job job, boolean reprocess) {

    final String[] rules = job.getProperty(PN_POLICY_RULES, String[].class);
    if (rules == null || rules.length == 0) {
      context.log("No policy rules found, skipping skipping policy import.");
    } else {
      policyService.apply(design, rules, true, reprocess);
    }
  }

  /*
    Process the resource according to the provided rules.
   */
  private void processPage(JobExecutionContext context, Page page, Job job, boolean reprocess) {
    final String[] rules = job.getProperty(PN_TEMPLATE_RULES, String[].class);
    if (rules == null || rules.length == 0) {
      context.log("No template rules found, skipping structure conversion.");
    } else {
      structureService.apply(page, rules);
    }
  }

  /*
    Process the resource using the Component rules.
   */
  private void processComponents(JobExecutionContext context, Resource root, Job job) throws RewriteException {
    final String[] rules = job.getProperty(PN_COMPONENT_RULES, String[].class);
    if (rules == null || rules.length == 0) {
      context.log("No component rules found, skipping skipping component conversion.");
    } else {
      componentService.apply(root, rules, true);
    }
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }
}
