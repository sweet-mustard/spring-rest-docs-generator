package be.sweetmustard.springrestdocsgenerator.listener


import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.containers.stream

class UninstallListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        super.beforePluginUnload(pluginDescriptor, isUpdate)

        ProjectManager.getInstance().openProjects.forEach { project ->
            val moduleForFile = ProjectRootManager.getInstance(project).contentRoots
            moduleForFile.forEach { println(it.path) }
            WriteCommandAction.runWriteCommandAction(project) {
                moduleForFile.stream()
                    .flatMap { it.children.stream() }
                    .map { it.findChild("spring-rest-docs-generator-settings.xml") }
                    .filter { it != null }
                    .forEach { it?.delete(null) }
            }
        }
    }
}
