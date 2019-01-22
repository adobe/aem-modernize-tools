package com.adobe.aem.modernize;

import java.util.List;

import org.apache.sling.api.resource.ResourceResolver;

public interface RewriteRuleService<T extends RewriteRule> {

    /**
     * Lists all of the registered ComponentRewriteRules for processing.
     * @return a list of all component rewrite rules.
     */
    List<T> getRules(ResourceResolver resolver) throws RewriteException;

}
