package com.adobe.aem.modernize.component.job;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.component.job.ComponentConversionJobExecutor.*;
import static com.adobe.aem.modernize.model.ConversionJobItem.*;

@Component(
    service = { JobExecutor.class },
    properties = {
        JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC
    }
)
public class ComponentConversionJobExecutor extends AbstractConversionJobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/component";

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(Job job, JobExecutionContext context, ResourceResolver resourceResolver) throws RewriteException, PersistenceException {
    String[] paths = job.getProperty(PN_PATHS, String[].class);
    context.initProgress(paths.length, -1);
    final String[] rules = job.getProperty(PN_COMPONENT_RULES, String[].class);
    if (rules == null || rules.length == 0) {
      context.log("No component rules found, skipping skipping component conversion.");
    } else {
      for (String path : paths) {
        Resource resource = resourceResolver.getResource(path);
        if (resource != null) {
          componentService.apply(resource, rules, false);
          context.incrementProgressCount(1);
        }
      }
    }
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }

}
