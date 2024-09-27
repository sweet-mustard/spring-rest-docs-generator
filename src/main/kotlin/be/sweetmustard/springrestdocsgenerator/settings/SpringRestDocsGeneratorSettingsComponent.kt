package be.sweetmustard.springrestdocsgenerator.settings

import be.sweetmustard.springrestdocsgenerator.SpringRestDocsGeneratorBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class SpringRestDocsGeneratorSettingsComponent(private val state: SpringRestDocsGeneratorState) {
    private val myMainPanel: DialogPanel = panel {
        group(SpringRestDocsGeneratorBundle.message("settings.annotations.header")) {
            twoColumnsRow(
                {
                    panel {
                        group(SpringRestDocsGeneratorBundle.message("settings.annotations.class.header")) {
                            buttonsGroup {
                                row {
                                    radioButton(
                                        SpringRestDocsGeneratorBundle.message("option.default"),
                                        true
                                    )
                                    text(SpringRestDocsGeneratorBundle.message("settings.annotations.class.default"))
                                }
                                row {
                                    radioButton(
                                        SpringRestDocsGeneratorBundle.message("option.custom"),
                                        false
                                    )
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
                        group(SpringRestDocsGeneratorBundle.message("settings.annotations.method.header")) {

                            row {
                                label(SpringRestDocsGeneratorBundle.message("option.default"))
                                text(SpringRestDocsGeneratorBundle.message("settings.annotations.method.default"))
                            }
                            additionalTestAnnotations()
                        }
                    }
                }
            )
        }
        group(SpringRestDocsGeneratorBundle.message("settings.mockmvc.header")) {
            row {
                textArea().bindText(state::mockMvcAdditions)
                    .align(Align.FILL)
                    .label(
                        SpringRestDocsGeneratorBundle.message("settings.mockmvc.label"),
                        LabelPosition.TOP
                    )
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
            ).label(SpringRestDocsGeneratorBundle.message("option.additional"), LabelPosition.TOP)
                .comment(SpringRestDocsGeneratorBundle.message("tooltip.enter-separated"))
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
            ).label(SpringRestDocsGeneratorBundle.message("option.additional"), LabelPosition.TOP)
                .comment(SpringRestDocsGeneratorBundle.message("tooltip.enter-separated"))
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