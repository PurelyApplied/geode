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

import org.apache.geode.gradle.plugins.jarcheck.tasks.ExamineJarContentTask
import org.apache.geode.gradle.plugins.jarcheck.tasks.ExamineJarManifestClasspathTask
import org.apache.geode.gradle.plugins.jarcheck.tasks.ListFileComparisonTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync

import java.nio.file.Path
import java.nio.file.Paths

class JarCheckPlugin implements Plugin<Project> {

  static final String EXTENSION_NAME = "jarCheck"
  static final String ROOT_CHECK_TASK_NAME = "jarCheckAll"
  static final String ROOT_UPDATE_TASK_NAME = "jarCheckUpdateAll"

  static final String EXPECTATIONS_BUILD_DIR = "src/build-resources/jarCheckExpectations"

  JarCheckExtension extension

  Set<String> seenJarNamePieces = new HashSet<>()


  @Override
  void apply(Project project) {
    project.getPlugins().apply(BasePlugin.class)

    project.tasks.register(ROOT_CHECK_TASK_NAME) {
      group "verification"
      description "This is a synthetic task to perform all jar checks configured with the JarCheck plugin."
    }

    project.tasks.register(ROOT_UPDATE_TASK_NAME, Sync) {
      into "${project.projectDir}/${EXPECTATIONS_BUILD_DIR}"
      group "verification"
      description "This is a synthetic task to perform all jar expectation updates configured with the JarCheck plugin."
    }

    // setup the extension
    extension = project.getExtensions().create(EXTENSION_NAME, JarCheckExtension.class, project)

    project.afterEvaluate({
      createTasks(project)
      hookIntoCheckIfRequested(project)
    } as Action<Project>)
  }

  static String stripToTaskNameFormat(File jarFile) {
    return stripToTaskNameFormat(jarFile.name)
  }

  static String stripToTaskNameFormat(String s) {
    return s.replaceAll(/(?i)\.jar$/, '').split(/[^a-zA-Z01-9]+/)*.capitalize().join("")
  }

  void hookIntoCheckIfRequested(Project project) {
    if (extension.makePartOfCheck) {
      project.tasks.named('check').configure {
        dependsOn project.tasks.named(ROOT_CHECK_TASK_NAME)
      }
    }
  }


  void createTaskTrio(Project project, String descriptor, Class examineTaskType, File jarFile, String jarTaskNamePiece, Path workingBuildDir, Path expectationDir, JarCheckConfiguration config) {
    project.logger.debug("Adding ${descriptor.toUpperCase()} checks for ${jarFile}")

    String examineTaskName = "examine${jarTaskNamePiece}${stripToTaskNameFormat(descriptor)}"
    String checkTaskName = "check${jarTaskNamePiece}${stripToTaskNameFormat(descriptor)}"
    String updateTaskName = "update${jarTaskNamePiece}Expected${stripToTaskNameFormat(descriptor)}"

    File expectationFile = expectationDir.resolve("${jarTaskNamePiece}-${descriptor.toLowerCase()}-expectation.txt").toFile()
    File actualFile = workingBuildDir.resolve("${jarTaskNamePiece}-${descriptor.toLowerCase()}-actual.txt").toFile()
    File reportFile = workingBuildDir.resolve("${jarTaskNamePiece}-${descriptor.toLowerCase()}-report.txt").toFile()

    createExpectationStubIfMissing(expectationFile, project, updateTaskName)

    project.tasks.register(examineTaskName, examineTaskType) {
      checks jarFile
      outputFile = actualFile

      if (config.jarCreator != null) {
        inputs.files { config.jarCreator }
      }
    }

    createUpdateAndCheckTasks(project, updateTaskName, actualFile, expectationFile, examineTaskName, checkTaskName, reportFile)
  }

  void createTasks(Project project) {
    if (extension.implicitlyCheckAll) {
      extension.checkAllJarTasks()
    }

    Path expectationDir = Paths.get("${project.projectDir}/${EXPECTATIONS_BUILD_DIR}")
    Path workingBuildDir = Paths.get("${project.buildDir}/${EXTENSION_NAME}")

    if (!expectationDir.toFile().exists()) {
      expectationDir.toFile().mkdirs()
    }
    extension.jarsToCheck.each { File jarFile, JarCheckConfiguration config ->
      String jarTaskNamePiece = stripToTaskNameFormat(jarFile)
      project.logger.debug("Creating check tasks for sanitized task name piece: ${jarTaskNamePiece}...")

      if (seenJarNamePieces.contains(jarTaskNamePiece)) {
        throw new IllegalArgumentException(
          "A jar with corresponding task name piece ${jarTaskNamePiece}" +
            " has already been registered for checking.  " +
            "Task names are generated by stripping non-alphanumeric characters and capitalizing.  " +
            "Tasks names cannot be inferred when similarly-named jars exist.  " +
            "Resolve this conflict or configure jar checking tasks directly.")
      }
      seenJarNamePieces.add(jarTaskNamePiece)

      if (config.checkContent) {
        createTaskTrio(project, "content", ExamineJarContentTask, jarFile, jarTaskNamePiece, workingBuildDir, expectationDir, config)
      }

      if (config.checkManifestClasspath) {
        createTaskTrio(project, "manifest-classpath", ExamineJarManifestClasspathTask, jarFile, jarTaskNamePiece, workingBuildDir, expectationDir, config)

      }
    }
  }

  private void createUpdateAndCheckTasks(Project project, String updateTaskname, File actualFile, File expectationFile, String examineTaskname, String checkTaskname, File reportFile) {
    project.tasks.register(updateTaskname, Copy) {
      from actualFile.parent
      into expectationFile.parent
      include actualFile.name
      rename actualFile.name, expectationFile.name

      inputs.files { project.tasks.named(examineTaskname) }
    }

    project.tasks.register(checkTaskname, ListFileComparisonTask) {
      actual = actualFile
      expectation = expectationFile
      report = reportFile
      correspondingUpdateTaskProvider = project.tasks.named(updateTaskname)

      inputs.files { project.tasks.named(examineTaskname) }
      inputs.files { expectationFile }
      mustRunAfter { project.tasks.named(updateTaskname) }
    }

    project.tasks.named(ROOT_CHECK_TASK_NAME).configure {
      dependsOn checkTaskname
    }
    project.tasks.named(ROOT_UPDATE_TASK_NAME).configure {
      with project.copySpec {
        from actualFile.parent
        include actualFile.name
        rename actualFile.name, expectationFile.name
      }

      inputs.files { project.tasks.named(examineTaskname) }
    }
  }

  private static void createExpectationStubIfMissing(File expectationFile, Project project, String updateContentTaskname) {
    if (!expectationFile.exists()) {
      project.logger.warn("Expected jar-check file '${expectationFile}' does not exist.  Creating empty file.  Run '${updateContentTaskname}' to initialize.")
      try {
        expectationFile.write("")
      } catch (ignored) {
        project.logger.error("Could not create empty file '${expectationFile}'.  Expect failures with message \"... 'expectation' does not exist.\"")
      }
    }
  }
}
