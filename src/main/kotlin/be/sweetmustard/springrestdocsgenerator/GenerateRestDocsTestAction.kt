package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.stream
import io.ktor.util.*


class GenerateRestDocsTestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val selectedMethod: PsiElement? = event.getData(CommonDataKeys.PSI_ELEMENT)

        if (selectedMethod is PsiMethod) {
            
            val requestMappingOfMethod = getRequestMappingOfMethod(selectedMethod)

            val requestMappingOfClass =
                getRequestMappingOfParentalRestController(selectedMethod)
            
            val requestUri =
                getUriFromAnnotation(requestMappingOfClass) + getUriFromAnnotation(requestMappingOfMethod)
            
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
                methodBodyBuilder.appendLine("request.contentType(MediaType.APPLICATION_JSON)")
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
            if (pathParameters.isNotEmpty()) {
                methodBodyBuilder.append(generatePathParametersDocumentation(pathParameters))
            }
            if (queryParameters.isNotEmpty()) {
                methodBodyBuilder.append(generateQueryParametersDocumentation(queryParameters))
            }
            if (requestObjectClass != null) {
                methodBodyBuilder.append(generateRequestObjectDocumentation(requestObjectClass))
            }
            if (responseObject != null) {
                methodBodyBuilder.append(generateResponseObjectDocumentation(responseObject))
            }
            methodBodyBuilder.append(");")
            println(methodBodyBuilder.toString())
        }
    }

    private fun generateQueryParametersDocumentation(queryParameters: List<PsiParameter>) =
        "queryParameters(\n" + generateDocumentationForParameters(queryParameters) + "\n)\n"

    private fun generatePathParametersDocumentation(pathParameters: List<PsiParameter>) =
        "pathParameters(\n" + generateDocumentationForParameters(pathParameters) + "\n)\n"

    private fun generateDocumentationForParameters(pathParameters: List<PsiParameter>): String? =
        pathParameters.stream()
            .map { param -> "parameterWithName(\"${param.name}\").description(\"\")" }
            .reduce { a, b -> "$a\n$b" }
            .orElse("")

    private fun generateRequestObjectDocumentation(requestObjectClass: PsiClass) =
        "requestFields(\n" + generateDocumentationForFields(requestObjectClass.fields) + "\n)\n"

    private fun generateResponseObjectDocumentation(requestObjectClass: PsiClass) =
        "responseFields(\n" + generateDocumentationForFields(requestObjectClass.fields) + "\n)\n"

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

    private fun generateDocumentationForFields(responseObjectFields: Array<PsiField>): String? =
        responseObjectFields.stream()
            .map { param -> "fieldWithPath(\"${param.name}\").description(\"\")" }
            .reduce { a, b -> "$a\n$b" }
            .orElse("")
    
    private fun String.camelToKebabCase(): String {
        val pattern = "(?<=.)[A-Z]".toRegex()
        return this.replace(pattern, "-$0").lowercase()
    }
}