package com.github.elvanss.springcheckdashboard.actions

import com.github.elvanss.springcheckdashboard.openapi.OpenApiGenerator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class GenerateOpenApiAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        OpenApiGenerator().generate(project)
    }
}
