package be.sweetmustard.springrestdocsgenerator.settings

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.ItemEvent
import javax.swing.*


class SpringRestDocsGeneratorSettingsComponent() {
    private var myMainPanel: JPanel? = null
    private val additionalClassAnnotations: JTextArea
    private val additionalTestMethodAnnotations: JTextArea
    private val mockMvcAdditions: JTextArea
    private val useDefaultClassAnnotation: JBRadioButton
    private val useCustomClassAnnotation: JBRadioButton
    private val customClassAnnotationText: JBTextField


    init {

        val classAnnotations = JPanel(GridLayout(2, 1))

        val mainClassAnnotation = JPanel(GridLayout(2, 1))

        val defaultClassAnnotation = JPanel(FlowLayout(FlowLayout.LEADING))
        useDefaultClassAnnotation = JBRadioButton("Default:")
        val defaultClassAnnotationText = JBTextArea(
            listOf(
                "@ExtendWith({RestDocumentationExtension.class})",
                "@AutoConfigureRestDocs",
                "@WebMvcTest({{rest-controller-name}.class})"
            ).joinToString(System.lineSeparator())
        )
        defaultClassAnnotation.add(useDefaultClassAnnotation)
        defaultClassAnnotation.add(defaultClassAnnotationText)

        val customClassAnnotation = JPanel(FlowLayout(FlowLayout.LEADING))
        useCustomClassAnnotation = JBRadioButton("Custom:")
        customClassAnnotationText = JBTextField(40)
        customClassAnnotation.add(useCustomClassAnnotation)
        customClassAnnotation.add(customClassAnnotationText)

        mainClassAnnotation.add(defaultClassAnnotation)
        mainClassAnnotation.add(customClassAnnotation)

        useDefaultClassAnnotation.addItemListener {
            useCustomClassAnnotation.isSelected = it.stateChange != ItemEvent.SELECTED
            customClassAnnotationText.isEnabled = it.stateChange != ItemEvent.SELECTED
        }
        useCustomClassAnnotation.addItemListener {
            useDefaultClassAnnotation.isSelected = (it.stateChange != ItemEvent.SELECTED)
        }

        additionalClassAnnotations = createTextArea()
        val additionalClassAnnotationsScrollPane =
            createLabelledScrollPaneAroundTextArea(
                additionalClassAnnotations,
                "Additional",
                "Semicolon- or enter-separated list"
            )

        classAnnotations.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Class level"), mainClassAnnotation, true)
                .addComponent(additionalClassAnnotationsScrollPane, 5)
                .panel
        )

        additionalTestMethodAnnotations = createTextArea()
        val additionalTestMethodAnnotationsScrollPane =
            createLabelledScrollPaneAroundTextArea(
                additionalTestMethodAnnotations,
                "Test method level",
                "Semicolon- or enter-separated list"
            )

        val annotations = JPanel(GridLayout(1, 2, 20, 0))
        annotations.border = IdeBorderFactory.createTitledBorder("Annotations")
        annotations.add(classAnnotations)
        annotations.add(additionalTestMethodAnnotationsScrollPane)

        mockMvcAdditions = createTextArea()
        val mockMvcAdditionsPanel =
            createLabelledScrollPaneAroundTextArea(
                mockMvcAdditions,
                "Code to insert inside mockMvc.perform()",
                ""
            )

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
        jTextArea.lineWrap = true
        jTextArea.margin.left = 4
        jTextArea.margin.top = 2
        return jTextArea
    }

    private fun createLabelledScrollPaneAroundTextArea(
        jTextArea: JTextArea,
        label: String,
        toolTip: String
    ): JComponent {
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
        return additionalClassAnnotations
    }

    fun getRestControllerAnnotations(): List<String> {
        return additionalClassAnnotations.text.split("[;\n]+".toRegex()).stream()
            .filter { it.isNotBlank() }.toList()
    }

    fun setRestControllerAnnotations(newRestControllerAnnotations: List<String>) {
        additionalClassAnnotations.text =
            newRestControllerAnnotations.joinToString(System.lineSeparator()) { it }
    }

    fun getMethodAnnotations(): List<String> {
        return additionalTestMethodAnnotations.text.split("[;\n]+".toRegex()).stream()
            .filter { it.isNotBlank() }.toList()
    }

    fun setMethodAnnotations(newMethodAnnotations: List<String>) {
        additionalTestMethodAnnotations.text =
            newMethodAnnotations.joinToString(System.lineSeparator()) { it }
    }

    fun getMockMvcAdditions(): String {
        return mockMvcAdditions.text
    }

    fun setMockMvcAdditions(newMockMvcAdditions: String) {
        mockMvcAdditions.text = newMockMvcAdditions
    }

    fun setDefaultAnnotationUsage(usage: Boolean) {
        useDefaultClassAnnotation.isSelected = usage
        useCustomClassAnnotation.isSelected = !usage
    }

    fun getDefaultAnnotationUsage(): Boolean {
        return useDefaultClassAnnotation.isSelected
    }

    fun getCustomClassAnnotation(): String {
        return customClassAnnotationText.text
    }

    fun setCustomClassAnnotation(newAnnotation: String) {
        customClassAnnotationText.text = newAnnotation
    }


}