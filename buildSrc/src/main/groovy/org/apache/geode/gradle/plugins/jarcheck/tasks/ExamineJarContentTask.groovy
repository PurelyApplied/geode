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
