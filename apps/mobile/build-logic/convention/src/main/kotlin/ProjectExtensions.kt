import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.int(alias: String) = findVersion(alias).get().requiredVersion.toInt()

/** `:core:data` → `io.github.namjh654.papy.core.data`, `:feature:auth` → `io.github.namjh654.papy.feature.auth` */
internal val Project.papyNamespace: String
    get() = "io.github.namjh654.papy" + path.replace(':', '.').replace('-', '_')
