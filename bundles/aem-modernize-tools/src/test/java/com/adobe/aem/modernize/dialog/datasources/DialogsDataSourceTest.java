/*
 *  (c) 2017 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.aem.modernize.dialog.datasources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

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
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class DialogsDataSourceTest {

    private static final String DIALOGS_ROOT = "/libs/cq/modernize/content/dialogs";
    private static final String ITEM_RESOURCE_TYPE = "cq/modernize/dialog/content/item";

    private DialogsDataSource dialogsDataSource;

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

        context.load().json("/dialog/test-dialogs.json", DIALOGS_ROOT);

        // register mock services
        context.registerService(Externalizer.class, externalizer);

        // register data source
        dialogsDataSource = context.registerService(DialogsDataSource.class, new DialogsDataSource());

        // prepare request resource
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put("path", DIALOGS_ROOT);
        properties.put("itemResourceType", ITEM_RESOURCE_TYPE);
        Mockito.stub(requestResource.getValueMap()).toReturn(new ValueMapDecorator(properties));

        // mock request
        Mockito.stub(request.getLocale()).toReturn(Locale.US);
        Mockito.stub(request.getResource()).toReturn(requestResource);
        Mockito.stub(request.getResourceResolver()).toReturn(resolver);
        Mockito.stub(request.getRequestPathInfo()).toReturn(requestPathInfo);

        // prepare externalizer
        Whitebox.setInternalState(dialogsDataSource, "externalizer", externalizer);

        // prepare expression resolver
        Mockito.stub(expressionResolver.resolve(DIALOGS_ROOT, Locale.US, String.class, request)).toReturn(DIALOGS_ROOT);
        Whitebox.setInternalState(dialogsDataSource, "expressionResolver", expressionResolver);
    }

    @Test
    public void testDoGet() throws Exception {
        List<String> expectedDialogPaths = new ArrayList<String>();
        expectedDialogPaths.addAll(Arrays.asList(
            DIALOGS_ROOT + "/classic/dialog",
            DIALOGS_ROOT + "/classic/design_dialog",
            DIALOGS_ROOT + "/coral2/cq:dialog",
            DIALOGS_ROOT + "/coral2/cq:design_dialog",
            DIALOGS_ROOT + "/level1/classicandcoral2/cq:dialog",
            DIALOGS_ROOT + "/level1/classicandcoral2/cq:design_dialog",
            DIALOGS_ROOT + "/level1/converted/dialog",
            DIALOGS_ROOT + "/level1/converted/cq:design_dialog.coral2",
            DIALOGS_ROOT + "/level1/coral2andbackup/cq:dialog"));

        dialogsDataSource.doGet(request, response);

        ArgumentCaptor<DataSource> dataSourceArgumentCaptor = ArgumentCaptor.forClass(DataSource.class);
        Mockito.verify(request).setAttribute(Mockito.anyString(), dataSourceArgumentCaptor.capture());

        DataSource dialogsDataSource = dataSourceArgumentCaptor.getValue();

        @SuppressWarnings("unchecked")
        List<Resource> dialogsList = IteratorUtils.toList(dialogsDataSource.iterator());

        assertEquals(expectedDialogPaths.size(), dialogsList.size());

        for (Resource dialogResource : dialogsList) {
            ValueMap valueMap = dialogResource.getValueMap();

            // expected properties
            assertNotNull(valueMap.get("dialogPath"));
            assertNotNull(valueMap.get("type"));
            assertNotNull(valueMap.get("href"));
            assertNotNull(valueMap.get("converted"));
            assertNotNull(valueMap.get("crxHref"));

            expectedDialogPaths.remove(valueMap.get("dialogPath"));
        }

        assertEquals(0, expectedDialogPaths.size());
    }
}
