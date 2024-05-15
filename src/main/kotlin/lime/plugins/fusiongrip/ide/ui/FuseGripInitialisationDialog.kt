package lime.plugins.fusiongrip.ide.ui

import com.intellij.database.model.RawDataSource
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Icons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel
import com.jetbrains.rd.util.addUnique
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode

class FuseGripInitialisationDialog(project: Project?, val dataSources: List<RawDataSource>) : DialogWrapper(project, true) {
    private val checkBoxMergeBases = JBCheckBox("Merge shards").apply {
        alignmentX = Component.LEFT_ALIGNMENT
        toolTipText = "If enabled, merges DataSources with same schema by creating '_combined' schema"
    }
    private val checkBoxMergeSchemas = JBCheckBox("Merge schemas").apply {
        alignmentX = Component.LEFT_ALIGNMENT
        toolTipText = "If enabled, merges multiple user defined schemas into one"
    }
    private val appendOnlyMode = JBCheckBox("Append only mode").apply {
        alignmentX = Component.LEFT_ALIGNMENT
        toolTipText = "If enabled, won't override and delete other created schemas"
    }
    private val databaseName = JBTextField("db").apply {
        columns = 20
        maximumSize = Dimension(Integer.MAX_VALUE, preferredSize.height)
    }

    init {
        init()
        title = "Create FuseGrip DataSource"
    }

    override fun createCenterPanel(): JComponent {
        val treeTable = createTreeTable()

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JScrollPane(treeTable))
            add(Box.createVerticalStrut(10))
            add(checkBoxMergeBases)
            add(checkBoxMergeSchemas)
            add(appendOnlyMode)
            add(Box.createVerticalStrut(10))
            add(databaseName)
        }
        return panel
    }

    private fun createTreeTable(): JTree {
        val root = CustomTreeNode("Root Node") // Use meaningful names
        populateTree(root)

        val model = ListTreeTableModel(root, arrayOf())
        val tree = JTree(model)
        tree.cellRenderer = CustomTreeCellRenderer()  // Using Default for simplicity
        tree.isRootVisible = false  // Set to true for visibility of root node
        tree.isVisible = true

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = tree.getRowForLocation(e.x, e.y)
                if (row != -1) {
                    val treePath = tree.getPathForRow(row)
                    val lastPathComponent = treePath.lastPathComponent
                    if (lastPathComponent is CustomTreeNode) {
                        val nodeChanged = { component:TreeNode -> (tree.model as DefaultTreeModel).nodeChanged(component) }
                        lastPathComponent.toggle(nodeChanged)
                        nodeChanged(lastPathComponent)
                        val parentPath = treePath.parentPath
                        if (parentPath != null) {
                            nodeChanged(parentPath.lastPathComponent as CustomTreeNode)
                        }
                    }
                }
            }
        })

        return tree
    }

    private fun populateTree(root: DefaultMutableTreeNode) {
        var gn = dataSources.groupBy { it.groupName }
        var groupToNode = HashMap<String, CustomTreeNode>()

        for (gsource in gn) {
            var groupParts = gsource.key?.split('/')
            if (groupParts != null) {
                var curr = ""
                var curNode = root
                for (part in groupParts) {
                    curr += part
                    if (!groupToNode.containsKey(curr)) {
                        var node = CustomTreeNode(part, true, AllIcons.Toolbar.Pin)
                        groupToNode[curr] = node
                        curNode.add(node)
                        curNode = node
                    } else {
                        curNode = groupToNode[curr] as DefaultMutableTreeNode
                    }
                    curr += "/"
                }
            }

            var parentNode = groupToNode[gsource.key] as? DefaultMutableTreeNode
            if (!groupToNode.containsKey(gsource.key)) {
                parentNode = root
            }

            for (source in gsource.value) {
                parentNode?.add(CustomTreeNode(source.name, true, source.getIcon(0)))
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        return null
    }
}

class CustomTreeNode(
    val text: String,
    var isSelected: Boolean = false,
    var icon: Icon? = null
) : DefaultMutableTreeNode(text) {

    fun toggle(callback: (TreeNode) -> Unit) {
        changeSelected(!isSelected, callback)
    }

    private fun changeSelected(v: Boolean, callback: (TreeNode) -> Unit) {
        var a = this;
        isSelected = v
        callback(this)
        children().asSequence().filterIsInstance<CustomTreeNode>().forEach {
            it.changeSelected(isSelected, callback)
        }
    }
}

class CustomTreeCellRenderer : TreeCellRenderer {
    private val label = JLabel()
    private val checkBox = JCheckBox()

    override fun getTreeCellRendererComponent(
        tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)

        if (value is CustomTreeNode) {
            checkBox.isSelected = value.isSelected
            label.text = value.text
            label.icon = value.icon // Apply the icon to the label
        }

        checkBox.revalidate()  // Ensure the checkbox updates its layout
        checkBox.repaint()

        panel.add(checkBox)
        panel.add(label)
        return panel
    }
}