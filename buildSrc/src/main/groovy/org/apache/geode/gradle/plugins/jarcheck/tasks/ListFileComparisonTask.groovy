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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

class ListFileComparisonTask extends DefaultTask {
  @InputFile
  File actual

  @InputFile
  File expectation

  @OutputFile
  File report

  TaskProvider<Task> correspondingUpdateTaskProvider = null

  ListFileComparisonTask() {
    setGroup('verification')
    setDescription("Check actual jar content against expectation.")
  }

  @TaskAction
  def compareExpectedToActual() {
    Set<String> expectedFiles = expectation.text.trim().split("\n") as Set<String>
    Set<String> actualFiles = actual.text.trim().split("\n") as Set<String>

    Set<String> extraFiles = actualFiles - expectedFiles
    Set<String> missingFiles = expectedFiles - actualFiles

    if (extraFiles.size() == 0 && missingFiles.size() == 0){
      report.write("Actual matches expectation.\n")
      return
    }

    report.write("Actual does not match expectation...\n")

    if (extraFiles.size() > 0 ) {
      report.withWriterAppend { out ->
        out.println("The following files were found but not expected:")
        out.println("  " + extraFiles.sort().join("\n  "))
      }
    }
    if (missingFiles.size() > 0 ) {
      report.withWriterAppend { out ->
        out.println("The following files expected but missing:")
        out.println("  " + missingFiles.sort().join("\n  "))
      }
    }

    if (null != correspondingUpdateTaskProvider) {
      report.withWriterAppend { out ->
        out.println("If the above changes are intentional, run '${correspondingUpdateTaskProvider.get().path}' to update baseline.")
      }
    }

    throw new GradleException("Jar did not match expectation...\n${report.text}")
  }
}
