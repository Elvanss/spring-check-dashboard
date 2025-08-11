package com.github.elvanss.springcheckdashboard.toolWindow

import com.github.elvanss.springcheckdashboard.model.Endpoint.EndpointInfo
import com.github.elvanss.springcheckdashboard.model.service.ServiceInfo
import com.github.elvanss.springcheckdashboard.openapi.OpenApiGenerator
import com.github.elvanss.springcheckdashboard.toolWindow.component.BeanRerender
import com.github.elvanss.springcheckdashboard.toolWindow.component.EndpointRerender
import com.github.elvanss.springcheckdashboard.toolWindow.component.ServiceRerender
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.RunContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
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
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import com.intellij.ui.components.JBScrollPane

import java.awt.event.ActionEvent
import com.intellij.openapi.module.Module


class SpringDashboardToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // ===================== SERVICES (Tree like Endpoints/Beans) =====================
        val svcRootNode = DefaultMutableTreeNode("Services")
        val svcTreeModel = DefaultTreeModel(svcRootNode)
        val svcTree = Tree(svcTreeModel)
        val svcScroll = JScrollPane(svcTree)

        val svcTitle = JLabel("Services").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }
        val svcReloadIdle = JLabel("Reload").apply {
            toolTipText = "Refresh Services"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 12f)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }
        val svcSpinner = AsyncProcessIcon("springcheck-services-refresh")
        val svcCardLayout = CardLayout()
        val svcCard = JPanel(svcCardLayout).apply {
            isOpaque = false
            add(svcReloadIdle, "idle")
            add(svcSpinner, "loading")
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }

        val svcToolbar = JToolBar().apply {
            isFloatable = false
            add(svcTitle)
            add(Box.createHorizontalGlue())
            add(svcCard)
        }

        val servicesPanel = JPanel(BorderLayout()).apply {
            add(svcToolbar, BorderLayout.NORTH)
            add(svcScroll, BorderLayout.CENTER)
        }

        fun refreshServices() {
            svcCardLayout.show(svcCard, "loading"); svcSpinner.isVisible = true; svcSpinner.resume()
            ApplicationManager.getApplication().executeOnPooledThread {
                ServiceRerender.loadServices(project, svcTreeModel, svcTree)
                SwingUtilities.invokeLater {
                    svcCardLayout.show(svcCard, "idle"); svcSpinner.suspend(); svcSpinner.isVisible = false
                }
            }
        }
        val svcReloadClick = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) { refreshServices() }
        }
        svcCard.addMouseListener(svcReloadClick)
        svcReloadIdle.addMouseListener(svcReloadClick)
        svcSpinner.addMouseListener(svcReloadClick)

        // Context menu: Run / Debug / Stop
        val svcMenu = JPopupMenu().apply {
            add(JMenuItem("Run", AllIcons.Actions.Execute).apply {
                addActionListener {
                    extractServiceInfo(svcTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.let { info ->
                        startService(project, info, "SpringCheck: ${info.moduleName}")
                        refreshServices()
                    }
                }
            })
            add(JMenuItem("Debug", AllIcons.Actions.StartDebugger).apply {
                addActionListener {
                    extractServiceInfo(svcTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.let { info ->
                        startServiceDebug(project, info, "SpringCheck: ${info.moduleName}")
                        refreshServices()
                    }
                }
            })
            addSeparator()
            add(JMenuItem("Stop", AllIcons.Actions.Suspend).apply {
                addActionListener {
                    extractServiceInfo(svcTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.let { info ->
                        stopService(project, "SpringCheck: ${info.moduleName}")
                        refreshServices()
                    }
                }
            })
        }

        svcTree.addMouseListener(object : MouseAdapter() {
            private fun showMenu(e: MouseEvent) {
                svcMenu.show(svcTree, e.x, e.y)
            }
            override fun mouseClicked(e: MouseEvent) {
                val node = svcTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    val info = extractServiceInfo(node) ?: return
                    val cfg = "SpringCheck: ${info.moduleName}"
                    if (isRunning(project, cfg)) stopService(project, cfg) else startService(project, info, cfg)
                    refreshServices()
                } else if (e.isPopupTrigger || SwingUtilities.isRightMouseButton(e)) {
                    showMenu(e)
                }
            }
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) mouseClicked(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) mouseClicked(e) }
        })

        // ===================== ENDPOINTS =====================
        val epRootNode = DefaultMutableTreeNode("Spring Endpoints")
        val epTreeModel = DefaultTreeModel(epRootNode)
        val epTree = Tree(epTreeModel)
        val epScroll = JScrollPane(epTree)

        val epTitle = JLabel("Endpoints").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }

        val epRefreshIdle = JLabel("Reload").apply {
            toolTipText = "Refresh State"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 12f)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }
        val epRefreshSpinner = AsyncProcessIcon("springcheck-endpoints-refresh")
        val epRefreshCardLayout = CardLayout()
        val epRefreshCard = JPanel(epRefreshCardLayout).apply {
            isOpaque = false
            add(epRefreshIdle, "idle")
            add(epRefreshSpinner, "loading")
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }

        val genSmart = JLabel("Generate").apply {
            toolTipText = "Generate OpenAPI (smart)"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 12f)
            preferredSize = Dimension(64, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }
        val genCaret = JLabel("▾").apply {
            toolTipText = "More generate options"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 12f)
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }

        val epToolbar = JToolBar().apply {
            isFloatable = false
            add(epTitle)
            add(Box.createHorizontalGlue())
            add(genSmart)
            add(genCaret)
            addSeparator(Dimension(8, 0))
            add(epRefreshCard)
        }

        val epPanel = JPanel(BorderLayout()).apply {
            add(epToolbar, BorderLayout.NORTH)
            add(epScroll, BorderLayout.CENTER)
        }

        // ===================== BEANS =====================
        val beanRootNode = DefaultMutableTreeNode("Spring Beans")
        val beanTreeModel = DefaultTreeModel(beanRootNode)
        val beanTree = Tree(beanTreeModel)
        val beanScroll = JScrollPane(beanTree)

        val beanTitle = JLabel("Beans").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }

        val beanRefreshIdle = JLabel("Reload").apply {
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(Font.BOLD, 12f)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(48, 24)
            minimumSize = Dimension(48, 24)
            maximumSize = Dimension(48, 24)
        }
        val beanRefreshSpinner = AsyncProcessIcon("springcheck-beans-refresh")
        val beanRefreshCardLayout = CardLayout()
        val beanRefreshCard = JPanel(beanRefreshCardLayout).apply {
            isOpaque = false
            add(beanRefreshIdle, "idle")
            add(beanRefreshSpinner, "loading")
            toolTipText = "Refresh"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(48, 24)
            maximumSize = Dimension(24, 24)
            minimumSize = Dimension(24, 24)
        }

        val beanToolbar = JToolBar().apply {
            isFloatable = false
            add(beanTitle)
            add(Box.createHorizontalGlue())
            add(beanRefreshCard)
        }

        val beanPanel = JPanel(BorderLayout()).apply {
            add(beanToolbar, BorderLayout.NORTH)
            add(beanScroll, BorderLayout.CENTER)
        }

        // ===================== Split 3 sections: Services | Endpoints | Beans =====================
        val midSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, epPanel, beanPanel).apply {
            resizeWeight = 0.6
            isContinuousLayout = true
            border = null
        }
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, servicesPanel, midSplit).apply {
            resizeWeight = 0.25
            isContinuousLayout = true
            border = null
        }

        val mainPanel = JPanel(BorderLayout()).apply { add(mainSplit, BorderLayout.CENTER) }
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        // ===================== Endpoints loading controls =====================
        var epStopGuardTimer: Timer? = null
        var epMinShowTimer: Timer? = null
        var epStartedAt = 0L
        val EP_MIN_VISIBLE_MS = 400
        var epIsLoading = false

        fun epHideLoadingNow() {
            epStopGuardTimer?.stop(); epStopGuardTimer = null
            epMinShowTimer?.stop();   epMinShowTimer = null
            epRefreshSpinner.suspend()
            epRefreshSpinner.isVisible = false
            epRefreshCardLayout.show(epRefreshCard, "idle")
            epRefreshCard.toolTipText = "Refresh"
            epIsLoading = false
            epRefreshCard.revalidate(); epRefreshCard.repaint()
            (epRefreshCard.parent as? JComponent)?.revalidate()
            (epRefreshCard.parent as? JComponent)?.repaint()
        }

        fun epStopLoading() {
            val elapsed = System.currentTimeMillis() - epStartedAt
            if (elapsed < EP_MIN_VISIBLE_MS) {
                epMinShowTimer?.stop()
                epMinShowTimer = Timer((EP_MIN_VISIBLE_MS - elapsed).toInt()) { epHideLoadingNow() }
                    .also { it.isRepeats = false; it.start() }
            } else epHideLoadingNow()
        }

        fun epStartLoading() {
            epStartedAt = System.currentTimeMillis()
            epRefreshCardLayout.show(epRefreshCard, "loading")
            epRefreshSpinner.isVisible = true
            epRefreshSpinner.resume()
            epRefreshCard.toolTipText = "Refreshing…"
            epRefreshCard.revalidate(); epRefreshCard.repaint()
            (epRefreshCard.parent as? JComponent)?.revalidate()
            (epRefreshCard.parent as? JComponent)?.repaint()
            epStopGuardTimer?.stop()
            epStopGuardTimer = Timer(10_000) { epHideLoadingNow() }.also { it.isRepeats = false; it.start() }
        }

        fun epStopOnModelChange(model: DefaultTreeModel) {
            val listener = object : TreeModelListener {
                private fun done() { model.removeTreeModelListener(this); epStopLoading() }
                override fun treeStructureChanged(e: TreeModelEvent?) = done()
                override fun treeNodesInserted(e: TreeModelEvent?) = done()
                override fun treeNodesRemoved(e: TreeModelEvent?) = done()
                override fun treeNodesChanged(e: TreeModelEvent?) { /* ignore */ }
            }
            model.addTreeModelListener(listener)
        }

        fun triggerEpRefresh() {
            if (epIsLoading) return
            epIsLoading = true
            doRefreshEndpoints(project, epTreeModel, epTree, startLoading = ::epStartLoading, onModelChanged = ::epStopOnModelChange)
        }

        val epRefreshClick = object : MouseAdapter() { override fun mouseClicked(e: MouseEvent?) { triggerEpRefresh() } }
        epRefreshCard.addMouseListener(epRefreshClick)
        epRefreshIdle.addMouseListener(epRefreshClick)
        epRefreshSpinner.addMouseListener(epRefreshClick)

        // ===================== Beans loading controls =====================
        var beanStopGuardTimer: Timer? = null
        var beanMinShowTimer: Timer? = null
        var beanStartedAt = 0L
        val BEAN_MIN_VISIBLE_MS = 400
        var beanIsLoading = false

        fun beanHideLoadingNow() {
            beanStopGuardTimer?.stop(); beanStopGuardTimer = null
            beanMinShowTimer?.stop();   beanMinShowTimer = null
            beanRefreshSpinner.suspend()
            beanRefreshSpinner.isVisible = false
            beanRefreshCardLayout.show(beanRefreshCard, "idle")
            beanRefreshCard.toolTipText = "Refresh"
            beanIsLoading = false
            beanRefreshCard.revalidate(); beanRefreshCard.repaint()
            (beanRefreshCard.parent as? JComponent)?.revalidate()
            (beanRefreshCard.parent as? JComponent)?.repaint()
        }

        fun beanStopLoading() {
            val elapsed = System.currentTimeMillis() - beanStartedAt
            if (elapsed < BEAN_MIN_VISIBLE_MS) {
                beanMinShowTimer?.stop()
                beanMinShowTimer = Timer((BEAN_MIN_VISIBLE_MS - elapsed).toInt()) { beanHideLoadingNow() }
                    .also { it.isRepeats = false; it.start() }
            } else beanHideLoadingNow()
        }

        fun beanStartLoading() {
            beanStartedAt = System.currentTimeMillis()
            beanRefreshCardLayout.show(beanRefreshCard, "loading")
            beanRefreshSpinner.isVisible = true
            beanRefreshSpinner.resume()
            beanRefreshCard.toolTipText = "Refreshing…"
            beanRefreshCard.revalidate(); beanRefreshCard.repaint()
            (beanRefreshCard.parent as? JComponent)?.revalidate()
            (beanRefreshCard.parent as? JComponent)?.repaint()
            beanStopGuardTimer?.stop()
            beanStopGuardTimer = Timer(10_000) { beanHideLoadingNow() }.also { it.isRepeats = false; it.start() }
        }

        fun beanStopOnModelChange(model: DefaultTreeModel) {
            val listener = object : TreeModelListener {
                private fun done() { model.removeTreeModelListener(this); beanStopLoading() }
                override fun treeStructureChanged(e: TreeModelEvent?) = done()
                override fun treeNodesInserted(e: TreeModelEvent?) = done()
                override fun treeNodesRemoved(e: TreeModelEvent?) = done()
                override fun treeNodesChanged(e: TreeModelEvent?) { /* ignore */ }
            }
            model.addTreeModelListener(listener)
        }

        fun triggerBeanRefresh() {
            if (beanIsLoading) return
            beanIsLoading = true
            doRefreshBeans(project, beanTreeModel, beanTree, startLoading = ::beanStartLoading, onModelChanged = ::beanStopOnModelChange)
        }

        val beanRefreshClick = object : MouseAdapter() { override fun mouseClicked(e: MouseEvent?) { triggerBeanRefresh() } }
        beanRefreshCard.addMouseListener(beanRefreshClick)
        beanRefreshIdle.addMouseListener(beanRefreshClick)
        beanRefreshSpinner.addMouseListener(beanRefreshClick)

        // ===================== OpenAPI menu (Endpoints only) =====================
        val generator = OpenApiGenerator()
        val genMenu = JPopupMenu()
        val miGenSelected = JMenuItem("Generate for selected").apply {
            toolTipText = "Endpoint / Controller / Module tùy theo node đang chọn"
        }
        val miGenAll = JMenuItem("Generate ALL endpoints")
        genMenu.add(miGenSelected)
        genMenu.addSeparator()
        genMenu.add(miGenAll)

        miGenSelected.addActionListener { _: ActionEvent ->
            val selection = epTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val app = ApplicationManager.getApplication()

            if (selection == null) {
                app.executeOnPooledThread { generator.generate(project) }
                return@addActionListener
            }

            val endpoint: EndpointInfo? = extractEndpointInfo(selection.userObject)
            val module: Module? = selectedModuleFromNode(project, selection)

            when {
                endpoint != null -> {
                    app.executeOnPooledThread { generator.generateSingle(project, endpoint) }
                }
                module != null -> {
                    app.executeOnPooledThread { generator.generateForModule(project, module) }
                }
                else -> {
                    val eps = collectEndpointsFromNode(selection)
                    if (eps.isNotEmpty()) {
                        app.executeOnPooledThread {
                            generator.generateForList(project, eps, labelForNode(selection))
                        }
                    } else {
                        app.executeOnPooledThread { generator.generate(project) }
                    }
                }
            }
        }


        miGenAll.addActionListener { ApplicationManager.getApplication().executeOnPooledThread { generator.generate(project) } }

        genSmart.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val selection = epTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                val app = ApplicationManager.getApplication()

                if (selection == null) {
                    app.executeOnPooledThread { generator.generate(project) }
                    return
                }

                val endpoint: EndpointInfo? = extractEndpointInfo(selection.userObject)
                val module: Module? = selectedModuleFromNode(project, selection)

                when {
                    endpoint != null -> app.executeOnPooledThread { generator.generateSingle(project, endpoint) }
                    module != null   -> app.executeOnPooledThread { generator.generateForModule(project, module) }
                    else -> {
                        val eps = collectEndpointsFromNode(selection)
                        if (eps.isNotEmpty()) {
                            app.executeOnPooledThread {
                                generator.generateForList(project, eps, labelForNode(selection))
                            }
                        } else {
                            app.executeOnPooledThread { generator.generate(project) }
                        }
                    }
                }
            }
        })

        val showMenu: (MouseEvent) -> Unit = { genMenu.show(genCaret, 0, genCaret.height) }
        genCaret.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showMenu(e)
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
        })

        // ===================== Initial load =====================
        DumbService.getInstance(project).runWhenSmart {
            refreshServices()
            EndpointRerender.loadEndpoints(project, epTreeModel, epTree)
            BeanRerender.loadBeans(project, beanTreeModel, beanTree)
        }
    }

    // ---------------- Helpers: refresh jobs ----------------
    private fun doRefreshEndpoints(
        project: Project,
        model: DefaultTreeModel,
        tree: Tree,
        startLoading: () -> Unit,
        onModelChanged: (DefaultTreeModel) -> Unit
    ) {
        val app = ApplicationManager.getApplication()
        startLoading()
        onModelChanged(model)
        DumbService.getInstance(project).runWhenSmart {
            EndpointRerender.loadEndpoints(project, model, tree)
            app.invokeLater { /* spinner stop via model event */ }
        }
    }

    private fun doRefreshBeans(
        project: Project,
        model: DefaultTreeModel,
        tree: Tree,
        startLoading: () -> Unit,
        onModelChanged: (DefaultTreeModel) -> Unit
    ) {
        val app = ApplicationManager.getApplication()
        startLoading()
        onModelChanged(model)
        DumbService.getInstance(project).runWhenSmart {
            BeanRerender.loadBeans(project, model, tree)
            app.invokeLater { /* spinner stop via model event */ }
        }
    }

    // ---------------- Helpers: OpenAPI ----------------
    private fun collectEndpointsFromNode(node: DefaultMutableTreeNode): List<EndpointInfo> {
        extractEndpointInfo(node.userObject)?.let { return listOf(it) }
        if (!node.isLeaf) {
            val out = mutableListOf<EndpointInfo>()
            val children = node.children()
            while (children.hasMoreElements()) {
                val ch = children.nextElement() as DefaultMutableTreeNode
                val ep = extractEndpointInfo(ch.userObject)
                if (ep != null) out += ep else if (!ch.isLeaf) out += collectEndpointsFromNode(ch)
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

    // ---------------- Helpers: Service actions & state ----------------
    private fun startService(project: Project, info: ServiceInfo, cfgName: String) {
        val runManager = RunManager.getInstance(project)
        val existing = runManager.allSettings.firstOrNull { it.name == cfgName }
        val settings = existing ?: runManager.createConfiguration(
            cfgName, ApplicationConfigurationType.getInstance().configurationFactories.first()
        ).also { runManager.addConfiguration(it) }

        val cfg = settings.configuration as ApplicationConfiguration
        cfg.mainClassName = info.mainClassFqn
        cfg.setModule(info.module)
        if (cfg.workingDirectory.isNullOrBlank()) cfg.workingDirectory = project.basePath

        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    private fun startServiceDebug(project: Project, info: ServiceInfo, cfgName: String) {
        val runManager = RunManager.getInstance(project)
        val existing = runManager.allSettings.firstOrNull { it.name == cfgName }
        val settings = existing ?: runManager.createConfiguration(
            cfgName, ApplicationConfigurationType.getInstance().configurationFactories.first()
        ).also { runManager.addConfiguration(it) }

        val cfg = settings.configuration as ApplicationConfiguration
        cfg.mainClassName = info.mainClassFqn
        cfg.setModule(info.module)
        if (cfg.workingDirectory.isNullOrBlank()) cfg.workingDirectory = project.basePath

        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
    }

    private fun stopService(project: Project, cfgName: String) {
        val mgr = RunContentManager.getInstance(project)
        val desc = mgr.allDescriptors.firstOrNull { it.displayName == cfgName }
        desc?.processHandler?.let { ph ->
            if (!ph.isProcessTerminated && !ph.isProcessTerminating) ph.destroyProcess()
        }
    }

    private fun isRunning(project: Project, cfgName: String): Boolean {
        val mgr = RunContentManager.getInstance(project)
        val desc = mgr.allDescriptors.firstOrNull { it.displayName == cfgName }
        val ph = desc?.processHandler
        return ph != null && !ph.isProcessTerminated && !ph.isProcessTerminating
    }

    private fun extractServiceInfo(node: DefaultMutableTreeNode?): ServiceInfo? {
        val uo = node?.userObject ?: return null
        return when (uo) {
            is ServiceRerender.Companion.DisplayService -> uo.info
            is ServiceInfo -> uo
            else -> runCatching {
                val m = uo::class.members.firstOrNull { it.name == "getServiceInfo" && it.parameters.size == 1 }
                m?.call(uo) as? ServiceInfo
            }.getOrNull()
        }
    }

    private fun selectedModuleFromNode(project: Project, node: DefaultMutableTreeNode): Module? {
        // depth: root=1, module=2, controller=3, endpoint=4
        val depth = node.path.size
        if (depth == 2) {
            val name = node.userObject?.toString() ?: return null
            return ModuleManager.getInstance(project).findModuleByName(name)
        }
        return null
    }


}
