package be.sweetmustard.springrestdocsgenerator

import be.sweetmustard.springrestdocsgenerator.GenerateRestDocsTestAction.SelectionItemType.CREATE
import be.sweetmustard.springrestdocsgenerator.GenerateRestDocsTestAction.SelectionItemType.JUMP
import com.intellij.codeInsight.hint.HintManager
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.getIfSingle
import com.intellij.util.containers.stream
import io.ktor.util.*
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListSelectionModel


class GenerateRestDocsTestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val selectedElement: PsiElement? = event.getData(CommonDataKeys.PSI_ELEMENT)
        val currentProject = event.project!!
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

        if (selectedMethod == null || selectedMethod.annotations.stream().map { it.qualifiedName }
                .noneMatch { it?.endsWith("Mapping") == true }) {
            HintManager.getInstance()
                .showErrorHint(editor, "Cannot generate documentation test for selected element.")
            return
        }
        
        showCreateOrJumpDialog(currentProject, selectedMethod, event)
    }

    private fun showCreateOrJumpDialog(
        currentProject: Project,
        selectedMethod: PsiMethod,
        event: AnActionEvent
    ) {
        val documentationTest =
            RestDocsHelper.getDocumentationTestForMethod(selectedMethod)
        val items = if (documentationTest != null) {
            listOf(
                SelectionItem(JUMP, "Jump to " + documentationTest.name, documentationTest),
            )
        } else {
            listOf(SelectionItem(CREATE, "Create new Documentation Test ...", null))
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(items)
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setTitle("Create Documentation Test for " + selectedMethod.name)
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
                if (it.type == JUMP) {
                    it.method!!.navigate(true)
                } else {
                    getTestSourcesRoot(selectedMethod, event) { testSourceRoot ->
                        WriteCommandAction.runWriteCommandAction(
                            currentProject,
                            it.title,
                            "",
                            { generateRestDocsTest(selectedMethod, currentProject, testSourceRoot) }
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


    private fun generateMethodBody(selectedMethod: PsiMethod): String {
        val requestMappingOfMethod = getRequestMappingOfMethod(selectedMethod)

        val requestMappingOfClass =
            getRequestMappingOfParentalRestController(selectedMethod)

        val requestUri =
            getUriFromAnnotation(requestMappingOfClass) + getUriFromAnnotation(
                requestMappingOfMethod
            )

        val requestMappingType =
            requestMappingOfMethod.resolveAnnotationType()?.name?.removeSuffix("Mapping")

        val pathParameters = selectedMethod.parameterList.parameters.filter {
            it.annotations.stream()
                .anyMatch { psiAnn ->
                    psiAnn.qualifiedName?.contains("org.springframework.web.bind.annotation.PathVariable")
                        ?: false
                }
        }

        val queryParameters = selectedMethod.parameterList.parameters.filter {
            it.annotations.stream()
                .anyMatch { psiAnn ->
                    psiAnn.qualifiedName?.contains("org.springframework.web.bind.annotation.RequestParam")
                        ?: false
                }
        }

        val requestObject = selectedMethod.parameterList.parameters.filter {
            it.annotations.stream()
                .anyMatch { psiAnn ->
                    psiAnn.qualifiedName?.contains("org.springframework.web.bind.annotation.RequestBody")
                        ?: false
                }
        }.firstOrNull()

        val requestObjectClass = PsiTypesUtil.getPsiClass(requestObject?.type)

        val responseStatus = selectedMethod.modifierList.annotations.filter {
            it.qualifiedName?.equals("org.springframework.web.bind.annotation.ResponseStatus")
                ?: false
        }.toList()

        val httpStatus: String? = if (responseStatus.isEmpty()) {
            "OK"
        } else {
            responseStatus.first().parameterList.attributes[0].value?.text
        }

        val methodBodyBuilder = StringBuilder()

        val responseObject = PsiTypesUtil.getPsiClass(selectedMethod.returnType)

        methodBodyBuilder.append("mockMvc.perform(${requestMappingType?.toLowerCasePreservingASCIIRules()}(\"$requestUri\"")
        if (pathParameters.isNotEmpty()) {
            methodBodyBuilder.append(", ".repeat(pathParameters.size))
        }
        methodBodyBuilder.appendLine(")")
        if (queryParameters.isNotEmpty()) {
            methodBodyBuilder.appendLine(queryParameters.stream()
                .map { param -> ".param(\"${param.name}\", )" }
                .reduce { a, b -> "$a\n$b" }
                .orElse(""))
        }
        if (requestObjectClass != null) {
            val requestObjectFields = requestObjectClass.fields
            methodBodyBuilder.appendLine(".contentType(MediaType.APPLICATION_JSON)")
            methodBodyBuilder.appendLine(".content(\"\"\"\n{")
            methodBodyBuilder.appendLine(requestObjectFields.stream()
                .map { field -> "\"${field.name}\":" }
                .reduce { a: String, b: String -> "$a,\n$b" }
                .orElse(""))
            methodBodyBuilder.appendLine("}\n\"\"\")")
        }
        methodBodyBuilder.appendLine(")")
        methodBodyBuilder.appendLine(".andExpect(status().is" + httpStatus?.removePrefix("HttpStatus.")
            ?.toLowerCasePreservingASCIIRules()
            ?.replaceFirstChar { c -> c.uppercaseChar() } + "())")
        methodBodyBuilder.appendLine(".andDo(document(\"${selectedMethod.name.camelToKebabCase()}\",")
        val documentationFields = listOf(
            generatePathParametersDocumentation(pathParameters),
            generateQueryParametersDocumentation(queryParameters),
            generateRequestObjectDocumentation(requestObjectClass),
            generateResponseObjectDocumentation(responseObject)
        ).stream()
            .filter(String::isNotEmpty)
            .reduce { a, b -> "$a,\n$b" }
            .orElse("")

        methodBodyBuilder.append(documentationFields)
        methodBodyBuilder.append(")\n);")
        val methodBody = methodBodyBuilder.toString()
        return methodBody
    }

    private fun generateRestDocsTest(
        selectedMethod: PsiMethod,
        currentProject: Project,
        testSourceRoot: VirtualFile
    ) {
        val elementFactory = JavaPsiFacade.getInstance(currentProject).elementFactory

        val restController = selectedMethod.parentOfType<PsiClass>()!!

        RestDocsHelper.getPossibleTestSourceRoots(restController)
        var documentationTestFile = RestDocsHelper.getCorrespondingDocumentationTestFile(
            testSourceRoot,
            restController
        )

        if (documentationTestFile == null) {
            val packageName = RestDocsHelper.getPackageName(restController)
            val testSourceRootDirectory =
                PsiManager.getInstance(currentProject).findDirectory(testSourceRoot)!!

            val directory =
                createPackageDirectoriesIfNeeded(testSourceRootDirectory, restController)

            val builder = StringBuilder()
            if (packageName.isNotEmpty()) {
                builder.appendLine("package $packageName;")
                builder.appendLine()
            }
            addImportsToDocumentationTest(builder)

            val documentationTestName =
                RestDocsHelper.getDocumentationTestFileName(restController)

            documentationTestFile = PsiFileFactory.getInstance(currentProject)
                .createFileFromText(
                    documentationTestName,
                    JavaFileType.INSTANCE,
                    builder.toString()
                )

            val restDocumentationTestClass =
                elementFactory.createClass(documentationTestName.removeSuffix(".java"))
            PsiUtil.setModifierProperty(restDocumentationTestClass, PsiModifier.PACKAGE_LOCAL, true)
            restDocumentationTestClass.modifierList?.addAnnotation("WebMvcTest(${restController.name}.class)")
            restDocumentationTestClass.modifierList?.addAnnotation("AutoConfigureRestDocs")
            restDocumentationTestClass.modifierList?.addAnnotation("ExtendWith({RestDocumentationExtension.class})")

            documentationTestFile.add(restDocumentationTestClass)
            val codeStyleManager = CodeStyleManager.getInstance(currentProject)
            codeStyleManager.reformat(documentationTestFile)

            documentationTestFile = directory.add(documentationTestFile) as PsiFile
        }

        val documentationTestClass = documentationTestFile.childrenOfType<PsiClass>()[0]

        addMockMvcFieldIfMissing(elementFactory, documentationTestClass)

        val documentationTestName = selectedMethod.name + "Example"

        var documentationTestMethod = documentationTestClass.methods.stream()
            .filter { it.name == documentationTestName }
            .getIfSingle()

        if (documentationTestMethod == null) {

            documentationTestMethod =
                elementFactory.createMethod(documentationTestName, PsiTypes.voidType())
            PsiUtil.addException(documentationTestMethod, "Exception")
            documentationTestMethod.modifierList.addAnnotation("Test")
            PsiUtil.setModifierProperty(documentationTestMethod, PsiModifier.PACKAGE_LOCAL, true)
            documentationTestMethod.body!!.add(
                elementFactory.createStatementFromText(
                    generateMethodBody(selectedMethod),
                    documentationTestMethod
                )
            )
            val addedDocumentationTestMethod = documentationTestClass.add(
                documentationTestMethod
            ) as PsiMethod
            addedDocumentationTestMethod.navigate(true)
        } else {
            documentationTestMethod.navigate(true)
        }
    }


    private fun addMockMvcFieldIfMissing(
        elementFactory: PsiElementFactory,
        documentationTestClass: PsiClass
    ) {
        val mockMvcType =
            elementFactory.createTypeByFQClassName("org.springframework.test.web.servlet.MockMvc")
        if (documentationTestClass.fields.stream().noneMatch { it.type == mockMvcType }) {
            val mockMvcField = elementFactory.createField("mockMvc", mockMvcType)
            PsiUtil.setModifierProperty(mockMvcField, PsiModifier.PRIVATE, true)
            mockMvcField.modifierList?.addAnnotation("Autowired")
            documentationTestClass.add(mockMvcField)
        }
    }

    private fun addImportsToDocumentationTest(builder: StringBuilder) {
        builder.appendLine()
        builder.appendLine("import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;")
        builder.appendLine("import static org.springframework.restdocs.payload.PayloadDocumentation.*;")
        builder.appendLine("import static org.springframework.restdocs.request.RequestDocumentation.*;")
        builder.appendLine("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;")
        builder.appendLine("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;")
        builder.appendLine()
        builder.appendLine("import org.junit.jupiter.api.Test;")
        builder.appendLine()
        builder.appendLine("import org.junit.jupiter.api.extension.ExtendWith;")
        builder.appendLine("import org.springframework.beans.factory.annotation.Autowired;")
        builder.appendLine("import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;")
        builder.appendLine("import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;")
        builder.appendLine("import org.springframework.http.MediaType;")
        builder.appendLine("import org.springframework.restdocs.RestDocumentationExtension;")
        builder.appendLine()
    }

    private fun generateQueryParametersDocumentation(queryParameters: List<PsiParameter>): String {
        if (queryParameters.isNotEmpty()) {
            return "queryParameters(\n" + generateDocumentationForParameters(queryParameters) + "\n)"
        }
        return ""
    }

    private fun generatePathParametersDocumentation(pathParameters: List<PsiParameter>): String {
        if (pathParameters.isNotEmpty()) {
            return "pathParameters(\n" + generateDocumentationForParameters(pathParameters) + "\n)"
        }
        return ""
    }

    private fun generateDocumentationForParameters(parameters: List<PsiParameter>): String? =
        parameters.stream()
            .map { param -> "parameterWithName(\"${param.name}\").description(\"\")" }
            .reduce { a, b -> "$a,\n$b" }
            .orElse("")

    private fun generateRequestObjectDocumentation(requestObjectClass: PsiClass?): String {
        if (requestObjectClass != null) {
            return "requestFields(\n" + generateDocumentationForFields(requestObjectClass.fields) + "\n)"
        }
        return ""
    }

    private fun generateResponseObjectDocumentation(responseObjectClass: PsiClass?): String {
        if (responseObjectClass != null) {
            return "responseFields(\n" + generateDocumentationForFields(responseObjectClass.fields) + "\n)"
        }
        return ""
    }

    private fun generateDocumentationForFields(responseObjectFields: Array<PsiField>): String? =
        responseObjectFields.stream()
            .map { param -> "fieldWithPath(\"${param.name}\").description(\"\")" }
            .reduce { a, b -> "$a,\n$b" }
            .orElse("")

    private fun getRequestMappingOfParentalRestController(selectedMethod: PsiMethod): PsiAnnotation? {
        val requestMappingClassLevel =
            selectedMethod.parentOfType<PsiClass>()?.annotations
                ?.filter {
                    it.qualifiedName?.contains("org.springframework.web.bind.annotation")
                        ?: false
                }
                ?.filter { it.qualifiedName?.contains("Mapping") ?: false }
                ?.get(0)
        return requestMappingClassLevel
    }

    private fun getRequestMappingOfMethod(method: PsiMethod): PsiAnnotation {
        val requestMappingMethodLevel =
            method.modifierList.annotations
                .filter {
                    it.qualifiedName?.contains("org.springframework.web.bind.annotation") ?: false
                }
                .filter {
                    it.qualifiedName?.contains("Mapping") ?: false
                }[0]
        return requestMappingMethodLevel
    }

    private fun getUriFromAnnotation(requestMappingClassLevel: PsiAnnotation?): String? {
        val uri =
            requestMappingClassLevel?.parameterList?.attributes?.filter { it.name == null || it.name == "value" || it.name == "path" }
                ?.getOrNull(0)
        if (uri == null) {
            return ""
        }
        return uri.literalValue
    }

    private fun String.camelToKebabCase(): String {
        val pattern = "(?<=.)[A-Z]".toRegex()
        return this.replace(pattern, "-$0").lowercase()
    }

    private fun createPackageDirectoriesIfNeeded(
        testSourceRootDirectory: PsiDirectory,
        selectedClass: PsiClass
    ): PsiDirectory {
        val packageName = RestDocsHelper.getPackageName(selectedClass)
        var directory = testSourceRootDirectory
        val packageNameParts = packageName.split(".").toList()
        for (packageNamePart in packageNameParts) {
            var subdirectory = directory.findSubdirectory(packageNamePart)
            if (subdirectory == null) {
                subdirectory = directory.createSubdirectory(packageNamePart)
            }
            directory = subdirectory
        }
        return directory
    }

    data class SelectionItem(
        val type: SelectionItemType,
        val title: String,
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
        CREATE,
        JUMP
    }
}