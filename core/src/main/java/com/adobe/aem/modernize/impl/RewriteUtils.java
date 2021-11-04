package com.adobe.aem.modernize.impl;

import java.util.Iterator;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.jackrabbit.commons.flat.TreeTraverser;
import org.apache.jackrabbit.oak.commons.PathUtils;

public class RewriteUtils {

  /**
   * Traverses the entire tree rooted at the provided node; Any property found with the search term will have that literal
   * replaced with the replacement value.
   */
  public static void updateReferences(Node root, String search, String replacement) throws RepositoryException {

    ValueFactory vf = root.getSession().getValueFactory();
    TreeTraverser traverser = new TreeTraverser(root);
    Iterator<Node> iterator = traverser.iterator();
    while (iterator.hasNext()) {
      Node node = iterator.next();
      PropertyIterator pi = node.getProperties();
      while (pi.hasNext()) {
        Property p = pi.nextProperty();
        if (p.getType() == PropertyType.STRING) {
          Value[] values;
          if (p.isMultiple()) {
            values = p.getValues();
            for (int i = 0; i < values.length; i++) {
              values[i] = vf.createValue(values[i].getString().replaceAll(search, replacement));
            }
            p.setValue(values);
          } else {
            p.setValue(p.getValue().getString().replaceAll(search, replacement));
          }
        }
      }
    }
  }

  /**
   * Given a source path, and destination relative root, determine where a new path would be, if depth in repository were kept constant.
   *
   * @param source     source path
   * @param targetRoot target path
   * @return the new path to the resource
   */
  public static String calcNewPath(String source, String targetRoot) {
    int depth = PathUtils.getDepth(targetRoot);
    int relDepth = PathUtils.getDepth(source) - depth;
    String relPath = PathUtils.getAncestorPath(source, relDepth);
    return source.replaceFirst(relPath, targetRoot);
  }
}
