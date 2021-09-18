package com.adobe.aem.modernize.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
import com.day.cq.wcm.api.designer.Design;
import com.day.cq.wcm.api.designer.Designer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.model.ConversionJobItem.*;

@Component(
    service = { JobExecutor.class },
    properties = {
        JobExecutor.PROPERTY_TOPICS + "=" + FullConversionJobExecutor.JOB_TOPIC
    }
)
public class FullConversionJobExecutor implements JobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/full";
  public static final String VERSION_LABEL = "Pre-Modernization";
  public static final String VERSION_DESC = "Version of content before the modernization process was performed.";
  private static final Logger logger = LoggerFactory.getLogger(FullConversionJobExecutor.class);
  private static final String SERVICE_NAME = "convert-content";

  @Reference
  private PoliciesImportRuleService policyService;

  @Reference
  private StructureRewriteRuleService structureService;

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  public JobExecutionResult process(final Job job, final JobExecutionContext context) {

    String[] paths = job.getProperty(PN_PAGE_PATHS, String[].class);
    if (paths == null || paths.length == 0) {
      return context.result().message("Invalid job state, no paths specified to process.").failed();
    }
    ResourceResolver resourceResolver = null;
    try {
      resourceResolver = login();

      final boolean reprocess = job.getProperty(PN_REPROCESS, false);
      updateTracking(job, resourceResolver);
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
      resourceResolver.commit();

      // TODO: Clean this up
      if (paths.length != preparedPaths.length) {
        return context.result().message(String.format("Number requested paths to process [{}] does not match processed [{}]", paths.length, preparedPaths.length)).failed();
      }
      return context.result().message("Successfully processed conversion job.").succeeded();
    } catch (LoginException e) {
      context.log("Unable to log in using service user: {}", e.getLocalizedMessage());
      logger.error("Unable to log in using service user to perform conversion", e);
      return context.result().message("Unable to log in using service user.").failed();
    } catch (RewriteException | PersistenceException e) {
      context.log("Error when trying to update the requested content.", e.getLocalizedMessage());
      logger.error("Unable to make changes to repository.", e);
      resourceResolver.revert();
      return context.result().message("Error when trying to save the changes.").failed();
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
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

  private void updateTracking(Job job, ResourceResolver resourceResolver) throws PersistenceException {
    String trackingPath = job.getProperty(PN_TRACKING_PATH, String.class);
    ModifiableValueMap mvm = resourceResolver.getResource(trackingPath).adaptTo(ModifiableValueMap.class);
    mvm.put(PN_JOB_ID, job.getId());
    resourceResolver.commit();
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


  /*
    Log into the repository with the Service user.
   */
  private ResourceResolver login() throws LoginException {
    return resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME));
  }

}
