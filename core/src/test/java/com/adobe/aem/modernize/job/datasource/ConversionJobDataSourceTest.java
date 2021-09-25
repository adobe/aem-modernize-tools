package com.adobe.aem.modernize.job.datasource;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockHit;
import com.adobe.aem.modernize.model.ComponentJob;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ConversionJobDataSourceTest {

  private static final String componentViewsPath = "/apps/aem-modernize/job/component/views";
  private static String jobDataPath;
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  public ConversionJobDataSource dataSource = new ConversionJobDataSource();

  @Mocked
  private ExpressionResolver expressionResolver;

  @Mocked
  private QueryBuilder queryBuilder;

  @Mocked
  private SearchResult searchResult;

  @BeforeAll
  public static void beforeAll() {
    Calendar today = Calendar.getInstance();
    jobDataPath = String.format("%s/%d/%d/%d",
        ComponentJob.JOB_DATA_LOCATION,
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH));
  }

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/datasource/test-component-job.json", jobDataPath);
    context.load().json("/job/datasource/test-component-view.json", componentViewsPath);
    context.registerService(ExpressionResolver.class, expressionResolver);
    context.registerService(QueryBuilder.class, queryBuilder);

    context.registerInjectActivateService(dataSource);
  }

  @Test
  public void testNoPath() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/missingPath");
    request.setResource(resource);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public void testNoItemResourceType() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/missingItemResourceType");
    request.setResource(resource);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public void testUnresolvedSearchRoot() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/pathNotResolved");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public <R extends ResourceResolver> void testFindJobsQueryFails(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/card");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${requestPathInfo.selectors[0]}", Locale.ENGLISH, Integer.class, request);
      result = 0;
      expressionResolver.resolve("${empty requestPathInfo.selectors[1] ? &quot;10&quot; : requestPathInfo.selectors[1]}", Locale.ENGLISH, Integer.class, request);
      result = 10;
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = new RepositoryException("Error");
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public void testNoMatches(@Mocked Query query,
                            @Mocked SearchResult searchResult
  ) throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/card");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${requestPathInfo.selectors[0]}", Locale.ENGLISH, Integer.class, request);
      result = 0;
      expressionResolver.resolve("${empty requestPathInfo.selectors[1] ? &quot;10&quot; : requestPathInfo.selectors[1]}", Locale.ENGLISH, Integer.class, request);
      result = 10;
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      query.getResult();
      result = searchResult;
      searchResult.getHits();
      result = Collections.emptyList();
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
  }

  @Test
  public <R extends ResourceResolver> void testFindJobs(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) throws Exception {

    final boolean[] closeCalled = { false };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0] = true;
      }
    };

    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/card");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);

    new Expectations() {{
      expressionResolver.resolve("${requestPathInfo.selectors[0]}", Locale.ENGLISH, Integer.class, request);
      result = 0;
      expressionResolver.resolve("${empty requestPathInfo.selectors[1] ? &quot;10&quot; : requestPathInfo.selectors[1]}", Locale.ENGLISH, Integer.class, request);
      result = 10;
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = buildHits();
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    Iterator<Resource> it = ds.iterator();
    assertEquals("geodemo-components0", it.next().getName(), "Resource hit included.");
    assertEquals("geodemo-components1", it.next().getName(), "Resource hit included.");
    assertEquals("geodemo-components2", it.next().getName(), "Resource hit included.");
    assertEquals("geodemo-components3", it.next().getName(), "Resource hit included.");

    assertTrue(closeCalled[0], "Query RR was closed");

  }

  private List<Hit> buildHits() {
    List<Hit> hits = new ArrayList<>();
    ResourceResolver rr = context.resourceResolver();
    hits.add(new MockHit(rr.getResource(jobDataPath + "/geodemo-components0")));
    hits.add(new MockHit(rr.getResource(jobDataPath + "/geodemo-components1")));
    hits.add(new MockHit(rr.getResource(jobDataPath + "/geodemo-components2")));
    hits.add(new MockHit(rr.getResource(jobDataPath + "/geodemo-components3")));
    return hits;
  }
}
