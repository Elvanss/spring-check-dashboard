package com.github.elvanss.springcheckdashboard.services.endpoint

import com.github.elvanss.springcheckdashboard.model.endpoint.ControllerInfo
import com.github.elvanss.springcheckdashboard.model.endpoint.EndpointInfo
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import org.jetbrains.uast.*
class SpringEndpointDetector {

    private val REQ_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
    private val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
    private val CONTROLLER = "org.springframework.stereotype.Controller"

    private val SHORT_TO_HTTP = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH"
    )

    fun detectControllersForModule(module: Module): List<ControllerInfo> {
        val project = module.project
        val moduleScope = GlobalSearchScope.moduleScope(module)
        val allScope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)

        val restCtrlAnno = facade.findClass(REST_CONTROLLER, allScope)
        val ctrlAnno = facade.findClass(CONTROLLER, allScope)

        val psiClasses = buildList<PsiClass> {
            if (restCtrlAnno != null) {
                addAll(AnnotatedElementsSearch.searchPsiClasses(restCtrlAnno, moduleScope).findAll())
            }
            if (ctrlAnno != null) {
                addAll(AnnotatedElementsSearch.searchPsiClasses(ctrlAnno, moduleScope).findAll())
            }
        }.distinct()

        val results = mutableListOf<ControllerInfo>()

        psiClasses.forEach { psiClass ->
            val uClass = psiClass.toUElement(UClass::class.java) ?: return@forEach
            val classPrefixes = extractPathsFrom(uClass.uAnnotations).ifEmpty { listOf("") }
            val methods = mutableListOf<EndpointInfo>()

            uClass.methods.forEach { uMethod ->
                methods += endpointsFromMethod(uMethod, classPrefixes)
            }

            if (methods.isNotEmpty()) {
                results += ControllerInfo(
                    moduleName = module.name,
                    controllerName = uClass.qualifiedName ?: (uClass.name ?: "UnknownController"),
                    methods = methods
                )
            }
        }

        return results
    }

    private fun endpointsFromMethod(uMethod: UMethod, classPrefixes: List<String>): List<EndpointInfo> {
        val out = mutableListOf<EndpointInfo>()
        val annos = uMethod.uAnnotations

        val pathsByHttp = linkedMapOf<String, MutableList<String>>()
        for (ann in annos) {
            val qn = ann.qualifiedName ?: continue
            val http = SHORT_TO_HTTP[qn] ?: continue
            val paths = extractPathsFrom(ann).ifEmpty { listOf("") }
            pathsByHttp.getOrPut(http) { mutableListOf() }.addAll(paths)
        }

        annos.firstOrNull { it.qualifiedName == REQ_MAPPING }?.let { ann ->
            val paths = extractPathsFrom(ann).ifEmpty { listOf("") }
            val methods = readRequestMappingMethods(ann)
            if (methods.isEmpty()) {
                pathsByHttp.getOrPut("UNKNOWN") { mutableListOf() }.addAll(paths)
            } else {
                methods.forEach { m -> pathsByHttp.getOrPut(m) { mutableListOf() }.addAll(paths) }
            }
        }

        if (pathsByHttp.isEmpty()) return out

        val psi = uMethod.sourcePsi
        for ((http, methodPaths) in pathsByHttp) {
            for (cp in classPrefixes) {
                for (mp in methodPaths) {
                    val merged = normalizePath(mergePath(cp, mp))
                    out += EndpointInfo(
                        path = merged,
                        httpMethod = http,
                        methodName = uMethod.name,
                        targetElement = psi ?: uMethod.javaPsi
                    )
                }
            }
        }
        return out
    }

    private fun extractPathsFrom(annotations: List<UAnnotation>): List<String> =
        annotations.flatMap { extractPathsFrom(it) }

    private fun extractPathsFrom(ann: UAnnotation): List<String> {
        val p = readStringArrayAttr(ann, "path")
        if (p.isNotEmpty()) return p
        return readStringArrayAttr(ann, "value")
    }

    private fun readStringArrayAttr(ann: UAnnotation, name: String): List<String> {
        val expr = ann.findDeclaredAttributeValue(name) ?: return emptyList()
        fun fromExpr(e: UExpression?): List<String> = when (e) {
            null -> emptyList()
            is ULiteralExpression -> (e.value as? String)?.let { listOf(it) } ?: emptyList()
            is UExpressionList -> e.expressions.flatMap { fromExpr(it) }
            is UCallExpression -> if (e.methodName == "arrayOf")
                e.valueArguments.flatMap { fromExpr(it) }
            else (e.evaluate() as? String)?.let { listOf(it) } ?: emptyList()
            else -> (e.evaluate() as? String)?.let { listOf(it) } ?: emptyList()
        }
        return fromExpr(expr)
    }

    private fun readRequestMappingMethods(ann: UAnnotation): List<String> {
        val expr = ann.findDeclaredAttributeValue("method") ?: return emptyList()
        fun mapOne(e: UExpression?): String? {
            if (e == null) return null
            val raw = (e as? ULiteralExpression)?.value?.toString() ?: e.asRenderString()
            val up = raw.uppercase()
            return when {
                up.endsWith(".GET") || up == "GET" -> "GET"
                up.endsWith(".POST") || up == "POST" -> "POST"
                up.endsWith(".PUT") || up == "PUT" -> "PUT"
                up.endsWith(".DELETE") || up == "DELETE" -> "DELETE"
                up.endsWith(".PATCH") || up == "PATCH" -> "PATCH"
                up.endsWith(".OPTIONS") || up == "OPTIONS" -> "OPTIONS"
                up.endsWith(".HEAD") || up == "HEAD" -> "HEAD"
                else -> null
            }
        }
        return when (expr) {
            is UExpressionList -> expr.expressions.mapNotNull { mapOne(it) }
            else -> listOfNotNull(mapOne(expr))
        }
    }

    private fun mergePath(a: String?, b: String?): String {
        val left = (a ?: "").trim()
        val right = (b ?: "").trim()
        val l = if (left.endsWith("/")) left.dropLast(1) else left
        val r = if (right.startsWith("/")) right else "/$right"
        return "$l$r"
    }

    private fun normalizePath(p: String): String =
        p.replace(Regex("//+"), "/").removeSuffix("/").ifEmpty { "/" }
}
