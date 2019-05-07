/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.gradle.plugins.jarcheck

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

class JarCheckExtension {
  boolean makePartOfCheck = true
  boolean implicitlyCheckAll = true // disabled if any explicit configuration is provided.

  final Project project

  Map<File, JarCheckConfiguration> jarsToCheck = [:]
  JarCheckExtension(Project project) {
    this.project = project
  }

  void partOfCheck(boolean doIt) {
    this.makePartOfCheck = doIt
  }

  void checkAllJarTasks() {
    implicitlyCheckAll = false
    project.tasks.withType(Jar).each { jarTask ->
      check(jarTask)
    }
  }

  void check(Object o) {
    implicitlyCheckAll = false
    check(o, {
      checkContent = true
      checkManifestClasspath = true
    } as Closure<JarCheckConfiguration>)
  }

  void check(Object o, Closure<JarCheckConfiguration> configClosure) {
    implicitlyCheckAll = false
    File jarFile
    Task jarCreator
    switch(o.class) {
      case TaskProvider:
        jarFile = (o as TaskProvider<Task>).get().outputs.files.singleFile
        jarCreator = (o as TaskProvider<Task>).get()
        break
      case Task:
        jarFile = (o as Task).outputs.files.singleFile
        jarCreator = (o as Task)
        break
      case File:
        jarFile = (o as File)
        jarCreator = null
        break
      default:
        jarFile = project.file(o)
        jarCreator = null
    }

    JarCheckConfiguration config = project.configure(new JarCheckConfiguration(), configClosure) as JarCheckConfiguration
    config.jarCreator = jarCreator
    jarsToCheck.put(jarFile, config)
  }
}
