package com.github.elvanss.springcheckdashboard.toolWindow.component

import com.intellij.ui.IconManager
import java.awt.Component
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class EndpointTreeCellRerender : DefaultTreeCellRenderer() {
    private val apiIcon: Icon = IconManager.getInstance().getIcon("/icons/api-icon.svg", javaClass.classLoader)

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        if (value is DefaultMutableTreeNode) {
            val userObj = value.userObject
            if (userObj is String && userObj.contains("]: [")) {
                icon = apiIcon
            }
        }

        return comp
    }
}
