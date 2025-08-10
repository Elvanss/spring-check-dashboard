package com.github.elvanss.springcheckdashboard.services.Bean

import com.github.elvanss.springcheckdashboard.model.Bean.BeanInfo
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class SpringBeanDetector {

    private val BEAN_ANNOS = listOf(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
        "org.springframework.context.annotation.Configuration"
    )
    private val CONFIGURATION = "org.springframework.context.annotation.Configuration"
    private val BEAN_METHOD = "org.springframework.context.annotation.Bean"

    fun detectBeansForModule(module: Module): List<BeanInfo> {
        val project = module.project
        val moduleScope = GlobalSearchScope.moduleScope(module)
        val allScope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)

        // 1) Tìm tất cả PsiClass trong module có 1 trong các bean annotations
        val classes: List<PsiClass> = buildList {
            BEAN_ANNOS.forEach { fqn ->
                val anno = facade.findClass(fqn, allScope) ?: return@forEach
                addAll(AnnotatedElementsSearch.searchPsiClasses(anno, moduleScope).findAll())
            }
        }.distinctBy { it.qualifiedName ?: it.name }

        val out = mutableListOf<BeanInfo>()

        // 2) Cho mỗi class: tạo BeanInfo cho chính class + nếu là @Configuration thì quét @Bean methods (UAST)
        classes.forEach { psiClass ->
            val uClass = psiClass.toUElement(UClass::class.java)

            // 2.1 Bean cho chính class (Component/Service/Repository/Controller/RestController/Configuration)
            val beanType = firstMatchingAnnotationShortName(psiClass, BEAN_ANNOS)
            out += BeanInfo(
                beanName = psiClass.name ?: "UnknownBean",
                beanType = beanType ?: "Bean",
                targetElement = psiClass.navigationElement
            )

            // 2.2 Nếu class là @Configuration → lấy @Bean methods (qua UAST để hỗ trợ Kotlin)
            val isConfiguration = hasAnnotation(psiClass, CONFIGURATION) ||
                    (uClass?.uAnnotations?.any { it.qualifiedName == CONFIGURATION } == true)

            if (isConfiguration && uClass != null) {
                uClass.methods.forEach { uMethod ->
                    val hasBean = uMethod.uAnnotations.any { it.qualifiedName == BEAN_METHOD }
                    if (hasBean) {
                        out += BeanInfo(
                            beanName = uMethod.name,
                            beanType = "Bean",
                            targetElement = uMethod.sourcePsi ?: uMethod.javaPsi
                        )
                    }
                }
            }
        }

        return out
    }

    /** Helpers */

    private fun hasAnnotation(psiClass: PsiClass, annoFqn: String): Boolean =
        psiClass.annotations.any { it.qualifiedName == annoFqn }

    private fun firstMatchingAnnotationShortName(psiClass: PsiClass, annos: List<String>): String? {
        val qns = psiClass.annotations.mapNotNull { it.qualifiedName }.toSet()
        return annos.firstOrNull { it in qns }?.substringAfterLast('.')
    }
}
