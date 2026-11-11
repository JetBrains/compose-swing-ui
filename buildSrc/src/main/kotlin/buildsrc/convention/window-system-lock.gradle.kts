// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import org.gradle.api.provider.Provider

// Applied by modules whose tests show real windows. See WindowSystemLock for what is being serialized.
private val windowSystemLock: Provider<WindowSystemLock> =
    gradle.sharedServices.registerIfAbsent("windowSystemLock", WindowSystemLock::class) {
        maxParallelUsages.set(1)
    }

tasks.withType<Test>().configureEach {
    usesService(windowSystemLock)
}
