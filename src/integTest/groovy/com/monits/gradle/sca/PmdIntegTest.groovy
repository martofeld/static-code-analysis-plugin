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

import com.monits.gradle.sca.fixture.AbstractPluginIntegTestFixture
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assert.assertThat

class PmdIntegTest extends AbstractPluginIntegTestFixture {
    @Unroll("PMD #pmdVersion should run when using gradle #version")
    def "PMD is run"() {
        given:
        writeBuildFile()
        goodCode()

        when:
        def result = gradleRunner()
            .withGradleVersion(version)
            .build()

        then:
        if (GradleVersion.version(version) >= GradleVersion.version('2.5')) {
            // Executed task capture is only available in Gradle 2.5+
            result.task(taskName()).outcome == SUCCESS
        }

        // Make sure report exists and was using the expected tool version
        reportFile().exists()
        reportFile().assertContents(containsString("<pmd version=\"$pmdVersion\""))

        where:
        version << ['2.3', '2.4', '2.8', '2.10', GradleVersion.current().version]
        pmdVersion = GradleVersion.version(version) < StaticCodeAnalysisPlugin.GRADLE_VERSION_PMD ?
                StaticCodeAnalysisPlugin.BACKWARDS_PMD_TOOL_VERSION : StaticCodeAnalysisPlugin.LATEST_PMD_TOOL_VERSION
    }

    def "PMD configures auxclasspath"() {
        given:
        writeBuildFile()
        buildScriptFile() << """
            afterEvaluate {
                def pmdTask = project.tasks.getByPath(':pmd');
                if (pmdTask != null && pmdTask.hasProperty('classpath') && !pmdTask.classpath.empty) {
                    println "Auxclasspath is configured"
                }
            }
        """
        goodCode()

        when:
        def result = gradleRunner()
                .withGradleVersion('2.8')
                .build()

        then:
        // The classpath must be configured, and not empty
        assertThat(result.output, containsString("Auxclasspath is configured"))

        // Make sure pmd report exists
        reportFile().exists()
    }

    String reportFileName() {
        'build/reports/pmd/pmd.xml'
    }

    String taskName() {
        ':pmd'
    }

    String toolName() {
        'pmd'
    }
}