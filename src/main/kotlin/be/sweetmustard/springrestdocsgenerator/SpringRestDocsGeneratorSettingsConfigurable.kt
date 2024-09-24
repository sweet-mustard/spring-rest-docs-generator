package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel


class SpringRestDocsGeneratorSettingsConfigurable(private val project : Project) : Configurable {

    private var mySettingsComponent: SpringRestDocsGeneratorSettingsComponent? = null


    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return "Spring REST Docs Generator"
    }
    
    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent?.getPreferredFocusedComponent()
    }

    override fun createComponent(): JPanel? {
        mySettingsComponent = SpringRestDocsGeneratorSettingsComponent()
        return mySettingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        val state: SpringRestDocsGeneratorState =
            Objects.requireNonNull(SpringRestDocsGeneratorSettings.getInstance(project).state)
        return !(mySettingsComponent?.getTestAnnotationLabels()?.equals(state.testAnnotations)!!)
    }

    override fun apply() {
        val state: SpringRestDocsGeneratorState =
            Objects.requireNonNull(SpringRestDocsGeneratorSettings.getInstance(project).state)
        state.testAnnotations = mySettingsComponent?.getTestAnnotationLabels()!!
    }

    override fun reset() {
        val state: SpringRestDocsGeneratorState =
            Objects.requireNonNull(SpringRestDocsGeneratorSettings.getInstance(project).state)
        mySettingsComponent?.setTestAnnotationLabels(state.testAnnotations)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}