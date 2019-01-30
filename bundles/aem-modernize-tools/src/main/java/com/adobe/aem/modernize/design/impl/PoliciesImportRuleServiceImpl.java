package com.adobe.aem.modernize.design.impl;

import com.adobe.aem.modernize.design.PoliciesImportRule;
import com.adobe.aem.modernize.design.PoliciesImportRuleService;
import com.adobe.aem.modernize.design.impl.rules.NodeBasedPoliciesImportRule;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Component
@Service
public class PoliciesImportRuleServiceImpl implements PoliciesImportRuleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesImportRuleService.class);

    /**
     * Relative path to the node containing node-based policy import rules
     */
    private static final String RULES_SEARCH_PATH = "cq/modernize/design/rules";

    /**
     * Keeps track of OSGi services implementing policy import rules
     */
    @Reference(
            referenceInterface = PoliciesImportRule.class,
            cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindRule",
            unbind = "unbindRule"
    )
    private List<PoliciesImportRule> rules = Collections.synchronizedList(new LinkedList<>());

    @SuppressWarnings("unused")
    public void bindRule(PoliciesImportRule rule) {
        rules.add(rule);
    }

    @SuppressWarnings("unused")
    public void unbindRule(PoliciesImportRule rule) {
        rules.remove(rule);
    }

    public List<PoliciesImportRule> getRules(ResourceResolver resolver) throws RepositoryException {
        final List<PoliciesImportRule> rules = new LinkedList<>();

        // Get rules provided as OSGi services (we need to synchronize, since the 'addAll' will iterate over 'rules')
        synchronized (this.rules) {
            rules.addAll(this.rules);
        }
        int jb = rules.size();

        // Get node-based rules
        Resource resource = resolver.getResource(RULES_SEARCH_PATH);
        if (resource != null) {
            Node rulesContainer = resource.adaptTo(Node.class);
            NodeIterator iterator = rulesContainer.getNodes();
            while (iterator.hasNext()) {
                Node nextNode = iterator.nextNode();
                if (isFolder(nextNode)) {
                    // add first level folder rules
                    NodeIterator nodeIterator = nextNode.getNodes();
                    while (nodeIterator.hasNext()) {
                        Node nestedNode = nodeIterator.nextNode();
                        // don't include nested folders
                        if (!isFolder(nestedNode)) {
                            rules.add(new NodeBasedPoliciesImportRule(nestedNode));
                        }
                    }
                } else {
                    // add rules directly at the rules search path
                    rules.add(new NodeBasedPoliciesImportRule(nextNode));
                }
            }
        }

        // sort rules according to their ranking
        Collections.sort(rules, new PoliciesImportRuleServiceImpl.RuleComparator());

        LOGGER.debug("Found {} rules ({} Java-based, {} node-based)", rules.size(), jb, rules.size() - jb);
        if (LOGGER.isDebugEnabled()) {
            for (PoliciesImportRule rule : rules) {
                LOGGER.debug(rule.toString());
            }
        }

        return rules;
    }

//    @Override
    public Set<String> getSlingResourceTypes(ResourceResolver resolver) throws RepositoryException {
        List<PoliciesImportRule> rules = getRules(resolver);

        Set<String> types = new HashSet<>(rules.size());

        for (PoliciesImportRule r : rules) {
            types.addAll(r.getSlingResourceTypes());
        }
        return types;
    }

    private class RuleComparator implements Comparator<PoliciesImportRule> {

        public int compare(PoliciesImportRule rule1, PoliciesImportRule rule2) {
            int ranking1 = rule1.getRanking();
            int ranking2 = rule2.getRanking();
            return Double.compare(ranking1, ranking2);
        }

    }

    private boolean isFolder(Node node) throws RepositoryException {
        String primaryType = node.getPrimaryNodeType().getName();

        return primaryType.equals("sling:Folder")
                || primaryType.equals("sling:OrderedFolder")
                || primaryType.equals(JcrConstants.NT_FOLDER);
    }

}
