package be.sweetmustard.springrestdocsgenerator

import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes

class TypeChecker {
    companion object {

        fun isBasicType(classType: PsiType): Boolean {
            val basicTypes = listOf(
                "String",
                "Integer",
                "Boolean",
                "UUID",
                "Long",
                "Double"
            ).union(PsiTypes.primitiveTypes().stream().map { it.name }.toList()).plus(
                PsiTypes.voidType().name
            )
            return basicTypes.stream().anyMatch { classType.toString().contains(it) }
        }

        fun isListType(classType: PsiType) = classType.toString().contains("List")

        fun isResponseEntityType(responseObjectType: PsiType) =
            responseObjectType.toString().contains("ResponseEntity")

        fun isMapType(classType: PsiType) = classType.toString().contains("Map")
    }
}