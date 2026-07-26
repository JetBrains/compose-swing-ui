package buildsrc.convention

import org.gradle.kotlin.dsl.create
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
}

private val coverage = extensions.create<JacocoCoverageExtension>("jacocoCoverage")

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
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
