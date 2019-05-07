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

package org.apache.geode.gradle.plugins.jarcheck.tasks

import org.gradle.api.tasks.TaskAction

class ExamineJarContentTask extends JarExaminationTasks {

  ExamineJarContentTask() {
    setGroup('verification')
    setDescription("Extract jar contents to file, for use by check task against some expectation.")
  }

  @TaskAction
  def getJarContent() {
    logger.info "Examining content of ${jarFile}"

    // This zip-tree will have as the root directory the name of the jar.
    // As a tmp file, this is not useful in our comparisons and so we strip the root directory.
    def jarContent = project.zipTree(jarFile)
    def expandedDir = project.file(jarContent.tree.expandedDir).toPath()
    def actualContents = (jarContent.files.collect() {
      expandedDir.relativize(it.toPath()).toString()
    }) as Set<String>

    logger.debug("Copying contents to ${outputFile}")
    outputFile.withWriter { out ->
      actualContents.each() {
        out.println(it)
      }
    }
  }
}
