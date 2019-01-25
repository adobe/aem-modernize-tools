package com.adobe.aem.modernize.structure.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.structure.PageStructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Service
public class StructureRewriteRuleServiceImpl implements StructureRewriteRuleService {


    private Logger logger = LoggerFactory.getLogger(StructureRewriteRuleServiceImpl.class);

    /**
     * Keeps track of OSGi services implementing structure rewrite rules
     */
    @Reference(
            referenceInterface = StructureRewriteRule.class,
            cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindRule",
            unbind = "unbindRule"
    )
    private List<StructureRewriteRule> rules = Collections.synchronizedList(new LinkedList<>());

    /**
     * Keeps track of OSGi services implementing structure rewrite rules
     */
    @Reference(
            referenceInterface = PageStructureRewriteRule.class,
            cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindPageRule",
            unbind = "unbindPageRule"
    )
    private List<PageStructureRewriteRule> pageRules = Collections.synchronizedList(new LinkedList<>());


    @SuppressWarnings("unused")
    public void bindRule(StructureRewriteRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(StructureRewriteRule rule) {
        rules.remove(rule);
    }

    @SuppressWarnings("unused")
    public void bindPageRule(PageStructureRewriteRule rule) {
        pageRules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindPageRule(PageStructureRewriteRule rule) {
        pageRules.remove(rule);
    }

    @Override
    public Set<String> getTemplates() {
        Set<String> templates = new HashSet<>(pageRules.size());
        for (PageStructureRewriteRule r : pageRules) {
            templates.add(r.getStaticTemplate());
        }
        return templates;
    }

    @Override
    public List<StructureRewriteRule> getRules(ResourceResolver resolver) {
        List<StructureRewriteRule> rulesCopy = new LinkedList<>();
        Iterator<StructureRewriteRule> it = rules.iterator();
        while (it.hasNext()) {
            rulesCopy.add(it.next());
        }

        return rulesCopy;
    }
}
