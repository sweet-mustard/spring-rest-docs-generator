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
            
            var methodBody =
                "mockMvc.perform(${requestMappingType?.toLowerCasePreservingASCIIRules()}(\"$requestUri\""
            if (pathParameters.isNotEmpty()) {
                methodBody += ", ".repeat(pathParameters.size)
            }
            methodBody += ")\n"
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
}