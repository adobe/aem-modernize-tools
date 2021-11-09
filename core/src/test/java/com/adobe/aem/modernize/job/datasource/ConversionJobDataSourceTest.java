package com.adobe.aem.modernize.job.datasource;

/*-
 * #%L
 * AEM Modernize Tools - Core
 * %%
 * Copyright (C) 2019 - 2021 Adobe Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;

import com.adobe.aem.modernize.MockHit;
import com.adobe.aem.modernize.model.ConversionJob;
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

  @Mocked
  private JobManager jobManager;

  @Mocked
  private Job success;

  @Mocked
  private Job active;

  @Mocked
  private Job queued;

  @Mocked
  private Job error;


  @BeforeAll
  public static void beforeAll() {
    Calendar today = Calendar.getInstance();
    jobDataPath = String.format("%s/%s/%d/%d/%d",
        ConversionJob.JOB_DATA_LOCATION,
        ConversionJob.Type.FULL.name().toLowerCase(),
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH));
  }

  @BeforeEach
  public void beforeEach() {
    context.load().json("/job/datasource/test-component-jobs.json", jobDataPath);
    context.load().json("/job/datasource/test-job-list-view.json", componentViewsPath);
    context.registerService(JobManager.class, jobManager);
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
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
  }


  @Test
  public void testNoJobResourceType() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/missingJobResourceType");
    request.setResource(resource);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
  }

  @Test
  public void testNoItemResourceType() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/missingItemResourceType");
    request.setResource(resource);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
  }

  @Test
  public void testUnresolvedSearchRoot() throws Exception {
    MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
    Resource resource = context.resourceResolver().getResource(componentViewsPath + "/pathNotResolved");
    request.setResource(resource);
    request.setLocale(Locale.ENGLISH);
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
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
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
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
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    assertNull(ds, "DataSource not set");
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

      active.getJobState();
      result = Job.JobState.ACTIVE;
      error.getJobState();
      result = Job.JobState.ERROR;
      queued.getJobState();
      result = Job.JobState.QUEUED;
      success.getJobState();
      result = Job.JobState.SUCCEEDED;

      jobManager.getJobById("1");
      result = queued;
      jobManager.getJobById("2");
      result = success;
      jobManager.getJobById("3");
      result = error;
      jobManager.getJobById("4");
      result = active;
      jobManager.getJobById("5");
      result = null;
    }};
    dataSource.doGet(request, new MockSlingHttpServletResponse());
    DataSource ds = (DataSource) request.getAttribute(DataSource.class.getName());
    Iterator<Resource> it = ds.iterator();
    ValueMap vm = it.next().adaptTo(ValueMap.class);
    assertEquals(jobDataPath + "/geodemo-components0", vm.get("path"), "Path set");
    assertEquals("GeoDemo Components", vm.get("title", String.class), "Title value.");
    assertFalse(vm.get("multi", Boolean.class), "Multi value");
    assertEquals("WAITING", vm.get("status", String.class), "Status value");
    assertEquals("info", vm.get("statusClass", String.class), "Status value");
    assertEquals("clock", vm.get("icon", String.class), "Status value");

    vm = it.next().adaptTo(ValueMap.class);
    assertEquals(jobDataPath + "/geodemo-components1", vm.get("path"), "Path set");
    assertEquals("GeoDemo Components", vm.get("title", String.class), "Title value.");
    assertTrue(vm.get("multi", Boolean.class), "Multi value");
    assertEquals("FAILED", vm.get("status", String.class), "Status value");
    assertEquals("error", vm.get("statusClass", String.class), "Status value");
    assertEquals("closeCircle", vm.get("icon", String.class), "Status value");

    vm = it.next().adaptTo(ValueMap.class);
    assertEquals(jobDataPath + "/geodemo-components2", vm.get("path"), "Path set");
    assertEquals("GeoDemo Components", vm.get("title", String.class), "Title value.");
    assertFalse(vm.get("multi", Boolean.class), "Multi value");
    assertEquals("ACTIVE", vm.get("status", String.class), "Status value");
    assertEquals("info", vm.get("statusClass", String.class), "Status value");
    assertEquals("clockCheck", vm.get("icon", String.class), "Status value");


    vm = it.next().adaptTo(ValueMap.class);
    assertEquals(jobDataPath + "/geodemo-components3", vm.get("path"), "Path set");
    assertEquals("GeoDemo Components", vm.get("title", String.class), "Title value.");
    assertFalse(vm.get("multi", Boolean.class), "Multi value");
    assertEquals("FAILED", vm.get("status", String.class), "Status value");
    assertEquals("error", vm.get("statusClass", String.class), "Status value");
    assertEquals("closeCircle", vm.get("icon", String.class), "Status value");

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
