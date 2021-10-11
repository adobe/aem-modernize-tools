package com.adobe.aem.modernize.job.datasource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.day.cq.search.Predicate;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.eval.JcrPropertyPredicateEvaluator;
import com.day.cq.search.eval.PathPredicateEvaluator;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;
import static org.apache.sling.event.jobs.Job.*;

/**
 * Returns a list of component job data stored in the `/var` directory.
 */
@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/components/job/datasource",
        SLING_SERVLET_METHODS + "=GET"
    }
)
public class ConversionJobDataSource extends SlingSafeMethodsServlet {

  private static final List<JobState> priority = Arrays.asList(
      JobState.ERROR,
      JobState.DROPPED,
      JobState.GIVEN_UP,
      JobState.STOPPED,
      JobState.ACTIVE,
      JobState.QUEUED,
      JobState.SUCCEEDED
  );

  private final static Logger logger = LoggerFactory.getLogger(ConversionJobDataSource.class);
  @Reference
  private ExpressionResolver expressionResolver;
  @Reference
  private QueryBuilder queryBuilder;
  @Reference
  private JobManager jobManager;

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    ResourceResolver rr = request.getResourceResolver();
    Resource resource = request.getResource();
    ValueMap vm = resource.getValueMap();
    String path = vm.get("path", String.class);
    if (StringUtils.isBlank(path)) {
      logger.warn("Job data search path was not specified.");
      return;
    }

    Resource searchRoot = rr.getResource(path);
    if (searchRoot == null) {
      logger.warn("Search path [{}] for component datasource was not found.", path);
      return;
    }

    String resourceType = vm.get("itemResourceType", String.class);
    if (StringUtils.isBlank(resourceType)) {
      logger.warn("Job data item resource type was not specified.");
      return;
    }

    ValueMap dvm = resource.getChild("datasource").getValueMap();
    String jobType = dvm.get("jobType", String.class);
    if (StringUtils.isBlank(jobType)) {
      logger.warn("DataSource job resource type was not specified.");
      return;
    }

    Integer offset = expressionResolver.resolve(dvm.get("offset", String.class), request.getLocale(), Integer.class, request);
    Integer limit = expressionResolver.resolve(dvm.get("limit", String.class), request.getLocale(), Integer.class, request);
    List<Resource> jobs = findJobs(rr, searchRoot, jobType, offset, limit);

    if (!jobs.isEmpty()) {
      DataSource ds = buildDataSource(request.getResourceResolver(), jobs, resourceType);
      request.setAttribute(DataSource.class.getName(), ds);
    }
  }

  private List<Resource> findJobs(ResourceResolver resourceResolver, Resource root, String type, Integer offset, Integer limit) {
    List<Resource> results = new ArrayList<>();
    PredicateGroup predicates = new PredicateGroup();

    // Path
    Predicate predicate = new Predicate(PathPredicateEvaluator.PATH);
    predicate.set(PathPredicateEvaluator.PATH, root.getPath());
    predicates.add(predicate);

    // Resource Type
    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, ResourceResolver.PROPERTY_RESOURCE_TYPE);
    predicate.set(JcrPropertyPredicateEvaluator.VALUE, type);
    predicate.set(JcrPropertyPredicateEvaluator.OPERATION, JcrPropertyPredicateEvaluator.OP_EQUALS);
    predicates.add(predicate);

    predicate = new Predicate(Predicate.ORDER_BY);
    predicate.set(Predicate.ORDER_BY, PathPredicateEvaluator.PATH);
    predicate.set(Predicate.PARAM_SORT, Predicate.SORT_DESCENDING);
    predicates.add(predicate);

    Session session = resourceResolver.adaptTo(Session.class);
    Query query = queryBuilder.createQuery(predicates, session);
    query.setStart(offset);
    query.setHitsPerPage(limit);
    SearchResult result = query.getResult();

    ResourceResolver qrr = null;
    try {
      for (final Hit hit : result.getHits()) {
        if (qrr == null) {
          qrr = hit.getResource().getResourceResolver();
        }
        // Reload hit from requests' resource resolver, as Query RR will be closed.
        results.add(resourceResolver.getResource(hit.getPath()));
      }
    } catch (RepositoryException e) {
      logger.error("Encountered an error while trying to search for conversion jobs.", e);
    } finally {
      if (qrr != null) {
        qrr.close();
      }
    }
    return results;
  }

  private DataSource buildDataSource(ResourceResolver resourceResolver, List<Resource> jobs, final String resourceType) {
    List<Resource> entries = jobs.stream().map(r -> {
      ConversionJob cj = r.adaptTo(ConversionJob.class);
      Map<String, Object> vm = new HashMap<>();
      vm.put("multi", cj.getBuckets().size() > 1);
      vm.put("title", cj.getTitle());

      Job job = getJob(cj);

      if (job != null) {
        switch (job.getJobState()) {
          case ERROR:
            vm.put("statusClass", "error");
            vm.put("icon", "closeCircle");
            break;
          case QUEUED:
            vm.put("statusClass", "info");
            vm.put("icon", "clock");
            break;
          case DROPPED:
          case STOPPED:
          case GIVEN_UP:
            vm.put("statusClass", "warn");
            vm.put("icon", "alert");
            break;
          case ACTIVE:
            vm.put("statusClass", "info");
            vm.put("icon", "clockCheck");
            break;
          case SUCCEEDED:
            vm.put("statusClass", "success");
            vm.put("icon", "checkCircle");
        }
        vm.put("status", job.getJobState().toString());
        vm.put("statusInt", priority.indexOf(job.getJobState()));
        Calendar finished = job.getFinishedDate();
        if (finished != null) {
          vm.put("finishedMs", finished.getTimeInMillis());
          vm.put("finished", finished.toInstant());
        } else {
          vm.put("finishedMs", 0);
          vm.put("finished", "");
        }
      } else {
        vm.put("status", "unknown");
        vm.put("icon", "helpCircle");
        vm.put("statusClass", "info");
        vm.put("finishedMs", 0);
        vm.put("finished", "");
      }

      return new ValueMapResource(resourceResolver, r.getPath(), resourceType, new ValueMapDecorator(vm));
    }).collect(Collectors.toList());
    return new SimpleDataSource(entries.iterator());
  }

  private Job getJob(ConversionJob conversionJob) {
    Job job = null;
    for (ConversionJobBucket bucket : conversionJob.getBuckets()) {
      String id = bucket.getJobId();
      Job j = jobManager.getJobById(id);

      if (j != null) {
        if (job == null) {
          job = j;
        } else if (priority.indexOf(j.getJobState()) < priority.indexOf(job.getJobState())) {
          job = j;
        }
      }
    }
    return job;
  }
}
