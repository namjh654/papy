import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.int(alias: String) = findVersion(alias).get().requiredVersion.toInt()

/** `:core:data` → `cloud.elsa.app.core.data`, `:feature:auth` → `cloud.elsa.app.feature.auth` */
internal val Project.elsaNamespace: String
    get() = "cloud.elsa.app" + path.replace(':', '.').replace('-', '_')
