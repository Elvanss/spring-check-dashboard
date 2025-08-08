package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.model.EndpointInfo
import com.github.elvanss.springcheckdashboard.services.SpringEndpointDetector
import com.github.elvanss.springcheckdashboard.toolWindow.component.EndpointTreeCellRerender
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.pom.Navigatable
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpringEndpointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Root node của tree
        val rootNode = DefaultMutableTreeNode("Spring Endpoints")
        val treeModel = DefaultTreeModel(rootNode)
        val tree = Tree(treeModel)
        tree.cellRenderer = EndpointTreeCellRerender("/icons/api-icon.svg")
        val scrollPane: JComponent = JScrollPane(tree)

        val content = contentFactory.createContent(scrollPane, "", false)
        toolWindow.contentManager.addContent(content)

        tree.addTreeSelectionListener { e ->
            val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val data = node.userObject
            if (data is EndpointInfo) {
                data.targetElement.let { element ->
                    if (element is Navigatable && element.canNavigateToSource()) {
                        element.navigate(true)
                    } else {
                        println("Cannot navigate to source for: ${data.methodName}")
                    }
                }
            }
        }

        DumbService.getInstance(project).runWhenSmart {
            val detector = SpringEndpointDetector()
            val rootNode = DefaultMutableTreeNode("Spring Endpoints")
            rootNode.removeAllChildren()

            val modules = ModuleManager.getInstance(project).modules
            for (module in modules) {
                val ctrls = detector.detectControllersForModule(module)
                if (ctrls.isNotEmpty()) {
                    val moduleNode = DefaultMutableTreeNode(module.name)
                    for (ctrl in ctrls) {
                        val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                        for (method in ctrl.methods) {
                            ctrlNode.add(DefaultMutableTreeNode(method))
                        }
                        moduleNode.add(ctrlNode)
                    }
                    rootNode.add(moduleNode)
                }
            }

            treeModel.setRoot(rootNode)
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }

    }
}
