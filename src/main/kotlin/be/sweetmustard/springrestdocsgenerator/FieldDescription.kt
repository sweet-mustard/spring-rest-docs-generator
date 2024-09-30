package be.sweetmustard.springrestdocsgenerator

data class FieldDescription(
    val pathPrefix: String,
    val path: String,
    val description: String
) {
    fun depth(): Int {
        return pathPrefix.count { it == '.' }
    }
}


