package com.adobe.aem.modernize.component.rule;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
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

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.component.ComponentRewriteRule;
import com.adobe.aem.modernize.component.rule.helpers.ColumnLayoutHelper;
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
import org.osgi.util.converter.Converters;
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
        service = { ComponentRewriteRule.class }
        ,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                "service.ranking=20"
        }
)
@Designate(ocd = ColumnControlRewriteRule.Config.class, factory = true)
public class ColumnControlRewriteRule implements ComponentRewriteRule {

  private static final Logger logger = LoggerFactory.getLogger(ColumnControlRewriteRule.class);
  private static final String PARSYS_BASE_TYPE = "foundation/components/parsys";
  private static final String RESPONSIVE_GRID_BASE_TYPE = "wcm/foundation/components/responsivegrid";
  private static final String PROP_RESOURCE_TYPE_DEFAULT = "foundation/components/parsys/colctrl";
  private static final String PROP_RESPONSIVE_TYPE = "RESPONSIVE";
  private static final String PROP_CONTAINER_TYPE = "CONTAINER";
  private static final String NN_HINT = "container";
  private static final String PN_LAYOUT = "layout";

  private String id = this.getClass().getName();
  private int ranking = Integer.MAX_VALUE;
  private boolean isResponsive;
  private String columnControlResourceType = PROP_RESOURCE_TYPE_DEFAULT;
  private String containerResourceType;
  private String layout;
  private ColumnLayoutHelper columnHelper;

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

  @Override
  public String getTitle() {
    return String.format("ColumnControlRewriteRule ('%s' => %s)",
            layout, columnHelper.getFormattedWidthConfigurations());
  }

  @Override
  public String getId() {
    return this.id;
  }

  /**
   * Checks if a node has a resource type that matches our criteria.
   */
  private boolean hasMatchingResourceType(Node node) throws RepositoryException {
    if (!node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
      return false;
    }

    boolean found = false;
    Session session = node.getSession();

    try (ResourceResolver rr = resourceResolverFactory.getResourceResolver(
            Collections.singletonMap(AUTHENTICATION_INFO_SESSION, session))) {

      String resourceType = node.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString();

      while (StringUtils.isNotBlank(resourceType)) {
        if (StringUtils.equals(RESPONSIVE_GRID_BASE_TYPE, resourceType)) {
          found = true;
          break;
        }
        resourceType = rr.getParentResourceType(resourceType);
      }

    } catch (LoginException e) {
      logger.error("Unable to get a ResourceResolver using Node Session info.", e);
      return false;
    }

    return found;
  }

  @Override
  public boolean matches(@NotNull Node node) throws RepositoryException {
    if (!hasMatchingResourceType(node)) {
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
    try {
      if (isResponsive) {
        return processResponsiveGrid(root);
      } else {
        return processContainer(root, finalPaths);
      }
    } catch (RepositoryException e) {
      throw new RewriteException("Error applying column control rewrite rule to " + root.getPath(), e);
    }
  }

  @Override
  public int getRanking() {
    return this.ranking;
  }

  /**
   * Process node as a responsive grid layout.
   */
  private Node processResponsiveGrid(Node root) throws RepositoryException {
    List<Queue<String>> columnContents = columnHelper.getColumnContent(root);

    // Function to check if all column queues are empty
    Function<List<Queue<String>>, Boolean> empty = (queues) -> {
      Queue<String> found = queues.stream().filter(q -> !q.isEmpty()).findFirst().orElse(null);
      return found == null;
    };

    Queue<String> order = new LinkedList<>();

    // Find and remove the first column control node
    Node child;
    NodeIterator siblings = root.getNodes();

    do {
      child = siblings.nextNode();
      if (columnHelper.isColumnNode(child)) {
        child.remove();
        break;
      }
      order.add(child.getName());
    } while (siblings.hasNext());

    // Process column contents
    boolean offset = false;
    boolean newline = false;

    while (!empty.apply(columnContents) && siblings.hasNext()) {
      for (int c = 0; c < columnContents.size(); c++) {
        Queue<String> column = columnContents.get(c);

        if (c == 0 && column.isEmpty()) {
          offset = true;
          continue;
        } else if (c != 0 && column.isEmpty()) {
          newline = true;
          continue;
        }

        // Process this column's content
        Node node = root.getNode(column.remove());
        columnHelper.addResponsiveConfiguration(node, c, newline, offset);
        order.add(node.getName());

        // Remove the next column control node
        child = siblings.nextNode();
        if (columnHelper.isColumnNode(child)) {
          child.remove();
          if (siblings.hasNext()) {
            siblings.nextNode();
          }
        }

        offset = false;
        newline = false;
      }
    }

    // Move remaining non-column content to the end, preserving order
    while (siblings.hasNext()) {
      child = siblings.nextNode();
      if (columnHelper.isColumnNode(child)) {
        child.remove();
        continue;
      }
      order.add(child.getName());
    }

    // Apply final ordering
    while (!order.isEmpty()) {
      root.orderBefore(order.remove(), null);
    }

    return root;
  }

  /**
   * Process node as container components.
   */
  private Node processContainer(Node root, Set<String> finalPaths) throws RepositoryException {
    NodeIterator siblings = root.getNodes();
    Node node = findFirstColumn(siblings);

    if (node == null) {
      return root; // Protect against NPE if incorrectly called
    }

    Session session = root.getSession();
    node.remove(); // Remove the starting column

    // Create containers for each column
    for (int i = 0; i < columnHelper.getColumnCount(); i++) {
      // Create the container
      String name = JcrUtil.createValidChildName(root, NN_HINT);
      Node container = root.addNode(name, NT_UNSTRUCTURED);
      container.setProperty(SLING_RESOURCE_TYPE_PROPERTY, containerResourceType);
      columnHelper.addResponsiveConfiguration(container, i, false, false);
      finalPaths.add(container.getPath());

      // Move nodes up to the next column into the container
      while (siblings.hasNext()) {
        node = siblings.nextNode();
        if (columnHelper.isColumnNode(node)) {
          // Found the next column break
          node.remove();
          break;
        }
        session.move(node.getPath(), PathUtils.concat(container.getPath(), node.getName()));
      }
    }

    // Move remaining siblings to after the containers
    while (siblings.hasNext()) {
      Node next = siblings.nextNode();
      root.orderBefore(next.getName(), null);
    }

    return root;
  }

  // Find the first column control node that matches our criteria
  private Node findFirstColumn(NodeIterator siblings) throws RepositoryException {
    return columnHelper.findFirstColumn(siblings, layout);
  }

  @Override
  public @NotNull Set<String> findMatches(@NotNull Resource resource) {
    final Set<String> paths = new HashSet<>();
    ResourceResolver rr = resource.getResourceResolver();

    new AbstractResourceVisitor() {
      @Override
      protected void visit(@NotNull Resource resource) {
        String resourceType = resource.getResourceType();

        if (StringUtils.equals(PARSYS_BASE_TYPE, resourceType)) {
          paths.add(resource.getPath());
        } else {
          // Check if it's a responsive grid
          while (StringUtils.isNotBlank(resourceType)) {
            if (StringUtils.equals(RESPONSIVE_GRID_BASE_TYPE, resourceType)) {
              paths.add(resource.getPath());
              break;
            }
            resourceType = rr.getParentResourceType(resourceType);
          }
        }
      }
    }.accept(resource);

    return paths;
  }

  @Override
  public boolean hasPattern(@NotNull String... slingResourceTypes) {
    List<String> types = Arrays.asList(slingResourceTypes);
    return types.contains(containerResourceType) ||
            types.contains(RESPONSIVE_GRID_BASE_TYPE) ||
            types.contains(PARSYS_BASE_TYPE);
  }

  @Activate
  @Modified
  @SuppressWarnings("unused")
  protected void activate(ComponentContext context, Config config) throws ConfigurationException {
    // Read service ranking property
    this.ranking = Converters.standardConverter()
            .convert(context.getProperties().get("service.ranking"))
            .defaultValue(Integer.MAX_VALUE)
            .to(Integer.class);

    this.id = Converters.standardConverter()
            .convert(context.getProperties().get("service.pid"))
            .defaultValue(this.id)
            .to(String.class);

    // Configure column control
    columnControlResourceType = config.column_control_resourceType();
    if (StringUtils.isBlank(columnControlResourceType)) {
      columnControlResourceType = PROP_RESOURCE_TYPE_DEFAULT;
    }

    // For now, hard-code to not responsive - this can be restored when UI is updated
    isResponsive = false; // !StringUtils.equals(PROP_CONTAINER_TYPE, type);

    containerResourceType = config.container_resourceType();
    if (!isResponsive && StringUtils.isBlank(containerResourceType)) {
      throw new ConfigurationException(
              "container.resourceType",
              "Container resource type is required when conversion is type CONTAINER.");
    }

    layout = config.layout_value();
    if (StringUtils.isBlank(layout)) {
      throw new ConfigurationException("layout.value", "Layout value property is required.");
    }

    // Parse number of columns from layout
    int columns;
    try {
      columns = Integer.parseInt(StringUtils.substringBefore(layout, ";"));
    } catch (NumberFormatException e) {
      throw new ConfigurationException("layout.value", "Unknown format of layout.");
    }

    // Initialize column helper
    columnHelper = new ColumnLayoutHelper(columnControlResourceType, columns);

    // Parse column width configurations
    String[] widthDefinitions = config.column_widths();
    if (ArrayUtils.isEmpty(widthDefinitions)) {
      throw new ConfigurationException("column.widths", "Column width property is required.");
    }

    try {
      columnHelper.parseWidthConfigurations(widthDefinitions);
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException("column.widths", e.getMessage());
    }
  }

  @ObjectClassDefinition(
          name = "AEM Modernize Tools - Column Control Rewrite Rule",
          description = "Rewrites Column control components to grid replacements."
  )
  @interface Config {
    @AttributeDefinition(
            name = "Column Control ResourceType",
            description = "The sling:resourceType of the column control to match, leave blank to use Foundation component."
    )
    String column_control_resourceType() default PROP_RESOURCE_TYPE_DEFAULT;

//    @AttributeDefinition(
//        name = "Conversion Type",
//        description = "Type of structure to convert to: RESPONSIVE will arrange column contents in parent responsive grid. CONTAINER will replace each column with a container.",
//        options = {
//            @Option(label = "Responsive", value = PROP_RESPONSIVE_TYPE),
//            @Option(label = "Container", value = PROP_CONTAINER_TYPE),
//        }
//    )
//    String grid_type() default PROP_RESPONSIVE_TYPE;

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