package com.github.elvanss.springcheckdashboard.services.service

import com.github.elvanss.springcheckdashboard.model.service.ServiceInfo
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.PsiManager

class SpringServiceDetector {

    private val SPRING_BOOT_ANNOTATION = "org.springframework.boot.autoconfigure.SpringBootApplication"

    fun detectServicesForModule(module: Module): List<ServiceInfo> {
        val project = module.project
        val allScope = GlobalSearchScope.allScope(project)
        val moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
        val facade = JavaPsiFacade.getInstance(project)

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
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, /* includeTests = */false)
        val cache = PsiShortNamesCache.getInstance(project)

        val nameHints = setOf("Application", "Main", "App", "Server")

        val results = LinkedHashSet<PsiClass>()
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

    private fun findFileByName(module: Module, filename: String): PsiFile? {
        val project = module.project
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
        val psiManager = PsiManager.getInstance(project)

        return FilenameIndex
            .getAllFilesByExt(project, filename.substringAfterLast('.'), scope)
            .firstOrNull { it.name == filename }
            ?.let { psiManager.findFile(it) }
    }

    private fun detectPort(module: Module): Int? {
        // application.properties
        findFileByName(module, "application.properties")?.let { psi ->
            psi.text.lineSequence().forEach { line ->
                val t = line.trim()
                if (!t.startsWith("#")) {
                    Regex("""^server\.port\s*=\s*(\d{2,5})\s*$""")
                        .find(t)
                        ?.groupValues?.get(1)
                        ?.toInt()
                        ?.let { return it }
                }
            }
        }

        // application.yml / application.yaml
        val ymlPsi = findFileByName(module, "application.yml")
            ?: findFileByName(module, "application.yaml")

        if (ymlPsi != null) {
            val rx = Regex("""(?s)server\s*:\s*.*?port\s*:\s*(\d{2,5})""")
            rx.find(ymlPsi.text)?.groupValues?.get(1)?.toInt()?.let { return it }
        }

        return null
    }

}
