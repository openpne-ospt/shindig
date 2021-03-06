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
package org.apache.shindig.social.dataservice.integration;

import org.apache.shindig.protocol.ContentTypes;
import org.junit.Test;

/**
 * Tests the XML serialization of ActivityStreams.
 */
public class RestfulXmlActivityEntryTest extends AbstractLargeRestfulTests{
  
  private static final String FIXTURE_LOC = "src/test/java/org/apache/shindig/social/dataservice/integration/fixtures/";
  
  @Test
  public void testGetActivityEntryXmlById() throws Exception {
    String resp = getResponse("/activitystreams/john.doe/@self/1/activity2", "GET", "xml", ContentTypes.OUTPUT_XML_CONTENT_TYPE);
    String expected = TestUtils.loadTestFixture(FIXTURE_LOC + "ActivityEntryXmlId.xml");
    assertTrue(TestUtils.xmlsEqual(expected, resp));
  }
  
  @Test
  public void testGetActivityEntryXmlByIds() throws Exception {
    String resp = getResponse("/activitystreams/john.doe/@self/1/activity1,activity2", "GET", "xml", ContentTypes.OUTPUT_XML_CONTENT_TYPE);
    String expected = TestUtils.loadTestFixture(FIXTURE_LOC + "ActivityEntryXmlIds.xml");
    assertTrue(TestUtils.xmlsEqual(expected, resp));
  }
  
  @Test
  public void testCreateActivityEntryXml() throws Exception {
    // TODO: Creating activity from XML not fully supported
  }
}
