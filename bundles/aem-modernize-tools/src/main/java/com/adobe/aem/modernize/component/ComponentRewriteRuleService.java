package com.adobe.aem.modernize.component;

import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * Provides a mechanism for listing all of the configured rules either via Nodes or custom implementations.
 */
public interface ComponentRewriteRuleService {

    /**
     * Lists all of the registered ComponentRewriteRules for processing.
     * @return a list of all component rewrite rules.
     */
    List<ComponentRewriteRule> getRules(ResourceResolver resolver) throws ComponentRewriteException;

    /**
     * Lists all of the sling:resourceType properties identified by the patterns.
     * @return
     */
    Set<String> getSlingResourceTypes(ResourceResolver resolver) throws ComponentRewriteException;
}
