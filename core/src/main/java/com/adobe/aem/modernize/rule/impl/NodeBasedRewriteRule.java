package com.adobe.aem.modernize.rule.impl;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.jackrabbit.oak.commons.PathUtils;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.wcm.api.NameConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeBasedRewriteRule implements RewriteRule {

  public static final String NN_CQ_REWRITE_PROPERTIES = "cq:rewriteProperties";
  public static final String NN_PATTERNS = "patterns";
  public static final String NN_AGGREGATE = "aggregate";
  public static final String NN_REPLACEMENT = "replacement";
  public static final String PN_CQ_RANKING = "cq:rewriteRanking";
  public static final String PN_CQ_REWRITE_OPTIONAL = "cq:rewriteOptional";
  public static final String PN_CQ_REWRITE_FINAL = "cq:rewriteFinal";
  public static final String PN_CQ_COPY_CHILDREN = "cq:copyChildren";
  public static final String PN_CQ_ORDER_BEFORE = "cq:orderBefore";
  public static final String PN_CQ_MAP_CHILDREN = "cq:rewriteMapChildren";

  private static final Logger logger = LoggerFactory.getLogger(NodeBasedRewriteRule.class);
  private static final Pattern PATTERN_NODE_PATTERN = Pattern.compile("\\[pattern:(.*)\\]");

  // pattern that matches the regex for mapped properties: ${<path>}
  private static final Pattern MAPPED_PATTERN = Pattern.compile("^(\\!{0,1})\\$\\{('.*?'|.*?)(:(.+))?\\}$");
  protected final Node rule;
  private final String id;
  private final String title;
  private Integer ranking;
  private final boolean aggregate;

  /**
   * Creates a new RewriteRule referencing the specified path.
   *
   * @param node the node containing the rewrite data
   */
  public NodeBasedRewriteRule(@NotNull Node node) throws RepositoryException {
    this.rule = node;
    this.id = node.getPath();
    this.title = node.hasProperty(NameConstants.PN_TITLE) ? node.getProperty(NameConstants.PN_TITLE).getString() : this.id;
    this.aggregate = rule.hasNode(NN_AGGREGATE);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public int getRanking() {
    if (ranking == null) {
      try {
        if (rule.hasProperty(PN_CQ_RANKING)) {
          this.ranking = Long.valueOf( rule.getProperty(PN_CQ_RANKING).getLong()).intValue();
        } else {
          this.ranking = RewriteRule.super.getRanking();
        }
      } catch (RepositoryException e) {
        logger.warn("Caught exception while reading the {} property from rule [{}], using default", PN_CQ_RANKING, id);
        this.ranking = RewriteRule.super.getRanking();
      }
    }
    return this.ranking;
  }

  @Override
  public boolean matches(@NotNull Node root) throws RepositoryException {

    // Need some kind of replacement, or it is an invalid rule.
    if (!rule.hasNode(NN_REPLACEMENT)) {
      return false;
    }

    if (rule.hasNode(NN_PATTERNS)) {
      return matchesPattern(root);
    } else if (aggregate) {
      return matchesAggregate(root);
    }
    return false;
  }

  @Nullable
  @Override
  public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RepositoryException, RewriteException {
    // check if the 'replacement' node exists
    if (!rule.hasNode(NN_REPLACEMENT)) {
      throw new RewriteException("The replacement node was removed between matching check and request to update.");
    }

    // if the replacement node has no children, we replace the tree by the empty tree,
    // i.e. we remove the original tree
    Node replacement = rule.getNode(NN_REPLACEMENT);
    if (!replacement.hasNodes()) {
      processRemovals(root);
      return null;
    }

    // true if the replacement tree is final and all its nodes are excluded from
    // further processing by the algorithm
    boolean treeIsFinal = false;
    if (replacement.hasProperty(PN_CQ_REWRITE_FINAL)) {
      treeIsFinal = replacement.getProperty(PN_CQ_REWRITE_FINAL).getBoolean();
    }
    /**
     * Approach:
     * - rename (move) the tree to be rewritten to a temporary name
     * - copy the replacement tree to be a new child of the original tree's parent with the original's name
     * - process the copied replacement tree (mapped properties, children, etc.)
     * - at the end, remove the original tree
     */
    // move (rename) original tree
    Map<String, String> patternNodeNames = new HashMap<>();
    Node parent = root.getParent();
    Node source;

    String tmpName = JcrUtil.createValidChildName(parent, "tmp-" + System.currentTimeMillis());
    if (aggregate) {
      source = parent;
      // Used in potential aggregate use case
      patternNodeNames = createPatternNodeMapping(parent, tmpName);
    } else {
      source = root;
    }

    String originalName = root.getName();
    root.getSession().move(root.getPath(), PathUtils.concat(parent.getPath(), tmpName));

    // copy replacement to original tree under original name
    Node replacementNext = replacement.getNodes().nextNode();
    Node updated = JcrUtil.copy(replacementNext, parent, originalName, false);

    // collect mappings: (node in original tree) -> (node in replacement tree)
    // Don't want to copy during traversal, as it'd mess with iteration
    final TreeStructure mappings = new TreeStructure();
    // traverse nodes of newly copied replacement tree
    for (Node node : new TreeTraverser(updated)) {

      // Rewrite Properties is special case - store it for later when processing properties
      Node rewritePropertiesNode = null;
      if (node.hasNode(NN_CQ_REWRITE_PROPERTIES)) {
        rewritePropertiesNode = node.getNode(NN_CQ_REWRITE_PROPERTIES);
      }

      // Store this node as being final, if the tree isn't already set
      if (processProperties(source, node, rewritePropertiesNode, patternNodeNames, mappings) && !treeIsFinal) {
        finalPaths.add(node.getPath());
      }

      if (rewritePropertiesNode != null) {
        rewritePropertiesNode.remove();
      }
    }

    mappings.processCopies(source, updated);
    mappings.processOrder(updated);

    if (treeIsFinal) {
      for (Node n : new TreeTraverser(updated)) {
        finalPaths.add(n.getPath());
      }
    }

    // Remove the nodes - aggregate were consolidated to one.
    if (aggregate) {
      for (String name : patternNodeNames.values()) {
        parent.getNode(name).remove();
      }
    } else {
      root.remove();
    }

    return updated;
  }

  private void processRemovals(Node root) throws RepositoryException {
    if (aggregate) {
      // Aggregate patterns match
      NodeIterator patterns = rule.getNode(NN_AGGREGATE).getNode(NN_PATTERNS).getNodes();
      Node parent = root.getParent();
      NodeIterator siblings = parent.getNodes();
      List<String> names = new ArrayList<>();
      // Find this node in the parent's node list
      while (siblings.hasNext()) {
        Node sibling = siblings.nextNode();
        if (root.getPath().equals(sibling.getPath())) {
          patterns.next();
          names.add(sibling.getName());
          break;
        }
      }
      // Get the remaining pattern matched nodes
      while (patterns.hasNext()) {
        patterns.next();
        names.add(siblings.nextNode().getName());
      }
      for (String name : names) {
        parent.getNode(name).remove();
      }
    } else {
      root.remove();
    }
  }

  /*
    Creates a map of pattern node names to the name of the node it matched
   */
  private Map<String, String> createPatternNodeMapping(Node parent, String newName) throws RepositoryException {
    Map<String, String> mapping = new HashMap<>();
    NodeIterator patterns = rule.getNode(NN_AGGREGATE).getNode(NN_PATTERNS).getNodes();
    Node pattern = patterns.nextNode();

    NodeIterator children = parent.getNodes();
    while (children.hasNext()) {
      Node child = children.nextNode();
      if (matches(child, pattern)) {
        // First child that matched will be what gets renamed
        mapping.put(pattern.getName(), newName);
        break;
      }
    }

    while (patterns.hasNext()) {
      mapping.put(patterns.nextNode().getName(), children.nextNode().getName());
    }

    return mapping;
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
      if (!iterator.checkNext()) {
        return false;
      }
    }
    // Base case
    return true;
  }

  /*
    Compares the node against the pattern, deep match
   */
  private boolean matches(@NotNull Node node, Node pattern) throws RepositoryException {

    // Check primary Node types
    if (!StringUtils.equals(node.getPrimaryNodeType().getName(), pattern.getPrimaryNodeType().getName())) {
      return false;

      // Check the properties
    } else if (!matchProperties(node, pattern.getProperties())) {
      return false;
    }

    // Check the children tree
    return matchTree(node, pattern);
  }

  /*
    Checks for required properties on Node
   */
  private boolean matchProperties(Node node, PropertyIterator requiredProperties) throws RepositoryException {
    // check that all properties of the pattern match
    while (requiredProperties.hasNext()) {
      Property property = requiredProperties.nextProperty();
      String name = property.getName();

      // skip protected properties
      if (property.getDefinition().isProtected()) {
        continue;
      }

      // Optional rewrites don't fail matching
      if (PN_CQ_REWRITE_OPTIONAL.equals(name)) {
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

  /*
    Check content tree for required children.
   */
  private boolean matchTree(Node node, Node patterns) throws RepositoryException {
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
        if (child.hasProperty(PN_CQ_REWRITE_OPTIONAL)) {
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
      } else if (!matchProperties(node.getNode(relativePath), pattern.getProperties())) {
        return false;
      }
    }
    return true;
  }

  /*
   * Replaces the value of a mapped property with a value from the original tree.
   */
  protected Property mapProperty(Node original, Property property, Map<String, String> patternMappings) throws RepositoryException {
    if (property.getType() != PropertyType.STRING) {
      // a mapped property must be of type string
      return null;
    }

    // array containing the expressions: ${<path>}
    Value[] values;
    if (property.isMultiple()) {
      values = property.getValues();
    } else {
      values = new Value[1];
      values[0] = property.getValue();
    }

    boolean deleteProperty = false;
    for (Value value : values) {
      String reference = value.getString();

      if (aggregate) {
        Matcher patternMatcher = PATTERN_NODE_PATTERN.matcher(reference);
        while (patternMatcher.find()) {
          String name = patternMappings.get(patternMatcher.group(1));
          if (name != null) {
            reference = patternMatcher.replaceFirst(name);
            patternMatcher.reset(reference);
          }
        }
      }

      Matcher matcher = MAPPED_PATTERN.matcher(reference);
      if (matcher.matches()) {
        // this is a mapped property, we will delete it if the mapped destination property doesn't exist
        deleteProperty = true;
        String path = matcher.group(2);
        // unwrap quoted property paths
        path = StringUtils.removeStart(StringUtils.stripEnd(path, "'"), "'");
        if (original.hasProperty(path)) {
          // replace property by mapped value in the original tree
          Property originalProperty = original.getProperty(path);
          String name = property.getName();
          Node parent = property.getParent();
          property.remove();
          Property newProperty = JcrUtil.copy(originalProperty, parent, name);

          // negate boolean properties if negation character has been set
          String negate = matcher.group(1);
          if ("!".equals(negate) && originalProperty.getType() == PropertyType.BOOLEAN) {
            newProperty.setValue(!newProperty.getBoolean());
          }

          // the mapping was successful
          deleteProperty = false;
          break;
        } else {
          String defaultValue = matcher.group(4);
          if (defaultValue != null) {
            if (property.isMultiple()) {
              // the property is multiple in the replacement,
              // recreate it so we can set the property to the default
              String name = property.getName();
              Node parent = property.getParent();
              property.remove();
              parent.setProperty(name, defaultValue);
            } else {
              property.setValue(defaultValue);
            }
            deleteProperty = false;
            break;
          }
        }
      }
    }
    if (deleteProperty) {
      // mapped destination does not exist, we don't include the property in replacement tree
      property.remove();
      return null;
    }

    return property;
  }

  /*
    Process the properties for the rewritten node; returns indication if this node was considered "final"
   */
  private boolean processProperties(Node originalContent, Node node, Node rewritePropertiesNode, Map<String, String> patternMappings, TreeStructure mapping) throws RepositoryException {
    PropertyIterator propertyIterator = node.getProperties();
    boolean nodeIsFinal = false;
    while (propertyIterator.hasNext()) {
      Property property = propertyIterator.nextProperty();
      // skip protected properties
      if (property.getDefinition().isProtected()) {
        continue;
      }

      // add mapping to collection
      if (PN_CQ_MAP_CHILDREN.equals(property.getName())) {
        mapping.addNodeMapping(node.getParent().getPath(), property.getString(), node.getName());
        // remove property, as we don't want it to be part of the result
        property.remove();
        continue;
      }

      // Add the mapping order
      if (PN_CQ_ORDER_BEFORE.equals(property.getName())) {
        mapping.addOrder(node.getPath(), property.getString());
        // remove order, as we don't want it to be part of the result
        property.remove();
        continue;
      }

      // Set up the status of this node being final
      if (PN_CQ_REWRITE_FINAL.equals(property.getName())) {
        nodeIsFinal = true;
        property.remove();
        continue;
      }

      // Set the flag if we are supposed to copy the children node.
      if (PN_CQ_COPY_CHILDREN.equals(property.getName())) {
        // Store path relative to start of traversal for copying nodes.
        mapping.addCopyChildrenPath(node.getPath());
        property.remove();
        continue;
      }
      // set value from original tree in case this is a mapped property
      Property mappedProperty = mapProperty(originalContent, property, patternMappings);
      if (mappedProperty != null && rewritePropertiesNode != null) {
        if (rewritePropertiesNode.hasProperty("./" + mappedProperty.getName())) {
          rewriteProperty(property, rewritePropertiesNode.getProperty("./" + mappedProperty.getName()));
        }
      }
    }
    return nodeIsFinal;
  }

  /*
   * Applies a string rewrite to a property.
   */
  protected void rewriteProperty(Property property, Property rewriteProperty) throws RepositoryException {
    if (property.getType() == PropertyType.STRING &&
        rewriteProperty.isMultiple() &&
        rewriteProperty.getValues().length == 2) {

      Value[] rewrite = rewriteProperty.getValues();

      if (rewrite[0].getType() == PropertyType.STRING && rewrite[1].getType() == PropertyType.STRING) {
        String pattern = rewrite[0].toString();
        String replacement = rewrite[1].toString();

        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(property.getValue().toString());
        property.setValue(matcher.replaceAll(replacement));
      }
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NodeBasedRewriteRule that = (NodeBasedRewriteRule) o;

    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return String.format("%s[path=%s,ranking=%d]", getTitle(), getId(), getRanking());
  }

  /*
    Tracks nodes to be copied or mapped from original to new tree
   */
  private static class TreeStructure {

    private final List<String> copyChildrenPaths = new ArrayList<>();
    private final Map<String, Map<String, String>> mappings = new HashMap<>();
    private final Map<String, String> ordering = new HashMap<>();

    /*
      Track which nodes need their children copied
     */
    public void addCopyChildrenPath(String path) {
      copyChildrenPaths.add(path);
    }

    /*
      Store the mapping of new node name -> source node
     */
    public void addNodeMapping(String parentPath, String oldName, String newName) {
      if (!mappings.containsKey(parentPath)) {
        mappings.put(parentPath, new HashMap<>());
      }
      mappings.get(parentPath).put(oldName, newName);
    }

    /*
      Track which path needs to be ordered, full path -> order before sibling
     */
    public void addOrder(String path, String before) {
      ordering.put(path, before);
    }

    /*
      Process the nodes to be copied and/or mapped from original
    */
    private void processCopies(Node fromRoot, Node toRoot) throws RepositoryException {

      for (String path : copyChildrenPaths) {

        // Create relative path to determine which children need to be copied
        // Get the parents of each path, then copy the children from original content
        String relPath = PathUtils.relativize(toRoot.getPath(), path);
        Node fromParent = fromRoot.getNode(relPath);
        Node toParent = toRoot.getNode(relPath);
        NodeIterator children = fromParent.getNodes();
        if (mappings.containsKey(toParent.getPath())) {
          Map<String, String> map = mappings.get(toParent.getPath());
          while (children.hasNext()) {
            Node child = children.nextNode();
            String name = child.getName();
            for (Map.Entry<String, String> entry : map.entrySet()) {
              // Find any mapping
              Node src = fromParent.getNode(entry.getKey());
              // Store the name, and remove the target or there will be a collision
              if (src.getPath().equals(child.getPath())) {
                name = entry.getValue();
                toParent.getNode(name).remove();
                break;
              }
            }
            JcrUtil.copy(child, toParent, name, false);
          }

        } else { // No mappings, just copy the children.
          while (children.hasNext()) {
            Node next = children.nextNode();
            // Check if node is to be renamed
            JcrUtil.copy(next, toParent, next.getName(), false);
          }
        }
      }
    }

    /*
      Applies the ordering rules.
     */
    private void processOrder(Node root) throws RepositoryException {
      // now that everything is copied, reorder
      for (Map.Entry<String, String> entry : ordering.entrySet()) {
        Node move = root.getNode(PathUtils.relativize(root.getPath(), entry.getKey()));
        move.getParent().orderBefore(move.getName(), entry.getValue());
      }
    }
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
    private AggregateIterator(Node start, Node patterns) throws RepositoryException {
      long skip = 0;
      NodeIterator siblings = start.getParent().getNodes();
      boolean found = false;
      while (!found && siblings.hasNext()) {
        skip++;
        found = siblings.nextNode().getName().equals(start.getName());
      }

      this.nodeIterator = start.getParent().getNodes();
      this.nodeIterator.skip(skip - (long) 1);
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
    public boolean checkNext() {
      return !patternIterator.hasNext() || nodeIterator.hasNext();
    }
  }
}
