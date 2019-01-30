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
package com.adobe.aem.modernize.design.impl;

public class PoliciesImportUtils {

    private static final String LEGACY_DESIGNS = "/etc/designs/";

    public static final String PN_IMPORTED = "cq:imported";

    public static String getDesignPath(String designPath) {
        if (designPath.startsWith(LEGACY_DESIGNS)) {
            designPath = designPath.substring(LEGACY_DESIGNS.length());
        }
        return designPath;
    }

}
