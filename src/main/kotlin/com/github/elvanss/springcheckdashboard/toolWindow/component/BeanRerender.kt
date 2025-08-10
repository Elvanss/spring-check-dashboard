package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.Bean.BeanInfo
import com.github.elvanss.springcheckdashboard.services.Bean.SpringBeanDetector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.ui.treeStructure.Tree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class BeanRerender {

    companion object {

        private data class DisplayBean(val info: BeanInfo) {
            override fun toString(): String = "• ${info.beanName} [${info.beanType}]"
        }

        fun loadBeans(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringBeanDetector()
            val rootNode = DefaultMutableTreeNode("Spring Beans")
            val app = ApplicationManager.getApplication()

            app.executeOnPooledThread {
                val moduleNodes = app.runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val beans = detector.detectBeansForModule(module)
                        if (beans.isEmpty()) return@mapNotNull null

                        DefaultMutableTreeNode(module.name).also { moduleNode ->
                            beans.forEach { bean ->
                                moduleNode.add(DefaultMutableTreeNode(DisplayBean(bean)))
                            }
                        }
                    }
                }

                SwingUtilities.invokeLater {
                    rootNode.removeAllChildren()
                    moduleNodes.forEach { rootNode.add(it) }
                    model.setRoot(rootNode)
                    expandAll(tree)
                }
            }

            tree.addTreeSelectionListener { e ->
                val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
                val endpoint = when (val uo = node.userObject) {
                    is DisplayBean -> uo.info
                    is BeanInfo -> uo
                    else -> null
                } ?: return@addTreeSelectionListener

                (endpoint.targetElement as? Navigatable)
                    ?.takeIf { it.canNavigateToSource() }
                    ?.navigate(true)
            }
        }

        private fun expandAll(tree: Tree) {
            var i = 0
            while (i < tree.rowCount) {
                tree.expandRow(i)
                i++
            }
        }
    }
}
