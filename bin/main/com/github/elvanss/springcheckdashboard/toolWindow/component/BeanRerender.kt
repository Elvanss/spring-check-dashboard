package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.github.elvanss.springcheckdashboard.model.bean.BeanInfo
import com.github.elvanss.springcheckdashboard.services.bean.SpringBeanDetector
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

class BeanRerender {

    companion object {

        private data class DisplayBean(val info: BeanInfo) {
            override fun toString(): String = "${info.beanName} [${info.beanType}]"
        }

        private class BeanTreeRenderer : ColoredTreeCellRenderer() {

            // Fallback helpers
            private fun load(path: String): Icon? = try {
                IconLoader.getIcon(path, javaClass)
            } catch (_: Throwable) {
                null
            }

            private val iconRoot: Icon? by lazy { UIManager.getIcon("Tree.openIcon") }
            private val iconModule: Icon? by lazy { UIManager.getIcon("Tree.closedIcon") }

            private val iconDefault: Icon by lazy {
                load("/icons/bean.svg") ?: UIManager.getIcon("Tree.leafIcon")
            }
            private val iconComponent: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }
            private val iconService: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }
            private val iconRepository: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }
            private val iconController: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }
            private val iconConfig: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }
            private val iconMethodBean: Icon by lazy { load("/icons/bean.svg") ?: iconDefault }

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
                val depth = node?.path?.size ?: 0 // root=1, module=2, leaf bean=3

                when {
                    uo is DisplayBean -> {
                        icon = when (uo.info.beanType.lowercase()) {
                            "springbootaplication" -> iconDefault
                            "component" -> iconComponent
                            "service" -> iconService
                            "repository" -> iconRepository
                            "restcontroller", "controller" -> iconController
                            "configuration" -> iconConfig
                            "bean" -> iconMethodBean
                            else -> iconDefault
                        }
                        append(text)
                    }
                    depth == 1 -> { // Root "Spring Beans"
                        icon = iconRoot
                        append(text)
                    }
                    depth == 2 -> { // Module name
                        icon = iconModule
                        append(text)
                    }
                    else -> append(text)
                }
            }
        }

        fun loadBeans(project: Project, model: DefaultTreeModel, tree: Tree) {
            val detector = SpringBeanDetector()
            val rootNode = DefaultMutableTreeNode("Spring Beans")
            val app = ApplicationManager.getApplication()

            tree.cellRenderer = BeanTreeRenderer()

            app.executeOnPooledThread {
                val moduleNodes = app.runReadAction<List<DefaultMutableTreeNode>> {
                    ModuleManager.getInstance(project).modules.mapNotNull { module ->
                        val beans = detector.detectBeansForModule(module)
                        if (beans.isEmpty()) return@mapNotNull null

                        DefaultMutableTreeNode(module.name).also { moduleNode ->
                            beans.forEach { bean ->
                                moduleNode.add(DefaultMutableTreeNode(DisplayBean(bean)))
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
                val bean = when (val uo = node.userObject) {
                    is DisplayBean -> uo.info
                    is BeanInfo -> uo
                    else -> null
                } ?: return@addTreeSelectionListener

                (bean.targetElement as? Navigatable)
                    ?.takeIf { it.canNavigateToSource() }
                    ?.navigate(true)
            }
        }

        private fun expandAll(tree: Tree) {
            var i = 0
            while (i < tree.rowCount) {
                tree.expandRow(i)
                i++
            }
        }
    }
}
