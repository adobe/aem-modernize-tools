package com.adobe.aem.modernize.structure.job;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.impl.RewriteUtils;
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.Revision;
import com.day.cq.wcm.api.WCMException;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static com.adobe.aem.modernize.structure.job.PageStructureJobExecutor.*;

@Component(
    service = { JobExecutor.class },
    property = {
        JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC
    }
)
public class PageStructureJobExecutor extends AbstractConversionJobExecutor {
  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/structure";

  @Reference
  private StructureRewriteRuleService structureService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket) {
    boolean reprocess = isReprocess(bucket);
    String targetPath = getTargetPath(bucket);
    context.initProgress(bucket.getPaths().size(), -1);
    ResourceResolver rr = bucket.getResource().getResourceResolver();
    PageManager pm = rr.adaptTo(PageManager.class);
    for (String path : bucket.getPaths()) {

      Page page = pm.getPage(path);
      if (page == null) {
        context.log("Path [{}] was not a page, skipping.", path);
        bucket.getNotFound().add(path);
        context.incrementProgressCount(1);
        continue;
      }

      // If reprocessing, restore from the latest version
      try {
        if (reprocess) {
          page = restore(pm, page);
        }
        createVersion(pm, page);
        if (StringUtils.isNotBlank(targetPath)) {
          page = copyPage(pm, page, targetPath);
        }
        Set<String> rules = getTemplateRules(bucket);
        structureService.apply(page, rules);
        bucket.getSuccess().add(path);
      } catch (WCMException e) {
        logger.error("Error occurred while trying to manage page versions.", e);
        bucket.getFailed().add(path);
      } catch (RewriteException e) {
        logger.error("Page structure conversion resulted in an error.", e);
        bucket.getFailed().add(path);
      }
      context.incrementProgressCount(1);
    }
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }

  private Page restore(PageManager pm, Page page) throws WCMException {
    String version = page.getProperties().get(PN_PRE_MODERNIZE_VERSION, String.class);
    if (StringUtils.isNotBlank(version)) {
      page = pm.restore(page.getPath(), version);
      ModifiableValueMap mvm = page.getContentResource().adaptTo(ModifiableValueMap.class);
      mvm.put(PN_PRE_MODERNIZE_VERSION, version);
    }
    return page;
  }

  private void createVersion(PageManager pm, Page page) throws WCMException {
    String version = page.getProperties().get(PN_PRE_MODERNIZE_VERSION, String.class);
    if (StringUtils.isBlank(version)) {
      String date = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date());
      String label = String.format("%s - %s", VERSION_LABEL, date);
      Revision revision = pm.createRevision(page, label, VERSION_DESC);
      ModifiableValueMap mvm = page.getContentResource().adaptTo(ModifiableValueMap.class);
      mvm.put(PN_PRE_MODERNIZE_VERSION, revision.getId());
    }
  }

  private Page copyPage(PageManager pm, Page source, String targetRoot) throws WCMException, RewriteException {
    String target = RewriteUtils.calcNewPath(source.getPath(), targetRoot);
    Page page = pm.getPage(target);
    if (page != null) {
      throw new RewriteException(String.format("Target page already exists for requested copy: {}", target));
    }
    return pm.copy(source, target, null, true, false, false); // Copy but only this page, fail if in conflict, don't save.
  }

}
