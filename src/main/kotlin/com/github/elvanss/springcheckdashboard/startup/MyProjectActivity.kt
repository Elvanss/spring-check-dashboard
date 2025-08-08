package com.github.elvanss.springcheckdashboard.startup

import com.github.elvanss.springcheckdashboard.listener.SpringPsiChangeListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val psiManager = PsiManager.getInstance(project)
        psiManager.addPsiTreeChangeListener(SpringPsiChangeListener(), project)
    }
}