/*
 * AEM Modernize Tools
 *
 * Copyright (c) 2019 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
package com.adobe.aem.modernize.policy;

import com.adobe.aem.modernize.rule.ServiceBasedRewriteRule;
import com.day.cq.wcm.api.designer.Design;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Interface for services that implement a Design rewrite rule.
 */
@ConsumerType
public interface PolicyImportRule extends ServiceBasedRewriteRule {

  /**
   * This method is used by services consuming this rule to set the root Policy location before the RewriteRules are applied.
   *
   * This method may be called at any time before the {@link #applyTo} method is called.
   *
   * @param destination the Design in which to save the new Policies
   */
  void setTargetDesign(Design destination);

}
