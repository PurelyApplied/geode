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

import org.apache.geode.gradle.plugins.jarcheck.JarCheckExtension.JarCheckConfiguration
import org.apache.geode.gradle.plugins.jarcheck.tasks.ListFileComparisonTask
import org.apache.geode.gradle.plugins.jarcheck.tasks.ExamineJarContentTask
import org.apache.geode.gradle.plugins.jarcheck.tasks.ExamineJarManifestClasspathTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy

import java.nio.file.Path
import java.nio.file.Paths

class JarCheckPlugin implements Plugin<Project> {

  static final String EXTENSION_NAME = "jarCheck"
  static final String ROOT_CHECK_TASK_NAME = "doJarChecks"
  static final String ROOT_UPDATE_TASK_NAME = "updateJarCheckExpectations"


  JarCheckExtension extension

  Set<String> seenJarNamePieces = new HashSet<>()


  @Override
  void apply(Project project) {
    project.getPlugins().apply(BasePlugin.class)

    project.tasks.register(ROOT_CHECK_TASK_NAME) {
      description "This is a synthetic task to perform all jar checks configured with the JarCheck plugin."
    }

    project.tasks.register(ROOT_UPDATE_TASK_NAME) {
      description "This is a synthetic task to perform all jar checks configured with the JarCheck plugin."
    }

    // setup the extension
    extension = project.getExtensions().create(EXTENSION_NAME, JarCheckExtension.class, project)

    project.afterEvaluate( { createTasks(project) } as Action<Project>)
    project.afterEvaluate( { hookIntoCheckIfRequested(project) } as Action<Project>)
  }

  static String sanitizeJarFilename(File jarFile) {
    return jarFile.name.replaceAll(/(?i)\.jar$/, '').split(/[- _.]/)*.capitalize().join("")
  }

  void hookIntoCheckIfRequested(Project project) {
    if (extension.makePartOfCheck) {
      project.tasks.named('check').configure {
        dependsOn project.tasks.named(ROOT_CHECK_TASK_NAME)
      }
    }
  }


  void createTasks(Project project) {
    Path expectationPath = Paths.get("${project.projectDir}/src/test/resources/expectations")
    Path workingBuildDir = Paths.get("${project.buildDir}/${EXTENSION_NAME}")
    extension.jarsToCheck.each { File jarFile, JarCheckConfiguration config ->
      println "Checking jarFile ${jarFile}..."
      println "Check Content: ${config.checkContent}"
      println "Check Manifest: ${config.checkManifestClasspath}"

      String jarTaskNamePiece = sanitizeJarFilename(jarFile)
      println "Sanitized task name piece: ${jarTaskNamePiece}"

      if (seenJarNamePieces.contains(jarTaskNamePiece)) {
        throw new IllegalArgumentException(
            "A jar with name ${jarFile.name} (or possibly ${jarTaskNamePiece})" +
                " has already been registered for checking." +
                "Tasks names cannot be inferred when similarly-named jars exist." +
                "Resolve this conflict or configure jar checking tasks directly.")
      }

      seenJarNamePieces.add(jarTaskNamePiece)

      if (config.checkContent) {
        println "Adding CONTENT checks for ${jarFile}"

        String examineContentTaskname = "examine${jarTaskNamePiece}Content"
        String checkContentTaskname = "check${jarTaskNamePiece}Content"
        String updateContentTaskname = "update${jarTaskNamePiece}ExpectedContent"

        File expectationFile = expectationPath.resolve("${jarTaskNamePiece}-content-expectation.txt").toFile()
        File actualFile = workingBuildDir.resolve("${jarTaskNamePiece}-content-actual.txt").toFile()
        File reportFile = workingBuildDir.resolve("${jarTaskNamePiece}-content-report.txt").toFile()

        project.tasks.register(examineContentTaskname, ExamineJarContentTask) {
          checks jarFile
          outputFile = actualFile

          if (config.jarCreator != null) {
            inputs.files { config.jarCreator }
          }
        }

        project.tasks.register(updateContentTaskname, Copy) {
          from actualFile.parent
          into expectationFile.parent
          include actualFile.name
          rename actualFile.name, expectationFile.name

          eachFile { fcp ->
            println "Copying file ${fcp} into ${expectationFile.parent} / ${expectationFile.name}"
          }
          inputs.files { project.tasks.named(examineContentTaskname) }
        }

        project.tasks.register(checkContentTaskname, ListFileComparisonTask) {
          actual = actualFile
          expectation = expectationFile
          report = reportFile

          inputs.files { project.tasks.named(examineContentTaskname) }
          inputs.files { expectationFile }
          mustRunAfter { project.tasks.named(updateContentTaskname) }
        }

        project.tasks.named(ROOT_CHECK_TASK_NAME).configure {
          dependsOn checkContentTaskname
        }
        project.tasks.named(ROOT_UPDATE_TASK_NAME).configure {
          dependsOn updateContentTaskname
        }
      }

      if (config.checkManifestClasspath) {
        println "Adding MANIFEST CLASSPATH checks for ${jarFile}"

        String examineContentTaskname = "examine${jarTaskNamePiece}ManifestClasspath"
        String checkContentTaskname = "check${jarTaskNamePiece}ManifestClasspath"
        String updateContentTaskname = "update${jarTaskNamePiece}ExpectedManifestClasspath"

        File expectationFile = expectationPath.resolve("${jarTaskNamePiece}-manifest-classpath-expectation.txt").toFile()
        File actualFile = workingBuildDir.resolve("${jarTaskNamePiece}-manifest-classpath-actual.txt").toFile()
        File reportFile = workingBuildDir.resolve("${jarTaskNamePiece}-manifest-classpath-report.txt").toFile()


        project.tasks.register(examineContentTaskname, ExamineJarManifestClasspathTask) {
          checks jarFile
          outputFile = actualFile

          if (config.jarCreator != null) {
            inputs.files { config.jarCreator }
          }
        }

        project.tasks.register(updateContentTaskname, Copy) {
          from actualFile.parent
          into expectationFile.parent
          include actualFile.name
          rename actualFile.name, expectationFile.name

          eachFile { fcp ->
            println "Copying file ${fcp} into ${expectationFile.parent} / ${expectationFile.name}"
          }
          inputs.files { project.tasks.named(examineContentTaskname)}
        }

        project.tasks.register(checkContentTaskname, ListFileComparisonTask) {
          actual = actualFile
          expectation = expectationFile
          report = reportFile

          inputs.files { project.tasks.named(examineContentTaskname) }
          inputs.files { expectationFile }
          mustRunAfter { project.tasks.named(updateContentTaskname) }
        }

        project.tasks.named(ROOT_CHECK_TASK_NAME).configure {
          dependsOn checkContentTaskname
        }
        project.tasks.named(ROOT_UPDATE_TASK_NAME).configure {
          dependsOn updateContentTaskname
        }
      }
    }
  }
}
