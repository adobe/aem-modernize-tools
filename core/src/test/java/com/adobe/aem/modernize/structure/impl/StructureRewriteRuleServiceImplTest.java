package com.adobe.aem.modernize.structure.impl;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.jcr.Node;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;

import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
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
    new Expectations() {{
      matchedRewriteRule.getId();
      result = "MatchedStructureRewriteRule";
      notMatchedRewriteRule.getId();
      result = "NotMatchedStructureRewriteRule";
      matchedRewriteRule.getRanking();
      result = 10;
      notMatchedRewriteRule.getRanking();
      result = 5;
    }};
    context.load().json("/structure/page-content.json", "/content/test");

    context.registerService(StructureRewriteRule.class, matchedRewriteRule);
    context.registerService(StructureRewriteRule.class, notMatchedRewriteRule);
    context.registerInjectActivateService(service, new HashMap<>());
  }

  @Test
  public void apply() throws Exception {

    new Expectations() {{
      notMatchedRewriteRule.matches(withInstanceOf(Node.class));
      result = false;
      matchedRewriteRule.matches(withInstanceOf(Node.class));
      result = true;
      matchedRewriteRule.applyTo(withInstanceOf(Node.class), withInstanceOf(Set.class));
    }};

    Resource page = context.resourceResolver().getResource("/content/test/matches");

    Set<String> rules = new HashSet<>();
    rules.add("MatchedStructureRewriteRule");
    rules.add("NotMatchedStructureRewriteRule");
    service.apply(page, rules);
  }
}
