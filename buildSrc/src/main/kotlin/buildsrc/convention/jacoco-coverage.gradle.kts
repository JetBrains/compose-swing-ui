package buildsrc.convention

import org.gradle.kotlin.dsl.create
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
}

private val coverage = extensions.create<JacocoCoverageExtension>("jacocoCoverage")

// A module may run its tests across more than one task. The report has to take in every one of them, or
// the floors below are measured against a fraction of the suite and pass or fail on the split rather
// than on the coverage. Each task contributes its own file as it is configured, which keeps the report
// off the task container: asking it for every test task while it is being iterated is what a plain
// `executionData(tasks.withType<Test>())` does, and that is refused.
private val testExecutionData = objects.fileCollection()

tasks.withType<Test>().configureEach {
    testExecutionData.from(extensions.getByType<JacocoTaskExtension>().destinationFile)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    executionData.setFrom(testExecutionData)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.named("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverage.lineMinimum.get()
            }
            if (coverage.branchMinimum.isPresent) {
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = coverage.branchMinimum.get()
                }
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
