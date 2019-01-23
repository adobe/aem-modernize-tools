package com.adobe.aem.modernize.template.impl.rules;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;

import com.adobe.aem.modernize.RewriteException;
import com.adobe.aem.modernize.RewriteRule;

/**
 * Rewrites Components of type <code>foundation/components/parsys</code> to the Responsive Layout container.
 */
@Component
@Service
public class ParsysRewriteRule implements RewriteRule {
    @Override
    public boolean matches(Node root) throws RepositoryException {
        return false;
    }

    @Override
    public Node applyTo(Node root, Set<Node> finalNodes) throws RewriteException, RepositoryException {
        return null;
    }
}
