/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.shindig.common.servlet;

import org.apache.shindig.common.EasyMockTestCase;

import org.junit.Test;

import org.apache.shindig.common.servlet.BasicAuthorityProvider;

/**
 * Simple test for AuthorityProvider.
 */
public class BasicAuthorityProviderTest extends EasyMockTestCase {

  @Test
  public void testProviderWorks() {
    String host = "myhost";
    String port = "9080";
    BasicAuthorityProvider provider = new BasicAuthorityProvider(host,port);
    assertEquals( "myhost:9080", provider.get().getAuthority());
  }

  @Test
  public void testDefaultHostAndPort() {
	String host = "";
	String port = "";
	BasicAuthorityProvider provider = new BasicAuthorityProvider(host,port);
	assertEquals("localhost:8080", provider.get().getAuthority() );
  }

  @Test
  public void testJettyHostAndPort() {
	String host = "";
	String port = "";
	System.setProperty("jetty.host", "localhost");
	System.setProperty("jetty.port", "9003");
	BasicAuthorityProvider provider = new BasicAuthorityProvider(host,port);
	assertEquals("localhost:9003", provider.get().getAuthority() );
	System.clearProperty("jetty.host");
	System.clearProperty("jetty.port");
  }

}
