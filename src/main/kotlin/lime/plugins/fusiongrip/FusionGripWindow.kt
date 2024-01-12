package lime.plugins.fusiongrip;

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import lime.plugins.fusiongrip.config.GenerationConfig
import lime.plugins.fusiongrip.tasks.FuseSourcesTask
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import javax.swing.*

class FusionGripWindow : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        ToolWindowContent.init(toolWindow);
        val content = ContentFactory.getInstance().createContent(ToolWindowContent.getContentPanel(), "", false);
        toolWindow.contentManager.addContent(content);
    }

    object ToolWindowContent {

        private val contentPanel = JPanel();

        fun init(toolWindow: ToolWindow) {
            contentPanel.setLayout(BorderLayout(10, 20));
            contentPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 100, 0));
            contentPanel.add(createControlsPanel(toolWindow), BorderLayout.CENTER);
        }

        fun getContentPanel(): JPanel {
            return contentPanel;
        }

        @NotNull
        private fun createTooltipIcon(tooltip: String): JButton {
            val iconButton = JButton(AllIcons.General.Information)

            iconButton.toolTipText = tooltip
            iconButton.margin = Insets(0, 0, 0, 0)
            iconButton.isContentAreaFilled = false
            iconButton.border = BorderFactory.createEmptyBorder()
            iconButton.preferredSize = Dimension(20, 20)

            return iconButton
        }

        @NotNull
        private fun createControlsPanel(toolWindow: ToolWindow): JPanel {
            val frame = JFrame("Confirmation Example")
            val controlsPanel = JPanel()
            val activateButton = JButton("Run generation")

            activateButton.addActionListener { _ ->
                if (false) {
                    Messages.showErrorDialog(
                        "Validation errors occured",
                        "Validation error",
                    )

                    return@addActionListener
                }

                val response = Messages.showYesNoDialog(
                    frame,
                    "Вы точно хотите создать DataSource'ы?",
                    "Подтверждение",
                    Messages.getQuestionIcon()
                )

                if (response == Messages.YES) {
                    val config = GenerationConfig()

                    try {
                        val createdCount = FuseSourcesTask().action(toolWindow.project)

                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Lime.FusionGrip")
                            .createNotification(
                                "Generation completed",
                                "Generation completed",
                                NotificationType.INFORMATION
                            )
                            .notify(toolWindow.project)
                    } catch (e: Exception) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Lime.FusionGrip")
                            .createNotification(
                                "Failed to generate",
                                "Error occured",
                                NotificationType.ERROR
                            )
                            .notify(toolWindow.project);
                    }
                }
            }

            controlsPanel.add(activateButton)
            return controlsPanel
        }
    }

    data class ValidatableInput<T: JComponent>(
        val panel: JPanel,
        val element: T,
        val revalidate: () -> Unit,
        val validationErr: () -> ValidationInfo?,
    )
}
