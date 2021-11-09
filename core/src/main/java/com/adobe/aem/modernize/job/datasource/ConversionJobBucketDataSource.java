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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

import com.adobe.aem.modernize.model.ConversionJob;
import com.adobe.aem.modernize.model.ConversionJobBucket;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/components/job/bucket/datasource",
        SLING_SERVLET_METHODS + "=GET"
    }
)
public class ConversionJobBucketDataSource extends SlingSafeMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(ConversionJobBucketDataSource.class);
  private static final String ITEM_RESOURCE_TYPE = "aem-modernize/components/job/bucket/item";

  @Reference
  private ExpressionResolver expressionResolver;

  @Override
  protected void doGet(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
    ResourceResolver rr = request.getResourceResolver();
    Resource resource = request.getResource();
    ValueMap vm = resource.getValueMap();
    String path = vm.get("path", String.class);
    if (StringUtils.isBlank(path)) {
      logger.warn("Job Detail path was not specified.");
      return;
    }
    path = expressionResolver.resolve(path, request.getLocale(), String.class, request);
    Resource jobResource = rr.getResource(path);
    if (jobResource == null) {
      logger.warn("Job Detail path was not found.");
      return;
    }
    ConversionJob job = jobResource.adaptTo(ConversionJob.class);
    if (job == null) {
      logger.warn("Path was not a valid Job.");
      return;
    }

    Integer bucketIdx = expressionResolver.resolve(vm.get("bucket", String.class), request.getLocale(), Integer.class, request);
    ConversionJobBucket bucket = job.getBuckets().get(bucketIdx);


    ValueMap dvm = resource.getChild("datasource").getValueMap();
    Integer offset = expressionResolver.resolve(dvm.get("offset", String.class), request.getLocale(), Integer.class, request);
    Integer limit = expressionResolver.resolve(dvm.get("limit", String.class), request.getLocale(), Integer.class, request);
    DataSource ds = buildDataSource(rr, bucket, offset, limit);
    request.setAttribute(DataSource.class.getName(), ds);
  }

  private DataSource buildDataSource(final ResourceResolver rr, final ConversionJobBucket bucket, int offset, int limit) {
    List<Resource> entries = bucket.getPaths().stream().skip(offset).limit(limit).map(p -> {
      Map<String, Object> vm = new HashMap<>();
      vm.put("path", p);
      if (bucket.getFailed().contains(p)) {
        vm.put("status", "Failed");
        vm.put("statusClass", "error");
        vm.put("icon", "closeCircle");
      } else if (bucket.getNotFound().contains(p)) {
        vm.put("status", "Not Found");
        vm.put("statusClass", "warn");
        vm.put("icon", "alert");
      } else if (bucket.getSuccess().contains(p)) {
        vm.put("status", "Success");
        vm.put("statusClass", "success");
        vm.put("icon", "checkCircle");
      } else {
        vm.put("status", "Unknown");
        vm.put("statusClass", "unknown");
        vm.put("icon", "helpCircle");

      }
      return new ValueMapResource(rr, p, ITEM_RESOURCE_TYPE, new ValueMapDecorator(vm));
    }).collect(Collectors.toList());
    return new SimpleDataSource(entries.iterator());
  }
}
