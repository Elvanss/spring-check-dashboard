package com.github.elvanss.springcheckdashboard.listener

import com.github.elvanss.springcheckdashboard.services.SpringEndpointService
import com.intellij.openapi.components.service
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class SpringPsiChangeListener : PsiTreeChangeListener {

    companion object {
        private val SPRING_ANNOTATIONS = setOf(
            "RestController", "Controller", "RequestMapping",
            "GetMapping", "PostMapping", "PutMapping",
            "DeleteMapping", "PatchMapping"
        )
    }

    override fun childAdded(event: PsiTreeChangeEvent) = scanFileIfNeeded(event)
    override fun childRemoved(event: PsiTreeChangeEvent) = scanFileIfNeeded(event)
    override fun childReplaced(event: PsiTreeChangeEvent) = scanFileIfNeeded(event)
    override fun childMoved(event: PsiTreeChangeEvent) = scanFileIfNeeded(event)
    override fun childrenChanged(event: PsiTreeChangeEvent) = scanFileIfNeeded(event)

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {}
    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}
    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}
    override fun beforeChildMovement(event: PsiTreeChangeEvent) {}
    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {}
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {}
    override fun propertyChanged(event: PsiTreeChangeEvent) {}

    private fun scanFileIfNeeded(event: PsiTreeChangeEvent) {
        val file = event.file as? PsiJavaFile ?: return

        // Kiểm tra xem file có chứa annotation Spring không
        if (file.text.contains("@RestController") || file.text.contains("@Controller")) {
            val project = file.project
            val endpointService = project.service<SpringEndpointService>()

            val endpoints = mutableListOf<String>()

            // Lặp qua tất cả các phương thức trong file
            PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).forEach { method ->
                method.modifierList.annotations.forEach { annotation ->
                    val annotationName = annotation.qualifiedName?.substringAfterLast(".") ?: return@forEach
                    if (SPRING_ANNOTATIONS.contains(annotationName)) {
                        val path = annotation.parameterList.attributes.firstOrNull()?.value?.text ?: ""
                        endpoints.add("${annotationName.replace("Mapping", "").uppercase()} $path")
                    }
                }
            }

            // Cập nhật cache endpoints
            endpointService.updateEndpoints(file.virtualFile.path, endpoints)
        }
    }
}
