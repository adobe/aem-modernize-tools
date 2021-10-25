package com.adobe.aem.modernize.rule.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.MockHit;
import com.adobe.aem.modernize.MockRule;
import com.adobe.aem.modernize.rule.RewriteRule;
import com.adobe.aem.modernize.rule.ServiceBasedRewriteRule;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class AbstractRewriteRuleServiceTest {

  public static SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

  private static final String[] RULE_PATHS = new String[] { "/content/rules/simple", "/content/rules/aggregate"};

  @Mocked
  private ServiceBasedRewriteRule matchedRewriteRule;

  @Mocked
  private ServiceBasedRewriteRule notMatchedRewriteRule;

  @Mocked
  private QueryBuilder queryBuilder;


  private final TestRewriteRuleService rewriteRuleService = new TestRewriteRuleService();

  @BeforeEach
  protected void beforeEach() {
    context.load().json("/rewrite/test-simple-rules.json", "/content/rules/simple");
    context.load().json("/rewrite/test-aggregate-rules.json", "/content/rules/aggregate");
    context.load().json("/rewrite/test-simple-rules.json", "/apps/not-registered/rules");
    context.load().json("/rewrite/test-content.json", "/content/test/all/jcr:content");
  }

  @Test
  public <R extends ResourceResolver> void testFindRulesQueryFails(
      @Mocked Query query,
      @Mocked SearchResult searchResult
  ) {
    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == QueryBuilder.class) {
          return (T) queryBuilder;
        } else {
          return inv.proceed();
        }
      }
    };
    new Expectations() {{

      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = new RepositoryException("Error");

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/serviceTest");
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = rewriteRuleService.find(root);
    assertTrue(paths.contains("/content/test/all/serviceTest"));
    paths.remove("/content/test/all/serviceTest");
    assertTrue(paths.isEmpty(), "Paths content");
  }

  @Test
  public <R extends ResourceResolver> void testFindComponentsQueryFails(@Mocked Query query, @Mocked SearchResult searchResult) {

    new MockUp<R>() {
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == QueryBuilder.class) {
          return (T) queryBuilder;
        } else {
          return inv.proceed();
        }
      }
    };

    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = new RepositoryException("Error");

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      result = Collections.singleton("/content/test/all/jcr:content/serviceTest");
      times = 1;
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      result = Collections.emptySet();
      times = 1;
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all");
    Set<String> paths = rewriteRuleService.find(root);
    assertTrue(paths.contains("/content/test/all/jcr:content/serviceTest"), "Service path in list");
    paths.remove("/content/test/all/jcr:content/serviceTest");
    assertTrue(paths.isEmpty(), "Paths content");
  }

  @Test
  public <R extends ResourceResolver> void testFind(@Mocked Query query, @Mocked SearchResult searchResult) {

    final int[] closeCalled = { 0 };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0]++;
      }

      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == QueryBuilder.class) {
          return (T) queryBuilder;
        } else {
          return inv.proceed();
        }
      }
    };
    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      times = 1;
      query.getResult();
      result = searchResult;
      times = 1;
      searchResult.getHits();
      result = buildComponentHits();

      matchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.singleton("/content/test/all/jcr:content/serviceTest");
      notMatchedRewriteRule.findMatches(withInstanceOf(Resource.class));
      times = 1;
      result = Collections.emptySet();
    }};

    Resource root = context.resourceResolver().getResource("/content/test/all/jcr:content");
    Set<String> paths = rewriteRuleService.find(root);

    assertTrue(paths.contains("/content/test/all/jcr:content/remove"), "Service rule");
    paths.remove("/content/test/all/jcr:content/remove");
    assertTrue(paths.contains("/content/test/all/jcr:content/simple"), "Simple rule");
    paths.remove("/content/test/all/jcr:content/simple");
    assertTrue(paths.contains("/content/test/all/jcr:content/copyChildren"), "Copy children rule");
    paths.remove("/content/test/all/jcr:content/copyChildren");
    assertTrue(paths.contains("/content/test/all/jcr:content/copyChildrenNested"), "Copy children rule");
    paths.remove("/content/test/all/jcr:content/copyChildrenNested");
    assertTrue(paths.contains("/content/test/all/jcr:content/copyChildrenOrder"), "Copy children order rule");
    paths.remove("/content/test/all/jcr:content/copyChildrenOrder");
    assertTrue(paths.contains("/content/test/all/jcr:content/mapProperties"), "Map properties rule");
    paths.remove("/content/test/all/jcr:content/mapProperties");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteOptional"), "Rewrite Optional rule");
    paths.remove("/content/test/all/jcr:content/rewriteOptional");
    assertTrue(paths.contains("/content/test/all/jcr:content/nestedRewriteOptional"), "Rewrite Optional rule");
    paths.remove("/content/test/all/jcr:content/nestedRewriteOptional");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteRanking"), "Ranking rule");
    paths.remove("/content/test/all/jcr:content/rewriteRanking");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteMapChildren"), "Rewrite map children rule");
    paths.remove("/content/test/all/jcr:content/rewriteMapChildren");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteMapChildrenNested"), "Rewrite map children rule");
    paths.remove("/content/test/all/jcr:content/rewriteMapChildrenNested");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteFinal"), "Rewrite final rule");
    paths.remove("/content/test/all/jcr:content/rewriteFinal");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteFinalOnReplacement"), "Rewrite final on replacement node rule.");
    paths.remove("/content/test/all/jcr:content/rewriteFinalOnReplacement");
    assertTrue(paths.contains("/content/test/all/jcr:content/rewriteProperties"), "Rewrite properties rule");
    paths.remove("/content/test/all/jcr:content/rewriteProperties");
    assertTrue(paths.contains("/content/test/all/jcr:content/simpleTree"), "Rewrite final on replacement node rule.");
    paths.remove("/content/test/all/jcr:content/simpleTree");
    assertTrue(paths.contains("/content/test/all/jcr:content/aggregate"), "Rewrite final on replacement node rule.");
    paths.remove("/content/test/all/jcr:content/aggregate");
    assertTrue(paths.contains("/content/test/all/jcr:content/serviceTest"), "Service rule");
    paths.remove("/content/test/all/jcr:content/serviceTest");
    assertTrue(paths.isEmpty(), "Rule count");

    assertEquals(1, closeCalled[0], "Query RR was closed");
  }


  @Test
  public <R extends ResourceResolver> void testListRules(@Mocked Query query, @Mocked SearchResult searchResult) {
    final int[] closeCalled = { 0 };

    new MockUp<R>() {
      @Mock
      public void close() {
        closeCalled[0]++;
      }
      @Mock
      public <T> T adaptTo(Invocation inv, Class<T> clazz) {
        if (clazz == QueryBuilder.class) {
          return (T) queryBuilder;
        } else {
          return inv.proceed();
        }
      }
    };

    final String[] foundRuleList = new String[] {
        "/content/rules/simple/simple/patterns/simple",
        "/content/rules/simple/copyChildren/patterns/copyChildren"
    };

    final String[] resourceTypes = new String[] {
        "aem-modernize/components/simple",
        "aem-modernize/components/copyChildren",
        "aem-modenrize/components/service"
    };

    new Expectations() {{
      queryBuilder.createQuery(withInstanceOf(PredicateGroup.class), withInstanceOf(Session.class));
      result = query;
      query.getResult();
      result = searchResult;
      searchResult.getHits();
      result = listRulesAsHits(foundRuleList);
      matchedRewriteRule.hasPattern(resourceTypes);
      result = true;
      notMatchedRewriteRule.hasPattern(resourceTypes);
      result = false;
    }};
    Set<RewriteRule> rules = rewriteRuleService.listRules(context.resourceResolver(), resourceTypes);

    assertEquals(3, rules.size(), "Rule path count");
    assertTrue(rules.contains(new MockRule("/content/rules/simple/simple")), "simple rule returned");
    assertTrue(rules.contains(new MockRule("/content/rules/simple/copyChildren")), "copyChildren rule returned");
    assertTrue(rules.contains(matchedRewriteRule), "Service rule returned");
    assertEquals(1, closeCalled[0], "Query RR was closed");

  }

  @Test
  public void testGetSlingResourceTypes() {

    Resource ruleRoot = context.resourceResolver().getResource("/content/rules/simple");
    Set<String> types = AbstractRewriteRuleService.getSlingResourceTypes(ruleRoot);

    assertTrue(types.contains("aem-modernize/components/remove"), "remove rule");
    types.remove("aem-modernize/components/remove");
    assertTrue(types.contains("aem-modernize/components/simple"), "simple rule");
    types.remove("aem-modernize/components/simple");
    assertTrue(types.contains("aem-modernize/components/copyChildren"), "copyChildren rule");
    types.remove("aem-modernize/components/copyChildren");
    assertTrue(types.contains("aem-modernize/components/copyChildrenNested"), "copyChildrenNested rule");
    types.remove("aem-modernize/components/copyChildrenNested");
    assertTrue(types.contains("aem-modernize/components/copyChildrenOrder"), "copyChildrenOrder rule");
    types.remove("aem-modernize/components/copyChildrenOrder");
    assertTrue(types.contains("aem-modernize/components/mapProperties"), "mapProperties rule");
    types.remove("aem-modernize/components/mapProperties");
    assertTrue(types.contains("aem-modernize/components/rewriteOptional"), "rewriteOptional rule");
    types.remove("aem-modernize/components/rewriteOptional");
    assertTrue(types.contains("aem-modernize/components/rewriteRanking"), "rewriteRanking rule");
    types.remove("aem-modernize/components/rewriteRanking");
    assertTrue(types.contains("aem-modernize/components/rewriteMapChildren"), "rewriteMapChildren rule");
    types.remove("aem-modernize/components/rewriteMapChildren");
    assertTrue(types.contains("aem-modernize/components/rewriteFinal"), "rewriteFinal rule");
    types.remove("aem-modernize/components/rewriteFinal");
    assertTrue(types.contains("aem-modernize/components/replacementRewriteFinal"), "replacementRewriteFinal rule");
    types.remove("aem-modernize/components/replacementRewriteFinal");
    assertTrue(types.contains("aem-modernize/components/rewriteProperties"), "rewriteProperties rule");
    types.remove("aem-modernize/components/rewriteProperties");
    assertTrue(types.isEmpty(), "Set result correct");

    ruleRoot = context.resourceResolver().getResource("/content/rules/aggregate");
    types = AbstractRewriteRuleService.getSlingResourceTypes(ruleRoot);

    assertTrue(types.contains("aem-modernize/components/title"), "title rule");
    types.remove("aem-modernize/components/title");
    assertTrue(types.contains("foundation/components/text"), "text rule");
    types.remove("foundation/components/text");
    assertTrue(types.contains("foundation/components/image"), "image rule");
    types.remove("foundation/components/image");
    assertTrue(types.isEmpty(), "Set result correct");

  }

  private List<Hit> buildComponentHits() {
    List<Hit> hits = new ArrayList<>();
    Resource ruleParent = context.resourceResolver().getResource("/content/test/all/jcr:content");
    for (Resource child : ruleParent.getChildren()) {
      hits.add(new MockHit(child));
    }
    return hits;
  }

  private List<Hit> listRulesAsHits(String... paths) {
    List<Hit> hits = new ArrayList<>();
    ResourceResolver rr = context.resourceResolver();
    for (String path : paths) {
      hits.add(new MockHit(rr.getResource(path)));
    }
    return hits;
  }

  private class TestRewriteRuleService extends AbstractRewriteRuleService<ServiceBasedRewriteRule> {
    @Override
    protected String[] getSearchPaths() {
      return RULE_PATHS;
    }

    @Override
    protected List<ServiceBasedRewriteRule> getServiceRules() {
      List<ServiceBasedRewriteRule> rules = new ArrayList<>();
      rules.add(matchedRewriteRule);
      rules.add(notMatchedRewriteRule);
      return rules;
    }
  }
}
