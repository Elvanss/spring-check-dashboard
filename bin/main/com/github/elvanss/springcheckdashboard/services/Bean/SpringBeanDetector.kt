package com.github.elvanss.springcheckdashboard.services.Bean

import com.github.elvanss.springcheckdashboard.model.Bean.BeanInfo
import com.intellij.openapi.module.Module
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class SpringBeanDetector {

    private val beanAnnotations = listOf(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
        "org.springframework.context.annotation.Configuration"
    )

    fun detectBeansForModule(module: Module): List<BeanInfo> {
        val project = module.project
        val moduleScope = GlobalSearchScope.moduleScope(module)
        val allScope = GlobalSearchScope.allScope(project)

        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val beans = mutableListOf<BeanInfo>()

        for (annoFqn in beanAnnotations) {
            val annoClass = javaPsiFacade.findClass(annoFqn, allScope) ?: continue
            val annotatedClasses: Collection<PsiClass> =
                AnnotatedElementsSearch.searchPsiClasses(annoClass, moduleScope).findAll()

            for (cls in annotatedClasses) {
                beans.add(
                    BeanInfo(
                        beanName = cls.name ?: "UnknownBean",
                        beanType = annoFqn.substringAfterLast("."),
                        targetElement = cls.navigationElement
                    )
                )

                // Nếu là @Configuration thì tìm @Bean methods
                if (annoFqn == "org.springframework.context.annotation.Configuration") {
                    for (method in cls.methods) {
                        if (method.hasAnnotation("org.springframework.context.annotation.Bean")) {
                            beans.add(
                                BeanInfo(
                                    beanName = method.name,
                                    beanType = "Bean",
                                    targetElement = method
                                )
                            )
                        }
                    }
                }
            }
        }

        return beans
    }
}
