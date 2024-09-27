package be.sweetmustard.springrestdocsgenerator

import be.sweetmustard.springrestdocsgenerator.TreeNode.Companion.NodeType.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.containers.stream

class TreeNode {
    data class TreeNode(val name: String, val subNodes: List<TreeNode>, val nodeType: NodeType)

    companion object {
        fun generateJsonRequestBody(requestObjectType: PsiType): String {
            val tree = generateTree(requestObjectType)
            return "\"\"\"" + System.lineSeparator() + buildJsonString(tree, 0) + "\"\"\""
        }

        private fun generateTree(type: PsiType) =
            TreeNode("", generateLeaves(type, MAXIMAL_DEPTH), ROOT)

        private fun generateLeaves(field: PsiField, remainingDepth: Int): List<TreeNode> {
            val subNodes = ArrayList<TreeNode>()
            if (remainingDepth < 0) {
                return subNodes
            }

            val fieldType = field.type

            if (TypeChecker.isListType(fieldType)) {
                val parameterType = (fieldType as PsiClassReferenceType).parameters[0]!!
                subNodes.add(
                    TreeNode(
                        field.name,
                        generateLeaves(parameterType, remainingDepth - 1),
                        NAMED_LIST
                    )
                )
            } else if (!TypeChecker.isBasicType(fieldType)) {
                subNodes.add(
                    TreeNode(
                        field.name,
                        generateLeaves(fieldType, remainingDepth - 1),
                        COMPOSITE_OBJECT
                    )
                )
            } else {
                subNodes.add(TreeNode(field.name, emptyList(), SIMPLE_OBJECT))

            }
            return subNodes
        }

        private fun generateLeaves(classType: PsiType, remainingDepth: Int): List<TreeNode> {
            val subNodes = ArrayList<TreeNode>()
            if (remainingDepth < 0) {
                return subNodes
            }

            if (TypeChecker.isListType(classType)) {
                val parameterType = (classType as PsiClassReferenceType).parameters[0]
                subNodes.add(
                    TreeNode(
                        "",
                        generateLeaves(parameterType, remainingDepth - 1),
                        UNNAMED_LIST
                    )
                )
            } else if (!TypeChecker.isBasicType(classType)) {
                subNodes.addAll(
                    PsiTypesUtil.getPsiClass(classType)?.fields!!.stream()
                        .map { generateLeaves(it, remainingDepth - 1) }
                        .flatMap { it.stream() }
                        .toList())
            }
            return subNodes
        }

        fun buildJsonString(treeNode: TreeNode, indent: Int): String {
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

        private fun buildJsonPiece(subNodes: List<TreeNode>, indent: Int): String {
            return subNodes.stream()
                .map { subNode -> buildJsonString(subNode, indent + 1) }
                .reduce { a, b -> "$a ," + System.lineSeparator() + b }
                .orElse("")
        }

        enum class NodeType {
            SIMPLE_OBJECT,
            NAMED_LIST,
            UNNAMED_LIST,
            COMPOSITE_OBJECT,
            ROOT
        }
    }
}