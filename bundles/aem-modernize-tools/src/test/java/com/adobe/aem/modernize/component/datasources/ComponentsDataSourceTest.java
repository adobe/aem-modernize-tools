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
package com.adobe.aem.modernize.component.datasources;

import com.adobe.aem.modernize.component.ComponentRewriteRuleService;
import com.adobe.aem.modernize.component.impl.ComponentRewriteRuleServiceImpl;
import com.adobe.granite.ui.components.ExpressionResolver;
import com.adobe.granite.ui.components.ds.DataSource;
import com.day.cq.commons.Externalizer;

import org.apache.commons.collections.IteratorUtils;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ComponentsDataSourceTest {

    private static final String COMPONENTS_ROOT = "/libs/cq/modernize/component/content";
    private static final String ITEM_RESOURCE_TYPE = "cq/modernize/component/components/item";
    private static final String RULES_ROOT = "/libs/cq/modernize/component/rules";

    private ComponentsDataSource componentsDataSource;
    private ComponentRewriteRuleService componentRewriteRuleService;

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



    @Before
    public void setUp() throws IOException, RepositoryException, ParseException {
        ResourceResolver resolver = context.resourceResolver();

        Session adminSession = resolver.adaptTo(Session.class);
        RepositoryUtil.registerNodeType(adminSession, getClass().getResourceAsStream("/nodetypes/nodetypes.cnd"));

        context.load().json("/component/test-content.json", COMPONENTS_ROOT);
        context.load().json("/component/test-rules.json", RULES_ROOT);

        // register mock services
        context.registerService(Externalizer.class, externalizer);

        // register data source
        componentsDataSource = context.registerService(ComponentsDataSource.class, new ComponentsDataSource());

        // register rule service;
        componentRewriteRuleService = context.registerService(ComponentRewriteRuleService.class, new ComponentRewriteRuleServiceImpl());

        // mock request
        Mockito.stub(request.getLocale()).toReturn(Locale.US);
        Mockito.stub(request.getResource()).toReturn(requestResource);
        Mockito.stub(request.getResourceResolver()).toReturn(resolver);
        Mockito.stub(request.getRequestPathInfo()).toReturn(requestPathInfo);

        // prepare externalizer
        Whitebox.setInternalState(componentsDataSource, "externalizer", externalizer);

        // prepare rewrite rule service
        Whitebox.setInternalState(componentsDataSource, "componentRewriteRuleService", componentRewriteRuleService);

    }

    @Test
    public void testDoGet() throws Exception {
        // prepare request resource
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("path", COMPONENTS_ROOT);
        properties.put("itemResourceType", ITEM_RESOURCE_TYPE);
        Mockito.stub(requestResource.getValueMap()).toReturn(new ValueMapDecorator(properties));

        // prepare expression resolver
        Mockito.stub(expressionResolver.resolve(COMPONENTS_ROOT, Locale.US, String.class, request)).toReturn(COMPONENTS_ROOT);
        Whitebox.setInternalState(componentsDataSource, "expressionResolver", expressionResolver);

        List<String> expectedComponentPaths = new ArrayList<>();
        expectedComponentPaths.addAll(Arrays.asList(
            COMPONENTS_ROOT + "/simple",
            COMPONENTS_ROOT + "/copyChildren",
            COMPONENTS_ROOT + "/rewriteOptional",
            COMPONENTS_ROOT + "/mapProperties",
            COMPONENTS_ROOT + "/rewriteProperties",
            COMPONENTS_ROOT + "/rewriteMapChildren",
            COMPONENTS_ROOT + "/copyChildrenOrder",
            COMPONENTS_ROOT + "/level1/simple",
            COMPONENTS_ROOT + "/level1/mapProperties",
            COMPONENTS_ROOT + "/level1/rewriteProperties",
            COMPONENTS_ROOT + "/level1/rewriteMapChildren"
        ));

        componentsDataSource.doGet(request, response);

        ArgumentCaptor<DataSource> dataSourceArgumentCaptor = ArgumentCaptor.forClass(DataSource.class);
        Mockito.verify(request).setAttribute(Mockito.anyString(), dataSourceArgumentCaptor.capture());

        DataSource componentsDataSource = dataSourceArgumentCaptor.getValue();

        @SuppressWarnings("unchecked")
        List<Resource> componentsList = IteratorUtils.toList(componentsDataSource.iterator());

        assertEquals(expectedComponentPaths.size(), componentsList.size());

        for (Resource componentResource : componentsList) {
            ValueMap valueMap = componentResource.getValueMap();

            // expected properties
            assertNotNull(valueMap.get("componentPath"));
            assertNotNull(valueMap.get("resourceType"));
            assertNotNull(valueMap.get("href"));
            assertNotNull(valueMap.get("crxHref"));

            expectedComponentPaths.remove(valueMap.get("componentPath"));
        }

        assertEquals(0, expectedComponentPaths.size());
    }


    @Test
    public void testDoSingleGet() throws Exception {

        // prepare request resource
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("path", COMPONENTS_ROOT + "/simple");
        properties.put("itemResourceType", ITEM_RESOURCE_TYPE);
        Mockito.stub(requestResource.getValueMap()).toReturn(new ValueMapDecorator(properties));

        // prepare expression resolver
        Mockito.stub(expressionResolver.resolve(COMPONENTS_ROOT + "/simple", Locale.US, String.class, request)).toReturn(COMPONENTS_ROOT + "/simple");
        Whitebox.setInternalState(componentsDataSource, "expressionResolver", expressionResolver);

        List<String> expectedComponentPaths = new ArrayList<>();
        expectedComponentPaths.addAll(Arrays.asList(
                COMPONENTS_ROOT + "/simple"
        ));

        componentsDataSource.doGet(request, response);

        ArgumentCaptor<DataSource> dataSourceArgumentCaptor = ArgumentCaptor.forClass(DataSource.class);
        Mockito.verify(request).setAttribute(Mockito.anyString(), dataSourceArgumentCaptor.capture());

        DataSource componentsDataSource = dataSourceArgumentCaptor.getValue();

        @SuppressWarnings("unchecked")
        List<Resource> componentsList = IteratorUtils.toList(componentsDataSource.iterator());

        assertEquals(expectedComponentPaths.size(), componentsList.size());

        for (Resource componentResource : componentsList) {
            ValueMap valueMap = componentResource.getValueMap();

            // expected properties
            assertNotNull(valueMap.get("componentPath"));
            assertNotNull(valueMap.get("resourceType"));
            assertNotNull(valueMap.get("href"));
            assertNotNull(valueMap.get("crxHref"));

            expectedComponentPaths.remove(valueMap.get("componentPath"));
        }

        assertEquals(0, expectedComponentPaths.size());
    }

}
