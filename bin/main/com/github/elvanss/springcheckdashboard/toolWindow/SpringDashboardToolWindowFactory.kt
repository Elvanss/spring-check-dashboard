package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.toolWindow.component.EndpointRerender
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.pom.Navigatable
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.*

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpringDashboardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Dropdown selector
        val selector = JComboBox(arrayOf("Endpoints", "Beans"))

        // Card layout chứa 2 view
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)

        // ====== Endpoints Panel ======
        val endpointRootNode = DefaultMutableTreeNode("Spring Endpoints")
        val endpointTreeModel = DefaultTreeModel(endpointRootNode)
        val endpointTree = Tree(endpointTreeModel)
        endpointTree.addTreeSelectionListener { e ->
            val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val data = node.userObject
            if (data is EndpointInfo) {
                (data.targetElement as? Navigatable)?.takeIf { it.canNavigateToSource() }?.navigate(true)
            }
        }
        val endpointScroll = JScrollPane(endpointTree)

        // ====== Beans Panel ======
        val beanRootNode = DefaultMutableTreeNode("Spring Beans")
        val beanTreeModel = DefaultTreeModel(beanRootNode)
        val beanTree = Tree(beanTreeModel)
        val beanScroll = JScrollPane(beanTree)

        // Thêm 2 card vào panel
        cardPanel.add(endpointScroll, "Endpoints")
        cardPanel.add(beanScroll, "Beans")

        // Layout tổng
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(selector, BorderLayout.NORTH)
        mainPanel.add(cardPanel, BorderLayout.CENTER)

        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        selector.addActionListener {
            val selected = selector.selectedItem as String
            cardLayout.show(cardPanel, selected)
        }

        DumbService.getInstance(project).runWhenSmart {
            EndpointRerender.loadEndpoints(project, endpointTreeModel, endpointTree)
            EndpointRerender.loadBeans(project, beanTreeModel, beanTree)
        }
    }
}
