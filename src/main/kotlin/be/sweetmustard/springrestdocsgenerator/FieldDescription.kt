package be.sweetmustard.springrestdocsgenerator

import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import java.util.stream.Collectors

val basicTypes = listOf("String", "Integer", "Boolean", "UUID", "Long", "Double").union(PsiTypes.primitiveTypes().stream().map { it.name }.toList()).plus(PsiTypes.voidType().name)

data class FieldDescription(
    val pathPrefix: String,
    val path: String,
    val description: String
) {
    fun depth() : Int {
        return pathPrefix.count { it == '.'}
    } 
}

fun generateResponseFieldDescriptions(responseObjectType: PsiType): String {
    val responseFieldDescriptions = generateFieldDescriptions(responseObjectType, "")
    return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
}

fun generateRequestFieldDescriptions(requestObjectType: PsiType): String {
    val requestFieldDescriptions = generateFieldDescriptions(requestObjectType, "")
    return buildFieldsDescriptionString(requestFieldDescriptions, HttpObjectType.REQUEST)
}

fun generateFieldDescriptions(field : PsiField, pathPrefix : String) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    
    val fieldType = field.type
    val description = "Fill in description for ${field.name}"

    if (isListType(fieldType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name + "[]", description))
        val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
        fieldDescriptions.addAll(generateFieldDescriptions(parameterType, pathPrefix + field.name + "[]."))
    } else {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
        if (!isBasicType(fieldType)) {
            fieldDescriptions.addAll(generateFieldDescriptions(fieldType, pathPrefix + field.name + "."))
        }
    } 
    return fieldDescriptions
}

fun generateFieldDescriptions(classType : PsiType, pathPrefix : String) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    
    if (isListType(classType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, "[]", "Fill in description"))
        val parameterType = (classType as PsiClassReferenceType).parameters[0]
        fieldDescriptions.addAll(generateFieldDescriptions(parameterType, "$pathPrefix[]."))
    } else if (!isBasicType(classType)) {
        for (field in PsiTypesUtil.getPsiClass(classType)?.fields!!) {
            fieldDescriptions.addAll(generateFieldDescriptions(field, pathPrefix))
        }
    }
    return fieldDescriptions
}

fun buildFieldsDescriptionString(fieldDescriptions : List<FieldDescription>, httpObjectType: HttpObjectType): String {
    val fieldDescriptionsGroupedByDepth = fieldDescriptions.stream()
        .collect(Collectors.groupingBy { it.depth() })

    val descriptions = ArrayList<String>()

    for (entry in fieldDescriptionsGroupedByDepth) {
        val fieldDescriptionsGroupedByPrefix = entry.value.stream()
            .collect(Collectors.groupingBy { it.pathPrefix })

        for (prefixGrouped in fieldDescriptionsGroupedByPrefix) {
            val prefixLine = if (prefixGrouped.key.isNotEmpty()) ".andWithPrefix(\"" + prefixGrouped.key + "\", " else httpObjectType.fieldsDescription + "("
            val fieldLines = prefixGrouped.value.stream()
                .map { "fieldWithPath(\"" + it.path + "\").description(\"" + it.description + "\")" }
                .reduce {s, t -> s + "," + System.lineSeparator() + t }
                .orElse("")

            val element =
                prefixLine + System.lineSeparator() + fieldLines + ")"
            descriptions.add(element)
        }
    }

    return descriptions.stream()
        .reduce {s, t, -> s + System.lineSeparator() + t}
        .orElse("")
}

private fun isListType(classType: PsiType) = classType.toString().contains("List")

private fun isBasicType(fieldType: PsiType) =
    basicTypes.stream().anyMatch { fieldType.toString().contains(it) }

enum class HttpObjectType(val fieldsDescription: String) {
    RESPONSE("responseFields"),
    REQUEST("requestFields");
    

}
