package be.sweetmustard.springrestdocsgenerator.generator

import be.sweetmustard.springrestdocsgenerator.TypeChecker
import be.sweetmustard.springrestdocsgenerator.settings.SpringRestDocsGeneratorState
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.getIfSingle
import com.intellij.util.containers.stream
import io.ktor.util.*
import java.util.logging.Logger

class TestMethodGenerator(typeChecker: TypeChecker) {

    private val logger = Logger.getLogger(TestMethodGenerator::class.java.name)
    private val fieldDescriptionGenerator = FieldDescriptionGenerator(typeChecker)
    private val jsonGenerator = JsonGenerator(typeChecker)


    internal fun getOrCreateDocumentationTestMethod(
        selectedMethod: PsiMethod,
        documentationTestClass: PsiClass,
        elementFactory: PsiElementFactory,
        projectState: SpringRestDocsGeneratorState
    ): PsiMethod {
        val documentationTestName = selectedMethod.name + "Example"
        logger.info("Getting documentation test method for %s".format(documentationTestName))
        var documentationTestMethod = documentationTestClass.methods.stream()
            .filter { it.name == documentationTestName }
            .getIfSingle()


        if (documentationTestMethod == null) {
            logger.info("Creating documentation test method %s".format(documentationTestName))
            documentationTestMethod =
                elementFactory.createMethod(documentationTestName, PsiTypes.voidType())

            PsiUtil.addException(documentationTestMethod, "Exception")
            for (annotation in projectState.restControllerDocumentationTestMethodAnnotations.reversed()) {
                documentationTestMethod.modifierList.addAnnotation(
                    annotation.replace(
                        "^@+".toRegex(),
                        ""
                    ).trim()
                )
            }
            documentationTestMethod.modifierList.addAnnotation("Test")

            PsiUtil.setModifierProperty(documentationTestMethod, PsiModifier.PACKAGE_LOCAL, true)

            documentationTestMethod.body!!.add(
                elementFactory.createStatementFromText(
                    generateMethodBody(
                        selectedMethod,
                        projectState
                    ),
                    documentationTestMethod
                )
            )
            return documentationTestClass.add(documentationTestMethod) as PsiMethod
        } else {
            return documentationTestMethod
        }
    }

    internal fun addMockMvcFieldIfMissing(
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

    private fun generateMethodBody(
        selectedMethod: PsiMethod,
        projectState: SpringRestDocsGeneratorState
    ): String {

        logger.info("Generating method body for %s".format(selectedMethod.name))
        val requestMappingOfMethod = getRequestMappingOfMethod(selectedMethod)

        val requestMappingOfClass =
            getRequestMappingOfParentalRestController(selectedMethod)

        val requestUri =
            getUriFromAnnotation(requestMappingOfClass) + getUriFromAnnotation(
                requestMappingOfMethod
            )

        val requestMappingType =
            requestMappingOfMethod.resolveAnnotationType()?.name?.removeSuffix("Mapping")
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

        val requestObjectType = requestObject?.type
        val requestObjectClass = PsiTypesUtil.getPsiClass(requestObjectType)

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

        val responseObjectType = selectedMethod.returnTypeElement?.type


        methodBodyBuilder.append("mockMvc.perform")
        methodBodyBuilder.openParenthesis()

        methodBodyBuilder.append("${requestMappingType?.toLowerCasePreservingASCIIRules()}")
        methodBodyBuilder.openParenthesis()

        methodBodyBuilder.append("\"$requestUri\"")
        methodBodyBuilder.append(", ".repeat(pathParameters.size))
        methodBodyBuilder.closeParenthesis()

        if (queryParameters.isNotEmpty()) {
            methodBodyBuilder.appendLine()
            methodBodyBuilder.append(
                queryParameters.stream()
                .map { param -> ".param(\"${param.name}\", )" }
                .reduce { a, b -> a + System.lineSeparator() + b }
                .orElse(""))
        }
        if (requestObjectClass != null) {
            methodBodyBuilder.appendLine(".contentType(MediaType.APPLICATION_JSON)")
            methodBodyBuilder.append(".content")

            methodBodyBuilder.openParenthesis()
            methodBodyBuilder.append(requestObjectType?.let {
                jsonGenerator.generateJsonRequestBody(it)
            })
            methodBodyBuilder.closeParenthesis()
        }
        methodBodyBuilder.appendLine(projectState.mockMvcAdditions)

        methodBodyBuilder.closeParenthesis()
        methodBodyBuilder.appendLine()

        methodBodyBuilder.appendLine(".andExpect(status().is" + getExpectedStatus(httpStatus) + "())")

        methodBodyBuilder.append(".andDo")
        methodBodyBuilder.openParenthesis()

        methodBodyBuilder.append("document")
        methodBodyBuilder.openParenthesis()

        methodBodyBuilder.append("\"${selectedMethod.name.camelToKebabCase()}\"")
        val documentationFields = listOf(
            generatePathParametersDocumentation(pathParameters),
            generateQueryParametersDocumentation(queryParameters),
            fieldDescriptionGenerator.generateRequestFieldDescriptions(requestObjectType),
            fieldDescriptionGenerator.generateResponseFieldDescriptions(responseObjectType)
        ).stream()
            .filter(String::isNotEmpty)
            .reduce { a, b -> "$a," + System.lineSeparator() + b }
            .map { "," + System.lineSeparator() + it }
            .orElse("")

        methodBodyBuilder.append(documentationFields)
        methodBodyBuilder.closeParenthesis()
        methodBodyBuilder.closeParenthesis()
        methodBodyBuilder.append(";")
        val methodBody = methodBodyBuilder.toString()

        return methodBody
    }

    private fun getExpectedStatus(httpStatus: String?): String? {
        return httpStatus?.removePrefix("HttpStatus.")
            ?.toLowerCasePreservingASCIIRules()
            ?.split("_")
            ?.stream()
            ?.map { s -> s.replaceFirstChar { it.uppercaseChar() } }
            ?.toList()
            ?.joinToString("")
    }

    private fun generateQueryParametersDocumentation(queryParameters: List<PsiParameter>): String {
        if (queryParameters.isNotEmpty()) {
            return "queryParameters(" + System.lineSeparator() + generateDocumentationForParameters(
                queryParameters
            ) + System.lineSeparator() + ")"
        }
        return ""
    }

    private fun generatePathParametersDocumentation(pathParameters: List<PsiParameter>): String {
        if (pathParameters.isNotEmpty()) {
            return "pathParameters(" + System.lineSeparator() + generateDocumentationForParameters(
                pathParameters
            ) + System.lineSeparator() + ")"
        }
        return ""
    }

    private fun generateDocumentationForParameters(parameters: List<PsiParameter>): String? =
        parameters.stream()
            .map { param -> "parameterWithName(\"${param.name}\").description(\"\")" }
            .reduce { a, b -> "$a," + System.lineSeparator() + b }
            .orElse("")

    private fun getRequestMappingOfParentalRestController(selectedMethod: PsiMethod): PsiAnnotation? {
        val requestMappingClassLevel =
            selectedMethod.parentOfType<PsiClass>()?.annotations
                ?.filter {
                    it.qualifiedName?.contains("org.springframework.web.bind.annotation")
                        ?: false
                }?.firstOrNull { it.qualifiedName?.contains("Mapping") ?: false }
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

    private fun getUriFromAnnotation(annotation: PsiAnnotation?): String? {
        if (annotation == null) {
            return ""
        }
        val uri =
            annotation.parameterList.attributes.firstOrNull { it.name == null || it.name == "value" || it.name == "path" }
        if (uri == null) {
            return ""
        }
        return uri.literalValue
    }

    private fun String.camelToKebabCase(): String {
        val pattern = "(?<=.)[A-Z]".toRegex()
        return this.replace(pattern, "-$0").lowercase()
    }

    private fun StringBuilder.openParenthesis() {
        this.append("(")
    }

    private fun StringBuilder.closeParenthesis() {
        this.append(")")
    }
}