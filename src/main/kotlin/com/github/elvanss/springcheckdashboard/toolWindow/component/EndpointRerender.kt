package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.services.Endpoint.SpringEndpointDetector
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

class EndpointRerender {
    companion object {
        fun loadEndpoints(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringEndpointDetector()
            val rootNode = DefaultMutableTreeNode("Spring Endpoints")

            ApplicationManager.getApplication().executeOnPooledThread {
                val moduleNodes = ApplicationManager.getApplication().runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val ctrls = detector.detectControllersForModule(module)
                        if (ctrls.isNotEmpty()) {
                            val moduleNode = DefaultMutableTreeNode(module.name)
                            ctrls.forEach { ctrl ->
                                val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                                ctrl.methods.forEach { method ->
                                    val displayNode = DefaultMutableTreeNode(object {
                                        override fun toString(): String = "• [${method.httpMethod}] ${method.path}"
                                        fun getEndpointInfo(): EndpointInfo = method
                                    })
                                    ctrlNode.add(displayNode)
                                }
                                moduleNode.add(ctrlNode)
                            }
                            moduleNode
                        } else null
                    }
                }
                SwingUtilities.invokeLater {
                    moduleNodes.forEach { rootNode.add(it) }
                    model.setRoot(rootNode)
                    expandAll(tree)
                }
            }

            tree.addTreeSelectionListener { e ->
                val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
                val data = node.userObject
                val endpointInfo = when (data) {
                    is EndpointInfo -> data
                    else -> (data as? Any)?.let {
                        it::class.members.find { m -> m.name == "getEndpointInfo" }?.call(it) as? EndpointInfo
                    }
                }
                if (endpointInfo != null) {
                    (endpointInfo.targetElement as? Navigatable)?.takeIf { it.canNavigateToSource() }?.navigate(true)
                }
            }
        }

        fun loadBeans(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringBeanDetector()
            val rootNode = DefaultMutableTreeNode("Spring Beans")

            ApplicationManager.getApplication().executeOnPooledThread {
                val moduleNodes = ApplicationManager.getApplication().runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val beans = detector.detectBeansForModule(module)
                        if (beans.isNotEmpty()) {
                            val moduleNode = DefaultMutableTreeNode(module.name)
                            beans.forEach { bean ->
                                val displayNode = DefaultMutableTreeNode(object {
                                    override fun toString(): String = "• ${bean.beanName} [${bean.beanType}]"
                                    fun getBeanInfo(): BeanInfo = bean
                                })
                                moduleNode.add(displayNode)
                            }
                            moduleNode
                        } else null
                    }
                }
                SwingUtilities.invokeLater {
                    moduleNodes.forEach { rootNode.add(it) }
                    model.setRoot(rootNode)
                    expandAll(tree)
                }
            }

            tree.addTreeSelectionListener { e ->
                val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
                val data = node.userObject
                val beanInfo = when (data) {
                    is BeanInfo -> data
                    else -> (data as? Any)?.let {
                        it::class.members.find { m -> m.name == "getBeanInfo" }?.call(it) as? BeanInfo
                    }
                }
                if (beanInfo != null) {
                    (beanInfo.targetElement as? Navigatable)?.takeIf { it.canNavigateToSource() }?.navigate(true)
                }
            }
        }

        private fun expandAll(tree: Tree) {
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }
    }
}