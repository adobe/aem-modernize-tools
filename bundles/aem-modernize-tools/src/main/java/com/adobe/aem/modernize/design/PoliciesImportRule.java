/*************************************************************************
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 * Copyright 2019 Adobe
 * All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of Adobe and its suppliers, if any. The intellectual
 * and technical concepts contained herein are proprietary to Adobe
 * and its suppliers and are protected by all applicable intellectual
 * property laws, including trade secret and copyright laws.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe.
 **************************************************************************/
package com.adobe.aem.modernize.design;

import com.adobe.aem.modernize.RewriteRule;

import javax.jcr.RepositoryException;
import java.util.Set;

/**
 * Interface for services that implement a Design rewrite rule.
 */
public interface PoliciesImportRule extends RewriteRule {

    /**
     * Returns a set of all <code>sling:resourceType</code> values specified in the <i>pattern</i> properties.
     * @return
     */
    Set<String> getPatternSlingResourceTypes() throws RepositoryException;

    /**
     * The replacement <code>sling:resourceType</code> specified on the replacement node.
     * @return
     * @throws RepositoryException
     */
    String getReplacementSlingResourceType() throws RepositoryException;

}
