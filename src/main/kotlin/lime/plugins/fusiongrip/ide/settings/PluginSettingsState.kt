//package lime.plugins.fusiongrip.ide.settings
//
//import com.intellij.openapi.application.ApplicationManager
//import com.intellij.openapi.components.PersistentStateComponent
//import com.intellij.openapi.components.State
//import com.intellij.openapi.components.Storage
//import com.intellij.util.xmlb.XmlSerializerUtil
//import org.jetbrains.annotations.NotNull
//
///**
// * Supports storing the application settings in a persistent way.
// * The [State] and [Storage] annotations define the name of the data and the file name where
// * these persistent application settings are stored.
// */
//@State(name = "org.intellij.sdk.settings.AppSettingsState", storages = Storage("SdkSettingsPlugin.xml"))
//internal class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {
//    var userId: String = "John Q. Public"
//    var ideaStatus: Boolean = false
//
//    override fun getState(): PluginSettingsState {
//        return this
//    }
//
//    override fun loadState(@NotNull state: PluginSettingsState) {
//        XmlSerializerUtil.copyBean(state, this)
//    }
//
//    companion object {
//        val instance: PluginSettingsState
//            get() = ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
//    }
//}