package com.adobe.aem.modernize.rule;

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

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import com.adobe.aem.modernize.RewriteException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public interface RewriteRule extends Comparable<RewriteRule> {

  /**
   * The title for this rule.
   *
   * @return the title
   */
  default String getTitle() {
    return this.getId();
  }

  /**
   * Returns the unique identifier for this Rule.
   *
   * @return the id
   */
  String getId();

  /**
   * Returns true if this rule matches the given subtree.
   *
   * @param root The root of the subtree to be checked for a match
   * @return true if this rule applies, false otherwise
   * @throws RepositoryException if reading the repository fails
   */
  boolean matches(@NotNull Node root) throws RepositoryException;

  /**
   * <p>Applies this rule to the subtree rooted at the specified <code>root</code> node. The implementation of this
   * method may either modify the properties and nodes contained in that tree, or replace it by adding a new child
   * to the parent of <code>root</code>. In the latter case, the implementation is responsible for removing the
   * original subtree (without saving).</p>
   *
   * <p>Repository modifications are made but not saved.</p>
   *
   * <p>Rewrite rules must not rewrite trees in a circular fashion, as this might lead to infinite loops.</p>
   *
   * <p>Optionally, the implementation can indicate which nodes of the resulting tree are final and therefore
   * safe for the algorithm to skip in subsequent traversals of the tree. Add the paths of final nodes to the
   * specified set.
   *
   * @param root       The root of the subtree to be rewritten
   * @param finalPaths list of nodes paths which should not be updated after this method completes
   * @return the root node of the rewritten tree, or null if it was removed
   * @throws RewriteException    if the rewrite operation failed or cannot be completed
   * @throws RepositoryException if the node updates cannot be saved
   */
  @Nullable
  Node applyTo(@NotNull Node root, @NotNull Set<String> finalPaths) throws RewriteException, RepositoryException;

  @Override
  default int compareTo(@NotNull RewriteRule o) {
    return Integer.compare(this.getRanking(), o.getRanking());
  }

  /**
   * Ranking of this Rule relative to others that may also match a given node.
   *
   * @return the rule's ranking
   */
  default int getRanking() {
    return Integer.MAX_VALUE;
  }

  class Comparator implements java.util.Comparator<RewriteRule> {
    @Override
    public int compare(RewriteRule left, RewriteRule right) {
      return Integer.compare(left.getRanking(), right.getRanking());
    }
  }
}
