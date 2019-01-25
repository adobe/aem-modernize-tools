package com.adobe.aem.modernize.component;

import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.RewriteRuleService;

/**
 * Provides a mechanism for listing all of the configured rules either via Nodes or custom implementations.
 */
public interface ComponentRewriteRuleService extends RewriteRuleService<ComponentRewriteRule> {

    /**
     * Lists all of the sling:resourceType properties identified by the patterns.
     * @return
     */
    Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException;
}
