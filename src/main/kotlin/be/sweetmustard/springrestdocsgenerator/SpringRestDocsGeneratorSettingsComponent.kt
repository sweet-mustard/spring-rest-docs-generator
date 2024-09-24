package be.sweetmustard.springrestdocsgenerator

import com.intellij.util.ui.FormBuilder
import javax.swing.*


class SpringRestDocsGeneratorSettingsComponent() {
    private var myMainPanel: JPanel? = null
    private val additionalRestControllerDocumentationTestAnnotations = JTextArea(5, 30)
    private val additionalTestMethodAnnotations = JTextArea(5, 30)
    
    
    init {
        additionalRestControllerDocumentationTestAnnotations.isEditable = true
        additionalRestControllerDocumentationTestAnnotations.wrapStyleWord = true
        additionalRestControllerDocumentationTestAnnotations.lineWrap =true
        val additionalRestControllerDocumentationTestAnnotationsScrollPane = JScrollPane(additionalRestControllerDocumentationTestAnnotations, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        val additionalRestControllerDocumentationTestAnnotationsLabel = JLabel("Annotations for RestControllerDocumentationTest classes")

        additionalTestMethodAnnotations.isEditable = true
        additionalTestMethodAnnotations.wrapStyleWord = true
        additionalTestMethodAnnotations.lineWrap =true
        val additionalTestMethodAnnotationsScrollPane = JScrollPane(additionalTestMethodAnnotations, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        val additionalTestMethodAnnotationsLabel = JLabel("Annotations for RestControllerDocumentationTest methods")
        
        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(additionalRestControllerDocumentationTestAnnotationsLabel, additionalRestControllerDocumentationTestAnnotationsScrollPane, 10, true)
            .addTooltip("Semicolon- or enter-separated list")
            .addLabeledComponent(additionalTestMethodAnnotationsLabel, additionalTestMethodAnnotationsScrollPane, 10, true)
            .addTooltip("Semicolon- or enter-separated list")
            .panel
        
        additionalRestControllerDocumentationTestAnnotations.font = myMainPanel!!.font
        additionalTestMethodAnnotations.font = myMainPanel!!.font
    }

    fun getPanel(): JPanel? {
        return myMainPanel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return additionalRestControllerDocumentationTestAnnotations
    }
    
    fun getRestControllerAnnotations(): List<String> {
        return additionalRestControllerDocumentationTestAnnotations.text.split("[;\n]+".toRegex()).stream().filter {it.isNotBlank()}.toList()
    }
    
    fun setRestControllerAnnotations(newRestControllerAnnotations : List<String>) {
        additionalRestControllerDocumentationTestAnnotations.text = newRestControllerAnnotations.joinToString(";") { it }
    }
    
    fun getMethodAnnotations(): List<String> {
        return additionalTestMethodAnnotations.text.split("[;\n]+".toRegex()).stream().filter {it.isNotBlank()}.toList()
    }

    fun setMethodAnnotations(newMethodAnnotations : List<String>) {
        additionalTestMethodAnnotations.text = newMethodAnnotations.joinToString(";") { it }
    }


}