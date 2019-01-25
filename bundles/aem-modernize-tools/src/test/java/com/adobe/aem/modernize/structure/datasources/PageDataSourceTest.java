/*
 *  (c) 2019 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.structure.datasources;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.jcr.Session;

import org.apache.commons.collections.IteratorUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import com.adobe.aem.modernize.structure.StructureRewriteRule;
import com.adobe.aem.modernize.structure.StructureRewriteRuleService;
import com.adobe.aem.modernize.structure.impl.rules.PageRewriteRule;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.day.cq.commons.Externalizer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class PageDataSourceTest {

    private static final String PAGE_ROOT = "/libs/cq/modernize/component/content";
    private static final String ITEM_RESOURCE_TYPE = "cq/modernize/component/structure/item";

    private static final String STATIC_HOME_TEMPLATE = "/apps/geometrixx/templates/homepage";
    private static final String STATIC_PRODUCT_TEMPLATE = "/apps/geometrixx/templates/productpage";
    private static final String EDITABLE_TEMPLATE = "/conf/geodemo/settings/wcm/templates/geometrixx-demo-home-page/structure";

    private PageDataSource pageDataSource;

    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    @Mock
    private Resource requestResource;

    @Mock
    private RequestPathInfo requestPathInfo;

    @Mock
    private ExpressionResolver expressionResolver;

    @Mock
    private Externalizer externalizer;

    @Mock
    private StructureRewriteRuleService structureRewriteRuleService;

    private BundleContext bundleContext;


    @Before
    public void setUp() throws Exception {
        bundleContext = MockOsgi.newBundleContext();

        ResourceResolver resolver = context.resourceResolver();

        List<StructureRewriteRule> rules = new LinkedList<>();
        Set<String> templates = new HashSet<>();
        templates.add(STATIC_HOME_TEMPLATE);
        templates.add(STATIC_PRODUCT_TEMPLATE);

        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/structure/test-content.json", PAGE_ROOT);

        // register data source
        pageDataSource = context.registerService(PageDataSource.class, new PageDataSource());


        // mock request
        Mockito.stub(request.getLocale()).toReturn(Locale.US);
        Mockito.stub(request.getResource()).toReturn(requestResource);
        Mockito.stub(request.getResourceResolver()).toReturn(resolver);
        Mockito.stub(request.getRequestPathInfo()).toReturn(requestPathInfo);

        StructureRewriteRule rule = new PageRewriteRule();
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("static.template", STATIC_HOME_TEMPLATE);
        props.put("editable.template", EDITABLE_TEMPLATE);
        MockOsgi.activate(rule, bundleContext, props);
        rules.add(rule);

        rule = new PageRewriteRule();
        props = new Hashtable<>();
        props.put("static.template", STATIC_PRODUCT_TEMPLATE);
        props.put("editable.template", "/conf/geodemo/settings/wcm/templates/geometrixx-demo-product-page/structure");
        MockOsgi.activate(rule, bundleContext, props);
        rules.add(rule);

        // register mock services

        bundleContext.registerService(ExpressionResolver.class, expressionResolver, new Hashtable<>());
        bundleContext.registerService(Externalizer.class, externalizer, new Hashtable<>());
        bundleContext.registerService(StructureRewriteRuleService.class, structureRewriteRuleService, new Hashtable<>());

        Mockito.stub(structureRewriteRuleService.getTemplates()).toReturn(templates);
        Mockito.stub(structureRewriteRuleService.getRules(resolver)).toReturn(rules);

        // inject dependencies
        MockOsgi.injectServices(pageDataSource, bundleContext);

        // prepare request resource
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("path", PAGE_ROOT);
        properties.put("itemResourceType", ITEM_RESOURCE_TYPE);
        Mockito.stub(requestResource.getValueMap()).toReturn(new ValueMapDecorator(properties));

        // prepare expression resolver
        Mockito.stub(expressionResolver.resolve(PAGE_ROOT, Locale.US, String.class, request)).toReturn(PAGE_ROOT);

    }

    @Test
    public void testDoGet() throws Exception {
        List<String> expectedPagePaths = new ArrayList<>();
        expectedPagePaths.add(PAGE_ROOT + "/matches/jcr:content");
        expectedPagePaths.add(PAGE_ROOT + "/fourColumns/jcr:content");

        pageDataSource.doGet(request, response);

        ArgumentCaptor<DataSource> dataSourceArgumentCaptor = ArgumentCaptor.forClass(DataSource.class);
        Mockito.verify(request).setAttribute(Mockito.anyString(), dataSourceArgumentCaptor.capture());

        DataSource pageDataSource = dataSourceArgumentCaptor.getValue();

        @SuppressWarnings("unchecked")
        List<Resource> pageList = IteratorUtils.toList(pageDataSource.iterator());

        assertEquals(expectedPagePaths.size(), pageList.size());

        for (Resource componentResource : pageList) {
            ValueMap valueMap = componentResource.getValueMap();

            // expected properties
            assertNotNull(valueMap.get("title"));
            assertNotNull(valueMap.get("pagePath"));
            assertNotNull(valueMap.get("templateType"));
            assertNotNull(valueMap.get("href"));
            assertNotNull(valueMap.get("crxHref"));

            expectedPagePaths.remove(valueMap.get("pagePath"));
        }

        assertEquals(0, expectedPagePaths.size());
    }
}