package be.sweetmustard.springrestdocsgenerator.generator

import be.sweetmustard.springrestdocsgenerator.FieldDescription
import be.sweetmustard.springrestdocsgenerator.TypeChecker
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import java.util.stream.Collectors

class FieldDescriptionGenerator(private val typeChecker: TypeChecker) {

    private val MAXIMAL_DEPTH = 10

    fun generateResponseFieldDescriptions(responseObjectType: PsiType?): String {
        if (responseObjectType == null) {
            return ""
        }
        if (typeChecker.isResponseEntityType(responseObjectType)) {
            val responseFieldDescriptions = generateFieldDescriptions(
                (responseObjectType as PsiClassReferenceType).parameters[0],
                "",
                MAXIMAL_DEPTH
            )
            return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
        } else {
            val responseFieldDescriptions = generateFieldDescriptions(
                responseObjectType,
                "",
                MAXIMAL_DEPTH
            )
            return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
        }
    }

    fun generateRequestFieldDescriptions(requestObjectType: PsiType?): String {
        if (requestObjectType == null) {
            return ""
        }
        val requestFieldDescriptions = generateFieldDescriptions(
            requestObjectType,
            "",
            MAXIMAL_DEPTH
        )
        return buildFieldsDescriptionString(requestFieldDescriptions, HttpObjectType.REQUEST)
    }

    fun generateFieldDescriptions(
        field: PsiField,
        pathPrefix: String,
        remainingDepth: Int
    ): List<FieldDescription> {
        val fieldDescriptions = ArrayList<FieldDescription>()
        if (remainingDepth < 0) {
            return fieldDescriptions
        }
        val fieldType = field.type
        val description = ""

        if (typeChecker.isListType(fieldType)) {
            fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
            val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
            fieldDescriptions.addAll(
                generateFieldDescriptions(
                    parameterType,
                    pathPrefix + field.name + "[].",
                    remainingDepth - 1
                )
            )
        } else {
            fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
            if (!typeChecker.isBasicType(fieldType) && !typeChecker.isMapType(fieldType) && !typeChecker.isJsonConvertibleType(
                    fieldType
                ) && !typeChecker.isEnumType(fieldType)
            ) {
                fieldDescriptions.addAll(
                    generateFieldDescriptions(
                        fieldType,
                        pathPrefix + field.name + ".",
                        remainingDepth - 1
                    )
                )
            }
        }
        return fieldDescriptions
    }

    fun generateFieldDescriptions(
        classType: PsiType,
        pathPrefix: String,
        remainingDepth: Int
    ): List<FieldDescription> {
        val fieldDescriptions = ArrayList<FieldDescription>()
        if (remainingDepth < 0) {
            return fieldDescriptions
        }

        if (typeChecker.isListType(classType)) {
            fieldDescriptions.add(FieldDescription(pathPrefix, "[]", ""))
            val parameterType = (classType as PsiClassReferenceType).parameters[0]
            fieldDescriptions.addAll(
                generateFieldDescriptions(
                    parameterType,
                    "$pathPrefix[].",
                    remainingDepth - 1
                )
            )
        } else if (!typeChecker.isBasicType(classType) && !typeChecker.isMapType(classType) && !typeChecker.isJsonConvertibleType(
                classType
            ) && !typeChecker.isEnumType(classType)
        ) {
            for (field in PsiTypesUtil.getPsiClass(classType)?.fields!!) {
                fieldDescriptions.addAll(
                    generateFieldDescriptions(
                        field,
                        pathPrefix,
                        remainingDepth - 1
                    )
                )
            }
        }
        return fieldDescriptions
    }

    fun buildFieldsDescriptionString(
        fieldDescriptions: List<FieldDescription>,
        httpObjectType: HttpObjectType
    ): String {
        val fieldDescriptionsGroupedByDepth = fieldDescriptions.stream()
            .collect(Collectors.groupingBy { it.depth() })

        val descriptions = ArrayList<String>()

        for (entry in fieldDescriptionsGroupedByDepth) {
            val fieldDescriptionsGroupedByPrefix = entry.value.stream()
                .collect(Collectors.groupingBy { it.pathPrefix })

            for (prefixGrouped in fieldDescriptionsGroupedByPrefix) {
                val prefixLine =
                    if (prefixGrouped.key.isNotEmpty()) ".andWithPrefix(\"" + prefixGrouped.key + "\", " else httpObjectType.fieldsDescription + "("
                val fieldLines = prefixGrouped.value.stream()
                    .map { "fieldWithPath(\"" + it.path + "\").description(\"" + it.description + "\")" }
                    .reduce { s, t -> s + "," + System.lineSeparator() + t }
                    .orElse("")

                val element =
                    prefixLine + System.lineSeparator() + fieldLines + ")"
                descriptions.add(element)
            }
        }

        return descriptions.stream()
            .reduce { s, t -> s + System.lineSeparator() + t }
            .orElse("")
    }

    enum class HttpObjectType(val fieldsDescription: String) {
        RESPONSE("responseFields"),
        REQUEST("requestFields");
    }
}