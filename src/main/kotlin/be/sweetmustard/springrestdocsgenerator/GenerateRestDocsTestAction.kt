package be.sweetmustard.springrestdocsgenerator

import be.sweetmustard.springrestdocsgenerator.GenerateRestDocsTestAction.SelectionItemType.*
import be.sweetmustard.springrestdocsgenerator.generator.TestFileGenerator
import be.sweetmustard.springrestdocsgenerator.generator.TestMethodGenerator
import be.sweetmustard.springrestdocsgenerator.settings.SpringRestDocsGeneratorSettings
import be.sweetmustard.springrestdocsgenerator.settings.SpringRestDocsGeneratorState
import com.intellij.codeInsight.hint.HintManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.stream
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListSelectionModel


class GenerateRestDocsTestAction : AnAction() {
    private val typeChecker: TypeChecker = TypeChecker()
    private val testMethodGenerator = TestMethodGenerator(typeChecker)
    private val testFileGenerator = TestFileGenerator()

    override fun actionPerformed(event: AnActionEvent) {
        val selectedElement: PsiElement? = event.getData(CommonDataKeys.PSI_ELEMENT)
        val currentProject = event.project!!
        val projectState = SpringRestDocsGeneratorSettings.getInstance(currentProject).state
        val editor = FileEditorManager.getInstance(currentProject).selectedTextEditor!!


        if (selectedElement == null) {
            HintManager.getInstance()
                .showErrorHint(editor, "Cannot generate documentation test for selected element.")
            return
        }

        val selectedMethod = if (selectedElement is PsiMethod) {
            selectedElement
        } else {
            selectedElement.parentOfType<PsiMethod>()
        }

        if (!canGenerateDocumentationTestForMethod(selectedMethod)) {
            HintManager.getInstance()
                .showErrorHint(editor, "Cannot generate documentation test for selected element.")
            return
        }

        typeChecker.updateJsonConvertibleTypes(currentProject)
        showCreateOrJumpDialog(
            currentProject,
            selectedMethod!!,
            event,
            projectState
        )
    }

    private fun canGenerateDocumentationTestForMethod(selectedMethod: PsiMethod?) =
        selectedMethod != null &&
                (selectedMethod.annotations.stream().map { it.qualifiedName }
                    .anyMatch { it?.endsWith("Mapping") == true } ||
                        selectedMethod.returnTypeElement?.type.toString()
                            .contains("ResponseEntity"))

    private fun showCreateOrJumpDialog(
        currentProject: Project,
        selectedMethod: PsiMethod,
        event: AnActionEvent,
        projectState: SpringRestDocsGeneratorState
    ) {
        val (documentationTestFile: PsiFile?, documentationTest: PsiMethod?) =
            RestDocsHelper.getDocumentationTestForMethod(selectedMethod)
        val items = if (documentationTest != null) {
            listOf(
                SelectionItem(
                    JUMP,
                    "Jump to " + documentationTest.name,
                    documentationTestFile,
                    documentationTest
                ),
            )
        } else if (documentationTestFile != null) {
            listOf(
                SelectionItem(
                    CREATE_NEW_TEST,
                    "Create new Documentation Test ...",
                    documentationTestFile,
                    null
                )
            )    
        } else {
            listOf(SelectionItem(CREATE_NEW_FILE, "Create new Documentation Test ...", null, null))
        }

        JBPopupFactory.getInstance().createPopupChooserBuilder(items)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setTitle("Choose Documentation Test for " + selectedMethod.name)
            .setRenderer(object : ColoredListCellRenderer<SelectionItem>() {
                override fun customizeCellRenderer(
                    list: JList<out SelectionItem>,
                    value: SelectionItem,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    icon = value.getIcon()
                    append(value.title)
                }
            })
            .setItemChosenCallback {
                when (it.type) {
                    JUMP -> it.method!!.navigate(true)
                    CREATE_NEW_FILE -> getTestSourcesRoot(selectedMethod, event) { testSourceRoot ->
                        WriteCommandAction.runWriteCommandAction(
                            currentProject,
                            it.title,
                            "",
                            {
                                generateRestDocumentationTest(
                                    selectedMethod,
                                    currentProject,
                                    testSourceRoot,
                                    projectState
                                )
                            }
                        )
                    }

                    CREATE_NEW_TEST -> {
                        WriteCommandAction.runWriteCommandAction(
                            currentProject,
                            it.title,
                            "",
                            {
                                generateRestDocumentationTest(
                                    selectedMethod,
                                    currentProject,
                                    it.testFile!!,
                                    projectState
                                )
                            }
                        )
                    }
                }
            }
            .createPopup()
            .showInBestPositionFor(event.dataContext)
    }

    private fun getTestSourcesRoot(
        selectedMethod: PsiMethod,
        event: AnActionEvent,
        callback: Consumer<VirtualFile>
    ) {
        val testSourceRoots = RestDocsHelper.getPossibleTestSourceRoots(selectedMethod)
        if (testSourceRoots.size != 1) {
            allowUserToSelectTestSourceRoot(testSourceRoots, selectedMethod, callback, event)
        } else {
            callback.accept(testSourceRoots[0])
        }
    }

    private fun allowUserToSelectTestSourceRoot(
        testSourceRoots: List<VirtualFile>,
        selectedMethod: PsiMethod,
        callback: Consumer<VirtualFile>,
        event: AnActionEvent
    ) {
        JBPopupFactory.getInstance().createPopupChooserBuilder(testSourceRoots)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setTitle("Choose test sources root for " + selectedMethod.name + " Rest Documentation Test generation")
            .setRenderer(object : ColoredListCellRenderer<VirtualFile>() {
                override fun customizeCellRenderer(
                    list: JList<out VirtualFile>,
                    value: VirtualFile,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    icon = AllIcons.Modules.TestRoot
                    append(value.presentableName)
                }
            })
            .setItemChosenCallback {
                callback.accept(it)
            }
            .createPopup()
            .showInBestPositionFor(event.dataContext)
    }

    private fun generateRestDocumentationTest(
        selectedMethod: PsiMethod,
        currentProject: Project,
        testSourceRoot: VirtualFile,
        projectState: SpringRestDocsGeneratorState
    ) {
        val elementFactory = JavaPsiFacade.getInstance(currentProject).elementFactory

        val documentationTestFile = testFileGenerator.createOrGetDocumentationTestFile(
            selectedMethod.parentOfType<PsiClass>()!!,
            currentProject,
            testSourceRoot,
            elementFactory,
            projectState
        )

        generateRestDocumentationTest(
            selectedMethod,
            documentationTestFile,
            projectState,
            elementFactory
        )
    }

    private fun generateRestDocumentationTest(
        selectedMethod: PsiMethod,
        currentProject: Project,
        documentationTestFile: PsiFile,
        projectState: SpringRestDocsGeneratorState
    ) {
        generateRestDocumentationTest(
            selectedMethod,
            documentationTestFile,
            projectState,
            JavaPsiFacade.getInstance(currentProject).elementFactory
        )
    }

    private fun generateRestDocumentationTest(
        selectedMethod: PsiMethod,
        documentationTestFile: PsiFile,
        projectState: SpringRestDocsGeneratorState,
        elementFactory: PsiElementFactory
    ) {
        val documentationTestClass = documentationTestFile.childrenOfType<PsiClass>()[0]

        testMethodGenerator.addMockMvcFieldIfMissing(elementFactory, documentationTestClass)

        val documentationTestMethod =
            testMethodGenerator.getOrCreateDocumentationTestMethod(
                selectedMethod,
                documentationTestClass,
                elementFactory,
                projectState
            )

        documentationTestMethod.navigate(true)
    }

    data class SelectionItem(
        val type: SelectionItemType,
        val title: String,
        val testFile: PsiFile?,
        val method: PsiMethod?
    ) {
        fun getIcon(): Icon {
            return if (method == null) {
                AllIcons.Actions.IntentionBulb
            } else {
                AllIcons.Nodes.Test
            }
        }
    }

    enum class SelectionItemType {
        CREATE_NEW_FILE,
        CREATE_NEW_TEST,
        JUMP
    }
}