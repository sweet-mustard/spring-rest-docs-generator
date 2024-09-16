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
            
            var methodBody =
                "mockMvc.perform(${requestMappingType?.toLowerCasePreservingASCIIRules()}(\"$requestUri\""
            if (pathParameters.isNotEmpty()) {
                methodBody += ", ".repeat(pathParameters.size)
            }
            methodBody += ")\n"
            if (queryParameters.isNotEmpty()) {
                methodBody += queryParameters.stream()
                    .map { param -> ".param(\"${param.name}\", )" }
                    .reduce { a, b -> "$a\n$b" }
                    .orElse("")
                methodBody += "\n"
            }
            if (requestObjectClass != null) {
                val requestObjectFields = requestObjectClass.fields
                methodBody += ".contentType(MediaType.APPLICATION_JSON)\n"
                methodBody += ".content(\"\"\"\n{\n"
                methodBody += requestObjectFields.stream()
                    .map { field -> "\"${field.name}\":" }
                    .reduce { a: String, b: String -> "$a,\n$b" }
                    .orElse("")
                methodBody += "\n}\n\"\"\")\n"
            }
            methodBody += ")\n"
            methodBody += ".andExpect(status().is" + httpStatus?.removePrefix("HttpStatus.")
                ?.toLowerCasePreservingASCIIRules()
                ?.replaceFirstChar { c -> c.uppercaseChar() } + "())\n"
            methodBody += ".andDo(document(\"${selectedMethod.name.camelToKebabCase()}\",\n"
            if (pathParameters.isNotEmpty()) {
                methodBody += "pathParameters(\n"
                methodBody += pathParameters.stream()
                    .map { param -> "parameterWithName(\"${param.name}\").description(\"\")" }
                    .reduce { a, b -> "$a\n$b" }
                    .orElse("")
                methodBody += "\n)\n"
            }
            if (queryParameters.isNotEmpty()) {
                methodBody += "queryParameters(\n"
                methodBody += queryParameters.stream()
                    .map { param -> "parameterWithName(\"${param.name}\").description(\"\")" }
                    .reduce { a, b -> "$a\n$b" }
                    .orElse("")
                methodBody += "\n)\n"
            }
            println(methodBody)
        }
    }

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
}