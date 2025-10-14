/*
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.collections.associate
import kotlin.collections.ifEmpty
import kotlin.jvm.java

plugins {
    id("kotlinx-io-publish") apply false
    id("kotlinx-io-dokka")
    alias(libs.plugins.kover)
    id("kotlinx-io-multiplatform")
}

//kotlin {
//    iosSimulatorArm64()
//}

allprojects {
    properties["DeployVersion"]?.let { version = it }
    repositories {
        mavenCentral()
    }
    tasks.configureEach {
        if (name == "commonizeNativeDistribution") {
            enabled = false
        }
    }
}

dependencies {
    kover(project(":kotlinx-io-core"))
    kover(project(":kotlinx-io-bytestring"))
//    kover(project(":kotlinx-io-okio"))

    dokka(project(":kotlinx-io-bytestring"))
    dokka(project(":kotlinx-io-core"))
//    dokka(project(":kotlinx-io-okio"))
}

kover {
    reports {
        verify {
            rule {
                minBound(95, CoverageUnit.LINE)

                // we allow lower branch coverage, because not all checks in the internal code lead to errors
                minBound(80, CoverageUnit.BRANCH)
            }
        }
    }
}

data class ModuleCoord(val group: String, val name: String, val version: String)
data class RemoteNode(val coord: ModuleCoord, val targetName: String, val files: List<File>)
data class ProjNode(val proj: Project, val targetName: String)

/* -------------------- Helpers -------------------- */

fun String.spmSafe(): String {
    var s = replace(Regex("[^A-Za-z0-9_]"), "_")
    if (s.firstOrNull()?.isLetter() != true && s.firstOrNull() != '_') s = "_$s"
    return s
}

fun createRelativeSymlink(link: java.nio.file.Path, target: java.nio.file.Path) {
    Files.createDirectories(link.parent)
    Files.deleteIfExists(link)
    val linkParentAbs = link.parent.toAbsolutePath().normalize()
    val targetAbs = target.toAbsolutePath().normalize()
    val relTarget = try { linkParentAbs.relativize(targetAbs) } catch (_: Exception) { target }
    println("Creating symlink: LINK=$link -> TARGET=$relTarget")
    Files.createSymbolicLink(link, relTarget)
}

fun createSPMStructure(packageRoot: File, moduleName: String, sources: Map<String, List<File>>) {
    sources.forEach { (setName, files) ->
        val targetDir = File(packageRoot, "Sources/$moduleName/$setName")
        targetDir.mkdirs()
        val used = mutableSetOf<String>()
        files.forEach { file ->
            var name = "${file.name}.swift" // keep .swift suffix in the link filename
            while (!used.add(name)) {
                val dot = name.lastIndexOf('.')
                val base = if (dot >= 0) name.substring(0, dot) else name
                val ext = if (dot >= 0) name.substring(dot) else ""
                name = "${base}_1$ext"
            }
            createRelativeSymlink(targetDir.resolve(name).toPath(), file.toPath())
        }
    }
}

/** Kotlin sources by source set via Kotlin Gradle extension (no hard-coded paths). */
fun Project.collectKotlinSourcesGeneric(preferSetNames: Set<String>): Map<String, List<File>> {
    val ext = extensions.findByType(KotlinProjectExtension::class.java) ?: return emptyMap()
    val allSets = ext.sourceSets.toList()
    val picked = allSets.filter { it.name in preferSetNames }.ifEmpty {
        allSets.filter { it.name == "main" } // fallback for JVM-style
    }
    return picked.associate { ss ->
        val files = ss.kotlin.srcDirs
            .flatMap { dir -> fileTree(dir).matching { include("**/*.kt", "**/*.kts") }.files }
            .filter { it.isFile }
            .sortedBy { it.path }
        ss.name to files
    }.filterValues { it.isNotEmpty() }
}

/** Direct project() deps for given source-set names (+ root impl/api). */
fun directProjectDepsForSets(host: Project, dependencyProject: Project, setNames: Set<String>): Set<Project> {
    val out = linkedSetOf<Project>()
    setNames.forEach { setName ->
        listOf("${setName}Implementation", "${setName}Api").forEach { confName ->
            dependencyProject.configurations.findByName(confName)?.dependencies?.forEach { dep ->
                if (dep is ProjectDependency) out += host.project(dep.path)
            }
        }
    }
    // iosSimulatorArm64() //
//    listOf("implementation", "api").forEach { confName ->
//        dependencyProject.configurations.findByName(confName)?.dependencies?.forEach { dep ->
//            if (dep is ProjectDependency) out += host.project(dep.path)
//        }
//    }
    return out
}

/** Direct EXTERNAL module deps (group:name:version) declared on the given sets (+ root impl/api). */
fun directExternalModulesForSets(p: Project, setNames: Set<String>): Set<ModuleCoord> {
    fun collectFrom(confName: String, acc: MutableSet<ModuleCoord>) {
        println("Collecting direct external modules for 2 ${p.path} @ $confName")
        val conf = p.configurations.findByName(confName) ?: return
        conf.dependencies.forEach { dep ->
            if (dep is ExternalModuleDependency) {
                val g = dep.group
                val n = dep.name
                val v = dep.version
                if (!g.isNullOrBlank() && n.isNotBlank() && !v.isNullOrBlank()) {
                    acc += ModuleCoord(g, n, v)
                }
            }
        }
    }
    val out = linkedSetOf<ModuleCoord>()
    setNames.forEach { set ->
        println("Collecting direct external modules for 1 ${p.path} @ $set")
        collectFrom("${set}Implementation", out)
        collectFrom("${set}Api", out)
    }
    collectFrom("implementation", out)
    collectFrom("api", out)

    return out
}

fun resolveSourcesJar(host: Project, coord: ModuleCoord): File? {
    val notation = "${coord.group}:${coord.name}:${coord.version}:sources@jar"
    val dep = host.dependencies.create(notation)
    val dc = host.configurations.detachedConfiguration(dep).apply { isTransitive = false }
    val files = try { dc.resolve() } catch (_: Exception) { emptySet<File>() }
    return files.firstOrNull()?.takeIf { it.isFile && it.extension == "jar" }
}

fun extractKotlinSources(jar: File, destDir: File): List<File> {
    val out = mutableListOf<File>()
    destDir.mkdirs()
    ZipInputStream(jar.inputStream().buffered()).use { zis ->
        while (true) {
            val e = zis.nextEntry ?: break
            if (e.isDirectory) {
                File(destDir, e.name).mkdirs()
            } else {
                if (e.name.endsWith(".kt") || e.name.endsWith(".kts")) {
                    val tgt = File(destDir, e.name)
                    tgt.parentFile?.mkdirs()
                    tgt.outputStream().use { os -> zis.copyTo(os) }
                    out += tgt
                }
            }
            zis.closeEntry()
        }
    }
    return out
}

fun collectProjectDepGraph(graph: LinkedHashMap<Project, Set<Project>>, host: Project, start: Project, setNames: Set<String>) {
    val kotlinExtension = host.extensions.findByName("kotlin") ?: return
    val kmpExtension = kotlinExtension as? KotlinMultiplatformExtension ?: return
    if (!kmpExtension.targets.names.contains("iosSimulatorArm64")) return
    val seen = mutableSetOf<Project>()
    val q = ArrayDeque<Project>()
//    q.add(host)
    val hostDeps = directProjectDepsForSets(host, start, setNames)
    hostDeps.forEach { q += it }
    graph[host] = hostDeps
    while (q.isNotEmpty()) {
        val dependencyProject = q.removeFirst()
        if (!seen.add(dependencyProject)) continue
        val deps = directProjectDepsForSets(host, dependencyProject, setNames).filter { it != host }.toSet()
        graph[dependencyProject] = deps
        deps.forEach { if (!seen.contains(it)) q += it }
    }
}

/** Create or reuse a remote SPM target by vendoring sources; returns the target's name. */
fun ensureRemoteTarget(
    host: Project,
    coord: ModuleCoord,
    vendorRoot: File,
    pkgRoot: File,
    remoteTargets: MutableMap<ModuleCoord, RemoteNode>
): RemoteNode? {
    remoteTargets[coord]?.let { return it }
    val jar = resolveSourcesJar(host, coord) ?: run {
        println("No sources JAR for ${coord.group}:${coord.name}:${coord.version}; skipping")
        return null
    }
    val extractDir = File(vendorRoot, "${coord.group}.${coord.name}/${coord.version}")
    val extracted = extractKotlinSources(jar, extractDir)
    if (extracted.isEmpty()) {
        println("Sources JAR had no Kotlin files for ${coord.group}:${coord.name}:${coord.version}; skipping")
        return null
    }
    val spmTarget = "${coord.group}_${coord.name}_v${coord.version.replace('.', '_')}".spmSafe()
    createSPMStructure(pkgRoot, spmTarget, mapOf("commonMain" to extracted))
    val node = RemoteNode(coord, spmTarget, extracted)
    remoteTargets[coord] = node
    return node
}

/* -------------------- NEW: Build -Xfragment* using the SPM links (remove .swift) -------------------- */

private fun buildKonanFragmentArgsUsingLinks(
    p: Project,
    preferSetNames: Set<String>,
    pkgRoot: File,
    targetName: String
): List<String> {
    val ext = p.extensions.findByType(KotlinProjectExtension::class.java) ?: return emptyList()
    val allSets: List<KotlinSourceSet> = ext.sourceSets.toList()

    // choose non-test sets; fall back to "main" for JVM-like projects
    val nonTest = allSets.filterNot { it.name.endsWith("Test", true) || it.name.endsWith("AndroidTest", true) }
    val chosen: List<KotlinSourceSet> = when {
        nonTest.any { it.name in preferSetNames } -> nonTest.filter { it.name in preferSetNames }
        nonTest.any { it.name == "main" } -> listOf(nonTest.first { it.name == "main" })
        else -> nonTest
    }
    if (chosen.isEmpty()) return emptyList()

    // Build refinement and depth from chosen sets
    val byName = chosen.associateBy { it.name }
    val parents: Map<String, Set<String>> = chosen.associate { ss ->
        ss.name to ss.dependsOn.mapNotNull { byName[it.name]?.name }.toSet()
    }
    val memo = mutableMapOf<String, Int>()
    fun depth(name: String): Int = memo.getOrPut(name) {
        val ps = parents[name].orEmpty()
        if (ps.isEmpty()) 0 else ps.maxOf { depth(it) } + 1
    }

    val xFragments = chosen.sortedByDescending { depth(it.name) }.joinToString(",") { it.name }
    val xRefines = chosen.flatMap { c -> c.dependsOn.map { pset -> "${c.name}:${pset.name}" } }
        .distinct().joinToString(",")

    // Build Xfragment-sources from symlinked paths (KEEP .swift)
    val spmTargetRoot = File(pkgRoot, "Sources/$targetName")
    val perSetFiles: Map<String, List<String>> = chosen.associate { ss ->
        val setDir = File(spmTargetRoot, ss.name)
        val files = if (setDir.isDirectory) {
            setDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".swift") }
                .map { it.absolutePath.removeSuffix(".swift")}
                .distinct()
                .sorted()
                .toList()
        } else emptyList()
        ss.name to files
    }.filterValues { it.isNotEmpty() }

    val xSources = perSetFiles.entries
        .flatMap { (set, files) -> files.map { f -> "$set:$f" } }
        .joinToString(",")

    val args = mutableListOf<String>()
    args += "-Xfragments=$xFragments"
    if (xRefines.isNotEmpty()) args += "-Xfragment-refines=$xRefines"
    if (xSources.isNotEmpty()) args += "-Xfragment-sources=$xSources"
    args += "-Xmulti-platform"

    // Debug prints to verify we're using symlinks:
    if (xSources.contains("/src/")) {
        println("WARNING: xSources contains '/src/' — expected symlink paths under Sources/. Check link creation order.")
    } else {
        println("xSources built from SPM links for target=$targetName")
    }
    return args
}

/** Remote (vendored) target flags computed from the symlinked files in Sources/<target>/... (remove .swift). */
private fun buildKonanFragmentArgsForRemoteTargetLinks(pkgRoot: File, targetName: String): List<String> {
    val spmTargetRoot = File(pkgRoot, "Sources/$targetName")
    if (!spmTargetRoot.isDirectory) return emptyList()

    // collect set directories under the target (e.g., "commonMain")
    val setNames = spmTargetRoot.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted().orEmpty()
    if (setNames.isEmpty()) return emptyList()

    val xFragments = setNames.joinToString(",")
    val xSources = setNames.flatMap { set ->
        val setDir = File(spmTargetRoot, set)
        setDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".swift") }
            .map { f -> "$set:${f.absolutePath.removeSuffix(".swift")}" }
            .toList()
    }.joinToString(",")

    val args = mutableListOf<String>()
    args += "-Xfragments=$xFragments"
    if (xSources.isNotEmpty()) args += "-Xfragment-sources=$xSources"
    args += "-Xmulti-platform"
    return args
}

/* Swift array serializer */
private fun swiftStringArray(items: List<String>): String =
    items.joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }

/* -------------------- Task -------------------- */

tasks.register("convertThisProjectToSwiftPMBuild") {
    group = "spm"
    description = "Export this KMP project (+ deps) as an SPM package via symlinks and generate Package.swift with cSettings flags from SPM links."

    doLast {
        val moduleName = project.name.spmSafe()
//        val baseAbsProp = project.findProperty("spmBaseAbs") as String?
//            ?: error("Pass base path with -PspmBaseAbs=/ABS/PATH")
//        val baseAbsProp = rootProject.layout.projectDirectory.dir(".").asFile
        val baseAbs = rootProject.layout.projectDirectory.dir(".").asFile
//        require(baseAbs.isAbsolute) { "spmBaseAbs must be absolute: $baseAbsProp" }

        val pkgRoot = baseAbs
        // project.delete(pkgRocot)

        // Source-set universe for dependency discovery (use iOS sim arm64 'main' comp)
        val comp = kotlin.iosSimulatorArm64().compilations.getByName("main")
        val setNamesForDeps: Set<String> = comp.allKotlinSourceSets.map { it.name }.toSet()

        // Main project sources (filtered, for symlinking)
        val mainSourcesBySet: Map<String, List<File>> =
            comp.allKotlinSourceSets.associate { ss ->
                val files = ss.kotlin.sourceDirectories
                    .flatMap { dir -> project.fileTree(dir).matching { include("**/*.kt", "**/*.kts") }.files }
                    .filter { it.isFile }.distinct().sortedBy { it.path }
                ss.name to files
            }.filterValues { it.isNotEmpty() }
        if (mainSourcesBySet.isEmpty()) error("No Kotlin sources to export.")

        // 1) Create main module links first
        createSPMStructure(pkgRoot, moduleName, mainSourcesBySet)

        // Project() dependency graph
        val depGraph = LinkedHashMap<Project, Set<Project>>()
        allprojects.forEach {
            collectProjectDepGraph(depGraph, it, it, setNamesForDeps)
        }
//        error(allprojects.map { it.name })
//        error(depGraph.keys.map { it.name })

        // For each project node, collect its external modules (direct only)
        val allProjects = listOf(project) + depGraph.keys.toList()
        println("All Projects are ${allProjects}")
        val externalsByProject: Map<Project, Set<ModuleCoord>> =
            allProjects.associateWith { p -> directExternalModulesForSets(p, setNamesForDeps) }

        println("ExternalsByProject: $externalsByProject")
        val vendorRoot = File(pkgRoot, ".vendor-cache").apply { mkdirs() }
        val remoteTargets = mutableMapOf<ModuleCoord, RemoteNode>()
        val projectNodes = mutableMapOf<Project, ProjNode>()

        // Main’s direct project deps (for manifest wiring)
//        val mainDirectProjDeps = directProjectDepsForSets(project, project, setNamesForDeps)
//        val mainDirectProjTargetNames = mainDirectProjDeps.map { it.name.spmSafe() }.toSet()

        // 2) Mirror each project() node’s sources and remember its target name (create links BEFORE computing flags)
        depGraph.keys.forEach { p ->
            val name = p.name.spmSafe()
            val srcs = p.collectKotlinSourcesGeneric(setNamesForDeps)
            if (srcs.isNotEmpty()) {
                createSPMStructure(pkgRoot, name, srcs)
                projectNodes[p] = ProjNode(p, name)
            } else {
                println("Skipping ${p.path} (${p.name}): no sources matching $setNamesForDeps (or 'main')")
            }
        }
        // 3) Realize remote targets (download + link) so we can compute flags from links
        externalsByProject[project].orEmpty().forEach {
            ensureRemoteTarget(project, it, vendorRoot, pkgRoot, remoteTargets)
        }

        println("RemoteTargets: $remoteTargets")


        projectNodes.keys.forEach { prj ->
            externalsByProject[prj].orEmpty().forEach {
                ensureRemoteTarget(project, it, vendorRoot, pkgRoot, remoteTargets)
            }
        }

        /* --------- Build per-target OTHER_CFLAGS payloads from SPM links --------- */
        val mainCOtherFlags: List<String> =
            buildKonanFragmentArgsUsingLinks(project, setNamesForDeps, pkgRoot, moduleName)

        val perProjCOtherFlags: Map<Project, List<String>> =
            projectNodes.mapValues { (proj, pn) ->
                buildKonanFragmentArgsUsingLinks(proj, setNamesForDeps, pkgRoot, pn.targetName)
            }

        val perRemoteCOtherFlags: Map<String, List<String>> =
            remoteTargets.values.associate { rn ->
                rn.targetName to buildKonanFragmentArgsForRemoteTargetLinks(pkgRoot, rn.targetName)
            }

        // ---------- Package.swift ----------
        val packageSwift = File(pkgRoot, "Package.swift")
        val manifest = buildString {
            appendLine("// swift-tools-version: 6.0")
            appendLine("import PackageDescription")
            appendLine()
            appendLine("let package = Package(")
            appendLine("    name: \"$moduleName\",")
            appendLine("    platforms: [.iOS(.v15), .macOS(.v13)],")
            appendLine("    products: [")
            val targetNames = projectNodes.values.joinToString(", ") { "\"${it.targetName}\"" }
            appendLine("        .library(name: \"$moduleName\", targets: [${targetNames}]),")
            appendLine("    ],")
            appendLine("    targets: [")

            // Main target: depends on direct project() targets + its direct remote targets
//            val mainRemoteDeps = externalsByProject[project].orEmpty()
//                .mapNotNull { remoteTargets[it]?.targetName }
//                .sorted()
//            val mainDepsNames = (mainDirectProjTargetNames + mainRemoteDeps).sorted()
//
//            val mainCFlagsLine =
//                if (mainCOtherFlags.isNotEmpty())
//                    "            , cSettings: [.unsafeFlags([${swiftStringArray(mainCOtherFlags)}])]"
//                else ""
//            appendLine("        .target(")
//            appendLine("            name: \"$moduleName\",")
//            appendLine("            dependencies: [${mainDepsNames.joinToString(", ") { "\"$it\"" }}],")
//            appendLine("            path: \"Sources/$moduleName\"$mainCFlagsLine")
//            appendLine("        )" + if (projectNodes.isNotEmpty() || remoteTargets.isNotEmpty()) "," else "")

            // For each project() node
            val projList = projectNodes.values.toList()
            projList.forEachIndexed { idx, pn ->
                val projRemoteDeps = externalsByProject[pn.proj].orEmpty()
                    .mapNotNull { remoteTargets[it]?.targetName }
                    .sorted()
                val projDirectProjDeps = depGraph[pn.proj].orEmpty()
                    .mapNotNull { d -> projectNodes[d]?.targetName }
                    .sorted()
                val depsForProj = (projDirectProjDeps + projRemoteDeps).sorted()
                val flags = perProjCOtherFlags[pn.proj].orEmpty()
                val cFlagsLine =
                    if (flags.isNotEmpty())
                        "            , cSettings: [.unsafeFlags([${swiftStringArray(flags)}])]"
                    else ""
                appendLine("        .target(")
                appendLine("            name: \"${pn.targetName}\",")
                appendLine("            dependencies: [${depsForProj.joinToString(", ") { "\"$it\"" }}],")
                appendLine("            path: \"Sources/${pn.targetName}\"$cFlagsLine")
                appendLine("        )" + if (idx != projList.lastIndex || remoteTargets.isNotEmpty()) "," else "")
            }

            // Vendored targets — flags from symlinked files
            val remList = remoteTargets.values.toList()
            remList.forEachIndexed { idx, rn ->
                val rFlags = perRemoteCOtherFlags[rn.targetName].orEmpty()
                val rCFlagsLine =
                    if (rFlags.isNotEmpty())
                        "            , cSettings: [.unsafeFlags([${swiftStringArray(rFlags)}])]"
                    else ""
                appendLine("        .target(")
                appendLine("            name: \"${rn.targetName}\",")
                appendLine("            path: \"Sources/${rn.targetName}\"$rCFlagsLine")
                appendLine("        )" + if (idx != remList.lastIndex) "," else "")
            }

            appendLine("    ]")
            appendLine(")")
        }
        packageSwift.writeText(manifest)

        println("SPM package root: $pkgRoot")
        println("Wrote: ${packageSwift.path}")
        println("NOTE: -Xfragment-sources now points to SPM symlink paths (with .swift).")
    }
}
