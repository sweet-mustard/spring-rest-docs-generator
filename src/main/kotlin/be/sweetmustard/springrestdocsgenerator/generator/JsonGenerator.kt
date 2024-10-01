package be.sweetmustard.springrestdocsgenerator.generator

import be.sweetmustard.springrestdocsgenerator.NodeType
import be.sweetmustard.springrestdocsgenerator.TreeNode
import be.sweetmustard.springrestdocsgenerator.TypeChecker
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.stream

class JsonGenerator(private val typeChecker: TypeChecker) {
    private val MAXIMAL_DEPTH = 10

    fun generateJsonRequestBody(requestObjectType: PsiType): String {
        val tree = generateTree(requestObjectType)
        return "\"\"\"" + System.lineSeparator() + buildJsonString(tree, 0) + "\"\"\""
    }

    private fun generateTree(type: PsiType) =
        TreeNode("", generateLeaves(type, MAXIMAL_DEPTH), NodeType.ROOT)

    private fun generateLeaves(field: PsiField?, remainingDepth: Int): List<TreeNode> {
        val subNodes = ArrayList<TreeNode>()
        if (remainingDepth < 0 || field == null) {
            return subNodes
        }

        val fieldType = field.type

        if (typeChecker.isListType(fieldType)) {
            val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
            subNodes.add(
                TreeNode(
                    field.name,
                    generateLeaves(parameterType, remainingDepth - 1),
                    NodeType.NAMED_LIST
                )
            )
        } else if (typeChecker.isArrayType(fieldType)) {
            val parameterType = (fieldType as PsiArrayType).componentType
            subNodes.add(
                TreeNode(
                    field.name,
                    generateLeaves(parameterType, remainingDepth - 1),
                    NodeType.NAMED_LIST
                )
            )
        } else if (typeChecker.isNestedType(fieldType)) {
            subNodes.add(
                TreeNode(
                    field.name,
                    generateLeaves(fieldType, remainingDepth - 1),
                    NodeType.COMPOSITE_OBJECT
                )
            )
        } else {
            subNodes.add(TreeNode(field.name, emptyList(), NodeType.SIMPLE_OBJECT))
        }
        return subNodes
    }

    private fun generateLeaves(classType: PsiType?, remainingDepth: Int): List<TreeNode> {
        val subNodes = ArrayList<TreeNode>()
        if (remainingDepth < 0 || classType == null) {
            return subNodes
        }

        if (typeChecker.isListType(classType)) {
            val parameterType = (classType as PsiClassReferenceType).parameters[0]
            subNodes.add(
                TreeNode(
                    "",
                    generateLeaves(parameterType, remainingDepth - 1),
                    NodeType.UNNAMED_LIST
                )
            )
        } else if (typeChecker.isArrayType(classType)) {
            val parameterType = (classType as PsiArrayType).componentType
            subNodes.add(
                TreeNode(
                    "",
                    generateLeaves(parameterType, remainingDepth - 1),
                    NodeType.UNNAMED_LIST
                )
            )
        } else if (typeChecker.isNestedType(classType)) {
            subNodes.addAll(
                PsiTypesUtil.getPsiClass(classType)?.fields!!.stream()
                    .map { generateLeaves(it, remainingDepth - 1) }
                    .flatMap { it.stream() }
                    .toList())
        }
        return subNodes
    }

    private fun buildJsonString(treeNode: TreeNode, indent: Int): String {
        val jsonNode = StringBuilder()
        val indentation = "  "
        when (treeNode.nodeType) {
            NodeType.ROOT -> {
                jsonNode.appendLine(indentation.repeat(indent) + "{")
                jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
                jsonNode.appendLine(indentation.repeat(indent) + "}")
            }

            NodeType.UNNAMED_LIST -> {
                jsonNode.appendLine(indentation.repeat(indent) + "[")
                jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
                jsonNode.appendLine(indentation.repeat(indent) + "]")
            }

            NodeType.NAMED_LIST -> {
                jsonNode.appendLine(indentation.repeat(indent) + "\"${treeNode.name}\": [")
                if (treeNode.subNodes.isNotEmpty()) {
                    jsonNode.appendLine(indentation.repeat(indent + 1) + "{")
                    jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
                    jsonNode.appendLine(indentation.repeat(indent + 1) + "}")
                }
                jsonNode.append(indentation.repeat(indent) + "]")
            }

            NodeType.COMPOSITE_OBJECT -> {
                jsonNode.appendLine(indentation.repeat(indent) + "\"${treeNode.name}\": {")
                jsonNode.appendLine(buildJsonPiece(treeNode.subNodes, indent + 1))
                jsonNode.append(indentation.repeat(indent) + "}")
            }

            NodeType.SIMPLE_OBJECT -> {
                jsonNode.append(indentation.repeat(indent) + "\"${treeNode.name}\":")
            }
        }
        return jsonNode.toString()
    }

    private fun buildJsonPiece(subNodes: List<TreeNode>, indent: Int): String {
        return subNodes.stream()
            .map { subNode -> buildJsonString(subNode, indent + 1) }
            .reduce { a, b -> "$a ," + System.lineSeparator() + b }
            .orElse("")
    }
}