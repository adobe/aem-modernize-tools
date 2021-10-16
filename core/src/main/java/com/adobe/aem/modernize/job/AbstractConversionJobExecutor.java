package com.adobe.aem.modernize.job;

import java.util.Collections;

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
import org.eclipse.jetty.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;

public abstract class AbstractConversionJobExecutor implements JobExecutor {

  public static final String PN_TRACKING_PATH = "tracking";
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String SERVICE_NAME = "convert-content";

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext context) {
    String trackingPath = job.getProperty(PN_TRACKING_PATH, String.class);
    if (StringUtil.isBlank(trackingPath)) {
      return context.result().message("Invalid job state, no paths specified to process.").cancelled();
    }
    ResourceResolver resourceResolver = null;
    try {
      resourceResolver = login();
      Resource tracking = resourceResolver.getResource(trackingPath);
      if (tracking == null) {
        context.log("No tracking path found for required job details.");
        logger.warn("No tracking path found for required job details.");
        return context.result().message("No tracking path found for required job details.").cancelled();
      }
      updateTracking(job, tracking);
      doProcess(job, context, tracking);
      resourceResolver.commit();
      return context.result().message("Successfully processed conversion job.").succeeded();
    } catch (LoginException e) {
      context.log("Unable to log in using service user: {}", e.getLocalizedMessage());
      logger.error("Unable to log in using service user to perform conversion", e);
      return context.result().message("Unable to log in using service user.").cancelled();
    } catch (RewriteException | PersistenceException e) {
      context.log("Error when trying to update the requested content.", e.getLocalizedMessage());
      logger.error("Unable to make changes to repository.", e);
      resourceResolver.revert();
      return context.result().message("Error when trying to save the changes.").cancelled();
    } finally {
      if (resourceResolver != null && resourceResolver.isLive()) {
        resourceResolver.close();
      }
    }
  }

  /*
    Log into the repository with the Service user.
   */
  private ResourceResolver login() throws LoginException {
    return getResourceResolverFactory().getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME));
  }

  /*
    Set this JobID onto the job/bucket details node.
   */
  private void updateTracking(Job job, Resource tracking) throws PersistenceException {
    ModifiableValueMap mvm = tracking.adaptTo(ModifiableValueMap.class);
    mvm.put(PN_JOB_ID, job.getId());
    tracking.getResourceResolver().commit();;
  }


  protected abstract void doProcess(@NotNull Job job, @NotNull JobExecutionContext context, @NotNull Resource tracking) throws RewriteException, PersistenceException;

  protected abstract ResourceResolverFactory getResourceResolverFactory();
}
