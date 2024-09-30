package be.sweetmustard.springrestdocsgenerator


data class TreeNode(val name: String, val subNodes: List<TreeNode>, val nodeType: NodeType)


enum class NodeType {
    SIMPLE_OBJECT,
    NAMED_LIST,
    UNNAMED_LIST,
    COMPOSITE_OBJECT,
    ROOT
}
