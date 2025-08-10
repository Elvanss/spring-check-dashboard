package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.openapi.OpenApiGenerator
import com.github.elvanss.springcheckdashboard.toolWindow.component.BeanRerender
import com.github.elvanss.springcheckdashboard.toolWindow.component.EndpointRerender
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SpringDashboardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // ===== Top controls =====
        val selector = JComboBox(arrayOf("Endpoints", "Beans"))

        val refreshLabel = JLabel("R").apply {
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 14f)
        }
        val generateLabel = JLabel("G").apply {
            toolTipText = "Generate OpenAPI"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 14f)
        }

        val toolbar = JToolBar().apply {
            isFloatable = false
            add(selector)
            addSeparator()
            add(refreshLabel)
            addSeparator()
            add(generateLabel)
        }

        // ===== Card layout with 2 views =====
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)

        // Endpoints
        val endpointRootNode = DefaultMutableTreeNode("Spring Endpoints")
        val endpointTreeModel = DefaultTreeModel(endpointRootNode)
        val endpointTree = Tree(endpointTreeModel)
        val endpointScroll = JScrollPane(endpointTree)

        // Beans
        val beanRootNode = DefaultMutableTreeNode("Spring Beans")
        val beanTreeModel = DefaultTreeModel(beanRootNode)
        val beanTree = Tree(beanTreeModel)
        val beanScroll = JScrollPane(beanTree)

        // Add cards
        cardPanel.add(endpointScroll, "Endpoints")
        cardPanel.add(beanScroll, "Beans")

        // Main layout
        val mainPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(cardPanel, BorderLayout.CENTER)
        }

        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // Switch cards
        selector.addActionListener {
            val selected = selector.selectedItem as String
            cardLayout.show(cardPanel, selected)
        }

        // Refresh logic
        fun doRefresh(selectedTab: String) {
            refreshLabel.isEnabled = false
            val app = ApplicationManager.getApplication()
            DumbService.getInstance(project).runWhenSmart {
                when (selectedTab) {
                    "Endpoints" -> EndpointRerender.loadEndpoints(project, endpointTreeModel, endpointTree)
                    "Beans" -> BeanRerender.loadBeans(project, beanTreeModel, beanTree)
                }
                app.invokeLater { refreshLabel.isEnabled = true }
            }
        }

        // Clicks
        refreshLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (refreshLabel.isEnabled) {
                    doRefresh(selector.selectedItem as String)
                }
            }
        })
        generateLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                OpenApiGenerator().generate(project)
            }
        })

        // Initial load
        DumbService.getInstance(project).runWhenSmart {
            EndpointRerender.loadEndpoints(project, endpointTreeModel, endpointTree)
            BeanRerender.loadBeans(project, beanTreeModel, beanTree)
        }
    }
}
