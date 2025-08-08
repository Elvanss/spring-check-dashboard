package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.services.SpringEndpointDetector
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
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
        val scrollPane: JComponent = JScrollPane(tree)

        val content = contentFactory.createContent(scrollPane, "", false)
        toolWindow.contentManager.addContent(content)

        DumbService.getInstance(project).runWhenSmart {
            val detector = SpringEndpointDetector()
            val controllers = detector.detectControllers(project)

            // Xóa data cũ
            rootNode.removeAllChildren()

            // Group theo module -> controller -> endpoints
            val moduleMap = controllers.groupBy { it.moduleName }
            for ((moduleName, ctrls) in moduleMap) {
                val moduleNode = DefaultMutableTreeNode(moduleName)
                for (ctrl in ctrls) {
                    val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                    for (method in ctrl.methods) {
                        val epText = "${method.path} [${method.httpMethod}]: ${method.methodName}"
                        ctrlNode.add(DefaultMutableTreeNode(epText))
                    }
                    moduleNode.add(ctrlNode)
                }
                rootNode.add(moduleNode)
            }

            treeModel.reload()
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }
    }
}
