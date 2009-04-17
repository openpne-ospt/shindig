/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets.render;

import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.gadgets.Gadget;
import org.apache.shindig.gadgets.parse.caja.CajaCssSanitizer;
import org.apache.shindig.gadgets.rewrite.ContentRewriterFeature;
import org.apache.shindig.gadgets.rewrite.ContentRewriterFeatureFactory;
import org.apache.shindig.gadgets.rewrite.ContentRewriterUris;
import org.apache.shindig.gadgets.rewrite.GadgetRewriter;
import org.apache.shindig.gadgets.rewrite.LinkRewriter;
import org.apache.shindig.gadgets.rewrite.MutableContent;
import org.apache.shindig.gadgets.rewrite.ProxyingLinkRewriter;
import org.apache.shindig.gadgets.servlet.ProxyBase;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;

/**
 * A content rewriter that will sanitize output for simple 'badge' like display.
 *
 * This is intentionally not as robust as Caja. It is a simple element whitelist. It can not be used
 * for sanitizing either javascript or CSS. CSS is desired in the long run, but it can't be proven
 * safe in the short term.
 *
 * Generally used in conjunction with a gadget that gets its dynamic behavior externally (proxied
 * rendering, OSML, etc.)
 */
public class SanitizingGadgetRewriter implements GadgetRewriter {
  private static final Set<String> URI_ATTRIBUTES = ImmutableSet.of("href", "src");

  /** Key stored as element user-data to bypass sanitization */
  private static final String BYPASS_SANITIZATION_KEY = "shindig.bypassSanitization";
  
  /** Attributes to forcibly rewrite and require an image mime type */
  private static final Map<String, ImmutableSet<String>> PROXY_IMAGE_ATTRIBUTES =
      ImmutableMap.of("img", ImmutableSet.of("src"));

  /**
   * Is the Gadget to be rendered sanitized?
   * @return true if sanitization will be enabled
   */
  public static boolean isSanitizedRenderingRequest(Gadget gadget) {
    return ("1".equals(gadget.getContext().getParameter("sanitize")));
  }
  
  /**
   * Marks that an element and all its attributes are trusted content.
   * This status is preserved across {@link Node#cloneNode} calls.  Be
   * extremely careful when using this, especially with {@code includingChildren}
   * set to {@code true}, as untrusted content that gets inserted (e.g, via
   * os:RenderAll in templating) would become trusted.
   * 
   * @param element the trusted element
   * @param includingChildren if true, children of this element will are also
   *     trusted.  Never set this to true on an element that will ever have
   *     untrusted children inserted (e.g., if it contains or may contain os:Render).
   */
  public static void bypassSanitization(Element element, boolean includingChildren) {
    element.setUserData(BYPASS_SANITIZATION_KEY,
        includingChildren ? Bypass.ALL : Bypass.ONLY_SELF, copyOnClone);
  }
  
  private static enum Bypass { ALL, ONLY_SELF, NONE };
  private static UserDataHandler copyOnClone = new UserDataHandler() {
    public void handle(short operation, String key, Object data, Node src, Node dst) {
      if (operation == NODE_CLONED) {
        dst.setUserData(key, data, copyOnClone);
      }
    }
  };
  
  private final Set<String> allowedTags;
  private final Set<String> allowedAttributes;
  private final CajaCssSanitizer cssSanitizer;
  private final ContentRewriterFeatureFactory rewriterFeatureFactory;
  private final ContentRewriterUris rewriterUris;

  @Inject
  public SanitizingGadgetRewriter(@AllowedTags Set<String> allowedTags,
      @AllowedAttributes Set<String> allowedAttributes,
      ContentRewriterFeatureFactory rewriterFeatureFactory,
      ContentRewriterUris rewriterUris,
      CajaCssSanitizer cssSanitizer) {
    this.allowedTags = allowedTags;
    this.allowedAttributes = allowedAttributes;
    this.rewriterUris = rewriterUris;
    this.cssSanitizer = cssSanitizer;
    this.rewriterFeatureFactory = rewriterFeatureFactory;
  }


  public void rewrite(Gadget gadget, MutableContent content) {
    if (gadget.sanitizeOutput()) {
      boolean sanitized = false;
      try {
        new NodeSanitizer(gadget).sanitize(content.getDocument().getDocumentElement());
        content.documentChanged();
        sanitized = true;
      } finally {
        // Defensively clean the content in case of failure
        if (!sanitized) {
          content.setContent("");
        }
      }
    }
  }

  /**
   * Utiliity class to sanitize HTML nodes recursively.
   */
  class NodeSanitizer {

    private final LinkRewriter cssRewriter;
    private final LinkRewriter imageRewriter;
    private final Uri context;

    NodeSanitizer(Gadget gadget) {
      this.context = gadget.getSpec().getUrl();
      Integer expires = rewriterFeatureFactory.getDefault().getExpires();
      ContentRewriterFeature rewriterFeature =
          rewriterFeatureFactory.createRewriteAllFeature(expires == null ? -1 : expires);

      String proxyBaseNoGadget = rewriterUris.getProxyBase(gadget.getContext().getContainer());
      cssRewriter = new SanitizingProxyingLinkRewriter(gadget.getSpec().getUrl(),
          rewriterFeature, proxyBaseNoGadget, "text/css");
      imageRewriter = new SanitizingProxyingLinkRewriter(gadget.getSpec().getUrl(),
          rewriterFeature, proxyBaseNoGadget, "image/*");
    }

    private void sanitize(Node node) {
      switch (node.getNodeType()) {
        case Node.CDATA_SECTION_NODE:
        case Node.TEXT_NODE:
        case Node.ENTITY_REFERENCE_NODE:
          break;
        case Node.ELEMENT_NODE:
        case Node.DOCUMENT_NODE:
          Element element = (Element) node;
          Bypass bypass = canBypassSanitization(element);
          if (bypass == Bypass.ALL) {
            return;
          } else if (bypass == Bypass.ONLY_SELF) {
            for (Node child : toList(node.getChildNodes())) {
              sanitize(child);
            }
          } else if (allowedTags.contains(element.getTagName().toLowerCase())) {
            // TODO - Add special case for stylesheet LINK nodes.
            // Special case handling for style nodes
            if (element.getTagName().equalsIgnoreCase("style")) {
              cssSanitizer.sanitize(element, context, cssRewriter);
            }
            filterAttributes(element);
            for (Node child : toList(node.getChildNodes())) {
              sanitize(child);
            }
          } else {
            node.getParentNode().removeChild(node);
          }
          break;
        case Node.COMMENT_NODE:
        default:
          // Must remove all comments to avoid conditional comment evaluation.
          // There might be other, unknown types as well. Don't trust them.
          node.getParentNode().removeChild(node);
          break;
      }
    }

    private void filterAttributes(Element element) {
      Set<String> rewriteImageAttrs = PROXY_IMAGE_ATTRIBUTES.get(element.getNodeName().toLowerCase());
      for (Attr attribute : toList(element.getAttributes())) {
        String name = attribute.getNodeName().toLowerCase();
        if (allowedAttributes.contains(name)) {
          if (URI_ATTRIBUTES.contains(name)) {
            try {
              Uri uri = Uri.parse(attribute.getNodeValue());
              String scheme = uri.getScheme();
              if (!isAllowedScheme(scheme)) {
                element.removeAttributeNode(attribute);
              } else if (rewriteImageAttrs != null && rewriteImageAttrs.contains(name)) {
                // Force rewrite the src of the image through the proxy. This is necessary
                // because IE will run arbitrary script in files referenced from src
                attribute.setValue(imageRewriter.rewrite(attribute.getNodeValue(), context));
              }
            } catch (IllegalArgumentException e) {
              // Not a valid URI.
              element.removeAttributeNode(attribute);
            }
          }
        } else {
          element.removeAttributeNode(attribute);
        }
      }
    }
  }


  /** Convert a NamedNodeMap to a list for easy and safe operations */
  private static List<Attr> toList(NamedNodeMap nodes) {
    List<Attr> list = new ArrayList<Attr>(nodes.getLength());

    for (int i = 0, j = nodes.getLength(); i < j; ++i) {
      list.add((Attr) nodes.item(i));
    }

    return list;
  }

  private static Bypass canBypassSanitization(Element element) {
    Bypass bypass = (Bypass) element.getUserData(BYPASS_SANITIZATION_KEY);
    if (bypass == null) {
      bypass = Bypass.NONE;
    }
    return bypass;
  }

  /** Convert a NamedNodeMap to a list for easy and safe operations */
  private static List<Node> toList(NodeList nodes) {
    List<Node> list = new ArrayList<Node>(nodes.getLength());

    for (int i = 0, j = nodes.getLength(); i < j; ++i) {
      list.add(nodes.item(i));
    }

    return list;
  }

  private static boolean isAllowedScheme(String scheme) {
    return scheme == null || scheme.equals("http") || scheme.equals("https");
  }

  /**
   * Forcible rewrite the link through the proxy and force sanitization with
   * an expected mime type
   */
  static class SanitizingProxyingLinkRewriter extends ProxyingLinkRewriter {

    private final String expectedMime;

    SanitizingProxyingLinkRewriter(Uri gadgetUri, ContentRewriterFeature rewriterFeature,
        String prefix, String expectedMime) {
      super(gadgetUri, rewriterFeature, prefix);
      this.expectedMime = expectedMime;
    }

    @Override
    public String rewrite(String link, Uri context) {
      try {
        Uri.parse(link);
      } catch (RuntimeException re) {
        // Any failure in parse
        return "about:blank";
      }
      String rewritten = super.rewrite(link, context);
      rewritten += '&' + ProxyBase.SANITIZE_CONTENT_PARAM + "=1";
      rewritten += '&' + ProxyBase.REWRITE_MIME_TYPE_PARAM + '=' + expectedMime;
      return rewritten;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @BindingAnnotation
  public @interface AllowedTags { }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @BindingAnnotation
  public @interface AllowedAttributes { }
}