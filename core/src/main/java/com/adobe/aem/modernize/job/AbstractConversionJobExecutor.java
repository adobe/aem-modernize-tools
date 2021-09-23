package com.adobe.aem.modernize.job;

import java.util.Collections;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;

import com.adobe.aem.modernize.RewriteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.model.ConversionJobItem.*;

public abstract class AbstractConversionJobExecutor implements JobExecutor {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String SERVICE_NAME = "convert-content";

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext context) {
    String[] paths = job.getProperty(PN_PATHS, String[].class);
    if (paths == null || paths.length == 0) {
      return context.result().message("Invalid job state, no paths specified to process.").failed();
    }
    ResourceResolver resourceResolver = null;
    try {
      resourceResolver = login();
      updateTracking(job, resourceResolver);
      doProcess(job, context, resourceResolver);
      resourceResolver.commit();
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

  /*
    Log into the repository with the Service user.
   */
  private ResourceResolver login() throws LoginException {
    return getResourceResolverFactory().getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME));
  }

  private void updateTracking(Job job, ResourceResolver resourceResolver) throws PersistenceException {
    String trackingPath = job.getProperty(PN_TRACKING_PATH, String.class);
    ModifiableValueMap mvm = resourceResolver.getResource(trackingPath).adaptTo(ModifiableValueMap.class);
    mvm.put(PN_JOB_ID, job.getId());
    resourceResolver.commit();
  }


  protected abstract void doProcess(Job job, JobExecutionContext context, ResourceResolver resourceResolver) throws RewriteException, PersistenceException;

  protected abstract ResourceResolverFactory getResourceResolverFactory();
}
