package com.adobe.aem.modernize.job.datasource;

import java.io.IOException;
import java.util.ArrayList;
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
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

import com.adobe.aem.modernize.model.ConversionJob;
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

  private static final Logger logger = LoggerFactory.getLogger(ConversionJobDataSource.class);
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

    Integer offset = expressionResolver.resolve(dvm.get("offset", String.class), request.getLocale(), Integer.class, request);
    Integer limit = expressionResolver.resolve(dvm.get("limit", String.class), request.getLocale(), Integer.class, request);
    List<Resource> jobs = findJobs(rr, searchRoot, offset, limit);

    if (!jobs.isEmpty()) {
      DataSource ds = buildDataSource(rr, jobs, resourceType);
      request.setAttribute(DataSource.class.getName(), ds);
    }
  }

  private List<Resource> findJobs(ResourceResolver resourceResolver, Resource root, Integer offset, Integer limit) {
    List<Resource> results = new ArrayList<>();
    PredicateGroup predicates = new PredicateGroup();

    // Path
    Predicate predicate = new Predicate(PathPredicateEvaluator.PATH);
    predicate.set(PathPredicateEvaluator.PATH, root.getPath());
    predicates.add(predicate);

    // Resource Type
    predicate = new Predicate(JcrPropertyPredicateEvaluator.PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.PROPERTY, JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY);
    predicate.set(JcrPropertyPredicateEvaluator.VALUE, ConversionJob.RESOURCE_TYPE);
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
      vm.put("path", r.getPath());

      ConversionJob.Status status = cj.getStatus();
      switch (status) {
        case FAILED:
          vm.put("statusClass", "error");
          vm.put("icon", "closeCircle");
          break;
        case WAITING:
          vm.put("statusClass", "info");
          vm.put("icon", "clock");
          break;
        case ACTIVE:
          vm.put("statusClass", "info");
          vm.put("icon", "clockCheck");
          break;
        case SUCCESS:
          vm.put("statusClass", "success");
          vm.put("icon", "checkCircle");
          break;
        case WARN:
          vm.put("statusClass", "warn");
          vm.put("icon", "alert");
          break;
        default:
          vm.put("statusClass", "info");
          vm.put("icon", "helpCircle");
      }
      vm.put("status", status.name());
      vm.put("statusInt", status.ordinal());
      Calendar finished =  cj.getFinished();
      if (finished != null) {
        vm.put("finishedMs", finished.getTimeInMillis());
        vm.put("finished", finished.toInstant());
      } else {
        vm.put("finishedMs", 0);
        vm.put("finished", "");
      }
      return new ValueMapResource(resourceResolver, r.getPath(), resourceType, new ValueMapDecorator(vm));
    }).collect(Collectors.toList());
    return new SimpleDataSource(entries.iterator());
  }


}
