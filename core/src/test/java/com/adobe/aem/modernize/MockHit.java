package com.adobe.aem.modernize;

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
