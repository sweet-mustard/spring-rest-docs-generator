package be.sweetmustard.springrestdocsgenerator.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel


class SpringRestDocsGeneratorSettingsConfigurable(private val project: Project) : Configurable {

    private var mySettingsComponent: SpringRestDocsGeneratorSettingsComponent? = null


    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return "Spring REST Docs Generator"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent?.getPreferredFocusedComponent()
    }

    override fun createComponent(): JPanel? {
        mySettingsComponent = SpringRestDocsGeneratorSettingsComponent(
            Objects.requireNonNull(SpringRestDocsGeneratorSettings.getInstance(project).state)
        )
        return mySettingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        return mySettingsComponent?.getPanel()?.isModified() == true
    }

    override fun apply() {
        mySettingsComponent?.getPanel()?.apply()
    }

    override fun reset() {
        mySettingsComponent?.getPanel()?.reset()
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}