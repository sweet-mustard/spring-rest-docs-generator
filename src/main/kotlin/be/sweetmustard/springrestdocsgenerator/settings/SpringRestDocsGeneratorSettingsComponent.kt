package be.sweetmustard.springrestdocsgenerator.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class SpringRestDocsGeneratorSettingsComponent(private val state: SpringRestDocsGeneratorState) {
    private val myMainPanel: DialogPanel = panel {
        group("Annotations") {
            twoColumnsRow(
                {
                    panel {
                        group("Class Level") {
                            buttonsGroup {
                                row {
                                    radioButton("Default:", true)
                                    text(
                                        listOf(
                                            "@ExtendWith({RestDocumentationExtension.class})",
                                            "@AutoConfigureRestDocs",
                                            "@WebMvcTest({rest-controller-name}.class)"
                                        ).joinToString("<br>")
                                    )
                                }
                                row {
                                    radioButton("Custom:", false)
                                    textArea().bindText(state::customClassAnnotation)
                                        .align(Align.FILL)
                                }
                            }.bind<Boolean>(state::useDefaultClassAnnotation)
                            additionalClassAnnotations()
                        }
                    }
                },
                {
                    panel {
                        group("Test Method Level") {

                            row {
                                label("Default:")
                                text("@Test")
                            }
                            additionalTestAnnotations()
                        }
                    }
                }
            )
        }
        group("MockMvc") {
            row {
                textArea().bindText(state::mockMvcAdditions)
                    .align(Align.FILL)
                    .label("Code to add inside mockMvc.perform()", LabelPosition.TOP)
                    .component.rows = 5
            }
        }
    }

    private fun Panel.additionalTestAnnotations() {
        row {
            textArea().bindText(
                {
                    state.restControllerDocumentationTestMethodAnnotations.stream()
                        .reduce { a, b -> a + System.lineSeparator() + b }.orElse("")
                },
                {
                    state.restControllerDocumentationTestMethodAnnotations =
                        it.split("\n+".toRegex()).stream().toList()
                }
            ).label("Additional", LabelPosition.TOP)
                .comment("Put separate values on separate lines")
                .align(Align.FILL)
                .component.rows = 5
        }
    }

    private fun Panel.additionalClassAnnotations() {
        row {
            textArea().bindText(
                {
                    state.restControllerDocumentationTestClassAnnotations.stream()
                        .reduce { a, b -> a + System.lineSeparator() + b }.orElse("")
                },
                {
                    state.restControllerDocumentationTestClassAnnotations =
                        it.split("\n+".toRegex()).stream().toList()
                }
            ).label("Additional", LabelPosition.TOP)
                .comment("Put separate values on separate lines")
                .align(Align.FILL)
                .component.rows = 5
        }
    }

    fun getPanel(): DialogPanel {
        return myMainPanel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return myMainPanel
    }


}