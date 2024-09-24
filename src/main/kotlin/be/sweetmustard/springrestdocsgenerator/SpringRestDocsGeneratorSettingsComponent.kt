package be.sweetmustard.springrestdocsgenerator

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel


class SpringRestDocsGeneratorSettingsComponent() {
    private var myMainPanel: JPanel? = null
    private val testAnnotations = JBTextArea()
    
    
    init {
        testAnnotations.isEditable = true
        testAnnotations.wrapStyleWord = true
        testAnnotations.lineWrap =true
        val testAnnotationsScrollPane = JBScrollPane(testAnnotations)
        val testAnnotationsLabel = JBLabel("Annotations for RestControllerDocumentationTest classes")
        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(testAnnotationsLabel, testAnnotationsScrollPane, 0, true)
            .addTooltip("Semicolon- or enter-separated list")
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        
        testAnnotations.font = myMainPanel!!.font
    }

    fun getPanel(): JPanel? {
        return myMainPanel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return testAnnotations
    }
    
    fun getTestAnnotationLabels(): List<String> {
        if (testAnnotations.text.isEmpty()) {
            return emptyList()
        }
        return testAnnotations.text.split("[;\n]+".toRegex()).stream().filter {it.isNotBlank()}.toList()
    }
    
    fun setTestAnnotationLabels(newTestAnnotations : List<String>) {
        testAnnotations.text = newTestAnnotations.joinToString(";") { it }
    }
    

}