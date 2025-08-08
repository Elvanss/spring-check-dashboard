package com.github.elvanss.springcheckdashboard.services

import com.github.elvanss.springcheckdashboard.model.ControllerInfo
import com.github.elvanss.springcheckdashboard.model.EndpointInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class SpringEndpointDetector {

    fun detectControllersForModule(module: Module): List<ControllerInfo> {
        val project = module.project
        val moduleScope = GlobalSearchScope.moduleScope(module)
        val allScope = GlobalSearchScope.allScope(project) // Để tìm annotation trong thư viện

        val controllers = mutableListOf<ControllerInfo>()
        val javaPsiFacade = JavaPsiFacade.getInstance(project)

        // Tìm annotation class trong thư viện
        val restControllerAnno = javaPsiFacade.findClass(
            "org.springframework.web.bind.annotation.RestController",
            allScope
        )
        val controllerAnno = javaPsiFacade.findClass(
            "org.springframework.stereotype.Controller",
            allScope
        )

        // Tìm các class trong module có gắn annotation này
        val annotatedClasses = mutableListOf<PsiClass>()
        if (restControllerAnno != null) {
            annotatedClasses += AnnotatedElementsSearch
                .searchPsiClasses(restControllerAnno, moduleScope)
                .findAll()
        }
        if (controllerAnno != null) {
            annotatedClasses += AnnotatedElementsSearch
                .searchPsiClasses(controllerAnno, moduleScope)
                .findAll()
        }

        val allClasses = annotatedClasses.distinct()

        // Giữ nguyên toàn bộ logic xử lý gốc
        for (cls in allClasses) {
            if (isController(cls)) {
                val basePath = extractClassLevelPath(cls) ?: ""
                val methods = cls.methods.mapNotNull { extractMethodInfo(it, basePath) }
                if (methods.isNotEmpty()) {
                    controllers.add(
                        ControllerInfo(
                            moduleName = module.name,
                            controllerName = cls.name ?: "UnknownController",
                            methods = methods
                        )
                    )
                }
            }
        }

        return controllers
    }



    @Suppress("unused")
//    fun detectControllers(project: Project): List<ControllerInfo> {
//        val scope = GlobalSearchScope.projectScope(project)
//        val psiCache = PsiShortNamesCache.getInstance(project)
//
//        val controllers = mutableListOf<ControllerInfo>()
//
//        val allClasses = psiCache.allClassNames.flatMap { className ->
//            psiCache.getClassesByName(className, scope).toList()
//        }
//
//        for (cls in allClasses) {
//            if (isController(cls)) {
//                val basePath = extractClassLevelPath(cls) ?: ""
//                val methods = cls.methods.mapNotNull { extractMethodInfo(it, basePath) }
//                if (methods.isNotEmpty()) {
//                    controllers.add(
//                        ControllerInfo(
//                            moduleName = detectModuleName(cls),
//                            controllerName = cls.name ?: "UnknownController",
//                            methods = methods
//                        )
//                    )
//                }
//            }
//        }
//
//        return controllers
//    }

    private fun isController(cls: PsiClass): Boolean {
        val annos = cls.annotations.mapNotNull { it.qualifiedName }
        return annos.any {
            it == "org.springframework.web.bind.annotation.RestController" ||
                    it == "org.springframework.stereotype.Controller"
        }
    }

    private fun extractClassLevelPath(cls: PsiClass): String? {
        for (anno in cls.annotations) {
            if (anno.qualifiedName == "org.springframework.web.bind.annotation.RequestMapping") {
                return extractPath(anno)
            }
        }
        return null
    }

    private fun extractMethodInfo(method: PsiMethod, basePath: String): EndpointInfo? {
        for (anno in method.annotations) {
            val qName = anno.qualifiedName ?: continue
            val httpMethod = when (qName) {
                "org.springframework.web.bind.annotation.GetMapping" -> "GET"
                "org.springframework.web.bind.annotation.PostMapping" -> "POST"
                "org.springframework.web.bind.annotation.PutMapping" -> "PUT"
                "org.springframework.web.bind.annotation.DeleteMapping" -> "DELETE"
                "org.springframework.web.bind.annotation.PatchMapping" -> "PATCH"
                "org.springframework.web.bind.annotation.RequestMapping" -> {
                    extractRequestMappingMethod(anno) ?: "REQUEST"
                }
                else -> null
            }

            if (httpMethod != null) {
                val path = combinePaths(basePath, extractPath(anno))
                return EndpointInfo(
                    path = path,
                    httpMethod = httpMethod,
                    methodName = method.name,
                    targetElement = method
                )
            }
        }
        return null
    }

    private fun extractPath(anno: PsiAnnotation): String? {
        val pathValue = anno.findAttributeValue("value") ?: anno.findAttributeValue("path")
        return pathValue?.text?.replace("\"", "")
    }

    private fun extractRequestMappingMethod(anno: PsiAnnotation): String? {
        val methodAttr = anno.findAttributeValue("method")?.text ?: return null
        return methodAttr
            .removePrefix("{")
            .removeSuffix("}")
            .split(",").joinToString(",") { it.trim().substringAfter("RequestMethod.") }
            .ifBlank { "REQUEST" }
    }

    private fun combinePaths(base: String?, sub: String?): String {
        val basePart = base?.trim('/') ?: ""
        val subPart = sub?.trim('/') ?: ""
        return "/${listOf(basePart, subPart).filter { it.isNotEmpty() }.joinToString("/")}"
    }

    private fun detectModuleName(cls: PsiClass): String {
        val filePath = cls.containingFile.virtualFile.path
        // Ví dụ: /.../ms-user/src/main/java/... => lấy ms-user
        return filePath.split("/").find { it.startsWith("ms-") } ?: "default-module"
    }
}
