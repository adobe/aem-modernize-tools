package com.adobe.aem.modernize.component.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import static com.adobe.aem.modernize.component.job.ComponentJobExecutor.*;
import static com.adobe.aem.modernize.model.ConversionJob.*;
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
  protected void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull Resource tracking) {

    final ModifiableValueMap mvm = tracking.adaptTo(ModifiableValueMap.class);
    final String[] paths = mvm.get(PN_PATHS, String[].class);
    context.initProgress(paths.length, -1);

    final List<String> successes = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    final List<String> notFound = new ArrayList<>();
    final Set<String> rules = getRules(tracking);
    if (rules == null || rules.size() == 0) {
      context.log("No component rules found, skipping skipping component conversion.");
    } else {
      ResourceResolver rr = tracking.getResourceResolver();
      for (String path : paths) {
        Resource resource = rr.getResource(path);
        if (resource != null) {
          try {
            componentService.apply(resource, rules, false);
            successes.add(path);
          } catch (RewriteException e) {
            logger.error("Component conversion resulted in an error", e);
            failures.add(path);
          }
          context.incrementProgressCount(1);
        } else {
          notFound.add(path);
        }
      }
      mvm.put(PN_SUCCESS, successes.toArray(new String[] {}));
      mvm.put(PN_FAILED, failures.toArray(new String[] {}));
      mvm.put(PN_NOT_FOUND, notFound.toArray(new String[] {}));
    }
  }

  private Set<String> getRules(Resource tracking) {
    Set<String> rules = Collections.emptySet();
    Resource parent = tracking.getParent();
    if (parent != null) {
      parent = parent.getParent();
    }
    if (parent != null) {
      String[] ruleList = parent.getValueMap().get(PN_COMPONENT_RULES, String[].class);
      if (ruleList != null) {
        rules = Arrays.stream(ruleList).collect(Collectors.toSet());
      }
    }
    return rules;
  }

  @Override
  protected ResourceResolverFactory getResourceResolverFactory() {
    return resourceResolverFactory;
  }

}
