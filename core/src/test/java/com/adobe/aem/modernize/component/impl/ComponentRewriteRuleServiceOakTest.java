package com.adobe.aem.modernize.component.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.day.cq.search.QueryBuilder;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

// This is here to prevent the other tests from taking longer as this needs an actual Oak impl.
@ExtendWith(SlingContextExtension.class)
public class ComponentRewriteRuleServiceOakTest {


  private final String[] RULE_PATHS = new String[] { "/apps/aem-modernize/component/rules", "/apps/customer/component/rules" };
  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private ComponentRewriteRuleService componentRewriteRuleService = new ComponentRewriteRuleServiceImpl();

  @Mocked
  private QueryBuilder queryBuilder;

  @BeforeEach
  public void beforeEach() {
    Map<String, Object> props = new HashMap<>();
    props.put("search.paths", RULE_PATHS);
    context.load().json("/component/test-rules.json", "/apps/aem-modernize/component/rules");
    context.load().json("/component/all-content.json", "/content/test/all");
    context.load().json("/component/aggregate-content.json", "/content/test/aggregate");
    context.registerService(QueryBuilder.class, queryBuilder);
    context.registerInjectActivateService(componentRewriteRuleService, props);
  }

  @Test
  public void testShallowKeepsOrder() throws Exception {

    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/copyChildren");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/copyChildren");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/mapProperties", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteOptional", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");

  }

  @Test
  public void testShallowAggregateKeepsOrder() throws Exception {

    Resource resource = context.resourceResolver().getResource("/content/test/all/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/all/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/simple", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteMapChildren", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinal", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteFinalOnReplacement", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteProperties", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");

  }

  @Test
  public void testShallowAggregateOnlyChild() throws Exception {

    Resource resource = context.resourceResolver().getResource("/content/test/aggregate/singleNode/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/aggregate/singleNode/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");

  }


  @Test
  public void testShallowAggregateFirstNode() throws Exception {

    Resource resource = context.resourceResolver().getResource("/content/test/aggregate/singleSibling/jcr:content/copyChildrenOrder");
    Set<String> rule = Collections.singleton("/apps/aem-modernize/component/rules/aggregate");
    componentRewriteRuleService.apply(resource, rule, false);
    context.resourceResolver().commit();
    String parentPath = "/content/test/aggregate/singleSibling/jcr:content";
    Resource parent = context.resourceResolver().getResource(parentPath);
    Iterator<Resource> children = parent.getChildren().iterator();
    assertEquals(parentPath + "/copyChildrenOrder", children.next().getPath(), "Order preserved");
    assertEquals(parentPath + "/rewriteRanking", children.next().getPath(), "Order preserved");
    assertFalse(children.hasNext(), "Children length");

  }
}
