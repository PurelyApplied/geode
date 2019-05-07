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


import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskAction

import java.util.jar.Manifest

class ExamineJarManifestClasspathTask extends JarExaminationTasks {

  ExamineJarManifestClasspathTask() {
    setGroup('verification')
    setDescription("Extract jar manifest to file, for use by check task against some expectation.")
  }


  Set<String> extractClasspathFromManifest(File manifestPath) {
    if (!manifestPath.exists()) {
      logger.debug("Expected manifest '${manifestPath.absolutePath}' not found.  Using empty classpath.")
      return [] as Set<String>
    }

    def manifest = new Manifest(manifestPath.newInputStream())
    def classpathValue = manifest.getMainAttributes().getValue('Class-Path')
    if (classpathValue == null) {
      logger.debug("Manifest file did not contain a Class-Path.  Using empty classpath.")
      return [] as Set<String>
    }
    return classpathValue.split() as Set<String>
  }

  @TaskAction
  def getJarManifest() {
    logger.info "Examining manifest of ${jarFile}"

    // Check the manifest of the jar
    FileTree jarTree = project.zipTree(jarFile)
    Object manifest = jarTree.find { it.path.endsWith('/META-INF/MANIFEST.MF') }
    if (manifest == null) {
      println "Jar file ${jarFile} does not appear to have a manifest.  Using empty classpath."
      outputFile.write("")
      return
    }

    File manifestFile = project.file(manifest)
    outputFile.write(extractClasspathFromManifest(manifestFile).sort().join("\n"))
  }
}
