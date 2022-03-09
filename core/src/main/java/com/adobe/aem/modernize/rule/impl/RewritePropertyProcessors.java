package com.adobe.aem.modernize.rule.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
class RewritePropertyProcessors {

  Node rewriteProperties;
  Node rewriteMapProperties;
  Node rewriteConsolidateProperties;

  void remove() throws RepositoryException {
    if (rewriteProperties != null) {
      rewriteProperties.remove();
    }
    if (rewriteMapProperties != null) {
      rewriteMapProperties.remove();
    }
    if (rewriteConsolidateProperties != null) {
      rewriteConsolidateProperties.remove();
    }
  }

  /*
   * Applies a string rewrite to a property.
   */
  void rewrite(Property property) throws RepositoryException {
    if (rewriteProperties != null &&
        rewriteProperties.hasProperty(property.getName())) {

      Property rule = rewriteProperties.getProperty(property.getName());
      if (property.getType() == PropertyType.STRING &&
          rule.isMultiple() &&
          rule.getValues().length == 2) {

        Value[] rewrite = rule.getValues();

        if (rewrite[0].getType() == PropertyType.STRING && rewrite[1].getType() == PropertyType.STRING) {
          String pattern = rewrite[0].toString();
          String replacement = rewrite[1].toString();

          Pattern compiledPattern = Pattern.compile(pattern);
          Matcher matcher = compiledPattern.matcher(property.getValue().toString());
          property.setValue(matcher.replaceAll(replacement));
        }
      }
    }
  }

  void map(Property property) throws RepositoryException {
    if (rewriteMapProperties != null &&
        rewriteMapProperties.hasNode(property.getName()) &&
        property.getType() == PropertyType.STRING) {

        String value = property.getString();
        Node map = rewriteMapProperties.getNode(property.getName());
        
        if (map.hasProperty(value) &&
          map.getProperty(value).getType() == PropertyType.STRING &&
          !map.getProperty(value).isMultiple()) {
        property.setValue(map.getProperty(value).getString());
      }
    }
  }

  void consolidate(Node source) throws RepositoryException {
    if (rewriteConsolidateProperties != null) {

      PropertyIterator rules = rewriteConsolidateProperties.getProperties();
      ValueFactory vf = source.getSession().getValueFactory();
      while (rules.hasNext()) {
        Property newProp = rules.nextProperty();
        if (newProp.getDefinition().isProtected()) {
          continue;
        }
        Value[] values;
        if (!newProp.isMultiple()) {
          values = new Value[] { newProp.getValue() };
        } else {
          values = newProp.getValues();
        }
        Value[] newValues = new Value[values.length];
        for (int i = 0; i < values.length; i++) {
          if (!source.hasProperty(values[i].getString())) {
            continue;
          }
          Property property = source.getProperty(values[i].getString());
          newValues[i] = vf.createValue(property.getString());
          property.remove();
        }
        source.setProperty(newProp.getName(), newValues);
      }
    }
  }
}
