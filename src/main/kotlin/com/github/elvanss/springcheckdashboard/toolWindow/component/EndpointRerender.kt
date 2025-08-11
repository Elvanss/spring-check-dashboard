package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.services.endpoint.SpringEndpointDetector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.treeStructure.Tree
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class EndpointRerender {
    companion object {
        data class DisplayEndpoint(val info: EndpointInfo) {
            override fun toString(): String = "[${info.httpMethod}] ${info.path}"
        }

        private class EndpointTreeRenderer : ColoredTreeCellRenderer() {
            private val endpointIcon: Icon by lazy {
                try {
                    IconLoader.getIcon("/icons/api-icon.svg", javaClass)
                } catch (_: Throwable) {
                    UIManager.getIcon("Tree.leafIcon")
                }
            }
            private val moduleIcon: Icon? by lazy {
                UIManager.getIcon("Tree.closedIcon")
            }
            private val controllerIcon: Icon? by lazy {
                UIManager.getIcon("Tree.closedIcon")
            }
            private val rootIcon: Icon? by lazy {
                UIManager.getIcon("Tree.openIcon")
            }

            override fun customizeCellRenderer(
                tree: javax.swing.JTree,
                value: Any,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode
                val text = node?.userObject?.toString().orEmpty()

                val depth = node?.path?.size ?: 0
                val uo = node?.userObject

                when {
                    uo is DisplayEndpoint -> {
                        icon = endpointIcon
                        append(text)
                    }
                    depth == 1 -> { // Root "Spring Endpoints"
                        icon = rootIcon
                        append(text)
                    }
                    depth == 2 -> { // Module
                        icon = moduleIcon
                        append(text)
                    }
                    depth == 3 -> { // Controller
                        icon = controllerIcon
                        append(text)
                    }
                    else -> {
                        append(text)
                    }
                }
            }
        }

        fun loadEndpoints(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringEndpointDetector()
            val rootNode = DefaultMutableTreeNode("Spring Endpoints")
            val app = ApplicationManager.getApplication()

            tree.cellRenderer = EndpointTreeRenderer()

            app.executeOnPooledThread {
                val moduleNodes = app.runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val controllers = detector.detectControllersForModule(module)
                        if (controllers.isEmpty()) return@mapNotNull null

                        DefaultMutableTreeNode(module.name).also { moduleNode ->
                            controllers.forEach { ctrl ->
                                val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                                ctrl.methods.forEach { ep ->
                                    ctrlNode.add(DefaultMutableTreeNode(DisplayEndpoint(ep)))
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
                    (tree as? JComponent)?.revalidate()
                    (tree as? JComponent)?.repaint()
                }
            }

            tree.addTreeSelectionListener { e ->
                val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
                val ep: EndpointInfo? = when (val uo = node.userObject) {
                    is DisplayEndpoint -> uo.info
                    is EndpointInfo -> uo
                    else -> null
                }
                ep?.let {
                    (it.targetElement as? Navigatable)?.takeIf { nav -> nav.canNavigateToSource() }?.navigate(true)
                }
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
