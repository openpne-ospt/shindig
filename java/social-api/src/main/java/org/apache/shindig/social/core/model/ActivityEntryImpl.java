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

package org.apache.shindig.social.core.model;

import org.apache.shindig.social.opensocial.model.ActivityEntry;
import org.apache.shindig.social.opensocial.model.ActivityObject;
import org.apache.shindig.social.opensocial.model.Extension;

/**
 * A simple bean implementation of an ActivityStream Entry.
 */
public class ActivityEntryImpl implements ActivityEntry {
  
  private ActivityObject actor;
  private String content;
  private ActivityObject generator;
  private String icon;
  private String id;
  private ActivityObject object;
  private String published;
  private ActivityObject provider;
  private ActivityObject target;
  private String title;
  private String updated;
  private String url;
  private String verb;
  private Extension openSocial;

  /**
   * Create a new empty ActivityEntry
   */
  public ActivityEntryImpl() { }
  
  public ActivityObject getActor() {
    return actor;
  }

  /** {@inheritDoc} */
  public void setActor(ActivityObject actor) {
    this.actor = actor;
  }

  /** {@inheritDoc} */
  public String getContent() {
    return content;
  }

  /** {@inheritDoc} */
  public void setContent(String content) {
    this.content = content;
  }

  /** {@inheritDoc} */
  public ActivityObject getGenerator() {
    return generator;
  }

  /** {@inheritDoc} */
  public void setGenerator(ActivityObject generator) {
    this.generator = generator;
  }

  /** {@inheritDoc} */
  public String getIcon() {
    return icon;
  }

  /** {@inheritDoc} */
  public void setIcon(String icon) {
    this.icon = icon;
  }

  /** {@inheritDoc} */
  public String getId() {
    return id;
  }

  /** {@inheritDoc} */
  public void setId(String id) {
    this.id = id;
  }

  /** {@inheritDoc} */
  public ActivityObject getObject() {
    return object;
  }

  /** {@inheritDoc} */
  public void setObject(ActivityObject object) {
    this.object = object;
  }

  /** {@inheritDoc} */
  public String getPublished() {
    return published;
  }

  /** {@inheritDoc} */
  public void setPublished(String published) {
    this.published = published;
  }

  /** {@inheritDoc} */
  public ActivityObject getProvider() {
    return provider;
  }

  /** {@inheritDoc} */
  public void setProvider(ActivityObject provider) {
    this.provider = provider;
  }

  /** {@inheritDoc} */
  public ActivityObject getTarget() {
    return target;
  }

  /** {@inheritDoc} */
  public void setTarget(ActivityObject target) {
    this.target = target;
  }

  /** {@inheritDoc} */
  public String getTitle() {
    return title;
  }

  /** {@inheritDoc} */
  public void setTitle(String title) {
    this.title = title;
  }

  /** {@inheritDoc} */
  public String getUpdated() {
    return updated;
  }

  /** {@inheritDoc} */
  public void setUpdated(String updated) {
    this.updated = updated;
  }
  
  /** {@inheritDoc} */
  public String getUrl() {
    return url;
  }

  /** {@inheritDoc} */
  public void setUrl(String url) {
    this.url = url;
  }

  /** {@inheritDoc} */
  public String getVerb() {
    return verb;
  }

  /** {@inheritDoc} */
  public void setVerb(String verb) {
    this.verb = verb;
  }

  /** {@inheritDoc} */
  public Extension getOpenSocial() {
    return openSocial;
  }

  /** {@inheritDoc} */
  public void setOpenSocial(Extension openSocial) {
    this.openSocial = openSocial;
  }

  /**
   * Sorts ActivityEntries in ascending order based on publish date.
   * 
   * @param that is the ActivityEntry to compare to this ActivityEntry
   * 
   * @return int represents how the ActivityEntries compare
   */
  public int compareTo(ActivityEntry that) {
    if (this.getPublished() == null && that.getPublished() == null) {
      return 0;   // both are null, equal
    } else if (this.getPublished() == null) {
      return -1;  // this is null, comes before real date
    } else if (that.getPublished() == null) {
      return 1;   // that is null, this comes after
    } else {      // compare publish dates in lexicographical order
      return this.getPublished().compareTo(that.getPublished());
    }
  }
}
