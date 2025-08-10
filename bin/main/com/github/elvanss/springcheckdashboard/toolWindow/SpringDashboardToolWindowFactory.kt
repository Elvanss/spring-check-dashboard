package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
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
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.jvm.java

class SpringDashboardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // ======== Top controls ========
        val selector = JComboBox(arrayOf("Endpoints", "Beans"))

        // Refresh (idle label + spinner inside CardLayout)
        val refreshIdle = JLabel("R").apply {
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 14f)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }
        val refreshSpinner = AsyncProcessIcon("springcheck-refresh")
        val refreshCardLayout = CardLayout()
        val refreshCard = JPanel(refreshCardLayout).apply {
            isOpaque = false
            add(refreshIdle, "idle")
            add(refreshSpinner, "loading")
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = java.awt.Dimension(24, 24)
            maximumSize = java.awt.Dimension(24, 24)
            minimumSize = java.awt.Dimension(24, 24)
        }

        // Generate (smart + dropdown caret)
        val generateSmart = JLabel("G").apply {
            toolTipText = "Generate OpenAPI (smart)"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 14f)
        }
        val generateCaret = JLabel("▾").apply {
            toolTipText = "More generate options"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 14f)
        }

        val toolbar = JToolBar().apply {
            isFloatable = false
            add(selector)
            addSeparator()
            add(generateSmart)
            add(generateCaret)
            addSeparator()
            add(refreshCard)
        }

        // ======== Cards (Endpoints / Beans) ========
        val cardLayout = CardLayout()
        val cardPanel = JPanel(cardLayout)

        // Endpoints tree
        val endpointRootNode = DefaultMutableTreeNode("Spring Endpoints")
        val endpointTreeModel = DefaultTreeModel(endpointRootNode)
        val endpointTree = Tree(endpointTreeModel)
        val endpointScroll = JScrollPane(endpointTree)

        // Beans tree
        val beanRootNode = DefaultMutableTreeNode("Spring Beans")
        val beanTreeModel = DefaultTreeModel(beanRootNode)
        val beanTree = Tree(beanTreeModel)
        val beanScroll = JScrollPane(beanTree)

        cardPanel.add(endpointScroll, "Endpoints")
        cardPanel.add(beanScroll, "Beans")

        val mainPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(cardPanel, BorderLayout.CENTER)
        }
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        selector.addActionListener {
            val selected = selector.selectedItem as String
            cardLayout.show(cardPanel, selected)
        }

        // ======== Loading control for Refresh ========
        var stopGuardTimer: Timer? = null
        var minShowTimer: Timer? = null
        var startedAt = 0L
        val MIN_VISIBLE_MS = 400
        var isLoading = false

        fun hideLoadingNow() {
            stopGuardTimer?.stop(); stopGuardTimer = null
            minShowTimer?.stop();   minShowTimer = null
            refreshSpinner.suspend()
            refreshSpinner.isVisible = false
            refreshCardLayout.show(refreshCard, "idle")
            refreshCard.toolTipText = "Refresh"
            isLoading = false
            // force redraw
            refreshCard.revalidate(); refreshCard.repaint()
            (refreshCard.parent as? JComponent)?.revalidate()
            (refreshCard.parent as? JComponent)?.repaint()
        }

        fun stopLoading() {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_VISIBLE_MS) {
                minShowTimer?.stop()
                minShowTimer = Timer((MIN_VISIBLE_MS - elapsed).toInt()) {
                    hideLoadingNow()
                }.also { it.isRepeats = false; it.start() }
            } else {
                hideLoadingNow()
            }
        }

        fun startLoading() {
            startedAt = System.currentTimeMillis()
            refreshCardLayout.show(refreshCard, "loading")
            refreshSpinner.isVisible = true
            refreshSpinner.resume()
            refreshCard.toolTipText = "Refreshing…"
            refreshCard.revalidate(); refreshCard.repaint()
            (refreshCard.parent as? JComponent)?.revalidate()
            (refreshCard.parent as? JComponent)?.repaint()

            stopGuardTimer?.stop()
            // fallback auto-stop sau 10s nếu model không phát event
            stopGuardTimer = Timer(10_000) { hideLoadingNow() }
                .also { it.isRepeats = false; it.start() }
        }

        fun oneShotStopOnModelChange(model: DefaultTreeModel) {
            val listener = object : TreeModelListener {
                private fun done() {
                    model.removeTreeModelListener(this)
                    stopLoading()
                }
                override fun treeStructureChanged(e: TreeModelEvent?) = done()
                override fun treeNodesInserted(e: TreeModelEvent?) = done()
                override fun treeNodesRemoved(e: TreeModelEvent?) = done()
                override fun treeNodesChanged(e: TreeModelEvent?) { /* ignore */ }
            }
            model.addTreeModelListener(listener)
        }

        // ======== Refresh click (attach to parent + both children) ========
        fun triggerRefresh() {
            if (isLoading) return
            isLoading = true
            doRefresh(
                project,
                selector.selectedItem as String,
                endpointTreeModel, endpointTree,
                beanTreeModel, beanTree,
                startLoading = { startLoading() },
                onModelChanged = { model -> oneShotStopOnModelChange(model) }
            )
        }
        val refreshClickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) { triggerRefresh() }
        }
        refreshCard.addMouseListener(refreshClickListener)
        refreshIdle.addMouseListener(refreshClickListener)
        refreshSpinner.addMouseListener(refreshClickListener)

        // ======== Generate (smart + dropdown) ========
        val generator = OpenApiGenerator()

        val genMenu = JPopupMenu()
        val miGenSelected = JMenuItem("Generate for selected").apply {
            toolTipText = "Endpoint / Controller / Module tùy theo node đang chọn"
        }
        val miGenAll = JMenuItem("Generate ALL endpoints")
        genMenu.add(miGenSelected)
        genMenu.addSeparator()
        genMenu.add(miGenAll)

        miGenSelected.addActionListener {
            val tab = selector.selectedItem as String
            if (tab != "Endpoints") { generator.generate(project); return@addActionListener }
            val selection = endpointTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            if (selection == null) { generator.generate(project); return@addActionListener }
            val eps = collectEndpointsFromNode(selection)
            if (eps.isNotEmpty()) generator.generateForList(project, eps, labelForNode(selection))
            else generator.generate(project)
        }
        miGenAll.addActionListener { generator.generate(project) }

        generateSmart.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val tab = selector.selectedItem as String
                if (tab != "Endpoints") { generator.generate(project); return }
                val selection = endpointTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                if (selection == null) { generator.generate(project); return }
                val eps = collectEndpointsFromNode(selection)
                if (eps.isNotEmpty()) generator.generateForList(project, eps, labelForNode(selection))
                else generator.generate(project)
            }
        })
        val showMenu: (MouseEvent) -> Unit = {
            genMenu.show(generateCaret, 0, generateCaret.height)
        }
        generateCaret.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showMenu(e)
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
        })

        // ======== Initial load ========
        DumbService.getInstance(project).runWhenSmart {
            EndpointRerender.loadEndpoints(project, endpointTreeModel, endpointTree)
            BeanRerender.loadBeans(project, beanTreeModel, beanTree)
        }
    }

    // ======== Helpers ========
    private fun doRefresh(
        project: Project,
        tab: String,
        endpointTreeModel: DefaultTreeModel,
        endpointTree: Tree,
        beanTreeModel: DefaultTreeModel,
        beanTree: Tree,
        startLoading: () -> Unit,
        onModelChanged: (DefaultTreeModel) -> Unit
    ) {
        val app = ApplicationManager.getApplication()
        startLoading()
        if (tab == "Endpoints") onModelChanged(endpointTreeModel) else onModelChanged(beanTreeModel)

        DumbService.getInstance(project).runWhenSmart {
            if (tab == "Endpoints") {
                EndpointRerender.loadEndpoints(project, endpointTreeModel, endpointTree)
            } else {
                BeanRerender.loadBeans(project, beanTreeModel, beanTree)
            }
            app.invokeLater { /* spinner sẽ dừng khi model phát event */ }
        }
    }

    private fun collectEndpointsFromNode(node: DefaultMutableTreeNode): List<EndpointInfo> {
        extractEndpointInfo(node.userObject)?.let { return listOf(it) }
        if (!node.isLeaf) {
            val out = mutableListOf<EndpointInfo>()
            val children = node.children()
            while (children.hasMoreElements()) {
                val ch = children.nextElement() as DefaultMutableTreeNode
                val ep = extractEndpointInfo(ch.userObject)
                if (ep != null) out += ep
                else if (!ch.isLeaf) out += collectEndpointsFromNode(ch)
            }
            return out
        }
        return emptyList()
    }

    private fun extractEndpointInfo(userObj: Any?): EndpointInfo? {
        if (userObj is EndpointInfo) return userObj
        if (userObj == null) return null
        return runCatching {
            val m = userObj::class.members.firstOrNull {
                it.name in setOf("getInfo", "getEndpointInfo") && it.parameters.size == 1
            }
            m?.call(userObj) as? EndpointInfo
        }.getOrNull()
    }

    private fun labelForNode(node: DefaultMutableTreeNode): String {
        val uo = node.userObject
        extractEndpointInfo(uo)?.let {
            val p = it.path.replace("/", "_").replace("{", "").replace("}", "")
                .replace(Regex("[^A-Za-z0-9_\\-]"), "")
            return "${it.httpMethod.lowercase()}$p-${it.methodName}"
        }
        return (uo?.toString() ?: "selection").lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
    }
}
