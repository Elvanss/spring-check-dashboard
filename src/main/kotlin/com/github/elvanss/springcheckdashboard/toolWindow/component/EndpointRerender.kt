package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.services.endpoint.SpringEndpointDetector
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

        /** Wrapper để hiển thị text đẹp mà vẫn giữ EndpointInfo trong node */
        private data class DisplayEndpoint(val info: EndpointInfo) {
            override fun toString(): String = "• [${info.httpMethod}] ${info.path}"
        }

        fun loadEndpoints(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringEndpointDetector()
            val rootNode = DefaultMutableTreeNode("Spring Endpoints")
            val app = ApplicationManager.getApplication()

            app.executeOnPooledThread {
                val moduleNodes = app.runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val controllers = detector.detectControllersForModule(module)
                        if (controllers.isEmpty()) return@mapNotNull null

                        DefaultMutableTreeNode(module.name).also { moduleNode ->
                            controllers.forEach { ctrl ->
                                val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                                ctrl.methods.forEach { ep ->
                                    // Lưu DisplayEndpoint (bên trong giữ EndpointInfo) để dễ lấy khi navigate
                                    val leaf = DefaultMutableTreeNode(DisplayEndpoint(ep))
                                    ctrlNode.add(leaf)
                                }
                                moduleNode.add(ctrlNode)
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
                val userObj = node.userObject

                // Hỗ trợ cả trường hợp ai đó set trực tiếp EndpointInfo
                val endpointInfo: EndpointInfo? = when (userObj) {
                    is DisplayEndpoint -> userObj.info
                    is EndpointInfo -> userObj
                    else -> null
                } ?: return@addTreeSelectionListener

                (endpointInfo?.targetElement as? Navigatable)
                    ?.takeIf { it.canNavigateToSource() }
                    ?.navigate(true)
            }
        }

        fun expandAll(tree: Tree) {
            var i = 0
            while (i < tree.rowCount) {
                tree.expandRow(i)
                i++
            }
        }
    }
}
