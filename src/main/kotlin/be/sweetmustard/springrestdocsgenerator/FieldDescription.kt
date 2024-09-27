package be.sweetmustard.springrestdocsgenerator

import be.sweetmustard.springrestdocsgenerator.NodeType.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.stream
import java.util.stream.Collectors

const val MAXIMAL_TREE_DEPTH = 10

fun generateResponseFieldDescriptions(responseObjectType: PsiType?): String {
    if (responseObjectType == null) {
        return ""
    }
    if (TypeChecker.isResponseEntityType(responseObjectType)) {
        val responseFieldDescriptions = generateFieldDescriptions(
            (responseObjectType as PsiClassReferenceType).parameters[0],
            "",
            MAXIMAL_TREE_DEPTH
        )
        return buildFieldsDescriptionString(responseFieldDescriptions, HttpObjectType.RESPONSE)
    } else {
        val responseFieldDescriptions = generateFieldDescriptions(
            responseObjectType,
            "",
            MAXIMAL_TREE_DEPTH
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
        MAXIMAL_TREE_DEPTH
    )
    return buildFieldsDescriptionString(requestFieldDescriptions, HttpObjectType.REQUEST)
}

fun generateJsonRequestBody(requestObjectType: PsiType): String {
    val tree = generateTree(requestObjectType)
    return "\"\"\"" + System.lineSeparator() + buildJsonString(tree, 0) +  "\"\"\""
}

private fun generateTree(type: PsiType) =
    TreeNode("", generateLeaves(type, MAXIMAL_TREE_DEPTH), ROOT)

fun generateFieldDescriptions(field: PsiField, pathPrefix: String, remainingDepth: Int) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    if (remainingDepth < 0) {
        return fieldDescriptions
    }
    val fieldType = field.type
    val description = ""

    if (TypeChecker.isListType(fieldType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
        val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
        fieldDescriptions.addAll(generateFieldDescriptions(
            parameterType,
            pathPrefix + field.name + "[].",
            remainingDepth - 1
        ))
    } else {
        fieldDescriptions.add(FieldDescription(pathPrefix, field.name, description))
        if (!TypeChecker.isBasicType(fieldType) && !TypeChecker.isMapType(fieldType)) {
            fieldDescriptions.addAll(generateFieldDescriptions(
                fieldType,
                pathPrefix + field.name + ".",
                remainingDepth - 1
            ))
        }
    } 
    return fieldDescriptions
}

fun generateFieldDescriptions(classType: PsiType, pathPrefix: String, remainingDepth: Int) : List<FieldDescription> {
    val fieldDescriptions = ArrayList<FieldDescription>()
    if (remainingDepth < 0) {
        return fieldDescriptions
    }

    if (TypeChecker.isListType(classType)) {
        fieldDescriptions.add(FieldDescription(pathPrefix, "[]", ""))
        val parameterType = (classType as PsiClassReferenceType).parameters[0]
        fieldDescriptions.addAll(generateFieldDescriptions(
            parameterType,
            "$pathPrefix[].",
            remainingDepth - 1
        ))
    } else if (!TypeChecker.isBasicType(classType) && !TypeChecker.isMapType(classType)) {
        for (field in PsiTypesUtil.getPsiClass(classType)?.fields!!) {
            fieldDescriptions.addAll(generateFieldDescriptions(
                field,
                pathPrefix,
                remainingDepth - 1
            ))
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
        .reduce {s, t -> s + System.lineSeparator() + t}
        .orElse("")
}

fun generateLeaves(field: PsiField, remainingDepth: Int) : List<TreeNode> {
    val subNodes = ArrayList<TreeNode>()
    if (remainingDepth < 0) {
        return subNodes
    }

    val fieldType = field.type

    if (TypeChecker.isListType(fieldType)) {
        val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
        subNodes.add(TreeNode(field.name, generateLeaves(parameterType, remainingDepth - 1), NAMED_LIST))
    } else if (!TypeChecker.isBasicType(fieldType)) {
            subNodes.add(TreeNode(field.name, generateLeaves(fieldType, remainingDepth - 1), COMPOSITE_OBJECT))
    } else {
            subNodes.add(TreeNode(field.name, emptyList(), SIMPLE_OBJECT))
        
    }
    return subNodes
}

fun generateLeaves(classType: PsiType, remainingDepth: Int) : List<TreeNode> {
    val subNodes = ArrayList<TreeNode>()
    if (remainingDepth < 0) {
        return subNodes
    }

    if (TypeChecker.isListType(classType)) {
        val parameterType = (classType as PsiClassReferenceType).parameters[0]
        subNodes.add(TreeNode("", generateLeaves(parameterType, remainingDepth - 1), UNNAMED_LIST))
    } else if (!TypeChecker.isBasicType(classType)) {
        subNodes.addAll(PsiTypesUtil.getPsiClass(classType)?.fields!!.stream()
            .map { generateLeaves(it, remainingDepth - 1) }
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
