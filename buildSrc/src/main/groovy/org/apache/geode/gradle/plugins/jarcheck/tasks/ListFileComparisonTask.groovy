package org.apache.geode.gradle.plugins.jarcheck.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ListFileComparisonTask extends DefaultTask {
  @InputFile
  File actual

  @InputFile
  File expectation

  @OutputFile
  File report

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

    throw new GradleException("Jar did not match expectation...\n${report.text}")
  }
}
