/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.aem.modernize.component.impl.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.osgi.PropertiesUtil;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.day.cq.commons.jcr.JcrUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.day.cq.wcm.api.NameConstants.*;
import static org.apache.jackrabbit.JcrConstants.*;
import static org.apache.sling.jcr.resource.api.JcrResourceConstants.*;

/**
 * Rewrites a Column Control component into multiple Responsive Grid layout components. Each column in the
 * original control will become a grid instance with all of the appropriately nested components.
 * <p>
 * The layout is mapped to column spans within the responsive grid.
 */
@Component(
    service = { ComponentRewriteRule.class },
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = {
        "service.ranking=20"
    }
)
@Designate(ocd = ColumnControlRewriteRule.Config.class, factory = true)
public class ColumnControlRewriteRule implements ComponentRewriteRule {

  protected static final String PN_WIDTH = "width";
  protected static final String PN_OFFSET = "offset";
  private static final Logger logger = LoggerFactory.getLogger(ColumnControlRewriteRule.class);
  private static final Pattern pattern = Pattern.compile("^(\\w+)=\\[([0-9,]+)\\]$");
  private static final String RESPONSIVE_GRID_BASE_TYPE = "wcm/foundation/components/responsivegrid";
  private static final String PROP_RESOURCE_TYPE_DEFAULT = "foundation/components/parsys/colctrl";
  private static final String PROP_RESPONSIVE_TYPE = "RESPONSIVE";
  private static final String PROP_CONTAINER_TYPE = "CONTAINER";
  private static final String NN_HINT = "container";
  private static final String PN_LAYOUT = "layout";


  private final Map<String, long[]> widths = new HashMap<>();
  private String id = this.getClass().getName();
  private int ranking = Integer.MAX_VALUE;
  private int columns;
  private boolean isResponsive;
  private String columnControlResourceType = PROP_RESOURCE_TYPE_DEFAULT;
  private String containerResourceType;
  private String layout;


  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public String getTitle() {
    String[] names = new String[widths.size()];
    List<String> keys = new ArrayList<>(widths.keySet());
    Collections.sort(keys);
    for (int i = 0; i < keys.size(); i++) {
      String key = keys.get(i);
      names[i] = String.format("%s=[%s]", key, StringUtils.join(widths.get(key), ','));
    }
    return String.format("ColumnControlRewriteRule ('%s' => %s)", layout, StringUtils.join(names, ','));
  }

  @Override
  public int getRanking() {
    return this.ranking;
  }

  // This Rule matches off a parent container which has the columns.
  @Override
  public @NotNull Set<String> findMatches(@NotNull Resource resource) {
    final Set<String> paths = new HashSet<>();
    new AbstractResourceVisitor() {
      @Override
      protected void visit(@NotNull Resource resource) {

        Iterator<Resource> children = resource.listChildren();
        while(children.hasNext()) {
          Resource child = children.next();
          if (StringUtils.equals(columnControlResourceType, child.getResourceType()) &&
              StringUtils.equals(layout, child.getValueMap().get(PN_LAYOUT, String.class))) {
            paths.add(resource.getPath());
          }
        }
      }
    }.accept(resource);
    return paths;
  }

  @Override
  public boolean hasPattern(@NotNull String... slingResourceTypes) {
    return Arrays.asList(slingResourceTypes).contains(columnControlResourceType);
  }

  @Override
  public boolean matches(@NotNull Node node) throws RepositoryException {

    if (!node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
      return false;
    }
    Property property = node.getProperty(SLING_RESOURCE_TYPE_PROPERTY);
    boolean found = false;
    try {
      Session session = node.getSession();
      ResourceResolver rr = resourceResolverFactory.getResourceResolver(Collections.singletonMap(AUTHENTICATION_INFO_SESSION, session));
      String resourceType = property.getString();
      while (StringUtils.isNotBlank(resourceType)) {
        if (StringUtils.equals(RESPONSIVE_GRID_BASE_TYPE, resourceType)) {
          found = true;
          break; // This node is of the correct type.
        }
        resourceType = rr.getParentResourceType(resourceType);

      }
    } catch (LoginException e) {
      logger.error("Unable to get a ResourceResolver using Node Session info.", e);
      return false;
    }

    if (!found) {
      return false;
    }

    return findFirstColumn(node.getNodes()) != null;
  }

  /**
   * Updates this node, the returned node is the primary column control resource, although this updates a number of sibling resources.
   *
   * @param root       The root of the subtree to be rewritten
   * @param finalPaths list of nodes that should not be processed
   * @return updated Node
   * @throws RewriteException    when an error occurs during the rewrite operation
   * @throws RepositoryException when any repository operation error occurs
   */
  @Nullable
  @Override
  public Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RewriteException, RepositoryException {

    if (isResponsive) {
      return processResponsiveGrid(root);
    } else {
      return processContainer(root, finalPaths);
    }
  }

  // Move the NodeIterator to the column if found.
  private Node findFirstColumn(NodeIterator siblings) throws RepositoryException {
    Node found = null;

    while (siblings.hasNext() && found == null) {
      Node node = siblings.nextNode();
      if (!node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
        continue;
      }
      Property property = node.getProperty(SLING_RESOURCE_TYPE_PROPERTY);
      if (property == null || !StringUtils.equals(columnControlResourceType, property.getString())) {
        continue;
      }

      if (!node.hasProperty(PN_LAYOUT)) {
        continue;
      }
      property = node.getProperty(PN_LAYOUT);
      if (property == null || !StringUtils.equals(layout, property.getString())) {
        continue;
      }
      found = node;
    }
    return found;
  }

  private Node processResponsiveGrid(Node root) throws RepositoryException {
    NodeIterator siblings = root.getNodes();
    Node node = findFirstColumn(siblings);
    node.remove(); // Remove starting column.
    int i = 0;
    do {
      while (siblings.hasNext()) {
        node = siblings.nextNode();
        if (StringUtils.equals(columnControlResourceType, node.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString())) {
          // Node is now the next column break;
          node.remove();  // Remove the columns when we find it, this ensures the end column gets deleted too.
          break;
        }
        addResponsive(node, i);
      }
      i++;
    } while (i < columns);

    return root;
  }

  private Node processContainer(Node root, Set<String> finalPaths) throws RepositoryException {

    NodeIterator siblings = root.getNodes();
    Node node = findFirstColumn(siblings);
    Session session = root.getSession();
    int i = 0;
    node.remove(); // Remove the starting column.
    do {
      // Create the container
      String name = JcrUtil.createValidChildName(root, NN_HINT);
      Node container = root.addNode(name, NT_UNSTRUCTURED);
      container.setProperty(SLING_RESOURCE_TYPE_PROPERTY, containerResourceType);
      addResponsive(container, i);
      finalPaths.add(container.getPath());

      // Added the container, remove the column break;
      // Move the nodes up to the next column into the container.
      while (siblings.hasNext()) {
        node = siblings.nextNode();
        if (StringUtils.equals(columnControlResourceType, node.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString())) {
          // Node is now the next column break;
          node.remove();  // Remove the columns when we find it, this ensures the end column gets deleted too.
          break;
        }
        session.move(node.getPath(), PathUtils.concat(container.getPath(), node.getName()));
      }

      i++;
    } while (i < columns);

    // Move remaining siblings to after the containers.
    while (siblings.hasNext()) {
      Node next = siblings.nextNode();
      root.orderBefore(next.getName(), null);
    }
    return root;
  }

  private void addResponsive(Node node, int index) throws RepositoryException {
    Node responsive = node.addNode(NN_RESPONSIVE_CONFIG, NT_UNSTRUCTURED);
    for (String key : widths.keySet()) {
      Node entry = responsive.addNode(key, NT_UNSTRUCTURED);
      entry.setProperty(PN_OFFSET, "0");
      entry.setProperty(PN_WIDTH, widths.get(key)[index]);
    }
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(ComponentContext context, Config config) throws RepositoryException, ConfigurationException {
    @SuppressWarnings("unchecked")
    Dictionary<String, Object> props = context.getProperties();
    // read service ranking property
    this.ranking = PropertiesUtil.toInteger(props.get("service.ranking"), this.ranking);
    this.id = PropertiesUtil.toString(props.get("service.pid"), this.id);

    columnControlResourceType = config.column_control_resourceType();
    if (StringUtils.isBlank(columnControlResourceType)) {
      columnControlResourceType = PROP_RESOURCE_TYPE_DEFAULT;
    }

    String type = config.grid_type();
    isResponsive = !StringUtils.equals(PROP_CONTAINER_TYPE, type);

    if (!isResponsive) {
      containerResourceType = config.container_resourceType();
      if (StringUtils.isBlank(containerResourceType)) {
        throw new ConfigurationException("container.resourceType", "Container resource type is required when conversion is type CONTAINER.");
      }
    }

    layout = config.layout_value();
    if (StringUtils.isBlank(layout)) {
      throw new ConfigurationException("layout.value", "Layout value property is required.");
    } else {
      try {
        columns = Integer.parseInt(StringUtils.substringBefore(layout, ";"));
      } catch (NumberFormatException e) {
        throw new ConfigurationException("layout.value", "Unknown format of layout.");
      }
    }

    String[] widthDefinitions = config.column_widths();
    if (ArrayUtils.isEmpty(config.column_widths())) {
      throw new ConfigurationException("column.widths", "Column width property is required.");
    } else {
      for (String def : widthDefinitions) {
        Matcher matcher = pattern.matcher(def);
        if (!matcher.matches()) {
          throw new ConfigurationException("column.widths", "Invalid format for one of the width configurations.");
        }
        String name = matcher.group(1);
        String[] widthStrs = matcher.group(2).split(",");
        if (widthStrs.length != columns) {
          throw new ConfigurationException("column.widths", "Number of columns doesn't match layout format.");
        }
        long[] widths = new long[columns];
        try {
          for (int i = 0; i < columns; i++) {
            widths[i] = Long.parseLong(widthStrs[i]);
          }
        } catch (NumberFormatException e) {
          throw new ConfigurationException("column.widths", "Column width definitions must be of type long.");
        }
        this.widths.put(name, widths);
      }
    }
  }

  @ObjectClassDefinition(
      name = "AEM Modernization Tools - Column Control Rewrite Rule",
      description = "Rewrites Column control components to grid replacements."
  )
  @interface Config {
    @AttributeDefinition(
        name = "Column Control ResourceType",
        description = "The sling:resourceType of the column control to match, leave blank to use Foundation component."
    )
    String column_control_resourceType() default PROP_RESOURCE_TYPE_DEFAULT;

    @AttributeDefinition(
        name = "Conversion Type",
        description = "Type of structure to convert to: RESPONSIVE will arrange column contents in parent responsive grid. CONTAINER will replace each column with a container.",
        options = {
            @Option(label = "Responsive", value = PROP_RESPONSIVE_TYPE),
            @Option(label = "Container", value = PROP_CONTAINER_TYPE),
        }
    )
    String grid_type() default PROP_RESPONSIVE_TYPE;

    @AttributeDefinition(
        name = "Container ResourceType",
        description = "The sling:resourceType of the containers to create, used when conversion type is CONTAINER."
    )
    String container_resourceType();

    @AttributeDefinition(
        name = "Layout Property Value",
        description = "The value of the `layout` property on the primary column control component."
    )
    String layout_value();

    @AttributeDefinition(
        name = "Column Widths",
        description = "Array of layout mapping widths for the conversion. " +
            "Format is '<name>=[<widths>]' where <name> is the responsive grid layout name, and <widths> is a list of widths foreach column size. " +
            "Example: default=[6,6] will set the responsive grid layout 'default' to each item in the a column to be six grid columns wide. " +
            "Each entry must have the number of columns matched in the layout property.",
        cardinality = Integer.MAX_VALUE
    )
    String[] column_widths();
  }

}
