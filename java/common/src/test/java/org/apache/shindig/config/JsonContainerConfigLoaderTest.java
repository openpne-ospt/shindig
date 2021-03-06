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

package org.apache.shindig.config;

import static org.apache.shindig.config.ContainerConfig.DEFAULT_CONTAINER;
import static org.apache.shindig.config.ContainerConfig.CONTAINER_KEY;
import static org.apache.shindig.config.ContainerConfig.PARENT_KEY;
import static org.junit.Assert.*;

import org.apache.shindig.expressions.Expressions;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JsonContainerConfigLoaderTest {

  private static final String TOP_LEVEL_NAME = "Top level name";
  private static final String TOP_LEVEL_VALUE = "Top level value";

  private static final String NESTED_KEY = "ne$ted";
  private static final String NESTED_NAME = "Nested name";
  private static final String NESTED_VALUE = "Nested value";
  private static final String NESTED_ALT_VALUE = "Nested value alt";

  private static final String CHILD_CONTAINER = "child";
  private static final String CONTAINER_A = "container-a";
  private static final String CONTAINER_B = "container-b";

  private static final String ARRAY_NAME = "array value";
  private static final String[] ARRAY_VALUE = {"Hello", "World"};
  private static final String ARRAY_ALT_VALUE = "Not an array";

  private ExpressionContainerConfig config;

  private File createContainer(JSONObject json) throws Exception {
    File file = File.createTempFile(getClass().getName(), ".json");
    file.deleteOnExit();
    BufferedWriter out = new BufferedWriter(new FileWriter(file));
    out.write(json.toString());
    out.close();
    return file;
  }

  private File createDefaultContainer() throws Exception {

    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{DEFAULT_CONTAINER});
    json.put(TOP_LEVEL_NAME, TOP_LEVEL_VALUE);
    json.put(ARRAY_NAME, ARRAY_VALUE);

    // small nested data.
    JSONObject nested = new JSONObject();
    nested.put(NESTED_NAME, NESTED_VALUE);

    json.put(NESTED_KEY, nested);
    return createContainer(json);
  }

  private void createConfigForTest(String containers) throws ContainerConfigException {
    JsonContainerConfigLoader
        .getTransactionFromFile(containers, "localhost", "8080", "",config).commit();
  }
  
  @Before
  public void setUp() {
    config = new ExpressionContainerConfig(Expressions.forTesting());
  }

  @Test
  public void parseBasicConfig() throws Exception {
    createConfigForTest(createDefaultContainer().getAbsolutePath());

    assertEquals(1, config.getContainers().size());
    for (String container : config.getContainers()) {
      assertEquals(DEFAULT_CONTAINER, container);
    }

    String value = config.getString(DEFAULT_CONTAINER, TOP_LEVEL_NAME);
    assertEquals(TOP_LEVEL_VALUE, value);

    Map<String, Object> nested = config.getMap(DEFAULT_CONTAINER, NESTED_KEY);
    String nestedValue = nested.get(NESTED_NAME).toString();
    assertEquals(NESTED_VALUE, nestedValue);
  }
  

  @Test
  public void aliasesArePopulated() throws Exception {
    JSONObject json = new JSONObject()
        .put(CONTAINER_KEY, new String[]{CONTAINER_A, CONTAINER_B})
        .put(NESTED_KEY, NESTED_VALUE);

    File parentFile = createDefaultContainer();
    File childFile = createContainer(json);

    createConfigForTest(childFile.getAbsolutePath() +
        JsonContainerConfigLoader.FILE_SEPARATOR + parentFile.getAbsolutePath());

    assertEquals(NESTED_VALUE, config.getString(CONTAINER_A, NESTED_KEY));
    assertEquals(NESTED_VALUE, config.getString(CONTAINER_B, NESTED_KEY));
  }

  @Test
  public void parseWithDefaultInheritance() throws Exception {
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{CHILD_CONTAINER});
    json.put(PARENT_KEY, DEFAULT_CONTAINER);
    json.put(ARRAY_NAME, ARRAY_ALT_VALUE);

    // small nested data.
    JSONObject nested = new JSONObject();
    nested.put(NESTED_NAME, NESTED_ALT_VALUE);

    json.put(NESTED_KEY, nested);

    File childFile = createContainer(json);
    File parentFile = createDefaultContainer();
    createConfigForTest(childFile.getAbsolutePath() +
        JsonContainerConfigLoader.FILE_SEPARATOR + parentFile.getAbsolutePath());

    String value = config.getString(CHILD_CONTAINER, TOP_LEVEL_NAME);
    assertEquals(TOP_LEVEL_VALUE, value);

    Map<String, Object> nestedObj = config.getMap(CHILD_CONTAINER, NESTED_KEY);
    String nestedValue = nestedObj.get(NESTED_NAME).toString();
    assertEquals(NESTED_ALT_VALUE, nestedValue);

    String arrayValue = config.getString(CHILD_CONTAINER, ARRAY_NAME);
    assertEquals(ARRAY_ALT_VALUE, arrayValue);

    // Verify that the parent value wasn't overwritten as well.

    List<String> actual = new ArrayList<String>();
    for (Object val : config.getList(DEFAULT_CONTAINER, ARRAY_NAME)) {
      actual.add(val.toString());
    }

    List<String> expected = Arrays.asList(ARRAY_VALUE);

    assertEquals(expected, actual);
  }

  @Test
  public void invalidContainerReturnsNull() throws Exception {
    createConfigForTest(createDefaultContainer().getAbsolutePath());
    assertNull("Did not return null for invalid container.", config.getString("fake", PARENT_KEY));
  }

  @Test(expected = ContainerConfigException.class)
  public void badConfigThrows() throws Exception {
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{CHILD_CONTAINER});
    json.put(PARENT_KEY, "bad bad bad parent!");
    json.put(ARRAY_NAME, ARRAY_ALT_VALUE);

    createConfigForTest(createContainer(json).getAbsolutePath());
  }

  @Test
  public void pathQuery() throws Exception {
    createConfigForTest(createDefaultContainer().getAbsolutePath());
    String path = "${" + NESTED_KEY + "['" + NESTED_NAME + "']}";
    String data = config.getString(DEFAULT_CONTAINER, path);
    assertEquals(NESTED_VALUE, data);
  }

  @Test
  public void expressionEvaluation() throws Exception {
    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{DEFAULT_CONTAINER});
    json.put("expression", "Hello, ${world}!");
    json.put("world", "Earth");

    createConfigForTest(createContainer(json).getAbsolutePath());

    assertEquals("Hello, Earth!", config.getString(DEFAULT_CONTAINER, "expression"));
  }

  @Test
  public void shindigPortTest() throws Exception {
    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{DEFAULT_CONTAINER});
    json.put("expression", "port=${SERVER_PORT}");

    createConfigForTest(createContainer(json).getAbsolutePath());

    assertEquals("port=8080", config.getString(DEFAULT_CONTAINER, "expression"));
  }

  @Test
  public void testCommonEnvironmentAddedToAllContainers() throws Exception {
    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{DEFAULT_CONTAINER, "testContainer"});
    json.put("port", "${SERVER_PORT}");
    json.put("host", "${SERVER_HOST}");

    createConfigForTest(createContainer(json).getAbsolutePath());

    assertEquals("8080", config.getString(DEFAULT_CONTAINER, "port"));
    assertEquals("8080", config.getString("testContainer", "port"));
    assertEquals("localhost", config.getString(DEFAULT_CONTAINER, "host"));
    assertEquals("localhost", config.getString("testContainer", "host"));
  }

  @Test
  public void expressionEvaluationUsingParent() throws Exception {
    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject();
    json.put(CONTAINER_KEY, new String[]{CHILD_CONTAINER});
    json.put(PARENT_KEY, DEFAULT_CONTAINER);
    json.put("parentExpression", "${parent['" + TOP_LEVEL_NAME + "']}");

    File childFile = createContainer(json);
    File parentFile = createDefaultContainer();
    createConfigForTest(childFile.getAbsolutePath() +
        JsonContainerConfigLoader.FILE_SEPARATOR + parentFile.getAbsolutePath());

    assertEquals(TOP_LEVEL_VALUE, config.getString(CHILD_CONTAINER, "parentExpression"));
  }

  @Test
  public void nullEntryEvaluation() throws Exception {
    // We use a JSON Object here to guarantee that we're well formed up front.
    JSONObject json = new JSONObject("{ 'gadgets.container' : ['default'], features : { osapi : null }}");
    createConfigForTest(createContainer(json).getAbsolutePath());
    assertNull(config.getMap("default", "features").get("osapi"));
  }
  
  @Test
  public void testNullEntriesOverrideEntriesInParent() throws Exception {
    // We use JSON Objects here to guarantee that we're well formed up front.
    JSONObject parent = new JSONObject("{ 'gadgets.container' : ['default'], features : { osapi : 'foo' }}");    
    JSONObject child = new JSONObject("{ 'gadgets.container' : ['child'], features : null}");    
    JSONObject grand = new JSONObject("{ 'gadgets.container' : ['grand'], parent : 'child'}");    
    createConfigForTest(createContainer(parent).getAbsolutePath());
    createConfigForTest(createContainer(child).getAbsolutePath());
    createConfigForTest(createContainer(grand).getAbsolutePath());
    assertEquals("foo", config.getMap("default", "features").get("osapi"));
    assertNull(config.getProperty("child", "features"));
    assertNull(config.getProperty("grand", "features"));
  }
}
