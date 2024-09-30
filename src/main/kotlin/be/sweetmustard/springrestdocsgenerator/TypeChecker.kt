package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTypesUtil

class TypeChecker {
    companion object {

        fun isBasicType(classType: PsiType): Boolean {
            val basicTypes = listOf(
                "String",
                "Integer",
                "Boolean",
                "UUID",
                "Long",
                "Double",
                "BigDecimal",
                "BigInteger",
                "LocalDate",
                "LocalDateTime",
                "ZonedDateTime"
            ).union(PsiTypes.primitiveTypes().stream().map { it.name }.toList()).plus(
                PsiTypes.voidType().name
            )
            return basicTypes.stream().anyMatch { classType.toString().contains(it) }
        }

        fun isListType(classType: PsiType) = classType.toString().contains("List")

        fun isResponseEntityType(responseObjectType: PsiType) =
            responseObjectType.toString().contains("ResponseEntity")

        fun isMapType(classType: PsiType) = classType.toString().contains("Map")

        fun isEnumType(classType: PsiType) = PsiTypesUtil.getPsiClass(classType)?.isEnum == true

        fun isJsonConvertibleType(psiType: PsiType?): Boolean {
            val project = PsiTypesUtil.getPsiClass(psiType)?.project ?: return false
            val allJsonConvertibleTypes =
                getAllJsonConvertibleTypes(project)
            return allJsonConvertibleTypes.stream()
                .anyMatch { PsiTypesUtil.getPsiClass(psiType)?.qualifiedName?.equals(it) == true }
        }

        fun getAllJsonConvertibleTypes(project: Project): Set<String> {
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
            return classNames.filterNotNull().toSet()
        }
    }
}