package com.github.elvanss.springcheckdashboard.services.Service

import com.github.elvanss.springcheckdashboard.model.service.ServiceInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class SpringServiceDetector {

    private val SPRING_BOOT_ANNOTATION = "org.springframework.boot.autoconfigure.SpringBootApplication"

    fun detectServicesForModule(module: Module): List<ServiceInfo> {
        val project = module.project
        val allScope = GlobalSearchScope.allScope(project)
        val moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
        val facade = JavaPsiFacade.getInstance(project)

        // tìm class @SpringBootApplication trong module
        val candidates: Collection<PsiClass> = buildList {
            val anno = facade.findClass(SPRING_BOOT_ANNOTATION, allScope)
            if (anno != null) {
                addAll(AnnotatedElementsSearch.searchPsiClasses(anno, moduleScope).findAll())
            }
        }

        val out = mutableListOf<ServiceInfo>()

        if (candidates.isNotEmpty()) {
            candidates.forEach { psiClass ->
                val fqn = psiClass.qualifiedName ?: return@forEach
                val name = psiClass.name ?: module.name
                val port = detectPort(module)
                out += ServiceInfo(
                    module = module,
                    moduleName = module.name,
                    serviceName = name,
                    mainClassFqn = fqn,
                    port = port
                )
            }
            return out
        }

        // Fallback: tìm main method + SpringApplication.run(...)
        val mains = findMainClassesInModule(module)
        mains.forEach { cls ->
            val fqn = cls.qualifiedName ?: return@forEach
            val name = cls.name ?: module.name
            val port = detectPort(module)
            out += ServiceInfo(
                module = module,
                moduleName = module.name,
                serviceName = name,
                mainClassFqn = fqn,
                port = port
            )
        }

        return out
    }

    private fun findMainClassesInModule(module: Module): List<PsiClass> {
        val project = module.project
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, /*includeTestScope=*/false)
        val cache = PsiShortNamesCache.getInstance(project)

        // Lọc theo vài “hint” tên phổ biến để tránh quét toàn bộ index
        val nameHints = setOf("Application", "Main", "App", "Server")

        val results = LinkedHashSet<PsiClass>() // giữ thứ tự + unique theo identity
        // Duyệt tất cả tên class trong index, nhưng chỉ lấy những tên khớp hint
        for (name in cache.allClassNames) {
            if (nameHints.any { name.endsWith(it) }) {
                val classes = cache.getClassesByName(name, scope)
                classes.forEach { cls ->
                    if (hasMainMethod(cls)) results += cls
                }
            }
        }
        return results.toList()
    }


    private fun hasMainMethod(c: PsiClass): Boolean {
        val m: PsiMethod? = c.findMethodsByName("main", false).firstOrNull()
        return m != null && m.parameterList.parametersCount == 1 &&
                m.parameterList.parameters[0].type.canonicalText == "java.lang.String[]"
    }

    private fun detectPort(module: Module): Int? {
        val project = module.project
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)

        // application.properties
        FilenameIndex.getVirtualFilesByName(project, "application.properties", scope).firstOrNull()?.let { vf ->
            try {
                VfsUtilCore.loadText(vf).lineSequence().forEach { line ->
                    val t = line.trim()
                    if (t.startsWith("#")) return@forEach
                    val m = Regex("""^server\.port\s*=\s*(\d{2,5})\s*$""").find(t)
                    if (m != null) return m.groupValues[1].toInt()
                }
            } catch (_: Throwable) {}
        }

        // application.yml / application.yaml
        val yaml = FilenameIndex.getVirtualFilesByName(project, "application.yml", scope).firstOrNull()
            ?: FilenameIndex.getVirtualFilesByName(project, "application.yaml", scope).firstOrNull()
        if (yaml != null) {
            try {
                val text = VfsUtilCore.loadText(yaml)
                // bắt dạng: server:\n  port: 8081  (rất giản lược)
                val rx = Regex("""(?s)server\s*:\s*.*?port\s*:\s*(\d{2,5})""")
                val m = rx.find(text)
                if (m != null) return m.groupValues[1].toInt()
            } catch (_: Throwable) {}
        }

        return null
    }
}
