package com.adobe.aem.modernize.component.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.component.job.ComponentJobExecutor.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

@Component(
    service = { JobExecutor.class },
    property = {
        JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC
    }
)
public class ComponentJobExecutor extends AbstractConversionJobExecutor {

  public static final String JOB_TOPIC = "com/adobe/aem/modernize/job/topic/convert/component";

  @Reference
  private ComponentRewriteRuleService componentService;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull ConversionJobBucket bucket) {

    Resource resource = bucket.getResource();
    final List<String> paths = bucket.getPaths();
    context.initProgress(paths.size(), -1);

    final Set<String> rules = getComponentRules(bucket);
    if (rules.isEmpty()) {
      context.log("No component rules found, skipping skipping component conversion.");
    } else {
      ResourceResolver rr = resource.getResourceResolver();
      for (String path : paths) {
        Resource r = rr.getResource(path);
        if (r != null) {
          try {
            componentService.apply(r, rules, false);
            bucket.getSuccess().add(path);
          } catch (RewriteException e) {
            logger.error("Component conversion resulted in an error", e);
            bucket.getFailed().add(path);
          }
          context.incrementProgressCount(1);
        } else {
          bucket.getNotFound().add(path);
        }
      }
    }
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }

}
