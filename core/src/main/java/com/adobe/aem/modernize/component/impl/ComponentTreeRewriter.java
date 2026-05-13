package com.adobe.aem.modernize.component.impl;

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.rule.RewriteRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs deep rewrites based on specified rules.
 *
 * <p>Algorithm: iterative pre-order DFS using a path-based stack. After each rule fires the current
 * node is re-fetched by path so stale references don't poison the rest of the walk. Children are
 * read fresh after each rule firing to pick up any subtree mutations. The outer loop re-walks the
 * tree until a pass produces no new matches; this is what discovers newly-created sibling nodes
 * (e.g. the temp container that {@code AdaptiveFormGuideContainerRewriterRule} adds under {@code
 * jcr:content}).
 *
 * <p>The previous algorithm restarted the traversal from the root after every single match — for
 * forms with hundreds of components that meant hundreds of full re-walks of the same tree (O(M²)
 * total node visits, dominated by TreeTraverser iterator overhead). This rewrite needs only 2–4
 * passes typically, cutting visits from ~M² to ~M·passes.
 */
@Deprecated(since = "2.1.0")
public class ComponentTreeRewriter {

  private static final Logger logger = LoggerFactory.getLogger(ComponentTreeRewriter.class);
  private static final int MAX_PASSES = 50;

  /**
   * Rewrites the specified tree according to the provided set of rules. Rules are applied in pre-order DFS.
   *
   * <p>Changes are not saved.
   *
   * @param root The root of the tree to be rewritten
   * @param rules The list of rules to apply to the tree
   * @return the root node of the rewritten tree, or null if it was removed
   */
  @Nullable
  static Node rewrite(@NotNull Node root, @NotNull List<RewriteRule> rules) throws RewriteException, RepositoryException {
    String rootPath = root.getPath();
    logger.debug("Rewriting content tree rooted at: {}", rootPath);
    long tick = System.currentTimeMillis();

    Session session = root.getSession();
    Node[] startNodeRef = new Node[] { root };

    Map<String, Set<String>> processed = new HashMap<>();
    for (RewriteRule rule : rules) {
      processed.put(rule.getId(), new HashSet<>());
    }
    Set<String> finalPaths = new LinkedHashSet<>();

    int passCount = 0;
    boolean changedInPass;
    do {
      passCount++;
      changedInPass = false;
      if (startNodeRef[0] == null) {
        break;
      }
      String startPath = startNodeRef[0].getPath();
      logger.debug("Starting pre-order tree traversal pass {} at root: {}", passCount, startPath);

      // Path-based DFS stack — rule mutations (deletes / renames / new siblings) can't poison it.
      Deque<String> stack = new ArrayDeque<>();
      Set<String> visitedInPass = new HashSet<>();
      stack.push(startPath);

      while (!stack.isEmpty()) {
        String path = stack.pop();
        if (!visitedInPass.add(path)) {
          continue;
        }
        if (!session.nodeExists(path)) {
          continue;
        }

        Node node = session.getNode(path);

        // Try rules unless this path was already finalized by a previous pass.
        if (!finalPaths.contains(path)) {
          for (RewriteRule rule : rules) {
            Set<String> ruleProcessed = processed.get(rule.getId());
            if (ruleProcessed.contains(path)) {
              continue;
            }
            if (!rule.matches(node)) {
              continue;
            }
            logger.debug("Rule [{}] matched subtree at [{}]", rule.getId(), path);
            Node result = rule.applyTo(node, finalPaths);
            ruleProcessed.add(path);
            finalPaths.add(path);
            if (node.equals(startNodeRef[0])) {
              startNodeRef[0] = result;
            }
            changedInPass = true;
            // Re-fetch node after rule application — it may have been deleted, replaced
            // (same path, different identifier), or otherwise structurally changed.
            node = session.nodeExists(path) ? session.getNode(path) : null;
            break;
          }
        }

        if (node == null) {
          // Node was deleted by a rule — don't recurse into a stale subtree.
          continue;
        }

        // Read children fresh (rather than from a pre-collected list) so subtree changes
        // made by rule.applyTo() are picked up immediately.
        NodeIterator children = node.getNodes();
        List<String> childPaths = new ArrayList<>();
        while (children.hasNext()) {
          childPaths.add(children.nextNode().getPath());
        }
        for (int i = childPaths.size() - 1; i >= 0; i--) {
          String childPath = childPaths.get(i);
          if (!visitedInPass.contains(childPath)) {
            stack.push(childPath);
          }
        }
      }

    } while (changedInPass && startNodeRef[0] != null && passCount < MAX_PASSES);

    long tock = System.currentTimeMillis();
    logger.info("Rewrote content tree rooted at [{}] in {}ms ({} passes)", rootPath, tock - tick, passCount);

    return startNodeRef[0];
  }
}
