package com.adobe.aem.modernize.rule.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.jackrabbit.oak.commons.PathUtils;

import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;

public class NodeBasedRewriteRule implements RewriteRule {

  public static final String NN_PATTERNS = "patterns";
  public static final String NN_AGGREGATE = "aggregate";
  public static final String NN_REPLACEMENT = "replacement";

  public static final String PROPERTY_OPTIONAL = "cq:rewriteOptional";

  private final Node rule;
  private final String id;

  /**
   * Creates a new RewriteRule referencing the specified path.
   *
   * @param node
   */
  public NodeBasedRewriteRule(@NotNull Node node) throws RepositoryException {
    this.rule = node;
    this.id = node.getPath();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean matches(Node root) throws RepositoryException {

    // Need some kind of replacement, or it is an invalid rule.
    if (!rule.hasNode(NN_REPLACEMENT)) {
      return false;
    }
    Node replacement = rule.getNode(NN_REPLACEMENT);
    if (!replacement.hasNodes()) {
      return false;
    }

    if (rule.hasNode(NN_PATTERNS)) {
      return matchesPattern(root);
    }

    if (rule.hasNode(NN_AGGREGATE)) {
      return matchesAggregate(root);
    }
    return false;
  }

  @Override
  public Node applyTo(Node root, Set<Node> finalNodes) throws RepositoryException {
    return null;
  }

  /*
    Matches the node against a simple pattern
   */
  private boolean matchesPattern(Node root) throws RepositoryException {
    NodeIterator patterns = rule.getNode(NN_PATTERNS).getNodes();
    while (patterns.hasNext()) {
      Node pattern = patterns.nextNode();
      if (matches(root, pattern)) {
        return true;
      }
    }
    return false;
  }

  /*
    Matches the node & siblings against a set of aggregated patterns
   */
  private boolean matchesAggregate(Node root) throws RepositoryException {
    Node aggregate = rule.getNode(NN_AGGREGATE);

    // No pattern node
    if (!aggregate.hasNode(NN_PATTERNS)) {
      return false;
    }

    // No patterns
    if (!aggregate.getNode(NN_PATTERNS).getNodes().hasNext()) {
      return false;
    }

    // Aggregate patterns must be in order.
    AggregateIterator iterator = new AggregateIterator(root, aggregate.getNode(NN_PATTERNS));
    while (iterator.hasNext()) {
      if (!matches(iterator.nextNode(), iterator.nextPattern())) {
        return false;
      }
      if (iterator.hasMatchingLength()) {
        return false;
      }
    }
    // Base case
    return true;
  }

  // Compares the node against the pattern, deep match
  private boolean matches(Node node, Node pattern) throws RepositoryException {

    // Check primary Node types
    if (!StringUtils.equals(node.getPrimaryNodeType().getName(), pattern.getPrimaryNodeType().getName())) {
      return false;

      // Check the properties
    } else if (!match(node, pattern.getProperties())) {
      return false;
    }

    // Check the children tree
    return match(node, pattern);
  }

  // Checks for required properties on Node
  private boolean match(Node node, PropertyIterator requiredProperties) throws RepositoryException {
    // check that all properties of the pattern match
    while (requiredProperties.hasNext()) {
      Property property = requiredProperties.nextProperty();
      String name = property.getName();

      // skip protected properties
      if (property.getDefinition().isProtected()) {
        continue;
      }

      // Optional rewrites don't fail matching
      if (PROPERTY_OPTIONAL.equals(name)) {
        continue;
      }

      // Missing required property fails rules
      if (!node.hasProperty(property.getName())) {
        return false;
      }

      // property values on pattern and tree differ
      if (!node.getProperty(name).getValue().equals(property.getValue())) {
        return false;
      }
    }
    return true;
  }

  // Check content tree for required children.
  private boolean match(Node node, Node patterns) throws RepositoryException {
    // check that the tree contains all children defined in the pattern (optimization measure, before
    // checking all children recursively)
    String rootPath = patterns.getPath();

    // Check each pattern's tree to verify that path exists under the node to match
    // Small optimization to skip property comparison of early nodes if later nodes are missing
    List<String> patternPaths = new ArrayList<>();
    NodeIterator patternChildren = patterns.getNodes();
    while (patternChildren.hasNext()) {
      for (Node child : new TreeTraverser(patternChildren.nextNode())) {
        // Skip optional children trees
        if (child.hasProperty(PROPERTY_OPTIONAL)) {
          continue;
        }
        // Check that the required relative path is in the node's structure
        String relativePath = PathUtils.relativize(rootPath, child.getPath());
        if (!node.hasNode(relativePath)) {
          return false;
        }
        // Add path for matching properties
        patternPaths.add(relativePath);
      }
    }

    // If we got here, the trees match at least path structure, now deep check the properties.
    for (String relativePath : patternPaths) {
      Node pattern = patterns.getNode(relativePath);
      // Check Primary Node Types
      if (!StringUtils.equals(node.getPrimaryNodeType().getName(), pattern.getPrimaryNodeType().getName())) {
        return false;

        // Check that the required relative path is in the node's structure
      } else if (!match(node.getNode(relativePath), pattern.getProperties())) {
        return false;
      }
    }
    return true;
  }

  private static class AggregateIterator implements NodeIterator {

    private final NodeIterator nodeIterator;
    private final NodeIterator patternIterator;

    /**
     * Iterator for keeping node and patterns in sync for comparisons.
     *
     * @param start    the start of the aggregate pattern
     * @param patterns the patterns to match
     */
    protected AggregateIterator(Node start, Node patterns) throws RepositoryException {
      int skip = 0;
      NodeIterator siblings = start.getParent().getNodes();
      boolean found = false;
      while (!found && siblings.hasNext()) {
        skip++;
        found = siblings.nextNode().getName().equals(start.getName());
      }

      this.nodeIterator = start.getParent().getNodes();
      this.nodeIterator.skip(skip - 1);
      this.patternIterator = patterns.getNodes();
    }

    public Node nextPattern() {
      return patternIterator.nextNode();
    }

    @Override
    public Node nextNode() {
      return nodeIterator.nextNode();
    }

    @Override
    public void skip(long skipNum) {
      throw new UnsupportedOperationException("Skip is not supported.");
    }

    @Override
    public long getSize() {
      return Math.min(nodeIterator.getSize(), patternIterator.getSize());
    }

    @Override
    public long getPosition() {
      throw new UnsupportedOperationException("Position is not supported.");
    }

    @Override
    public boolean hasNext() {
      return nodeIterator.hasNext() && patternIterator.hasNext();
    }

    @Override
    public Object next() {
      throw new UnsupportedOperationException("Next is not supported.");
    }

    // Check if there are more patterns, if so, but no more nodes, does not match
    public boolean hasMatchingLength() {
      return !patternIterator.hasNext() || nodeIterator.hasNext();
    }
  }
}
