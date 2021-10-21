package com.adobe.aem.modernize.component.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;

import com.adobe.aem.modernize.component.impl.ComponentTreeRewriter;
import com.adobe.aem.modernize.rule.RewriteRule;
import mockit.Mocked;
import mockit.Expectations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SlingContextExtension.class)
public class ComponentTreeRewriterTest {


  public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);


  @Mocked
  private RewriteRule simpleRule;

  @Mocked
  private RewriteRule addsFinal;


  @Test
  public void preservesOrder() throws Exception {

    List<RewriteRule> rules = new ArrayList<>();
    rules.add(simpleRule);

    new Expectations() {{
      simpleRule.matches(withInstanceOf(Node.class));
      result = false;
      times = 9;
    }};


    context.load().json("/rewrite/test-ordered.json", "/content/test");
    Node root = context.resourceResolver().getResource("/content/test/ordered").adaptTo(Node.class);
    new ComponentTreeRewriter(rules).rewrite(root);

    Session session = root.getSession();
    assertTrue(session.hasPendingChanges(), "Updates were made");
    session.save();
    Resource updated = context.resourceResolver().getResource("/content/test/ordered");

    // Preserved Order
    Iterator<Resource> children = updated.listChildren();
    assertEquals("simple", children.next().getName(), "First child correct.");
    assertEquals("mapProperties", children.next().getName(), "Second child correct.");
    assertEquals("rewriteProperties", children.next().getName(), "Third child correct.");
    assertEquals("rewriteMapChildren", children.next().getName(), "Fourth child correct.");
  }

  @Test
  public void skipsFinalPaths() throws Exception {

    SetRootFinalRewriteRule finalRewriteRule = new SetRootFinalRewriteRule("/content/test/final/mapProperties");
    List<RewriteRule> rules = new ArrayList<>();
    rules.add(simpleRule);
    rules.add(finalRewriteRule);

    new Expectations() {{
      simpleRule.matches(withInstanceOf(Node.class));
      result = false;
    }};


    context.load().json("/rewrite/test-final.json", "/content/test");
    Node root = context.resourceResolver().getResource("/content/test/final").adaptTo(Node.class);
    new ComponentTreeRewriter(rules).rewrite(root);

    Session session = root.getSession();
    assertTrue(session.hasPendingChanges(), "Updates were made");
    session.save();

    // Should only be called once when matched.
    assertEquals(1, finalRewriteRule.invoked, "Rewrite rule invocations");
  }

  private static class SetRootFinalRewriteRule implements RewriteRule {

    private final String path;
    public int invoked = 0;
    public SetRootFinalRewriteRule(String path) {
      this.path = path;
    }

    @Override
    public String getId() {
      return "Mock";
    }

    @Override
    public boolean matches(Node root) throws RepositoryException {
      if (StringUtils.equals(root.getPath(), path)) {
        invoked++;
      }
      return StringUtils.equals(root.getPath(), path);
    }

    @Override
    public Node applyTo(Node root, Set<String> finalPaths) throws RepositoryException {
      if (StringUtils.equals(root.getPath(), path)) {
        finalPaths.add(root.getPath());
      }
      return root;
    }
  }
}
