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
package com.adobe.aem.modernize.dialog;

import com.adobe.aem.modernize.RewriteRule;

/**
 * Interface for services that implement a dialog rewrite rule. A rewrite rule matches certain subtrees of the
 * dialog tree (usually corresponding to one dialog component) and rewrites (i.e. modifies or replaces) them.
 */
public interface DialogRewriteRule extends RewriteRule {

}
