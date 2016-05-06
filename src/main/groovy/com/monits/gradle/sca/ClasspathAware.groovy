/*
 * Copyright 2010-2016 Monits S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.monits.gradle.sca

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails

/**
 * Trait for configuring classpath aware tasks.
*/
@CompileStatic
trait ClasspathAware {

    private static final List<String> AUTOGENERATED_CLASSES = [
        '**/R.class', // R.java
        '**/R$*.class', // R.java inner classes
        '**/Manifest.class', // Manifest.java
        '**/Manifest$*.class', // Manifest.java inner classes
        '**/BuildConfig.class', // BuildConfig.java
        '**/BuildConfig$*.class', // BuildConfig.java inner classes
    ].asImmutable()

    private static final String MOCKABLE_ANDROID_JAR_TASK = 'mockableAndroidJar'

    @CompileStatic(TypeCheckingMode.SKIP)
    void setupAndroidClasspathAwareTask(final Task taskToConfigure, final Project project) {
        /*
         * For best results, this task needs ALL classes, including Android's SDK,
         * but we need that configure before execution to be considered in up-to-date check.
         * We do it in a separate task, executing AFTER all other needed tasks are done
         */
        Task cpTask = project.task(
                'configureClasspathFor' + taskToConfigure.name.capitalize()) { Task self ->
            // The mockable android jar allows us to know Android's classes in our analysis
            Task t = project.tasks.findByName(MOCKABLE_ANDROID_JAR_TASK)
            if (t != null) {
                self.dependsOn t
            }

            // we need all other task to be done first
            self.dependsOn taskToConfigure.dependsOn.findAll { it != self } // avoid cycles
        } << {
            configAndroidClasspath(taskToConfigure, project,
                    project.tasks.findByName(MOCKABLE_ANDROID_JAR_TASK))
        }

        taskToConfigure.dependsOn cpTask
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void configAndroidClasspath(final Task task, final Project project, final Task mockableAndroidJarTask) {
        // Manually add classes of project dependencies
        FileCollection classTree = project.files()
        project.fileTree(
            dir:"${project.buildDir}/intermediates/exploded-aar/${project.rootProject.name}/",
            include:'*/unspecified/',).visit { FileVisitDetails it ->
                if (!it.directory) {
                    return
                }
                if (it.path.contains('/')) {
                    return
                }
                classTree += getProjectClassTree(project, it.path)
            }

        FileCollection mockableAndroidJar = project.files()
        if (mockableAndroidJarTask) {
            mockableAndroidJar += mockableAndroidJarTask.outputs.files
        }

        task.classpath = project.configurations.scaconfig +
                project.fileTree(
                        dir:"${project.buildDir}/intermediates/exploded-aar/",
                        include:'**/*.jar',
                        exclude:"${project.rootProject.name}/*/unspecified/jars/classes.jar",) +
                mockableAndroidJar +
                getProjectAutogeneratedClasses(project) +
                classTree
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param proj The project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
     */
    static FileTree getProjectAutogeneratedClasses(final Project proj) {
        ConfigurableFileTree tree = proj.fileTree(dir:"${proj.buildDir}/intermediates/classes/")
        tree.include(AUTOGENERATED_CLASSES) as FileTree
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param project The project being configured.
     * @param path The path to the project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
     */
    FileTree getProjectClassTree(final Project project, final String path) {
        getProjectClassTree(project.rootProject.findProject(':' + path))
    }

    /**
     * Retrieves a FileTree pointing to all interesting .class files for
     * static code analysis. This ignores for instance, Android's autogenerated classes
     *
     * @param proj The project whose class file tree to obtain.
     * @return FileTree pointing to all interesting .class files
     */
    FileTree getProjectClassTree(final Project proj) {
        ConfigurableFileTree tree = proj.fileTree(dir:"${proj.buildDir}/intermediates/classes/")
        tree.exclude(AUTOGENERATED_CLASSES) as FileTree
    }
}