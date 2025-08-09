package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.model.Bean.BeanInfo
import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.services.Endpoint.SpringEndpointDetector
import com.github.elvanss.springcheckdashboard.services.Bean.SpringBeanDetector
import com.github.elvanss.springcheckdashboard.toolWindow.component.EndpointTreeCellRerender
import com.intellij.openapi.module.ModuleManager
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
        endpointTree.cellRenderer = EndpointTreeCellRerender("/icons/api-icon.svg")
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
        beanTree.cellRenderer = EndpointTreeCellRerender("/icons/bean-icon.svg")
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

        // Event khi đổi dropdown
        selector.addActionListener {
            val selected = selector.selectedItem as String
            cardLayout.show(cardPanel, selected)
        }

        // Load data khi IntelliJ đã index xong
        DumbService.getInstance(project).runWhenSmart {
            loadEndpoints(project, endpointTreeModel, endpointTree)
            loadBeans(project, beanTreeModel, beanTree)
        }
    }

    private fun loadEndpoints(project: Project, model: DefaultTreeModel, tree: Tree) {
        val detector = SpringEndpointDetector()
        val rootNode = DefaultMutableTreeNode("Spring Endpoints")
        ModuleManager.getInstance(project).modules.forEach { module ->
            val ctrls = detector.detectControllersForModule(module)
            if (ctrls.isNotEmpty()) {
                val moduleNode = DefaultMutableTreeNode(module.name)
                ctrls.forEach { ctrl ->
                    val ctrlNode = DefaultMutableTreeNode(ctrl.controllerName)
                    ctrl.methods.forEach { method ->
                        ctrlNode.add(DefaultMutableTreeNode(method))
                    }
                    moduleNode.add(ctrlNode)
                }
                rootNode.add(moduleNode)
            }
        }
        model.setRoot(rootNode)
        expandAll(tree)
    }
    private fun loadBeans(project: Project, model: DefaultTreeModel, tree: Tree) {
        val detector = SpringBeanDetector()
        val rootNode = DefaultMutableTreeNode("Spring Beans")

        tree.addTreeSelectionListener { e ->
            val node = e?.path?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val data = node.userObject
            if (data is BeanInfo) {
                (data.targetElement as? Navigatable)?.takeIf { it.canNavigateToSource() }?.navigate(true)
            }
        }

        ModuleManager.getInstance(project).modules.forEach { module ->
            val beans = detector.detectBeansForModule(module)
            if (beans.isNotEmpty()) {
                val moduleNode = DefaultMutableTreeNode(module.name)
                beans.forEach { bean ->
                    val displayNode = DefaultMutableTreeNode(bean).apply {
                        userObject = object {
                            override fun toString(): String {
                                return "${bean.beanName} [${bean.beanType}]"
                            }
                            fun getBeanInfo(): BeanInfo = bean
                        }
                    }
                    moduleNode.add(displayNode)
                }
                rootNode.add(moduleNode)
            }
        }
        model.setRoot(rootNode)
        expandAll(tree)
    }


    private fun expandAll(tree: Tree) {
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }
}
