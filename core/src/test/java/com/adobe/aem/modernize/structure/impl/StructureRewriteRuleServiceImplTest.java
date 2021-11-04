package com.adobe.aem.modernize.structure.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;

import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.day.cq.wcm.api.Page;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(AemContextExtension.class)
public class StructureRewriteRuleServiceImplTest {

  public final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

  private final StructureRewriteRuleService service = new StructureRewriteRuleServiceImpl();

  @Mocked
  private StructureRewriteRule matchedRewriteRule;

  @Mocked
  private StructureRewriteRule notMatchedRewriteRule;

  @BeforeEach
  public void beforeEach() {

    context.load().json("/structure/page-content.json", "/content/test");

    context.registerService(StructureRewriteRule.class, matchedRewriteRule);
    context.registerService(StructureRewriteRule.class, notMatchedRewriteRule);
    context.registerInjectActivateService(service, new HashMap<>());
  }

  @Test
  public void apply() throws Exception {

    new Expectations() {{
      matchedRewriteRule.getId();
      result = "MatchedStructureRewriteRule";
      notMatchedRewriteRule.getId();
      result = "NotMatchedStructureRewriteRule";
      matchedRewriteRule.getRanking();
      result = 10;
      notMatchedRewriteRule.getRanking();
      result = 5;
      notMatchedRewriteRule.matches(withInstanceOf(Node.class));
      result = false;
      matchedRewriteRule.matches(withInstanceOf(Node.class));
      result = true;
      matchedRewriteRule.applyTo(withInstanceOf(Node.class), withInstanceOf(Set.class));
    }};

    Page page = context.resourceResolver().getResource("/content/test/matches").adaptTo(Page.class);

    Set<String> rules = new HashSet<>();
    rules.add("MatchedStructureRewriteRule");
    rules.add("NotMatchedStructureRewriteRule");
    service.apply(page, rules);

  }
}
