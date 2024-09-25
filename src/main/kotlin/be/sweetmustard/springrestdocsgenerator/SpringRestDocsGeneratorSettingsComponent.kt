package be.sweetmustard.springrestdocsgenerator

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.awt.GridLayout
import javax.swing.*


class SpringRestDocsGeneratorSettingsComponent() {
    private var myMainPanel: JPanel? = null
    private val additionalRestControllerDocumentationTestAnnotations : JTextArea
    private val additionalTestMethodAnnotations : JTextArea
    private val mockMvcAdditions : JTextArea
    
    
    init {
        additionalRestControllerDocumentationTestAnnotations = createTextArea()
        val additionalRestControllerDocumentationTestAnnotationsScrollPane = 
            createLabelledScrollPaneAroundTextArea(additionalRestControllerDocumentationTestAnnotations, "Documentation classes", "Semicolon- or enter-separated list")
        
        additionalTestMethodAnnotations = createTextArea()
        val additionalTestMethodAnnotationsScrollPane = 
            createLabelledScrollPaneAroundTextArea(additionalTestMethodAnnotations, "Test methods", "Semicolon- or enter-separated list")

        val annotations = JPanel(GridLayout(1, 2, 20, 0))
        annotations.border = IdeBorderFactory.createTitledBorder("Annotations")
        annotations.add(additionalRestControllerDocumentationTestAnnotationsScrollPane)
        annotations.add(additionalTestMethodAnnotationsScrollPane)
        
        mockMvcAdditions = createTextArea()
        val mockMvcAdditionsPanel =
            createLabelledScrollPaneAroundTextArea(mockMvcAdditions, "Code to insert inside mockMvc.perform()", "")

        myMainPanel = FormBuilder.createFormBuilder()
            .addComponent(annotations)
            .addComponent(mockMvcAdditionsPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createTextArea(): JTextArea {
        val jTextArea = JTextArea(5, 10)
        jTextArea.isEditable = true
        jTextArea.wrapStyleWord = true
        jTextArea.lineWrap =true
        jTextArea.margin.left = 4
        jTextArea.margin.top = 2
        return jTextArea
    }

    private fun createLabelledScrollPaneAroundTextArea(jTextArea: JTextArea, label : String, toolTip : String) : JComponent {
        val jScrollPane = JScrollPane(
            jTextArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        jScrollPane.border = BorderFactory.createLineBorder(JBColor.border(), 1)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel(label),
                jScrollPane,
                10,
                true
            )
            .addTooltip(toolTip)
            .panel
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
        additionalRestControllerDocumentationTestAnnotations.text = newRestControllerAnnotations.joinToString(System.lineSeparator()) { it }
    }
    
    fun getMethodAnnotations(): List<String> {
        return additionalTestMethodAnnotations.text.split("[;\n]+".toRegex()).stream().filter {it.isNotBlank()}.toList()
    }

    fun setMethodAnnotations(newMethodAnnotations : List<String>) {
        additionalTestMethodAnnotations.text = newMethodAnnotations.joinToString(System.lineSeparator()) { it }
    }
    
    fun getMockMvcAdditions() : List<String> {
        return mockMvcAdditions.text.split("[;\n]+".toRegex()).stream().filter {it.isNotBlank()}.toList()
    }
    
    fun setMockMvcAdditions(newMockMvcAdditions : List<String>) {
        mockMvcAdditions.text = newMockMvcAdditions.joinToString(System.lineSeparator()) { it }
    }


}