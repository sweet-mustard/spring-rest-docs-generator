package be.sweetmustard.springrestdocsgenerator

import be.sweetmustard.springrestdocsgenerator.NodeType.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.stream
import java.util.stream.Collectors

fun generateResponseFieldDescriptions(responseObjectType: PsiType?): String {
    if (responseObjectType == null) {
        return ""
    }
    if (isResponseEntityType(responseObjectType)) {
        val responseFieldDescriptions = generateFieldDescriptions((responseObjectType as PsiClassReferenceType).parameters[0], "")
        return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
    } else {
        val responseFieldDescriptions = generateFieldDescriptions(responseObjectType, "")
        return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
    }
}

fun generateRequestFieldDescriptions(requestObjectType: PsiType?): String {
    if (requestObjectType == null) {
        return ""
    }
    val requestFieldDescriptions = generateFieldDescriptions(requestObjectType, "")
    return buildFieldsDescriptionString(requestFieldDescriptions, HttpObjectType.REQUEST)
}

fun generateJsonRequestBody(requestObjectType: PsiType): String {
    val tree = TreeNode("", generateClassTree(requestObjectType), ROOT)
    return "\"\"\"" + System.lineSeparator() + buildJsonString(tree, 0) +  "\"\"\""
}

fun generateFieldDescriptions(field : PsiField, pathPrefix : String) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    
    val fieldType = field.type
    val description = ""

    if (isListType(fieldType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
        val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
        fieldDescriptions.addAll(generateFieldDescriptions(parameterType, pathPrefix + field.name + "[]."))
    } else {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
        if (!isBasicType(fieldType) && !isMapType(fieldType)) {
            fieldDescriptions.addAll(generateFieldDescriptions(fieldType, pathPrefix + field.name + "."))
        }
    } 
    return fieldDescriptions
}

fun generateFieldDescriptions(classType : PsiType, pathPrefix : String) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    
    if (isListType(classType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, "[]", ""))
        val parameterType = (classType as PsiClassReferenceType).parameters[0]
        fieldDescriptions.addAll(generateFieldDescriptions(parameterType, "$pathPrefix[]."))
    } else if (!isBasicType(classType) && !isMapType(classType)) {
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

fun generateClassTree(field : PsiField) : List<TreeNode> {
    val subNodes = ArrayList<TreeNode>()

    val fieldType = field.type

    if (isListType(fieldType)) {
        val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
        subNodes.add(TreeNode(field.name, generateClassTree(parameterType), NAMED_LIST))
    } else {
        if (!isBasicType(fieldType)) {
            subNodes.add(TreeNode(field.name, generateClassTree(fieldType), COMPOSITE_OBJECT))
        } else {
            subNodes.add(TreeNode(field.name, emptyList(), SIMPLE_OBJECT))
        }
    }
    return subNodes
}

fun generateClassTree(classType : PsiType) : List<TreeNode> {
    val subNodes = ArrayList<TreeNode>()

    if (isListType(classType)) {
        val parameterType = (classType as PsiClassReferenceType).parameters[0]
        subNodes.add(TreeNode("", generateClassTree(parameterType), UNNAMED_LIST))
    } else if (!isBasicType(classType)) {
        subNodes.addAll(PsiTypesUtil.getPsiClass(classType)?.fields!!.stream()
            .map { generateClassTree(it) }
            .flatMap { it.stream() }
            .toList())
    }
    return subNodes
}

fun buildJsonString(treeNode: TreeNode, indent : Int) : String {
    val jsonNode = StringBuilder()
    val indentation = "  "
    when (treeNode.nodeType) {
        ROOT -> {
            jsonNode.appendLine(indentation.repeat(indent) + "{")
            jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
            jsonNode.appendLine(indentation.repeat(indent) + "}")
        }
        UNNAMED_LIST -> {
            jsonNode.appendLine(indentation.repeat(indent) + "[")
            jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
            jsonNode.appendLine(indentation.repeat(indent) + "]")
        }
        NAMED_LIST -> {
            jsonNode.appendLine(indentation.repeat(indent) + "\"${treeNode.name}\": [")
            if (treeNode.subNodes.isNotEmpty()) {
                jsonNode.appendLine(indentation.repeat(indent + 1) + "{")
                jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
                jsonNode.appendLine(indentation.repeat(indent + 1) + "}")
            }
            jsonNode.append(indentation.repeat(indent) + "]")
        }
        COMPOSITE_OBJECT -> {
            jsonNode.appendLine(indentation.repeat(indent) + "\"${treeNode.name}\": {")
            jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
            jsonNode.append(indentation.repeat(indent) + "}")
        }
        SIMPLE_OBJECT -> {
            jsonNode.append(indentation.repeat(indent) + "\"${treeNode.name}\":")
        }
    }
    return jsonNode.toString()
}

fun buildJsonPiece(subNodes : List<TreeNode>, indent: Int) : String {
    return subNodes.stream()
        .map { subNode -> buildJsonString(subNode, indent + 1) }
        .reduce { a, b -> "$a ," + System.lineSeparator() + b }
        .orElse("")
}

val basicTypes = listOf("String", "Integer", "Boolean", "UUID", "Long", "Double").union(PsiTypes.primitiveTypes().stream().map { it.name }.toList()).plus(PsiTypes.voidType().name)

private fun isBasicType(classType: PsiType) =
    basicTypes.stream().anyMatch { classType.toString().contains(it) }

private fun isListType(classType: PsiType) = classType.toString().contains("List")

private fun isResponseEntityType(responseObjectType: PsiType) =
    responseObjectType.toString().contains("ResponseEntity")

private fun isMapType(classType: PsiType) = classType.toString().contains("Map")


data class FieldDescription(
    val pathPrefix: String,
    val path: String,
    val description: String
) {
    fun depth() : Int {
        return pathPrefix.count { it == '.'}
    }
}

data class TreeNode(val name: String, val subNodes: List<TreeNode>, val nodeType: NodeType)

enum class HttpObjectType(val fieldsDescription: String) {
    RESPONSE("responseFields"),
    REQUEST("requestFields");
}

enum class NodeType {
    SIMPLE_OBJECT,
    NAMED_LIST,
    UNNAMED_LIST,
    COMPOSITE_OBJECT,
    ROOT
}
