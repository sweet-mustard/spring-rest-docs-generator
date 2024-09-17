package be.sweetmustard.springrestdocsgenerator

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.getIfSingle
import com.intellij.util.containers.stream
import io.ktor.util.*


class GenerateRestDocsTestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val selectedMethod: PsiElement? = event.getData(CommonDataKeys.PSI_ELEMENT)

        if (selectedMethod is PsiMethod) {

            val methodBody = generateMethodBody(selectedMethod)

            generateRestDocsTest(selectedMethod, methodBody)

        }
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

    private fun generateRestDocsTest(selectedMethod: PsiMethod, methodBody: String) {
        val currentProject = selectedMethod.project
        val elementFactory = JavaPsiFacade.getInstance(currentProject).elementFactory

        val restController = selectedMethod.parentOfType<PsiClass>()!!

        val possibleTestSourceRoots = RestDocsHelper.getPossibleTestSourceRoots(restController)
        val testSourceRoot = possibleTestSourceRoots[0]
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
            println(documentationTestFile)

            val restDocumentationTestClass = elementFactory.createClass(documentationTestName.removeSuffix(".java"))
            PsiUtil.setModifierProperty(restDocumentationTestClass, PsiModifier.PACKAGE_LOCAL, true)
            restDocumentationTestClass.modifierList?.addAnnotation("WebMvcTest(${restController.name}.class)")
            restDocumentationTestClass.modifierList?.addAnnotation("AutoConfigureRestDocs")
            restDocumentationTestClass.modifierList?.addAnnotation("ExtendWith({RestDocumentationExtension.class})")

            documentationTestFile.add(restDocumentationTestClass)
            val codeStyleManager = CodeStyleManager.getInstance(currentProject)
            codeStyleManager.reformat(documentationTestFile)
            

            WriteCommandAction.runWriteCommandAction(currentProject) {
                directory.add(documentationTestFile)
            }
            
        }

        val documentationTestClass = documentationTestFile.childrenOfType<PsiClass>()[0]

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
                    methodBody,
                    documentationTestMethod
                )
            )
            WriteCommandAction.runWriteCommandAction(currentProject) {
                documentationTestClass.add(
                    documentationTestMethod
                )
            }
        }
        documentationTestMethod.navigate(true)

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
}