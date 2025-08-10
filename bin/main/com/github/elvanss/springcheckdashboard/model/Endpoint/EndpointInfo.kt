package com.github.elvanss.springcheckdashboard.model.Endpoint

import com.intellij.psi.PsiElement
data class EndpointInfo(
    val httpMethod: String,
    val path: String,
    val methodName: String,
    val targetElement: PsiElement
) {
    override fun toString(): String {
        return "[$httpMethod] $path"
    }
}