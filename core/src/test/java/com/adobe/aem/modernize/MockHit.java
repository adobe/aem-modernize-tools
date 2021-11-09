package com.adobe.aem.modernize;

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

import java.util.Map;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import com.day.cq.search.result.Hit;

public class MockHit implements Hit {

  private final Resource resource;

  public MockHit(Resource resource) {
    this.resource = resource;
  }

  @Override
  public Resource getResource() throws RepositoryException {
    return this.resource;
  }

  @Override
  public Node getNode() throws RepositoryException {
    return this.resource.adaptTo(Node.class);
  }

  @Override
  public String getPath() throws RepositoryException {
    return this.resource.getPath();
  }

  @Override
  public ValueMap getProperties() throws RepositoryException {
    return this.resource.getValueMap();
  }

  @Override
  public long getIndex() {
    throw new UnsupportedOperationException("Unsupported");
  }

  @Override
  public Map<String, String> getExcerpts() throws RepositoryException {
    throw new UnsupportedOperationException("Unsupported");
  }

  @Override
  public String getExcerpt() throws RepositoryException {
    throw new UnsupportedOperationException("Unsupported");
  }

  @Override
  public String getTitle() throws RepositoryException {
    throw new UnsupportedOperationException("Unsupported");
  }

  @Override
  public double getScore() throws RepositoryException {
    throw new UnsupportedOperationException("Unsupported");
  }
}
