package com.adobe.aem.modernize.job.datasource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
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
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/components/convert/job/datasource",
        SLING_SERVLET_METHODS + "=GET"
    }
)
public class ConversionJobDataSource extends SlingSafeMethodsServlet {

  private final static Logger logger = LoggerFactory.getLogger(ConversionJobDataSource.class);
  @Reference
  private ExpressionResolver expressionResolver;
  @Reference
  private QueryBuilder queryBuilder;

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

    String type = vm.get("itemResourceType", String.class);
    if (StringUtils.isBlank(type)) {
      logger.warn("Job data item resource type was not specified.");
      return;
    }

    ValueMap dvm = resource.getChild("datasource").getValueMap();
    Integer offset = expressionResolver.resolve(dvm.get("offset", String.class), request.getLocale(), Integer.class, request);
    Integer limit = expressionResolver.resolve(dvm.get("limit", String.class), request.getLocale(), Integer.class, request);
    List<Resource> jobs = findJobs(rr, searchRoot, type, offset, limit);

    if (!jobs.isEmpty()) {
      DataSource ds = new SimpleDataSource(jobs.iterator());
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
    predicate.set(Predicate.ORDER_BY, "@" + JcrConstants.JCR_CREATED);
    predicate.set(Predicate.PARAM_SORT, Predicate.SORT_DESCENDING);
    predicates.add(predicate);

    Session session = resourceResolver.adaptTo(Session.class);
    Query query = queryBuilder.createQuery(predicates, session);
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
}
