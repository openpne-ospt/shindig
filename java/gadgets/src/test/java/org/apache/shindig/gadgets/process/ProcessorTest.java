/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.shindig.gadgets.process;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.config.ContainerConfig;
import org.apache.shindig.config.JsonContainerConfig;
import org.apache.shindig.expressions.Expressions;
import org.apache.shindig.gadgets.Gadget;
import org.apache.shindig.gadgets.GadgetBlacklist;
import org.apache.shindig.gadgets.GadgetContext;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.GadgetSpecFactory;
import org.apache.shindig.gadgets.features.FeatureRegistry;
import org.apache.shindig.gadgets.features.FeatureRegistryProvider;
import org.apache.shindig.gadgets.spec.GadgetSpec;
import org.apache.shindig.gadgets.variables.VariableSubstituter;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Lists;

import org.apache.shindig.gadgets.variables.Substituter;

public class ProcessorTest {
  private static final Uri SPEC_URL = Uri.parse("http://example.org/gadget.xml");
  private static final Uri TYPE_URL_HREF = Uri.parse("http://example.org/gadget.php");
  private static final String BASIC_HTML_CONTENT = "Hello, World!";
  protected static final String GADGET =
      "<Module>" +
      " <ModulePrefs title='foo'/>" +
      " <Content view='html' type='html'>" + BASIC_HTML_CONTENT + "</Content>" +
      " <Content view='url' type='url' href='" + TYPE_URL_HREF + "'/>" +
      " <Content view='alias' type='html'>" + BASIC_HTML_CONTENT + "</Content>" +
      "</Module>";

  private final FakeGadgetSpecFactory gadgetSpecFactory = new FakeGadgetSpecFactory();
  private final FakeVariableSubstituter substituter = new FakeVariableSubstituter();
  private final FakeBlacklist blacklist = new FakeBlacklist();

  private ContainerConfig containerConfig;
  private Processor processor;

  @Before
  public void setUp() throws Exception {
    JSONObject config = new JSONObject('{' + ContainerConfig.DEFAULT_CONTAINER + ':' +
        "{'gadgets.container': ['default']," +
         "'gadgets.features':{views:" +
           "{aliased: {aliases: ['some-alias', 'alias']}}" +
         "}}}");

    containerConfig = new JsonContainerConfig(config, Expressions.forTesting());
    FeatureRegistryProvider registryProvider = new FeatureRegistryProvider() {
      public FeatureRegistry get(String repository) {
        return null;
      }
    };
    processor = new Processor(gadgetSpecFactory, substituter, containerConfig, blacklist,
        registryProvider);
  }

  private GadgetContext makeContext(final String view, final Uri specUrl, final boolean sanitize) {
    return new GadgetContext() {
      @Override
      public Uri getUrl() {
        if (specUrl == null) {
          return null;
        }
        return specUrl;
      }

      @Override
      public String getView() {
        return view;
      }

      @Override
      public boolean getSanitize() {
        return sanitize;
      }
    };
  }

  private GadgetContext makeContext(final String view) {
    return makeContext(view, SPEC_URL, false);
  }

  @Test
  public void normalProcessing() throws Exception {
    Gadget gadget = processor.process(makeContext("html"));
    assertEquals(BASIC_HTML_CONTENT, gadget.getCurrentView().getContent());
  }

  @Test(expected = ProcessingException.class)
  public void handlesGadgetExceptionGracefully() throws Exception {
    gadgetSpecFactory.exception = new GadgetException(GadgetException.Code.INVALID_PATH);
    processor.process(makeContext("url"));
  }

  @Test
  public void doViewAliasing() throws Exception {
    Gadget gadget = processor.process(makeContext("aliased"));
    assertEquals(BASIC_HTML_CONTENT, gadget.getCurrentView().getContent());
  }

  @Test
  public void noSupportedViewHasNoCurrentView() throws Exception {
    Gadget gadget = processor.process(makeContext("not-real-view"));
    assertNull(gadget.getCurrentView());
  }

  @Test
  public void substitutionsPerformedTypeHtml() throws Exception {
    processor.process(makeContext("html"));
    assertTrue("Substitutions not performed", substituter.wasSubstituted);
  }

  @Test
  public void substitutionsPerformedTypeUrl() throws Exception {
    processor.process(makeContext("url"));
    assertTrue("Substitutions not performed", substituter.wasSubstituted);
  }

  @Test
  public void blacklistChecked() throws Exception {
    processor.process(makeContext("url"));
    assertTrue("Blacklist not checked", blacklist.wasChecked);
  }

  @Test
  public void blacklistedGadgetThrows() throws Exception {
    blacklist.isBlacklisted = true;
    try {
      processor.process(makeContext("html"));
      fail("expected ProcessingException");
    } catch (ProcessingException e) {
      assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getHttpStatusCode());
    }

  }

  @Test
  public void nullUrlThrows() throws Exception {
    try {
      processor.process(makeContext("html", null, false));
      fail("expected ProcessingException");
    } catch (ProcessingException e) {
      assertEquals(HttpServletResponse.SC_BAD_REQUEST, e.getHttpStatusCode());
    }
  }

  @Test
  public void nonHttpOrHttpsThrows() throws Exception {
    try {
      processor.process(makeContext("html", Uri.parse("file://foo"), false));
      fail("expected ProcessingException");
    } catch (ProcessingException e) {
      assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getHttpStatusCode());
    }
  }

  @Test
  public void typeUrlViewsAreSkippedForSanitizedGadget() throws Exception {
    Gadget gadget = processor.process(makeContext("url", SPEC_URL, true));
    assertNull(gadget.getCurrentView());
    gadget = processor.process(makeContext("html", SPEC_URL, true));
    assertEquals(BASIC_HTML_CONTENT, gadget.getCurrentView().getContent());
  }

  private static class FakeBlacklist implements GadgetBlacklist {
    protected boolean wasChecked;
    protected boolean isBlacklisted;

    protected FakeBlacklist() {
    }

    public boolean isBlacklisted(Uri gadgetUri) {
      wasChecked = true;
      return isBlacklisted;
    }
  }

  private static class FakeGadgetSpecFactory implements GadgetSpecFactory {
    protected GadgetException exception;

    protected FakeGadgetSpecFactory() {
    }

    public GadgetSpec getGadgetSpec(GadgetContext context) throws GadgetException {
      if (exception != null) {
        throw exception;
      }
      return new GadgetSpec(context.getUrl(), GADGET);
    }
  }

  private static class FakeVariableSubstituter extends VariableSubstituter {
    protected boolean wasSubstituted;

    protected FakeVariableSubstituter() {
      super(Lists.<Substituter>newArrayList());
    }

    @Override
    public GadgetSpec substitute(GadgetContext context, GadgetSpec spec) {
      wasSubstituted = true;
      return spec;
    }
  }
}
