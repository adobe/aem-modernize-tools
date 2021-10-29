package com.adobe.aem.modernize.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.policy.PolicyImportRuleService;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.WCMException;
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
  private PolicyImportRuleService policyService;

  @Reference
  private StructureRewriteRuleService structureService;

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(Job job, JobExecutionContext context, ConversionJobBucket bucket) {
    final boolean reprocess = job.getProperty(PN_REPROCESS, false);
    Resource resource = bucket.getResource();
    ResourceResolver rr = resource.getResourceResolver();

    Set<String> templateRules = getTemplateRules(bucket);
    Set<String> policyRules = getPolicyRules(bucket);
    String dest = getTargetConfPath(bucket);
    Set<String> componentRules = getComponentRules(bucket);

    ModifiableValueMap mvm = resource.adaptTo(ModifiableValueMap.class);
    String[] paths = mvm.get(PN_PATHS, String[].class);

    context.initProgress(paths.length * 2, -1);

    List<String> preparedPaths = preparePages(context, bucket, reprocess);
    for (String path : preparedPaths) {
      Page root = rr.getResource(path).adaptTo(Page.class);
      try {
        if (policyRules.isEmpty() || dest == null) {
          context.log("No policy rules or target found, skipping skipping policy import.");
        } else {
          Resource src = root.getContentResource();
          policyService.apply(src, dest, policyRules, true, reprocess);
        }

        if (templateRules.isEmpty()) {
          context.log("No template rules found, skipping structure conversion.");
        } else {
          structureService.apply(root, templateRules);
        }

        if (componentRules.isEmpty()) {
          context.log("No component rules found, skipping skipping component conversion.");
        } else {
          componentService.apply(root.getContentResource(), componentRules, true);
        }
        context.incrementProgressCount(1);
      } catch (RewriteException e) {
        logger.error("Conversion resulted in an error", e);
        bucket.getFailed().add(path);
      }
    }
  }

  private List<String> preparePages(JobExecutionContext context, ConversionJobBucket bucket, boolean reprocess) {

    List<String> paths = bucket.getPaths();
    ResourceResolver rr = bucket.getResource().getResourceResolver();
    List<String> processed = new ArrayList<>(paths.size());
    PageManager pm = rr.adaptTo(PageManager.class);
    for (String path : paths) {
      Page page = pm.getPage(path);
      if (page == null) {
        context.log("Path [{}] does not resolve to a Page, removing from list.", path);
        context.incrementProgressCount(1);
        bucket.getNotFound().add(path);
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
        rr.commit();
        processed.add(path);
        context.incrementProgressCount(1);
      } catch (WCMException | PersistenceException e) {
        logger.error("Error occurred trying to create or restore a page version", e);
        context.log("Could not prepare page [{}], skipping.", page.getPath());
        bucket.getFailed().add(path);
      }
    }
    return processed;
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }
}
