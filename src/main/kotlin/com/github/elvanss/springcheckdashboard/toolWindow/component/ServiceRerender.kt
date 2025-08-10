package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.service.ServiceInfo
import com.github.elvanss.springcheckdashboard.services.Service.SpringServiceDetector

import com.intellij.execution.ui.RunContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.treeStructure.Tree
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ServiceRerender {
    companion object {

        data class DisplayService(val info: ServiceInfo, val running: Boolean) {
            override fun toString(): String = "${info.serviceName}::[${info.port ?: "-"}]"
            fun getServiceInfo(): ServiceInfo = info
        }

        private fun isRunning(project: Project, cfgName: String): Boolean {
            val mgr = RunContentManager.getInstance(project)
            val desc = mgr.allDescriptors.firstOrNull { it.displayName == cfgName }
            val ph = desc?.processHandler
            return ph != null && !ph.isProcessTerminated && !ph.isProcessTerminating
        }

        private class ServiceTreeRenderer : ColoredTreeCellRenderer() {
            private val iconRoot: Icon? by lazy { UIManager.getIcon("Tree.openIcon") }
            private val iconModule: Icon? by lazy { UIManager.getIcon("Tree.closedIcon") }
            private val iconRun = AllIcons.Actions.Execute
            private val iconStop = AllIcons.Actions.Suspend

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
                val uo = node?.userObject
                val depth = node?.path?.size ?: 0 // root=1, module=2, service=3

                when {
                    uo is DisplayService -> {
                        icon = if (uo.running) iconStop else iconRun
                        append(text)
                    }
                    depth == 1 -> { // Root
                        icon = iconRoot; append(text)
                    }
                    depth == 2 -> { // Module
                        icon = iconModule; append(text)
                    }
                    else -> append(text)
                }
            }
        }

        fun loadServices(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringServiceDetector()
            val rootNode = DefaultMutableTreeNode("Services")
            val app = ApplicationManager.getApplication()

            tree.cellRenderer = ServiceTreeRenderer()

            app.executeOnPooledThread {
                val moduleNodes = app.runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val services = detector.detectServicesForModule(module)
                        if (services.isEmpty()) return@mapNotNull null

                        DefaultMutableTreeNode(module.name).also { moduleNode ->
                            services.forEach { svc ->
                                val cfgName = "SpringCheck: ${svc.moduleName}"
                                val running = isRunning(project, cfgName)
                                moduleNode.add(DefaultMutableTreeNode(DisplayService(svc, running)))
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
