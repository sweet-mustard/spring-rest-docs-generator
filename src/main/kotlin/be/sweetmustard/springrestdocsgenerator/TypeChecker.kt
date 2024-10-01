package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTypesUtil
import java.util.stream.Collectors

class TypeChecker {

    private var jsonConvertibleTypes: Set<String> = HashSet()
    private val basicTypes = listOf(
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Boolean",
        "java.util.UUID",
        "java.lang.Long",
        "java.lang.Double",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        "java.time.LocalDate",
        "java.time.LocalDateTime",
        "java.time.ZonedDateTime",
        "java.time.Instant",
        "java.time.Duration"
    ).union(PsiTypes.primitiveTypes().stream().map { PsiTypesUtil.getPsiClass(it)?.qualifiedName }
        .toList())
        .plus(PsiTypesUtil.getPsiClass(PsiTypes.voidType())?.qualifiedName)

    fun isNestedType(classType: PsiType): Boolean {
        return !isBasicType(classType) &&
                !isMapType(classType) &&
                !isJsonConvertibleType(classType) &&
                !isEnumType(classType)
    }

    private fun isBasicType(classType: PsiType): Boolean {
        return basicTypes.stream()
            .anyMatch { PsiTypesUtil.getPsiClass(classType)?.qualifiedName == it }
    }

    fun isListType(classType: PsiType) =
        PsiTypesUtil.getPsiClass(classType)?.qualifiedName == "java.util.List"

    fun isResponseEntityType(responseObjectType: PsiType) =
        PsiTypesUtil.getPsiClass(responseObjectType)?.qualifiedName == "org.springframework.http.ResponseEntity"

    private fun isMapType(classType: PsiType) =
        PsiTypesUtil.getPsiClass(classType)?.qualifiedName == "java.util.Map"

    private fun isEnumType(classType: PsiType) = PsiTypesUtil.getPsiClass(classType)?.isEnum == true

    private fun isJsonConvertibleType(psiType: PsiType?): Boolean {
        return jsonConvertibleTypes.stream()
            .anyMatch { PsiTypesUtil.getPsiClass(psiType)?.qualifiedName?.equals(it) == true }
    }

    fun updateJsonConvertibleTypes(project: Project) {
        val classNames = HashSet<String?>()

        ClassInheritorsSearch.search(
            JavaPsiFacade.getInstance(project).findClass(
                "com.fasterxml.jackson.databind.JsonSerializer",
                GlobalSearchScope.allScope(project)
            )!!,
            GlobalSearchScope.projectScope(project),
            true
        ).map { it.extendsList }
            .forEach { psiReferenceList ->
                for (referenceElement in psiReferenceList?.referenceElements!!) {
                    referenceElement.typeParameters.forEach {
                        classNames.add(
                            PsiTypesUtil.getPsiClass(
                                it
                            )?.qualifiedName
                        )
                    }
                }
            }
        jsonConvertibleTypes = classNames.filterNotNull().stream().collect(Collectors.toSet())
    }
}