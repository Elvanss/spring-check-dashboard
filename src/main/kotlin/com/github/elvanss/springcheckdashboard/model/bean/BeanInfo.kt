package com.github.elvanss.springcheckdashboard.model.bean

import com.intellij.psi.PsiElement

data class BeanInfo(
    val beanName: String,
    val beanType: String,
    val targetElement: PsiElement
) {
    override fun toString(): String = "$beanName [$beanType]"
}
