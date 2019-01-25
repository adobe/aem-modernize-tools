package com.adobe.aem.modernize.structure;

import java.util.Set;

import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.RewriteRuleService;
import com.adobe.aem.modernize.component.ComponentRewriteRule;

/**
 * Provides a mechanism for listing all of the configured rules either via Nodes or custom implementations.
 */
public interface StructureRewriteRuleService extends RewriteRuleService<StructureRewriteRule> {

    /**
     * Lists all of the cq:template properties identified by the patterns.
     * @return
     */
    Set<String> getTemplates();
}
