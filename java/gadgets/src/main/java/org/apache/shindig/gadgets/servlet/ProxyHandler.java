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
package org.apache.shindig.gadgets.servlet;

import static org.apache.shindig.gadgets.rewrite.image.BasicImageRewriter.PARAM_NO_EXPAND;
import static org.apache.shindig.gadgets.rewrite.image.BasicImageRewriter.PARAM_RESIZE_HEIGHT;
import static org.apache.shindig.gadgets.rewrite.image.BasicImageRewriter.PARAM_RESIZE_QUALITY;
import static org.apache.shindig.gadgets.rewrite.image.BasicImageRewriter.PARAM_RESIZE_WIDTH;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.servlet.HttpUtil;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.common.uri.UriBuilder;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.LockedDomainService;
import org.apache.shindig.gadgets.http.HttpRequest;
import org.apache.shindig.gadgets.http.HttpResponse;
import org.apache.shindig.gadgets.http.RequestPipeline;
import org.apache.shindig.gadgets.rewrite.RequestRewriterRegistry;
import org.apache.shindig.gadgets.rewrite.RewritingException;
import org.apache.shindig.gadgets.uri.ProxyUriManager;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles open proxy requests.
 */
@Singleton
public class ProxyHandler extends ProxyBase {
  private static final Logger logger = Logger.getLogger(ProxyHandler.class.getName());

  private static final String[] INTEGER_RESIZE_PARAMS = new String[] {
    PARAM_RESIZE_HEIGHT, PARAM_RESIZE_WIDTH, PARAM_RESIZE_QUALITY, PARAM_NO_EXPAND
  };

  // TODO: parameterize these.
  static final Integer LONG_LIVED_REFRESH = (365 * 24 * 60 * 60);  // 1 year
  static final Integer DEFAULT_REFRESH = (60 * 60);                // 1 hour
  
  static final String FALLBACK_URL_PARAM = "fallback_url";

  private final RequestPipeline requestPipeline;
  private final LockedDomainService lockedDomainService;
  private final RequestRewriterRegistry contentRewriterRegistry;
  private final ProxyUriManager proxyUriManager;

  @Inject
  public ProxyHandler(RequestPipeline requestPipeline,
                      LockedDomainService lockedDomainService,
                      RequestRewriterRegistry contentRewriterRegistry,
                      ProxyUriManager proxyUriManager) {
    this.requestPipeline = requestPipeline;
    this.lockedDomainService = lockedDomainService;
    this.contentRewriterRegistry = contentRewriterRegistry;
    this.proxyUriManager = proxyUriManager;
  }

  /**
   * Generate a remote content request based on the parameters sent from the client.
   */
  private HttpRequest buildHttpRequest(HttpServletRequest request, Uri uri,
      ProxyUriManager.ProxyUri uriCtx, Uri tgt) throws GadgetException {
    validateUrl(tgt);

    HttpRequest req = uriCtx.makeHttpRequest(uriCtx.getResource());
    copySanitizedIntegerParams(uri, req);

    // Allow the rewriter to use an externally forced MIME type. This is needed
    // allows proper rewriting of <script src="x"/> where x is returned with
    // a content type like text/html which unfortunately happens all too often
    req.setRewriteMimeType(uri.getQueryParameter(REWRITE_MIME_TYPE_PARAM));
    req.setSanitizationRequested("1".equals(uri.getQueryParameter(SANITIZE_CONTENT_PARAM)));

    this.setRequestHeaders(request, req);
    
    return req;
  }

  private void copySanitizedIntegerParams(Uri uri, HttpRequest req) {
    for (String resizeParamName : INTEGER_RESIZE_PARAMS) {
      if (uri.getQueryParameter(resizeParamName) != null) {
        req.setParam(resizeParamName,
            NumberUtils.createInteger(uri.getQueryParameter(resizeParamName)));
      }
    }
  }

  @Override
  protected void doFetch(HttpServletRequest request, HttpServletResponse response)
      throws IOException, GadgetException {
    if (request.getHeader("If-Modified-Since") != null) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }

    // TODO: Consider removing due to redundant logic.
    String host = request.getHeader("Host");
    if (!lockedDomainService.isSafeForOpenProxy(host)) {
      // Force embedded images and the like to their own domain to avoid XSS
      // in gadget domains.
      String msg = "Embed request for url " + getParameter(request, URL_PARAM, "") +
          " made to wrong domain " + host;
      logger.info(msg);
      throw new GadgetException(GadgetException.Code.INVALID_PARAMETER, msg,
          HttpResponse.SC_BAD_REQUEST);
    }
    
    Uri requestUri = new UriBuilder(request).toUri();
    ProxyUriManager.ProxyUri proxyUri = proxyUriManager.process(requestUri);
    
    try {
      HttpUtil.setCachingHeaders(response,
          proxyUri.translateStatusRefresh(LONG_LIVED_REFRESH, DEFAULT_REFRESH), false);
    } catch (GadgetException gex) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, gex.getMessage());
      return;
    }

    HttpRequest rcr = buildHttpRequest(request, requestUri, proxyUri, proxyUri.getResource());
    if (rcr == null) {
      throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
          "No url paramater in request", HttpResponse.SC_BAD_REQUEST);      
    }
    HttpResponse results = requestPipeline.execute(rcr);
    
    if (results.isError()) {
      // Error: try the fallback. Particularly useful for proxied images.
      String fallbackStr = requestUri.getQueryParameter(FALLBACK_URL_PARAM);
      if (fallbackStr != null) {
        try {
          HttpRequest fallbackRcr =
              buildHttpRequest(request, requestUri, proxyUri, Uri.parse(fallbackStr));
          results = requestPipeline.execute(fallbackRcr);
        } catch (IllegalArgumentException e) {
          throw new GadgetException(GadgetException.Code.INVALID_PARAMETER,
              FALLBACK_URL_PARAM + " param is invalid: " + e, HttpResponse.SC_BAD_REQUEST);
        }
      }
    }
    
    if (contentRewriterRegistry != null) {
      try {
        results = contentRewriterRegistry.rewriteHttpResponse(rcr, results);
      } catch (RewritingException e) {
        throw new GadgetException(GadgetException.Code.INTERNAL_SERVER_ERROR, e,
            e.getHttpStatusCode());
      }
    }

    for (Map.Entry<String, String> entry : results.getHeaders().entries()) {
      String name = entry.getKey();
      if (!DISALLOWED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
          response.addHeader(name, entry.getValue());
      }
    }

    String responseType = results.getHeader("Content-Type");
    if (!StringUtils.isEmpty(rcr.getRewriteMimeType())) {
      String requiredType = rcr.getRewriteMimeType();
      // Use a 'Vary' style check on the response
      if (requiredType.endsWith("/*") &&
          !StringUtils.isEmpty(responseType)) {
        requiredType = requiredType.substring(0, requiredType.length() - 2);
        if (!responseType.toLowerCase().startsWith(requiredType.toLowerCase())) {
          response.setContentType(requiredType);
          responseType = requiredType;
        }
      } else {
        response.setContentType(requiredType);
        responseType = requiredType;
      }
    }

    setResponseHeaders(request, response, results);

    if (results.getHttpStatusCode() != HttpResponse.SC_OK) {
      if (results.getHttpStatusCode() == HttpResponse.SC_INTERNAL_SERVER_ERROR) {
        // External "internal error" should be mapped to gateway error
        response.sendError(HttpResponse.SC_BAD_GATEWAY);
      } else {
        response.sendError(results.getHttpStatusCode());
      }
    }

    IOUtils.copy(results.getResponse(), response.getOutputStream());
  }
}
